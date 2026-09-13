package com.aatechsolutions.elgransazon.presentation.dto;

import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Everything the cash-register view and its PDF need for one session:
 * what it started with, what was sold, the manual movements and the expected
 * vs counted cash.
 */
@Data
@Builder
public class CashRegisterSummary {

    /** Cash the drawer started with. */
    private BigDecimal initialAmount;

    /** Total sold by the cashier in the session window (IVA included, no tips). */
    private BigDecimal totalSales;

    /** Tips collected in the session window (reference only; not in the drawer). */
    private BigDecimal totalTips;

    /** Number of PAID orders collected in the session window. */
    private int salesCount;

    /** Sales per payment method. */
    private Map<PaymentMethodType, BigDecimal> salesByMethod;

    /** Total paid out (Pago). */
    private BigDecimal totalExpenses;

    /** Total cash injected (Entrada). */
    private BigDecimal totalIncomes;

    /** Total withdrawn from the drawer (Retiro). */
    private BigDecimal totalWithdrawals;

    /** Sales paid in cash (the only ones that end up in the drawer). */
    private BigDecimal cashSales;

    /** initial + cash sales + entradas − pagos − retiros. */
    private BigDecimal expectedCash;

    /** Physical cash counted at close (null while the drawer is open). */
    private BigDecimal countedAmount;

    /** counted − expected (null while the drawer is open). */
    private BigDecimal difference;

    public boolean isHasDifference() {
        return difference != null;
    }
}
