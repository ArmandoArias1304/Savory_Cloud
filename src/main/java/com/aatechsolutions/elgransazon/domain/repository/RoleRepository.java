package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for Role entity
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Find a role by its name
     * 
     * @param nombreRol the role name
     * @return Optional containing the role if found
     */
    Optional<Role> findByNombreRol(String nombreRol);

    /**
     * Check if a role exists by name
     * 
     * @param nombreRol the role name
     * @return true if role exists, false otherwise
     */
    boolean existsByNombreRol(String nombreRol);
}