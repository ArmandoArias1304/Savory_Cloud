package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.util.DeliveryStatusSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rules for advancing a DELIVERY order's status from the staff lists.
 *
 * <p>Only the repartidor, or admin/gerente/cajero when the configuration
 * permission is enabled, may advance READY -> ON_THE_WAY -> DELIVERED. The waiter
 * is never allowed to advance a delivery order.</p>
 */
class DeliveryStatusSupportTest {

    private Order deliveryOrder(OrderStatus status) {
        return Order.builder()
                .idOrder(1L)
                .orderNumber("ORD-1")
                .orderType(OrderType.DELIVERY)
                .status(status)
                .build();
    }

    private Order dineInOrder(OrderStatus status) {
        return Order.builder()
                .idOrder(2L)
                .orderNumber("ORD-2")
                .orderType(OrderType.DINE_IN)
                .status(status)
                .build();
    }

    private SystemConfiguration config(boolean parent, boolean deliveryChild) {
        return SystemConfiguration.builder()
                .enableOrderStatusPermission(parent)
                .staffCanManageDeliveryOrders(deliveryChild)
                .build();
    }

    @Test
    void nextDeliveryStatusMovesReadyToOnTheWay() {
        assertEquals(OrderStatus.ON_THE_WAY,
                DeliveryStatusSupport.nextDeliveryStatus(deliveryOrder(OrderStatus.READY)));
    }

    @Test
    void nextDeliveryStatusMovesOnTheWayToDelivered() {
        assertEquals(OrderStatus.DELIVERED,
                DeliveryStatusSupport.nextDeliveryStatus(deliveryOrder(OrderStatus.ON_THE_WAY)));
    }

    @Test
    void nextDeliveryStatusIsNullForNonActionableDeliveryStatus() {
        assertNull(DeliveryStatusSupport.nextDeliveryStatus(deliveryOrder(OrderStatus.DELIVERED)));
        assertNull(DeliveryStatusSupport.nextDeliveryStatus(deliveryOrder(OrderStatus.PENDING)));
    }

    @Test
    void nextDeliveryStatusIsNullForNonDeliveryOrders() {
        assertNull(DeliveryStatusSupport.nextDeliveryStatus(dineInOrder(OrderStatus.READY)));
    }

    @Test
    void adminManagerCashierCanAdvanceOnlyWhenFlagEnabled() {
        SystemConfiguration on = config(true, true);
        SystemConfiguration off = config(true, false);
        Order ready = deliveryOrder(OrderStatus.READY);

        assertTrue(DeliveryStatusSupport.canStaffAdvanceDelivery("admin", ready, on));
        assertTrue(DeliveryStatusSupport.canStaffAdvanceDelivery("manager", ready, on));
        assertTrue(DeliveryStatusSupport.canStaffAdvanceDelivery("cashier", ready, on));
        // Parent flag alone is not enough.
        assertFalse(DeliveryStatusSupport.canStaffAdvanceDelivery("admin", ready, off));
        // Child flag alone is not enough.
        assertFalse(DeliveryStatusSupport.canStaffAdvanceDelivery("admin", ready, config(false, true)));
        assertFalse(DeliveryStatusSupport.canStaffAdvanceDelivery("admin", ready, null));
    }

    @Test
    void waiterIsNeverAllowedToAdvanceDeliveryOrders() {
        Order ready = deliveryOrder(OrderStatus.READY);
        assertFalse(DeliveryStatusSupport.canStaffAdvanceDelivery("waiter", ready, config(true, true)));
        assertFalse(DeliveryStatusSupport.isStatusChangeAllowed(
                "waiter", ready, OrderStatus.ON_THE_WAY, config(true, true)));
        assertFalse(DeliveryStatusSupport.isStatusChangeAllowed(
                "waiter", ready, OrderStatus.DELIVERED, config(true, true)));
    }

    @Test
    void repartidorKeepsItsAbilityRegardlessOfFlag() {
        Order ready = deliveryOrder(OrderStatus.READY);
        assertTrue(DeliveryStatusSupport.isStatusChangeAllowed(
                "delivery", ready, OrderStatus.ON_THE_WAY, config(false, false)));
        assertTrue(DeliveryStatusSupport.isStatusChangeAllowed(
                "delivery", ready, OrderStatus.ON_THE_WAY, null));
    }

    @Test
    void staffCanOnlyJumpToTheImmediateNextDeliveryStatus() {
        Order ready = deliveryOrder(OrderStatus.READY);
        SystemConfiguration on = config(true, true);
        // Skipping ON_THE_WAY is not allowed.
        assertFalse(DeliveryStatusSupport.isStatusChangeAllowed(
                "admin", ready, OrderStatus.DELIVERED, on));
        assertTrue(DeliveryStatusSupport.isStatusChangeAllowed(
                "admin", ready, OrderStatus.ON_THE_WAY, on));

        Order onTheWay = deliveryOrder(OrderStatus.ON_THE_WAY);
        assertTrue(DeliveryStatusSupport.isStatusChangeAllowed(
                "cashier", onTheWay, OrderStatus.DELIVERED, on));
    }

    @Test
    void nonDeliveryOrdersAreUnaffectedByTheGuard() {
        Order dineIn = dineInOrder(OrderStatus.READY);
        // Waiters can still mark a READY dine-in order as DELIVERED.
        assertTrue(DeliveryStatusSupport.isStatusChangeAllowed(
                "waiter", dineIn, OrderStatus.DELIVERED, null));
        // Non-delivery orders keep their own transitions.
        assertTrue(DeliveryStatusSupport.isStatusChangeAllowed(
                "admin", dineIn, OrderStatus.DELIVERED, null));
    }
}
