package com.aatechsolutions.elgransazon.presentation.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Quantity of an OrderDetail assigned to one account of a split bill
 * (per-person "ITEMS" mode).
 *
 * Order lines hold whole units (e.g. 4 Cokes entered as a single line), so
 * each assignment is a whole number of units and the sum across accounts must
 * equal the line quantity. Fractional values are rejected by the backend.
 */
@Data
@NoArgsConstructor
public class SplitItemDTO {

    /** OrderDetail id of the original line. */
    private Long orderDetailId;

    /** Assigned quantity (e.g. 1, 0.5, 1.5). */
    private BigDecimal quantity;
}