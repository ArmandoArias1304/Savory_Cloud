package com.aatechsolutions.elgransazon.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One payment-method portion submitted from the payment form or a split
 * account card.
 *
 * Serialized as JSON, e.g. {@code [{"method":"CASH","amount":"50.00"},{"method":"DEBIT_CARD","amount":"80.00"}]}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTenderDTO {

    /** PaymentMethodType name (CASH, CREDIT_CARD, DEBIT_CARD, TRANSFER). */
    private String method;

    /** Portion of the collected total (without tip) paid with this method. */
    private BigDecimal amount;
}
