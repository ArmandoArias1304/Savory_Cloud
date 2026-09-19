package com.aatechsolutions.elgransazon.util;

import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.Payment;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.PaymentTender;
import com.aatechsolutions.elgransazon.presentation.dto.PaymentTenderDTO;
import com.aatechsolutions.elgransazon.presentation.dto.SplitAccountDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Shared rules for collecting an order (or a split account) with one or more
 * payment methods.
 *
 * <p>Invariants:</p>
 * <ul>
 *   <li>At least one method with amount &gt; 0.</li>
 *   <li>No negative amounts; at most 2 decimals; cap $999,999.99.</li>
 *   <li>The same method appears at most once (duplicates are merged).</li>
 *   <li>Σ amounts == collected total (without tip), after a 1-cent residual
 *       is absorbed by the largest line.</li>
 *   <li>Every method must be allowed for the collector and enabled in
 *       {@code SystemConfiguration}.</li>
 * </ul>
 *
 * <p>Legacy paid rows without tender rows are treated as a single tender of
 * {@code paymentMethod} covering {@code total}, so reports and the cash
 * drawer stay consistent with historical data.</p>
 */
public final class PaymentTenderSupport {

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("999999.99");
    private static final BigDecimal CENT = new BigDecimal("0.01");
    private static final ObjectMapper JSON = new ObjectMapper();

    private PaymentTenderSupport() {
    }

    /**
     * One normalized method + amount pair used throughout validation,
     * persistence and reporting.
     */
    public static final class TenderLine {
        private final PaymentMethodType method;
        private final BigDecimal amount;

        public TenderLine(PaymentMethodType method, BigDecimal amount) {
            this.method = method;
            this.amount = amount;
        }

        public PaymentMethodType getMethod() {
            return method;
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }

    // ---------- Parse / normalize ----------

    /**
     * Parse the {@code paymentTenders} form parameter. Blank or invalid JSON
     * with no usable rows returns an empty list (caller falls back to the
     * single {@code paymentMethod} field).
     */
    public static List<TenderLine> parseJson(String paymentTendersJson) {
        if (paymentTendersJson == null || paymentTendersJson.isBlank()) {
            return List.of();
        }
        try {
            List<PaymentTenderDTO> dtos = JSON.readValue(paymentTendersJson,
                    new TypeReference<List<PaymentTenderDTO>>() {});
            return fromDtos(dtos);
        } catch (Exception e) {
            throw new IllegalArgumentException("Los montos por método de pago no son válidos");
        }
    }

    public static List<TenderLine> fromDtos(List<PaymentTenderDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        List<TenderLine> lines = new ArrayList<>();
        for (PaymentTenderDTO dto : dtos) {
            if (dto == null || dto.getMethod() == null || dto.getMethod().isBlank()) {
                continue;
            }
            PaymentMethodType method;
            try {
                method = PaymentMethodType.valueOf(dto.getMethod().trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("Método de pago no válido: " + dto.getMethod());
            }
            BigDecimal amount = dto.getAmount() != null ? dto.getAmount() : BigDecimal.ZERO;
            lines.add(new TenderLine(method, amount));
        }
        return normalize(lines);
    }

    public static List<TenderLine> fromSingle(PaymentMethodType method, BigDecimal total) {
        if (method == null) {
            throw new IllegalArgumentException("Debe seleccionar un método de pago");
        }
        BigDecimal amount = scale(total);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El total a cobrar debe ser mayor a cero");
        }
        return List.of(new TenderLine(method, amount));
    }

    /**
     * Resolve the submitted mix: prefer the JSON tenders; otherwise a single
     * method covering the expected total.
     */
    public static List<TenderLine> resolveSubmitted(String paymentTendersJson,
                                                    PaymentMethodType paymentMethod,
                                                    BigDecimal expectedTotal) {
        List<TenderLine> parsed = parseJson(paymentTendersJson);
        if (parsed.isEmpty()) {
            return fromSingle(paymentMethod, expectedTotal);
        }
        return fitToTotal(parsed, expectedTotal);
    }

    /**
     * Resolve tenders of a split account: prefer the account's tender list;
     * otherwise a single method covering the account total.
     */
    public static List<TenderLine> resolveAccount(SplitAccountDTO acc, BigDecimal accountTotal) {
        String label = acc != null ? acc.getDisplayLabel() : "Cuenta";
        List<TenderLine> parsed = fromDtos(acc != null ? acc.getPaymentTenders() : null);
        if (parsed.isEmpty()) {
            PaymentMethodType method = parseMethodName(
                    acc != null ? acc.getPaymentMethod() : null, label);
            return fromSingle(method, accountTotal);
        }
        try {
            return fitToTotal(parsed, accountTotal);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(label + ": " + e.getMessage());
        }
    }

    /**
     * Merge duplicate methods, drop zeros, scale to 2 decimals.
     */
    public static List<TenderLine> normalize(List<TenderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        Map<PaymentMethodType, BigDecimal> merged = new LinkedHashMap<>();
        for (TenderLine line : lines) {
            if (line == null || line.getMethod() == null) {
                continue;
            }
            BigDecimal amount = line.getAmount() != null ? line.getAmount() : BigDecimal.ZERO;
            merged.merge(line.getMethod(), amount, BigDecimal::add);
        }
        List<TenderLine> result = new ArrayList<>();
        for (Map.Entry<PaymentMethodType, BigDecimal> entry : merged.entrySet()) {
            BigDecimal amount = scale(entry.getValue());
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                result.add(new TenderLine(entry.getKey(), amount));
            }
        }
        return result;
    }

    /**
     * Force the mix to cover {@code expected} exactly. A 1-cent residual is
     * absorbed by the largest line (same last-line convention used by split
     * accounts). A larger gap is a validation error: never charge more or
     * less than the order/account total.
     */
    public static List<TenderLine> fitToTotal(List<TenderLine> lines, BigDecimal expected) {
        BigDecimal target = scale(expected);
        if (target.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El total a cobrar debe ser mayor a cero");
        }
        List<TenderLine> normalized = normalize(lines);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Debe indicar al menos un método de pago con monto");
        }
        if (normalized.size() == 1) {
            return List.of(new TenderLine(normalized.get(0).getMethod(), target));
        }
        BigDecimal sum = sum(normalized);
        BigDecimal delta = target.subtract(sum);
        if (delta.abs().compareTo(CENT) > 0) {
            throw new IllegalArgumentException(
                    "La suma de los métodos de pago (" + money(sum)
                            + ") debe ser exactamente el total (" + money(target) + ")");
        }
        if (delta.compareTo(BigDecimal.ZERO) == 0) {
            return normalized;
        }
        int largest = 0;
        for (int i = 1; i < normalized.size(); i++) {
            if (normalized.get(i).getAmount().compareTo(normalized.get(largest).getAmount()) > 0) {
                largest = i;
            }
        }
        List<TenderLine> fitted = new ArrayList<>(normalized.size());
        for (int i = 0; i < normalized.size(); i++) {
            TenderLine line = normalized.get(i);
            if (i == largest) {
                BigDecimal adjusted = scale(line.getAmount().add(delta));
                if (adjusted.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException(
                            "La suma de los métodos de pago (" + money(sum)
                                    + ") debe ser exactamente el total (" + money(target) + ")");
                }
                fitted.add(new TenderLine(line.getMethod(), adjusted));
            } else {
                fitted.add(line);
            }
        }
        return fitted;
    }

    public static void validateAmounts(List<TenderLine> lines, String label) {
        String prefix = label != null && !label.isBlank() ? label + ": " : "";
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException(prefix + "debe seleccionar un método de pago");
        }
        for (TenderLine line : lines) {
            if (line.getMethod() == null) {
                throw new IllegalArgumentException(prefix + "método de pago no válido");
            }
            BigDecimal amount = line.getAmount() != null ? line.getAmount() : BigDecimal.ZERO;
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(prefix + "el monto de "
                        + line.getMethod().getDisplayName() + " debe ser mayor a cero");
            }
            if (amount.compareTo(MAX_AMOUNT) > 0) {
                throw new IllegalArgumentException(prefix + "el monto de "
                        + line.getMethod().getDisplayName() + " no puede ser mayor a $999,999.99");
            }
            if (amount.scale() > 2) {
                throw new IllegalArgumentException(prefix + "el monto de "
                        + line.getMethod().getDisplayName() + " solo permite hasta 2 decimales");
            }
        }
    }

    public static void assertMethodsAllowed(List<TenderLine> lines,
                                            Predicate<PaymentMethodType> allowed,
                                            String label) {
        String prefix = label != null && !label.isBlank() ? label + ": " : "";
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException(prefix + "debe seleccionar un método de pago");
        }
        for (TenderLine line : lines) {
            if (!allowed.test(line.getMethod())) {
                throw new IllegalStateException(prefix + "el método de pago '"
                        + line.getMethod().getDisplayName() + "' no está permitido para este cobro");
            }
        }
    }

    public static void assertMethodsAllowed(List<TenderLine> lines,
                                            Set<PaymentMethodType> allowed,
                                            String label) {
        assertMethodsAllowed(lines,
                method -> allowed != null && allowed.contains(method),
                label);
    }

    /**
     * Methods used by a split account: the mix if present, otherwise the
     * single {@code paymentMethod} field.
     */
    public static List<PaymentMethodType> methodsOf(SplitAccountDTO acc) {
        List<TenderLine> mix = fromDtos(acc != null ? acc.getPaymentTenders() : null);
        if (!mix.isEmpty()) {
            return mix.stream().map(TenderLine::getMethod).toList();
        }
        return List.of(parseMethodName(acc != null ? acc.getPaymentMethod() : null,
                acc != null ? acc.getDisplayLabel() : "Cuenta"));
    }

    public static PaymentMethodType parseMethodName(String name, String label) {
        String prefix = label != null && !label.isBlank() ? label + ": " : "";
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(prefix + "debe seleccionar un método de pago");
        }
        try {
            return PaymentMethodType.valueOf(name.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(prefix + "método de pago no válido");
        }
    }

    public static PaymentMethodType primary(List<TenderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        TenderLine best = lines.get(0);
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).getAmount().compareTo(best.getAmount()) > 0) {
                best = lines.get(i);
            }
        }
        return best.getMethod();
    }

    public static boolean isCashOnly(List<TenderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return false;
        }
        return lines.stream().allMatch(line -> line.getMethod() == PaymentMethodType.CASH);
    }

    public static boolean isMixed(List<TenderLine> lines) {
        if (lines == null || lines.size() < 2) {
            return false;
        }
        return lines.stream().map(TenderLine::getMethod).distinct().count() > 1;
    }

    public static boolean uses(List<TenderLine> lines, PaymentMethodType method) {
        if (lines == null || method == null) {
            return false;
        }
        return lines.stream().anyMatch(line -> line.getMethod() == method);
    }

    public static BigDecimal amountOf(List<TenderLine> lines, PaymentMethodType method) {
        if (lines == null || method == null) {
            return BigDecimal.ZERO;
        }
        return lines.stream()
                .filter(line -> line.getMethod() == method)
                .map(TenderLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal sum(List<TenderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return lines.stream()
                .map(TenderLine::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Compact label for tickets/lists: {@code Efectivo} or
     * {@code Efectivo + Tarjeta de Débito}.
     */
    public static String displayNames(List<TenderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return "N/A";
        }
        return lines.stream()
                .map(line -> line.getMethod().getDisplayName())
                .collect(Collectors.joining(" + "));
    }

    /**
     * Label with amounts, for tickets and confirmation dialogs:
     * {@code Efectivo $50.00 + Débito $80.00}.
     */
    public static String displayWithAmounts(List<TenderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return "N/A";
        }
        return lines.stream()
                .map(line -> line.getMethod().getDisplayName() + " " + money(line.getAmount()))
                .collect(Collectors.joining(" + "));
    }

    // ---------- Entity helpers ----------

    /**
     * Tenders that actually collected this order (split accounts merged, or
     * whole-order tenders, or the legacy single method). Does not invent
     * tenders for unpaid orders beyond the customer's declared method.
     */
    public static List<TenderLine> collected(Order order) {
        if (order == null) {
            return List.of();
        }
        if (order.isSplitOrder()) {
            List<TenderLine> all = new ArrayList<>();
            for (Payment payment : order.getPayments()) {
                all.addAll(collected(payment));
            }
            return normalize(all);
        }
        List<TenderLine> stored = fromEntities(orderLevelTenders(order));
        if (!stored.isEmpty()) {
            return stored;
        }
        if (order.getPaymentMethod() == null) {
            return List.of();
        }
        BigDecimal total = order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO;
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of(new TenderLine(order.getPaymentMethod(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
        }
        return List.of(new TenderLine(order.getPaymentMethod(), scale(total)));
    }

    public static List<TenderLine> collected(Payment payment) {
        if (payment == null) {
            return List.of();
        }
        List<TenderLine> stored = fromEntities(payment.getPaymentTenders());
        if (!stored.isEmpty()) {
            return stored;
        }
        if (payment.getPaymentMethod() == null) {
            return List.of();
        }
        BigDecimal total = payment.getTotal() != null ? payment.getTotal() : BigDecimal.ZERO;
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        return List.of(new TenderLine(payment.getPaymentMethod(), scale(total)));
    }

    public static boolean uses(Order order, PaymentMethodType method) {
        return uses(collected(order), method);
    }

    public static boolean uses(Payment payment, PaymentMethodType method) {
        return uses(collected(payment), method);
    }

    /**
     * Replace the whole-order tenders of a regular collection. Split-account
     * tenders (those with {@code payment != null}) are left untouched because
     * {@code Order.paymentTenders} is restricted to {@code id_payment IS NULL}.
     */
    public static void applyToOrder(Order order, List<TenderLine> lines) {
        if (order == null) {
            return;
        }
        validateAmounts(lines, null);
        if (order.getPaymentTenders() == null) {
            order.setPaymentTenders(new ArrayList<>());
        } else {
            order.getPaymentTenders().clear();
        }
        for (TenderLine line : lines) {
            PaymentTender tender = PaymentTender.builder()
                    .company(order.getCompany())
                    .order(order)
                    .payment(null)
                    .paymentMethod(line.getMethod())
                    .amount(line.getAmount())
                    .build();
            order.getPaymentTenders().add(tender);
        }
        PaymentMethodType primary = primary(lines);
        if (primary != null) {
            order.setPaymentMethod(primary);
        }
    }

    public static void applyToPayment(Payment payment, List<TenderLine> lines) {
        if (payment == null) {
            return;
        }
        validateAmounts(lines, payment.getPersonLabel());
        if (payment.getPaymentTenders() == null) {
            payment.setPaymentTenders(new ArrayList<>());
        } else {
            payment.getPaymentTenders().clear();
        }
        for (TenderLine line : lines) {
            PaymentTender tender = PaymentTender.builder()
                    .company(payment.getCompany())
                    .order(payment.getOrder())
                    .payment(payment)
                    .paymentMethod(line.getMethod())
                    .amount(line.getAmount())
                    .build();
            payment.getPaymentTenders().add(tender);
        }
        PaymentMethodType primary = primary(lines);
        if (primary != null) {
            payment.setPaymentMethod(primary);
        }
    }

    /**
     * SAT c_FormaPago for a collection: the method that liquidated the
     * largest amount (Guía de llenado CFDI 4.0). A mixed ticket therefore
     * still gets one clave (01/03/04/28), never 99. {@code null} only when
     * there is no method at all (unknown → 99).
     */
    public static PaymentMethodType satPaymentFormMethod(List<TenderLine> lines) {
        return primary(lines);
    }

    // ---------- Reporting / cash drawer ----------

    /**
     * Sales totals by method for a list of paid orders. Split accounts and
     * mixed tenders contribute only their own amounts, so the four method
     * columns sum to the order totals (without tip).
     */
    public static Map<PaymentMethodType, BigDecimal> totalsByMethod(List<Order> orders) {
        Map<PaymentMethodType, BigDecimal> map = emptyByMethod();
        if (orders == null) {
            return map;
        }
        for (Order order : orders) {
            for (TenderLine line : collected(order)) {
                map.merge(line.getMethod(), line.getAmount(), BigDecimal::add);
            }
        }
        map.replaceAll((method, value) -> value.setScale(2, RoundingMode.HALF_UP));
        return map;
    }

    /**
     * Count of orders that used each method. A mixed order is counted in
     * every method it used, so the counts can add up to more than the number
     * of orders (the filter contract).
     */
    public static Map<String, Long> orderCountsByMethod(List<Order> orders) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (orders == null) {
            return counts;
        }
        for (Order order : orders) {
            List<TenderLine> lines = collected(order);
            if (lines.isEmpty()) {
                continue;
            }
            lines.stream()
                    .map(line -> line.getMethod().getDisplayName())
                    .distinct()
                    .forEach(name -> counts.merge(name, 1L, Long::sum));
        }
        return counts;
    }

    /**
     * Display-name → amount, aligned with {@link #orderCountsByMethod} keys.
     */
    public static Map<String, BigDecimal> totalsByMethodDisplayName(List<Order> orders) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (Map.Entry<PaymentMethodType, BigDecimal> entry : totalsByMethod(orders).entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                map.put(entry.getKey().getDisplayName(), entry.getValue());
            }
        }
        return map;
    }

    public static Map<PaymentMethodType, BigDecimal> emptyByMethod() {
        Map<PaymentMethodType, BigDecimal> map = new EnumMap<>(PaymentMethodType.class);
        for (PaymentMethodType method : PaymentMethodType.values()) {
            map.put(method, BigDecimal.ZERO);
        }
        return map;
    }

    public static Map<PaymentMethodType, BigDecimal> amountsByMethod(List<TenderLine> lines) {
        Map<PaymentMethodType, BigDecimal> map = emptyByMethod();
        if (lines == null) {
            return map;
        }
        for (TenderLine line : lines) {
            map.merge(line.getMethod(), line.getAmount(), BigDecimal::add);
        }
        map.replaceAll((method, value) -> value.setScale(2, RoundingMode.HALF_UP));
        return map;
    }

    public static String money(BigDecimal value) {
        BigDecimal amount = value != null ? value : BigDecimal.ZERO;
        return String.format("$%,.2f", amount);
    }

    private static List<TenderLine> fromEntities(List<PaymentTender> tenders) {
        if (tenders == null || tenders.isEmpty()) {
            return List.of();
        }
        List<TenderLine> lines = new ArrayList<>();
        for (PaymentTender tender : tenders) {
            if (tender == null || tender.getPaymentMethod() == null) {
                continue;
            }
            lines.add(new TenderLine(tender.getPaymentMethod(),
                    tender.getAmount() != null ? tender.getAmount() : BigDecimal.ZERO));
        }
        return normalize(lines);
    }

    /**
     * Whole-order tenders only (those not attached to a split account). The
     * {@code @SQLRestriction} on the collection already excludes payment
     * tenders; this filter is a safety net when the collection is populated
     * in memory (tests, newly applied rows).
     */
    private static List<PaymentTender> orderLevelTenders(Order order) {
        if (order.getPaymentTenders() == null || order.getPaymentTenders().isEmpty()) {
            return List.of();
        }
        return order.getPaymentTenders().stream()
                .filter(tender -> tender.getPayment() == null)
                .toList();
    }

    private static BigDecimal scale(BigDecimal value) {
        BigDecimal amount = value != null ? value : BigDecimal.ZERO;
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
