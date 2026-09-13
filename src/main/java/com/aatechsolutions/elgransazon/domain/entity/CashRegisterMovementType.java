package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Type of a manual cash-register movement.
 *
 * The cashier records the money that leaves or enters the drawer outside of
 * sales: paying a supplier (hielo, gas, limpieza...), an extra cash injection
 * or a withdrawal/safe drop.
 */
public enum CashRegisterMovementType {

    /** Money paid out of the drawer (e.g. hielo, gas, limpieza). */
    EXPENSE("Pago", true),

    /** Extra cash added to the drawer. */
    INCOME("Entrada", false),

    /** Money taken out of the drawer (retiro / caja chica). */
    WITHDRAWAL("Retiro", true);

    private final String displayName;
    private final boolean cashOut;

    CashRegisterMovementType(String displayName, boolean cashOut) {
        this.displayName = displayName;
        this.cashOut = cashOut;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Whether this movement reduces the expected cash in the drawer. */
    public boolean isCashOut() {
        return cashOut;
    }
}
