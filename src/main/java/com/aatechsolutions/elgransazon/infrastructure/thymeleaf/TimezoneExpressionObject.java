package com.aatechsolutions.elgransazon.infrastructure.thymeleaf;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Thymeleaf expression object exposed as {@code #tz}.
 *
 * Usage in templates:
 * <pre>
 *   ${#tz.format(order.createdAt, 'dd/MM/yyyy HH:mm')}
 *   ${#tz.format(record.addedAt, 'HH:mm')}
 * </pre>
 *
 * For {@link LocalDateTime}: converts from UTC (as stored in DB) to the current company timezone.
 * For {@link LocalDate}: plain formatting — no timezone shift.
 * Null-safe: returns empty string for null values.
 *
 * Company timezone is resolved from {@link CompanyContext} (ThreadLocal).
 * Defaults to America/Mexico_City when no company context is present
 * (programmer routes, scheduled tasks, etc.).
 */
public class TimezoneExpressionObject {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Mexico_City");
    private static final ZoneId UTC = ZoneId.of("UTC");

    private ZoneId getZone() {
        Company company = CompanyContext.getCurrentCompany();
        if (company != null && company.getTimezone() != null && !company.getTimezone().isBlank()) {
            try {
                return ZoneId.of(company.getTimezone());
            } catch (Exception ignored) {
                // fall through
            }
        }
        return DEFAULT_ZONE;
    }

    /**
     * Format a UTC-stored LocalDateTime in the company's local timezone.
     */
    public String format(LocalDateTime utcDateTime, String pattern) {
        if (utcDateTime == null) return "";
        ZoneId zone = getZone();
        LocalDateTime local = utcDateTime.atZone(UTC).withZoneSameInstant(zone).toLocalDateTime();
        return local.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Format a LocalDate (no timezone conversion — dates are timezone-unaware).
     */
    public String format(LocalDate date, String pattern) {
        if (date == null) return "";
        return date.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Generic overload — dispatches to the typed methods above.
     */
    public String format(Object temporal, String pattern) {
        if (temporal == null) return "";
        if (temporal instanceof LocalDateTime) return format((LocalDateTime) temporal, pattern);
        if (temporal instanceof LocalDate)    return format((LocalDate) temporal, pattern);
        return temporal.toString();
    }
}
