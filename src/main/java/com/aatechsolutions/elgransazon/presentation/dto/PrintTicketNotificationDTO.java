package com.aatechsolutions.elgransazon.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO sent via WebSocket to the printer agent when an order is paid.
 * Sent to the specific user (cashier/waiter/admin) who processed the payment,
 * so only their printer-agent prints — not all agents company-wide.
 * Topic: /topic/print/ticket/{companyId}/{username}
 * NOTE: DELIVERY orders do NOT trigger this notification (delivery person is remote).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PrintTicketNotificationDTO {
    private Long orderId;
    private String orderNumber;
    private Long companyId;
}
