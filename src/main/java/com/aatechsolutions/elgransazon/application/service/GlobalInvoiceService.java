package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.Payment;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared logic for the factura global (público en general) management.
 *
 * <p>Answers three questions for a company + date range:</p>
 * <ul>
 *   <li>which paid tickets are PENDING the global invoice (no individual CFDI,
 *       not amparado by a previous global invoice and not excluded by hand);</li>
 *   <li>which tickets were EXCLUDED by hand (flag
 *       {@code factura_global_excluida = true}), so they can be re-included;</li>
 *   <li>how to flag/unflag tickets without touching tickets that are already
 *       invoiced or outside the range.</li>
 * </ul>
 *
 * <p>The exclusion flag exists for restaurants that contracted the billing service
 * AFTER their accountant had already invoiced some operations outside the system:
 * those tickets must never be stamped again inside a global invoice.</p>
 *
 * <p>A "ticket" is one concept of the factura global: a whole order without split
 * accounts, or one account ({@link Payment}) of a split bill.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GlobalInvoiceService {

    /** Ticket key prefix for a whole order ({@code ORDER:123}). */
    public static final String ORDER_PREFIX = "ORDER:";

    /** Ticket key prefix for one account of a split bill ({@code PAYMENT:123}). */
    public static final String PAYMENT_PREFIX = "PAYMENT:";

    /** Ticket type value used in the JSON payloads. */
    public static final String TYPE_ORDER = "ORDER";

    /** Ticket type value used in the JSON payloads. */
    public static final String TYPE_PAYMENT = "PAYMENT";

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Result of an exclusion update: how many orders and split accounts were flagged.
     */
    public record ExclusionResult(int orderCount, int paymentCount, boolean excluded) {
        public int total() {
            return orderCount + paymentCount;
        }
    }

    // ========== Range helpers ==========

    /**
     * Company timezone, falling back to America/Mexico_City when not configured.
     */
    public ZoneId resolveZone(Company company) {
        if (company != null && company.getTimezone() != null && !company.getTimezone().isBlank()) {
            try {
                return ZoneId.of(company.getTimezone());
            } catch (Exception ignored) {
                // Invalid timezone configured: use the restaurant default
            }
        }
        return ZoneId.of("America/Mexico_City");
    }

    /**
     * Validate {@code from <= to}.
     */
    public void validateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("Ambas fechas son requeridas");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("La fecha 'desde' debe ser menor o igual a 'hasta'");
        }
    }

    /**
     * SAT periodicity code for the range: {@code "01"} (diario) for a single day,
     * {@code "04"} (mensual) for a full month, or {@code null} when the range cannot be
     * billed as a global invoice.
     *
     * <p>A null periodicity is fine for the exclusion panel (any range can be filtered
     * manually), but the emission endpoint rejects it.</p>
     */
    public String resolvePeriodicity(LocalDate fromDate, LocalDate toDate) {
        validateRange(fromDate, toDate);
        if (fromDate.equals(toDate)) {
            return "01"; // Diario
        }
        if (fromDate.getDayOfMonth() == 1
                && toDate.equals(fromDate.withDayOfMonth(fromDate.lengthOfMonth()))) {
            return "04"; // Mensual
        }
        return null;
    }

    /**
     * First instant of the range, as UTC (world of the persistence layer).
     */
    public LocalDateTime startOfRangeUtc(Company company, LocalDate fromDate) {
        return fromDate.atStartOfDay(resolveZone(company))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    /**
     * Exclusive end of the range (next day at 00:00, company timezone) as UTC.
     */
    public LocalDateTime endOfRangeUtc(Company company, LocalDate toDate) {
        return toDate.plusDays(1).atStartOfDay(resolveZone(company))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    // ========== Tickets ==========

    /**
     * Tickets pending the global invoice in the range: whole orders without split
     * accounts plus every account of a split bill, excluding the ones flagged as
     * {@code factura_global_excluida = true}.
     */
    public List<Map<String, Object>> findPendingTickets(Company company, LocalDate fromDate, LocalDate toDate) {
        validateRange(fromDate, toDate);

        ZoneId zone = resolveZone(company);
        LocalDateTime startUtc = startOfRangeUtc(company, fromDate);
        LocalDateTime endUtc = endOfRangeUtc(company, toDate);

        List<Map<String, Object>> tickets = new ArrayList<>();
        for (Order o : orderRepository.findPaidOrdersPendingGlobalInvoiceByDateRange(company, startUtc, endUtc)) {
            tickets.add(ticketMap(TYPE_ORDER, o.getIdOrder(), o.getOrderNumber(), o.getPaidAt(),
                    o.getTotal(), o.getPaymentMethodsDisplay(), zone));
        }
        for (Payment p : paymentRepository.findPaidPendingGlobalInvoiceByDateRange(company, startUtc, endUtc)) {
            tickets.add(ticketMap(TYPE_PAYMENT, p.getIdPayment(), p.getPaymentFolio(), p.getPaidAt(),
                    p.getTotal(), p.getPaymentMethodsDisplay(), zone));
        }
        return tickets;
    }

    /**
     * Tickets flagged as excluded from the global invoice in the range. Only tickets
     * still pending are listed: an already stamped ticket can no longer be re-included.
     */
    public List<Map<String, Object>> findExcludedTickets(Company company, LocalDate fromDate, LocalDate toDate) {
        validateRange(fromDate, toDate);

        ZoneId zone = resolveZone(company);
        LocalDateTime startUtc = startOfRangeUtc(company, fromDate);
        LocalDateTime endUtc = endOfRangeUtc(company, toDate);

        List<Map<String, Object>> tickets = new ArrayList<>();
        for (Order o : orderRepository.findPaidOrdersExcludedFromGlobalInvoiceByDateRange(company, startUtc, endUtc)) {
            tickets.add(ticketMap(TYPE_ORDER, o.getIdOrder(), o.getOrderNumber(), o.getPaidAt(),
                    o.getTotal(), o.getPaymentMethodsDisplay(), zone));
        }
        for (Payment p : paymentRepository.findPaidExcludedFromGlobalInvoiceByDateRange(company, startUtc, endUtc)) {
            tickets.add(ticketMap(TYPE_PAYMENT, p.getIdPayment(), p.getPaymentFolio(), p.getPaidAt(),
                    p.getTotal(), p.getPaymentMethodsDisplay(), zone));
        }
        return tickets;
    }

    /**
     * Sum of the ticket amounts (used for the panel totals).
     */
    public BigDecimal sumTotals(List<Map<String, Object>> tickets) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> ticket : tickets) {
            Object value = ticket.get("total");
            if (value instanceof BigDecimal amount) {
                total = total.add(amount);
            }
        }
        return total;
    }

    /**
     * Flag (or unflag) the selected tickets as excluded from the global invoice.
     *
     * <p>Only tickets inside the given range that are still pending are updated; a
     * ticket that was invoiced individually or amparado by a global invoice in the
     * meantime is silently skipped, which is why the updated counters are returned.</p>
     *
     * @param ticketKeys keys built by the panel ({@code ORDER:12}, {@code PAYMENT:34})
     * @param excluded   true to exclude, false to re-include
     */
    @Transactional
    public ExclusionResult setExclusion(Company company, String username, LocalDate fromDate, LocalDate toDate,
                                       List<String> ticketKeys, boolean excluded) {
        validateRange(fromDate, toDate);

        List<Long> orderIds = new ArrayList<>();
        List<Long> paymentIds = new ArrayList<>();
        for (String key : ticketKeys == null ? List.<String>of() : ticketKeys) {
            Long id = parseTicketId(key, ORDER_PREFIX);
            if (id != null) {
                orderIds.add(id);
                continue;
            }
            id = parseTicketId(key, PAYMENT_PREFIX);
            if (id != null) {
                paymentIds.add(id);
            }
        }

        if (orderIds.isEmpty() && paymentIds.isEmpty()) {
            throw new IllegalArgumentException("Seleccione al menos una operación");
        }

        LocalDateTime startUtc = startOfRangeUtc(company, fromDate);
        LocalDateTime endUtc = endOfRangeUtc(company, toDate);
        String updatedBy = username != null && !username.isBlank() ? username : "PROGRAMMER";

        int orders = 0;
        if (!orderIds.isEmpty()) {
            orders = orderRepository.updateGlobalInvoiceExclusion(company, orderIds, excluded, updatedBy, startUtc, endUtc);
        }
        int payments = 0;
        if (!paymentIds.isEmpty()) {
            payments = paymentRepository.updateGlobalInvoiceExclusion(company, paymentIds, excluded, updatedBy, startUtc, endUtc);
        }

        log.info("Global invoice exclusion {} by {}: {} orders, {} accounts (company={}, {} -> {})",
                excluded ? "applied" : "reverted", updatedBy, orders, payments,
                company != null ? company.getSlug() : "n/a", fromDate, toDate);

        return new ExclusionResult(orders, payments, excluded);
    }

    // ========== Private helpers ==========

    private Map<String, Object> ticketMap(String type, Long id, String folio, LocalDateTime paidAtUtc,
                                          BigDecimal total, String paymentMethod, ZoneId zone) {
        Map<String, Object> ticket = new LinkedHashMap<>();
        ticket.put("key", (TYPE_ORDER.equals(type) ? ORDER_PREFIX : PAYMENT_PREFIX) + id);
        ticket.put("type", type);
        ticket.put("id", id);
        ticket.put("folio", folio);
        ticket.put("date", paidAtUtc != null
                ? paidAtUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDate().toString()
                : "");
        ticket.put("total", total != null ? total : BigDecimal.ZERO);
        ticket.put("paymentMethod", paymentMethod);
        return ticket;
    }

    /**
     * Reads the numeric id of a ticket key with the given prefix, or null when the key
     * does not belong to that kind of ticket.
     */
    private Long parseTicketId(String key, String prefix) {
        if (key == null || !key.startsWith(prefix)) {
            return null;
        }
        try {
            return Long.valueOf(key.substring(prefix.length()).trim());
        } catch (NumberFormatException e) {
            log.warn("Ignoring malformed global invoice ticket key: {}", key);
            return null;
        }
    }
}
