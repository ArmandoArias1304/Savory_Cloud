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
        ctx.setVariable("staffDeliveryEnabled", false);
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
