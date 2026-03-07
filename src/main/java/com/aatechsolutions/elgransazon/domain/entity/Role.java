package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Role entity representing different roles in the POS system
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idRol"})
@ToString(exclude = {"employees"})
public class Role implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    private Long idRol;

    @Column(name = "nombre_rol", nullable = false, unique = true, length = 50)
    private String nombreRol;

    @ManyToMany(mappedBy = "roles")
    @Builder.Default
    private Set<Employee> employees = new HashSet<>();

    /**
     * Role name constants for easy reference
     */
    public static final String ADMIN = "ROLE_ADMIN";
    public static final String MANAGER = "ROLE_MANAGER";
    public static final String WAITER = "ROLE_WAITER";
    public static final String CHEF = "ROLE_CHEF";
    public static final String BARISTA = "ROLE_BARISTA";
    public static final String CASHIER = "ROLE_CASHIER";
    public static final String DELIVERY = "ROLE_DELIVERY";
    public static final String CLIENT = "ROLE_CLIENT";
    public static final String PROGRAMMER = "ROLE_PROGRAMMER";

    /**
     * 
     * Constructor for creating roles with just the name
     */
    public Role(String nombreRol) {
        this.nombreRol = nombreRol;
    }

    /**
     * Get display name for the role (without ROLE_ prefix)
     */
    public String getDisplayName() {
        if (nombreRol == null) {
            return "Sin rol";
        }
        
        String name = nombreRol.replace("ROLE_", "");
        
        // Map to Spanish names
        switch (name) {
            case "ADMIN": return "Administrador";
            case "MANAGER": return "Gerente";
            case "WAITER": return "Mesero";
            case "CHEF": return "Chef";
            case "BARISTA": return "Barista";
            case "CASHIER": return "Cajero";
            case "DELIVERY": return "Repartidor";
            case "CLIENT": return "Cliente";
            case "PROGRAMMER": return "Programador";
            default: return name;
        }
    }
}
