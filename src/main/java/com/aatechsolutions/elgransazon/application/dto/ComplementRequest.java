package com.aatechsolutions.elgransazon.application.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for creating/updating Complements
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplementRequest {
    
    @NotBlank(message = "El nombre del complemento es requerido")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    private String name;
    
    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    private String description;
    
    @NotNull(message = "El precio extra es requerido")
    @DecimalMin(value = "0.0", message = "El precio extra no puede ser negativo")
    private BigDecimal extraPrice;
    
    private Boolean active;
    
    /**
     * Request for adding ingredient to complement recipe
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddIngredientRequest {
        @NotNull(message = "El ID del ingrediente es requerido")
        private Long ingredientId;
        
        @NotNull(message = "La cantidad es requerida")
        @DecimalMin(value = "0.0001", message = "La cantidad debe ser mayor a 0")
        private BigDecimal quantity;
        
        @Size(max = 50, message = "La unidad no puede exceder 50 caracteres")
        private String unit;
    }
    
    /**
     * Request for associating complement with item menu
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssociateToItemRequest {
        @NotNull(message = "El ID del item de menú es requerido")
        private Long itemMenuId;
        
        @NotNull(message = "El ID del complemento es requerido")
        private Long complementId;
        
        @NotNull(message = "La cantidad máxima es requerida")
        @Min(value = 1, message = "La cantidad máxima debe ser al menos 1")
        private Integer maxQuantity;
        
        @Min(value = 0, message = "El orden de display no puede ser negativo")
        private Integer displayOrder;
    }
    
    /**
     * Request for updating complement configuration on item menu
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateConfigRequest {
        @Min(value = 1, message = "La cantidad máxima debe ser al menos 1")
        private Integer maxQuantity;
        
        @Min(value = 0, message = "El orden de display no puede ser negativo")
        private Integer displayOrder;
        
        private Boolean active;
    }
    
    /**
     * Request for selecting complements when creating order
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SelectComplementRequest {
        @NotNull(message = "El ID del complemento es requerido")
        private Long complementId;
        
        @NotNull(message = "La cantidad es requerida")
        @Min(value = 1, message = "La cantidad debe ser al menos 1")
        private Integer quantity;
        
        @Size(max = 255, message = "Las notas no pueden exceder 255 caracteres")
        private String notes;
    }
}
