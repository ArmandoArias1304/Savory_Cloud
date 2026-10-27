package com.aatechsolutions.elgransazon.application.dto;

import java.time.LocalDateTime;

/**
 * Result of redeeming a reward. The created order already contains the reward
 * items at $0.00, so the cashier only has to close it as a normal sale.
 */
public record LoyaltyRedemptionResult(
        Long redemptionId,
        Long orderId,
        String orderNumber,
        String rewardName,
        int stampsSpent,
        int remainingBalance,
        LocalDateTime appliedAt
) {
}
