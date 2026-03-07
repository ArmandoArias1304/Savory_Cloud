package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.BackupConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for BackupConfiguration entity
 */
@Repository
public interface BackupConfigurationRepository extends JpaRepository<BackupConfiguration, Long> {
    
    /**
     * Get the first (and should be only) configuration
     */
    default Optional<BackupConfiguration> getConfiguration() {
        return findAll().stream().findFirst();
    }
}
