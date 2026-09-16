package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Type of a manual cash-register movement.
 *
 * The cashier records the money that leaves or enters the drawer outside of
 * sales: paying a supplier (hielo, gas, limpieza...), an extra cash injection
 * or the cash tips collected with the bill.
 */
public enum CashRegisterMovementType {

    /** Money paid out of the drawer (e.g. hielo, gas, limpieza). */
    EXPENSE("Pago", true),

    /** Extra cash added to the drawer. */
    INCOME("Entrada", false),

    /**
     * Cash tips collected with the bill. Order totals are stored without the tip, so
     * this money reaches the drawer on top of the sale and ADDS to it: the cashier
     * records it here to know how much of the counted cash belongs to the staff.
     */
    TIPS("Propinas efectivo", false),

    /**
     * Legacy value: cash tips were first stored as WITHDRAWAL, which subtracted from the
     * drawer and left the count short. It is kept so sessions saved before the fix still
     * load, now adding like {@link #TIPS}, and it is no longer offered when registering a
     * movement (see {@link #selectable()}).
     */
    WITHDRAWAL("Propinas efectivo", false);

    private final String displayName;
    private final boolean cashOut;

    CashRegisterMovementType(String displayName, boolean cashOut) {
        this.displayName = displayName;
        this.cashOut = cashOut;
    }

    /**
     * Types offered when registering a movement, in the order they are listed in the form.
     * Legacy values are left out so the cashier never sees the same concept twice.
     */
    public static CashRegisterMovementType[] selectable() {
        return new CashRegisterMovementType[]{EXPENSE, INCOME, TIPS};
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Whether this movement reduces the expected cash in the drawer. */
    public boolean isCashOut() {
        return cashOut;
    }
}
