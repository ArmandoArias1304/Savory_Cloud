package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.BusinessHoursService;
import com.aatechsolutions.elgransazon.application.service.CashierOrderServiceImpl;
import com.aatechsolutions.elgransazon.application.service.CashRegisterService;
import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.application.service.PromotionService;
import com.aatechsolutions.elgransazon.application.service.ReservationService;
import com.aatechsolutions.elgransazon.application.service.RestaurantTableService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.application.service.WebSocketNotificationService;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.AbstractView;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /cashier/orders/{id}/detail-panel sirve el cuerpo del modal que la lista abre al pulsar una
 * tarjeta (y que vuelve a pedirse si el pedido cambia mientras está abierto).
 *
 * <p>Aquí se comprueba el cableado real del controlador en Spring MVC: la ruta, el pedido cargado
 * CON sus ítems y cuentas ({@code findByIdWithDetails}, no un pedido pelado), el rol y los mismos
 * permisos de staff que usa la columna de acciones. Si la ruta no llegara a registrarse, el
 * navegador vería un 404 y el modal mostraría su mensaje de error.</p>
 */
class CashierOrderDetailPanelEndpointTest {

    private static final String PANEL_VIEW = "fragments/order-detail-panel :: panelFromModel";

    private CashierOrderServiceImpl cashierOrderService;
    private SystemConfigurationService systemConfigurationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cashierOrderService = mock(CashierOrderServiceImpl.class);
        systemConfigurationService = mock(SystemConfigurationService.class);

        CashierController controller = new CashierController(
                cashierOrderService,
                mock(OrderService.class),
                mock(RestaurantTableService.class),
                mock(ItemMenuService.class),
                mock(EmployeeService.class),
                systemConfigurationService,
                mock(CategoryService.class),
                mock(OrderRepository.class),
                mock(PromotionService.class),
                mock(BusinessHoursService.class),
                mock(WebSocketNotificationService.class),
                mock(DateTimeService.class),
                mock(ReservationService.class),
                mock(CashRegisterService.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                // Las aserciones leen el nombre de vista y el modelo: no hace falta pintar HTML.
                .setViewResolvers((viewName, locale) -> noOpView(viewName))
                .build();
    }

    @Test
    @DisplayName("El panel se sirve con el pedido cargado con sus ítems y cuentas")
    void servesTheDetailPanelOfOneOrder() throws Exception {
        Order order = order(91L);

        MvcResult result = panelOf(order);

        assertThat(result.getModelAndView().getViewName()).isEqualTo(PANEL_VIEW);
        Map<String, Object> model = result.getModelAndView().getModel();
        assertThat(model.get("order")).isSameAs(order);
        assertThat(model.get("currentRole")).isEqualTo("cashier");
        // Los ítems y las cuentas viajan con el pedido: es lo que el modal muestra y lo que no
        // cabe en la fila de la tabla.
        assertThat(((Order) model.get("order")).getOrderDetails()).isNotEmpty();
        verify(cashierOrderService).findByIdWithDetails(91L);
    }

    @Test
    @DisplayName("El panel llega con los mismos permisos de staff que la columna de acciones")
    void exposesTheStaffPermissionFlags() throws Exception {
        when(systemConfigurationService.getConfiguration()).thenReturn(SystemConfiguration.builder()
                .enableOrderStatusPermission(true)
                .staffCanManageChefItems(true)
                .build());

        Map<String, Object> model = panelOf(order(92L)).getModelAndView().getModel();

        assertThat(model.get("staffOrderStatusEnabled")).isEqualTo(true);
        assertThat(model.get("staffChefEnabled")).isEqualTo(true);
        assertThat(model.get("staffBaristaEnabled")).isEqualTo(false);
        assertThat(model.get("staffParrilleroEnabled")).isEqualTo(false);
        assertThat(model.get("staffDeliveryEnabled")).isEqualTo(false);
    }

    @Test
    @DisplayName("La ruta del panel se registra y responde 200 (no un 404)")
    void theRouteIsRegistered() throws Exception {
        when(cashierOrderService.findByIdWithDetails(93L)).thenReturn(Optional.of(order(93L)));

        mockMvc.perform(get("/cashier/orders/93/detail-panel")
                        .principal(new UsernamePasswordAuthenticationToken("cajera", "n/a",
                                List.of(new SimpleGrantedAuthority("ROLE_CASHIER")))))
                .andExpect(status().isOk());
    }

    private MvcResult panelOf(Order order) throws Exception {
        when(cashierOrderService.findByIdWithDetails(order.getIdOrder())).thenReturn(Optional.of(order));
        return mockMvc.perform(get("/cashier/orders/" + order.getIdOrder() + "/detail-panel")
                        .principal(new UsernamePasswordAuthenticationToken("cajera", "n/a",
                                List.of(new SimpleGrantedAuthority("ROLE_CASHIER")))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private Order order(long id) {
        Order order = Order.builder()
                .idOrder(id)
                .orderNumber("ORD-20260929-0" + id)
                .orderType(OrderType.DINE_IN)
                .status(OrderStatus.DELIVERED)
                .paymentMethod(PaymentMethodType.CASH)
                .createdAt(LocalDateTime.of(2026, 9, 29, 14, 0))
                .total(new BigDecimal("350.00"))
                .taxRate(new BigDecimal("16"))
                .customerName("Ana López")
                .orderDetails(new ArrayList<>())
                .build();

        OrderDetail detail = new OrderDetail();
        detail.setIdOrderDetail(1L);
        detail.setItemName("Tacos al pastor");
        detail.setQuantity(2);
        detail.setUnitPrice(new BigDecimal("50.00"));
        detail.setSubtotal(new BigDecimal("100.00"));
        detail.setItemStatus(OrderStatus.DELIVERED);
        order.addOrderDetail(detail);

        return order;
    }

    /** No pinta nada: las aserciones leen el modelo y el nombre de vista. */
    private static AbstractView noOpView(String beanName) {
        AbstractView noOp = new AbstractView() {
            @Override
            protected void renderMergedOutputModel(Map<String, Object> model,
                    HttpServletRequest request, HttpServletResponse response) {
                // no rendering needed
            }
        };
        noOp.setBeanName(beanName);
        return noOp;
    }
}
