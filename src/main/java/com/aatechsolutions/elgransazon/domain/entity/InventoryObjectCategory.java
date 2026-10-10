package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * InventoryObjectCategory entity representing categories for inventory objects
 * Examples: Mesas, Electrónica, Menaje de cocina, Limpieza, etc.
 * MULTI-TENANT: Name uniqueness is per company, not global
 */
@Entity
@Table(name = "inventory_object_categories", uniqueConstraints = {
    @UniqueConstraint(name = "uk_inventory_object_category_name_company", columnNames = {"name", "company_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idCategory"})
@ToString(exclude = {"company", "createdBy"})
public class InventoryObjectCategory implements Serializable {

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

    @Size(max = 50, message = "El icono no puede exceder 50 caracteres")
    @Column(name = "icon", length = 50)
    @Builder.Default
    private String icon = "inventory_2";

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Formato de color inválido (use formato hexadecimal #RRGGBB)")
    @Size(max = 7, message = "El color debe tener 7 caracteres")
    @Column(name = "color", length = 7)
    @Builder.Default
    private String color = "#38e07b";

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    /**
     * Lifecycle callback to set timestamps
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.icon == null || this.icon.isBlank()) {
            this.icon = "inventory_2";
        }
        if (this.color == null || this.color.isBlank()) {
            this.color = "#38e07b";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
