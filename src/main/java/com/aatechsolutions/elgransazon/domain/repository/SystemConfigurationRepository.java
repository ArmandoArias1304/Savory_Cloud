package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for SystemConfiguration entity
 * MULTI-TENANT: Each company has exactly one configuration (OneToOne relationship)
 */
@Repository
public interface SystemConfigurationRepository extends JpaRepository<SystemConfiguration, Long> {

    /**
     * Find the first (and should be only) configuration
     * LEGACY: Used for backward compatibility when no company context exists
     * Uses Spring Data derived query with 'First' keyword to limit to 1 result
     */
    Optional<SystemConfiguration> findFirstByOrderByIdAsc();

    /**
     * Check if any configuration exists
     */
    @Query("SELECT COUNT(sc) > 0 FROM SystemConfiguration sc")
    boolean existsConfiguration();

    /**
     * Count total configurations (should always be 0 or 1)
     */
    @Query("SELECT COUNT(sc) FROM SystemConfiguration sc")
    long countConfigurations();

    // ========== Multi-Tenant Methods (by Company) ==========

    /**
     * Find configuration by company
     */
    Optional<SystemConfiguration> findByCompany(Company company);

    /**
     * Check if configuration exists for company
     */
    boolean existsByCompany(Company company);

    /**
     * Find configuration by company ID
     */
    @Query("SELECT sc FROM SystemConfiguration sc WHERE sc.company.idCompany = :companyId")
    Optional<SystemConfiguration> findByCompanyId(@Param("companyId") Long companyId);
}
