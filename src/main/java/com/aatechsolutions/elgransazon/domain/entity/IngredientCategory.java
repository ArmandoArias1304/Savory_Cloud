package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * IngredientCategory entity representing categories for ingredients
 * Examples: Vegetables, Meats, Dairy, Spices, etc.
 * MULTI-TENANT: Name uniqueness is per company, not global
 */
@Entity
@Table(name = "ingredient_categories", uniqueConstraints = {
    @UniqueConstraint(name = "uk_ingredient_category_name_company", columnNames = {"name", "company_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idCategory"})
@ToString(exclude = {"company", "suppliers", "ingredients", "createdBy"})
public class IngredientCategory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_category")
    private Long idCategory;

    // ========== Company Relationship (Multi-Tenant) ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotBlank(message = "El nombre de la categoría es requerido")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Size(max = 50, message = "El icono no puede exceder 50 caracteres")
    @Column(name = "icon", length = 50)
    private String icon;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Formato de color inválido (use formato hexadecimal #RRGGBB)")
    @Size(max = 7, message = "El color debe tener 7 caracteres")
    @Column(name = "color", length = 7)
    private String color;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Many-to-Many relationship with Supplier
     * A category can be supplied by multiple suppliers
     */
    @ManyToMany(mappedBy = "categories", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Supplier> suppliers = new HashSet<>();

    /**
     * One-to-Many relationship with Ingredient
     * A category can have multiple ingredients
     */
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = false, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Ingredient> ingredients = new ArrayList<>();

    /**
     * Lifecycle callback to set updatedAt before update operations
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Lifecycle callback to set createdAt before persist operations
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    /**
     * Add a supplier to this category
     */
    public void addSupplier(Supplier supplier) {
        this.suppliers.add(supplier);
        supplier.getCategories().add(this);
    }

    /**
     * Remove a supplier from this category
     */
    public void removeSupplier(Supplier supplier) {
        this.suppliers.remove(supplier);
        supplier.getCategories().remove(this);
    }

    /**
     * Add an ingredient to this category
     */
    public void addIngredient(Ingredient ingredient) {
        this.ingredients.add(ingredient);
        ingredient.setCategory(this);
    }

    /**
     * Remove an ingredient from this category
     */
    public void removeIngredient(Ingredient ingredient) {
        this.ingredients.remove(ingredient);
        ingredient.setCategory(null);
    }
}
