package com.aatechsolutions.elgransazon.application.dto;

/**
 * Small projection used by the staff customer picker. It intentionally does
 * not expose email, password or address data.
 */
public record LoyaltyCustomerView(
        Long id,
        String fullName,
        String phone,
        int balance,
        int stampsRequired,
        int lifetimeEarned,
        String lastVisitLabel
) {
    public int getRemainingForReward() {
        return Math.max(0, stampsRequired - balance);
    }
}
