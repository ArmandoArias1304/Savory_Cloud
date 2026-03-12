package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import com.aatechsolutions.elgransazon.infrastructure.util.CompanyLocalTime;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Reservation entity representing customer reservations for restaurant tables
 */
@Entity
@Table(name = "reservations", indexes = {
        @Index(name = "idx_reservation_date", columnList = "reservation_date"),
        @Index(name = "idx_reservation_status", columnList = "status"),
        @Index(name = "idx_table_date", columnList = "id_table, reservation_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"company", "restaurantTable"})
public class Reservation implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reservation")
    private Long id;

    // ========== Company Relationship (Multi-Tenant) ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotBlank(message = "El nombre del cliente es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @NotBlank(message = "El teléfono del cliente es obligatorio")
    @Pattern(regexp = "^[0-9]{10}$", message = "El teléfono debe tener exactamente 10 dígitos")
    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @Email(message = "El formato del correo electrónico es inválido")
    @Size(max = 100, message = "El correo no puede exceder 100 caracteres")
    @Column(name = "customer_email", length = 100)
    private String customerEmail;

    @NotNull(message = "El número de comensales es obligatorio")
    @Min(value = 1, message = "Debe haber al menos 1 comensal")
    @Max(value = 50, message = "El número de comensales no puede exceder 50")
    @Column(name = "number_of_guests", nullable = false)
    private Integer numberOfGuests;

    @NotNull(message = "La fecha de reservación es obligatoria")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @Column(name = "reservation_date", nullable = false)
    private LocalDate reservationDate;

    @NotNull(message = "La hora de reservación es obligatoria")
    @Column(name = "reservation_time", nullable = false)
    private LocalTime reservationTime;

    @NotNull(message = "El estado es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.PENDING;

    @Size(max = 500, message = "Las peticiones especiales no pueden exceder 500 caracteres")
    @Column(name = "special_requests", length = 500)
    private String specialRequests;

    @NotNull(message = "La mesa es obligatoria")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_table", nullable = false)
    private RestaurantTable restaurantTable;

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
     * Lifecycle callbacks to set timestamps
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Get full reservation date and time
     */
    public LocalDateTime getReservationDateTime() {
        return LocalDateTime.of(reservationDate, reservationTime);
    }

    /**
     * Get formatted reservation date
     */
    public String getFormattedReservationDate() {
        if (reservationDate == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return reservationDate.format(formatter);
    }

    /**
     * Get formatted reservation time
     */
    public String getFormattedReservationTime() {
        if (reservationTime == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        return reservationTime.format(formatter);
    }

    /**
     * Get formatted reservation date and time
     */
    public String getFormattedReservationDateTime() {
        return getFormattedReservationDate() + " " + getFormattedReservationTime();
    }

    /**
     * Get status display name
     */
    public String getStatusDisplayName() {
        return status != null ? status.getDisplayName() : "";
    }

    /**
     * Get table display name
     */
    public String getTableDisplayName() {
        return restaurantTable != null ? restaurantTable.getDisplayName() : "";
    }

    /**
     * Get number of guests display
     */
    public String getGuestsDisplay() {
        if (numberOfGuests == null) {
            return "0";
        }
        return numberOfGuests == 1 ? "1 persona" : numberOfGuests + " personas";
    }

    /**
     * Check if reservation is active
     */
    public boolean isActive() {
        return status != null && status.isActive();
    }

    /**
     * Check if reservation is editable
     */
    public boolean isEditable() {
        return status != null && status.isEditable();
    }

    /**
     * Check if reservation can be cancelled
     */
    public boolean isCancellable() {
        return status != null && status.isCancellable();
    }

    /**
     * Check if reservation is for today
     */
    public boolean isToday() {
        return reservationDate != null && reservationDate.equals(CompanyLocalTime.today());
    }

    /**
     * Check if reservation is upcoming (today or future)
     */
    public boolean isUpcoming() {
        if (reservationDate == null) {
            return false;
        }
        LocalDate today = CompanyLocalTime.today();
        return !reservationDate.isBefore(today);
    }

    /**
     * Check if reservation is past
     */
    public boolean isPast() {
        if (reservationDate == null) {
            return false;
        }
        LocalDate today = CompanyLocalTime.today();
        return reservationDate.isBefore(today);
    }

    /**
     * Get formatted created date
     */
    public String getFormattedCreatedAt() {
        if (createdAt == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return createdAt.format(formatter);
    }

    /**
     * Get formatted updated date
     */
    public String getFormattedUpdatedAt() {
        if (updatedAt == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return updatedAt.format(formatter);
    }
}
