package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.IngredientCategory;
import com.aatechsolutions.elgransazon.domain.entity.IngredientStockHistory;
import com.aatechsolutions.elgransazon.domain.repository.CompanyRepository;
import com.aatechsolutions.elgransazon.domain.repository.IngredientCategoryRepository;
import com.aatechsolutions.elgransazon.domain.repository.IngredientRepository;
import com.aatechsolutions.elgransazon.domain.repository.IngredientStockHistoryRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Editar y anular entradas del historial contra la base real.
 *
 * <p>Aquí se comprueba lo que ve el usuario después de la operación: el stock del ingrediente,
 * la entrada que desaparece del historial y el gasto que deja de contar. Todo corre en una
 * transacción que se revierte, así que no queda nada en la base de desarrollo.</p>
 */
@SpringBootTest
@Transactional
class IngredientStockHistoryCorrectionIntegrationTest {

    @Autowired
    private IngredientService ingredientService;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private IngredientStockHistoryRepository historyRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private IngredientCategoryRepository categoryRepository;

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("anular una entrada la quita del historial y de los gastos, y resta su cantidad")
    void voidingRemovesTheEntryFromHistoryAndExpenses() {
        Ingredient ingredient = newIngredient();
        IngredientStockHistory entry = historyRepository.save(entry(ingredient, "4.00", "5.00"));

        assertThat(ingredientService.getStockHistory(ingredient.getIdIngredient())).hasSize(1);
        assertThat(ingredientService.getTotalCostByIngredient(ingredient.getIdIngredient()))
                .isEqualByComparingTo("20.00");

        ingredientService.voidStockHistory(ingredient.getIdIngredient(), entry.getId(),
                "compra duplicada", null);

        assertThat(ingredientService.getStockHistory(ingredient.getIdIngredient())).isEmpty();
        assertThat(ingredientService.getTotalCostByIngredient(ingredient.getIdIngredient()))
                .isEqualByComparingTo("0.00");
        assertThat(reloaded(ingredient).getCurrentStock()).isEqualByComparingTo("6.00");
        assertThat(entry.getVoidedAt()).isNotNull();
        assertThat(entry.getVoidReason()).isEqualTo("compra duplicada");
    }

    @Test
    @DisplayName("editar la cantidad mueve el stock por la diferencia y recalcula el gasto")
    void editingMovesTheStockAndRecomputesTheExpense() {
        Ingredient ingredient = newIngredient();
        IngredientStockHistory entry = historyRepository.save(entry(ingredient, "4.00", "5.00"));

        ingredientService.updateStockHistory(ingredient.getIdIngredient(), entry.getId(),
                new BigDecimal("3.00"), new BigDecimal("4.00"), null);

        assertThat(reloaded(ingredient).getCurrentStock()).isEqualByComparingTo("9.00");
        assertThat(ingredientService.getStockHistory(ingredient.getIdIngredient()))
                .singleElement()
                .satisfies(saved -> {
                    assertThat(saved.getQuantityAdded()).isEqualByComparingTo("3.00");
                    assertThat(saved.getTotalCost()).isEqualByComparingTo("12.00");
                    assertThat(saved.getEditedAt()).isNotNull();
                });
        assertThat(ingredientService.getTotalCostByIngredient(ingredient.getIdIngredient()))
                .isEqualByComparingTo("12.00");
    }

    // ========== Helpers ==========

    private Ingredient newIngredient() {
        Company company = companyRepository.findAll().stream().findFirst().orElseThrow();
        CompanyContext.setCurrentCompany(company);

        IngredientCategory category = categoryRepository.findByCompany(company).stream()
                .findFirst()
                .orElseGet(() -> categoryRepository.save(IngredientCategory.builder()
                        .company(company)
                        .name("Historial " + UUID.randomUUID().toString().substring(0, 8))
                        .active(true)
                        .build()));

        return ingredientRepository.save(Ingredient.builder()
                .company(company)
                .category(category)
                .name("Historial " + UUID.randomUUID().toString().substring(0, 8))
                .unitOfMeasure("KG")
                .currentStock(new BigDecimal("10.00"))
                .minStock(BigDecimal.ZERO)
                .maxStock(new BigDecimal("100.00"))
                .costPerUnit(new BigDecimal("5.00"))
                .currency("MXN")
                .active(true)
                .build());
    }

    private IngredientStockHistory entry(Ingredient ingredient, String quantity, String cost) {
        return IngredientStockHistory.builder()
                .ingredient(ingredient)
                .quantityAdded(new BigDecimal(quantity))
                .costPerUnit(new BigDecimal(cost))
                .previousStock(new BigDecimal("6.00"))
                .newStock(new BigDecimal("10.00"))
                .addedAt(LocalDateTime.now())
                .build();
    }

    private Ingredient reloaded(Ingredient ingredient) {
        return ingredientRepository.findById(ingredient.getIdIngredient()).orElseThrow();
    }
}
