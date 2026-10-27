package com.aatechsolutions.elgransazon.application.dto;

/**
 * One reward order that was created by a redemption and still has to be
 * closed. It is shown on the cashier loyalty screen so staff can hand the
 * free item over and settle the order without collecting money.
 *
 * @param orderId         order primary key
 * @param orderNumber     human readable order number
 * @param customerName    customer who redeemed the reward
 * @param rewardName      reward that generated the order
 * @param statusCode      order status enum name (READY, DELIVERED, ...), used to
 *                        keep the row in sync over the WebSocket
 * @param statusLabel     current order status in Spanish
 * @param canBeSettled    true when the order is READY or DELIVERED
 */
public record LoyaltyRewardOrderView(
        Long orderId,
        String orderNumber,
        String customerName,
        String rewardName,
        String statusCode,
        String statusLabel,
        boolean canBeSettled
) {
}
