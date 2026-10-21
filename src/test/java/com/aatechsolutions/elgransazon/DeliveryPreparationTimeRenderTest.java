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
 * La tabla del repartidor enseña el tiempo estimado de preparación como referencia
 * para organizar la entrega. Es un dato informativo: el reparto no lo calcula, solo
 * lo muestra junto al pedido.
 */
@SpringBootTest
class DeliveryPreparationTimeRenderTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Test
    @DisplayName("La tabla del repartidor muestra el tiempo estimado de la comanda")
    void deliveryTableShowsTheOrderEstimate() {
        ItemMenu soup = ItemMenu.builder().name("Sopa del día").preparationTimeMinutes(10).build();
        soup.setIdItemMenu(1L);
        ItemMenu steak = ItemMenu.builder().name("Corte a la parrilla").preparationTimeMinutes(15).build();
        steak.setIdItemMenu(2L);

        Order order = Order.builder()
                .orderNumber("ORD-20261020-12")
                .status(OrderStatus.READY)
                .orderType(OrderType.DELIVERY)
                .paymentMethod(PaymentMethodType.CASH)
                .customerName("Ana López")
                .customerPhone("5551234567")
                .deliveryAddress("Calle 1 #2")
                .createdAt(LocalDateTime.now().minusMinutes(20))
                .createdBy("cajeroA")
                .total(new BigDecimal("180.00"))
                .subtotal(new BigDecimal("155.17"))
                .taxAmount(new BigDecimal("24.83"))
                .taxRate(new BigDecimal("16"))
                .tip(BigDecimal.ZERO)
                .deliveryCost(BigDecimal.ZERO)
                .orderDiscount(BigDecimal.ZERO)
                .build();
        order.setIdOrder(12L);
        order.setOrderDetails(new ArrayList<>(List.of(
                OrderDetail.builder()
                        .order(order)
                        .itemMenu(soup)
                        .itemName("Sopa del día")
                        .quantity(1)
                        .unitPrice(new BigDecimal("60.00"))
                        .subtotal(new BigDecimal("60.00"))
                        .itemStatus(OrderStatus.READY)
                        .build(),
                OrderDetail.builder()
                        .order(order)
                        .itemMenu(steak)
                        .itemName("Corte a la parrilla")
                        .quantity(2)
                        .unitPrice(new BigDecimal("60.00"))
                        .subtotal(new BigDecimal("120.00"))
                        .itemStatus(OrderStatus.READY)
                        .build())));

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("globalSystemConfig", GlobalSystemConfig.builder().systemName("SavoryCloud").build());
        variables.put("systemConfig", new SystemConfiguration());
        variables.put("config", new SystemConfiguration());
        variables.put("orders", List.of(order));
        variables.put("currentRole", "delivery");
        variables.put("role", "Delivery");
        variables.put("username", "repartidorA");
        variables.put("companyId", 2L);
        variables.put("currentPage", 1);
        variables.put("totalPages", 1);
        variables.put("totalElements", 1);
        variables.put("pageSize", 15);
        variables.put("orderStatuses", OrderStatus.values());

        JakartaServletWebApplication application = JakartaServletWebApplication
                .buildApplication(new MockServletContext());
        WebContext context = new WebContext(application.buildExchange(
                new MockHttpServletRequest(), new MockHttpServletResponse()));
        context.setVariables(variables);

        String html = templateEngine.process("delivery/orders/pending", context);
        String text = html.replaceAll("\\s+", " ");

        assertTrue(text.contains("≈ 25 min"), "muestra el tiempo estimado junto al pedido");
        assertTrue(text.contains("timer"), "usa el ícono de reloj para identificarlo");
    }
}
