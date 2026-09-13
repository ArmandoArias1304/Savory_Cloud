package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.Payment;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The waiter must see the tip left on their orders: a column in the orders list and
 * a card in the order detail. Both templates are rendered with the real engine so a
 * broken expression fails here instead of in the browser.
 */
@SpringBootTest
class WaiterTipVisibilityTest {

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
                "mesero", "n/a", List.of(new SimpleGrantedAuthority("ROLE_WAITER"))));
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

    private Order orderWithTip(BigDecimal tip, boolean split) {
        OrderDetail detail = OrderDetail.builder()
                .itemName("Tacos de arrachera")
                .quantity(2)
                .unitPrice(new BigDecimal("90.00"))
                .subtotal(new BigDecimal("180.00"))
                .build();

        Order order = Order.builder()
                .idOrder(76L)
                .orderNumber("ORD-20260912-076")
                .orderType(OrderType.DINE_IN)
                .status(OrderStatus.PAID)
                .paymentMethod(PaymentMethodType.CASH)
                .createdAt(LocalDateTime.of(2026, 9, 12, 14, 30))
                .subtotal(new BigDecimal("180.00"))
                .taxAmount(new BigDecimal("24.83"))
                .taxRate(new BigDecimal("16.00"))
                .total(new BigDecimal("180.00"))
                .tip(tip)
                .customerName("Cliente de prueba")
                .customerPhone("4421234567")
                .orderDetails(new ArrayList<>(List.of(detail)))
                .build();

        if (split) {
            Payment first = Payment.builder()
                    .personLabel("Ana")
                    .accountNumber(1)
                    .paymentFolio("ORD-20260912-076-01")
                    .paymentMethod(PaymentMethodType.CASH)
                    .total(new BigDecimal("90.00"))
                    .tip(new BigDecimal("30.00"))
                    .build();
            Payment second = Payment.builder()
                    .personLabel("Luis")
                    .accountNumber(2)
                    .paymentFolio("ORD-20260912-076-02")
                    .paymentMethod(PaymentMethodType.CASH)
                    .total(new BigDecimal("90.00"))
                    .tip(new BigDecimal("15.50"))
                    .build();
            order.setPayments(new ArrayList<>(List.of(first, second)));
        }
        return order;
    }

    private void baseFlags(AbstractContext ctx, Order order) {
        SystemConfiguration config = SystemConfiguration.builder()
                .restaurantName("El Gran Sazon")
                .taxRate(new BigDecimal("16.00"))
                .build();

        ctx.setVariable("globalSystemConfig", GlobalSystemConfig.builder().systemName("Test").build());
        ctx.setVariable("systemConfig", config);
        ctx.setVariable("config", config);
        ctx.setVariable("username", "mesero");
        ctx.setVariable("currentRole", "waiter");
        ctx.setVariable("role", "Mesero");
        ctx.setVariable("order", order);
        ctx.setVariable("orderDetails", order.getOrderDetails());
        ctx.setVariable("tables", List.<RestaurantTable>of());
        ctx.setVariable("statuses", OrderStatus.values());
        ctx.setVariable("orderTypes", OrderType.values());
        ctx.setVariable("isRestaurantOpen", true);
        ctx.setVariable("waiterDeliveryCanCollect", true);
        ctx.setVariable("staffOrderStatusEnabled", false);
        ctx.setVariable("staffChefEnabled", false);
        ctx.setVariable("staffBaristaEnabled", false);
        ctx.setVariable("staffParrilleroEnabled", false);
    }

    private void listFlags(AbstractContext ctx, Order order) {
        baseFlags(ctx, order);
        ctx.setVariable("orders", List.of(order));
        ctx.setVariable("currentPage", 1);
        ctx.setVariable("totalPages", 1);
        ctx.setVariable("totalElements", 1);
        ctx.setVariable("pageSize", 10);
        ctx.setVariable("pendingCount", 0L);
        ctx.setVariable("inPreparationCount", 0L);
        ctx.setVariable("paidCount", 1L);
        ctx.setVariable("companyId", 1L);
        ctx.setVariable("date", null);
        ctx.setVariable("type", null);
        ctx.setVariable("status", null);
        ctx.setVariable("tableId", null);
        ctx.setVariable("selectedStatus", null);
        ctx.setVariable("selectedOrderType", null);
        ctx.setVariable("selectedTableId", null);
        ctx.setVariable("selectedDate", null);
        ctx.setVariable("printTicketOrderId", null);
        ctx.setVariable("printTicketOrderIds", List.of());
    }

    @Test
    void orderViewShowsTipCard() {
        AbstractContext ctx = webContext();
        baseFlags(ctx, orderWithTip(new BigDecimal("45.50"), false));

        String html = templateEngine.process("waiter/orders/view", ctx);

        assertNotNull(html);
        assertTrue(html.contains("Te dejaron"), "the tip card should be rendered");
        assertTrue(html.contains("45.50"), "the tip amount should be shown");
    }

    @Test
    void orderViewShowsTipBreakdownForSplitAccounts() {
        AbstractContext ctx = webContext();
        baseFlags(ctx, orderWithTip(new BigDecimal("45.50"), true));

        String html = templateEngine.process("waiter/orders/view", ctx);

        assertTrue(html.contains("Por cuenta"), "a split bill should break the tip down per account");
        assertTrue(html.contains("Ana"), "the account label should be shown");
        assertTrue(html.contains("30.00"), "the first account tip");
        assertTrue(html.contains("15.50"), "the second account tip");
    }

    @Test
    void orderViewHidesTipCardWhenNoTipWasLeft() {
        AbstractContext ctx = webContext();
        baseFlags(ctx, orderWithTip(BigDecimal.ZERO, false));

        String html = templateEngine.process("waiter/orders/view", ctx);

        assertFalse(html.contains("Te dejaron"), "no tip means no tip card");
    }

    @Test
    void ordersListShowsTipColumn() {
        AbstractContext ctx = webContext();
        listFlags(ctx, orderWithTip(new BigDecimal("45.50"), false));

        String html = templateEngine.process("waiter/orders/list", ctx);

        assertNotNull(html);
        assertTrue(html.contains("Propina"), "the list should have a tip column");
        assertTrue(html.contains("45.50"), "the tip amount should be shown in the list");
    }
}
