package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Enum representing the status of a restaurant table
 * 
 * States:
 * - AVAILABLE: Table is free and can be used
 * - OCCUPIED: Table has an active order (not paid yet)
 * - OUT_OF_SERVICE: Table is temporarily unavailable
 * 
 * State transitions:
 * - AVAILABLE -> OCCUPIED: When an order is created for this table
 * - OCCUPIED -> AVAILABLE: When the order is paid
 * - AVAILABLE <-> OUT_OF_SERVICE: Manual change (only from edit form when AVAILABLE)
 */
public enum TableStatus {
    AVAILABLE("Disponible"),
    OCCUPIED("Ocupada"),
    OUT_OF_SERVICE("Fuera de Servicio");

    private final String displayName;

    TableStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get status by display name
     */
    public static TableStatus fromDisplayName(String displayName) {
        for (TableStatus status : values()) {
            if (status.displayName.equalsIgnoreCase(displayName)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown table status: " + displayName);
    }

    /**
     * Check if table can be used for orders
     */
    public boolean canBeUsedForOrders() {
        return this == AVAILABLE;
    }

    /**
     * Check if table status can be manually changed
     * Only AVAILABLE tables can have their status changed manually
     */
    public boolean canBeManuallyChanged() {
        return this == AVAILABLE || this == OUT_OF_SERVICE;
    }
}
