package com.aatechsolutions.elgransazon.presentation.dto;

import com.aatechsolutions.elgransazon.domain.entity.CashRegisterSession;
import lombok.AllArgsConstructor;
import lombok.Data;

/** One row of the cash-register history: the session plus its day summary. */
@Data
@AllArgsConstructor
public class CashRegisterHistoryRow {
    private CashRegisterSession session;
    private CashRegisterSummary summary;
}
