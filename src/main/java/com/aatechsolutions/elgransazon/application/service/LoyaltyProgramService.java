package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.LoyaltyProgram;
import com.aatechsolutions.elgransazon.domain.repository.LoyaltyProgramRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Reads and updates the loyalty card configuration of the current restaurant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyProgramService {

    private final LoyaltyProgramRepository programRepository;
    private final LoyaltyCycleService cycleService;
    private final DateTimeService dateTimeService;

    @Transactional(readOnly = true)
    public Optional<LoyaltyProgram> findForCompany(Company company) {
        return programRepository.findByCompany(company);
    }

    @Transactional
    public boolean isCycleOpen(Company company) {
        return cycleService.processAndCheck(company);
    }

    @Transactional
    public LoyaltyProgram getOrCreate(Company company) {
        cycleService.processAndCheck(company);
        return programRepository.findByCompany(company)
                .orElseGet(() -> programRepository.save(LoyaltyProgram.builder()
                        .company(company)
                        .stampsRequired(10)
                        .minimumPurchaseAmount(BigDecimal.ZERO)
                        .minimumItems(1)
                        .maxStampsPerSale(1)
                        .stampExpiryDays(0)
                        .active(true)
                        .updatedBy("SYSTEM")
                        .build()));
    }

    @Transactional
    public LoyaltyProgram requireForCurrentCompany() {
        Company company = CompanyContext.requireCurrentCompany();
        cycleService.processAndCheck(company);
        return programRepository.findByCompany(company)
                .orElseThrow(() -> new IllegalStateException("El programa de fidelización no está configurado"));
    }

    @Transactional
    public LoyaltyProgram update(Company company, LoyaltyProgram form, String username) {
        LoyaltyProgram program = getOrCreate(company);
        LocalDate requestedDeadline = form.getStampCycleDeadline();
        if (requestedDeadline != null && requestedDeadline.isBefore(dateTimeService.todayLocal())) {
            throw new IllegalArgumentException(
                    "La fecha límite debe ser hoy o una fecha futura");
        }

        if (form.getStampsRequired() != null) {
            if (form.getStampsRequired() < 1 || form.getStampsRequired() > 100) {
                throw new IllegalArgumentException("La tarjeta debe requerir entre 1 y 100 sellos");
            }
            program.setStampsRequired(form.getStampsRequired());
        }

        if (form.getMinimumPurchaseAmount() != null) {
            if (form.getMinimumPurchaseAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("El monto mínimo no puede ser negativo");
            }
            program.setMinimumPurchaseAmount(form.getMinimumPurchaseAmount().setScale(2, java.math.RoundingMode.HALF_UP));
        }

        if (form.getMinimumItems() != null) {
            if (form.getMinimumItems() < 0 || form.getMinimumItems() > 100) {
                throw new IllegalArgumentException("Los artículos mínimos deben estar entre 0 y 100");
            }
            program.setMinimumItems(form.getMinimumItems());
        }

        if (form.getMaxStampsPerSale() != null) {
            if (form.getMaxStampsPerSale() < 1 || form.getMaxStampsPerSale() > 10) {
                throw new IllegalArgumentException("El máximo de sellos por venta debe estar entre 1 y 10");
            }
            program.setMaxStampsPerSale(form.getMaxStampsPerSale());
        }

        if (form.getMaxStampsPerDay() != null) {
            if (form.getMaxStampsPerDay() < 0 || form.getMaxStampsPerDay() > 50) {
                throw new IllegalArgumentException("El límite diario debe estar entre 0 y 50");
            }
            program.setMaxStampsPerDay(form.getMaxStampsPerDay());
        }

        if (form.getStampExpiryDays() != null) {
            if (form.getStampExpiryDays() < 0 || form.getStampExpiryDays() > 3650) {
                throw new IllegalArgumentException("La vigencia debe estar entre 0 y 3650 días");
            }
            program.setStampExpiryDays(form.getStampExpiryDays());
        }

        if (!java.util.Objects.equals(program.getStampCycleDeadline(), requestedDeadline)) {
            program.setStampCycleDeadline(requestedDeadline);
            program.setStampCycleResetDeadline(null);
        }

        program.setCardTitle(trimToNull(form.getCardTitle()));
        program.setCardMessage(trimToNull(form.getCardMessage()));
        program.setRewardText(trimToNull(form.getRewardText()));
        program.setLogoUrl(trimToNull(form.getLogoUrl()));
        program.setTerms(trimToNull(form.getTerms()));
        program.setActive(form.getActive() == null || form.getActive());
        program.setUpdatedBy(username);

        LoyaltyProgram saved = programRepository.save(program);
        log.info("Loyalty program updated for company {} by {}", company.getIdCompany(), username);
        return saved;
    }

    /**
     * Saves only the two face colours of the client card.
     *
     * <p>It is a separate operation (and endpoint) from {@link #update} so the
     * small colour form never overwrites the rules or the texts. A blank value
     * keeps the colour already stored; a malformed one is rejected.</p>
     */
    @Transactional
    public LoyaltyProgram updateCardColors(Company company, String frontColor, String backColor,
                                           String username) {
        LoyaltyProgram program = getOrCreate(company);

        if (frontColor != null && !frontColor.isBlank()) {
            String front = LoyaltyProgram.normalizeHex(frontColor);
            if (front == null) {
                throw new IllegalArgumentException(
                        "El color del frente debe tener el formato #RRGGBB");
            }
            program.setCardFrontColor(front);
        }

        if (backColor != null && !backColor.isBlank()) {
            String back = LoyaltyProgram.normalizeHex(backColor);
            if (back == null) {
                throw new IllegalArgumentException(
                        "El color del reverso debe tener el formato #RRGGBB");
            }
            program.setCardBackColor(back);
        }

        program.setUpdatedBy(username);
        LoyaltyProgram saved = programRepository.save(program);
        log.info("Loyalty card colours updated for company {} by {}", company.getIdCompany(), username);
        return saved;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
