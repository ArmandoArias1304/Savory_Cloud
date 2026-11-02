package com.aatechsolutions.elgransazon.util;

import com.aatechsolutions.elgransazon.application.dto.OccupiedTableInfoDTO;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.domain.entity.TableStatus;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TableOccupancySupportTest {

    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 10, 30, 14, 30);

    private OrderRepository orderRepository;
    private DateTimeService dateTimeService;
    private Company company;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        dateTimeService = mock(DateTimeService.class);

        company = new Company();
        company.setIdCompany(1L);
        company.setName("Restaurante de prueba");
        CompanyContext.setCurrentCompany(company);

        when(dateTimeService.toCompanyTime(OPENED_AT)).thenReturn(OPENED_AT);
        when(dateTimeService.nowLocal()).thenReturn(OPENED_AT.plusMinutes(45));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("Resume la mesa ocupada con el mesero, el pedido, el tiempo y el total")
    void summarizesTheOccupiedTable() {
        RestaurantTable table = occupiedTable(3L, 7);
        Order order = activeOrder(table, "meseroA", "Juan", "Perez", OrderStatus.IN_PREPARATION);
        order.setCustomerName("Ana López");
        order.setTotal(new BigDecimal("250.00"));
        order.setOrderDetails(new ArrayList<>(List.of(detail(2), detail(1))));

        when(orderRepository.findActiveOrdersWithDetailsByTableIds(company, List.of(3L)))
                .thenReturn(List.of(order));

        Map<Long, OccupiedTableInfoDTO> occupancy = TableOccupancySupport.build(
                List.of(table), "waiter", "meseroA", false,
                orderRepository, dateTimeService);

        assertThat(occupancy).containsOnlyKeys(3L);
        OccupiedTableInfoDTO info = occupancy.get(3L);
        assertThat(info.orderId()).isEqualTo(77L);
        assertThat(info.orderNumber()).isEqualTo("ORD-20261030-077");
        assertThat(info.waiterName()).isEqualTo("Juan Perez");
        assertThat(info.waiterInitials()).isEqualTo("JP");
        assertThat(info.customerName()).isEqualTo("Ana López");
        assertThat(info.openedAt()).isEqualTo("14:30");
        assertThat(info.occupiedMinutes()).isEqualTo(45);
        assertThat(info.status()).isEqualTo("IN_PREPARATION");
        assertThat(info.statusDisplay()).isEqualTo("En preparación");
        assertThat(info.itemCount()).isEqualTo(3);
        assertThat(info.total()).isEqualByComparingTo("250.00");
        assertThat(info.canViewOrder()).isTrue();
        assertThat(info.orderUrl()).isEqualTo("/waiter/orders/view/77");
    }

    @Test
    @DisplayName("Un mesero ve quién tiene la mesa de un compañero, pero no puede abrir ese pedido")
    void waiterSeesTheOccupantWithoutAnOrderLinkWhenItIsNotTheirOrder() {
        RestaurantTable table = occupiedTable(3L, 7);
        Order order = activeOrder(table, "meseroA", "Juan", "Perez", OrderStatus.PENDING);
        when(orderRepository.findActiveOrdersWithDetailsByTableIds(company, List.of(3L)))
                .thenReturn(List.of(order));

        OccupiedTableInfoDTO info = TableOccupancySupport.build(
                List.of(table), "waiter", "meseroB", false,
                orderRepository, dateTimeService).get(3L);

        assertThat(info.waiterName()).isEqualTo("Juan Perez");
        assertThat(info.canViewOrder()).isFalse();
        assertThat(info.orderUrl()).isNull();
    }

    @Test
    @DisplayName("Admin y cajero pueden abrir el pedido de cualquier mesa")
    void adminAndCashierCanOpenAnyActiveOrder() {
        RestaurantTable table = occupiedTable(3L, 7);
        Order order = activeOrder(table, "meseroA", "Juan", "Perez", OrderStatus.READY);
        when(orderRepository.findActiveOrdersWithDetailsByTableIds(company, List.of(3L)))
                .thenReturn(List.of(order));

        OccupiedTableInfoDTO adminInfo = TableOccupancySupport.build(
                List.of(table), "admin", "admin", true,
                orderRepository, dateTimeService).get(3L);
        OccupiedTableInfoDTO cashierInfo = TableOccupancySupport.build(
                List.of(table), "cashier", "cajera", true,
                orderRepository, dateTimeService).get(3L);

        assertThat(adminInfo.canViewOrder()).isTrue();
        assertThat(adminInfo.orderUrl()).isEqualTo("/admin/orders/view/77");
        assertThat(cashierInfo.canViewOrder()).isTrue();
        assertThat(cashierInfo.orderUrl()).isEqualTo("/cashier/orders/view/77");
    }

    @Test
    @DisplayName("El gerente usa la ruta de admin en el enlace del pedido")
    void managerUsesTheAdminOrderRoute() {
        RestaurantTable table = occupiedTable(3L, 7);
        Order order = activeOrder(table, "meseroA", "Juan", "Perez", OrderStatus.READY);
        when(orderRepository.findActiveOrdersWithDetailsByTableIds(company, List.of(3L)))
                .thenReturn(List.of(order));

        OccupiedTableInfoDTO info = TableOccupancySupport.build(
                List.of(table), "manager", "gerente", true,
                orderRepository, dateTimeService).get(3L);

        assertThat(info.orderUrl()).isEqualTo("/admin/orders/view/77");
    }

    @Test
    @DisplayName("Si no hay mesas ocupadas no se consulta la base")
    void doesNotQueryWhenThereAreNoOccupiedTables() {
        RestaurantTable available = new RestaurantTable();
        available.setId(9L);
        available.setTableNumber(9);
        available.setStatus(TableStatus.AVAILABLE);

        Map<Long, OccupiedTableInfoDTO> occupancy = TableOccupancySupport.build(
                List.of(available), "waiter", "meseroA", false,
                orderRepository, dateTimeService);

        assertThat(occupancy).isEmpty();
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("Sin mesero asignado se muestra el usuario que capturó el pedido")
    void fallsBackToTheUserWhoCreatedTheOrder() {
        RestaurantTable table = occupiedTable(3L, 7);
        Order order = activeOrder(table, "meseroA", "Juan", "Perez", OrderStatus.PENDING);
        order.setEmployee(null);
        order.setCreatedBy("meseroA");
        when(orderRepository.findActiveOrdersWithDetailsByTableIds(company, List.of(3L)))
                .thenReturn(List.of(order));

        OccupiedTableInfoDTO info = TableOccupancySupport.build(
                List.of(table), "waiter", "meseroA", false,
                orderRepository, dateTimeService).get(3L);

        assertThat(info.waiterName()).isEqualTo("meseroA");
        assertThat(info.waiterInitials()).isEqualTo("M");
    }

    private RestaurantTable occupiedTable(Long id, int number) {
        RestaurantTable table = new RestaurantTable();
        table.setId(id);
        table.setTableNumber(number);
        table.setStatus(TableStatus.OCCUPIED);
        return table;
    }

    private Order activeOrder(RestaurantTable table, String username, String nombre, String apellido,
                              OrderStatus status) {
        Employee employee = new Employee();
        employee.setIdEmpleado(2L);
        employee.setUsername(username);
        employee.setNombre(nombre);
        employee.setApellido(apellido);

        Order order = new Order();
        order.setIdOrder(77L);
        order.setOrderNumber("ORD-20261030-077");
        order.setTable(table);
        order.setEmployee(employee);
        order.setCreatedBy(username);
        order.setStatus(status);
        order.setTotal(new BigDecimal("0.00"));
        order.setCreatedAt(OPENED_AT);
        order.setOrderDetails(new ArrayList<>());
        return order;
    }

    private OrderDetail detail(int quantity) {
        OrderDetail detail = new OrderDetail();
        detail.setQuantity(quantity);
        detail.setUnitPrice(new BigDecimal("50.00"));
        detail.setSubtotal(new BigDecimal("50.00").multiply(BigDecimal.valueOf(quantity)));
        return detail;
    }
}
