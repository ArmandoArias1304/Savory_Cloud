package com.aatechsolutions.elgransazon.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Service for timezone-aware date/time operations in the service and controller layers.
 *
 * All timestamps stored in the database are UTC (the JVM runs in UTC).
 * This service converts those UTC values to the current company's local timezone
 * for display purposes and for business-logic comparisons (e.g. "is today?").
 *
 * Defaults to America/Mexico_City when no CompanyContext is available
 * (programmer routes, scheduled tasks).
 */
public interface DateTimeService {

    /** Current LocalDateTime in the company's local timezone. */
    LocalDateTime nowLocal();

    /** Current LocalDate in the company's local timezone. */
    LocalDate todayLocal();

    /** ZoneId of the current company, or the default (America/Mexico_City). */
    ZoneId getCompanyZone();

    /**
     * Convert a UTC LocalDateTime (as stored in the DB) to the company's local timezone.
     * Returns null if utcDateTime is null.
     */
    LocalDateTime toCompanyTime(LocalDateTime utcDateTime);

    /**
     * Format a UTC LocalDateTime in the company's local timezone using the given pattern.
     * Returns an empty string if utcDateTime is null.
     */
    String formatToCompanyTime(LocalDateTime utcDateTime, String pattern);

    /**
     * Convert the start of a company-local date (midnight) to a UTC LocalDateTime for DB comparisons.
     * Use this instead of localDate.atStartOfDay() when comparing against UTC-stored DB values.
     */
    LocalDateTime startOfDayUtc(LocalDate localDate);

    /**
     * Convert the end of a company-local date (23:59:59.999...) to a UTC LocalDateTime for DB comparisons.
     * Use this instead of localDate.atTime(23,59,59) when comparing against UTC-stored DB values.
     */
    LocalDateTime endOfDayUtc(LocalDate localDate);
}
