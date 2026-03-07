package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * RestaurantTable entity representing tables in the restaurant
 */
@Entity
@Table(name = "restaurant_table", uniqueConstraints = {
    @UniqueConstraint(name = "uk_table_number_company", columnNames = {"table_number", "company_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"company"})
public class RestaurantTable implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_table")
    private Long id;

    // ========== Company Relationship (Multi-Tenant) ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotNull(message = "El número de mesa es obligatorio")
    @Min(value = 1, message = "El número de mesa debe ser mayor a 0")
    @Column(name = "table_number", nullable = false)
    private Integer tableNumber;

    @NotNull(message = "La capacidad es obligatoria")
    @Min(value = 1, message = "La capacidad debe ser al menos 1 persona")
    @Max(value = 50, message = "La capacidad no puede exceder 50 personas")
    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Size(max = 100, message = "La ubicación no puede exceder 100 caracteres")
    @Column(name = "location", length = 100)
    private String location;

    @NotNull(message = "El estado es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TableStatus status = TableStatus.AVAILABLE;

    @Size(max = 500, message = "Los comentarios no pueden exceder 500 caracteres")
    @Column(name = "comments", length = 500)
    private String comments;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Lifecycle callback to set timestamps
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if table is available
     */
    public boolean isAvailable() {
        return this.status == TableStatus.AVAILABLE;
    }

    /**
     * Check if table is occupied (has active unpaid order)
     */
    public boolean isOccupied() {
        return this.status == TableStatus.OCCUPIED;
    }

    /**
     * Check if table is out of service
     */
    public boolean isOutOfService() {
        return this.status == TableStatus.OUT_OF_SERVICE;
    }

    /**
     * Get status display name
     */
    public String getStatusDisplayName() {
        return status != null ? status.getDisplayName() : "Sin estado";
    }

    /**
     * Get formatted capacity
     */
    public String getCapacityDisplay() {
        if (capacity == null) {
            return "No definido";
        }
        return capacity == 1 ? "1 persona" : capacity + " personas";
    }

    /**
     * Get location or default text
     */
    public String getLocationDisplay() {
        return location != null && !location.trim().isEmpty() ? location : "Sin ubicación";
    }

    /**
     * Get formatted created date
     */
    public String getFormattedCreatedAt() {
        if (createdAt == null) {
            return "Desconocido";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return createdAt.format(formatter);
    }

    /**
     * Get formatted updated date
     */
    public String getFormattedUpdatedAt() {
        if (updatedAt == null) {
            return "Nunca";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return updatedAt.format(formatter);
    }

    /**
     * Get table display name (Mesa #X)
     */
    public String getDisplayName() {
        return "Mesa #" + tableNumber;
    }

    /**
     * Check if table can be used for new orders
     * Only AVAILABLE tables can have new orders created
     */
    public boolean canBeUsedForOrders() {
        return this.status == TableStatus.AVAILABLE;
    }

    /**
     * Check if table status can be manually changed
     * OCCUPIED tables cannot have their status changed manually
     */
    public boolean canStatusBeManuallyChanged() {
        return this.status == TableStatus.AVAILABLE || this.status == TableStatus.OUT_OF_SERVICE;
    }
}
