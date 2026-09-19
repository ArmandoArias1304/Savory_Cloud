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
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The cashier's global board ("Pedidos Globales e Historial de Cobros") must keep a DELIVERY
 * order that is already ON_THE_WAY: the staff permission (admin/gerente/cajero) advances the
 * order from that very row, so dropping it would leave no way to mark it ENTREGADO.
 */
class CashierOrdersDeliveryBoardTest {

    private static final String CASHIER = "cajera";

    private CashierOrderServiceImpl cashierOrderService;
    private OrderService adminOrderService;
    private RestaurantTableService restaurantTableService;
    private SystemConfigurationService systemConfigurationService;
    private DateTimeService dateTimeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cashierOrderService = mock(CashierOrderServiceImpl.class);
        adminOrderService = mock(OrderService.class);
        restaurantTableService = mock(RestaurantTableService.class);
        systemConfigurationService = mock(SystemConfigurationService.class);
        dateTimeService = mock(DateTimeService.class);

        CashierController controller = new CashierController(
                cashierOrderService,
                adminOrderService,
                restaurantTableService,
                mock(ItemMenuService.class),
                mock(EmployeeService.class),
                systemConfigurationService,
                mock(CategoryService.class),
                mock(OrderRepository.class),
                mock(PromotionService.class),
                mock(BusinessHoursService.class),
                mock(WebSocketNotificationService.class),
                dateTimeService,
                mock(ReservationService.class),
                mock(com.aatechsolutions.elgransazon.application.service.CashRegisterService.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                // The assertions read the model, so HTML is never rendered.
                .setViewResolvers((viewName, locale) -> noOpView(viewName))
                .build();

        Company company = new Company();
        company.setIdCompany(3L);
        CompanyContext.setCurrentCompany(company);

        when(dateTimeService.todayLocal()).thenReturn(LocalDate.of(2026, 9, 12));
        when(dateTimeService.startOfDayUtc(any())).thenReturn(LocalDateTime.of(2026, 9, 12, 0, 0));
        when(dateTimeService.endOfDayUtc(any())).thenReturn(LocalDateTime.of(2026, 9, 13, 0, 0));
        when(cashierOrderService.findOrdersByCurrentEmployee()).thenReturn(List.of());
        when(cashierOrderService.countPaidOrdersByUsernameAndDateRange(anyString(), any(), any())).thenReturn(0L);
        when(cashierOrderService.getRevenueByUsernameAndDateRange(anyString(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(restaurantTableService.findAllOrderByTableNumber()).thenReturn(List.of());
        // Staff permission ON: admin/gerente/cajero advance delivery orders one click
        when(systemConfigurationService.getConfiguration()).thenReturn(SystemConfiguration.builder()
                .enableOrderStatusPermission(true)
                .staffCanManageDeliveryOrders(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private Authentication cashier() {
        return new UsernamePasswordAuthenticationToken(CASHIER, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_CASHIER")));
    }

    private Order order(long id, OrderStatus status, String createdBy) {
        return Order.builder()
                .idOrder(id)
                .orderNumber("ORD-20260912-0" + id)
                .orderType(OrderType.DELIVERY)
                .status(status)
                .createdBy(createdBy)
                .createdAt(LocalDateTime.of(2026, 9, 12, 14, 0))
                .total(new BigDecimal("150.00"))
                .customerName("Cliente domicilio")
                .orderDetails(new ArrayList<>())
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Order> globalBoard(Order... orders) throws Exception {
        when(adminOrderService.findAll()).thenReturn(List.of(orders));

        MvcResult result = mockMvc.perform(get("/cashier/orders").principal(cashier())).andReturn();
        Map<String, Object> model = result.getModelAndView().getModel();
        return (List<Order>) model.get("unpaidOrders");
    }

    @Test
    @DisplayName("Un pedido EN CAMINO sigue en la tabla global del cajero")
    void onTheWayDeliveryOrderStaysOnTheBoard() throws Exception {
        Order onTheWay = order(90L, OrderStatus.ON_THE_WAY, "cliente");
        Order ready = order(91L, OrderStatus.READY, "cliente");

        List<Order> board = globalBoard(onTheWay, ready);

        assertThat(board).containsExactlyInAnyOrder(onTheWay, ready);
    }

    @Test
    @DisplayName("El cajero no ve en la global los pedidos que él mismo creó")
    void ordersCreatedByTheCashierStayOutOfTheGlobalBoard() throws Exception {
        Order own = order(92L, OrderStatus.ON_THE_WAY, CASHIER);
        when(cashierOrderService.findOrdersByCurrentEmployee()).thenReturn(List.of(own));

        assertThat(globalBoard(own)).isEmpty();
    }

    @Test
    @DisplayName("Un pedido cancelado o pagado por alguien más no entra en la tabla global")
    void closedOrdersStayOutOfTheGlobalBoard() throws Exception {
        Order cancelled = order(93L, OrderStatus.CANCELLED, "cliente");
        Order paidByAnother = order(94L, OrderStatus.PAID, "cliente");
        paidByAnother.setPaidBy(Employee.builder().idEmpleado(9L).username("otro").build());

        assertThat(globalBoard(cancelled, paidByAnother)).isEmpty();
    }

    @Test
    @DisplayName("Con el permiso activo el cajero recibe la bandera para avanzar el reparto")
    void theStaffDeliveryPermissionReachesTheTemplate() throws Exception {
        MvcResult result = mockMvc.perform(get("/cashier/orders").principal(cashier())).andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        assertThat(model.get("staffOrderStatusEnabled")).isEqualTo(true);
        assertThat(model.get("staffDeliveryEnabled")).isEqualTo(true);
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
