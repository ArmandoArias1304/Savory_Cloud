package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Status of a stamp redemption.
 *
 * <p>{@code PENDING} reserves the stamps while the order is being built,
 * {@code APPLIED} links the redemption to the resulting order and
 * {@code CANCELLED} returns the stamps if the order is abandoned or cancelled
 * before payment.</p>
 */
public enum LoyaltyRedemptionStatus {

    PENDING("Pendiente"),
    APPLIED("Aplicado"),
    CANCELLED("Cancelado");

    private final String displayName;

    LoyaltyRedemptionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
