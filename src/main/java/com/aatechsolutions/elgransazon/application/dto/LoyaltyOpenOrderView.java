package com.aatechsolutions.elgransazon.application.dto;

import java.math.BigDecimal;

/**
 * One open dine-in bill a waiter can attach a redemption to, so the free items
 * land on the table's bill and the kitchen knows where they go.
 *
 * @param orderId      order primary key
 * @param orderNumber  human readable order number
 * @param tableLabel   "Mesa 4", or "Sin mesa" when the order has no table
 * @param customerName registered customer or the name typed on the order
 * @param statusLabel  current order status in Spanish
 * @param total        current bill total (the redemption does not change it)
 */
public record LoyaltyOpenOrderView(
        Long orderId,
        String orderNumber,
        String tableLabel,
        String customerName,
        String statusLabel,
        BigDecimal total
) {
}
