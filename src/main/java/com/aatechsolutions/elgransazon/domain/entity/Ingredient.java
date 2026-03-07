package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Ingredient entity representing ingredients used in the restaurant
 * Manages inventory, stock levels, and supplier relationships through categories
 */
@Entity
@Table(name = "ingredients", uniqueConstraints = {
    @UniqueConstraint(name = "uk_ingredient_name_company", columnNames = {"name", "company_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idIngredient"})
@ToString(exclude = {"company", "category"})
public class Ingredient implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ingredient")
    private Long idIngredient;

    // ========== Company Relationship (Multi-Tenant) ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotBlank(message = "El nombre del ingrediente es requerido")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // ========== Stock & Inventory ==========

    @NotNull(message = "El stock actual es requerido")
    @DecimalMin(value = "0.0", inclusive = true, message = "El stock actual no puede ser negativo")
    @Digits(integer = 7, fraction = 3, message = "El stock debe tener máximo 7 dígitos y 3 decimales")
    @Column(name = "current_stock", precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal currentStock = BigDecimal.ZERO;

    @NotNull(message = "El stock mínimo es requerido")
    @DecimalMin(value = "0.0", inclusive = true, message = "El stock mínimo no puede ser negativo")
    @Digits(integer = 7, fraction = 3, message = "El stock mínimo debe tener máximo 7 dígitos y 3 decimales")
    @Column(name = "min_stock", precision = 10, scale = 3)
    private BigDecimal minStock;

    @DecimalMin(value = "0.0", inclusive = true, message = "El stock máximo no puede ser negativo")
    @Digits(integer = 7, fraction = 3, message = "El stock máximo debe tener máximo 7 dígitos y 3 decimales")
    @Column(name = "max_stock", precision = 10, scale = 3)
    private BigDecimal maxStock;

    @Size(max = 20, message = "La unidad de medida no puede exceder 20 caracteres")
    @Column(name = "unit_of_measure", length = 20)
    private String unitOfMeasure;

    // ========== Pricing ==========

    @DecimalMin(value = "0.0", inclusive = true, message = "El costo por unidad no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El costo debe tener máximo 8 dígitos y 2 decimales")
    @Column(name = "cost_per_unit", precision = 10, scale = 2)
    private BigDecimal costPerUnit;

    @Size(max = 3, message = "El código de moneda debe tener 3 caracteres")
    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "USD";

    // ========== Storage ==========

    @Size(max = 100, message = "La ubicación de almacenamiento no puede exceder 100 caracteres")
    @Column(name = "storage_location", length = 100)
    private String storageLocation;

    @Min(value = 0, message = "La vida útil no puede ser negativa")
    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    // ========== Status ==========

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Version
    @Column(name = "version")
    private Long version;

    // ========== Category Relationship ==========

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_category", nullable = false)
    private IngredientCategory category;

    // ========== Timestamps ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Lifecycle callback to set updatedAt before update operations
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        validateStockLevels();
    }

    /**
     * Lifecycle callback to set createdAt before persist operations
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        validateStockLevels();
    }

    /**
     * Validate stock levels logic
     */
    private void validateStockLevels() {
        // Validate currentStock <= maxStock (if maxStock is set)
        if (maxStock != null && currentStock != null && currentStock.compareTo(maxStock) > 0) {
            throw new IllegalStateException("El stock actual no puede ser mayor al stock máximo");
        }
        
        // Validate minStock <= maxStock (if both are set)
        if (minStock != null && maxStock != null && minStock.compareTo(maxStock) > 0) {
            throw new IllegalStateException("El stock mínimo no puede ser mayor al stock máximo");
        }
    }

    // ========== Stock Level Business Logic ==========

    /**
     * Check if ingredient is completely out of stock (currentStock = 0)
     */
    public boolean isOutOfStock() {
        return currentStock == null || currentStock.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Check if ingredient has low stock (currentStock <= minStock)
     * Yellow zone: currentStock <= minStock AND currentStock > 0
     */
    public boolean isLowStock() {
        if (currentStock == null || minStock == null) {
            return false;
        }
        
        // Low stock when at or below minimum (but not zero)
        return currentStock.compareTo(minStock) <= 0 && currentStock.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if stock is healthy (currentStock > minStock)
     */
    public boolean isHealthyStock() {
        if (currentStock == null || minStock == null) {
            return false;
        }
        return currentStock.compareTo(minStock) > 0;
    }

    /**
     * Get stock status as a readable string
     * - AGOTADO: currentStock = 0
     * - STOCK BAJO: currentStock <= minStock (but > 0)
     * - OK: currentStock > minStock
     */
    public String getStockStatus() {
        if (isOutOfStock()) {
            return "AGOTADO";
        }
        if (isLowStock()) {
            return "STOCK BAJO";
        }
        return "OK";
    }

    /**
     * Get stock percentage (0-100) relative to 0-max range
     * Bar should go from 0 (empty) to maxStock (full)
     */
    public int getStockPercentage() {
        if (maxStock == null || currentStock == null) {
            return 0;
        }
        
        // If maxStock is 0, return 0
        if (maxStock.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        
        // Calculate percentage: (currentStock / maxStock) * 100
        BigDecimal percentage = currentStock.multiply(BigDecimal.valueOf(100))
                .divide(maxStock, 0, RoundingMode.HALF_UP);
        
        return Math.min(100, Math.max(0, percentage.intValue()));
    }

    /**
     * Get stock color for UI (green, yellow)
     * - Yellow: currentStock <= minStock (includes out of stock with red handled in UI)
     * - Green: currentStock > minStock
     */
    public String getStockColor() {
        if (isOutOfStock()) {
            return "red";  // For out of stock
        }
        if (isLowStock()) {
            return "yellow";  // For low stock (currentStock <= minStock)
        }
        return "green";  // For healthy stock (currentStock > minStock)
    }
}
