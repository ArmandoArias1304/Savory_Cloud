package com.aatechsolutions.elgransazon.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for Complement entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplementDTO {
    
    private Long id;
    private String name;
    private String description;
    private BigDecimal extraPrice;
    private String imageUrl;
    private Boolean active;
    private Boolean available;
    private Integer maxPortions;
    private BigDecimal estimatedCost;
    
    // Nested list of ingredients
    private List<ComplementIngredientDTO> ingredients;
    
    /**
     * DTO for ComplementIngredient - recipe component
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplementIngredientDTO {
        private Long id;
        private Long ingredientId;
        private String ingredientName;
        private BigDecimal quantity;
        private String unit;
        private BigDecimal currentStock;
        private Boolean hasEnoughStock;
    }
}
