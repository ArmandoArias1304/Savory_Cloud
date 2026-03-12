package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class DateTimeServiceImpl implements DateTimeService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Mexico_City");
    private static final ZoneId UTC = ZoneId.of("UTC");

    @Override
    public LocalDateTime nowLocal() {
        return LocalDateTime.now(getCompanyZone());
    }

    @Override
    public LocalDate todayLocal() {
        return LocalDate.now(getCompanyZone());
    }

    @Override
    public ZoneId getCompanyZone() {
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

    @Override
    public LocalDateTime toCompanyTime(LocalDateTime utcDateTime) {
        if (utcDateTime == null) return null;
        ZonedDateTime utcZoned = utcDateTime.atZone(UTC);
        return utcZoned.withZoneSameInstant(getCompanyZone()).toLocalDateTime();
    }

    @Override
    public String formatToCompanyTime(LocalDateTime utcDateTime, String pattern) {
        if (utcDateTime == null) return "";
        return toCompanyTime(utcDateTime).format(DateTimeFormatter.ofPattern(pattern));
    }

    @Override
    public LocalDateTime startOfDayUtc(LocalDate localDate) {
        return localDate.atStartOfDay(getCompanyZone()).withZoneSameInstant(UTC).toLocalDateTime();
    }

    @Override
    public LocalDateTime endOfDayUtc(LocalDate localDate) {
        return localDate.atTime(LocalTime.MAX).atZone(getCompanyZone()).withZoneSameInstant(UTC).toLocalDateTime();
    }
}
