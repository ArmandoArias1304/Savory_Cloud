package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ItemIngredient entity representing the recipe for menu items
 * This is the join table between ItemMenu and Ingredient
 * Stores the quantity of each ingredient required for a menu item
 */
@Entity
@Table(name = "item_ingredients",
       uniqueConstraints = @UniqueConstraint(columnNames = {"id_item_menu", "id_ingredient"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idItemIngredient"})
@ToString(exclude = {"itemMenu", "ingredient"})
public class ItemIngredient implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_item_ingredient")
    private Long idItemIngredient;

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
     * Many-to-One relationship with ItemMenu
     * This ingredient belongs to a specific menu item
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_item_menu", nullable = false)
    private ItemMenu itemMenu;

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

    /**
     * Lifecycle callback to set createdAt before persist operations
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        validateUnitMatch();
    }

    /**
     * Lifecycle callback to validate before update operations
     */
    @PreUpdate
    protected void onUpdate() {
        validateUnitMatch();
    }

    // ========== Validation Methods ==========

    /**
     * Validate that the unit matches the ingredient's unit of measure
     * This ensures consistency in the recipe
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
     * Check if there's enough stock of this ingredient for the given quantity of items
     * @param itemQuantity Number of menu items to prepare
     * @return true if there's enough stock
     */
    public boolean hasEnoughStock(int itemQuantity) {
        if (ingredient == null || ingredient.getCurrentStock() == null) {
            return false;
        }

        BigDecimal requiredQuantity = quantity.multiply(BigDecimal.valueOf(itemQuantity));
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
     * Deduct the required quantity from ingredient stock
     * @param itemQuantity Number of menu items being prepared
     * @return The new stock level
     */
    public BigDecimal deductFromStock(int itemQuantity) {
        if (ingredient == null) {
            throw new IllegalStateException("No se puede descontar: ingrediente no definido");
        }

        BigDecimal requiredQuantity = quantity.multiply(BigDecimal.valueOf(itemQuantity));
        BigDecimal currentStock = ingredient.getCurrentStock();

        if (currentStock == null || currentStock.compareTo(requiredQuantity) < 0) {
            throw new IllegalStateException(
                String.format("Stock insuficiente de '%s'. Requerido: %s %s, Disponible: %s %s",
                              ingredient.getName(),
                              requiredQuantity, unit,
                              currentStock != null ? currentStock : BigDecimal.ZERO, unit)
            );
        }

        BigDecimal newStock = currentStock.subtract(requiredQuantity);
        ingredient.setCurrentStock(newStock);
        
        return newStock;
    }

    /**
     * Get formatted quantity with unit
     */
    public String getFormattedQuantity() {
        if (quantity == null || unit == null) {
            return "0";
        }
        return String.format("%s %s", quantity.stripTrailingZeros().toPlainString(), unit);
    }

    /**
     * Get ingredient name (convenience method)
     */
    public String getIngredientName() {
        return ingredient != null ? ingredient.getName() : "Sin ingrediente";
    }

    /**
     * Get available stock of the ingredient
     */
    public BigDecimal getAvailableStock() {
        return ingredient != null && ingredient.getCurrentStock() != null 
               ? ingredient.getCurrentStock() 
               : BigDecimal.ZERO;
    }
}
