package com.aatechsolutions.elgransazon.application.event;

/**
 * Published after an order is marked PAID and the transaction committed.
 * Listeners must be idempotent because payment flows retry.
 */
public record OrderPaidEvent(Long orderId, String paidBy) {
}
