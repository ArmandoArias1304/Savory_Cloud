package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.io.Serializable;
import java.time.LocalTime;

/**
 * BusinessHours entity representing opening and closing times for each day
 */
@Entity
@Table(name = "business_hours", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"system_configuration_id", "day_of_week"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"systemConfiguration"})
public class BusinessHours implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull(message = "Day of week is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 20)
    private DayOfWeek dayOfWeek;

    @NotNull(message = "Open time is required")
    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @NotNull(message = "Close time is required")
    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    @Column(name = "is_closed", nullable = false)
    @Builder.Default
    private Boolean isClosed = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_configuration_id", nullable = false)
    private SystemConfiguration systemConfiguration;

    /**
     * Validate that close time is after open time
     */
    @PrePersist
    @PreUpdate
    protected void validateTimes() {
        if (!isClosed && openTime != null && closeTime != null) {
            if (closeTime.isBefore(openTime) || closeTime.equals(openTime)) {
                throw new IllegalArgumentException("La hora de cierre debe ser después de la hora de apertura");
            }
        }
    }

    /**
     * Get display name for the day
     */
    public String getDayDisplayName() {
        return dayOfWeek != null ? dayOfWeek.getDisplayName() : "";
    }

    /**
     * Check if the restaurant is open at a specific time on this day
     */
    public boolean isOpenAt(LocalTime time) {
        if (isClosed) {
            return false;
        }
        return !time.isBefore(openTime) && !time.isAfter(closeTime);
    }
}
