package com.aatechsolutions.elgransazon.infrastructure.util;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Static utility for domain entities to obtain the current date/time
 * in the active company's timezone WITHOUT introducing a service dependency.
 *
 * Strategy:
 *   • Reads the current Company from CompanyContext (ThreadLocal, set by CompanyContextFilter).
 *   • Falls back to America/Mexico_City when no company context is present
 *     (e.g. scheduler threads, programmer routes).
 *
 * IMPORTANT: Do NOT use this for persisting timestamps to the DB.
 * Use regular LocalDateTime.now() for @PrePersist/@PreUpdate so that UTC is always stored.
 * Use this ONLY for business-logic comparisons (e.g. isOpen?, isAvailableNow?).
 */
public final class CompanyLocalTime {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Mexico_City");

    private CompanyLocalTime() {}

    public static LocalDateTime now() {
        return LocalDateTime.now(getZone());
    }

    public static LocalDate today() {
        return LocalDate.now(getZone());
    }

    public static ZoneId getZone() {
        Company company = CompanyContext.getCurrentCompany();
        if (company != null && company.getTimezone() != null && !company.getTimezone().isBlank()) {
            try {
                return ZoneId.of(company.getTimezone());
            } catch (Exception ignored) {
                // fall through to default
            }
        }
        return DEFAULT_ZONE;
    }
}
