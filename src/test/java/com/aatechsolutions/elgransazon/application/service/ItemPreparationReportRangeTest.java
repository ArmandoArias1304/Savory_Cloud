package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.ItemPreparation;
import com.aatechsolutions.elgransazon.domain.repository.ItemIngredientRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemPreparationRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The reports ask the internal-production service for a period, so the dates have to mean the
 * same thing the sales reports mean: a local day range converted to the stored instants, and a
 * single bound treated as that one day. Otherwise the PDF would count a different day than the
 * sales next to it.
 */
class ItemPreparationReportRangeTest {

    private ItemPreparationRepository preparationRepository;
    private DateTimeService dateTimeService;
    private ItemPreparationServiceImpl service;
    private Company company;

    @BeforeEach
    void setUp() {
        preparationRepository = mock(ItemPreparationRepository.class);
        dateTimeService = mock(DateTimeService.class);
        service = new ItemPreparationServiceImpl(
                preparationRepository,
                mock(ItemMenuRepository.class),
                mock(ItemIngredientRepository.class),
                mock(IngredientStockService.class),
                mock(ItemMenuService.class),
                dateTimeService);

        company = Company.builder().idCompany(3L).name("El Gran Sazón").build();
        CompanyContext.setCurrentCompany(company);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("Sin fechas el reporte cuenta todos los lotes de la empresa")
    void withoutDatesEveryBatchIsCounted() {
        List<ItemPreparation> all = List.of(ItemPreparation.builder().quantity(10).build());
        when(preparationRepository.findRecentByCompany(eq(company), any(Pageable.class))).thenReturn(all);

        List<ItemPreparation> result = service.findPreparationsForReport(null, null);

        assertEquals(all, result);
    }

    @Test
    @DisplayName("Con una sola fecha el reporte cubre ese único día, igual que las ventas")
    void aSingleDateCoversThatDay() {
        LocalDate day = LocalDate.of(2026, 10, 1);
        LocalDateTime from = LocalDateTime.of(2026, 10, 1, 6, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 2, 5, 59, 59);
        when(dateTimeService.startOfDayUtc(day)).thenReturn(from);
        when(dateTimeService.endOfDayUtc(day)).thenReturn(to);
        when(preparationRepository.findByCompanyAndPeriod(company, from, to)).thenReturn(List.of());

        service.findPreparationsForReport(day, null);

        verify(preparationRepository).findByCompanyAndPeriod(company, from, to);
    }

    @Test
    @DisplayName("Un rango usa el inicio del primer día y el fin del último, en hora de la empresa")
    void aRangeUsesTheCompanyDayBounds() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 5);
        LocalDateTime from = LocalDateTime.of(2026, 10, 1, 6, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 6, 5, 59, 59);
        when(dateTimeService.startOfDayUtc(start)).thenReturn(from);
        when(dateTimeService.endOfDayUtc(end)).thenReturn(to);
        when(preparationRepository.findByCompanyAndPeriod(company, from, to)).thenReturn(List.of());

        service.findPreparationsForReport(start, end);

        verify(preparationRepository).findByCompanyAndPeriod(company, from, to);
    }
}
