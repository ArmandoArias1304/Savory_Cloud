package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.OrderTransferService;
import com.aatechsolutions.elgransazon.application.service.WebSocketNotificationService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.Role;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Traspaso de pedidos entre meseros: la solicitud solo la puede enviar el mesero que
 * atiende el pedido, el destino debe aceptarla para que el pedido cambie de dueño, y
 * {@code createdBy} nunca se reescribe.
 */
class OrderTransferServiceTest {

    private static final Long ORDER_ID = 77L;

    private OrderRepository orderRepository;
    private EmployeeRepository employeeRepository;
    private WebSocketNotificationService wsNotificationService;
    private OrderTransferService transferService;

    private Company company;
    private Employee waiterA;
    private Employee waiterB;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        wsNotificationService = mock(WebSocketNotificationService.class);
        transferService = new OrderTransferService(orderRepository, employeeRepository, wsNotificationService);

        company = new Company();
        company.setIdCompany(1L);
        CompanyContext.setCurrentCompany(company);

        waiterA = waiter(2L, "meseroA");
        waiterB = waiter(3L, "meseroB");

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void requestingATransferAsksTheTargetWaiterInRealTime() {
        Order order = orderOwnedBy(waiterA);
        when(orderRepository.findByIdWithLock(ORDER_ID)).thenReturn(Optional.of(order));
        when(employeeRepository.findById(waiterB.getIdEmpleado())).thenReturn(Optional.of(waiterB));

        Order result = transferService.requestTransfer(ORDER_ID, waiterB.getIdEmpleado(), "meseroA");

        assertEquals(waiterB, result.getTransferRequestedTo());
        assertNotNull(result.getTransferRequestedAt());
        assertEquals("meseroA", result.getCreatedBy(), "createdBy no se toca");
        assertEquals(waiterA, result.getEmployee(), "el pedido sigue siendo del mesero actual");
        verify(wsNotificationService).notifyTransferRequested(result, waiterA, waiterB);
    }

    @Test
    void onlyTheWaiterWhoAttendsTheOrderCanRequestTheTransfer() {
        Order order = orderOwnedBy(waiterA);
        when(orderRepository.findByIdWithLock(ORDER_ID)).thenReturn(Optional.of(order));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> transferService.requestTransfer(ORDER_ID, waiterB.getIdEmpleado(), "otroMesero"));

        assertTrue(error.getMessage().contains("atiende"), error.getMessage());
        verify(wsNotificationService, never()).notifyTransferRequested(any(), any(), any());
    }

    @Test
    void aTransferredAwayWaiterCannotRequestAnotherTransfer() {
        Order order = orderOwnedBy(waiterB);
        order.setCreatedBy("meseroA");
        when(orderRepository.findByIdWithLock(ORDER_ID)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class,
                () -> transferService.requestTransfer(ORDER_ID, waiterA.getIdEmpleado(), "meseroA"));
    }

    @Test
    void paidOrdersCannotBeTransferred() {
        Order order = orderOwnedBy(waiterA);
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findByIdWithLock(ORDER_ID)).thenReturn(Optional.of(order));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> transferService.requestTransfer(ORDER_ID, waiterB.getIdEmpleado(), "meseroA"));

        assertTrue(error.getMessage().contains("pagados"), error.getMessage());
    }

    @Test
    void onlyWaitersCanReceiveAnOrder() {
        Order order = orderOwnedBy(waiterA);
        when(orderRepository.findByIdWithLock(ORDER_ID)).thenReturn(Optional.of(order));
        Employee chef = employee(9L, "chef1", Role.CHEF);
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(chef));

        assertThrows(IllegalStateException.class,
                () -> transferService.requestTransfer(ORDER_ID, 9L, "meseroA"));
    }

    @Test
    void anInactiveWaiterCannotReceiveAnOrder() {
        Order order = orderOwnedBy(waiterA);
        when(orderRepository.findByIdWithLock(ORDER_ID)).thenReturn(Optional.of(order));
        waiterB.setEnabled(false);
        when(employeeRepository.findById(waiterB.getIdEmpleado())).thenReturn(Optional.of(waiterB));

        assertThrows(IllegalStateException.class,
                () -> transferService.requestTransfer(ORDER_ID, waiterB.getIdEmpleado(), "meseroA"));
    }

    @Test
    void theOrderCannotBeTransferredToItsOwnWaiter() {
        Order order = orderOwnedBy(waiterA);
        when(orderRepository.findByIdWithLock(ORDER_ID)).thenReturn(Optional.of(order));
        when(employeeRepository.findById(waiterA.getIdEmpleado())).thenReturn(Optional.of(waiterA));

        assertThrows(IllegalStateException.class,
                () -> transferService.requestTransfer(ORDER_ID, waiterA.getIdEmpleado(), "meseroA"));
    }

    @Test
    void acceptingMovesTheOrderToTheNewWaiterAndKeepsTheCreator() {
        Order order = orderOwnedBy(waiterA);
        order.setCreatedBy("meseroA");
        order.setTransferRequestedTo(waiterB);
        when(orderRepository.findByIdWithLock(ORDER_ID)).thenReturn(Optional.of(order));

        Order result = transferService.acceptTransfer(ORDER_ID, "meseroB");

        assertEquals(waiterB, result.getEmployee(), "el nuevo mesero queda a cargo");
        assertEquals(waiterA, result.getTransferredFrom(), "se registra de quién viene");
        assertNotNull(result.getTransferredAt());
        assertFalse(result.hasPendingTransfer());
        assertEquals("meseroA", result.getCreatedBy(), "la bitácora del creador no cambia");
        assertTrue(result.isOperatedBy("meseroB"));
        assertTrue(result.isReadOnlyFor("meseroA"));
        verify(wsNotificationService).notifyTransferResolved(result, waiterA, waiterB, true);
    }

    @Test
    void onlyTheTargetWaiterCanAcceptTheTransfer() {
        Order order = orderOwnedBy(waiterA);
        order.setTransferRequestedTo(waiterB);
        when(orderRepository.findByIdWithLock(ORDER_ID)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> transferService.acceptTransfer(ORDER_ID, "meseroA"));
        assertEquals(waiterA, order.getEmployee());
        verify(wsNotificationService, never()).notifyTransferResolved(any(), any(), any(), eq(true));
    }

    @Test
    void denyingKeepsTheOrderWithItsCurrentWaiter() {
        Order order = orderOwnedBy(waiterA);
        order.setTransferRequestedTo(waiterB);
        when(orderRepository.findByIdWithLock(ORDER_ID)).thenReturn(Optional.of(order));

        Order result = transferService.denyTransfer(ORDER_ID, "meseroB");

        assertEquals(waiterA, result.getEmployee());
        assertFalse(result.hasPendingTransfer());
        assertNull(result.getTransferredFrom());
        verify(wsNotificationService).notifyTransferResolved(result, waiterA, waiterB, false);
    }

    @Test
    void acceptingAfterTheOrderWasPaidDropsTheTransfer() {
        Order order = orderOwnedBy(waiterA);
        order.setTransferRequestedTo(waiterB);
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findByIdWithLock(ORDER_ID)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> transferService.acceptTransfer(ORDER_ID, "meseroB"));

        assertEquals(waiterA, order.getEmployee(), "una venta cerrada no cambia de dueño");
        assertFalse(order.hasPendingTransfer(), "la solicitud pendiente se descarta");
        verify(wsNotificationService, never()).notifyTransferResolved(any(), any(), any(), eq(true));
    }

    @Test
    void anotherCompanyOrderCannotBeTransferred() {
        Order order = orderOwnedBy(waiterA);
        Company otherCompany = new Company();
        otherCompany.setIdCompany(99L);
        order.setCompany(otherCompany);
        when(orderRepository.findByIdWithLock(ORDER_ID)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class,
                () -> transferService.requestTransfer(ORDER_ID, waiterB.getIdEmpleado(), "meseroA"));
    }

    @Test
    void pendingTransfersAreResolvedForTheLoggedInUser() {
        Order order = orderOwnedBy(waiterA);
        order.setTransferRequestedTo(waiterB);
        when(orderRepository.findPendingTransfersForUserAndCompany("meseroB", company))
                .thenReturn(java.util.List.of(order));

        assertEquals(1, transferService.findPendingTransfersFor("meseroB").size());
        assertTrue(transferService.findPendingTransfersFor(null).isEmpty());
    }

    private Order orderOwnedBy(Employee waiter) {
        Order order = new Order();
        order.setIdOrder(ORDER_ID);
        order.setOrderNumber("ORD-20260928-001");
        order.setStatus(OrderStatus.READY);
        order.setCompany(company);
        order.setEmployee(waiter);
        order.setCreatedBy(waiter.getUsername());
        order.setTotal(new BigDecimal("250.00"));
        return order;
    }

    private static Employee waiter(long id, String username) {
        return employee(id, username, Role.WAITER);
    }

    private static Employee employee(long id, String username, String role) {
        Employee employee = new Employee();
        employee.setIdEmpleado(id);
        employee.setUsername(username);
        employee.setNombre("Nombre" + id);
        employee.setApellido("Apellido" + id);
        employee.setEnabled(true);
        employee.setCompany(CompanyContext.getCurrentCompany());
        employee.setRoles(Set.of(new Role(role)));
        return employee;
    }
}
