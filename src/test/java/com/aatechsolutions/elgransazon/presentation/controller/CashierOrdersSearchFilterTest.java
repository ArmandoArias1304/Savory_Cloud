package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.BusinessHoursService;
import com.aatechsolutions.elgransazon.application.service.CashRegisterService;
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
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * El buscador de la lista del cajero: un solo campo de texto que angosta los DOS tableros
 * (Mis pedidos y Pedidos Globales) con el mismo criterio, y que cuando el término trae la fecha
 * del folio fija el filtro de Fecha a ese día.
 *
 * <p>Se comprueba sobre el controlador real (MockMvc sin Spring, no hay MySQL en el entorno de
 * pruebas): si la búsqueda dejara de llegar al modelo, o si el folio pegado de otro día no
 * moviera el filtro de fecha, el cajero vería un tablero vacío sin saber por qué.</p>
 */
class CashierOrdersSearchFilterTest {

    private static final String CASHIER = "cajera";

    private CashierOrderServiceImpl cashierOrderService;
    private OrderService adminOrderService;
    private RestaurantTableService restaurantTableService;
    private DateTimeService dateTimeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cashierOrderService = mock(CashierOrderServiceImpl.class);
        adminOrderService = mock(OrderService.class);
        restaurantTableService = mock(RestaurantTableService.class);
        dateTimeService = mock(DateTimeService.class);

        CashierController controller = new CashierController(
                cashierOrderService,
                adminOrderService,
                restaurantTableService,
                mock(ItemMenuService.class),
                mock(EmployeeService.class),
                mock(SystemConfigurationService.class),
                mock(CategoryService.class),
                mock(OrderRepository.class),
                mock(PromotionService.class),
                mock(BusinessHoursService.class),
                mock(WebSocketNotificationService.class),
                dateTimeService,
                mock(ReservationService.class),
                mock(CashRegisterService.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                // Las aserciones leen el modelo: no hace falta pintar HTML.
                .setViewResolvers((viewName, locale) -> noOpView(viewName))
                .build();

        Company company = new Company();
        company.setIdCompany(3L);
        CompanyContext.setCurrentCompany(company);

        when(dateTimeService.todayLocal()).thenReturn(LocalDate.of(2026, 9, 12));
        when(dateTimeService.startOfDayUtc(any()))
                .thenAnswer(invocation -> ((LocalDate) invocation.getArgument(0)).atStartOfDay());
        when(dateTimeService.endOfDayUtc(any()))
                .thenAnswer(invocation -> ((LocalDate) invocation.getArgument(0)).atTime(23, 59, 59));

        when(cashierOrderService.findOrdersByCurrentEmployee()).thenReturn(List.of());
        when(cashierOrderService.countPaidOrdersByUsernameAndDateRange(anyString(), any(), any()))
                .thenReturn(0L);
        when(cashierOrderService.getRevenueByUsernameAndDateRange(anyString(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(restaurantTableService.findAllOrderByTableNumber()).thenReturn(List.of());
        when(adminOrderService.findAll()).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("Buscar por consecutivo deja solo ese pedido en los dos tableros")
    void theDaySequenceNarrowsBothBoards() throws Exception {
        Order mine007 = order(7L, "ORD-20260912-007", "Ana López", "5511111111", CASHIER);
        Order mine012 = order(12L, "ORD-20260912-012", "Beto Ruiz", "5522222222", CASHIER);
        Order global003 = order(3L, "ORD-20260912-003", "Carla Díaz", "5533333333", "cliente");
        Order yesterday007 = order(77L, "ORD-20260911-007", "Diego Soto", "5544444444", "cliente");
        when(cashierOrderService.findOrdersByCurrentEmployee()).thenReturn(List.of(mine007, mine012));
        when(adminOrderService.findAll()).thenReturn(List.of(global003, yesterday007));

        Map<String, Object> model = search("7");

        assertThat(orders(model, "myOrders")).containsExactly(mine007);
        assertThat(orders(model, "unpaidOrders")).containsExactly(yesterday007);
        assertThat(model.get("searchTerm")).isEqualTo("7");
        assertThat(model.get("searchResultCount")).isEqualTo(2);
    }

    @Test
    @DisplayName("El folio pegado de otro día fija la Fecha a ese día")
    void aFullFolioPinsTheDateFilter() throws Exception {
        Order fromYesterday = order(7L, "ORD-20260911-007", "Ana López", "5511111111", "cliente");
        Order today = order(8L, "ORD-20260912-008", "Beto Ruiz", "5522222222", "cliente");
        when(adminOrderService.findAll()).thenReturn(List.of(today, fromYesterday));

        Map<String, Object> model = search("ORD-20260911-007");

        assertThat(model.get("selectedDate")).isEqualTo("2026-09-11");
        assertThat(model.get("selectedDateValue")).isEqualTo(LocalDate.of(2026, 9, 11));
        // El pedido de ayer aparece y el de hoy queda fuera por el rango de fecha, no por el folio.
        assertThat(orders(model, "unpaidOrders")).containsExactly(fromYesterday);
    }

    @Test
    @DisplayName("El teléfono y el nombre del cliente también encuentran el pedido")
    void findsByPhoneAndCustomerName() throws Exception {
        Order byPhone = order(7L, "ORD-20260912-007", "Ana López", "5512345678", "cliente");
        Order byName = order(8L, "ORD-20260912-008", "José Ramírez", "5599999999", "cliente");
        when(adminOrderService.findAll()).thenReturn(List.of(byPhone, byName));

        assertThat(orders(search("+52 55 1234 5678"), "unpaidOrders")).containsExactly(byPhone);
        assertThat(orders(search("jose"), "unpaidOrders")).containsExactly(byName);
    }

    @Test
    @DisplayName("Sin término de búsqueda no se angosta nada")
    void withoutTermNothingIsFiltered() throws Exception {
        Order mine = order(7L, "ORD-20260912-007", "Ana López", "5511111111", CASHIER);
        Order other = order(8L, "ORD-20260912-008", "Beto Ruiz", "5522222222", "cliente");
        when(cashierOrderService.findOrdersByCurrentEmployee()).thenReturn(List.of(mine));
        when(adminOrderService.findAll()).thenReturn(List.of(other));

        Map<String, Object> model = list("/cashier/orders");

        assertThat(orders(model, "myOrders")).containsExactly(mine);
        assertThat(orders(model, "unpaidOrders")).containsExactly(other);
        assertThat(model.get("searchTerm")).isNull();
        assertThat(model.get("searchResultCount")).isEqualTo(2);
    }

    @SuppressWarnings("unchecked")
    private List<Order> orders(Map<String, Object> model, String attribute) {
        return (List<Order>) model.get(attribute);
    }

    private Map<String, Object> search(String term) throws Exception {
        // .param() y no querystring a mano: el término lleva espacios, "+" y acentos.
        MvcResult result = mockMvc.perform(get("/cashier/orders").param("search", term)
                        .principal(cashier()))
                .andReturn();
        return result.getModelAndView().getModel();
    }

    private Map<String, Object> list(String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url).principal(cashier())).andReturn();
        return result.getModelAndView().getModel();
    }

    private Authentication cashier() {
        return new UsernamePasswordAuthenticationToken(CASHIER, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_CASHIER")));
    }

    private Order order(long id, String orderNumber, String customerName, String phone, String createdBy) {
        // La fecha del pedido sale del propio folio (ORD-yyyyMMdd-NNN): así el rango de fecha que
        // el buscador puede fijar es coherente con el número que se escribió.
        LocalDate day = LocalDate.parse(orderNumber.substring(4, 12), DateTimeFormatter.BASIC_ISO_DATE);
        return Order.builder()
                .idOrder(id)
                .orderNumber(orderNumber)
                .orderType(OrderType.DINE_IN)
                .status(OrderStatus.DELIVERED)
                .paymentMethod(PaymentMethodType.CASH)
                .createdBy(createdBy)
                .createdAt(day.atTime(14, 0))
                .total(new BigDecimal("150.00"))
                .customerName(customerName)
                .customerPhone(phone)
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
