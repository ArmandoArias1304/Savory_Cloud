package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.LoyaltyAccount;
import com.aatechsolutions.elgransazon.domain.entity.LoyaltyProgram;
import com.aatechsolutions.elgransazon.domain.entity.LoyaltyStampEventType;
import com.aatechsolutions.elgransazon.domain.repository.LoyaltyAccountRepository;
import com.aatechsolutions.elgransazon.domain.repository.LoyaltyProgramRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoyaltyCycleServiceTest {

    @Mock private LoyaltyProgramRepository programRepository;
    @Mock private LoyaltyAccountRepository accountRepository;
    @Mock private LoyaltyLedgerService ledgerService;
    @Mock private DateTimeService dateTimeService;

    private LoyaltyCycleService service;
    private Company company;
    private LoyaltyProgram program;
    private LoyaltyAccount account;

    @BeforeEach
    void setUp() {
        service = new LoyaltyCycleService(
                programRepository, accountRepository, ledgerService, dateTimeService);
        company = Company.builder().idCompany(7L).build();
        program = LoyaltyProgram.builder()
                .company(company)
                .stampCycleDeadline(LocalDate.of(2026, 12, 31))
                .build();
        account = LoyaltyAccount.builder()
                .id(11L)
                .company(company)
                .balance(6)
                .lifetimeEarned(14)
                .lifetimeRedeemed(8)
                .build();
        when(dateTimeService.todayLocal()).thenReturn(LocalDate.of(2027, 1, 1));
        when(programRepository.findByCompanyForUpdate(company)).thenReturn(Optional.of(program));
        lenient().when(accountRepository.findByCompanyOrderByBalance(company)).thenReturn(List.of(account));
        lenient().when(accountRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(account));
        lenient().when(accountRepository.save(account)).thenReturn(account);
        lenient().when(programRepository.save(program)).thenReturn(program);
    }

    @Test
    void resetsBalanceOnceAndPreservesLifetimeTotalsAndLedgerHistory() {
        assertFalse(service.processAndCheck(company));

        assertTrue(account.getBalance() == 0);
        assertTrue(account.getLifetimeEarned() == 14);
        assertTrue(account.getLifetimeRedeemed() == 8);
        verify(ledgerService).record(eq(company), eq(account), isNull(), isNull(),
                eq(LoyaltyStampEventType.EXPIRE), eq(-6), eq(0),
                contains("2026-12-31"), eq("SYSTEM"));

        assertFalse(service.processAndCheck(company));
        verify(accountRepository, times(1)).findByCompanyOrderByBalance(company);
        verify(ledgerService, times(1)).record(nullable(Company.class),
                nullable(LoyaltyAccount.class), nullable(com.aatechsolutions.elgransazon.domain.entity.Order.class),
                nullable(com.aatechsolutions.elgransazon.domain.entity.LoyaltyRedemption.class),
                any(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void leavesStampsValidThroughTheConfiguredDeadline() {
        when(dateTimeService.todayLocal()).thenReturn(LocalDate.of(2026, 12, 31));

        assertTrue(service.processAndCheck(company));
        assertTrue(account.getBalance() == 6);
        verify(ledgerService, never()).record(
                any(), any(), any(), any(), any(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void doesNotExpireStampsWhenThereIsNoDeadline() {
        program.setStampCycleDeadline(null);

        assertTrue(service.processAndCheck(company));
        assertTrue(account.getBalance() == 6);
        verify(ledgerService, never()).record(
                any(), any(), any(), any(), any(), anyInt(), anyInt(), anyString(), anyString());
    }
}
