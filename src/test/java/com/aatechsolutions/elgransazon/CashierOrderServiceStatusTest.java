package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.CashierOrderServiceImpl;
import com.aatechsolutions.elgransazon.application.service.OrderServiceImpl;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The cashier's own status rules must allow the delivery advance flow
 * (READY -> ON_THE_WAY -> DELIVERED) that the list button triggers, while still
 * rejecting transitions a cashier should never make.
 */
class CashierOrderServiceStatusTest {

    private OrderServiceImpl delegate;
    private CashierOrderServiceImpl cashierService;

    @BeforeEach
    void setUp() {
        delegate = mock(OrderServiceImpl.class);
        when(delegate.changeStatus(anyLong(), any(OrderStatus.class), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0, Long.class) == null ? null : orderOfType(OrderType.DINE_IN, OrderStatus.READY));
        cashierService = new CashierOrderServiceImpl(delegate);
    }

    private static Order orderOfType(OrderType type, OrderStatus status) {
        Order order = new Order();
        order.setOrderType(type);
        order.setStatus(status);
        return order;
    }

    private void expectAllowed(OrderType type, OrderStatus from, OrderStatus to) {
        Order order = orderOfType(type, from);
        when(delegate.findByIdOrThrow(7L)).thenReturn(order);
        when(delegate.changeStatus(7L, to, "cajero")).thenReturn(order);
        assertDoesNotThrow(() -> cashierService.changeStatus(7L, to, "cajero"),
                "Se esperaba permitir " + from + " -> " + to + " en " + type);
    }

    private void expectRejected(OrderType type, OrderStatus from, OrderStatus to) {
        Order order = orderOfType(type, from);
        when(delegate.findByIdOrThrow(7L)).thenReturn(order);
        assertThrows(IllegalStateException.class, () -> cashierService.changeStatus(7L, to, "cajero"),
                "Se esperaba rechazar " + from + " -> " + to + " en " + type);
    }

    @Test
    void cashierCanSendDeliveryOrderOnTheWay() {
        expectAllowed(OrderType.DELIVERY, OrderStatus.READY, OrderStatus.ON_THE_WAY);
    }

    @Test
    void cashierCanMarkDeliveryOrderDeliveredFromOnTheWay() {
        expectAllowed(OrderType.DELIVERY, OrderStatus.ON_THE_WAY, OrderStatus.DELIVERED);
    }

    @Test
    void cashierKeepsOriginalTransitions() {
        expectAllowed(OrderType.DINE_IN, OrderStatus.READY, OrderStatus.DELIVERED);
        expectAllowed(OrderType.TAKEOUT, OrderStatus.READY, OrderStatus.DELIVERED);
        expectAllowed(OrderType.DELIVERY, OrderStatus.DELIVERED, OrderStatus.PAID);
    }

    @Test
    void cashierCannotPutANonDeliveryOrderOnTheWay() {
        expectRejected(OrderType.DINE_IN, OrderStatus.READY, OrderStatus.ON_THE_WAY);
    }

    @Test
    void cashierCannotSkipDeliverySteps() {
        // DELIVERED can only go to PAID, never back through the delivery flow
        expectRejected(OrderType.DELIVERY, OrderStatus.DELIVERED, OrderStatus.ON_THE_WAY);
        // READY cannot jump straight to PENDING
        expectRejected(OrderType.DELIVERY, OrderStatus.READY, OrderStatus.PENDING);
    }
}
