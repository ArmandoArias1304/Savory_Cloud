package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ComplementIngredient entity representing the recipe for complements
 * This is the join table between Complement and Ingredient
 * Stores the quantity of each ingredient required for a complement
 */
@Entity
@Table(name = "complement_ingredients",
       uniqueConstraints = @UniqueConstraint(columnNames = {"id_complement", "id_ingredient"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idComplementIngredient"})
@ToString(exclude = {"complement", "ingredient"})
public class ComplementIngredient implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_complement_ingredient")
    private Long idComplementIngredient;

    // ========== Quantity Configuration ==========

    @NotNull(message = "La cantidad es requerida")
    @DecimalMin(value = "0.001", inclusive = true, message = "La cantidad debe ser mayor a 0")
    @Digits(integer = 7, fraction = 3, message = "La cantidad debe tener máximo 7 dígitos y 3 decimales")
    @Column(name = "quantity", precision = 10, scale = 3, nullable = false)
    private BigDecimal quantity;

    @NotBlank(message = "La unidad de medida es requerida")
    @Size(max = 20, message = "La unidad de medida no puede exceder 20 caracteres")
    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    // ========== Relationships ==========

    /**
     * Many-to-One relationship with Complement
     * This ingredient belongs to a specific complement
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_complement", nullable = false)
    private Complement complement;

    /**
     * Many-to-One relationship with Ingredient
     * References the inventory ingredient
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ingredient", nullable = false)
    private Ingredient ingredient;

    // ========== Timestamps ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        validateUnitMatch();
    }

    @PreUpdate
    protected void onUpdate() {
        validateUnitMatch();
    }

    // ========== Validation Methods ==========

    /**
     * Validate that the unit matches the ingredient's unit of measure
     */
    private void validateUnitMatch() {
        if (ingredient != null && unit != null) {
            String ingredientUnit = ingredient.getUnitOfMeasure();
            if (ingredientUnit != null && !unit.equalsIgnoreCase(ingredientUnit)) {
                throw new IllegalStateException(
                    String.format("La unidad de medida '%s' no coincide con la del ingrediente '%s'", 
                                  unit, ingredientUnit)
                );
            }
        }
    }

    // ========== Business Logic Methods ==========

    /**
     * Check if there's enough stock of this ingredient for the given quantity of complements
     * @param complementQuantity Number of complements to prepare
     * @return true if there's enough stock
     */
    public boolean hasEnoughStock(int complementQuantity) {
        if (ingredient == null || ingredient.getCurrentStock() == null) {
            return false;
        }

        BigDecimal requiredQuantity = quantity.multiply(BigDecimal.valueOf(complementQuantity));
        return ingredient.getCurrentStock().compareTo(requiredQuantity) >= 0;
    }

    /**
     * Calculate the cost of this ingredient for one portion
     * Formula: quantity * ingredient.costPerUnit
     */
    public BigDecimal calculateCost() {
        if (ingredient == null || ingredient.getCostPerUnit() == null || quantity == null) {
            return BigDecimal.ZERO;
        }

        return quantity.multiply(ingredient.getCostPerUnit());
    }

    /**
     * Calculate how many portions can be made with current stock
     * @return Maximum portions available
     */
    public int calculateMaxPortions() {
        if (ingredient == null || ingredient.getCurrentStock() == null || quantity == null) {
            return 0;
        }

        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return Integer.MAX_VALUE;
        }

        return ingredient.getCurrentStock()
                .divide(quantity, 0, java.math.RoundingMode.DOWN)
                .intValue();
    }

    /**
     * Deduct the required quantity from ingredient stock
     * @param complementQuantity Number of complements being prepared
     * @return The new stock level
     */
    public BigDecimal deductFromStock(int complementQuantity) {
        if (ingredient == null) {
            throw new IllegalStateException("No se puede descontar: ingrediente no definido");
        }

        BigDecimal requiredQuantity = quantity.multiply(BigDecimal.valueOf(complementQuantity));
        BigDecimal currentStock = ingredient.getCurrentStock();

        if (currentStock == null || currentStock.compareTo(requiredQuantity) < 0) {
            throw new IllegalStateException(
                String.format("Stock insuficiente de '%s' para complemento. Requerido: %s %s, Disponible: %s %s",
                        ingredient.getName(),
                        requiredQuantity.stripTrailingZeros().toPlainString(),
                        unit,
                        currentStock != null ? currentStock.stripTrailingZeros().toPlainString() : "0",
                        unit)
            );
        }

        BigDecimal newStock = currentStock.subtract(requiredQuantity);
        ingredient.setCurrentStock(newStock);

        return newStock;
    }

    /**
     * Return stock to ingredient (when cancelling orders)
     * @param complementQuantity Number of complements being cancelled
     * @return The new stock level
     */
    public BigDecimal returnToStock(int complementQuantity) {
        if (ingredient == null) {
            throw new IllegalStateException("No se puede devolver stock: ingrediente no definido");
        }

        BigDecimal quantityToReturn = quantity.multiply(BigDecimal.valueOf(complementQuantity));
        BigDecimal currentStock = ingredient.getCurrentStock();

        if (currentStock == null) {
            currentStock = BigDecimal.ZERO;
        }

        BigDecimal newStock = currentStock.add(quantityToReturn);
        
        // Check if we would exceed maxStock
        BigDecimal maxStock = ingredient.getMaxStock();
        if (maxStock != null && newStock.compareTo(maxStock) > 0) {
            // Update maxStock to accommodate the returned stock
            ingredient.setMaxStock(newStock);
        }
        
        ingredient.setCurrentStock(newStock);

        return newStock;
    }

    // ========== Helper Methods ==========

    /**
     * Get the ingredient name safely
     */
    public String getIngredientName() {
        return ingredient != null ? ingredient.getName() : "N/A";
    }

    /**
     * Get formatted quantity with unit
     */
    public String getFormattedQuantity() {
        return String.format("%s %s", 
                quantity != null ? quantity.stripTrailingZeros().toPlainString() : "0",
                unit != null ? unit : "");
    }
}
