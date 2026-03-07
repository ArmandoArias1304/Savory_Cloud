package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Supplier entity representing ingredient suppliers
 * Manages supplier information and their ingredient categories
 */
@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idSupplier"})
@ToString(exclude = {"company", "categories", "createdBy"})
public class Supplier implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_supplier")
    private Long idSupplier;

    // ========== Company Relationship (Multi-Tenant) ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotBlank(message = "El nombre del proveedor es requerido")
    @Size(min = 2, max = 150, message = "El nombre debe tener entre 2 y 150 caracteres")
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Size(max = 100, message = "El nombre del contacto no puede exceder 100 caracteres")
    @Column(name = "contact_person", length = 100)
    private String contactPerson;

    @Pattern(regexp = "^[0-9]{10}$",
            message = "El teléfono debe tener exactamente 10 dígitos")
    @Column(name = "phone", length = 20)
    private String phone;

    @Email(message = "Formato de email inválido")
    @Size(max = 150, message = "El email no puede exceder 150 caracteres")
    @Column(name = "email", length = 150)
    private String email;

    @Size(max = 300, message = "La dirección no puede exceder 300 caracteres")
    @Column(name = "address", length = 300)
    private String address;

    @Size(max = 500, message = "Las notas no pueden exceder 500 caracteres")
    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Min(value = 1, message = "La valoración debe ser entre 1 y 5")
    @Max(value = 5, message = "La valoración debe ser entre 1 y 5")
    @Column(name = "rating")
    private Integer rating;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Many-to-Many relationship with IngredientCategory
     * A supplier can supply multiple ingredient categories
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "supplier_ingredient_categories",
            joinColumns = @JoinColumn(name = "id_supplier"),
            inverseJoinColumns = @JoinColumn(name = "id_category")
    )
    @Builder.Default
    private Set<IngredientCategory> categories = new HashSet<>();

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
     * Add a category to this supplier
     */
    public void addCategory(IngredientCategory category) {
        this.categories.add(category);
        category.getSuppliers().add(this);
    }

    /**
     * Remove a category from this supplier
     */
    public void removeCategory(IngredientCategory category) {
        this.categories.remove(category);
        category.getSuppliers().remove(this);
    }

    /**
     * Get rating as stars string
     */
    public String getRatingStars() {
        if (rating == null) return "";
        return "⭐".repeat(rating);
    }
}
