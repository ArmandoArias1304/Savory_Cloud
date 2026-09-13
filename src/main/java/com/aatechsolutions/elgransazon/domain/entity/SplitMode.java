package com.aatechsolutions.elgransazon.domain.entity;

/**
 * How a bill was split into per-person accounts (Payment rows).
 *
 * ITEMS: each person pays for the items explicitly assigned to them in whole
 *        units (an order line of qty 4 is 4 assignable units; the sum across
 *        accounts must equal the line quantity).
 *
 * EQUAL ("Partes iguales") is no longer created: it prorated quantities per
 * person (fractional products), which is not valid before the SAT — a product
 * cannot be divided. The constant is kept only to read historical payments
 * that still carry splitMode = EQUAL.
 */
public enum SplitMode {
    /** Legacy: kept only for historical payments, never created anymore. */
    @Deprecated
    EQUAL("Partes iguales"),
    ITEMS("Por persona");

    private final String displayName;

    SplitMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}