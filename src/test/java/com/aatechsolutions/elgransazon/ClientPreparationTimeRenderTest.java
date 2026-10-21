package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las dos pantallas del cliente donde se ve el pedido —la lista y el detalle— deben
 * enseñar el tiempo estimado y ofrecer el reloj flotante con el tiempo transcurrido.
 * Se renderizan las plantillas reales con el motor de la aplicación y un pedido con
 * dos platillos de 10 y 15 minutos, así que el estimado esperado es 25 minutos.
 */
@SpringBootTest
class ClientPreparationTimeRenderTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockServletContext servletContext;

    @BeforeEach
    void setUp() {
        servletContext = new MockServletContext();
    }

    @Test
    @DisplayName("La lista de pedidos muestra el estimado y el reloj flotante")
    void ordersListShowsEstimateAndClock() {
        String html = render("client/orders", variables());

        assertTrue(html.contains("≈ 25 min"), "muestra el tiempo estimado de la comanda");
        assertTrue(html.contains("prepClockButton"), "ofrece el reloj flotante");
        assertTrue(html.contains("data-order-number=\"ORD-20261020-7\""), "el reloj identifica el pedido");
    }

    @Test
    @DisplayName("El detalle del pedido muestra la tarjeta de tiempo estimado y el reloj")
    void orderDetailShowsEstimateAndClock() {
        String html = render("client/order-detail", variables());

        assertTrue(html.contains("Tiempo aproximado de preparación"), "explica el tiempo estimado");
        assertTrue(html.contains("≈ 25 min"), "muestra la suma de los platillos");
        assertTrue(html.contains("prepClockButton"), "ofrece el reloj flotante");
    }

    private Map<String, Object> variables() {
        Order order = order();
        SystemConfiguration config = new SystemConfiguration();
        config.setRestaurantName("El Gran Sazón");

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("systemConfig", config);
        variables.put("config", config);
        variables.put("order", order);
        variables.put("orderDetails", order.getOrderDetails());
        variables.put("orders", List.of(order));
        variables.put("trackedOrders", List.of(order));
        variables.put("currentPage", 1);
        variables.put("totalPages", 1);
        variables.put("totalElements", 1);
        variables.put("totalOrders", 1);
        variables.put("activeOrders", 1);
        variables.put("completedOrders", 0);
        variables.put("pageSize", 15);
        variables.put("orderStatuses", OrderStatus.values());
        variables.put("customer", order.getCustomer());
        variables.put("currentUsername", "cliente@example.com");
        variables.put("currentRole", "client");
        return variables;
    }

    private Order order() {
        ItemMenu soup = ItemMenu.builder().name("Sopa del día").preparationTimeMinutes(10).build();
        soup.setIdItemMenu(1L);
        ItemMenu steak = ItemMenu.builder().name("Corte a la parrilla").preparationTimeMinutes(15).build();
        steak.setIdItemMenu(2L);

        Customer customer = new Customer();
        customer.setFullName("Ana López");
        customer.setEmail("cliente@example.com");

        Order order = Order.builder()
                .orderNumber("ORD-20261020-7")
                .status(OrderStatus.IN_PREPARATION)
                .orderType(OrderType.TAKEOUT)
                .paymentMethod(PaymentMethodType.CASH)
                .customer(customer)
                .customerName("Ana López")
                .createdAt(LocalDateTime.now().minusMinutes(9))
                .createdBy("cliente@example.com")
                .total(new BigDecimal("180.00"))
                .subtotal(new BigDecimal("155.17"))
                .taxAmount(new BigDecimal("24.83"))
                .taxRate(new BigDecimal("16"))
                .tip(BigDecimal.ZERO)
                .deliveryCost(BigDecimal.ZERO)
                .orderDiscount(BigDecimal.ZERO)
                .build();
        order.setIdOrder(7L);
        order.setOrderDetails(new ArrayList<>(List.of(
                OrderDetail.builder()
                        .order(order)
                        .itemMenu(soup)
                        .itemName("Sopa del día")
                        .quantity(1)
                        .unitPrice(new BigDecimal("60.00"))
                        .subtotal(new BigDecimal("60.00"))
                        .itemStatus(OrderStatus.IN_PREPARATION)
                        .build(),
                OrderDetail.builder()
                        .order(order)
                        .itemMenu(steak)
                        .itemName("Corte a la parrilla")
                        .quantity(2)
                        .unitPrice(new BigDecimal("60.00"))
                        .subtotal(new BigDecimal("120.00"))
                        .itemStatus(OrderStatus.PENDING)
                        .build())));
        return order;
    }

    private String render(String template, Map<String, Object> variables) {
        JakartaServletWebApplication application = JakartaServletWebApplication
                .buildApplication(servletContext);
        WebContext context = new WebContext(application.buildExchange(
                new MockHttpServletRequest(servletContext),
                new MockHttpServletResponse()));
        context.setVariables(variables);
        return templateEngine.process(template, context);
    }
}
