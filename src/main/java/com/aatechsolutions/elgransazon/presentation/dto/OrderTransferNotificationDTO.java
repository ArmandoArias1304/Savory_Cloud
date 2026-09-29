package com.aatechsolutions.elgransazon.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Payload sent over WebSocket for the waiter-to-waiter order transfer flow.
 *
 * <p>Delivery is per user ({@code /user/{username}/queue/order-transfers}), never a
 * broadcast: only the waiter who has to answer (or the one waiting for the answer)
 * cares about the event.</p>
 *
 * <p>{@code notificationType} values:</p>
 * <ul>
 *     <li>{@code TRANSFER_REQUEST}: the target waiter must accept or deny.</li>
 *     <li>{@code TRANSFER_ACCEPTED}: the previous owner is told the order now belongs
 *         to someone else (it disappears from his operating list).</li>
 *     <li>{@code TRANSFER_DENIED}: the previous owner is told the request was rejected
 *         and he keeps the order.</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderTransferNotificationDTO {

    private String notificationType;

    private Long orderId;
    private String orderNumber;

    /** Full name of the waiter who is handing the order over. */
    private String fromName;

    /** Full name of the waiter receiving the order. */
    private String toName;

    /** Convenience fields for the confirmation dialog. */
    private Integer tableNumber;
    private BigDecimal total;

    private String message;
}
