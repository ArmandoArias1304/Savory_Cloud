package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Enum representing the unit used to count an inventory object
 *
 * Examples:
 * - PIEZA: one television, one pot
 * - SET: a set of pans
 * - JUEGO: a set of cutlery
 * - CAJA: a box of glasses
 * - OTRO: anything else
 */
public enum ObjectUnit {
    PIEZA("Pieza"),
    SET("Set"),
    JUEGO("Juego"),
    CAJA("Caja"),
    OTRO("Otro");

    private final String displayName;

    ObjectUnit(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get unit by display name
     */
    public static ObjectUnit fromDisplayName(String displayName) {
        for (ObjectUnit unit : values()) {
            if (unit.displayName.equalsIgnoreCase(displayName)) {
                return unit;
            }
        }
        throw new IllegalArgumentException("Unknown object unit: " + displayName);
    }
}
