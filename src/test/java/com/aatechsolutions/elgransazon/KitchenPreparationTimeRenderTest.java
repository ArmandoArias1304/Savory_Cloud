package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
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
 * La tabla de cocina (la misma plantilla para chef, barista y parrillero) muestra,
 * en cada platillo, el tiempo promedio configurado, y en el encabezado de la
 * tarjeta el estimado de toda la comanda. Es la referencia que el preparador usa
 * para saber cuánto debería tardar el pedido completo.
 */
@SpringBootTest
class KitchenPreparationTimeRenderTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Test
    @DisplayName("La tabla de cocina muestra el tiempo por platillo y el de la comanda")
    void kitchenTableShowsDishAndOrderPreparationTimes() {
        ItemMenu soup = ItemMenu.builder().name("Sopa del día").preparationTimeMinutes(10).build();
        soup.setIdItemMenu(1L);
        ItemMenu steak = ItemMenu.builder().name("Corte a la parrilla").preparationTimeMinutes(15).build();
        steak.setIdItemMenu(2L);

        Order order = Order.builder()
                .orderNumber("ORD-20261020-9")
                .status(OrderStatus.IN_PREPARATION)
                .orderType(OrderType.DINE_IN)
                .paymentMethod(PaymentMethodType.CASH)
                .createdAt(LocalDateTime.now().minusMinutes(6))
                .createdBy("meseroA")
                .total(new BigDecimal("150.00"))
                .subtotal(new BigDecimal("129.31"))
                .taxAmount(new BigDecimal("20.69"))
                .taxRate(new BigDecimal("16"))
                .tip(BigDecimal.ZERO)
                .deliveryCost(BigDecimal.ZERO)
                .orderDiscount(BigDecimal.ZERO)
                .build();
        order.setIdOrder(9L);
        order.setOrderDetails(new ArrayList<>(List.of(
                OrderDetail.builder()
                        .order(order)
                        .itemMenu(soup)
                        .itemName("Sopa del día")
                        .quantity(1)
                        .unitPrice(new BigDecimal("50.00"))
                        .subtotal(new BigDecimal("50.00"))
                        .itemStatus(OrderStatus.IN_PREPARATION)
                        .build(),
                OrderDetail.builder()
                        .order(order)
                        .itemMenu(steak)
                        .itemName("Corte a la parrilla")
                        .quantity(2)
                        .unitPrice(new BigDecimal("50.00"))
                        .subtotal(new BigDecimal("100.00"))
                        .itemStatus(OrderStatus.IN_PREPARATION)
                        .build())));

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("globalSystemConfig", GlobalSystemConfig.builder().systemName("SavoryCloud").build());
        variables.put("systemConfig", new SystemConfiguration());
        variables.put("config", new SystemConfiguration());
        variables.put("orders", List.of(order));
        variables.put("currentChef", "chefA");
        variables.put("currentRole", "chef");
        variables.put("rolePrefix", "chef");
        variables.put("currentRoleLabel", "Chef");
        variables.put("companyId", 2L);
        variables.put("currentPage", 1);
        variables.put("totalPages", 1);
        variables.put("totalElements", 1);
        variables.put("pageSize", 15);
        variables.put("pendingCount", 0L);
        variables.put("inPreparationCount", 1L);
        variables.put("orderStatuses", OrderStatus.values());

        String html = render("chef/orders/pending", variables);
        String text = html.replaceAll("\\s+", " ");

        assertTrue(text.contains("Comanda <span>≈ 25 min</span>"),
                "el encabezado suma los platillos de la orden");
        assertTrue(text.contains("10 min"), "cada platillo enseña su propio tiempo");
        assertTrue(text.contains("15 min"), "también el segundo platillo");
    }

    private String render(String template, Map<String, Object> variables) {
        JakartaServletWebApplication application = JakartaServletWebApplication
                .buildApplication(new MockServletContext());
        WebContext context = new WebContext(application.buildExchange(
                new MockHttpServletRequest(), new MockHttpServletResponse()));
        context.setVariables(variables);
        return templateEngine.process(template, context);
    }
}
