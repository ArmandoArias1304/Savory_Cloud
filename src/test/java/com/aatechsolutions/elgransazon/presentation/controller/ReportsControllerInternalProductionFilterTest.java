package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.IngredientCategoryService;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.application.service.ItemPreparationService;
import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.application.service.ReportPdfService;
import com.aatechsolutions.elgransazon.domain.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * The internal-production block has to cover the same dates the user picks next to the download
 * buttons. If the range were ignored, the PDF would mix batches from every day, which is exactly
 * what makes a report useless.
 */
class ReportsControllerInternalProductionFilterTest {

    private ItemPreparationService preparationService;
    private ReportsController controller;

    @BeforeEach
    void setUp() {
        preparationService = mock(ItemPreparationService.class);
        controller = new ReportsController(
                mock(OrderService.class),
                mock(ReportPdfService.class),
                preparationService,
                mock(IngredientService.class),
                mock(IngredientCategoryService.class),
                mock(CategoryService.class),
                mock(CustomerRepository.class),
                mock(DateTimeService.class));
    }

    @Test
    @DisplayName("El rango elegido se pasa tal cual a la producción interna")
    void theChosenRangeReachesTheInternalProductionLookup() {
        controller.findInternalProduction("2026-10-01", "2026-10-20");

        verify(preparationService).findPreparationsForReport(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20));
        verifyNoMoreInteractions(preparationService);
    }

    @Test
    @DisplayName("Una sola fecha se manda como ese día, sin inventar el otro extremo")
    void aSingleDateIsSentAsTheOnlyBound() {
        controller.findInternalProduction("2026-10-20", null);

        verify(preparationService).findPreparationsForReport(LocalDate.of(2026, 10, 20), null);
    }

    @Test
    @DisplayName("Sin fechas se piden todos los lotes")
    void withoutDatesEveryBatchIsRequested() {
        controller.findInternalProduction(null, "");

        verify(preparationService).findPreparationsForReport(null, null);
    }

    @Test
    @DisplayName("Una fecha inválida no rompe la descarga: se cae a todos los lotes")
    void anInvalidDateFallsBackToEveryBatch() {
        controller.findInternalProduction("20/10/2026", null);

        verify(preparationService).findPreparationsForReport(null, null);
    }
}
