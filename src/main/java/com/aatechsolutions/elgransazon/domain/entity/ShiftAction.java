package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Enum representing actions performed on employee shift assignments
 */
public enum ShiftAction {
    ASSIGNED("Asignado"),
    REMOVED("Removido");

    private final String displayName;

    ShiftAction(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get ShiftAction from display name
     */
    public static ShiftAction fromDisplayName(String displayName) {
        for (ShiftAction action : values()) {
            if (action.displayName.equalsIgnoreCase(displayName)) {
                return action;
            }
        }
        throw new IllegalArgumentException("No enum constant for display name: " + displayName);
    }
}
