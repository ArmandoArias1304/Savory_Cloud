package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.BusinessHoursService;
import com.aatechsolutions.elgransazon.application.service.CashRegisterService;
import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.ComandaEscPosService;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.application.service.OrderTransferService;
import com.aatechsolutions.elgransazon.application.service.PromotionService;
import com.aatechsolutions.elgransazon.application.service.ReservationService;
import com.aatechsolutions.elgransazon.application.service.RestaurantTableService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.application.service.TicketEscPosService;
import com.aatechsolutions.elgransazon.application.service.TicketPdfService;
import com.aatechsolutions.elgransazon.application.service.WebSocketNotificationService;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.repository.ComplementRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComboItemRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComplementRepository;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.domain.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /{role}/orders/{id}/detail-panel sirve el cuerpo del modal que las listas de admin/gerente y
 * del mesero abren al pulsar una tarjeta: lo que no cabe en la fila de la tabla (ítems consumidos
 * con sus notas y complementos, cuentas por persona) sin salir de la lista.
 *
 * <p>Aquí se comprueba el cableado real en Spring MVC: la ruta, que el pedido se cargue CON sus
 * ítems ({@code findByIdWithDetails}, no un pedido pelado), que el rol viaje al panel como
 * {@code currentRole} y que se pida al servicio de ESE rol —el del mesero solo resuelve sus
 * pedidos, así que un pedido ajeno no se puede abrir ni sabiendo su id.</p>
 *
 * <p>También fija que la vista sea {@code panelInfoFromModel} y no {@code panelFromModel}: el
 * panel de admin/mesero va SIN bloque de acciones, porque los botones que van dentro del modal son
 * los de la fila de cada rol (los del mesero traen reglas propias, como la transferencia) y los
 * pone la página. Si se cambiara a la variante con acciones, el mesero vería los botones del
 * cajero, que apuntan a /cashier/payments y le darían 403.</p>
 */
class AdminWaiterOrderDetailPanelEndpointTest {

    private static final String PANEL_INFO_VIEW = "fragments/order-detail-panel :: panelInfoFromModel";

    private OrderService adminOrderService;
    private OrderService waiterOrderService;
    private SystemConfigurationService systemConfigurationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminOrderService = mock(OrderService.class);
        waiterOrderService = mock(OrderService.class);
        systemConfigurationService = mock(SystemConfigurationService.class);

        OrderController controller = new OrderController(
                adminOrderService,
                waiterOrderService,
                mock(OrderService.class),
                mock(OrderService.class),
                mock(OrderService.class),
                mock(OrderService.class),
                mock(OrderService.class),
                mock(RestaurantTableService.class),
                mock(ItemMenuService.class),
                mock(EmployeeService.class),
                systemConfigurationService,
                mock(CategoryService.class),
                mock(OrderRepository.class),
                mock(PromotionService.class),
                mock(WebSocketNotificationService.class),
                mock(BusinessHoursService.class),
                mock(TicketPdfService.class),
                mock(TicketEscPosService.class),
                mock(ComandaEscPosService.class),
                mock(PaymentRepository.class),
                mock(ComplementRepository.class),
                mock(ItemMenuComplementRepository.class),
                mock(ItemMenuComboItemRepository.class),
                mock(ObjectMapper.class),
                mock(ReservationService.class),
                mock(DateTimeService.class),
                mock(CashRegisterService.class),
                mock(OrderTransferService.class),
                mock(com.aatechsolutions.elgransazon.application.service.CustomerService.class),
                mock(com.aatechsolutions.elgransazon.application.service.CustomerAddressService.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                // Las aserciones leen el nombre de vista y el modelo: no hace falta pintar HTML.
                .setViewResolvers((viewName, locale) -> noOpView(viewName))
                .build();
    }

    @Test
    @DisplayName("El panel de admin llega con el pedido cargado con sus ítems y sin bloque de acciones")
    void servesThePanelWithoutActionsForAdmin() throws Exception {
        Order order = order(79L);
        when(adminOrderService.findByIdWithDetails(79L)).thenReturn(Optional.of(order));

        MvcResult result = panelOf("admin", 79L, admin());

        assertThat(result.getModelAndView().getViewName()).isEqualTo(PANEL_INFO_VIEW);
        Map<String, Object> model = result.getModelAndView().getModel();
        assertThat(model.get("order")).isSameAs(order);
        assertThat(model.get("currentRole")).isEqualTo("admin");
        // Los ítems viajan con el pedido: es lo que el modal muestra y lo que no cabe en la fila.
        assertThat(((Order) model.get("order")).getOrderDetails()).isNotEmpty();
        verify(adminOrderService).findByIdWithDetails(79L);
    }

    @Test
    @DisplayName("El mesero recibe su panel pidiéndolo a su propio servicio")
    void servesThePanelForTheWaiter() throws Exception {
        Order order = order(80L);
        when(waiterOrderService.findByIdWithDetails(80L)).thenReturn(Optional.of(order));

        MvcResult result = panelOf("waiter", 80L, waiter());

        assertThat(result.getModelAndView().getViewName()).isEqualTo(PANEL_INFO_VIEW);
        assertThat(result.getModelAndView().getModel().get("currentRole")).isEqualTo("waiter");
        // El servicio del mesero es el que filtra por propiedad del pedido.
        verify(waiterOrderService).findByIdWithDetails(80L);
    }

    @Test
    @DisplayName("El gerente entra por la ruta del admin y recibe el mismo panel")
    void theManagerUsesTheAdminRoute() throws Exception {
        Order order = order(81L);
        when(adminOrderService.findByIdWithDetails(81L)).thenReturn(Optional.of(order));

        MvcResult result = panelOf("admin", 81L, manager());

        assertThat(result.getModelAndView().getViewName()).isEqualTo(PANEL_INFO_VIEW);
        assertThat(result.getModelAndView().getModel().get("currentRole")).isEqualTo("admin");
    }

    @Test
    @DisplayName("Un pedido que el rol no puede ver no se sirve")
    void anOrderTheRoleCannotSeeIsNotServed() throws Exception {
        // El servicio del mesero devuelve vacío cuando el pedido no es suyo.
        when(waiterOrderService.findByIdWithDetails(600L)).thenReturn(Optional.empty());

        Exception error = assertThrows(Exception.class, () -> panelOf("waiter", 600L, waiter()));

        assertThat(messagesOf(error)).contains("Pedido no encontrado");
    }

    @Test
    @DisplayName("Un rol no puede pedir el panel de otro con su propia ruta")
    void aRoleCannotAskForAnotherRolesPanel() throws Exception {
        Exception error = assertThrows(Exception.class, () -> panelOf("admin", 79L, waiter()));

        assertThat(messagesOf(error)).contains("Role mismatch");
    }

    @Test
    @DisplayName("El panel lleva los mismos permisos de staff que la fila, y el del reparto nunca es del mesero")
    void exposesTheSameStaffPermissionFlagsAsTheRow() throws Exception {
        when(systemConfigurationService.getConfiguration()).thenReturn(SystemConfiguration.builder()
                .enableOrderStatusPermission(true)
                .staffCanManageChefItems(true)
                .staffCanManageDeliveryOrders(true)
                .build());
        when(adminOrderService.findByIdWithDetails(82L)).thenReturn(Optional.of(order(82L)));
        when(waiterOrderService.findByIdWithDetails(83L)).thenReturn(Optional.of(order(83L)));

        Map<String, Object> adminModel = panelOf("admin", 82L, admin()).getModelAndView().getModel();
        Map<String, Object> waiterModel = panelOf("waiter", 83L, waiter()).getModelAndView().getModel();

        assertThat(adminModel.get("staffOrderStatusEnabled")).isEqualTo(true);
        assertThat(adminModel.get("staffChefEnabled")).isEqualTo(true);
        assertThat(adminModel.get("staffBaristaEnabled")).isEqualTo(false);
        assertThat(adminModel.get("staffDeliveryEnabled")).isEqualTo(true);

        // El mesero NUNCA puede avanzar el reparto, aunque el permiso esté encendido.
        assertThat(waiterModel.get("staffOrderStatusEnabled")).isEqualTo(true);
        assertThat(waiterModel.get("staffDeliveryEnabled")).isEqualTo(false);
    }

    private MvcResult panelOf(String role, long id, Authentication authentication) throws Exception {
        return mockMvc.perform(get("/" + role + "/orders/" + id + "/detail-panel")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** El mensaje del error y el de sus causas: la excepción viaja envuelta por el servlet. */
    private String messagesOf(Throwable error) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append(" | ");
            }
        }
        return messages.toString();
    }

    private Authentication admin() {
        return principal("admin", "ROLE_ADMIN");
    }

    private Authentication manager() {
        return principal("gerente", "ROLE_MANAGER");
    }

    private Authentication waiter() {
        return principal("mesero", "ROLE_WAITER");
    }

    private Authentication principal(String username, String role) {
        return new UsernamePasswordAuthenticationToken(username, "n/a",
                List.of(new SimpleGrantedAuthority(role)));
    }

    private Order order(long id) {
        Order order = Order.builder()
                .idOrder(id)
                .orderNumber("ORD-20261015-0" + id)
                .orderType(OrderType.DINE_IN)
                .status(OrderStatus.DELIVERED)
                .paymentMethod(PaymentMethodType.CASH)
                .createdAt(LocalDateTime.of(2026, 10, 15, 14, 0))
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
