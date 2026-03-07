package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.io.Serializable;
import java.time.LocalTime;

/**
 * ItemMenuAvailability entity representing the days and hours when a menu item is available.
 * Each day can have its own start and end time, automatically adjusted to restaurant business hours.
 * 
 * Example: A "Breakfast Special" might be available:
 * - Monday to Friday: 8:00 AM to 10:00 PM
 * - Saturday: 8:00 AM to 5:00 PM (auto-adjusted because restaurant closes at 5pm)
 */
@Entity
@Table(name = "item_menu_availability", 
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"id_item_menu", "day_of_week"})
    },
    indexes = {
        @Index(name = "idx_item_menu_availability_item", columnList = "id_item_menu"),
        @Index(name = "idx_item_menu_availability_day", columnList = "day_of_week")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"itemMenu"})
public class ItemMenuAvailability implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * The menu item this availability belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_item_menu", nullable = false)
    @NotNull(message = "El item del menú es requerido")
    private ItemMenu itemMenu;

    /**
     * Day of the week when this item is available
     */
    @NotNull(message = "El día de la semana es requerido")
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 20)
    private DayOfWeek dayOfWeek;

    /**
     * Start time for this specific day.
     * This may differ from the general availability time if the restaurant
     * has different hours on this day.
     */
    @Column(name = "start_time")
    private LocalTime startTime;

    /**
     * End time for this specific day.
     * This may differ from the general availability time if the restaurant
     * has different hours on this day (e.g., Saturday closes earlier).
     */
    @Column(name = "end_time")
    private LocalTime endTime;

    /**
     * Indicates if this day's schedule was auto-adjusted due to restaurant hours
     */
    @Column(name = "was_auto_adjusted")
    @Builder.Default
    private Boolean wasAutoAdjusted = false;

    /**
     * Get display name for the day
     */
    public String getDayDisplayName() {
        return dayOfWeek != null ? dayOfWeek.getDisplayName() : "";
    }

    /**
     * Get formatted time range for display
     */
    public String getTimeRangeDisplay() {
        if (startTime == null || endTime == null) {
            return "Todo el día";
        }
        return startTime.toString() + " - " + endTime.toString();
    }
}
