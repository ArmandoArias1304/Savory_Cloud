package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Types of movements written to the loyalty ledger.
 *
 * <p>The ledger is append-only: a correction is a new {@link #ADJUST} or
 * {@link #REVERSE} row, never an edit of a previous row. This is what makes
 * the customer balance auditable.</p>
 */
public enum LoyaltyStampEventType {

    EARN("Sello ganado"),
    REDEEM("Sello canjeado"),
    REVERSE("Reversión"),
    EXPIRE("Sellos vencidos"),
    ADJUST("Ajuste manual");

    private final String displayName;

    LoyaltyStampEventType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
