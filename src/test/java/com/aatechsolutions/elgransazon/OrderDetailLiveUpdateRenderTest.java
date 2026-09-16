package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The order detail view must keep the order status and every item status up to date over
 * WebSocket, without a manual reload. That needs three things in the rendered page: the
 * ids the script patches ({@code order-status-badge}, {@code item-status-cell-<detail>}),
 * the ids of the order/company the script listens for, and the shared client itself.
 * Rendering with the real engine catches a broken expression here instead of in the
 * browser.
 */
@SpringBootTest
class OrderDetailLiveUpdateRenderTest {

    private static final Long ORDER_ID = 76L;
    private static final Long COMPANY_ID = 7L;
    private static final Long DETAIL_ID = 501L;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private AbstractContext webContext(String role) {
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webApplicationContext);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setContextPath("");
        SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "empleado", "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))));
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

    private Order order(OrderStatus itemStatus) {
        Company company = new Company();
        company.setIdCompany(COMPANY_ID);

        ItemMenu tacos = ItemMenu.builder().name("Tacos").requiresPreparation(true).build();
        OrderDetail detail = OrderDetail.builder()
                .idOrderDetail(DETAIL_ID)
                .itemMenu(tacos)
                .itemName("Tacos de arrachera")
                .quantity(2)
                .unitPrice(new BigDecimal("90.00"))
                .subtotal(new BigDecimal("180.00"))
                .itemStatus(itemStatus)
                .build();

        return Order.builder()
                .idOrder(ORDER_ID)
                .orderNumber("ORD-20260912-076")
                .orderType(OrderType.DINE_IN)
                // The order-level badge stays PENDING so the assertions below isolate the
                // item badge (whose "Por aceptar" is the role-dependent one).
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethodType.CASH)
                .company(company)
                .createdAt(LocalDateTime.of(2026, 9, 12, 14, 30))
                .subtotal(new BigDecimal("180.00"))
                .taxAmount(new BigDecimal("24.83"))
                .taxRate(new BigDecimal("16.00"))
                .total(new BigDecimal("180.00"))
                .customerName("Cliente de prueba")
                .customerPhone("4421234567")
                .orderDetails(new ArrayList<>(List.of(detail)))
                .build();
    }

    private AbstractContext context(String role, OrderStatus itemStatus) {
        Order order = order(itemStatus);
        AbstractContext ctx = webContext(role);
        SystemConfiguration config = SystemConfiguration.builder()
                .restaurantName("El Gran Sazon")
                .taxRate(new BigDecimal("16.00"))
                .build();

        ctx.setVariable("globalSystemConfig", GlobalSystemConfig.builder().systemName("Test").build());
        ctx.setVariable("systemConfig", config);
        ctx.setVariable("config", config);
        ctx.setVariable("username", "empleado");
        ctx.setVariable("currentRole", role);
        ctx.setVariable("role", role);
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
        ctx.setVariable("staffDeliveryEnabled", false);
        return ctx;
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "cashier", "waiter"})
    @DisplayName("Cada detalle de pedido escucha el WebSocket y expone los ids que el script parchea")
    void detailViewsExposeLiveUpdateHooks(String role) {
        String html = templateEngine.process(role + "/orders/view",
                context(role, OrderStatus.IN_PREPARATION));

        // Ids the shared client patches in place.
        assertTrue(html.contains("id=\"order-status-badge\""),
                role + ": el badge del pedido debe tener id para poder actualizarse en sitio");
        assertTrue(html.contains("id=\"item-status-cell-" + DETAIL_ID + "\""),
                role + ": cada item debe exponer su celda de estado");

        // Order/company the client filters the notifications by.
        assertTrue(html.contains("ORDER_VIEW_ID = " + ORDER_ID),
                role + ": la vista debe conocer el id del pedido");
        assertTrue(html.contains("ORDER_VIEW_COMPANY_ID = " + COMPANY_ID),
                role + ": la vista debe conocer la empresa (topics multi-tenant)");

        // The shared client itself.
        assertTrue(html.contains("/js/order-detail-live.js"),
                role + ": la vista debe cargar el cliente de actualización en vivo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "cashier"})
    @DisplayName("Admin y cajero sí muestran el badge 'Por aceptar' de un item por aceptar")
    void adminAndCashierShowToAcceptBadge(String role) {
        String html = templateEngine.process(role + "/orders/view",
                context(role, OrderStatus.TO_ACCEPT));

        assertTrue(html.contains("ORDER_VIEW_SHOW_TO_ACCEPT = true"),
                role + ": el badge 'Por aceptar' se muestra y el cliente debe respetarlo");
        assertTrue(html.contains("item-status-cell-" + DETAIL_ID),
                role + ": el item por aceptar sigue siendo parcheable en sitio");
        // Each view has its own glyph; the live client must reuse it.
        String expectedIcon = "cashier".equals(role) ? "hourglass_empty" : "hourglass_top";
        assertTrue(html.contains(expectedIcon),
                role + ": el badge 'Por aceptar' del item es parte de la vista");
        assertTrue(html.contains("ORDER_VIEW_TO_ACCEPT_ICON = \"" + expectedIcon + "\""),
                role + ": el cliente debe reusar el glyph de esa vista");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("El mesero no muestra el badge 'Por aceptar' de un item por aceptar")
    void waiterDoesNotShowToAcceptBadge() {
        String html = templateEngine.process("waiter/orders/view",
                context("waiter", OrderStatus.TO_ACCEPT));

        assertTrue(html.contains("ORDER_VIEW_SHOW_TO_ACCEPT = false"),
                "el cliente del mesero no debe pintar el badge 'Por aceptar'");
        assertFalse(html.contains("hourglass_top"),
                "la vista del mesero nunca muestra el badge 'Por aceptar' de un item");
    }
}
