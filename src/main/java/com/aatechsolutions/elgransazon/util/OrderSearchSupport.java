package com.aatechsolutions.elgransazon.util;

import com.aatechsolutions.elgransazon.domain.entity.Order;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared matching rules for the free-text "Buscar" box of every order list
 * (admin, manager, waiter and cashier), so the four roles find exactly the same
 * orders with the same term instead of drifting apart per template.
 *
 * <p>One term matches any of:</p>
 * <ul>
 *   <li><b>Order number</b> — case-insensitive substring of the folio
 *       ({@code ORD-20260929-007}) or of a fragment of it.</li>
 *   <li><b>Day sequence</b> — a short numeric term ({@code 7}, {@code 007}) is
 *       read as the sequence number of the folio, never as a substring. That is
 *       deliberate: matching {@code 007} as text would also hit any folio whose
 *       date happens to contain it (e.g. {@code ORD-20261007-012}). The internal
 *       id is accepted as well, so "el pedido 4821" also works.</li>
 *   <li><b>Customer phone</b> — compared digits-only, so {@code 5512345678}
 *       finds a phone stored with spaces or dashes, and {@code 52 55 1234 5678}
 *       (as copied from WhatsApp) matches by its last 10 digits.</li>
 *   <li><b>Customer name</b> — substring ignoring case and accents
 *       ("jose" finds "José").</li>
 * </ul>
 *
 * <p>Nothing here touches the table filter: the panel already has a Mesa select
 * and duplicating it would make "5" ambiguous (order 5 vs. table 5).</p>
 */
public final class OrderSearchSupport {

    /** Order numbers are built as {@code ORD-yyyyMMdd-NNN} (see {@link Order#generateOrderNumber(int)}). */
    private static final Pattern ORDER_NUMBER = Pattern.compile("(?i)ORD-(\\d{8})-(\\d{1,4})");

    /**
     * A term that carries the folio day: {@code ORD-20260929-007},
     * {@code 20260929-007} or {@code 20260929}. A bare 10-digit phone never
     * matches on purpose, so typing a phone cannot silently pin a date.
     */
    private static final Pattern FOLIO_DATE =
            Pattern.compile("^(?:ORD-?)?(\\d{8})(?:-\\d{1,4})?$", Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter FOLIO_DAY = DateTimeFormatter.BASIC_ISO_DATE;

    /** Guard against nonsense folio dates ("00010101"); real data lives in this window. */
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    /** Below this many digits a numeric term is the folio sequence, not a phone. */
    private static final int PHONE_MIN_DIGITS = 7;

    /** Length of a Mexican number without the country/mobile prefix. */
    private static final int NATIONAL_PHONE_DIGITS = 10;

    /** Above this many digits a numeric term is read as a folio fragment or a phone. */
    private static final int ID_MAX_DIGITS = 6;

    private OrderSearchSupport() {
    }

    /**
     * Keeps the orders matching {@code term}, preserving the incoming order.
     * A null/blank term returns the original list untouched.
     */
    public static List<Order> filter(List<Order> orders, String term) {
        if (orders == null) {
            return List.of();
        }
        if (!hasTerm(term)) {
            return orders;
        }
        List<Order> matches = new ArrayList<>(orders.size());
        for (Order order : orders) {
            if (matches(order, term)) {
                matches.add(order);
            }
        }
        return matches;
    }

    /**
     * Whether the order matches the search term. A null/blank term matches everything.
     */
    public static boolean matches(Order order, String term) {
        if (order == null) {
            return false;
        }
        if (!hasTerm(term)) {
            return true;
        }
        String trimmed = term.trim();

        // Short numeric term: the sequence of the day (or the internal id). It never falls
        // through to the folio text, so "007" cannot match another day's date digits.
        if (isSequenceTerm(trimmed)) {
            int number = Integer.parseInt(trimmed);
            if (order.getIdOrder() != null && order.getIdOrder() == number) {
                return true;
            }
            Integer sequence = sequenceOf(order.getOrderNumber());
            return sequence != null && sequence == number;
        }

        // "El pedido 4821": the internal id, when the term is only digits but too long to be a
        // sequence (a 4+ digit number could also be a piece of the folio, so it keeps falling
        // through to the checks below).
        if (isIdTerm(trimmed) && order.getIdOrder() != null
                && order.getIdOrder() == Long.parseLong(trimmed)) {
            return true;
        }

        if (containsFolded(order.getOrderNumber(), trimmed)) {
            return true;
        }

        String digits = digitsOf(trimmed);
        if (digits.length() >= PHONE_MIN_DIGITS && phoneMatches(order.getCustomerPhone(), digits)) {
            return true;
        }

        return containsFolded(order.getCustomerName(), trimmed);
    }

    /**
     * Day encoded in the term when it carries the folio date
     * ({@code ORD-20260929-007}, {@code 20260929-007}, {@code 20260929});
     * {@code null} when the term carries no usable date.
     *
     * <p>Used by the lists to pin the Fecha field to the day of the folio, so
     * searching an order from another day never returns an empty board just
     * because the date filter is still set to today.</p>
     */
    public static LocalDate dateHint(String term) {
        if (!hasTerm(term)) {
            return null;
        }
        Matcher matcher = FOLIO_DATE.matcher(term.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            LocalDate day = LocalDate.parse(matcher.group(1), FOLIO_DAY);
            return day.getYear() >= MIN_YEAR && day.getYear() <= MAX_YEAR ? day : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** True when there is something to search for. */
    public static boolean hasTerm(String term) {
        return term != null && !term.isBlank();
    }

    /** Trimmed term, or {@code null} when it is empty (keeps the query string clean). */
    public static String trimToNull(String term) {
        if (!hasTerm(term)) {
            return null;
        }
        return term.trim();
    }

    /**
     * Hidden sequence of a folio, or of any legacy number ending in digits
     * ("MOSTRADOR-12" → 12). {@code null} when there is nothing numeric to read.
     */
    private static Integer sequenceOf(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            return null;
        }
        String trimmed = orderNumber.trim();
        Matcher matcher = ORDER_NUMBER.matcher(trimmed);
        String digits = matcher.matches() ? matcher.group(2) : trailingDigits(trimmed);
        if (digits.isEmpty() || digits.length() > 9) {
            return null;
        }
        return Integer.valueOf(digits);
    }

    private static boolean isSequenceTerm(String term) {
        String digits = digitsOf(term);
        return !digits.isEmpty() && digits.length() <= 3 && digits.length() == term.length();
    }

    /** Numeric term long enough to be an internal id, still too short to be a phone. */
    private static boolean isIdTerm(String term) {
        String digits = digitsOf(term);
        return digits.length() > 3 && digits.length() <= ID_MAX_DIGITS && digits.length() == term.length();
    }

    /**
     * Digits of the term against the stored phone. A term longer than 10 digits is also
     * compared by its last 10, because that is how the number arrives when it is copied with
     * the country/mobile prefix the WhatsApp link writes ({@code 52 55 1234 5678}).
     */
    private static boolean phoneMatches(String customerPhone, String termDigits) {
        String phone = digitsOf(customerPhone);
        if (phone.isEmpty()) {
            return false;
        }
        if (phone.contains(termDigits)) {
            return true;
        }
        if (termDigits.length() > NATIONAL_PHONE_DIGITS) {
            return phone.contains(termDigits.substring(termDigits.length() - NATIONAL_PHONE_DIGITS));
        }
        return false;
    }

    private static String trailingDigits(String value) {
        int start = value.length();
        while (start > 0 && Character.isDigit(value.charAt(start - 1))) {
            start--;
        }
        return value.substring(start);
    }

    private static boolean containsFolded(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) {
            return false;
        }
        return fold(haystack).contains(fold(needle));
    }

    private static String digitsOf(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    /** Lowercase, accent-free text so search ignores "José" vs "jose" and casing. */
    private static String fold(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
