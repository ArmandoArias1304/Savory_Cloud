package com.aatechsolutions.elgransazon.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderTipBeneficiaryTest {

    @Test
    void dineInTipBelongsToCreatorNotCollector() {
        Employee waiter = employee(1L);
        Employee cashier = employee(2L);

        Order order = new Order();
        order.setOrderType(OrderType.DINE_IN);
        order.setEmployee(waiter);
        order.setPaidBy(cashier);

        assertEquals(waiter, order.getTipBeneficiary());
        assertTrue(order.tipBelongsTo(waiter));
        assertFalse(order.tipBelongsTo(cashier));
    }

    @Test
    void takeoutTipBelongsToCreator() {
        Employee cashier = employee(2L);

        Order order = new Order();
        order.setOrderType(OrderType.TAKEOUT);
        order.setEmployee(cashier);
        order.setPaidBy(cashier);

        assertTrue(order.tipBelongsTo(cashier));
    }

    @Test
    void deliveryTipBelongsToRiderEvenIfCashierCollected() {
        Employee cashier = employee(2L);
        Employee rider = employee(3L);

        Order order = new Order();
        order.setOrderType(OrderType.DELIVERY);
        order.setEmployee(cashier);
        order.setPaidBy(cashier);
        order.setDeliveredBy(rider);

        assertEquals(rider, order.getTipBeneficiary());
        assertTrue(order.tipBelongsTo(rider));
        assertFalse(order.tipBelongsTo(cashier));
    }

    @Test
    void deliveryWithoutRiderFallsBackToCreator() {
        Employee cashier = employee(2L);

        Order order = new Order();
        order.setOrderType(OrderType.DELIVERY);
        order.setEmployee(cashier);
        order.setPaidBy(cashier);

        assertTrue(order.tipBelongsTo(cashier));
    }

    private static Employee employee(Long id) {
        Employee emp = new Employee();
        emp.setIdEmpleado(id);
        return emp;
    }
}
