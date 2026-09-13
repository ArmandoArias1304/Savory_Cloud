package com.aatechsolutions.elgransazon.util;

import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;

/**
 * Shared rules for advancing a DELIVERY order's status by staff.
 *
 * <p>Normally only the repartidor (ROLE_DELIVERY) can advance a delivery order:
 * {@code READY (Listo) -> ON_THE_WAY (En camino) -> DELIVERED (Entregado)}.</p>
 *
 * <p>When the admin enables the system-configuration permission
 * ({@code enableOrderStatusPermission} AND {@code staffCanManageDeliveryOrders}),
 * <b>admin, gerente and cajero</b> gain the same one-click ability. The waiter is
 * intentionally excluded from this permission, and the repartidor keeps its own
 * ability regardless of the flag.</p>
 */
public final class DeliveryStatusSupport {

    private DeliveryStatusSupport() {
    }

    /**
     * The repartidor keeps its own ability to advance delivery orders, independently
     * of the staff permission flag.
     */
    public static boolean isDeliveryRole(String role) {
        return role != null && role.equalsIgnoreCase("delivery");
    }

    /**
     * Roles allowed to advance delivery orders through the staff permission.
     * Deliberately excludes the waiter.
     */
    public static boolean isStaffRoleAllowed(String role) {
        if (role == null) {
            return false;
        }
        String lower = role.toLowerCase();
        return lower.equals("admin") || lower.equals("manager") || lower.equals("cashier");
    }

    /**
     * Whether the staff-managed delivery permission is enabled in the configuration.
     * The parent flag gates the child flag, exactly like the chef/barista/parrillero
     * sub-permissions.
     */
    public static boolean isDeliveryFlagEnabled(SystemConfiguration cfg) {
        return cfg != null
                && Boolean.TRUE.equals(cfg.getEnableOrderStatusPermission())
                && Boolean.TRUE.equals(cfg.getStaffCanManageDeliveryOrders());
    }

    /**
     * Next status when a DELIVERY order is advanced by staff:
     * {@code READY -> ON_THE_WAY}, {@code ON_THE_WAY -> DELIVERED}.
     *
     * @return the next status, or {@code null} when the order is not an advanceable
     *         delivery order (wrong type or terminal/intermediate status).
     */
    public static OrderStatus nextDeliveryStatus(Order order) {
        if (order == null || order.getOrderType() != OrderType.DELIVERY) {
            return null;
        }
        if (order.getStatus() == OrderStatus.READY) {
            return OrderStatus.ON_THE_WAY;
        }
        if (order.getStatus() == OrderStatus.ON_THE_WAY) {
            return OrderStatus.DELIVERED;
        }
        return null;
    }

    /**
     * Whether a staff role may advance the given delivery order right now.
     */
    public static boolean canStaffAdvanceDelivery(String role, Order order, SystemConfiguration cfg) {
        return isStaffRoleAllowed(role)
                && isDeliveryFlagEnabled(cfg)
                && nextDeliveryStatus(order) != null;
    }

    /**
     * Server-side guard for a status-change attempt on a delivery order.
     *
     * <p>Non-delivery orders, and changes that are not the delivery advance
     * transition, are always allowed here (other rules still apply downstream).
     * On a delivery order, {@code ON_THE_WAY} / {@code DELIVERED} is only allowed
     * for the repartidor or for admin/gerente/cajero when the permission is on and
     * the target status is exactly the next delivery status.</p>
     */
    public static boolean isStatusChangeAllowed(String role,
                                                Order order,
                                                OrderStatus newStatus,
                                                SystemConfiguration cfg) {
        if (order == null || order.getOrderType() != OrderType.DELIVERY) {
            return true;
        }
        if (newStatus != OrderStatus.ON_THE_WAY && newStatus != OrderStatus.DELIVERED) {
            return true;
        }
        if (isDeliveryRole(role)) {
            return true;
        }
        return canStaffAdvanceDelivery(role, order, cfg) && newStatus == nextDeliveryStatus(order);
    }
}
