package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ItemMenuComplement entity representing the relationship between ItemMenu and Complement
 * Defines which complements are available for which menu items
 * 
 * Examples:
 * - Boneless 250gr -> Salsa BBQ (default: true, required: false, max: 2)
 * - Boneless 250gr -> Mango Habanero (default: false, required: false, max: 2)
 * - Hamburguesa -> Extra Queso (default: false, required: false, max: 1)
 */
@Entity
@Table(name = "item_menu_complements",
       uniqueConstraints = @UniqueConstraint(columnNames = {"id_item_menu", "id_complement"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idItemMenuComplement"})
@ToString(exclude = {"itemMenu", "complement"})
public class ItemMenuComplement implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_item_menu_complement")
    private Long idItemMenuComplement;

    // ========== Relationships ==========

    /**
     * Many-to-One relationship with ItemMenu
     * The menu item that can use this complement
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_item_menu", nullable = false)
    private ItemMenu itemMenu;

    /**
     * Many-to-One relationship with Complement
     * The complement available for the menu item
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_complement", nullable = false)
    private Complement complement;

    // ========== Configuration ==========

    /**
     * Maximum quantity of this complement that can be added to a single item
     * Example: max 3 salsas per order of boneless
     */
    @NotNull(message = "La cantidad máxima es requerida")
    @Min(value = 1, message = "La cantidad máxima debe ser al menos 1")
    @Column(name = "max_quantity", nullable = false)
    @Builder.Default
    private Integer maxQuantity = 1;

    /**
     * Display order for showing complements in UI
     * Lower numbers appear first
     */
    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    /**
     * Status - allows disabling specific complement associations
     * without deleting the relationship
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    // ========== Timestamps ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        validateMaxQuantity();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        validateMaxQuantity();
    }

    // ========== Validation Methods ==========

    /**
     * Validate that max quantity is at least 1
     */
    private void validateMaxQuantity() {
        if (maxQuantity == null || maxQuantity < 1) {
            throw new IllegalStateException("La cantidad máxima debe ser al menos 1");
        }
    }

    // ========== Helper Methods ==========

    /**
     * Check if a quantity is within the allowed range
     * @param quantity Quantity to validate
     * @return true if quantity is valid (between 1 and maxQuantity)
     */
    public boolean isQuantityValid(int quantity) {
        return quantity >= 1 && quantity <= maxQuantity;
    }

    /**
     * Check if this complement configuration is currently available
     * Considers: active status, complement availability, and stock
     */
    public boolean isAvailable() {
        return active 
                && complement != null 
                && complement.getActive() 
                && complement.getAvailable();
    }

    /**
     * Get complement name safely
     */
    public String getComplementName() {
        return complement != null ? complement.getName() : "N/A";
    }

    /**
     * Get item menu name safely
     */
    public String getItemMenuName() {
        return itemMenu != null ? itemMenu.getName() : "N/A";
    }

    /**
     * Check if this complement has enough stock for the given quantity
     * @param quantity Number of complement portions needed
     * @return true if there's enough stock
     */
    public boolean hasEnoughStock(int quantity) {
        return complement != null && complement.hasEnoughStock(quantity);
    }
}
