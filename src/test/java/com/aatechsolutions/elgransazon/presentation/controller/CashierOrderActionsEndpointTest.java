package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.BusinessHoursService;
import com.aatechsolutions.elgransazon.application.service.CashierOrderServiceImpl;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /cashier/orders/{id}/actions returns the actions column of ONE row so the cashier list can
 * swap it in place (no full page reload) after a status change: the cashier's own click or a
 * STATUS_CHANGE notification pushed over WebSocket. It must render the shared fragment and
 * expose the same staff-permission flags the list uses, otherwise the rebuilt column would show
 * more or fewer buttons than the one rendered with the page.
 */
class CashierOrderActionsEndpointTest {

    private static final String FRAGMENT_VIEW = "fragments/order-actions :: actionsFromModel";

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
                mock(ReservationService.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                // The assertions read the view name + model, so no HTML is rendered here.
                .setViewResolvers((viewName, locale) -> noOpView(viewName))
                .build();
    }

    private Order order(long id) {
        return Order.builder()
                .idOrder(id)
                .orderNumber("ORD-20260912-0" + id)
                .orderType(OrderType.DELIVERY)
                .status(OrderStatus.ON_THE_WAY)
                .paymentMethod(PaymentMethodType.CASH)
                .createdAt(LocalDateTime.of(2026, 9, 12, 14, 0))
                .total(new BigDecimal("150.00"))
                .customerName("Cliente domicilio")
                .orderDetails(new ArrayList<>())
                .build();
    }

    private MvcResult actionsOf(Order order) throws Exception {
        when(cashierOrderService.findByIdOrThrow(order.getIdOrder())).thenReturn(order);
        return mockMvc.perform(get("/cashier/orders/" + order.getIdOrder() + "/actions")
                .principal(new UsernamePasswordAuthenticationToken("cajera", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_CASHIER")))))
                .andExpect(status().isOk())
                .andReturn();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> modelOf(Order order) throws Exception {
        return actionsOf(order).getModelAndView().getModel();
    }

    @Test
    @DisplayName("La columna de acciones de una fila se renderiza con el fragmento compartido")
    void rendersTheSharedFragmentForThatOrder() throws Exception {
        Order order = order(90L);

        MvcResult result = actionsOf(order);

        assertThat(result.getModelAndView().getViewName()).isEqualTo(FRAGMENT_VIEW);
        Map<String, Object> model = result.getModelAndView().getModel();
        assertThat(model.get("order")).isSameAs(order);
        assertThat(model.get("currentRole")).isEqualTo("cashier");
    }

    @Test
    @DisplayName("El fragmento llega con los mismos permisos de staff que la lista")
    void exposesTheStaffPermissionFlags() throws Exception {
        when(systemConfigurationService.getConfiguration()).thenReturn(SystemConfiguration.builder()
                .enableOrderStatusPermission(true)
                .staffCanManageDeliveryOrders(true)
                .staffCanManageChefItems(true)
                .build());

        Map<String, Object> model = modelOf(order(91L));

        assertThat(model.get("staffOrderStatusEnabled")).isEqualTo(true);
        assertThat(model.get("staffDeliveryEnabled")).isEqualTo(true);
        assertThat(model.get("staffChefEnabled")).isEqualTo(true);
        assertThat(model.get("staffBaristaEnabled")).isEqualTo(false);
        assertThat(model.get("staffParrilleroEnabled")).isEqualTo(false);
    }

    @Test
    @DisplayName("Sin el permiso padre el sub-permiso de reparto no habilita el botón")
    void deliverySubPermissionIsChainedToTheParent() throws Exception {
        when(systemConfigurationService.getConfiguration()).thenReturn(SystemConfiguration.builder()
                .enableOrderStatusPermission(false)
                .staffCanManageDeliveryOrders(true)
                .build());

        Map<String, Object> model = modelOf(order(92L));

        assertThat(model.get("staffOrderStatusEnabled")).isEqualTo(false);
        assertThat(model.get("staffDeliveryEnabled")).isEqualTo(false);
    }

    /** Records nothing: the assertions read the model, this only avoids rendering HTML. */
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
