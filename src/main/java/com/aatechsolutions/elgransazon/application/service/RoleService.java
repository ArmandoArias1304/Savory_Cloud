package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Role;
import com.aatechsolutions.elgransazon.domain.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for Role management
 * Handles business logic for role operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private final RoleRepository roleRepository;

    /**
     * Find all roles
     * 
     * @return List of all roles
     */
    @Transactional(readOnly = true)
    public List<Role> findAll() {
        log.debug("Finding all roles");
        return roleRepository.findAll();
    }

    /**
     * Find role by ID
     * 
     * @param id Role ID
     * @return Optional containing the role if found
     */
    @Transactional(readOnly = true)
    public Optional<Role> findById(Long id) {
        log.debug("Finding role by id: {}", id);
        return roleRepository.findById(id);
    }

    /**
     * Find role by name
     * 
     * @param nombreRol Role name
     * @return Optional containing the role if found
     */
    @Transactional(readOnly = true)
    public Optional<Role> findByNombreRol(String nombreRol) {
        log.debug("Finding role by name: {}", nombreRol);
        return roleRepository.findByNombreRol(nombreRol);
    }

    /**
     * Create a new role
     * 
     * @param role Role to create
     * @return Created role
     * @throws IllegalArgumentException if role with same name already exists
     */
    @Transactional
    public Role create(Role role) {
        log.info("Creating new role: {}", role.getNombreRol());

        if (roleRepository.existsByNombreRol(role.getNombreRol())) {
            log.error("Role with name {} already exists", role.getNombreRol());
            throw new IllegalArgumentException("Role with name '" + role.getNombreRol() + "' already exists");
        }

        Role savedRole = roleRepository.save(role);
        log.info("Role created successfully with id: {}", savedRole.getIdRol());
        
        return savedRole;
    }

    /**
     * Update an existing role
     * 
     * @param id Role ID to update
     * @param roleDetails Updated role details
     * @return Updated role
     * @throws IllegalArgumentException if role not found
     */
    @Transactional
    public Role update(Long id, Role roleDetails) {
        log.info("Updating role with id: {}", id);

        Role role = roleRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Role not found with id: {}", id);
                    return new IllegalArgumentException("Role not found with id: " + id);
                });

        role.setNombreRol(roleDetails.getNombreRol());

        Role updatedRole = roleRepository.save(role);
        log.info("Role updated successfully: {}", updatedRole.getIdRol());
        
        return updatedRole;
    }

    /**
     * Delete a role
     * 
     * @param id Role ID to delete
     * @throws IllegalArgumentException if role not found
     */
    @Transactional
    public void delete(Long id) {
        log.info("Deleting role with id: {}", id);

        if (!roleRepository.existsById(id)) {
            log.error("Role not found with id: {}", id);
            throw new IllegalArgumentException("Role not found with id: " + id);
        }

        roleRepository.deleteById(id);
        log.info("Role deleted successfully: {}", id);
    }

    /**
     * Initialize default roles if they don't exist
     * Useful for application startup
     */
    @Transactional
    public void initializeDefaultRoles() {
        log.info("Initializing default roles");

        createRoleIfNotExists(Role.ADMIN);
        createRoleIfNotExists(Role.WAITER);
        createRoleIfNotExists(Role.CHEF);

        log.info("Default roles initialized successfully");
    }

    /**
     * Helper method to create a role if it doesn't exist
     */
    private void createRoleIfNotExists(String roleName) {
        if (!roleRepository.existsByNombreRol(roleName)) {
            Role role = new Role(roleName);
            roleRepository.save(role);
            log.debug("Created role: {}", roleName);
        } else {
            log.debug("Role already exists: {}", roleName);
        }
    }
}
