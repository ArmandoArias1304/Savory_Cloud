package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.LicenseEvent;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.SystemLicense;
import com.aatechsolutions.elgransazon.domain.repository.LicenseEventRepository;
import com.aatechsolutions.elgransazon.domain.repository.SystemLicenseRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LicenseServicePaymentMethodTest {

    private final Company company = Company.builder().idCompany(9L).name("Restaurante").build();
    private final SystemLicenseRepository licenseRepository = mock(SystemLicenseRepository.class);
    private final LicenseEventRepository eventRepository = mock(LicenseEventRepository.class);
    private final LicenseService licenseService = new LicenseService(licenseRepository, eventRepository);

    @Test
    void renewalStoresThePaymentMethodOnTheEvent() {
        SystemLicense license = SystemLicense.builder()
                .id(7L)
                .company(company)
                .purchaseDate(LocalDateTime.now().minusMonths(1))
                .expirationDate(LocalDateTime.now().plusMonths(1))
                .status(SystemLicense.LicenseStatus.ACTIVE)
                .build();
        when(licenseRepository.findById(7L)).thenReturn(Optional.of(license));

        licenseService.renewLicenseById(7L, 1, 500.0, "programmer", PaymentMethodType.TRANSFER);

        ArgumentCaptor<LicenseEvent> event = ArgumentCaptor.forClass(LicenseEvent.class);
        verify(eventRepository).save(event.capture());
        assertThat(event.getValue().getPaymentMethod()).isEqualTo(PaymentMethodType.TRANSFER);
        assertThat(event.getValue().getAmount()).isEqualTo(500.0);
        assertThat(event.getValue().getMonths()).isEqualTo(1);
    }
}
