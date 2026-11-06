package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.LicenseEvent;
import com.aatechsolutions.elgransazon.domain.entity.SystemLicense;
import com.aatechsolutions.elgransazon.domain.repository.LicenseEventRepository;
import com.aatechsolutions.elgransazon.domain.repository.SystemLicenseRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LicenseServiceReceiptAuthorizationTest {

    private final Company company = Company.builder().idCompany(9L).name("Restaurante").build();
    private final SystemLicenseRepository licenseRepository = mock(SystemLicenseRepository.class);
    private final LicenseEventRepository eventRepository = mock(LicenseEventRepository.class);
    private final LicenseService licenseService = new LicenseService(licenseRepository, eventRepository);

    @BeforeEach
    void setCompanyContext() {
        CompanyContext.setCurrentCompany(company);
        when(licenseRepository.findByCompany(company)).thenReturn(Optional.of(
                SystemLicense.builder().id(7L).company(company).build()));
    }

    @AfterEach
    void clearCompanyContext() {
        CompanyContext.clear();
    }

    @Test
    void returnsPaidEventForCurrentCompanyLicense() {
        LicenseEvent event = paidEvent(7L, LicenseEvent.EventType.RENEWED);
        when(eventRepository.findById(42L)).thenReturn(Optional.of(event));

        assertThat(licenseService.getPaidEventForCurrentLicense(42L)).contains(event);
    }

    @Test
    void doesNotReturnAnotherCompaniesPayment() {
        when(eventRepository.findById(42L)).thenReturn(
                Optional.of(paidEvent(8L, LicenseEvent.EventType.RENEWED)));

        assertThat(licenseService.getPaidEventForCurrentLicense(42L)).isEmpty();
    }

    @Test
    void doesNotReturnNonPaymentLicenseEvents() {
        when(eventRepository.findById(42L)).thenReturn(
                Optional.of(paidEvent(7L, LicenseEvent.EventType.UPDATED)));

        assertThat(licenseService.getPaidEventForCurrentLicense(42L)).isEmpty();
    }

    private LicenseEvent paidEvent(Long licenseId, LicenseEvent.EventType type) {
        return LicenseEvent.builder()
                .id(42L)
                .licenseId(licenseId)
                .eventType(type)
                .amount(100.0)
                .build();
    }
}
