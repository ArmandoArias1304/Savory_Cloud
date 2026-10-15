package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.OrderServiceImpl;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El tablero de cocina tal como lo recibe el navegador, con el pulso de pedido nuevo.
 *
 * <p>La cocina no mira la pantalla de forma continua: cuando entra un pedido por WebSocket, el
 * tablero tiene que poder señalar visualmente CUÁL es. Eso depende de tres cosas que viven en la
 * plantilla y que nada más cubriría si desaparecieran: que cada tarjeta traiga su número de pedido
 * en un atributo, que exista el aviso para los pedidos que aún no están renderizados, y que el
 * manejador de notificaciones llame a la función que pulsa la tarjeta.</p>
 *
 * <p>Se renderiza la plantilla real a través del controlador, así que además de lo anterior sirve
 * de red contra un error de sintaxis de Thymeleaf en una pantalla que ningún otro test abría.</p>
 */
@SpringBootTest
@WithMockUser(username = "admin", roles = "ADMIN")
class KitchenBoardNewOrderPulseRenderTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    /** El admin de cocina usa el servicio calificado {@code adminOrderService}. */
    @MockitoBean
    private OrderServiceImpl adminOrderService;

    @MockitoBean
    private SystemConfigurationService systemConfigurationService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        when(systemConfigurationService.getConfiguration()).thenReturn(
                SystemConfiguration.builder()
                        .restaurantName("El Gran Sazón")
                        .build());
    }

    private Order pendingOrder(String orderNumber) {
        return Order.builder()
                .idOrder(1L)
                .orderNumber(orderNumber)
                .orderType(OrderType.DINE_IN)
                .status(OrderStatus.PENDING)
                .total(new BigDecimal("185.00"))
                .createdAt(LocalDateTime.now())
                // La tarjeta pinta "Mesa N" para los pedidos de salón a partir de
                // order.table, así que un pedido de prueba sin mesa no se puede renderizar.
                .table(RestaurantTable.builder().id(1L).tableNumber(5).build())
                .build();
    }

    @Test
    @DisplayName("cada tarjeta del tablero lleva su número de pedido para poder pulsarla")
    void everyCardCarriesItsOrderNumber() throws Exception {
        when(adminOrderService.findAll()).thenReturn(List.of(pendingOrder("ORD-1")));

        mockMvc.perform(get("/admin/kitchen"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"kitchenOrders\"")))
                .andExpect(content().string(containsString("data-order-number=\"ORD-1\"")));
    }

    @Test
    @DisplayName("el tablero pulsa la tarjeta del pedido que llega por WebSocket")
    void theBoardPulsesTheCardOfAnIncomingOrder() throws Exception {
        when(adminOrderService.findAll()).thenReturn(List.of(pendingOrder("ORD-1")));

        mockMvc.perform(get("/admin/kitchen"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("function highlightNewOrder(")))
                .andExpect(content().string(containsString("highlightNewOrder(notification.orderNumber)")))
                .andExpect(content().string(containsString("\"kds-new-order\"")))
                .andExpect(content().string(containsString("scrollIntoView")));
    }

    @Test
    @DisplayName("el aviso de pedidos nuevos existe aunque el tablero esté vacío")
    void theNewOrdersNoticeIsAlwaysOnTheBoard() throws Exception {
        when(adminOrderService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/kitchen"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"nuevosPedidosAviso\"")))
                .andExpect(content().string(containsString("id=\"nuevosPedidosTexto\"")))
                .andExpect(content().string(containsString("function marcarPedidosNuevos(")));
    }
}
