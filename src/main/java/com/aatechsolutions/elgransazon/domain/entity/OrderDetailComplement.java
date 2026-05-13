package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OrderDetailComplement entity representing complements selected for an order detail item
 * Stores the complements chosen by the customer for each item in their order
 * 
 * Example:
 * OrderDetail: Boneless 250gr (quantity: 2)
 *   - OrderDetailComplement: Salsa BBQ (quantity: 2 - one per boneless)
 *   - OrderDetailComplement: Mango Habanero (quantity: 1 - only for one boneless)
 */
@Entity
@Table(name = "order_detail_complements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"orderDetail", "complement"})
public class OrderDetailComplement implements Serializable {

    /**
     * JPA-safe equals: two new (unsaved) entities with null ID are only equal
     * if they are the same object instance. Persisted entities are equal by ID.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderDetailComplement that = (OrderDetailComplement) o;
        return idOrderDetailComplement != null && idOrderDetailComplement.equals(that.idOrderDetailComplement);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_order_detail_complement")
    private Long idOrderDetailComplement;

    // ========== Relationships ==========

    /**
     * Many-to-One relationship with OrderDetail
     * The order detail item this complement belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_order_detail", nullable = false)
    private OrderDetail orderDetail;

    /**
     * Many-to-One relationship with Complement
     * The complement that was selected
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_complement", nullable = false)
    private Complement complement;

    /**
     * Snapshot of the complement name at the time the order was placed.
     * Null for legacy records created before this field was added.
     * Use {@link #getComplementName()} which applies automatic fallback.
     */
    @Size(max = 100)
    @Column(name = "complement_name", length = 100)
    private String complementName;

    // ========== Quantity and Pricing ==========

    /**
     * Quantity of this complement selected
     * Example: 2 salsas for 2 boneless
     */
    @NotNull(message = "La cantidad es requerida")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /**
     * Unit price of the complement at the time of order
     * Stored to preserve historical price even if complement price changes later
     */
    @NotNull(message = "El precio unitario es requerido")
    @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El precio debe tener máximo 8 dígitos y 2 decimales")
    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    /**
     * Subtotal for this complement (quantity * unitPrice)
     */
    @NotNull(message = "El subtotal es requerido")
    @Column(name = "subtotal", precision = 10, scale = 2, nullable = false)
    private BigDecimal subtotal;

    // ========== Stock Tracking ==========

    /**
     * Flag to track if stock has been deducted for this complement
     * Used to prevent double deduction or missed returns
     */
    @Column(name = "stock_deducted", nullable = false)
    @Builder.Default
    private Boolean stockDeducted = false;

    // ========== Timestamps ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        calculateSubtotal();
    }

    // ========== Business Methods ==========

    /**
     * Calculate subtotal from quantity and unit price
     */
    public void calculateSubtotal() {
        if (this.quantity != null && this.unitPrice != null) {
            this.subtotal = this.unitPrice
                    .multiply(BigDecimal.valueOf(this.quantity))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }

    /**
     * Initialize from a complement
     * Sets unit price from complement's current extra price
     * @param complement The complement being added
     * @param quantity Quantity selected
     */
    public void initializeFromComplement(Complement complement, int quantity) {
        this.complement = complement;
        this.complementName = complement.getName();
        this.quantity = quantity;
        this.unitPrice = complement.getExtraPrice();
        calculateSubtotal();
    }

    /**
     * Deduct stock from complement ingredients
     * Should be called when order is confirmed
     */
    public void deductStock() {
        if (complement == null || stockDeducted) {
            return;
        }

        for (ComplementIngredient ci : complement.getIngredients()) {
            ci.deductFromStock(quantity);
        }
        
        stockDeducted = true;
    }

    /**
     * Return stock to complement ingredients
     * Should be called when order is cancelled or item is removed
     */
    public void returnStock() {
        if (complement == null || !stockDeducted) {
            return;
        }

        for (ComplementIngredient ci : complement.getIngredients()) {
            ci.returnToStock(quantity);
        }
        
        stockDeducted = false;
    }

    /**
     * Check if there's enough stock for this complement selection
     */
    public boolean hasEnoughStock() {
        return complement != null && complement.hasEnoughStock(quantity);
    }

    // ========== Helper Methods ==========

    /**
     * Get complement name safely.
     * Returns the snapshot {@link #complementName} if present (new records),
     * or falls back to the live {@link Complement#getName()} for legacy records.
     */
    public String getComplementName() {
        if (complementName != null && !complementName.isBlank()) {
            return complementName;
        }
        return complement != null ? complement.getName() : "N/A";
    }

    /**
     * Get formatted price display
     */
    public String getFormattedPrice() {
        if (unitPrice == null) return "$0.00";
        return String.format("$%.2f", unitPrice);
    }

    /**
     * Get formatted subtotal display
     */
    public String getFormattedSubtotal() {
        if (subtotal == null) return "$0.00";
        return String.format("$%.2f", subtotal);
    }

    /**
     * Check if this is a free complement (price = 0)
     */
    public boolean isFree() {
        return unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) == 0;
    }
}
