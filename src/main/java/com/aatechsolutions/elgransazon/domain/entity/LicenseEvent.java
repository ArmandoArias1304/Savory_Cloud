package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * License Event entity
 * Tracks all events related to the system license
 */
@Entity
@Table(name = "license_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class LicenseEvent implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "license_id")
    private Long licenseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50, columnDefinition = "VARCHAR(50)")
    private EventType eventType;

    @Column(name = "event_date", nullable = false)
    @Builder.Default
    private LocalDateTime eventDate = LocalDateTime.now();

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "performed_by")
    private String performedBy;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "months")
    private Integer months;

    @PrePersist
    protected void onCreate() {
        if (eventDate == null) {
            eventDate = LocalDateTime.now();
        }
    }

    /**
     * Event type enum
     */
    public enum EventType {
        CREATED("Creada"),
        RENEWED("Renovada"),
        EXPIRED("Expirada"),
        SUSPENDED("Suspendida"),
        REACTIVATED("Reactivada"),
        UPDATED("Actualizada"),
        CHECKED("Verificada"),
        NOTIFICATION_SENT("Notificación Enviada");

        private final String displayName;

        EventType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Get event type display name
     */
    public String getEventTypeDisplayName() {
        return eventType.getDisplayName();
    }
}
