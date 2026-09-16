package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
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
import org.springframework.web.servlet.View;
import org.thymeleaf.context.AbstractContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The actions column of an order row is rendered by fragments/order-actions, and the list
 * re-renders exactly that fragment in place (endpoint GET /cashier/orders/{id}/actions) when
 * the status changes, locally or through a WebSocket STATUS_CHANGE. This test renders the very
 * same fragment the endpoint returns, so the "Cobrar pedido" button appearing when the order
 * is ENTREGADO cannot silently break.
 */
@SpringBootTest
class OrderActionsFragmentRenderTest {

    /**
     * The controller returns "fragments/order-actions :: actionsFromModel", which the Thymeleaf
     * view resolver splits into this template + this markup selector.
     */
    private static final String TEMPLATE = "fragments/order-actions";

    private static final Set<String> FRAGMENT = Set.of("actionsFromModel");

    /** El nombre de vista que devuelve el controlador. */
    private static final String FRAGMENT_VIEW = "fragments/order-actions :: actionsFromModel";

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private org.springframework.web.servlet.ViewResolver viewResolver;

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

    private Order order(OrderStatus status, OrderType type, OrderStatus itemStatus) {
        OrderDetail detail = OrderDetail.builder()
                .idOrderDetail(1L)
                .itemName("Tacos de arrachera")
                .quantity(2)
                .unitPrice(new BigDecimal("90.00"))
                .subtotal(new BigDecimal("180.00"))
                .itemStatus(itemStatus)
                .selectedComplements(new ArrayList<>())
                .build();

        Order order = Order.builder()
                .idOrder(76L)
                .orderNumber("ORD-20260912-076")
                .orderType(type)
                .status(status)
                .paymentMethod(PaymentMethodType.CASH)
                .createdAt(LocalDateTime.of(2026, 9, 12, 14, 30))
                .total(new BigDecimal("180.00"))
                .customerName("Cliente de prueba")
                .orderDetails(new ArrayList<>(List.of(detail)))
                .build();
        return order;
    }

    private String render(Order order, boolean staffDeliveryEnabled) {
        AbstractContext ctx = webContext();
        ctx.setVariable("order", order);
        ctx.setVariable("currentRole", "cashier");
        ctx.setVariable("staffOrderStatusEnabled", staffDeliveryEnabled);
        ctx.setVariable("staffChefEnabled", false);
        ctx.setVariable("staffBaristaEnabled", false);
        ctx.setVariable("staffParrilleroEnabled", false);
        ctx.setVariable("staffDeliveryEnabled", staffDeliveryEnabled);
        // Exactly what the view resolver does with the controller's view name: process the
        // template selecting only that fragment, so the AJAX response is the rendered column.
        return templateEngine.process(TEMPLATE, FRAGMENT, ctx);
    }

    @Test
    void entregadoOrderOffersTheCobrarButton() {
        // Every line already DELIVERED: this is the state the cashier reaches by one click on
        // "Entregado", and the moment the cobrar button must show up.
        String html = render(order(OrderStatus.DELIVERED, OrderType.DINE_IN, OrderStatus.DELIVERED), false);

        assertTrue(html.contains("/cashier/payments/form/76"),
                "un pedido ENTREGADO debe ofrecer el cobro en la columna de acciones");
        assertTrue(html.contains("Cobrar pedido"), "el botón de cobrar debe traer su título");
    }

    @Test
    void readyOrderDoesNotOfferTheCobrarButtonYet() {
        // EN PREPARACIÓN / LISTO: the cashier must not be able to charge it, and that rule
        // lives here on the server (the JS only swaps the fragment).
        String html = render(order(OrderStatus.READY, OrderType.DINE_IN, OrderStatus.IN_PREPARATION), false);

        assertFalse(html.contains("Cobrar pedido"),
                "mientras no esté ENTREGADO no debe ofrecerse el botón de cobrar");
    }

    @Test
    void onTheWayDeliveryOrderOffersTheDeliveredStep() {
        String html = render(order(OrderStatus.ON_THE_WAY, OrderType.DELIVERY, OrderStatus.DELIVERED), true);

        assertTrue(html.contains("btn-advance-delivery"), "el botón de reparto debe seguir ahí");
        assertTrue(html.contains("data-next-status=\"DELIVERED\""),
                "desde EN CAMINO el siguiente paso es ENTREGADO");
    }

    @Test
    void actionsFollowTheAreaOfTheRole() {
        String html = render(order(OrderStatus.DELIVERED, OrderType.DINE_IN, OrderStatus.DELIVERED), false);

        assertTrue(html.contains("/cashier/orders/view/76"),
                "los enlaces deben salir en el área del rol que renderiza la columna");
    }

    @Test
    void cancelledOrderOnlyKeepsTheReadOnlyActions() {
        String html = render(order(OrderStatus.CANCELLED, OrderType.DINE_IN, OrderStatus.CANCELLED), true);

        assertFalse(html.contains("Cobrar pedido"), "un pedido cancelado ya no se cobra");
        assertFalse(html.contains("btn-cancel-order"), "un pedido cancelado ya no se cancela");
        assertTrue(html.contains("/cashier/orders/view/76"), "pero sí sigue siendo consultable");
    }

    @Test
    void theRenderedColumnCarriesNoWrapperMarkup() {
        // The list assigns this HTML to the innerHTML of the cell, so it must be just the
        // action buttons: no <html>/<body>/<td> wrapper would be valid inside the row.
        String html = render(order(OrderStatus.DELIVERED, OrderType.DINE_IN, OrderStatus.DELIVERED), false);

        assertFalse(html.contains("<html"), "el fragmento no debe traer el documento completo");
        assertFalse(html.contains("<td"), "el fragmento no debe traer la celda (la pone la lista)");
        assertEquals(1, count(html, "flex items-center justify-center gap-2"),
                "debe salir exactamente un grupo de botones");
    }

    /**
     * The endpoint answers with the view name "fragments/order-actions :: actionsFromModel". This
     * renders that name through the REAL view resolver of the application, so if the syntax ever
     * stops working (Thymeleaf upgrade, resolver change) the in-place refresh fails here instead of
     * in the browser, when the cashier marks a delivery as ENTREGADO.
     */
    @Test
    void theViewNameReturnedByTheControllerRendersTheColumn() throws Exception {
        View view = viewResolver.resolveViewName(FRAGMENT_VIEW, Locale.ROOT);
        assertNotNull(view, "el resolver debe reconocer 'plantilla :: fragmento' como vista");

        Order order = order(OrderStatus.DELIVERED, OrderType.DINE_IN, OrderStatus.DELIVERED);
        Map<String, Object> model = new HashMap<>();
        model.put("order", order);
        model.put("currentRole", "cashier");
        model.put("staffOrderStatusEnabled", false);
        model.put("staffChefEnabled", false);
        model.put("staffBaristaEnabled", false);
        model.put("staffParrilleroEnabled", false);
        model.put("staffDeliveryEnabled", false);

        MockHttpServletRequest request = new MockHttpServletRequest(new MockServletContext());
        request.setContextPath("");
        MockHttpServletResponse response = new MockHttpServletResponse();
        view.render(model, request, response);

        String html = response.getContentAsString();
        assertTrue(html.contains("Cobrar pedido"),
                "la columna devuelta por el servidor debe traer el botón de cobrar");
        assertFalse(html.contains("<td"), "y ser solo el contenido de la celda, sin envoltorios");
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
}
