package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.IngredientStockHistory;
import com.aatechsolutions.elgransazon.domain.repository.ComplementIngredientRepository;
import com.aatechsolutions.elgransazon.domain.repository.IngredientCategoryRepository;
import com.aatechsolutions.elgransazon.domain.repository.IngredientRepository;
import com.aatechsolutions.elgransazon.domain.repository.IngredientStockHistoryRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemIngredientRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Corregir y anular entradas del historial de stock.
 *
 * <p>Lo delicado es el stock: editar ajusta solo la diferencia y anular resta la cantidad
 * completa. En ambos casos el inventario nunca puede quedar negativo, y una entrada anulada no
 * se puede volver a tocar. Se fija también que no se pueda operar sobre el historial de otro
 * ingrediente cambiando el id de la ruta.</p>
 */
class IngredientStockHistoryCorrectionTest {

    private IngredientRepository ingredientRepository;
    private IngredientStockHistoryRepository historyRepository;
    private IngredientServiceImpl service;

    private Company company;
    private Employee editor;
    private Ingredient ingredient;
    private IngredientStockHistory record;

    @BeforeEach
    void setUp() {
        ingredientRepository = mock(IngredientRepository.class);
        historyRepository = mock(IngredientStockHistoryRepository.class);

        service = new IngredientServiceImpl(
                ingredientRepository,
                mock(IngredientCategoryRepository.class),
                historyRepository,
                mock(ItemIngredientRepository.class),
                mock(ComplementIngredientRepository.class));

        company = Company.builder().idCompany(1L).slug("ambar").name("Ambar").build();
        CompanyContext.setCurrentCompany(company);

        editor = Employee.builder().idEmpleado(5L).nombre("Ana").apellido("Ruiz").build();

        ingredient = Ingredient.builder()
                .idIngredient(7L)
                .company(company)
                .name("Tomate")
                .unitOfMeasure("KG")
                .currentStock(new BigDecimal("16.00"))
                .minStock(BigDecimal.ZERO)
                .maxStock(new BigDecimal("100.00"))
                .active(true)
                .build();

        // Entrada original: +10, stock 6 -> 16.
        record = IngredientStockHistory.builder()
                .id(99L)
                .ingredient(ingredient)
                .quantityAdded(new BigDecimal("10.00"))
                .costPerUnit(new BigDecimal("5.00"))
                .previousStock(new BigDecimal("6.00"))
                .newStock(new BigDecimal("16.00"))
                .addedAt(LocalDateTime.now())
                .addedBy(editor)
                .build();

        when(historyRepository.findByIdAndIngredient_Company(99L, company))
                .thenReturn(Optional.of(record));
        when(ingredientRepository.save(any(Ingredient.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.save(any(IngredientStockHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("editar la cantidad ajusta el stock solo por la diferencia")
    void editingTheQuantityAdjustsTheStockByTheDelta() {
        service.updateStockHistory(7L, 99L, new BigDecimal("8.00"),
                new BigDecimal("5.00"), editor);

        assertThat(ingredient.getCurrentStock()).isEqualByComparingTo("14.00");
        assertThat(record.getQuantityAdded()).isEqualByComparingTo("8.00");
        assertThat(record.getTotalCost()).isEqualByComparingTo("40.00");
        assertThat(record.getNewStock()).isEqualByComparingTo("14.00");
        assertThat(record.getEditedAt()).isNotNull();
        assertThat(record.getEditedBy()).isSameAs(editor);
    }

    @Test
    @DisplayName("editar solo el costo no mueve el stock")
    void editingOnlyTheCostLeavesTheStockUntouched() {
        service.updateStockHistory(7L, 99L, new BigDecimal("10.00"),
                new BigDecimal("6.50"), editor);

        assertThat(ingredient.getCurrentStock()).isEqualByComparingTo("16.00");
        assertThat(record.getTotalCost()).isEqualByComparingTo("65.00");
        assertThat(record.getEditedAt()).isNotNull();
    }

    @Test
    @DisplayName("una corrección que dejaría el stock negativo se rechaza sin tocar nada")
    void anEditThatWouldGoNegativeIsRejected() {
        ingredient.setCurrentStock(new BigDecimal("3.00"));

        assertThatThrownBy(() -> service.updateStockHistory(7L, 99L,
                new BigDecimal("4.00"), new BigDecimal("5.00"), editor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No se puede corregir")
                .hasMessageContaining("3.00");

        assertThat(ingredient.getCurrentStock()).isEqualByComparingTo("3.00");
        assertThat(record.getQuantityAdded()).isEqualByComparingTo("10.00");
        verify(ingredientRepository, never()).save(any());
    }

    @Test
    @DisplayName("anular resta la cantidad completa y deja el motivo y quién lo hizo")
    void voidingSubtractsTheWholeQuantity() {
        service.voidStockHistory(7L, 99L, "compra duplicada", editor);

        assertThat(ingredient.getCurrentStock()).isEqualByComparingTo("6.00");
        assertThat(record.isVoided()).isTrue();
        assertThat(record.getVoidedAt()).isNotNull();
        assertThat(record.getVoidedBy()).isSameAs(editor);
        assertThat(record.getVoidReason()).isEqualTo("compra duplicada");
    }

    @Test
    @DisplayName("anular una entrada que ya se consumió se bloquea con el cálculo explícito")
    void voidingWhenTheStockWasAlreadyConsumedIsBlocked() {
        ingredient.setCurrentStock(new BigDecimal("8.00"));

        assertThatThrownBy(() -> service.voidStockHistory(7L, 99L, null, editor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No se puede anular")
                .hasMessageContaining("-2.00");

        assertThat(ingredient.getCurrentStock()).isEqualByComparingTo("8.00");
        assertThat(record.isVoided()).isFalse();
        verify(ingredientRepository, never()).save(any());
    }

    @Test
    @DisplayName("una entrada anulada ya no se puede editar ni volver a anular")
    void anAlreadyVoidedEntryCannotBeTouched() {
        record.setVoidedAt(LocalDateTime.now());
        record.setVoidedBy(editor);

        assertThatThrownBy(() -> service.updateStockHistory(7L, 99L,
                new BigDecimal("8.00"), new BigDecimal("5.00"), editor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya fue anulada");

        assertThatThrownBy(() -> service.voidStockHistory(7L, 99L, null, editor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya fue anulada");
    }

    @Test
    @DisplayName("no se puede tocar una entrada de otro ingrediente cambiando el id de la ruta")
    void anEntryOfAnotherIngredientIsRejected() {
        assertThatThrownBy(() -> service.updateStockHistory(8L, 99L,
                new BigDecimal("8.00"), new BigDecimal("5.00"), editor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no pertenece a este ingrediente");
    }

    @Test
    @DisplayName("para unidades (UN) la cantidad corregida debe ser entera")
    void unitIngredientsOnlyAcceptWholeQuantities() {
        ingredient.setUnitOfMeasure("UN");

        assertThatThrownBy(() -> service.updateStockHistory(7L, 99L,
                new BigDecimal("2.50"), new BigDecimal("5.00"), editor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("números enteros");
    }

    @Test
    @DisplayName("la cantidad corregida debe ser mayor a cero")
    void theCorrectedQuantityMustBePositive() {
        assertThatThrownBy(() -> service.updateStockHistory(7L, 99L,
                BigDecimal.ZERO, new BigDecimal("5.00"), editor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayor a 0");
    }
}
