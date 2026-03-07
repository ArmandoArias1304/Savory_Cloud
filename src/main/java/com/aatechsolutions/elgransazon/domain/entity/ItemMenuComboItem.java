package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ItemMenuComboItem entity representing the relationship between a Combo ItemMenu and its child ItemMenu items.
 * This is the join table that defines which menu items are part of a combo and their quantities.
 * 
 * Example:
 * - "Combo Familiar" (combo) → Hamburguesa Clásica (quantity: 2)
 * - "Combo Familiar" (combo) → Papas Fritas (quantity: 2)
 * - "Combo Familiar" (combo) → Coca-Cola (quantity: 2)
 */
@Entity
@Table(name = "item_menu_combo_items",
       uniqueConstraints = @UniqueConstraint(columnNames = {"id_combo_menu", "id_child_menu"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idComboItem"})
@ToString(exclude = {"comboMenu", "childMenu"})
public class ItemMenuComboItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_combo_item")
    private Long idComboItem;

    // ========== Relationships ==========

    /**
     * The combo ItemMenu that contains this child item.
     * This is the "parent" combo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_combo_menu", nullable = false)
    private ItemMenu comboMenu;

    /**
     * The child ItemMenu that is part of the combo.
     * This is the individual item included in the combo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_child_menu", nullable = false)
    private ItemMenu childMenu;

    // ========== Configuration ==========

    /**
     * How many of this child item are included in one combo.
     * Example: Combo includes 2 hamburgers → quantity = 2
     */
    @NotNull(message = "La cantidad es requerida")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    /**
     * Display order for showing combo items in UI.
     * Lower numbers appear first.
     */
    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    // ========== Timestamps ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    // ========== Convenience Methods ==========

    /**
     * Get the name of the child menu item
     */
    public String getChildMenuName() {
        return childMenu != null ? childMenu.getName() : "Sin item";
    }

    /**
     * Get the price of the child menu item (informational only - combo uses its own price)
     */
    public java.math.BigDecimal getChildMenuPrice() {
        return childMenu != null ? childMenu.getPrice() : java.math.BigDecimal.ZERO;
    }

    /**
     * Check if the child item requires chef preparation
     */
    public boolean childRequiresChefPreparation() {
        return childMenu != null && Boolean.TRUE.equals(childMenu.getRequiresPreparation());
    }

    /**
     * Check if the child item requires barista preparation
     */
    public boolean childRequiresBaristaPreparation() {
        return childMenu != null && Boolean.TRUE.equals(childMenu.getRequiresBaristaPreparation());
    }

    /**
     * Check if the child item has enough stock for the combo quantity
     */
    public boolean childHasEnoughStock(int comboQuantity) {
        if (childMenu == null) return false;
        return childMenu.hasEnoughStock(quantity * comboQuantity);
    }
}
