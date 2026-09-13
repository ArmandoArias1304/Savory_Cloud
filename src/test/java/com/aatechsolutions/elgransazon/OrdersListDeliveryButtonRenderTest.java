package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for the "Avanzar reparto" one-click button: processing the real
 * admin and cashier order-list templates catches Thymeleaf syntax/expression
 * errors and verifies the delivery button renders only when the flag is on.
 */
@SpringBootTest
class OrdersListDeliveryButtonRenderTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private AbstractContext webContext() {
        MockServletContext servletContext = new MockServletContext();
        // sec:authorize (sidebar) resolves roles through the servlet context's WebApplicationContext.
        servletContext.setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webApplicationContext);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setContextPath("");
        // #authentication is read from the security context by the Thymeleaf security dialect.
        SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "ana", "n/a", List.of(new SimpleGrantedAuthority("ROLE_WAITER"))));
        SecurityContextHolder.setContext(securityContext);
        request.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        IWebExchange exchange = application.buildExchange(request, response);
        return new WebContext(exchange);
    }

    private GlobalSystemConfig globalConfig() {
        return GlobalSystemConfig.builder().systemName("Test").systemLogoUrl(null).build();
    }

    private SystemConfiguration systemConfig() {
        return SystemConfiguration.builder()
                .restaurantName("El Gran Sazon")
                .enableOrderStatusPermission(true)
                .staffCanManageDeliveryOrders(true)
                .build();
    }

    private Order deliveryOrder() {
        return Order.builder()
                .idOrder(1L)
                .orderNumber("ORD-1")
                .orderType(OrderType.DELIVERY)
                .status(OrderStatus.READY)
                .total(new BigDecimal("150.00"))
                .paymentMethod(PaymentMethodType.CASH)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void baseFlags(AbstractContext ctx, boolean staffDeliveryEnabled) {
        ctx.setVariable("globalSystemConfig", globalConfig());
        ctx.setVariable("systemConfig", systemConfig());
        ctx.setVariable("username", "ana");
        ctx.setVariable("currentRole", "admin");
        ctx.setVariable("tables", List.<RestaurantTable>of());
        ctx.setVariable("statuses", OrderStatus.values());
        ctx.setVariable("orderTypes", OrderType.values());
        ctx.setVariable("currentPage", 1);
        ctx.setVariable("totalPages", 1);
        ctx.setVariable("totalElements", 1);
        ctx.setVariable("pageSize", 15);
        ctx.setVariable("globalCurrentPage", 1);
        ctx.setVariable("globalTotalPages", 1);
        ctx.setVariable("globalTotalElements", 0);
        ctx.setVariable("pendingCount", 0L);
        ctx.setVariable("inPreparationCount", 0L);
        ctx.setVariable("paidCount", 0L);
        ctx.setVariable("myPendingCount", 0L);
        ctx.setVariable("unpaidCount", 0L);
        ctx.setVariable("paidOrdersCount", 0L);
        ctx.setVariable("unpaidTotal", BigDecimal.ZERO);
        ctx.setVariable("todayRevenue", BigDecimal.ZERO);
        ctx.setVariable("myCollectedRevenue", BigDecimal.ZERO);
        ctx.setVariable("myOwnRevenue", BigDecimal.ZERO);
        ctx.setVariable("othersCollectedRevenue", BigDecimal.ZERO);
        ctx.setVariable("isRestaurantOpen", true);
        ctx.setVariable("staffOrderStatusEnabled", true);
        ctx.setVariable("staffChefEnabled", false);
        ctx.setVariable("staffBaristaEnabled", false);
        ctx.setVariable("staffParrilleroEnabled", false);
        ctx.setVariable("waiterDeliveryCanCollect", true);
        ctx.setVariable("staffDeliveryEnabled", staffDeliveryEnabled);
    }

    @Test
    void adminListRendersDeliveryButtonWhenFlagOn() {
        AbstractContext ctx = webContext();
        baseFlags(ctx, true);
        ctx.setVariable("orders", List.of(deliveryOrder()));

        String html = templateEngine.process("admin/orders/list", ctx);
        assertNotNull(html);
        assertTrue(html.contains("btn-advance-delivery p-2"), "delivery advance button should render");
        assertTrue(html.contains("ON_THE_WAY"), "next status should be ON_THE_WAY for a READY delivery order");
    }

    @Test
    void adminListHidesDeliveryButtonWhenFlagOff() {
        AbstractContext ctx = webContext();
        baseFlags(ctx, false);
        ctx.setVariable("orders", List.of(deliveryOrder()));

        String html = templateEngine.process("admin/orders/list", ctx);
        assertNotNull(html);
        assertFalse(html.contains("btn-advance-delivery p-2"), "delivery advance button should be hidden");
    }

    @Test
    void cashierListRendersDeliveryButtonWhenFlagOn() {
        AbstractContext ctx = webContext();
        baseFlags(ctx, true);
        ctx.setVariable("currentRole", "cashier");
        ctx.setVariable("myOrders", List.of(deliveryOrder()));
        ctx.setVariable("unpaidOrders", List.of());

        String html = templateEngine.process("cashier/orders/list", ctx);
        assertNotNull(html);
        assertTrue(html.contains("btn-advance-delivery p-2"), "delivery advance button should render");
    }
}
