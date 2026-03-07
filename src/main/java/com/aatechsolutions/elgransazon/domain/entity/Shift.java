package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Shift entity representing work shifts for employees
 */
@Entity
@Table(name = "shifts", uniqueConstraints = {
    @UniqueConstraint(name = "uk_shift_name_company", columnNames = {"name", "company_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"company", "employees"})
public class Shift implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ========== Company Relationship (Multi-Tenant) ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotBlank(message = "Shift name is required")
    @Size(min = 2, max = 100, message = "Shift name must be between 2 and 100 characters")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    @Column(name = "description", length = 500)
    private String description;

    @NotNull(message = "Start time is required")
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "shift_work_days", joinColumns = @JoinColumn(name = "shift_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    @Builder.Default
    private Set<DayOfWeek> workDays = new HashSet<>();

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "employee_shifts",
            joinColumns = @JoinColumn(name = "shift_id"),
            inverseJoinColumns = @JoinColumn(name = "employee_id")
    )
    @Builder.Default
    private Set<Employee> employees = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Lifecycle callback to validate times before persist
     */
    @PrePersist
    @PreUpdate
    protected void validateAndSetTimestamps() {
        if (startTime != null && endTime != null) {
            if (endTime.isBefore(startTime) || endTime.equals(startTime)) {
                throw new IllegalArgumentException("La hora de fin debe ser después de la hora de inicio");
            }
        }
        
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Get formatted time range
     */
    public String getTimeRange() {
        if (startTime == null || endTime == null) {
            return "";
        }
        return startTime.toString() + " - " + endTime.toString();
    }

    /**
     * Get work days as comma-separated string in Spanish
     */
    public String getWorkDaysString() {
        if (workDays == null || workDays.isEmpty()) {
            return "";
        }
        return workDays.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(DayOfWeek::getDisplayName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Get sorted work days
     */
    public List<DayOfWeek> getSortedWorkDays() {
        return workDays.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList();
    }

    /**
     * Check if shift is active on a specific day
     */
    public boolean isActiveOnDay(DayOfWeek day) {
        return active && workDays.contains(day);
    }

    /**
     * Get count of assigned employees
     */
    public int getEmployeeCount() {
        return employees != null ? employees.size() : 0;
    }

    /**
     * Check if employee is assigned to this shift
     */
    public boolean hasEmployee(Employee employee) {
        return employees != null && employees.contains(employee);
    }

    /**
     * Add employee to shift
     */
    public void addEmployee(Employee employee) {
        if (employees == null) {
            employees = new HashSet<>();
        }
        employees.add(employee);
    }

    /**
     * Remove employee from shift
     */
    public void removeEmployee(Employee employee) {
        if (employees != null) {
            employees.remove(employee);
        }
    }

    /**
     * Check if shift has any employees assigned
     */
    public boolean hasEmployees() {
        return employees != null && !employees.isEmpty();
    }

    /**
     * Get list of employee names
     */
    public List<String> getEmployeeNames() {
        if (employees == null || employees.isEmpty()) {
            return Collections.emptyList();
        }
        return employees.stream()
                .map(Employee::getFullName)
                .sorted()
                .toList();
    }
}
