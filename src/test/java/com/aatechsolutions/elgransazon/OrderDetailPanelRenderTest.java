package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetailComplement;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.Payment;
import com.aatechsolutions.elgransazon.domain.entity.PaymentDetail;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.infrastructure.thymeleaf.TimezoneDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El panel de detalle del pedido es lo que el cajero ve al pulsar una tarjeta de la lista de
 * pedidos: lo sirve GET /cashier/orders/{id}/detail-panel (fragments/order-detail-panel) y debe
 * mostrar lo que no cabe en la fila de la tabla —los ítems consumidos, con sus notas y
 * complementos, y las cuentas por persona con su ticket y su autofactura— sin dejar de ofrecer
 * las MISMAS acciones de la fila.
 *
 * <p>Se renderiza la plantilla real con el nombre de vista exacto que devuelve el controlador,
 * sin levantar Spring (no hay MySQL en el entorno de pruebas): el resolver lee {@code templates/}
 * del classpath y el contexto web resuelve las URLs {@code @{...}}.</p>
 */
class OrderDetailPanelRenderTest {

    /** El nombre de vista que devuelve CashierController.orderDetailPanel(). */
    private static final String PANEL = "fragments/order-detail-panel";

    private static final Set<String> PANEL_FROM_MODEL = Set.of("panelFromModel");

    private TemplateEngine engine;
    private Company company;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setDialect(new SpringStandardDialect());
        engine.addDialect(new TimezoneDialect());

        company = new Company();
        company.setIdCompany(1L);
        company.setName("Restaurante de prueba");
        company.setTimezone("America/Mexico_City");
        CompanyContext.setCurrentCompany(company);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void laCabeceraIdentificaLaMesaElPedidoYElEstado() {
        Order order = orderedAtTable(7);
        order.setCustomerName("Ana López");

        String html = renderPanel(order);

        assertTrue(html.contains("ORD-20260929-001"), "número del pedido");
        assertTrue(html.contains(">Mesa</span") && html.contains(">7</span>"),
                "mesa del pedido, en su propia caja");
        assertTrue(html.contains("Entregado"), "estado del pedido");
        assertTrue(html.contains("$250.00"), "total del pedido");
        assertTrue(html.contains("Ana López"), "cliente");
        assertTrue(html.contains("Atendido por"), "quién atiende la mesa");
    }

    @Test
    void losItemsConsumidosSalenConSuCantidadSuNotaYSuComplemento() {
        Order order = orderedAtTable(7);

        OrderDetail tacos = detail("Tacos al pastor", 2, "50.00");
        tacos.setComments("sin cebolla");
        OrderDetailComplement queso = new OrderDetailComplement();
        queso.setComplementName("Queso extra");
        queso.setQuantity(1);
        queso.setUnitPrice(new BigDecimal("15.00"));
        tacos.addComplement(queso);
        order.addOrderDetail(tacos);

        String html = renderPanel(order);

        assertRenderedText(html, "Ítems consumidos");
        assertTrue(html.contains("Tacos al pastor"), "nombre del ítem");
        assertTrue(html.contains("sin cebolla"), "la nota del comensal es lo que cocina ve en papel");
        assertTrue(html.contains("Queso extra"), "complementos elegidos");
        assertTrue(html.contains("$100.00"), "importe del ítem");
    }

    @Test
    void elPanelAvisaDelCobroParcialPorPersona() {
        Order order = orderedAtTable(7);
        OrderDetail tacos = detail("Tacos al pastor", 2, "50.00");
        tacos.setPaidQuantity(new BigDecimal("1"));
        order.addOrderDetail(tacos);

        String html = renderPanel(order);

        assertTrue(html.contains("Cobro parcial por persona"), "el pedido se cobró por partes");
        assertTrue(html.contains("Cobradas 1 de 2"), "unidades ya cobradas de cada línea");
        assertTrue(html.contains("Falta:"), "lo que queda por cobrar");
    }

    @Test
    void lasCuentasPorPersonaLlevanSuConsumoTicketYAutofactura() {
        Order order = orderedAtTable(7);
        order.addOrderDetail(detail("Tacos al pastor", 2, "50.00"));
        addPayment(order, payment(11L, "Ana", 1, "PAY-0001", "90.00", "clave-ana", "Cerveza"));
        addPayment(order, payment(12L, "Luis", 2, "PAY-0002", "60.00", "clave-luis", "Tacos"));

        String html = renderPanel(order);

        assertRenderedText(html, "Cuentas por persona");
        assertTrue(html.contains("PAY-0001") && html.contains("PAY-0002"), "folio de cada cuenta");
        assertTrue(html.contains("Ana") && html.contains("Luis"), "a nombre de quién va cada cuenta");
        assertRenderedText(html, "Consumió");
        assertTrue(html.contains("Cerveza"), "producto consumido por esa persona");
        assertTrue(html.contains("/autofactura/clave-ana"), "autofactura de la cuenta");
        assertTrue(html.contains("/cashier/orders/77/download-ticket/11"),
                "ticket de la cuenta (cada persona se lleva el suyo)");
    }

    @Test
    void elAcordeonDeLasListasSiguePintandoLaCuenta() {
        Order order = orderedAtTable(7);
        order.addOrderDetail(detail("Tacos al pastor", 2, "50.00"));
        addPayment(order, payment(11L, "Ana", 1, "PAY-0001", "90.00", "clave-ana", "Cerveza"));
        addPayment(order, payment(12L, "Luis", 2, "PAY-0002", "60.00", "clave-luis", "Tacos"));

        // El acordeón de las listas (tablas) y la tarjeta del panel comparten el contenido de
        // UNA cuenta: si la extracción se rompiera, las listas se quedarían sin folio, sin
        // consumo y sin ticket por persona.
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("order", order);
        variables.put("role", "cashier");
        String html = process("fragments/split-tickets", variables, Set.of("accounts"));

        assertTrue(html.contains("ticket(s) por persona"), "el botón que despliega las cuentas");
        assertTrue(html.contains("PAY-0001") && html.contains("PAY-0002"), "folio de cada cuenta");
        assertTrue(html.contains("Consumió</p>") || flat(html).contains("Consumió </p>"),
                "lo que consumió cada persona");
        assertTrue(html.contains("/cashier/orders/77/download-ticket/11"), "ticket por persona");
        assertTrue(html.contains("/autofactura/clave-ana"), "autofactura por persona");
    }

    @Test
    void unPedidoSinCuentasDivididasNoMuestraLaSeccionDeCuentas() {
        Order order = orderedAtTable(7);
        order.addOrderDetail(detail("Tacos al pastor", 2, "50.00"));

        String html = renderPanel(order);

        assertFalse(flat(html).contains("Cuentas por persona </p>"), "no hay cuentas que mostrar");
        assertFalse(flat(html).contains("Consumió </p>"), "ni consumo por persona");
        assertFalse(html.contains("/autofactura/"), "ni autofactura de cuenta");
    }

    @Test
    void laMesaYElNumeroDePedidoSonLoMasVisible() {
        Order order = orderedAtTable(7);

        String html = renderPanel(order);

        // El cajero busca el pedido por la mesa y lo confirma por el número: los dos van en
        // grande, en su propia caja, y no como una línea secundaria de la cabecera.
        assertTrue(html.contains("text-2xl sm:text-4xl font-extrabold"),
                "el número de mesa se renderiza en grande");
        assertTrue(html.contains(">7</span>"), "y es el número de la mesa del pedido");
        assertTrue(html.contains(">Mesa</span"), "la caja dice que ese número es la mesa");
        assertTrue(html.contains("text-2xl sm:text-3xl font-extrabold"),
                "el número de pedido también va en grande");
        assertTrue(html.contains(order.getOrderNumber()), "con el número completo a la vista");

        // En celular la caja de la mesa se encoge y el estado con el total pasan a una sola
        // línea: así el número de pedido, lo más largo de la cabecera, cabe sin partirse.
        assertTrue(html.contains("h-16 w-16 sm:h-24 sm:w-24"),
                "la caja de la mesa se encoge en celular y crece en pantallas anchas");
        assertTrue(html.contains("gap-x-4 gap-y-3"),
                "la cabecera reparte mesa/pedido y estado/total sin amontonarlos");
        assertTrue(html.contains("shrink-0 text-left sm:text-right"),
                "el estado y el total se apilan a la derecha solo cuando hay espacio");
        assertTrue(html.contains("flex items-center gap-3 sm:block"),
                "en celular el estado y el total comparten línea");
        assertTrue(html.contains("order-last w-full min-w-0 sm:order-none sm:w-auto sm:flex-1"),
                "el número de pedido baja a su propia línea completa en celular, para verse entero");

        order.setTable(null);
        order.setOrderType(OrderType.DELIVERY);
        String sinMesa = renderPanel(order);
        assertTrue(sinMesa.contains("Sin mesa"),
                "un pedido sin mesa (reparto) no muestra un número de mesa vacío");
    }

    @Test
    void lasAccionesDelPanelSonParaElDedo() {
        String html = renderPanel(orderedAtTable(7));

        // Los botones del panel se agrandan y se separan más que en la fila de la tabla, y
        // vuelven a crecer en pantallas anchas: son blancos táctiles, no una celda de 10 columnas.
        assertTrue(html.contains("order-detail-actions-box"),
                "las acciones del panel llevan su propio contenedor");
        assertTrue(html.contains(".order-detail-actions-box > div > a"),
                "se ajusta el tamaño de los botones del fragmento compartido");
        assertTrue(html.contains("padding: 0.625rem"), "más área de toque en móvil");
        assertTrue(html.contains("font-size: 1.0625rem"), "y el icono más grande");
        assertTrue(html.contains("@media (min-width: 640px)"),
                "con un tamaño mayor cuando la pantalla lo permite");
        assertTrue(html.contains("flex-wrap: wrap"), "y se acomodan si no caben en una fila");
    }

    @Test
    void elPanelOfreceLasMismasAccionesDeLaFila() {
        Order order = orderedAtTable(7);
        OrderDetail tacos = detail("Tacos al pastor", 2, "50.00");
        tacos.setItemStatus(OrderStatus.DELIVERED);
        order.addOrderDetail(tacos);

        String html = renderPanel(order);

        assertRenderedText(html, "Acciones del pedido");
        assertTrue(html.contains("/cashier/orders/view/77"), "ver el pedido completo");
        assertTrue(html.contains("/cashier/payments/form/77"), "cobrar el pedido entregado");
        assertTrue(html.contains("split=items"),
                "cobrar solo lo entregado a la persona que se va");
    }

    // ================================================================
    // Utilidades
    // ================================================================

    /** HTML con los espacios colapsados: la plantilla va indentada y el texto renderizado
     *  puede quedar separado de su etiqueta de cierre por un salto de línea. */
    private static String flat(String html) {
        return html.replaceAll("\\s+", " ");
    }

    /**
     * El texto debe salir RENDERIZADO y no solo dentro de un comentario de la plantilla (los
     * comentarios viajan al HTML, así que buscar la frase suelta daría un falso positivo).
     */
    private static void assertRenderedText(String html, String text) {
        assertTrue(flat(html).contains(text + " </p>"),
                () -> "debe renderizarse el texto \"" + text + "\"");
    }

    private String renderPanel(Order order) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("order", order);
        variables.put("currentRole", "cashier");
        variables.put("staffOrderStatusEnabled", false);
        variables.put("staffChefEnabled", false);
        variables.put("staffBaristaEnabled", false);
        variables.put("staffParrilleroEnabled", false);
        variables.put("staffDeliveryEnabled", false);
        return process(PANEL, variables, PANEL_FROM_MODEL);
    }

    /**
     * @param selectors fragmentos a renderizar; vacío para procesar la plantilla completa
     *                  (los {@code th:fragment} se renderizan igual, en su sitio).
     */
    private String process(String template, Map<String, Object> variables, Set<String> selectors) {
        JakartaServletWebApplication application = JakartaServletWebApplication
                .buildApplication(new MockServletContext());
        WebContext context = new WebContext(
                application.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse()));
        context.setVariables(variables);
        return selectors.isEmpty()
                ? engine.process(template, context)
                : engine.process(template, selectors, context);
    }

    /** Pedido ENTREGADO, atendido por un mesero, en la mesa indicada. */
    private Order orderedAtTable(int tableNumber) {
        RestaurantTable table = new RestaurantTable();
        table.setId(3L);
        table.setTableNumber(tableNumber);

        Order order = new Order();
        order.setIdOrder(77L);
        order.setOrderNumber("ORD-20260929-001");
        order.setStatus(OrderStatus.DELIVERED);
        order.setOrderType(OrderType.DINE_IN);
        order.setPaymentMethod(PaymentMethodType.CASH);
        order.setCompany(company);
        order.setTable(table);
        order.setEmployee(employee(2L, "meseroA", "Juan", "Perez"));
        order.setCreatedBy("meseroA");
        order.setTotal(new BigDecimal("250.00"));
        order.setTaxRate(new BigDecimal("16"));
        order.setCreatedAt(LocalDateTime.of(2026, 9, 29, 14, 30));
        return order;
    }

    private OrderDetail detail(String name, int quantity, String unitPrice) {
        OrderDetail detail = new OrderDetail();
        detail.setIdOrderDetail(55L);
        detail.setItemName(name);
        detail.setQuantity(quantity);
        detail.setUnitPrice(new BigDecimal(unitPrice));
        detail.setSubtotal(new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(quantity)));
        detail.setItemStatus(OrderStatus.DELIVERED);
        return detail;
    }

    /** Una cuenta por persona, con el consumo que esa persona se llevó. */
    private Payment payment(Long id, String personLabel, int accountNumber, String folio,
                            String total, String autofacturaKey, String consumedItem) {
        Payment payment = new Payment();
        payment.setIdPayment(id);
        payment.setCompany(company);
        payment.setPersonLabel(personLabel);
        payment.setAccountNumber(accountNumber);
        payment.setPaymentFolio(folio);
        payment.setPaymentMethod(PaymentMethodType.CASH);
        payment.setTotal(new BigDecimal(total));
        payment.setAutofacturaKey(autofacturaKey);

        PaymentDetail consumed = new PaymentDetail();
        consumed.setItemName(consumedItem);
        consumed.setQuantity(BigDecimal.ONE);
        consumed.setUnitPrice(new BigDecimal(total));
        consumed.setSubtotal(new BigDecimal(total));
        consumed.setTotal(new BigDecimal(total));
        payment.addPaymentDetail(consumed);

        return payment;
    }

    /** El pedido recibe la cuenta como lo hace el flujo de cobro: por el lado del pedido. */
    private void addPayment(Order order, Payment payment) {
        payment.setOrder(order);
        order.getPayments().add(payment);
    }

    private static Employee employee(long id, String username, String nombre, String apellido) {
        Employee employee = new Employee();
        employee.setIdEmpleado(id);
        employee.setUsername(username);
        employee.setNombre(nombre);
        employee.setApellido(apellido);
        employee.setEnabled(true);
        return employee;
    }
}
