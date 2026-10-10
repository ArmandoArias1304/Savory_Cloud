package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * InventoryObject entity representing any countable asset of the restaurant
 * that is not a food ingredient: tables, televisions, pots, remote controls, etc.
 *
 * MULTI-TENANT: every object belongs to a company and names are unique per company.
 */
@Entity
@Table(name = "inventory_objects", uniqueConstraints = {
    @UniqueConstraint(name = "uk_inventory_object_name_company", columnNames = {"name", "company_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idObject"})
@ToString(exclude = {"company", "category", "createdBy"})
public class InventoryObject implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_object")
    private Long idObject;

    // ========== Company Relationship (Multi-Tenant) ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotBlank(message = "El nombre del objeto es requerido")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // ========== Category Relationship ==========
    // La obligatoriedad se valida en el controlador (llega como categoryId),
    // igual que en el inventario de ingredientes.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_category", nullable = false)
    private InventoryObjectCategory category;

    // ========== Counting ==========

    @NotNull(message = "La cantidad es requerida")
    @DecimalMin(value = "0.0", inclusive = true, message = "La cantidad no puede ser negativa")
    @Digits(integer = 7, fraction = 2, message = "La cantidad debe tener máximo 7 dígitos y 2 decimales")
    @Column(name = "quantity", precision = 9, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    @NotNull(message = "La unidad es obligatoria")
    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 20)
    @Builder.Default
    private ObjectUnit unit = ObjectUnit.PIEZA;

    @NotNull(message = "La condición es obligatoria")
    @Enumerated(EnumType.STRING)
    @Column(name = "object_condition", nullable = false, length = 20)
    @Builder.Default
    private ObjectCondition condition = ObjectCondition.BUENO;

    // ========== Location & Responsibility ==========

    @Size(max = 100, message = "La ubicación no puede exceder 100 caracteres")
    @Column(name = "location", length = 100)
    private String location;

    @Size(max = 120, message = "El responsable no puede exceder 120 caracteres")
    @Column(name = "responsible", length = 120)
    private String responsible;

    // ========== Purchase Data ==========

    @DecimalMin(value = "0.0", inclusive = true, message = "El costo no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El costo debe tener máximo 8 dígitos y 2 decimales")
    @Column(name = "purchase_cost", precision = 10, scale = 2)
    private BigDecimal purchaseCost;

    @Size(max = 3, message = "El código de moneda debe tener 3 caracteres")
    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "MXN";

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Size(max = 100, message = "La marca no puede exceder 100 caracteres")
    @Column(name = "brand", length = 100)
    private String brand;

    @Size(max = 100, message = "El modelo no puede exceder 100 caracteres")
    @Column(name = "model", length = 100)
    private String model;

    @Size(max = 100, message = "El número de serie no puede exceder 100 caracteres")
    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Size(max = 500, message = "Las notas no pueden exceder 500 caracteres")
    @Column(name = "notes", length = 500)
    private String notes;

    // ========== Status ==========

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Version
    @Column(name = "version")
    private Long version;

    // ========== Timestamps & Audit ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.quantity == null) {
            this.quantity = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========== Business Logic ==========

    /**
     * Total invested value of this object line (quantity * unit cost).
     * Returns zero when no purchase cost has been captured.
     */
    public BigDecimal getTotalValue() {
        if (quantity == null || purchaseCost == null) {
            return BigDecimal.ZERO;
        }
        return quantity.multiply(purchaseCost).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * An object line is "available" when it is active and still usable.
     */
    public boolean isAvailable() {
        return Boolean.TRUE.equals(active) && condition != null && condition.isUsable();
    }
}
