package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * OrderDetail entity representing individual items in an order
 */
@Entity
@Table(name = "order_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"order", "itemMenu", "selectedComplements"})
public class OrderDetail implements Serializable {

    /**
     * JPA-safe equals: two new (unsaved) entities with null ID are only equal
     * if they are the same object instance. Persisted entities are equal by ID.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderDetail that = (OrderDetail) o;
        return idOrderDetail != null && idOrderDetail.equals(that.idOrderDetail);
    }

    @Override
    public int hashCode() {
        // Use a constant hashCode so it stays consistent before and after ID assignment
        return getClass().hashCode();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_order_detail")
    private Long idOrderDetail;

    // ========== Relationships ==========

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_order", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_item_menu", nullable = false)
    private ItemMenu itemMenu;

    // ========== Quantity and Pricing ==========

    @NotNull(message = "La cantidad es requerida")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @NotNull(message = "El precio unitario es requerido")
    @DecimalMin(value = "0.0", inclusive = true, message = "El precio debe ser mayor o igual a 0")
    @Digits(integer = 8, fraction = 2, message = "El precio debe tener máximo 8 dígitos y 2 decimales")
    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @NotNull(message = "El subtotal es requerido")
    @Column(name = "subtotal", precision = 10, scale = 2, nullable = false)
    private BigDecimal subtotal;

    // ========== Promotion Fields ==========

    /**
     * Price of the item with promotion applied (per unit)
     * If no promotion: null or equals unitPrice
     * If promotion applied: discounted price per unit
     */
    @Digits(integer = 8, fraction = 2, message = "El precio promocional debe tener máximo 8 dígitos y 2 decimales")
    @Column(name = "promotion_applied_price", precision = 10, scale = 2)
    private BigDecimal promotionAppliedPrice;

    /**
     * ID of the promotion that was applied to this item
     * Null if no promotion was applied
     */
    @Column(name = "applied_promotion_id")
    private Long appliedPromotionId;

    // ========== Special Instructions ==========

    @Size(max = 500, message = "Los comentarios no pueden exceder 500 caracteres")
    @Column(name = "comments", length = 500)
    private String comments;

    // ========== Complements ==========

    /**
     * One-to-Many relationship with OrderDetailComplement
     * Stores the complements selected for this order item
     */
    @OneToMany(mappedBy = "orderDetail", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderDetailComplement> selectedComplements = new ArrayList<>();

    // ========== Item Status (individual per item) ==========

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", length = 20, nullable = false)
    @Builder.Default
    private OrderStatus itemStatus = OrderStatus.PENDING;

    @Column(name = "is_new_item", nullable = false)
    @Builder.Default
    private Boolean isNewItem = false;

    @Column(name = "added_at")
    private LocalDateTime addedAt;

    @Column(name = "prepared_by")
    private String preparedBy;

    // ========== Combo Grouping ==========

    /**
     * Groups order details that belong to the same combo.
     * Format: "COMBO-{comboItemMenuId}-{timestamp}" or similar unique ID.
     * NULL for non-combo items.
     * Used to visually group combo children in views and to enforce
     * combo-level operations (e.g., cancel all children together).
     */
    @Column(name = "combo_group_id", length = 50)
    private String comboGroupId;

    // ========== Timestamps ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.itemStatus == null) {
            this.itemStatus = OrderStatus.PENDING;
        }
        if (this.isNewItem == null) {
            this.isNewItem = false;
        }
        if (this.addedAt == null) {
            this.addedAt = LocalDateTime.now();
        }
    }

    // ========== Business Methods ==========

    /**
     * Calculate subtotal from quantity and unit price
     * If promotion is applied, uses promotionAppliedPrice instead of unitPrice
     * 
     * IMPORTANT: For BUY_X_PAY_Y promotions, the subtotal should be set directly
     * by the controller to avoid precision errors from divide/multiply operations.
     * This method will skip recalculation if subtotal is already set.
     */
    public void calculateSubtotal() {
        // If subtotal is already set (e.g., for BUY_X_PAY_Y promotions), don't recalculate
        if (this.subtotal != null && this.subtotal.compareTo(BigDecimal.ZERO) > 0) {
            // Ensure it's rounded to 2 decimals
            this.subtotal = this.subtotal.setScale(2, java.math.RoundingMode.HALF_UP);
            return;
        }
        
        if (this.quantity != null) {
            // Use promotional price if available, otherwise use regular price
            BigDecimal priceToUse = (this.promotionAppliedPrice != null) 
                ? this.promotionAppliedPrice 
                : this.unitPrice;
            
            if (priceToUse != null) {
                // Multiply and round to 2 decimals to ensure precision
                this.subtotal = priceToUse
                    .multiply(BigDecimal.valueOf(this.quantity))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            }
        }
    }

    /**
     * Calculate savings from promotion
     * @return Amount saved, or ZERO if no promotion
     */
    public BigDecimal getSavings() {
        if (promotionAppliedPrice == null || unitPrice == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        
        // Savings per unit = unitPrice - promotionAppliedPrice
        BigDecimal savingsPerUnit = unitPrice.subtract(promotionAppliedPrice);
        
        // Total savings = savingsPerUnit * quantity
        return savingsPerUnit.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * Check if this item has a promotion applied
     */
    public boolean hasPromotionApplied() {
        return promotionAppliedPrice != null && appliedPromotionId != null;
    }

    /**
     * Get formatted unit price
     */
    public String getFormattedUnitPrice() {
        if (unitPrice == null) {
            return "$0.00";
        }
        return String.format("$%.2f", unitPrice);
    }

    /**
     * Get formatted subtotal
     */
    public String getFormattedSubtotal() {
        if (subtotal == null) {
            return "$0.00";
        }
        return String.format("$%.2f", subtotal);
    }

    /**
     * Check if this item is newly added
     */
    public boolean isNew() {
        return Boolean.TRUE.equals(isNewItem);
    }

    /**
     * Mark this item as new (added after initial order)
     */
    public void markAsNew() {
        this.isNewItem = true;
        this.addedAt = LocalDateTime.now();
    }

    /**
     * Check if item is pending preparation
     */
    public boolean isPending() {
        return itemStatus == OrderStatus.PENDING;
    }

    /**
     * Check if item is in preparation
     */
    public boolean isInPreparation() {
        return itemStatus == OrderStatus.IN_PREPARATION;
    }

    /**
     * Check if item is ready
     */
    public boolean isReady() {
        return itemStatus == OrderStatus.READY;
    }

    /**
     * Check if item is delivered
     */
    public boolean isDelivered() {
        return itemStatus == OrderStatus.DELIVERED;
    }

    /**
     * Check if this order detail is part of a combo group (either parent or child)
     */
    public boolean isComboMember() {
        return comboGroupId != null && !comboGroupId.isEmpty();
    }

    /**
     * Check if this order detail is a combo child (part of combo, price = $0)
     */
    public boolean isComboChild() {
        return isComboMember() && unitPrice != null && unitPrice.compareTo(java.math.BigDecimal.ZERO) == 0;
    }

    /**
     * Check if this order detail is a combo parent (part of combo, price > $0)
     */
    public boolean isComboParent() {
        return isComboMember() && unitPrice != null && unitPrice.compareTo(java.math.BigDecimal.ZERO) > 0;
    }

    /**
     * Get comments without the legacy [Combo: ...] prefix for display purposes.
     * Old orders may have "[Combo: Name] user comment" or just "[Combo: Name]" stored.
     */
    public String getDisplayComments() {
        if (comments == null) return null;
        String cleaned = comments.replaceAll("^\\[Combo: [^\\]]*\\]\\s*", "").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    // ========== Complement Methods ==========

    /**
     * Add a complement to this order detail
     * @param orderDetailComplement The complement selection to add
     */
    public void addComplement(OrderDetailComplement orderDetailComplement) {
        if (orderDetailComplement != null) {
            selectedComplements.add(orderDetailComplement);
            orderDetailComplement.setOrderDetail(this);
        }
    }

    /**
     * Remove a complement from this order detail
     * @param orderDetailComplement The complement selection to remove
     */
    public void removeComplement(OrderDetailComplement orderDetailComplement) {
        if (orderDetailComplement != null) {
            selectedComplements.remove(orderDetailComplement);
            orderDetailComplement.setOrderDetail(null);
        }
    }

    /**
     * Clear all complements from this order detail
     */
    public void clearComplements() {
        for (OrderDetailComplement odc : new ArrayList<>(selectedComplements)) {
            removeComplement(odc);
        }
    }

    /**
     * Check if this order detail has any complements
     */
    public boolean hasComplements() {
        return selectedComplements != null && !selectedComplements.isEmpty();
    }

    /**
     * Calculate total price of all selected complements
     * For sauce complements (isSauce=true), subtotal is multiplied by item quantity
     * since sauces are per-serving (one sauce per each unit of the item)
     * @return Sum of all complement subtotals (with sauce multiplication)
     */
    public BigDecimal getComplementsTotal() {
        if (selectedComplements == null || selectedComplements.isEmpty()) {
            return BigDecimal.ZERO;
        }

        int itemQty = this.quantity != null ? this.quantity : 1;

        return selectedComplements.stream()
                .map(odc -> {
                    BigDecimal compSubtotal = odc.getSubtotal() != null ? odc.getSubtotal() : BigDecimal.ZERO;
                    // For sauces, multiply by item quantity (sauces are per-serving)
                    if (odc.getComplement() != null && Boolean.TRUE.equals(odc.getComplement().getIsSauce())) {
                        compSubtotal = compSubtotal.multiply(BigDecimal.valueOf(itemQty));
                    }
                    return compSubtotal;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate total including item price and complements
     * @return subtotal + complementsTotal
     */
    public BigDecimal getTotalWithComplements() {
        BigDecimal itemSubtotal = this.subtotal != null ? this.subtotal : BigDecimal.ZERO;
        return itemSubtotal.add(getComplementsTotal());
    }
}
