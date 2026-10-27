package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.LoyaltyAccount;
import com.aatechsolutions.elgransazon.domain.entity.LoyaltyProgram;
import com.aatechsolutions.elgransazon.domain.entity.LoyaltyStampEventType;
import com.aatechsolutions.elgransazon.domain.repository.LoyaltyAccountRepository;
import com.aatechsolutions.elgransazon.domain.repository.LoyaltyProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Processes global stamp-cycle deadlines without deleting the loyalty ledger.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyCycleService {

    private final LoyaltyProgramRepository programRepository;
    private final LoyaltyAccountRepository accountRepository;
    private final LoyaltyLedgerService ledgerService;
    private final DateTimeService dateTimeService;

    /**
     * Expires the current balance once its last valid day has passed.
     *
     * @return whether the program can currently earn and redeem stamps
     */
    @Transactional
    public boolean processAndCheck(Company company) {
        Optional<LoyaltyProgram> result = programRepository.findByCompanyForUpdate(company);
        if (result.isEmpty()) {
            return true;
        }

        LoyaltyProgram program = result.get();
        LocalDate deadline = program.getStampCycleDeadline();
        LocalDate today = dateTimeService.todayLocal();
        if (deadline != null && deadline.isBefore(today)
                && !deadline.equals(program.getStampCycleResetDeadline())) {
            resetAccounts(company, deadline);
            program.setStampCycleResetDeadline(deadline);
            programRepository.save(program);
            log.info("Loyalty stamp cycle expired for company {} at {}",
                    company.getIdCompany(), deadline);
        }

        return deadline == null || !deadline.isBefore(today);
    }

    @Transactional
    public boolean processExpiredCycle(Company company) {
        return processAndCheck(company);
    }

    private void resetAccounts(Company company, LocalDate deadline) {
        List<LoyaltyAccount> accounts = accountRepository.findByCompanyOrderByBalance(company);
        for (LoyaltyAccount listedAccount : accounts) {
            LoyaltyAccount account = accountRepository.findByIdForUpdate(listedAccount.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "No se encontró una cuenta de fidelización durante el reinicio"));
            int expiredStamps = account.safeBalance();
            if (expiredStamps == 0) {
                continue;
            }

            account.setBalance(0);
            accountRepository.save(account);
            ledgerService.record(company, account, null, null,
                    LoyaltyStampEventType.EXPIRE, -expiredStamps, 0,
                    "Vencimiento del ciclo de sellos al " + deadline, "SYSTEM");
        }
    }
}
