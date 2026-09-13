package com.aatechsolutions.elgransazon.util;

import com.aatechsolutions.elgransazon.domain.entity.Order;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared rules for the order-level discount captured at payment time.
 *
 * <p>Supports two capture modes submitted by the forms:</p>
 * <ul>
 *   <li>{@code MONTO}     — fixed amount in pesos (admin/cashier, normal payment).</li>
 *   <li>{@code PORCENTAJE}— percentage over the total (0–100, 2 decimals).</li>
 * </ul>
 *
 * <p>Any charge that is split among people ("dividir cuenta" / por persona) or
 * that happens after someone at the table was already charged (partial
 * collections, "persona que se va") only accepts a percentage, and the moment
 * one is captured the order is locked: every later collection must reuse
 * exactly the same percentage. A fixed amount ($) is only accepted on a single,
 * full payment of an order nobody was charged for yet.</p>
 */
public final class OrderDiscountSupport {

    public static final String TYPE_AMOUNT = "MONTO";
    public static final String TYPE_PERCENT = "PORCENTAJE";

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999.99");
    private static final BigDecimal MAX_PERCENT = new BigDecimal("100");

    private OrderDiscountSupport() {
    }

    /**
     * Result of validating a submitted discount.
     *
     * @param percentMode true when the discount is a percentage
     * @param percent     the validated percentage (0 when amount mode)
     * @param amount      the resolved amount in pesos over the order base
     * @param lock        true when the order must lock the percentage (departing-guest flow)
     */
    public static final class Resolved {
        private final boolean percentMode;
        private final BigDecimal percent;
        private final BigDecimal amount;
        private final boolean lock;

        Resolved(boolean percentMode, BigDecimal percent, BigDecimal amount, boolean lock) {
            this.percentMode = percentMode;
            this.percent = percent;
            this.amount = amount;
            this.lock = lock;
        }

        public boolean isPercentMode() {
            return percentMode;
        }

        public BigDecimal getPercent() {
            return percent;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public boolean isLock() {
            return lock;
        }
    }

    /**
     * Validates the submitted discount and resolves it against the order.
     *
     * @param order         order being charged (may be null in isolated tests)
     * @param discountType  submitted type ({@code MONTO} / {@code PORCENTAJE}); null is treated as amount
     * @param amountInput   submitted fixed amount (usually 0 when percentage mode)
     * @param percentInput  submitted percentage (usually 0 when amount mode)
     * @param allowDiscount false for waiters on unlocked orders (they never discount)
     * @param percentageOnlyFlow true when the caller knows the charge is split among
     *                           people or is a departing-guest/partial collection
     *                           (percentage only + lock). Orders that already have
     *                           partial collections or split accounts are treated the
     *                           same way even when this flag is false.
     * @throws IllegalArgumentException on contradictory values, out-of-range values or a locked order
     */
    public static Resolved resolve(Order order, String discountType,
                                   BigDecimal amountInput, BigDecimal percentInput,
                                   boolean allowDiscount, boolean percentageOnlyFlow) {
        BigDecimal amount = amountInput != null ? amountInput : BigDecimal.ZERO;
        BigDecimal percent = percentInput != null ? percentInput : BigDecimal.ZERO;
        String type = discountType != null ? discountType.trim().toUpperCase() : null;

        // 0. Percentage is mandatory (and locked) once the bill is being split or
        //    someone at the table was already charged: a fixed amount would change
        //    retroactively what earlier guests already paid.
        boolean percentageOnly = percentageOnlyFlow
                || (order != null && (order.hasPartialCollections() || order.isSplitOrder()));

        // 1. Locked order (someone left with a discount): the captured percentage is
        //    mandatory for every later collection — a different % or a fixed amount is rejected.
        if (order != null && order.hasLockedOrderDiscount()) {
            BigDecimal fixed = order.getOrderDiscountPercent();
            boolean amountAttempt = TYPE_AMOUNT.equals(type) && amount.compareTo(BigDecimal.ZERO) > 0;
            boolean percentAttempt = TYPE_PERCENT.equals(type)
                    && percent.compareTo(BigDecimal.ZERO) > 0
                    && percent.compareTo(fixed) != 0;
            if (amountAttempt || percentAttempt) {
                throw new IllegalArgumentException("El descuento de esta orden quedó fijado en "
                        + order.getFormattedOrderDiscountPercent()
                        + "%. Debe cobrarse con ese mismo porcentaje y no como monto fijo.");
            }
            return new Resolved(true, fixed, order.resolveDiscountAmountForPercent(fixed), false);
        }

        // 2. No discount permission (waiters) or no discount UI: no discount on unlocked orders.
        if (!allowDiscount) {
            return new Resolved(false, BigDecimal.ZERO, BigDecimal.ZERO, false);
        }

        if (TYPE_PERCENT.equals(type)) {
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalArgumentException(
                        "No se puede enviar un monto fijo y un porcentaje de descuento al mismo tiempo.");
            }
            validatePercent(percent);
            if (percent.compareTo(BigDecimal.ZERO) == 0) {
                return new Resolved(false, BigDecimal.ZERO, BigDecimal.ZERO, false);
            }
            BigDecimal resolved = order != null
                    ? order.resolveDiscountAmountForPercent(percent)
                    : BigDecimal.ZERO;
            return new Resolved(true, percent, resolved, percentageOnly);
        }

        if (TYPE_AMOUNT.equals(type) || type == null || type.isEmpty()) {
            if (percent.compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalArgumentException(
                        "No se puede enviar un monto fijo y un porcentaje de descuento al mismo tiempo.");
            }
            if (percentageOnly && amount.compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalArgumentException(
                        "En cobros divididos (por persona o persona que se va) solo se permite descuento por porcentaje (%).");
            }
            validateAmount(order, amount);
            return new Resolved(false, BigDecimal.ZERO, amount, false);
        }

        throw new IllegalArgumentException("Tipo de descuento no válido: " + discountType);
    }

    private static void validatePercent(BigDecimal percent) {
        if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(MAX_PERCENT) > 0) {
            throw new IllegalArgumentException("El porcentaje de descuento debe estar entre 0 y 100.");
        }
        if (percent.scale() > 2) {
            throw new IllegalArgumentException("El porcentaje de descuento solo permite hasta 2 decimales.");
        }
    }

    private static void validateAmount(Order order, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El descuento sobre el total no puede ser negativo.");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("El descuento sobre el total no puede ser mayor a $999,999.99.");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("El descuento sobre el total solo permite hasta 2 decimales.");
        }
        if (order != null) {
            BigDecimal max = order.hasPartialCollections()
                    ? order.getRemainingTotal()
                    : order.getGrossBeforeOrderDiscount();
            if (max == null) {
                max = BigDecimal.ZERO;
            }
            if (amount.compareTo(max) > 0) {
                throw new IllegalArgumentException(
                        "El descuento (" + String.format("$%.2f", amount)
                                + ") no puede ser mayor a lo que resta por cobrar ("
                                + String.format("$%.2f", max) + ").");
            }
        }
    }

    /**
     * Rounds a resolved discount amount to 2 decimals (helper for callers that
     * resolve percentages outside the order entity).
     */
    public static BigDecimal round2(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}
