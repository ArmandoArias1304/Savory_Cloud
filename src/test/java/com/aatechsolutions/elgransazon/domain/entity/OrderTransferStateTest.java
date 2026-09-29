package com.aatechsolutions.elgransazon.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reglas de propiedad del pedido cuando se transfiere entre meseros:
 * {@code createdBy} es la bitácora (nunca cambia) y {@code employee} es el responsable
 * vigente que opera el pedido y recibe la propina.
 */
class OrderTransferStateTest {

    @Test
    void theResponsibleWaiterOperatesTheOrderEvenIfSomeoneElseCreatedIt() {
        Employee cashier = employee(1L, "cajero");
        Employee waiter = employee(2L, "mesero");

        Order order = new Order();
        order.setCreatedBy("cajero");
        order.setEmployee(waiter);

        assertTrue(order.isOperatedBy("mesero"));
        assertFalse(order.isOperatedBy("cajero"), "quien capturó el pedido no lo opera");
    }

    @Test
    void legacyOrderWithoutResponsibleFallsBackToItsCreator() {
        Order order = new Order();
        order.setCreatedBy("mesero");
        order.setEmployee(null);

        assertTrue(order.isOperatedBy("mesero"));
        assertFalse(order.isReadOnlyFor("mesero"));
    }

    @Test
    void creatorKeepsReadOnlyVisibilityAfterTransferringTheOrder() {
        Employee previousOwner = employee(2L, "meseroA");
        Employee newOwner = employee(3L, "meseroB");

        Order order = new Order();
        order.setCreatedBy("meseroA");
        order.setEmployee(newOwner);
        order.setTransferredFrom(previousOwner);
        order.setTransferredAt(LocalDateTime.now());

        assertTrue(order.isReadOnlyFor("meseroA"), "el creador solo puede consultarlo");
        assertFalse(order.isOperatedBy("meseroA"));
        assertFalse(order.isReadOnlyFor("meseroB"));
        assertTrue(order.isOperatedBy("meseroB"), "el nuevo mesero es el dueño operativo");
        assertTrue(order.wasTransferred());
    }

    @Test
    void pendingTransferKeepsTheCurrentOwnerInCharge() {
        Employee owner = employee(2L, "meseroA");
        Employee target = employee(3L, "meseroB");

        Order order = new Order();
        order.setCreatedBy("meseroA");
        order.setEmployee(owner);
        order.setTransferRequestedTo(target);
        order.setTransferRequestedAt(LocalDateTime.now());

        assertTrue(order.hasPendingTransfer());
        assertFalse(order.wasTransferred(), "todavía no cambia de dueño");
        assertTrue(order.isOperatedBy("meseroA"));
        assertFalse(order.isOperatedBy("meseroB"));
    }

    @Test
    void paidAndCancelledOrdersCannotBeTransferred() {
        for (OrderStatus status : new OrderStatus[] { OrderStatus.PAID, OrderStatus.CANCELLED }) {
            Order order = new Order();
            order.setStatus(status);
            assertFalse(order.canBeTransferred(), "no se transfiere un pedido " + status);
        }

        for (OrderStatus status : new OrderStatus[] { OrderStatus.PENDING, OrderStatus.IN_PREPARATION,
                OrderStatus.READY, OrderStatus.DELIVERED }) {
            Order order = new Order();
            order.setStatus(status);
            assertTrue(order.canBeTransferred(), "debe poder transferirse un pedido " + status);
        }
    }

    @Test
    void aPendingRequestDoesNotBlockAskingAnotherColleague() {
        Order order = new Order();
        order.setStatus(OrderStatus.READY);
        order.setTransferRequestedTo(employee(3L, "meseroB"));

        assertTrue(order.canBeTransferred(), "el dueño puede re-solicitar a otro mesero");
    }

    @Test
    void legendNamesThePreviousAndCurrentWaitersWithTheTimestamp() {
        Employee previousOwner = employee(2L, "meseroA", "Juan", "Perez");
        Employee newOwner = employee(3L, "meseroB", "Luis", "Gomez");

        Order order = new Order();
        order.setEmployee(newOwner);
        order.setTransferredFrom(previousOwner);
        order.setTransferredAt(LocalDateTime.of(2026, 9, 28, 14, 30));

        assertEquals("Transferido de Juan Perez a Luis Gomez el 28/09/2026 14:30",
                order.getTransferLegend());
        assertEquals("Luis Gomez", order.getOwnerName());
    }

    @Test
    void orderWithoutTransfersHasNoLegend() {
        Order order = new Order();
        order.setCreatedBy("meseroA");
        order.setEmployee(employee(2L, "meseroA", "Juan", "Perez"));

        assertFalse(order.wasTransferred());
        assertEquals("", order.getTransferLegend());
        assertEquals("Juan Perez", order.getOwnerName());
    }

    @Test
    void tipFollowsTheTransferredOrder() {
        Employee previousOwner = employee(2L, "meseroA");
        Employee newOwner = employee(3L, "meseroB");

        Order order = new Order();
        order.setOrderType(OrderType.DINE_IN);
        order.setEmployee(newOwner);
        order.setTransferredFrom(previousOwner);

        assertTrue(order.tipBelongsTo(newOwner));
        assertFalse(order.tipBelongsTo(previousOwner));
    }

    private static Employee employee(long id, String username) {
        return employee(id, username, "Nombre" + id, "Apellido" + id);
    }

    private static Employee employee(long id, String username, String nombre, String apellido) {
        Employee employee = new Employee();
        employee.setIdEmpleado(id);
        employee.setUsername(username);
        employee.setNombre(nombre);
        employee.setApellido(apellido);
        employee.setEnabled(true);
        return employee;
    }
}
