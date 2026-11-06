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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Costo promedio ponderado del ingrediente.
 *
 * <p>El costo con el que se costean los platillos sale del promedio de las compras, no del último
 * precio pagado. Se fija aquí que una compra más cara sube el promedio, que las ventas no lo
 * mueven (por eso no hace falta registrar cada salida), que corregir y anular una compra lo
 * recalculan, y que sin compras vigentes se conserva el costo anterior en vez de perderlo.</p>
 */
class IngredientAverageCostTest {

    private IngredientRepository ingredientRepository;
    private IngredientStockHistoryRepository historyRepository;
    private IngredientServiceImpl service;

    private Company company;
    private Employee buyer;
    private Ingredient tomato;

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

        buyer = Employee.builder().idEmpleado(5L).nombre("Ana").apellido("Ruiz").build();

        tomato = Ingredient.builder()
                .idIngredient(7L)
                .company(company)
                .name("Tomate")
                .unitOfMeasure("KG")
                .currentStock(new BigDecimal("10.00"))
                .minStock(BigDecimal.ZERO)
                .maxStock(new BigDecimal("100.00"))
                .costPerUnit(new BigDecimal("5.00"))
                .active(true)
                .build();

        when(ingredientRepository.findById(7L)).thenReturn(Optional.of(tomato));
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
    @DisplayName("una compra más cara sube el costo promedio ponderado")
    void aDearerPurchaseRaisesTheWeightedAverage() {
        stubValidPurchases(purchase("10.00", "5.00"), purchase("10.00", "7.00"));

        service.addStock(7L, new BigDecimal("10.00"), new BigDecimal("7.00"), buyer);

        assertThat(tomato.getCurrentStock()).isEqualByComparingTo("20.00");
        assertThat(tomato.getCostPerUnit()).isEqualByComparingTo("6.00");
    }

    @Test
    @DisplayName("el promedio no depende del stock que queda: las ventas no lo mueven")
    void theAverageIgnoresWhatSalesLeftInStock() {
        // Casi todo se vendió: solo quedan 2 kg, pero el promedio de compras sigue siendo 6.00.
        tomato.setCurrentStock(new BigDecimal("2.00"));
        stubValidPurchases(purchase("10.00", "5.00"), purchase("10.00", "7.00"));

        service.addStock(7L, new BigDecimal("10.00"), new BigDecimal("7.00"), buyer);

        assertThat(tomato.getCostPerUnit()).isEqualByComparingTo("6.00");
    }

    @Test
    @DisplayName("el promedio se guarda con dos decimales, redondeando")
    void theAverageIsRoundedToTheColumnScale() {
        stubValidPurchases(purchase("1.00", "1.00"), purchase("1.00", "1.01"));

        service.addStock(7L, new BigDecimal("1.00"), new BigDecimal("1.01"), buyer);

        // (1.00 + 1.01) / 2 = 1.005 → 1.01 con HALF_UP.
        assertThat(tomato.getCostPerUnit()).isEqualByComparingTo("1.01");
    }

    @Test
    @DisplayName("sin compras vigentes se conserva el costo anterior")
    void withoutValidPurchasesThePreviousCostIsKept() {
        stubValidPurchases();

        service.addStock(7L, new BigDecimal("10.00"), new BigDecimal("7.00"), buyer);

        assertThat(tomato.getCostPerUnit()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("corregir el costo de una compra recalcula el promedio")
    void correctingAPurchaseRecalculatesTheAverage() {
        IngredientStockHistory record = purchase("10.00", "5.00");
        when(historyRepository.findByIdAndIngredient_Company(99L, company))
                .thenReturn(Optional.of(record));
        stubValidPurchases(record);

        service.updateStockHistory(7L, 99L, new BigDecimal("10.00"), new BigDecimal("6.50"), buyer);

        assertThat(tomato.getCostPerUnit()).isEqualByComparingTo("6.50");
    }

    @Test
    @DisplayName("anular una compra la saca del promedio")
    void voidingAPurchaseRemovesItFromTheAverage() {
        IngredientStockHistory duplicated = purchase("10.00", "3.00");
        IngredientStockHistory real = purchase("10.00", "7.00");
        when(historyRepository.findByIdAndIngredient_Company(99L, company))
                .thenReturn(Optional.of(duplicated));
        // Después de anular, el historial vigente solo devuelve la compra real.
        stubValidPurchases(real);

        service.voidStockHistory(7L, 99L, "compra duplicada", buyer);

        assertThat(tomato.getCostPerUnit()).isEqualByComparingTo("7.00");
    }

    @Test
    @DisplayName("el costo se guarda en el ingrediente, que es lo que lee el costeo")
    void theAverageIsPersistedOnTheIngredient() {
        stubValidPurchases(purchase("4.00", "3.00"));

        service.addStock(7L, new BigDecimal("4.00"), new BigDecimal("3.00"), buyer);

        assertThat(tomato.getCostPerUnit()).isEqualByComparingTo("3.00");
        verify(ingredientRepository).save(tomato);
    }

    // ========== Helpers ==========

    private void stubValidPurchases(IngredientStockHistory... entries) {
        when(historyRepository.findByIngredientAndVoidedAtIsNullOrderByAddedAtDesc(tomato))
                .thenReturn(List.of(entries));
    }

    private IngredientStockHistory purchase(String quantity, String cost) {
        return IngredientStockHistory.builder()
                .id(99L)
                .ingredient(tomato)
                .quantityAdded(new BigDecimal(quantity))
                .costPerUnit(new BigDecimal(cost))
                .previousStock(BigDecimal.ZERO)
                .newStock(new BigDecimal(quantity))
                .addedAt(LocalDateTime.now())
                .addedBy(buyer)
                .build();
    }
}
