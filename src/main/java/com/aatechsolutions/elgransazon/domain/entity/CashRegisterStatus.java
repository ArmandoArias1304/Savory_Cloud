package com.aatechsolutions.elgransazon.domain.entity;

/**
 * State of a cashier's cash-register session (apertura / cierre de caja).
 *
 * A cashier can only have ONE OPEN session at a time; the drawer is closed at
 * the end of the day and from then on it is read-only.
 */
public enum CashRegisterStatus {
    OPEN("Abierta"),
    CLOSED("Cerrada");

    private final String displayName;

    CashRegisterStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
