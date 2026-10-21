package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing the history of stock additions for ingredients
 * Tracks when stock is added, quantity, cost, and who added it
 */
@Entity
@Table(name = "ingredient_stock_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngredientStockHistory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 7, fraction = 3)
    @Column(name = "quantity_added", precision = 10, scale = 3, nullable = false)
    private BigDecimal quantityAdded; // Cantidad de stock agregado

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    @Digits(integer = 8, fraction = 2)
    @Column(name = "cost_per_unit", precision = 10, scale = 2, nullable = false)
    private BigDecimal costPerUnit; // Precio de compra por unidad

    @Column(name = "total_cost", precision = 12, scale = 2, nullable = false)
    private BigDecimal totalCost; // quantityAdded * costPerUnit

    @Column(name = "previous_stock", precision = 10, scale = 3)
    private BigDecimal previousStock; // Stock antes de la actualización

    @Column(name = "new_stock", precision = 10, scale = 3)
    private BigDecimal newStock; // Stock después de la actualización

    @Column(name = "added_at", nullable = false)
    @Builder.Default
    private LocalDateTime addedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by")
    private Employee addedBy; // Quien agregó el stock

    // ========== Correcciones ==========

    @Column(name = "edited_at")
    private LocalDateTime editedAt; // Última corrección de cantidad/costo

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edited_by")
    private Employee editedBy;

    /**
     * Una entrada anulada ya no cuenta para el stock ni para los gastos, pero se conserva
     * para saber quién la anuló y por qué. {@code null} = entrada vigente.
     */
    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voided_by")
    private Employee voidedBy;

    @Column(name = "void_reason", length = 255)
    private String voidReason;

    public boolean isVoided() {
        return voidedAt != null;
    }

    @PrePersist
    @PreUpdate
    protected void calculateTotalCost() {
        if (this.quantityAdded != null && this.costPerUnit != null) {
            this.totalCost = this.quantityAdded.multiply(this.costPerUnit)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }
}
