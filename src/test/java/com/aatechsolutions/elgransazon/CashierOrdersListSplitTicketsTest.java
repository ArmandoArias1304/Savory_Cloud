package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.Payment;
import com.aatechsolutions.elgransazon.domain.entity.PaymentDetail;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.context.WebApplicationContext;
import org.thymeleaf.context.AbstractContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cashier order list shows TWO tables: "Mis Pedidos" (own) and "Pedidos Globales
 * e Historial de Cobros" (global). A split order must deploy its N per-person tickets
 * in BOTH of them (one ticket link per account), and must never offer the whole-order
 * ticket there: that one is only the table total and is not handed to a customer.
 *
 * The real template is rendered so a broken fragment/expression fails here instead of
 * in the browser.
 */
@SpringBootTest
class CashierOrdersListSplitTicketsTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private AbstractContext webContext() {
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webApplicationContext);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setContextPath("");
        SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "cajera", "n/a", List.of(new SimpleGrantedAuthority("ROLE_CASHIER"))));
        SecurityContextHolder.setContext(securityContext);
        request.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        IWebExchange exchange = JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(request, response);
        return new WebContext(exchange);
    }

    private int count(String haystack, String needle) {
        int found = 0;
        int from = 0;
        while ((from = haystack.indexOf(needle, from)) >= 0) {
            found++;
            from += needle.length();
        }
        return found;
    }

    /** A PAID order, optionally split into two accounts (one ticket each). */
    private Order paidOrder(boolean split) {
        Order order = Order.builder()
                .idOrder(76L)
                .orderNumber("ORD-20260912-076")
                .orderType(OrderType.DINE_IN)
                .status(OrderStatus.PAID)
                .paymentMethod(PaymentMethodType.CASH)
                .createdAt(LocalDateTime.of(2026, 9, 12, 14, 30))
                .total(new BigDecimal("180.00"))
                .customerName("Cliente de prueba")
                .orderDetails(new ArrayList<>())
                .build();

        if (!split) {
            return order;
        }

        List<Payment> payments = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Payment payment = Payment.builder()
                    .idPayment(100L + i)
                    .personLabel("Persona " + (i + 1))
                    .accountNumber(i + 1)
                    .paymentFolio("ORD-20260912-076-0" + (i + 1))
                    .paymentMethod(PaymentMethodType.CASH)
                    .total(new BigDecimal("90.00"))
                    .autofacturaKey("autofactura-key-" + (i + 1))
                    .selfInvoiceUrl("https://demo.local/autofactura/autofactura-key-" + (i + 1))
                    .build();
            payment.setPaymentDetails(new ArrayList<>(List.of(
                    PaymentDetail.builder()
                            .itemName("Tacos de arrachera")
                            .quantity(new BigDecimal("2.0000"))
                            .unitPrice(new BigDecimal("45.00"))
                            .total(new BigDecimal("90.00"))
                            .build())));
            payments.add(payment);
        }
        order.setPayments(payments);
        return order;
    }

    private String render(Order order) {
        return render(order, false);
    }

    private String render(Order order, boolean staffDeliveryEnabled) {
        AbstractContext ctx = webContext();
        SystemConfiguration config = SystemConfiguration.builder()
                .restaurantName("El Gran Sazon")
                .staffCanManageDeliveryOrders(true)
                .build();

        ctx.setVariable("globalSystemConfig", GlobalSystemConfig.builder().systemName("Test").build());
        ctx.setVariable("systemConfig", config);
        ctx.setVariable("config", config);
        ctx.setVariable("username", "cajera");
        ctx.setVariable("currentRole", "cashier");
        ctx.setVariable("tables", List.<RestaurantTable>of());
        ctx.setVariable("statuses", OrderStatus.values());
        ctx.setVariable("orderTypes", OrderType.values());
        ctx.setVariable("myOrders", List.of(order));
        ctx.setVariable("unpaidOrders", List.of(order));
        ctx.setVariable("currentPage", 1);
        ctx.setVariable("totalPages", 1);
        ctx.setVariable("totalElements", 1);
        ctx.setVariable("pageSize", 15);
        ctx.setVariable("globalCurrentPage", 1);
        ctx.setVariable("globalTotalPages", 1);
        ctx.setVariable("globalTotalElements", 1);
        ctx.setVariable("pendingCount", 0L);
        ctx.setVariable("inPreparationCount", 0L);
        ctx.setVariable("paidCount", 1L);
        ctx.setVariable("myPendingCount", 0L);
        ctx.setVariable("unpaidCount", 0L);
        ctx.setVariable("paidOrdersCount", 1L);
        ctx.setVariable("unpaidTotal", BigDecimal.ZERO);
        ctx.setVariable("todayRevenue", BigDecimal.ZERO);
        ctx.setVariable("myCollectedRevenue", BigDecimal.ZERO);
        ctx.setVariable("myOwnRevenue", BigDecimal.ZERO);
        ctx.setVariable("othersCollectedRevenue", BigDecimal.ZERO);
        ctx.setVariable("isRestaurantOpen", true);
        ctx.setVariable("staffOrderStatusEnabled", false);
        ctx.setVariable("staffChefEnabled", false);
        ctx.setVariable("staffBaristaEnabled", false);
        ctx.setVariable("staffParrilleroEnabled", false);
        ctx.setVariable("staffDeliveryEnabled", staffDeliveryEnabled);
        ctx.setVariable("waiterDeliveryCanCollect", true);
        ctx.setVariable("printTicketOrderId", null);
        ctx.setVariable("printTicketOrderIds", List.of());

        return templateEngine.process("cashier/orders/list", ctx);
    }

    @Test
    void splitOrderDeploysItsTicketsInBothTables() {
        String html = render(paidOrder(true));

        // One "Ver N ticket(s) por persona" toggle per table (own + global)
        assertEquals(2, count(html, "ticket(s) por persona"),
                "el acordeón de tickets por persona debe salir en la tabla propia y en la global");
        // Each account offers its ticket once per table (own + global)
        assertEquals(2, count(html, "/cashier/orders/76/download-ticket/100"),
                "la cuenta 1 debe ofrecer su ticket en ambas tablas");
        assertEquals(2, count(html, "/cashier/orders/76/download-ticket/101"),
                "la cuenta 2 debe ofrecer su ticket en ambas tablas");
        // The accounts themselves are listed with their folio in both tables
        assertEquals(2, count(html, "ORD-20260912-076-01"), "folio de la cuenta 1 en ambas tablas");
        assertEquals(2, count(html, "ORD-20260912-076-02"), "folio de la cuenta 2 en ambas tablas");
        // The purple autofactura button of each account must be there too, in both tables
        assertEquals(2, count(html, "/autofactura/autofactura-key-1"),
                "la autofactura de la cuenta 1 debe ofrecerse en ambas tablas");
        assertEquals(2, count(html, "/autofactura/autofactura-key-2"),
                "la autofactura de la cuenta 2 debe ofrecerse en ambas tablas");
        // No whole-order ticket: it is the table total and is not handed to a customer
        assertFalse(html.contains("/cashier/orders/76/download-ticket\""),
                "una orden dividida no debe ofrecer el ticket del pedido completo");
    }

    @Test
    void regularOrderStillOffersTheWholeOrderTicket() {
        String html = render(paidOrder(false));

        assertFalse(html.contains("ticket(s) por persona"),
                "sin cuentas no hay acordeón de tickets por persona");
        assertTrue(html.contains("/cashier/orders/76/download-ticket\""),
                "un cobro normal sí ofrece el ticket del pedido completo");
    }

    /**
     * Both tables render the actions column from the shared fragment (fragments/order-actions),
     * tagged with .order-actions-cell, and the page rebuilds ONE row in place: on a
     * STATUS_CHANGE notification from the WebSocket and on the cashier's own delivery one-click,
     * the column is re-rendered by the server instead of reloading the whole page.
     */
    @Test
    void actionsColumnIsRefreshedInPlaceFromTheServer() {
        String html = render(paidOrder(false));

        // Una celda de acciones por tabla (Mis Pedidos + global), del mismo fragmento
        assertEquals(2, count(html, "class=\"order-actions-cell"),
                "las dos tablas deben marcar su columna de acciones para poder refrescarla");
        // El refresco localiza esa columna en las filas del pedido...
        assertTrue(html.contains("tr[data-order-id=\"${orderId}\"] .order-actions-cell"),
                "el refresco debe buscar la columna de acciones de la fila");
        // ...y pide al servidor la columna de ESA fila...
        assertTrue(html.contains("/cashier/orders/${orderId}/actions"),
                "el refresco debe pedir la columna ya renderizada por el servidor");
        // ...vuelve a enlazar los botones nuevos (los recién creados no traen listeners)...
        assertTrue(html.contains("function bindOrderActionButtons"),
                "los botones re-renderizados deben volver a enlazarse");

        // ...y se usa al llegar un aviso STATUS_CHANGE por WebSocket, no solo el semáforo
        int statusChangeBranch = html.indexOf("if (type === \"STATUS_CHANGE\") {");
        assertTrue(statusChangeBranch > 0, "el handler de WebSocket debe seguir tratando STATUS_CHANGE");
        assertTrue(html.substring(statusChangeBranch, statusChangeBranch + 600)
                        .contains("refreshOrderActions(orderId)"),
                "un STATUS_CHANGE por WebSocket debe refrescar la columna de acciones de la fila");
    }

    /**
     * El avance de reparto del cajero (En camino -> Entregado) ya no recarga la página: refresca
     * el semáforo y la columna de acciones, y ahí es donde aparece el botón de cobrar.
     */
    @Test
    void cashierDeliveryOneClickRefreshesTheRowWithoutReloading() {
        String html = render(onTheWayDeliveryOrder(), true);

        int advance = html.indexOf("function advanceDeliveryStatus");
        assertTrue(advance > 0, "el flujo de reparto del cajero debe seguir ahí");
        String flow = html.substring(advance, html.indexOf("function ", advance + 10));
        assertTrue(flow.contains("refreshOrderActions(orderId)"),
                "al marcar ENTREGADO debe refrescar la columna (ahí nace el botón de cobrar)");
        assertTrue(flow.contains("updateRowStatusBadge(orderId, nextStatus)"),
                "y también el semáforo de la fila");
        assertFalse(flow.contains("window.location.reload"),
                "el avance de reparto ya no debe recargar la página");
    }

    /**
     * A DELIVERY order that is already on its way must keep its row (and therefore the
     * "Entregado" button) in BOTH tables, so the staff permission can finish the delivery.
     */
    @Test
    void onTheWayDeliveryOrderKeepsItsAdvanceButton() {
        String html = render(onTheWayDeliveryOrder(), true);

        assertTrue(html.contains("ORD-20260912-090"), "la fila del reparto debe seguir en la lista");
        assertTrue(html.contains("En camino"), "el estado EN CAMINO debe verse en las dos tablas");
        // Un botón por tabla + el listener JS del final de la página
        assertEquals(3, count(html, "btn-advance-delivery"),
                "cada tabla debe ofrecer el botón de avanzar el reparto");
        assertEquals(2, count(html, "data-next-status=\"DELIVERED\""),
                "desde EN CAMINO el siguiente paso ofrecido debe ser ENTREGADO");
    }

    private Order onTheWayDeliveryOrder() {
        return Order.builder()
                .idOrder(90L)
                .orderNumber("ORD-20260912-090")
                .orderType(OrderType.DELIVERY)
                .status(OrderStatus.ON_THE_WAY)
                .paymentMethod(PaymentMethodType.CASH)
                .createdAt(LocalDateTime.of(2026, 9, 12, 15, 0))
                .total(new BigDecimal("150.00"))
                .customerName("Cliente domicilio")
                .orderDetails(new ArrayList<>())
                .build();
    }

    @Test
    void splitOrderRendersTheWholeTemplate() {
        String html = render(paidOrder(true));

        assertNotNull(html);
        // Sanity check: both sections of the cashier list are present
        assertTrue(html.contains("Pedidos Globales e Historial de Cobros"));
        assertTrue(Arrays.stream(html.split("payments-detail")).count() > 1,
                "las filas de cuentas se repiten para cada tabla");
    }
}
