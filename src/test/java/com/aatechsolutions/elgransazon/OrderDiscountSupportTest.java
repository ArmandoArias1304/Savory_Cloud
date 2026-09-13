package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.util.OrderDiscountSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server-side rules for capturing the order discount as a fixed amount ($) or a
 * percentage (%), including the departing-guest lock semantics.
 */
class OrderDiscountSupportTest {

    private Order order(String unitPrice, int qty) {
        BigDecimal price = new BigDecimal(unitPrice);
        OrderDetail detail = OrderDetail.builder()
                .idOrderDetail(1L)
                .itemName("Plato")
                .quantity(qty)
                .unitPrice(price)
                .subtotal(price.multiply(BigDecimal.valueOf(qty)))
                .itemStatus(OrderStatus.DELIVERED)
                .selectedComplements(new ArrayList<>())
                .build();
        Order order = Order.builder()
                .idOrder(1L)
                .orderNumber("ORD-1")
                .orderType(OrderType.DINE_IN)
                .status(OrderStatus.DELIVERED)
                .taxRate(new BigDecimal("16.00"))
                .paymentMethod(PaymentMethodType.CASH)
                .orderDetails(new ArrayList<>())
                .build();
        order.addOrderDetail(detail);
        order.recalculateAmounts();
        return order;
    }

    @Test
    void amountDiscountIsAccepted() {
        Order o = order("100.00", 2);
        OrderDiscountSupport.Resolved r = OrderDiscountSupport.resolve(
                o, "MONTO", new BigDecimal("50.00"), BigDecimal.ZERO, true, false);
        assertFalse(r.isPercentMode());
        assertEquals(new BigDecimal("50.00"), r.getAmount());
    }

    @Test
    void percentDiscountResolvesOverTotal() {
        Order o = order("100.00", 2);
        OrderDiscountSupport.Resolved r = OrderDiscountSupport.resolve(
                o, "PORCENTAJE", BigDecimal.ZERO, new BigDecimal("10.00"), true, false);
        assertTrue(r.isPercentMode());
        assertEquals(new BigDecimal("10.00"), r.getPercent());
        assertEquals(new BigDecimal("20.00"), r.getAmount());
    }

    @Test
    void sendingAmountAndPercentTogetherIsRejected() {
        Order o = order("100.00", 2);
        assertThrows(IllegalArgumentException.class, () -> OrderDiscountSupport.resolve(
                o, "PORCENTAJE", new BigDecimal("5.00"), new BigDecimal("10.00"), true, false));
        assertThrows(IllegalArgumentException.class, () -> OrderDiscountSupport.resolve(
                o, "MONTO", new BigDecimal("5.00"), new BigDecimal("10.00"), true, false));
    }

    @Test
    void percentOutOfRangeIsRejected() {
        Order o = order("100.00", 2);
        assertThrows(IllegalArgumentException.class, () -> OrderDiscountSupport.resolve(
                o, "PORCENTAJE", BigDecimal.ZERO, new BigDecimal("150"), true, false));
        assertThrows(IllegalArgumentException.class, () -> OrderDiscountSupport.resolve(
                o, "PORCENTAJE", BigDecimal.ZERO, new BigDecimal("-1"), true, false));
    }

    @Test
    void amountAboveRemainingIsRejected() {
        Order o = order("100.00", 2);
        assertThrows(IllegalArgumentException.class, () -> OrderDiscountSupport.resolve(
                o, "MONTO", new BigDecimal("250.00"), BigDecimal.ZERO, true, false));
    }

    @Test
    void departureOnlyAcceptsPercentAndLocks() {
        Order o = order("100.00", 2);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> OrderDiscountSupport.resolve(
                o, "MONTO", new BigDecimal("10.00"), BigDecimal.ZERO, true, true));
        assertTrue(ex.getMessage().contains("porcentaje"), ex.getMessage());

        OrderDiscountSupport.Resolved r = OrderDiscountSupport.resolve(
                o, "PORCENTAJE", BigDecimal.ZERO, new BigDecimal("10.00"), true, true);
        assertTrue(r.isPercentMode());
        assertTrue(r.isLock(), "a captured percentage in the departing-guest flow locks the order");
    }

    @Test
    void splitFlowOnlyAcceptsPercent() {
        Order o = order("100.00", 2);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> OrderDiscountSupport.resolve(
                o, "MONTO", new BigDecimal("10.00"), BigDecimal.ZERO, true, true));
        assertTrue(ex.getMessage().contains("porcentaje"), ex.getMessage());

        OrderDiscountSupport.Resolved r = OrderDiscountSupport.resolve(
                o, "PORCENTAJE", BigDecimal.ZERO, new BigDecimal("10.00"), true, true);
        assertTrue(r.isPercentMode());
        assertTrue(r.isLock(), "a split bill locks the captured percentage");
    }

    @Test
    void alreadyPartiallyChargedOrderOnlyAcceptsPercentAndLocks() {
        Order o = order("100.00", 2);
        // Simulate a prior collection: one unit of the line was already charged.
        o.getOrderDetails().get(0).addPaidQuantity(BigDecimal.ONE);
        assertTrue(o.hasPartialCollections());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> OrderDiscountSupport.resolve(
                o, "MONTO", new BigDecimal("10.00"), BigDecimal.ZERO, true, false));
        assertTrue(ex.getMessage().contains("porcentaje"), ex.getMessage());

        OrderDiscountSupport.Resolved r = OrderDiscountSupport.resolve(
                o, "PORCENTAJE", BigDecimal.ZERO, new BigDecimal("10.00"), true, false);
        assertTrue(r.isPercentMode());
        assertTrue(r.isLock());
    }

    @Test
    void lockedOrderRejectsDifferentPercentOrAmount() {
        Order o = order("100.00", 2);
        o.setOrderDiscountPercent(new BigDecimal("10.00"));
        o.setOrderDiscountLocked(true);
        o.recalculateAmounts();

        IllegalArgumentException diff = assertThrows(IllegalArgumentException.class, () -> OrderDiscountSupport.resolve(
                o, "PORCENTAJE", BigDecimal.ZERO, new BigDecimal("15.00"), true, false));
        assertTrue(diff.getMessage().contains("fijado en 10%"), diff.getMessage());

        assertThrows(IllegalArgumentException.class, () -> OrderDiscountSupport.resolve(
                o, "MONTO", new BigDecimal("10.00"), BigDecimal.ZERO, true, false));

        OrderDiscountSupport.Resolved ok = OrderDiscountSupport.resolve(
                o, "PORCENTAJE", BigDecimal.ZERO, new BigDecimal("10.00"), true, false);
        assertTrue(ok.isPercentMode());
        assertEquals(new BigDecimal("10.00"), ok.getPercent());
    }

    @Test
    void waitersGetNoDiscountUnlessLocked() {
        Order o = order("100.00", 2);
        assertEquals(BigDecimal.ZERO,
                OrderDiscountSupport.resolve(o, "MONTO", new BigDecimal("50.00"), BigDecimal.ZERO, false, false).getAmount());

        Order locked = order("100.00", 2);
        locked.setOrderDiscountPercent(new BigDecimal("10.00"));
        locked.setOrderDiscountLocked(true);
        locked.recalculateAmounts();
        OrderDiscountSupport.Resolved r = OrderDiscountSupport.resolve(
                locked, null, BigDecimal.ZERO, BigDecimal.ZERO, false, false);
        assertTrue(r.isPercentMode());
        assertEquals(new BigDecimal("10.00"), r.getPercent());
    }
}
