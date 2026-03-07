package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.SystemLicense;
import com.aatechsolutions.elgransazon.domain.entity.SystemLicense.LicenseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for SystemLicense entity
 * MULTI-TENANT: Each company has exactly one license (OneToOne relationship)
 */
@Repository
public interface SystemLicenseRepository extends JpaRepository<SystemLicense, Long> {

    /**
     * Find license by license key
     */
    Optional<SystemLicense> findByLicenseKey(String licenseKey);

    /**
     * Find licenses by status
     */
    List<SystemLicense> findByStatus(LicenseStatus status);

    /**
     * Find licenses expiring before a given date
     */
    @Query("SELECT sl FROM SystemLicense sl WHERE sl.expirationDate <= :date AND sl.status = 'ACTIVE'")
    List<SystemLicense> findLicensesExpiringBefore(@Param("date") LocalDate date);

    /**
     * Find licenses expiring in the next X days
     */
    @Query("SELECT sl FROM SystemLicense sl WHERE sl.expirationDate BETWEEN :today AND :futureDate AND sl.status = 'ACTIVE'")
    List<SystemLicense> findLicensesExpiringBetween(@Param("today") LocalDate today, @Param("futureDate") LocalDate futureDate);

    /**
     * Check if any active license exists
     */
    @Query("SELECT COUNT(sl) > 0 FROM SystemLicense sl WHERE sl.status = 'ACTIVE' AND sl.expirationDate > :today")
    boolean existsActiveLicense(@Param("today") LocalDate today);

    /**
     * Get the first (and should be only) license in the system
     * LEGACY: Used for backward compatibility when no company context exists
     */
    Optional<SystemLicense> findFirstByOrderByIdAsc();

    // ========== Multi-Tenant Methods (by Company) ==========

    /**
     * Find license by company
     */
    Optional<SystemLicense> findByCompany(Company company);

    /**
     * Check if license exists for company
     */
    boolean existsByCompany(Company company);

    /**
     * Find license by company ID
     */
    @Query("SELECT sl FROM SystemLicense sl WHERE sl.company.idCompany = :companyId")
    Optional<SystemLicense> findByCompanyId(@Param("companyId") Long companyId);
}
