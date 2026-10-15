package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.ComplementIngredient;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.IngredientCategory;
import com.aatechsolutions.elgransazon.domain.entity.ItemIngredient;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La unidad de una fila de receta es una copia de la del ingrediente y se valida contra ella al
 * guardarse. Si el ingrediente cambia de unidad (por ejemplo de UN a G) y las filas se quedan con
 * la vieja, cualquier edición posterior de esa receta falla con "la unidad de medida no coincide".
 *
 * <p>Estas pruebas fijan el contrato de {@code IngredientServiceImpl.update}: al cambiar la unidad se
 * re-etiquetan todas las filas que usan el ingrediente (platillos y complementos) en la misma
 * transacción, de modo que la copia vuelve a coincidir. Las cantidades no se convierten: no existe
 * un factor de conversión, y eso queda deliberadamente fuera del alcance.</p>
 */
class IngredientUnitPropagationTest {

    private static final Long INGREDIENT_ID = 24L;

    private IngredientRepository ingredientRepository;
    private IngredientCategoryRepository categoryRepository;
    private ItemIngredientRepository itemIngredientRepository;
    private ComplementIngredientRepository complementIngredientRepository;
    private IngredientServiceImpl service;

    @BeforeEach
    void setUp() {
        ingredientRepository = mock(IngredientRepository.class);
        categoryRepository = mock(IngredientCategoryRepository.class);
        itemIngredientRepository = mock(ItemIngredientRepository.class);
        complementIngredientRepository = mock(ComplementIngredientRepository.class);

        service = new IngredientServiceImpl(
                ingredientRepository,
                categoryRepository,
                mock(IngredientStockHistoryRepository.class),
                itemIngredientRepository,
                complementIngredientRepository);

        CompanyContext.setCurrentCompany(Company.builder()
                .idCompany(2L)
                .slug("quinta")
                .name("Quinta El Paraíso")
                .build());

        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(i -> i.getArgument(0));
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(
                IngredientCategory.builder().idCategory(7L).name("Verduras").build()));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("al cambiar la unidad se re-etiquetan las recetas de platillos y complementos")
    void changingTheUnitRelabelsEveryRecipeThatUsesTheIngredient() {
        Ingredient stored = storedIngredient();
        when(ingredientRepository.findByIdIngredientAndCompany(eq(INGREDIENT_ID), any(Company.class)))
                .thenReturn(Optional.of(stored));

        ItemIngredient itemRow = ItemIngredient.builder()
                .ingredient(stored)
                .quantity(new BigDecimal("2"))
                .unit("UN")
                .build();
        ComplementIngredient complementRow = ComplementIngredient.builder()
                .ingredient(stored)
                .quantity(new BigDecimal("1"))
                .unit("UN")
                .build();
        when(itemIngredientRepository.findByIngredientId(INGREDIENT_ID)).thenReturn(List.of(itemRow));
        when(complementIngredientRepository.findByIngredientIdIngredient(INGREDIENT_ID))
                .thenReturn(List.of(complementRow));

        service.update(INGREDIENT_ID, detailsWithUnit("G"));

        assertThat(itemRow.getUnit()).isEqualTo("G");
        assertThat(complementRow.getUnit()).isEqualTo("G");
        // La invariante que exige la validación de las filas: la copia coincide con el ingrediente
        assertThat(itemRow.getUnit()).isEqualTo(stored.getUnitOfMeasure());
        assertThat(complementRow.getUnit()).isEqualTo(stored.getUnitOfMeasure());
        verify(itemIngredientRepository).saveAll(any());
        verify(complementIngredientRepository).saveAll(any());
    }

    @Test
    @DisplayName("la cantidad no se convierte: sólo cambia la unidad")
    void theQuantityIsLeftUntouched() {
        when(ingredientRepository.findByIdIngredientAndCompany(eq(INGREDIENT_ID), any(Company.class)))
                .thenReturn(Optional.of(storedIngredient()));

        ItemIngredient itemRow = ItemIngredient.builder()
                .ingredient(storedIngredient())
                .quantity(new BigDecimal("2"))
                .unit("UN")
                .build();
        when(itemIngredientRepository.findByIngredientId(INGREDIENT_ID)).thenReturn(List.of(itemRow));

        service.update(INGREDIENT_ID, detailsWithUnit("G"));

        assertThat(itemRow.getQuantity()).isEqualByComparingTo("2");
        assertThat(itemRow.getUnit()).isEqualTo("G");
    }

    @Test
    @DisplayName("si la unidad no cambia y las recetas ya coinciden, no se guarda ninguna fila")
    void anUnchangedUnitLeavesMatchingRecipesAlone() {
        when(ingredientRepository.findByIdIngredientAndCompany(eq(INGREDIENT_ID), any(Company.class)))
                .thenReturn(Optional.of(storedIngredient()));
        when(itemIngredientRepository.findByIngredientId(INGREDIENT_ID)).thenReturn(List.of(
                ItemIngredient.builder().quantity(new BigDecimal("2")).unit("UN").build()));
        when(complementIngredientRepository.findByIngredientIdIngredient(INGREDIENT_ID)).thenReturn(List.of(
                ComplementIngredient.builder().quantity(new BigDecimal("1")).unit("UN").build()));

        service.update(INGREDIENT_ID, detailsWithUnit("UN"));

        verify(itemIngredientRepository, never()).saveAll(any());
        verify(complementIngredientRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("la comparación de unidades no distingue mayúsculas")
    void anUnitWrittenInAnotherCaseIsNotAChange() {
        when(ingredientRepository.findByIdIngredientAndCompany(eq(INGREDIENT_ID), any(Company.class)))
                .thenReturn(Optional.of(storedIngredient()));
        when(itemIngredientRepository.findByIngredientId(INGREDIENT_ID)).thenReturn(List.of(
                ItemIngredient.builder().quantity(new BigDecimal("2")).unit("UN").build()));
        when(complementIngredientRepository.findByIngredientIdIngredient(INGREDIENT_ID)).thenReturn(List.of(
                ComplementIngredient.builder().quantity(new BigDecimal("1")).unit("UN").build()));

        service.update(INGREDIENT_ID, detailsWithUnit("un"));

        verify(itemIngredientRepository, never()).saveAll(any());
        verify(complementIngredientRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("una receta que quedó con la unidad vieja se repara al volver a guardar el ingrediente")
    void aRecipeLeftWithTheOldUnitIsRepairedOnTheNextSave() {
        when(ingredientRepository.findByIdIngredientAndCompany(eq(INGREDIENT_ID), any(Company.class)))
                .thenReturn(Optional.of(ingredientWithUnit("G")));

        ItemIngredient itemRow = ItemIngredient.builder()
                .quantity(new BigDecimal("2"))
                .unit("UN")
                .build();
        ComplementIngredient complementRow = ComplementIngredient.builder()
                .quantity(new BigDecimal("1"))
                .unit("UN")
                .build();
        when(itemIngredientRepository.findByIngredientId(INGREDIENT_ID)).thenReturn(List.of(itemRow));
        when(complementIngredientRepository.findByIngredientIdIngredient(INGREDIENT_ID))
                .thenReturn(List.of(complementRow));

        // El ingrediente ya estaba en G: el usuario lo guarda sin cambiar nada y las filas se reparan
        service.update(INGREDIENT_ID, detailsWithUnit("G"));

        assertThat(itemRow.getUnit()).isEqualTo("G");
        assertThat(complementRow.getUnit()).isEqualTo("G");
        verify(itemIngredientRepository).saveAll(any());
        verify(complementIngredientRepository).saveAll(any());
    }

    @Test
    @DisplayName("el conteo de recetas suma platillos y complementos")
    void countingRecipeUsagesAddsBothKindsOfRecipe() {
        when(itemIngredientRepository.findByIngredientId(INGREDIENT_ID))
                .thenReturn(List.of(mock(ItemIngredient.class), mock(ItemIngredient.class)));
        when(complementIngredientRepository.findByIngredientIdIngredient(INGREDIENT_ID))
                .thenReturn(List.of(mock(ComplementIngredient.class)));

        assertThat(service.countRecipeUsages(INGREDIENT_ID)).isEqualTo(3);
    }

    private Ingredient storedIngredient() {
        return ingredientWithUnit("UN");
    }

    private Ingredient ingredientWithUnit(String unit) {
        return Ingredient.builder()
                .idIngredient(INGREDIENT_ID)
                .name("TOMATE")
                .unitOfMeasure(unit)
                .currentStock(new BigDecimal("20"))
                .minStock(new BigDecimal("5"))
                .currency("MXN")
                .active(true)
                .build();
    }

    private Ingredient detailsWithUnit(String unit) {
        return Ingredient.builder()
                .idIngredient(INGREDIENT_ID)
                .name("TOMATE")
                .unitOfMeasure(unit)
                .currentStock(new BigDecimal("20"))
                .minStock(new BigDecimal("5"))
                .currency("MXN")
                .active(true)
                .category(IngredientCategory.builder().idCategory(7L).build())
                .build();
    }
}
