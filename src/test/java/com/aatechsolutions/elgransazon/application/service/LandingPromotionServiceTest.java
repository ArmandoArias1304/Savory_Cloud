package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Promotion;
import com.aatechsolutions.elgransazon.domain.entity.PromotionType;
import com.aatechsolutions.elgransazon.domain.repository.PromotionRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La landing anuncia una promoción desde que está activa y dentro de sus fechas, aunque en este
 * momento esté fuera de su horario: el cliente la lee hoy y vuelve el viernes. Aplicar el
 * descuento sigue siendo cosa de {@code isValidNow()}, así que anunciarla no regala el precio.
 */
@ExtendWith(MockitoExtension.class)
class LandingPromotionServiceTest {

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private DateTimeService dateTimeService;

    private PromotionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PromotionServiceImpl(promotionRepository, dateTimeService);
        CompanyContext.setCurrentCompany(Company.builder()
                .idCompany(7L)
                .name("Ambar Gourmet")
                .slug("ambar-gourmet")
                .build());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    /** miércoles 21/10/2026, 10:00 — la promoción es de viernes 18:00 a 21:00. */
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 21);
    private static final LocalDateTime WEDNESDAY_MORNING = WEDNESDAY.atTime(10, 0);

    private Promotion fridayNightPromotion() {
        return Promotion.builder()
                .idPromotion(1L)
                .name("Noches de viernes 2x1")
                .promotionType(PromotionType.BUY_X_PAY_Y)
                .buyQuantity(2)
                .payQuantity(1)
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 31))
                .validDays("FRIDAY")
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(21, 0))
                .active(true)
                .build();
    }

    @Test
    void landingAnnouncesItEvenWhenTodayIsNotItsDayOrHour() {
        Promotion promotion = fridayNightPromotion();
        Company company = CompanyContext.getCurrentCompany();

        when(dateTimeService.todayLocal()).thenReturn(WEDNESDAY);
        when(promotionRepository.findActivePromotionsForDateByCompany(company, WEDNESDAY))
                .thenReturn(List.of(promotion));

        List<Promotion> announced = service.findActiveForLanding();

        assertEquals(List.of(promotion), announced,
                "the landing shows an active promotion inside its dates, whatever the time");
        verify(promotionRepository).findActivePromotionsForDateByCompany(company, WEDNESDAY);
    }

    @Test
    void theDiscountStillOnlyAppliesInsideItsWindow() {
        Promotion promotion = fridayNightPromotion();
        Company company = CompanyContext.getCurrentCompany();

        when(dateTimeService.nowLocal()).thenReturn(WEDNESDAY_MORNING);
        when(promotionRepository.findActivePromotionsForDateByCompany(company, WEDNESDAY))
                .thenReturn(List.of(promotion));

        assertTrue(service.findActivePromotions().isEmpty(),
                "applying the promotion must keep respecting days and hours");
    }
}
