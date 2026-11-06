package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.IngredientWaste;
import com.aatechsolutions.elgransazon.domain.entity.IngredientWasteReason;
import com.aatechsolutions.elgransazon.domain.repository.IngredientRepository;
import com.aatechsolutions.elgransazon.domain.repository.IngredientWasteRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngredientWasteServiceImplTest {

    private IngredientRepository ingredientRepository;
    private IngredientWasteRepository wasteRepository;
    private IngredientWasteServiceImpl service;
    private Company company;
    private Ingredient ingredient;

    @BeforeEach
    void setUp() {
        ingredientRepository = mock(IngredientRepository.class);
        wasteRepository = mock(IngredientWasteRepository.class);
        service = new IngredientWasteServiceImpl(ingredientRepository, wasteRepository);
        company = Company.builder().idCompany(1L).name("Restaurante").slug("restaurante").build();
        CompanyContext.setCurrentCompany(company);
        ingredient = Ingredient.builder()
                .idIngredient(4L)
                .company(company)
                .name("Aceite")
                .unitOfMeasure("LT")
                .currentStock(new BigDecimal("10.00"))
                .costPerUnit(new BigDecimal("50.00"))
                .build();
        when(ingredientRepository.findByIdWithLock(4L)).thenReturn(Optional.of(ingredient));
        when(wasteRepository.save(any(IngredientWaste.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void recordsWasteCostAndDeductsStockAtomically() {
        IngredientWaste waste = service.recordWaste(4L, new BigDecimal("1.25"),
                IngredientWasteReason.SPILLED, LocalDate.of(2026, 11, 4), "Se cayó el recipiente", null);

        assertThat(ingredient.getCurrentStock()).isEqualByComparingTo("8.75");
        assertThat(waste.getQuantity()).isEqualByComparingTo("1.25");
        assertThat(waste.getUnitCostSnapshot()).isEqualByComparingTo("50.00");
        assertThat(waste.getTotalCost()).isEqualByComparingTo("62.50");
        assertThat(waste.getReason()).isEqualTo(IngredientWasteReason.SPILLED);
        assertThat(waste.getOccurredOn()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(waste.getNote()).isEqualTo("Se cayó el recipiente");
        verify(ingredientRepository).save(ingredient);
    }

    @Test
    void doesNotRecordMoreThanAvailableStock() {
        assertThatThrownBy(() -> service.recordWaste(4L, new BigDecimal("10.01"),
                IngredientWasteReason.EXPIRED, LocalDate.now(), null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stock insuficiente");

        assertThat(ingredient.getCurrentStock()).isEqualByComparingTo("10.00");
        org.mockito.Mockito.verifyNoInteractions(wasteRepository);
    }

    @Test
    void preventsRecordingWasteForAnotherCompany() {
        ingredient.setCompany(Company.builder().idCompany(2L).name("Otra").slug("otra").build());

        assertThatThrownBy(() -> service.recordWaste(4L, BigDecimal.ONE,
                IngredientWasteReason.BURNT, LocalDate.now(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no pertenece");
        org.mockito.Mockito.verifyNoInteractions(wasteRepository);
    }
}
