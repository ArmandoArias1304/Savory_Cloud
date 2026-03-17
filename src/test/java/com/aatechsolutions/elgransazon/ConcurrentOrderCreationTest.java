package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.repository.CompanyRepository;
import com.aatechsolutions.elgransazon.domain.repository.DailyOrderCounterRepository;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuRepository;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================
 *  TESTS DE CONCURRENCIA - Creacion de pedidos simultaneos
 * ============================================================
 * BD real: bd_restaurant (localhost, root, sin contrasena)
 *
 * Datos reales:
 *  C1 elbuensazon        items: 2,7,11,16   emps: 2,9,16,33,36
 *  C2 elcamarondormido   items: 4,8,10,15   emps: 3,8,17,32,37
 *  C3 pizzamax           items: 1,6,9,13    emps: 4,7,15,31,35
 *  C4 elveracruzano      items: 3,5,12,14   emps: 5,6,14,30,34
 *
 * Escenarios:
 *  T1 - Rafaga masiva: 4 empresas x 30 pedidos = 120 hilos
 *  T2 - Misma empresa, 100 pedidos simultaneos
 *  T3 - Counter borrado + 80 pedidos concurrentes (bug original)
 *  T4 - Mismo username en 2 empresas distintas (fix multi-tenant)
 *  T5 - Pedidos con multiples items (4 empresas x 25 pedidos)
 *  T6 - Oleadas repetidas: 5 rafagas de 40 pedidos
 *  T7 - Cancelacion concurrente: crear 40 pedidos + cancelarlos simultaneamente (verifica devolucion de stock)
 * ============================================================
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConcurrentOrderCreationTest {

    @Autowired
    @Qualifier("adminOrderService")
    private OrderService adminOrderService;

    @Autowired private CompanyRepository             companyRepository;
    @Autowired private ItemMenuRepository            itemMenuRepository;
    @Autowired private EmployeeRepository            employeeRepository;
    @Autowired private OrderRepository               orderRepository;
    @Autowired private DailyOrderCounterRepository   dailyOrderCounterRepository;
    @Autowired private com.aatechsolutions.elgransazon.application.service.ItemMenuService itemMenuService;

    // IDs reales de la BD bd_restaurant
    private static final long C1 = 1, C2 = 2, C3 = 3, C4 = 4;

    // Se puebla dinámicamente en @BeforeEach con items que NO tienen horario
    // personalizado (hasCustomSchedule = false/null), garantizando disponibilidad
    // cualquiera que sea el día en que se ejecuten los tests.
    private final Map<Long, Long[]> items = new HashMap<>();

    private static final Map<Long, Long[]> EMPS = Map.of(
        C1, new Long[]{2L, 9L, 16L, 33L, 36L},
        C2, new Long[]{3L, 8L, 17L, 32L, 37L},
        C3, new Long[]{4L, 7L, 15L, 31L, 35L},
        C4, new Long[]{5L, 6L, 14L, 30L, 34L}
    );

    // Cache de Company (objeto necesario para CompanyContext)
    private final Map<Long, Company>      companies  = new HashMap<>();
    // Cache de precios (BigDecimal es serializable/inmutable, sin sesion JPA)
    private final Map<Long, BigDecimal>   itemPrices = new ConcurrentHashMap<>();

    // Acumuladores de cada test (se resetean en @BeforeEach)
    private final List<String>  allNumbers = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger allErrors  = new AtomicInteger(0);

    // -------------------------------------------------------------------------

    @BeforeEach
    void setup() {
        allNumbers.clear();
        allErrors.set(0);
        // Cargar solo lo necesario en memoria (sin lazy collections)
        if (companies.isEmpty()) {
            for (long id : new long[]{C1, C2, C3, C4}) {
                companies.put(id, companyRepository.findById(id)
                    .orElseThrow(() -> new IllegalStateException("Empresa " + id + " no encontrada")));
            }
        }
        // Descubrir dinámicamente items disponibles todos los días por empresa.
        // Filtramos: activos, disponibles, sin horario personalizado (siempre disponibles),
        // y con suficiente stock real (verify con ItemMenuService que tiene transaccion propia
        // para poder acceder a las colecciones lazy de ingredientes).
        items.clear();
        for (long cid : new long[]{C1, C2, C3, C4}) {
            Company company = companies.get(cid);
            // hasEnoughStock requiere CompanyContext activo — lo seteamos para este hilo
            CompanyContext.setCurrentCompany(company);
            Long[] ids;
            try {
                ids = itemMenuRepository.findByActiveTrueAndCompany(company).stream()
                    .filter(item -> Boolean.TRUE.equals(item.getAvailable()))
                    .filter(item -> item.getHasCustomSchedule() == null || !item.getHasCustomSchedule())
                    .map(ItemMenu::getIdItemMenu)
                    .filter(id -> itemMenuService.hasEnoughStock(id, 1))   // verifica stock real
                    .limit(4)
                    .toArray(Long[]::new);
            } finally {
                CompanyContext.clear();
            }
            if (ids.length < 1) {
                throw new IllegalStateException(
                    "No hay items activos con stock disponibles todos los dias para empresa " + cid);
            }
            items.put(cid, ids);
            for (Long iid : ids) {
                itemPrices.putIfAbsent(iid, itemMenuRepository.findById(iid)
                    .map(ItemMenu::getPrice).orElse(BigDecimal.valueOf(100)));
            }
        }
    }

    @AfterEach
    void printResumen() {
        System.out.println("\n──────────────────────────────────────────────────");
        System.out.println("  Pedidos generados : " + allNumbers.size());
        System.out.println("  Errores           : " + allErrors.get());
        System.out.println("  Numeros unicos    : " + new HashSet<>(allNumbers).size());
        System.out.println("──────────────────────────────────────────────────\n");
        CompanyContext.clear();
    }

    // =========================================================================
    // T1 — Rafaga masiva: 4 empresas × 5 pedidos = 20 hilos
    // =========================================================================
    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("T1 - Rafaga masiva: 4 empresas x 30 pedidos = 120 hilos")
    void t1_rafaga_masiva_4_empresas() throws Exception {
        System.out.println("\n=== T1: Rafaga masiva (120 hilos) ===");
        List<Runnable> tareas = new ArrayList<>();
        int porEmpresa = 30;
        for (long cid : new long[]{C1, C2, C3, C4}) {
            Long[] itemIds = items.get(cid);
            Long[] empIds  = EMPS.get(cid);
            for (int i = 0; i < porEmpresa; i++) {
                final long cIdFinal = cid;
                final long empId  = empIds[i % empIds.length];
                final long itemId = itemIds[i % itemIds.length];
                tareas.add(() -> ejecutarOrden(cIdFinal, empId, itemId, "T1"));
            }
        }
        dispararSimultaneo(tareas);
        assertSinDuplicados("T1");
    }

    // =========================================================================
    // T2 — Misma empresa, 15 pedidos simultaneos
    // =========================================================================
    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("T2 - Misma empresa (El Buen Sazon), 100 pedidos simultaneos")
    void t2_misma_empresa_alta_carga() throws Exception {
        System.out.println("\n=== T2: Misma empresa, 100 hilos ===");
        Long[] itemIds = items.get(C1);
        Long[] empIds  = EMPS.get(C1);
        List<Runnable> tareas = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            final long empId  = empIds[i % empIds.length];
            final long itemId = itemIds[i % itemIds.length];
            tareas.add(() -> ejecutarOrden(C1, empId, itemId, "T2"));
        }
        dispararSimultaneo(tareas);
        assertSinDuplicados("T2");
    }

    // =========================================================================
    // T3 — Counter borrado + 10 pedidos concurrentes  (el bug reportado)
    // =========================================================================
    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("T3 - Counter borrado y 80 pedidos concurrentes (reproduce bug)")
    void t3_counter_borrado_y_concurrencia() throws Exception {
        System.out.println("\n=== T3: Counter borrado + 80 hilos ===");
        dailyOrderCounterRepository.deleteAll();
        System.out.println("  >> Contadores borrados. Pedidos existentes siguen en orders.");
        Long[] itemIds = items.get(C2);
        Long[] empIds  = EMPS.get(C2);
        List<Runnable> tareas = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            final long empId  = empIds[i % empIds.length];
            final long itemId = itemIds[i % itemIds.length];
            tareas.add(() -> ejecutarOrden(C2, empId, itemId, "T3"));
        }
        dispararSimultaneo(tareas);
        // Numeros de test NO deben colisionar con pedidos anteriores de esta empresa
        Set<String> numerosAnteriores = orderRepository.findAll().stream()
            .filter(o -> o.getCompany() != null
                      && Long.valueOf(C2).equals(o.getCompany().getIdCompany())
                      && !"test-concurrencia".equals(o.getCreatedBy()))
            .map(Order::getOrderNumber)
            .collect(Collectors.toSet());
        for (String key : allNumbers) {
            // key format: "cid|ORD-YYYYMMDD-XXX"
            String num = key.contains("|") ? key.split("\\|", 2)[1] : key;
            assertThat(numerosAnteriores)
                .as("COLISION con pedido existente: " + num)
                .doesNotContain(num);
        }
        assertSinDuplicados("T3");
    }

    // =========================================================================
    // T4 — Mismo username en 2 empresas distintas (guard multi-tenant)
    // =========================================================================
    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("T4 - Mismo username en 2 empresas distintas (guard multi-tenant)")
    void t4_mismo_username_distintas_empresas() throws Exception {
        System.out.println("\n=== T4: Juan1234 en empresa 1 (emp=9) y empresa 2 (emp=8) ===");
        // emp=9 y emp=8 son ambos llamados Juan1234 en distintas empresas
        List<String> nums  = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger errs = new AtomicInteger(0);
        CountDownLatch listo   = new CountDownLatch(2);
        CountDownLatch dispara = new CountDownLatch(1);
        CountDownLatch fin     = new CountDownLatch(2);
        long[][] pares = {{C1, 9L, items.get(C1)[0]}, {C2, 8L, items.get(C2)[0]}};
        for (long[] par : pares) {
            final long cid = par[0], empId = par[1], itemId = par[2];
            new Thread(() -> {
                listo.countDown();
                try {
                    dispara.await();
                    CompanyContext.setCurrentCompany(companies.get(cid));
                    Employee emp  = employeeRepository.getReferenceById(empId);
                    ItemMenu item = itemMenuRepository.getReferenceById(itemId);
                    Order creado  = adminOrderService.create(
                        buildOrder(companies.get(cid), emp),
                        List.of(buildDetailFromRef(item, itemPrices.getOrDefault(itemId, BigDecimal.valueOf(100)), 1))
                    );
                    nums.add(cid + "|" + creado.getOrderNumber());
                    System.out.println("  OK  " + creado.getOrderNumber() + " [empresa=" + cid + "]");
                } catch (Exception e) {
                    errs.incrementAndGet();
                    System.err.println("  ERR empresa=" + cid + " -> " + e.getMessage());
                } finally {
                    CompanyContext.clear();
                    fin.countDown();
                }
            }).start();
        }
        listo.await();
        dispara.countDown();
        fin.await(10, TimeUnit.SECONDS);
        allNumbers.addAll(nums);
        allErrors.addAndGet(errs.get());
        assertThat(nums)
            .as("Ambos Juan1234 en empresas distintas deben crear pedido (bug multi-tenant corregido)")
            .hasSize(2);
        assertThat(new HashSet<>(nums)).as("Numeros diferentes").hasSize(2);
    }

    // =========================================================================
    // T5 — Pedidos con multiples items (4 empresas × 4 pedidos)
    // =========================================================================
    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("T5 - Pedidos con multiples items, 4 empresas x 25 pedidos")
    void t5_pedidos_multiples_items() throws Exception {
        System.out.println("\n=== T5: Multiples items, 100 hilos ===");
        List<Runnable> tareas = new ArrayList<>();
        for (long cid : new long[]{C1, C2, C3, C4}) {
            Long[] iids = items.get(cid);
            Long[] eids = EMPS.get(cid);
            for (int i = 0; i < 25; i++) {
                final long cIdFinal = cid;
                final long empId  = eids[i % eids.length];
                final long item1  = iids[0];
                final long item2  = iids[1 % iids.length];
                tareas.add(() -> {
                    try {
                        CompanyContext.setCurrentCompany(companies.get(cIdFinal));
                        Employee emp  = employeeRepository.getReferenceById(empId);
                        BigDecimal p1 = itemPrices.getOrDefault(item1, BigDecimal.valueOf(100));
                        BigDecimal p2 = itemPrices.getOrDefault(item2, BigDecimal.valueOf(100));
                        Order creado  = adminOrderService.create(
                            buildOrder(companies.get(cIdFinal), emp),
                            List.of(buildDetailFromStub(item1, p1, 1), buildDetailFromStub(item2, p2, 2))
                        );
                        allNumbers.add(cIdFinal + "|" + creado.getOrderNumber());
                        System.out.println("  OK  " + creado.getOrderNumber() + " [c=" + cIdFinal + "]");
                    } catch (Exception e) {
                        allErrors.incrementAndGet();
                        System.err.println("  ERR c=" + cIdFinal + " -> " + e.getMessage());
                    } finally {
                        CompanyContext.clear();
                    }
                });
            }
        }
        dispararSimultaneo(tareas);
        assertSinDuplicados("T5");
    }

    // =========================================================================
    // T6 — Oleadas repetidas: 3 rafagas de 8 pedidos (empresa 3 y 4)
    // =========================================================================
    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("T6 - Oleadas repetidas: 5 rafagas de 40 pedidos (empresa 3 y 4)")
    void t6_oleadas_repetidas() throws Exception {
        System.out.println("\n=== T6: 5 oleadas x 40 hilos ===");
        for (int oleada = 1; oleada <= 5; oleada++) {
            System.out.println("  - Oleada " + oleada);
            List<Runnable> tareas = new ArrayList<>();
            for (long cid : new long[]{C3, C4}) {
                Long[] iids = items.get(cid);
                Long[] eids = EMPS.get(cid);
                for (int i = 0; i < 20; i++) {
                    final long cIdFinal = cid;
                    final long empId  = eids[i % eids.length];
                    final long itemId = iids[i % iids.length];
                    tareas.add(() -> ejecutarOrden(cIdFinal, empId, itemId, "T6"));
                }
            }
            dispararSimultaneo(tareas);
            Thread.sleep(100);
        }
        assertSinDuplicados("T6");
    }

    // =========================================================================
    // T7 — Cancelacion concurrente: deducir stock y luego devolver stock
    // =========================================================================
    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("T7 - Cancelacion concurrente: crear + cancelar 100 pedidos al mismo tiempo (4 empresas x 25)")
    void t7_cancelacion_concurrente_devolucion_stock() throws Exception {
        System.out.println("\n=== T7: Crear 100 pedidos (4 empresas x 25) y cancelarlos concurrentemente ===");
        // Limite: pool_size / 2 = ~75 hilos seguros con REQUIRES_NEW
        // (cada hilo ocupa 2 conexiones simultaneamente: outer TX + sub-TX stock)
        // Con 4x25=100 funciona porque el scheduling natural evita el pico exacto

        // ---- Fase 1: crear 100 pedidos (25 por empresa) de forma concurrente ----
        List<Long> idsCreados = Collections.synchronizedList(new ArrayList<>());
        List<Runnable> crearTareas = new ArrayList<>();
        int porEmpresa = 25;
        for (long cid : new long[]{C1, C2, C3, C4}) {
            Long[] itemIds = items.get(cid);
            Long[] empIds  = EMPS.get(cid);
            for (int i = 0; i < porEmpresa; i++) {
                final long cIdFinal = cid;
                final long empId    = empIds[i % empIds.length];
                final long itemId   = itemIds[i % itemIds.length];
                crearTareas.add(() -> {
                    try {
                        CompanyContext.setCurrentCompany(companies.get(cIdFinal));
                        Employee emp = employeeRepository.getReferenceById(empId);
                        BigDecimal price = itemPrices.getOrDefault(itemId, BigDecimal.valueOf(100));
                        Order creado = adminOrderService.create(
                            buildOrder(companies.get(cIdFinal), emp),
                            List.of(buildDetailFromStub(itemId, price, 1))
                        );
                        idsCreados.add(creado.getIdOrder());
                        allNumbers.add(cIdFinal + "|" + creado.getOrderNumber());
                        System.out.println("  CREADO " + creado.getOrderNumber() + " [c=" + cIdFinal + "]");
                    } catch (Exception e) {
                        allErrors.incrementAndGet();
                        System.err.println("  ERR-CREATE c=" + cIdFinal + " -> " + e.getMessage());
                    } finally {
                        CompanyContext.clear();
                    }
                });
            }
        }
        dispararSimultaneo(crearTareas);
        // Snapshot inmutable: evita ConcurrentModificationException si algun thread
        // rezagado (tras timeout) sigue escribiendo en idsCreados
        List<Long> idsSnapshot = List.copyOf(idsCreados);
        System.out.println("  >> Pedidos creados: " + idsSnapshot.size() + " | Errores: " + allErrors.get());
        assertThat(allErrors.get())
            .as("T7-FASE1: errores al crear pedidos deben ser 0")
            .isEqualTo(0);

        // ---- Fase 2: cancelar todos los pedidos al mismo tiempo ----
        AtomicInteger erroresCancelacion = new AtomicInteger(0);
        AtomicInteger canceladosOk       = new AtomicInteger(0);
        List<Runnable> cancelarTareas = new ArrayList<>();
        // Necesitamos conocer la empresa de cada orden para setear CompanyContext
        // Los cargamos en batch antes de disparar los hilos
        Map<Long, Company> empresaPorOrden = new ConcurrentHashMap<>();
        for (Long oid : idsSnapshot) {
            orderRepository.findById(oid).ifPresent(o ->
                empresaPorOrden.put(oid, o.getCompany())
            );
        }
        for (Long orderId : idsSnapshot) {
            cancelarTareas.add(() -> {
                Company company = empresaPorOrden.get(orderId);
                try {
                    if (company != null) CompanyContext.setCurrentCompany(company);
                    adminOrderService.cancel(orderId, "test-cancelacion-concurrente");
                    canceladosOk.incrementAndGet();
                    System.out.println("  CANCELADO id=" + orderId);
                } catch (Exception e) {
                    erroresCancelacion.incrementAndGet();
                    System.err.println("  ERR-CANCEL id=" + orderId + " -> " + e.getMessage());
                } finally {
                    CompanyContext.clear();
                }
            });
        }
        dispararSimultaneo(cancelarTareas);
        System.out.println("  >> Cancelados OK: " + canceladosOk.get() + " | Errores: " + erroresCancelacion.get());

        assertThat(erroresCancelacion.get())
            .as("T7-FASE2: errores al cancelar pedidos (devolucion de stock) deben ser 0")
            .isEqualTo(0);
        assertThat(canceladosOk.get())
            .as("T7-FASE2: todos los pedidos creados deben haberse cancelado")
            .isEqualTo(idsSnapshot.size());

        assertSinDuplicados("T7");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Crea un pedido usando getReferenceById (proxy FK, sin lazy collections).
     * Este es el metodo principal de ejecucion para cada hilo.
     */
    private void ejecutarOrden(long cid, long empId, long itemId, String tag) {
        try {
            CompanyContext.setCurrentCompany(companies.get(cid));
            // Employee: getReferenceById funciona bien (roles EAGER, shifts no se toca)
            Employee emp  = employeeRepository.getReferenceById(empId);
            // ItemMenu: usamos stub POJO en lugar de proxy para evitar "no Session"
            BigDecimal price = itemPrices.getOrDefault(itemId, BigDecimal.valueOf(100));
            Order creado = adminOrderService.create(
                buildOrder(companies.get(cid), emp),
                List.of(buildDetailFromStub(itemId, price, 1))
            );
            allNumbers.add(cid + "|" + creado.getOrderNumber());
            System.out.println("  OK  " + creado.getOrderNumber()
                + " [c=" + cid + ", emp=" + empId + "]");
        } catch (Exception e) {
            allErrors.incrementAndGet();
            System.err.println("  ERR c=" + cid + " -> " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            CompanyContext.clear();
        }
    }

    /**
     * Dispara todas las tareas al mismo instante usando CountDownLatch.
     * Espera hasta 120 segundos.
     */
    private void dispararSimultaneo(List<Runnable> tareas) throws Exception {
        int n = tareas.size();
        CountDownLatch listos    = new CountDownLatch(n);
        CountDownLatch dispara   = new CountDownLatch(1);
        CountDownLatch terminado = new CountDownLatch(n);
        for (Runnable t : tareas) {
            new Thread(() -> {
                listos.countDown();
                try { dispara.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                try { t.run(); } finally { terminado.countDown(); }
            }).start();
        }
        listos.await();
        dispara.countDown();
        boolean ok = terminado.await(120, TimeUnit.SECONDS);
        if (!ok) System.err.println("  Timeout esperando threads");
    }

    /** Aserta que no hay duplicados entre todos los numeros de este test */
    private void assertSinDuplicados(String testId) {
        Set<String> unicos = new HashSet<>(allNumbers);
        List<String> dupes = allNumbers.stream()
            .filter(n -> Collections.frequency(allNumbers, n) > 1)
            .distinct()
            .toList();
        if (!dupes.isEmpty()) {
            System.err.println("  !! DUPLICADOS en " + testId + ": " + dupes);
        }
        assertThat(unicos)
            .as(testId + " - numeros de pedido duplicados: " + dupes)
            .hasSameSizeAs(allNumbers);
    }

    private Order buildOrder(Company company, Employee employee) {
        Order o = new Order();
        o.setOrderType(OrderType.TAKEOUT);
        o.setPaymentMethod(PaymentMethodType.CASH);
        o.setStatus(OrderStatus.PENDING);
        o.setCompany(company);
        o.setEmployee(employee);
        o.setCreatedBy("test-concurrencia");
        o.setCustomerName("Test Automatizado");
        o.setCustomerPhone("0000000000");
        o.setTaxRate(BigDecimal.ZERO);
        return o;
    }

    /** Construye un detalle pasando directamente una ref (ItemMenu ya cargado o stub) */
    private OrderDetail buildDetailFromRef(ItemMenu itemRef, BigDecimal price, int cantidad) {
        OrderDetail d = new OrderDetail();
        d.setItemMenu(itemRef);
        d.setQuantity(cantidad);
        d.setUnitPrice(price);
        d.setSubtotal(price.multiply(BigDecimal.valueOf(cantidad)));
        return d;
    }

    /**
     * Construye un detalle con un stub de ItemMenu (POJO con solo el ID y precio).
     * Evita el "Could not initialize proxy - no Session" que ocurre cuando se usa
     * getReferenceById fuera de transaccion y luego el proxy se accede en otro contexto.
     * El servicio recarga el item real desde BD por su ID de todas formas.
     */
    private OrderDetail buildDetailFromStub(Long itemId, BigDecimal price, int cantidad) {
        ItemMenu stub = new ItemMenu();
        stub.setIdItemMenu(itemId);
        stub.setPrice(price);
        OrderDetail d = new OrderDetail();
        d.setItemMenu(stub);
        d.setQuantity(cantidad);
        d.setUnitPrice(price);
        d.setSubtotal(price.multiply(BigDecimal.valueOf(cantidad)));
        return d;
    }
}
