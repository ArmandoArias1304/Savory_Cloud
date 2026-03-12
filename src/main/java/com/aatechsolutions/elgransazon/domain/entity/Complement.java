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
 * Complement entity representing additional items that can be added to menu items
 * Examples: "Salsa BBQ", "Mango Habanero", "Extra Queso", etc.
 * Each complement has its own recipe (list of ingredients) for stock management
 */
@Entity
@Table(name = "complements", uniqueConstraints = {
    @UniqueConstraint(name = "uk_complement_name_company", columnNames = {"name", "company_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idComplement"})
@ToString(exclude = {"company", "ingredients", "itemMenuComplements"})
public class Complement implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_complement")
    private Long idComplement;

    // ========== Company Relationship (Multi-Tenant) ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotBlank(message = "El nombre del complemento es requerido")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    @Column(name = "description", length = 500)
    private String description;

    // ========== Pricing ==========

    /**
     * Extra price charged when this complement is added to an order
     * Can be 0 if the complement is included for free
     */
    @NotNull(message = "El precio extra es requerido")
    @DecimalMin(value = "0.0", inclusive = true, message = "El precio extra no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El precio debe tener máximo 8 dígitos y 2 decimales")
    @Column(name = "extra_price", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal extraPrice = BigDecimal.ZERO;

    // ========== Image ==========

    // ========== Status ==========

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Indicates if the complement is currently available for sale
     * Calculated automatically based on ingredient stock availability
     */
    @Column(name = "available", nullable = false)
    @Builder.Default
    private Boolean available = true;

    /**
     * Indicates if this complement is a sauce
     * Sauces have special handling: they are limited per ItemMenu by maxSauces field
     * TRUE: This is a sauce (e.g., BBQ, Mango Habanero, Ranch)
     * FALSE: This is a regular complement (e.g., Extra Cheese, Onion Rings)
     */
    @Column(name = "is_sauce", nullable = false)
    @Builder.Default
    private Boolean isSauce = false;

    // ========== Relationships ==========

    /**
     * One-to-Many relationship with ComplementIngredient (Recipe)
     * List of ingredients required to prepare this complement
     */
    @OneToMany(mappedBy = "complement", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ComplementIngredient> ingredients = new ArrayList<>();

    /**
     * One-to-Many relationship with ItemMenuComplement
     * Menu items that can use this complement
     */
    @OneToMany(mappedBy = "complement", fetch = FetchType.LAZY)
    @Builder.Default
    private List<ItemMenuComplement> itemMenuComplements = new ArrayList<>();

    // ========== Timestamps ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    // ========== Ingredient Management Methods ==========

    /**
     * Add an ingredient to this complement's recipe
     */
    public void addIngredient(ComplementIngredient complementIngredient) {
        if (complementIngredient != null) {
            ingredients.add(complementIngredient);
            complementIngredient.setComplement(this);
        }
    }

    /**
     * Remove an ingredient from this complement's recipe
     */
    public void removeIngredient(ComplementIngredient complementIngredient) {
        if (complementIngredient != null) {
            ingredients.remove(complementIngredient);
            complementIngredient.setComplement(null);
        }
    }

    /**
     * Clear all ingredients from the recipe
     */
    public void clearIngredients() {
        for (ComplementIngredient ci : new ArrayList<>(ingredients)) {
            removeIngredient(ci);
        }
    }

    // ========== Stock Management Methods ==========

    /**
     * Check if there's enough stock of all ingredients for the specified quantity
     * @param quantity Number of complement portions to check
     * @return true if there's enough stock for all ingredients, false if no ingredients defined
     */
    public boolean hasEnoughStock(int quantity) {
        // No ingredients = not available (must have at least one ingredient to be valid)
        if (ingredients == null || ingredients.isEmpty()) {
            return false;
        }

        for (ComplementIngredient ci : ingredients) {
            if (!ci.hasEnoughStock(quantity)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Calculate how many portions of this complement can be made with current stock
     * @return Maximum portions available, or 0 if no ingredients defined
     */
    public int calculateMaxPortions() {
        // No ingredients = 0 portions available
        if (ingredients == null || ingredients.isEmpty()) {
            return 0;
        }

        int minPortions = Integer.MAX_VALUE;
        for (ComplementIngredient ci : ingredients) {
            int portions = ci.calculateMaxPortions();
            if (portions < minPortions) {
                minPortions = portions;
            }
        }
        return minPortions;
    }

    /**
     * Update availability based on ingredient stock
     */
    public void updateAvailability() {
        this.available = this.active && hasEnoughStock(1);
    }

    /**
     * Calculate the cost of this complement (sum of all ingredient costs)
     * @return Total cost of ingredients for one portion
     */
    public BigDecimal calculateCost() {
        if (ingredients == null || ingredients.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return ingredients.stream()
                .map(ComplementIngredient::calculateCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate profit margin for this complement
     * @return extraPrice - cost
     */
    public BigDecimal calculateProfitMargin() {
        return extraPrice.subtract(calculateCost());
    }
}
