package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Enum representing the physical condition of an inventory object
 *
 * States:
 * - NUEVO: Recently bought / never used
 * - BUENO: In good working condition
 * - REGULAR: Works but shows wear
 * - DAÑADO: Damaged, still owned but not usable
 * - DESECHADO: Discarded / no longer counted as an asset
 */
public enum ObjectCondition {
    NUEVO("Nuevo"),
    BUENO("Bueno"),
    REGULAR("Regular"),
    DANADO("Dañado"),
    DESECHADO("Desechado");

    private final String displayName;

    ObjectCondition(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get condition by display name
     */
    public static ObjectCondition fromDisplayName(String displayName) {
        for (ObjectCondition condition : values()) {
            if (condition.displayName.equalsIgnoreCase(displayName)) {
                return condition;
            }
        }
        throw new IllegalArgumentException("Unknown object condition: " + displayName);
    }

    /**
     * Check if the object is still usable
     */
    public boolean isUsable() {
        return this == NUEVO || this == BUENO || this == REGULAR;
    }
}
