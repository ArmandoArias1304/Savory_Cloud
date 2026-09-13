package com.aatechsolutions.elgransazon.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO sent via WebSocket to the printer agents when an order is paid.
 * Broadcast to the whole company (topic /topic/print/ticket/{companyId}) so the ticket comes
 * out of the PC that actually has the ticket printer connected, whoever charged the order.
 * Agents without that printer skip it and the shared-printer case is resolved with the
 * exactly-once claim endpoint.
 *
 * <p>Two kinds of events share this topic:
 * <ul>
 *     <li>{@code paymentIds} empty: whole-order ticket (normal charge).</li>
 *     <li>{@code paymentIds} not empty: one ticket per account (split bill / departing guest).
 *         The whole-order ticket is NEVER printed for those: it is just the table total and is
 *         not handed to any customer.</li>
 * </ul>
 *
 * NOTE: DELIVERY orders do NOT trigger this notification (delivery person is remote).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PrintTicketNotificationDTO {

    private Long orderId;
    private String orderNumber;
    private Long companyId;

    /** Payment (account) ids to print. Empty means "whole order ticket". */
    private List<Long> paymentIds;

    /** Whole-order ticket event (no accounts). */
    public PrintTicketNotificationDTO(Long orderId, String orderNumber, Long companyId) {
        this(orderId, orderNumber, companyId, List.of());
    }
}
