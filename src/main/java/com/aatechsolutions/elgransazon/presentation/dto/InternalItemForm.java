package com.aatechsolutions.elgransazon.presentation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Form backing bean for an internal production item (buffet dish, staff meal).
 *
 * <p>Deliberately small: an internal item has a name, a description, a category used only for
 * organization and a recipe. There is no price, no image, no schedule, no complement and no
 * kitchen station, because the item is never sold nor sent to a comanda.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class InternalItemForm {

    private Long idItemMenu;
    private String name;
    private String description;
    private Long categoryId;

    /** Recipe rows, bound as {@code recipe[0].ingredientId}, {@code recipe[0].quantity}, ... */
    private List<RecipeRow> recipe = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RecipeRow {

        private Long ingredientId;
        private BigDecimal quantity;

        public RecipeRow(Long ingredientId, BigDecimal quantity) {
            this.ingredientId = ingredientId;
            this.quantity = quantity;
        }
    }
}
