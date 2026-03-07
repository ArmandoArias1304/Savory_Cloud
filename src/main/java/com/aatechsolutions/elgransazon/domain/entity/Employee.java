package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * Employee entity representing employees who can access the POS system
 * Employees belong to a specific company (except PROGRAMMER who is global with null company)
 */
@Entity
@Table(name = "employee", uniqueConstraints = {
    @UniqueConstraint(name = "uk_employee_username_company", columnNames = {"username", "company_id"}),
    @UniqueConstraint(name = "uk_employee_telefono_company", columnNames = {"telefono", "company_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idEmpleado"})
@ToString(exclude = {"company", "roles", "supervisor", "shifts"})
public class Employee implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_empleado")
    private Long idEmpleado;

    // ========== Company Relationship (Multi-Tenant) ==========
    // NULL for PROGRAMMER (global), NOT NULL for all other employees
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = true)
    private Company company;

    @NotBlank(message = "El usuario es obligatorio")
    @Size(min = 3, max = 50, message = "El usuario debe tener entre 3 y 50 caracteres")
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(min = 2, max = 100, message = "El apellido debe tener entre 2 y 100 caracteres")
    @Column(name = "apellido", nullable = false, length = 100)
    private String apellido;

    @Min(value = 18, message = "La edad mínima es 18 años")
    @Max(value = 100, message = "La edad máxima es 100 años")
    @Column(name = "edad")
    private Integer edad;

    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    @Column(name = "contrasenia", nullable = false)
    private String contrasenia;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "El teléfono debe contener exactamente 10 dígitos sin espacios")
    @Column(name = "telefono", length = 20, nullable = true)
    private String telefono;

    @Column(name = "salario")
    private Double salario;

    @Column(name = "ultimo_acceso")
    private LocalDateTime lastAccess;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_supervisor")
    private Employee supervisor;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "employee_roles",
            joinColumns = @JoinColumn(name = "id_empleado"),
            inverseJoinColumns = @JoinColumn(name = "id_rol")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @ManyToMany(mappedBy = "employees", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Shift> shifts = new HashSet<>();

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
     * Returns the full name of the employee
     */
    public String getFullName() {
        return nombre + " " + apellido;
    }

    /**
     * Get initials from first name and last name
     */
    public String getInitials() {
        String firstInitial = nombre != null && !nombre.isEmpty() ? nombre.substring(0, 1).toUpperCase() : "";
        String lastInitial = apellido != null && !apellido.isEmpty() ? apellido.substring(0, 1).toUpperCase() : "";
        return firstInitial + lastInitial;
    }

    /**
     * Get formatted last access date
     */
    public String getFormattedLastAccess() {
        if (lastAccess == null) {
            return "Nunca";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return lastAccess.format(formatter);
    }

    /**
     * Get formatted salary
     */
    public String getFormattedSalary() {
        if (salario == null) {
            return "No definido";
        }
        return String.format("$%,.2f", salario);
    }

    /**
     * Check if employee has a specific role
     */
    public boolean hasRole(String roleName) {
        return roles.stream()
                .anyMatch(role -> role.getNombreRol().equals(roleName));
    }

    /**
     * Get the primary role (first role in the set)
     * Used for determining which page to redirect to
     */
    public String getPrimaryRole() {
        return roles.stream()
                .findFirst()
                .map(Role::getNombreRol)
                .orElse("ROLE_EMPLOYEE");
    }

    /**
     * Get role display name
     */
    public String getRoleDisplayName() {
        return roles.stream()
                .findFirst()
                .map(Role::getDisplayName)
                .orElse("Sin rol");
    }

    /**
     * Get count of assigned shifts
     */
    public int getShiftCount() {
        return shifts != null ? shifts.size() : 0;
    }

    /**
     * Get shift names as comma-separated string
     */
    public String getShiftNames() {
        if (shifts == null || shifts.isEmpty()) {
            return "Sin turnos";
        }
        return shifts.stream()
                .map(Shift::getName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("Sin turnos");
    }

    /**
     * Get supervisor name or default text
     */
    public String getSupervisorName() {
        return supervisor != null ? supervisor.getFullName() : "Sin supervisor";
    }
}
