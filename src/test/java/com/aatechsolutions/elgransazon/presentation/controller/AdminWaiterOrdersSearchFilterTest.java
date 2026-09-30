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
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.repository.ComplementRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComboItemRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComplementRepository;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.domain.repository.PaymentRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * El buscador de las listas de admin/gerente (/admin/orders) y del mesero (/waiter/orders): la
 * misma caja de texto y el mismo criterio que la lista del cajero, cada quien dentro de lo que ya
 * podía ver (el mesero sigue viendo solo sus pedidos).
 */
class AdminWaiterOrdersSearchFilterTest {

    private static final String ADMIN = "admin";

    private OrderService adminOrderService;
    private OrderService waiterOrderService;
    private DateTimeService dateTimeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminOrderService = mock(OrderService.class);
        waiterOrderService = mock(OrderService.class);
        dateTimeService = mock(DateTimeService.class);

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
                mock(SystemConfigurationService.class),
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
                dateTimeService,
                mock(CashRegisterService.class),
                mock(OrderTransferService.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                // Las aserciones leen el modelo: no hace falta pintar HTML.
                .setViewResolvers((viewName, locale) -> noOpView(viewName))
                .build();

        Company company = new Company();
        company.setIdCompany(3L);
        CompanyContext.setCurrentCompany(company);

        when(dateTimeService.todayLocal()).thenReturn(LocalDate.of(2026, 9, 29));
        when(dateTimeService.startOfDayUtc(any()))
                .thenAnswer(invocation -> ((LocalDate) invocation.getArgument(0)).atStartOfDay());
        when(dateTimeService.endOfDayUtc(any()))
                .thenAnswer(invocation -> ((LocalDate) invocation.getArgument(0)).atTime(23, 59, 59));

        when(adminOrderService.findAll()).thenReturn(List.of());
        when(adminOrderService.findByDateRange(any(), any())).thenReturn(List.of());
        when(waiterOrderService.findAll()).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("El admin encuentra por consecutivo y la lista sigue siendo admin/orders/list")
    void adminFindsBySequence() throws Exception {
        Order seven = order(7L, "ORD-20260929-007");
        Order five = order(5L, "ORD-20260929-005");
        when(adminOrderService.findAll()).thenReturn(List.of(seven, five));

        MvcResult result = search("/admin/orders?search=007", admin());

        assertThat(result.getModelAndView().getViewName()).isEqualTo("admin/orders/list");
        assertThat(orders(result)).containsExactly(seven);
        assertThat(result.getModelAndView().getModel().get("searchResultCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("El gerente usa la misma ruta y el mismo buscador")
    void managerSearchesOnTheSameRoute() throws Exception {
        Order five = order(5L, "ORD-20260929-005");
        when(adminOrderService.findAll()).thenReturn(List.of(order(7L, "ORD-20260929-007"), five));

        MvcResult result = search("/admin/orders?search=005", manager());

        assertThat(orders(result)).containsExactly(five);
    }

    @Test
    @DisplayName("El mesero busca dentro de sus propios pedidos")
    void waiterSearchesOnlyHisOrders() throws Exception {
        Order ana = order(7L, "ORD-20260929-007");
        ana.setCustomerName("Ana López");
        Order beto = order(8L, "ORD-20260929-008");
        beto.setCustomerName("Beto Ruiz");
        when(waiterOrderService.findAll()).thenReturn(List.of(ana, beto));

        MvcResult result = search("/waiter/orders?search=ana", waiter());

        assertThat(result.getModelAndView().getViewName()).isEqualTo("waiter/orders/list");
        assertThat(orders(result)).containsExactly(ana);
        // El mesero nunca consulta el servicio global: la búsqueda no amplía lo que ya veía.
        // (findAll se llama dos veces: la lista y las métricas del propio mesero.)
        verify(waiterOrderService, atLeastOnce()).findAll();
    }

    @Test
    @DisplayName("Un término con la fecha del folio consulta ese día, no hoy")
    void aFolioFromAnotherDayPinsTheDateRange() throws Exception {
        Order otherDay = order(4L, "ORD-20260831-004");
        when(adminOrderService.findByDateRange(any(), any())).thenReturn(List.of(otherDay));

        MvcResult result = search("/admin/orders?search=ORD-20260831-004", admin());

        assertThat(orders(result)).containsExactly(otherDay);
        assertThat(result.getModelAndView().getModel().get("selectedDate")).isEqualTo("2026-08-31");
        // El rango del día se usa para la lista y para las métricas del encabezado.
        verify(dateTimeService, atLeastOnce()).startOfDayUtc(LocalDate.of(2026, 8, 31));
    }

    @SuppressWarnings("unchecked")
    private List<Order> orders(MvcResult result) {
        return (List<Order>) result.getModelAndView().getModel().get("orders");
    }

    private MvcResult search(String url, Authentication authentication) throws Exception {
        return mockMvc.perform(get(url).principal(authentication)).andReturn();
    }

    private Authentication admin() {
        return principal(ADMIN, "ROLE_ADMIN");
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

    private Order order(long id, String orderNumber) {
        LocalDate day = LocalDate.parse(orderNumber.substring(4, 12), DateTimeFormatter.BASIC_ISO_DATE);
        return Order.builder()
                .idOrder(id)
                .orderNumber(orderNumber)
                .orderType(OrderType.DINE_IN)
                .status(OrderStatus.DELIVERED)
                .paymentMethod(PaymentMethodType.CASH)
                .createdAt(day.atTime(14, 0))
                .total(new BigDecimal("150.00"))
                .customerName("Cliente de prueba")
                .customerPhone("5599999999")
                .orderDetails(new ArrayList<>())
                .build();
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
