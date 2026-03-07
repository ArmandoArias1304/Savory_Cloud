package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for GlobalSystemConfig entity.
 * This entity is a SINGLETON (only id=1 exists).
 * 
 * Usage:
 * - findById(1L) to get the global config
 * - save() to create/update
 * - existsById(1L) to check if initialized
 */
@Repository
public interface GlobalSystemConfigRepository extends JpaRepository<GlobalSystemConfig, Long> {
    
    /**
     * Check if the global configuration exists.
     * Convenience method for initialization checks.
     */
    default boolean configurationExists() {
        return existsById(1L);
    }

    /**
     * Get the singleton global configuration.
     * Returns null if not initialized.
     */
    default GlobalSystemConfig getConfiguration() {
        return findById(1L).orElse(null);
    }
}
