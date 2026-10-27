package com.aatechsolutions.elgransazon.infrastructure.scheduler;

import com.aatechsolutions.elgransazon.application.service.LoyaltyCycleService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.repository.CompanyRepository;
import com.aatechsolutions.elgransazon.domain.repository.LoyaltyProgramRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoyaltyCycleExpiryJob {

    private final LoyaltyProgramRepository programRepository;
    private final CompanyRepository companyRepository;
    private final LoyaltyCycleService cycleService;

    @Scheduled(fixedDelayString = "${loyalty.cycle-expiry-check-ms:60000}")
    public void processExpiredCycles() {
        for (Long companyId : programRepository.findCompanyIdsWithStampCycleDeadline()) {
            try {
                Company company = companyRepository.findById(companyId)
                        .orElseThrow(() -> new IllegalStateException(
                                "No se encontró la empresa " + companyId
                                        + " para procesar el ciclo de fidelización"));
                CompanyContext.setCurrentCompany(company);
                cycleService.processAndCheck(company);
            } catch (RuntimeException e) {
                log.error("No se pudo procesar el vencimiento de fidelización para la empresa {}",
                        companyId, e);
            } finally {
                CompanyContext.clear();
            }
        }
    }
}
