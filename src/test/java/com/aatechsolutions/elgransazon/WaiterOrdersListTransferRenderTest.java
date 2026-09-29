package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La lista del mesero debe ofrecer el botón de transferencia solo al mesero que atiende el
 * pedido, marcar con badges los pedidos transferidos / con transferencia pendiente y dejar
 * en solo lectura los pedidos que el creador ya entregó a otro mesero.
 *
 * <p>Se renderiza la plantilla real sin levantar Spring (no hay MySQL en el entorno de
 * pruebas): el resolver de Thymeleaf lee {@code templates/} del classpath y el contexto web
 * resuelve las URLs {@code @{...}}.</p>
 */
class WaiterOrdersListTransferRenderTest {

    private static final String CURRENT_USER = "meseroA";

    private TemplateEngine engine;
    private Company company;
    private Employee waiterA;
    private Employee waiterB;

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
        // Same dialect the application uses (SpEL + #tz), so the template is evaluated
        // exactly as it is in production, just without the Spring context.
        engine.setDialect(new SpringStandardDialect());
        engine.addDialect(new TimezoneDialect());

        company = new Company();
        company.setIdCompany(1L);
        company.setName("Restaurante de prueba");
        company.setTimezone("America/Mexico_City");
        CompanyContext.setCurrentCompany(company);

        waiterA = employee(2L, "meseroA", "Juan", "Perez");
        waiterB = employee(3L, "meseroB", "Luis", "Gomez");
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    // El script de la página menciona la clase del botón para enganchar el click, así que
    // el botón RENDERIZADO se detecta por la clase completa del elemento.
    private static final String TRANSFER_BUTTON = "btn-transfer-order p-2";

    @Test
    void theWaiterWhoAttendsTheOrderCanTransferIt() {
        String html = render(List.of(orderOwnedByMe(OrderStatus.READY)));

        assertTrue(html.contains(TRANSFER_BUTTON), "debe mostrar el botón de transferir");
        assertTrue(html.contains("/waiter/orders/view/77"), "la fila del pedido se renderizó");
    }

    @Test
    void theTransferButtonIsNotConfusableWithTheOtherActions() {
        String html = render(List.of(orderOwnedByMe(OrderStatus.READY)));

        assertTrue(html.contains("order-transfer-icon"),
                "el botón lleva su propio icono (dos meseros y el pedido que pasa)");
        assertFalse(html.contains("fa-right-left"),
                "no comparte el glifo doble flecha del botón cambiar estado");
        assertTrue(html.contains("bg-violet-100"),
                "usa un color que ninguna otra acción de la fila ocupa");
        assertFalse(html.contains("btn-transfer-order p-2 rounded-lg bg-indigo-50"),
                "ya no se ve igual que \"avanzar preparación\" (índigo)");
    }

    @Test
    void aTransferredAwayOrderBecomesReadOnlyForItsCreator() {
        Order order = orderOwnedBy(waiterB, OrderStatus.DELIVERED);
        order.setCreatedBy(CURRENT_USER);
        order.setTransferredFrom(waiterA);
        order.setTransferredAt(LocalDateTime.of(2026, 9, 28, 14, 30));

        String html = render(List.of(order));

        assertTrue(html.contains("order-transferred-badge"), "badge de pedido transferido");
        assertTrue(html.contains("rounded-full text-xs font-bold bg-violet-100 text-violet-800"),
                "el badge usa el mismo color de la transferencia");
        assertTrue(html.contains("<path d=\"M13 21a4.6 4.6 0 0 1 9.2 0\""),
                "el badge usa el icono propio de transferencia (dos meseros y la flecha)");
        assertTrue(html.contains("Transferido de Juan Perez a Luis Gomez el 28/09/2026 14:30"),
                "leyenda de trazabilidad");
        assertTrue(html.contains("order-readonly-hint"), "el creador solo puede consultarlo");
        assertFalse(html.contains(TRANSFER_BUTTON), "ya no puede volver a transferirlo");
    }

    @Test
    void theOwningWaiterIsNotRestrictedAndSeesNoTransferBadge() {
        String html = render(List.of(orderOwnedByMe(OrderStatus.IN_PREPARATION)));

        assertFalse(html.contains("order-readonly-hint"), "el dueño sí puede operar el pedido");
        assertFalse(html.contains("order-transferred-badge"), "no viene de otro mesero");
        assertTrue(html.contains("/waiter/orders/edit/77") || html.contains("/waiter/orders/view/77"),
                "las acciones del dueño siguen visibles");
    }

    @Test
    void aPendingTransferIsShownUntilTheTargetAnswers() {
        Order order = orderOwnedByMe(OrderStatus.READY);
        order.setTransferRequestedTo(waiterB);
        order.setTransferRequestedAt(LocalDateTime.now());

        String html = render(List.of(order));

        assertTrue(html.contains("order-pending-transfer-badge"), "badge de transferencia pendiente");
        assertTrue(html.contains("Esperando que Luis Gomez acepte la transferencia"),
                "el tooltip dice a quién se le pidió");
        assertTrue(html.contains(TRANSFER_BUTTON), "aún se puede re-solicitar a otro mesero");
    }

    @Test
    void theTransferDataReachesThePageAsJavascript() {
        String html = render(List.of(orderOwnedByMe(OrderStatus.READY)));

        assertTrue(html.contains("const waiterColleagues = {\"3\":\"Luis Gomez\"}"),
                "el selector de compañeros se serializa al JavaScript");
        assertTrue(html.contains("\"orderNumber\":\"ORD-20260928-099\""),
                "las solicitudes pendientes viajan al JavaScript");
    }

    @Test
    void theOrderViewsShowWhoAttendsItAndTheTransferTrail() {
        Order order = orderOwnedBy(waiterB, OrderStatus.DELIVERED);
        order.setCreatedBy(CURRENT_USER);
        order.setTransferredFrom(waiterA);
        order.setTransferredAt(LocalDateTime.of(2026, 9, 28, 14, 30));

        for (String template : List.of("waiter/orders/view", "admin/orders/view", "cashier/orders/view")) {
            String html = renderView(template, order);
            assertTrue(html.contains("Atendido por:"), template + ": etiqueta del mesero a cargo");
            assertTrue(html.contains("Luis Gomez"), template + ": nombre del mesero a cargo");
            assertTrue(html.contains("Transferido de Juan Perez a Luis Gomez el 28/09/2026 14:30"),
                    template + ": leyenda de trazabilidad");
            assertTrue(html.contains("font-semibold bg-violet-100 text-violet-800"),
                    template + ": la píldora usa el color de la transferencia");
            assertTrue(html.contains(CURRENT_USER), template + ": \"creado por\" se conserva");
        }
    }

    @Test
    void paidOrdersOfferNoTransferButton() {
        Order order = orderOwnedByMe(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order.setPaidBy(waiterA);

        String html = render(List.of(order));

        assertFalse(html.contains(TRANSFER_BUTTON), "un pedido pagado ya no se transfiere");
    }

    private String render(List<Order> orders) {
        GlobalSystemConfig globalConfig = new GlobalSystemConfig();
        globalConfig.setSystemName("El Gran Sazón");

        SystemConfiguration config = new SystemConfiguration();
        config.setCompany(company);
        config.setRestaurantName("El Gran Sazón");
        config.setEnableOrderStatusPermission(false);

        Map<String, String> colleagues = new LinkedHashMap<>();
        colleagues.put(String.valueOf(waiterB.getIdEmpleado()), waiterB.getFullName());

        List<Map<String, Object>> pendingAlerts = new ArrayList<>();
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("notificationType", "TRANSFER_REQUEST");
        alert.put("orderId", 99L);
        alert.put("orderNumber", "ORD-20260928-099");
        alert.put("tableNumber", 4);
        alert.put("total", new BigDecimal("350.00"));
        alert.put("fromName", "Pedro Ruiz");
        alert.put("message", "Pedro Ruiz quiere transferirte el pedido #ORD-20260928-099");
        pendingAlerts.add(alert);

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("globalSystemConfig", globalConfig);
        variables.put("systemConfig", config);
        variables.put("orders", orders);
        variables.put("statuses", OrderStatus.values());
        variables.put("orderTypes", OrderType.values());
        variables.put("paymentMethods", PaymentMethodType.values());
        variables.put("tables", List.<RestaurantTable>of());
        variables.put("currentRole", "waiter");
        variables.put("currentUsername", CURRENT_USER);
        variables.put("currentPage", 1);
        variables.put("totalPages", 1);
        variables.put("totalElements", orders.size());
        variables.put("pageSize", 15);
        variables.put("paidCount", 0L);
        variables.put("pendingCount", 0L);
        variables.put("inPreparationCount", 0L);
        variables.put("staffOrderStatusEnabled", false);
        variables.put("staffChefEnabled", false);
        variables.put("staffBaristaEnabled", false);
        variables.put("staffParrilleroEnabled", false);
        variables.put("staffDeliveryEnabled", false);
        variables.put("waiterDeliveryCanCollect", false);
        variables.put("waiterCanCollect", false);
        variables.put("isRestaurantOpen", true);
        variables.put("waiterColleagues", colleagues);
        variables.put("pendingTransferRequests", pendingAlerts);

        return processTemplate("waiter/orders/list", variables);
    }

    private String renderView(String template, Order order) {
        GlobalSystemConfig globalConfig = new GlobalSystemConfig();
        globalConfig.setSystemName("El Gran Sazón");

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("globalSystemConfig", globalConfig);
        variables.put("order", order);
        variables.put("orderDetails", order.getOrderDetails());
        return processTemplate(template, variables);
    }

    private String processTemplate(String template, Map<String, Object> variables) {
        JakartaServletWebApplication application = JakartaServletWebApplication
                .buildApplication(new MockServletContext());
        WebContext context = new WebContext(
                application.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse()));
        context.setVariables(variables);
        return engine.process(template, context);
    }

    private Order orderOwnedByMe(OrderStatus status) {
        return orderOwnedBy(waiterA, status);
    }

    private Order orderOwnedBy(Employee owner, OrderStatus status) {
        Order order = new Order();
        order.setIdOrder(77L);
        order.setOrderNumber("ORD-20260928-001");
        order.setStatus(status);
        order.setOrderType(OrderType.DINE_IN);
        order.setPaymentMethod(PaymentMethodType.CREDIT_CARD);
        order.setCompany(company);
        order.setEmployee(owner);
        order.setCreatedBy(owner.getUsername());
        order.setTotal(new BigDecimal("250.00"));
        order.setTaxRate(new BigDecimal("16"));
        order.setCreatedAt(LocalDateTime.now());
        return order;
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
