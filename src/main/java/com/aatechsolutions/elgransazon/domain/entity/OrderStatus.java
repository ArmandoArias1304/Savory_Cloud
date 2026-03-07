package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Enum representing the status of an order
 */
public enum OrderStatus {
    PENDING("Pendiente"),
    IN_PREPARATION("En preparación"),
    READY("Listo"),
    ON_THE_WAY("En camino"),      // Solo para DELIVERY
    DELIVERED("Entregado"),
    CANCELLED("Cancelado"),
    PAID("Pagado");               // Para módulo futuro de pagos

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if this status allows cancellation
     * Cannot cancel if: ON_THE_WAY, DELIVERED, PAID, or already CANCELLED
     */
    public boolean canBeCancelled() {
        return this != ON_THE_WAY && this != DELIVERED && this != PAID && this != CANCELLED;
    }

    /**
     * Check if this status should return stock when cancelled
     * Only PENDING returns stock automatically
     * For IN_PREPARATION and READY, stock must be returned manually
     */
    public boolean shouldReturnStockOnCancel() {
        return this == PENDING;
    }

    /**
     * Get valid next statuses based on order type
     */
    public static OrderStatus[] getValidNextStatuses(OrderStatus current, OrderType orderType) {
        if (current == null) {
            return new OrderStatus[]{PENDING};
        }

        switch (current) {
            case PENDING:
                return new OrderStatus[]{IN_PREPARATION};
            case IN_PREPARATION:
                return new OrderStatus[]{READY};
            case READY:
                // DELIVERY can go to ON_THE_WAY, others go directly to DELIVERED
                if (orderType == OrderType.DELIVERY) {
                    return new OrderStatus[]{ON_THE_WAY};
                } else {
                    return new OrderStatus[]{DELIVERED};
                }
            case ON_THE_WAY:
                return new OrderStatus[]{DELIVERED};
            case DELIVERED:
                return new OrderStatus[]{PAID};
            case PAID:
            case CANCELLED:
                return new OrderStatus[]{};  // No more transitions
            default:
                return new OrderStatus[]{};
        }
    }

    /**
     * Validate if a status transition is allowed
     */
    public static boolean isValidTransition(OrderStatus from, OrderStatus to, OrderType orderType) {
        if (from == null || to == null) {
            return false;
        }

        OrderStatus[] validStatuses = getValidNextStatuses(from, orderType);
        for (OrderStatus valid : validStatuses) {
            if (valid == to) {
                return true;
            }
        }
        return false;
    }
}
