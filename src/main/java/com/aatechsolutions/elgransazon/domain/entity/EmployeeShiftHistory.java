package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * EmployeeShiftHistory entity representing the history of shift assignments
 */
@Entity
@Table(name = "employee_shift_history", indexes = {
        @Index(name = "idx_employee_action_date", columnList = "employee_id, action_date"),
        @Index(name = "idx_shift_action_date", columnList = "shift_id, action_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"employee", "shift", "actionBy"})
public class EmployeeShiftHistory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull(message = "Employee is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @NotNull(message = "Shift is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @NotNull(message = "Action is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private ShiftAction action;

    @NotNull(message = "Action date is required")
    @Column(name = "action_date", nullable = false)
    @Builder.Default
    private LocalDateTime actionDate = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_by_id")
    private Employee actionBy;

    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Lifecycle callback to set timestamps
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.actionDate == null) {
            this.actionDate = LocalDateTime.now();
        }
    }

    /**
     * Get formatted action date
     */
    public String getFormattedActionDate() {
        if (actionDate == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return actionDate.format(formatter);
    }

    /**
     * Get employee name
     */
    public String getEmployeeName() {
        return employee != null ? employee.getFullName() : "N/A";
    }

    /**
     * Get shift name
     */
    public String getShiftName() {
        return shift != null ? shift.getName() : "N/A";
    }

    /**
     * Get action by employee name
     */
    public String getActionByName() {
        return actionBy != null ? actionBy.getFullName() : "Sistema";
    }

    /**
     * Get action display name
     */
    public String getActionDisplayName() {
        return action != null ? action.getDisplayName() : "";
    }

    /**
     * Check if action was assignment
     */
    public boolean isAssignment() {
        return action == ShiftAction.ASSIGNED;
    }

    /**
     * Check if action was removal
     */
    public boolean isRemoval() {
        return action == ShiftAction.REMOVED;
    }

    /**
     * Get CSS badge class based on action
     */
    public String getActionBadgeClass() {
        if (action == null) {
            return "bg-secondary";
        }
        return switch (action) {
            case ASSIGNED -> "bg-success";
            case REMOVED -> "bg-danger";
        };
    }
}
