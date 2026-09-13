package com.aatechsolutions.elgransazon.presentation.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One person's account as submitted from the split-bill form.
 *
 * Serialized as JSON inside the {@code splitAccounts} form parameter:
 * <pre>
 * [{"personLabel":"Persona 1","paymentMethod":"CASH","tip":"0.00","items":[{"orderDetailId":5,"quantity":"1.5"}]}]
 * </pre>
 */
@Data
@NoArgsConstructor
public class SplitAccountDTO {

    /** 1-based index of the account (Persona 1..N). */
    private Integer index;

    /** Optional label, e.g. "Persona 1". */
    private String personLabel;

    /** PaymentMethodType name (CASH, CREDIT_CARD, DEBIT_CARD, TRANSFER). */
    private String paymentMethod;

    /** Tip for this account (amount, not percentage). */
    private BigDecimal tip = BigDecimal.ZERO;

    /**
     * Assigned items (only used in ITEMS mode).
     * Each entry references an OrderDetail id and a (possibly fractional) quantity.
     */
    private List<SplitItemDTO> items = new ArrayList<>();

    /**
     * Non-blank display label for validation messages.
     */
    public String getDisplayLabel() {
        if (personLabel != null && !personLabel.isBlank()) {
            return personLabel.trim();
        }
        return "Persona " + (index != null ? index : "?");
    }
}