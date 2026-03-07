package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * CustomerAddress entity for storing multiple delivery addresses per customer
 * Includes GPS coordinates for map-based location selection
 * Addresses are specific to a company (customer can have different addresses per restaurant)
 */
@Entity
@Table(name = "customer_addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idAddress"})
public class CustomerAddress implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_address")
    private Long idAddress;

    // ========== Company Relationship (Multi-Tenant) ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_customer", nullable = false)
    private Customer customer;

    @NotBlank(message = "El nombre de la dirección es requerido")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    @Column(name = "label", nullable = false, length = 100)
    private String label; // e.g., "Casa", "Trabajo", "Casa de mamá"

    @NotBlank(message = "La dirección es requerida")
    @Size(max = 500, message = "La dirección no puede exceder 500 caracteres")
    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @Column(name = "reference", length = 300)
    private String reference; // Additional reference like "Portón azul", "Frente al parque"

    @NotNull(message = "La latitud es requerida")
    @DecimalMin(value = "-90.0", message = "Latitud inválida")
    @DecimalMax(value = "90.0", message = "Latitud inválida")
    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @NotNull(message = "La longitud es requerida")
    @DecimalMin(value = "-180.0", message = "Longitud inválida")
    @DecimalMax(value = "180.0", message = "Longitud inválida")
    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

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
        if (this.isDefault == null) {
            this.isDefault = false;
        }
        if (this.active == null) {
            this.active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Get formatted display string for address (without reference)
     */
    public String getDisplayAddress() {
        return address;
    }
    
    /**
     * Get the reference/notes for this address
     */
    public String getReference() {
        return reference;
    }
}
