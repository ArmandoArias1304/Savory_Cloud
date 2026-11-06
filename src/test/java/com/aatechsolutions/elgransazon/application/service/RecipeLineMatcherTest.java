package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemIngredient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comparación de recetas para decidir si un tamaño venía copiando la del padre.
 *
 * <p>Importa el orden y los ceros de relleno no: 0.50 kg y 0.5 kg son la misma cantidad. Lo que sí
 * importa es que estén los mismos ingredientes con las mismas cantidades; si el tamaño tiene receta
 * propia, no debe tocarse.</p>
 */
class RecipeLineMatcherTest {

    @Test
    @DisplayName("la misma receta en otro orden coincide")
    void theSameRecipeInAnotherOrderMatches() {
        Map<Long, BigDecimal> parent = RecipeLineMatcher.snapshot(
                List.of(line(1L, "0.50"), line(2L, "2.00")));

        assertThat(RecipeLineMatcher.matches(parent, List.of(line(2L, "2"), line(1L, "0.5")))).isTrue();
    }

    @Test
    @DisplayName("una cantidad distinta no coincide")
    void aDifferentQuantityDoesNotMatch() {
        Map<Long, BigDecimal> parent = RecipeLineMatcher.snapshot(List.of(line(1L, "0.50")));

        assertThat(RecipeLineMatcher.matches(parent, List.of(line(1L, "0.80")))).isFalse();
    }

    @Test
    @DisplayName("un ingrediente extra no coincide")
    void anExtraIngredientDoesNotMatch() {
        Map<Long, BigDecimal> parent = RecipeLineMatcher.snapshot(List.of(line(1L, "0.50")));

        assertThat(RecipeLineMatcher.matches(parent, List.of(line(1L, "0.50"), line(2L, "1.00")))).isFalse();
    }

    @Test
    @DisplayName("una receta vacía no coincide con una que tiene líneas")
    void anEmptyRecipeDoesNotMatchOneWithLines() {
        Map<Long, BigDecimal> parent = RecipeLineMatcher.snapshot(List.of());

        assertThat(RecipeLineMatcher.matches(parent, List.of(line(1L, "0.50")))).isFalse();
        assertThat(RecipeLineMatcher.matches(parent, List.of())).isTrue();
    }

    private ItemIngredient line(Long ingredientId, String quantity) {
        return ItemIngredient.builder()
                .ingredient(Ingredient.builder().idIngredient(ingredientId).name("Ing " + ingredientId).build())
                .quantity(new BigDecimal(quantity))
                .unit("KG")
                .build();
    }
}
