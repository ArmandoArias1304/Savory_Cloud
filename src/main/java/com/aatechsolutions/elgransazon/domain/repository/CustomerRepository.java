package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Customer entity
 * 
 * MULTI-TENANT: All queries should filter by Company.
 * Global methods (without company) are kept only for PROGRAMMER operations and system-level queries.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    
    // ========== MULTI-TENANT Methods (by Company) - PRIMARY ==========
    
    /**
     * Find customer by username and company (case insensitive)
     */
    Optional<Customer> findByUsernameIgnoreCaseAndCompany(String username, Company company);
    
    /**
     * Find customer by email and company (case insensitive)
     */
    Optional<Customer> findByEmailIgnoreCaseAndCompany(String email, Company company);
    
    /**
     * Find customer by username OR email and company (case insensitive)
     */
    @Query("SELECT c FROM Customer c WHERE c.company = :company AND (LOWER(c.username) = LOWER(:identifier) OR LOWER(c.email) = LOWER(:identifier))")
    Optional<Customer> findByUsernameOrEmailAndCompany(@Param("identifier") String identifier, @Param("company") Company company);
    
    /**
     * Check if username exists in company (case insensitive)
     */
    boolean existsByUsernameIgnoreCaseAndCompany(String username, Company company);
    
    /**
     * Check if email exists in company (case insensitive)
     */
    boolean existsByEmailIgnoreCaseAndCompany(String email, Company company);
    
    /**
     * Check if phone exists in company
     */
    boolean existsByPhoneAndCompany(String phone, Company company);
    
    /**
     * Find all customers by company
     */
    List<Customer> findByCompany(Company company);
    
    /**
     * Find all active customers by company
     */
    List<Customer> findByCompanyAndActiveTrue(Company company);
    
    /**
     * Find customer by ID and company (for security validation)
     */
    Optional<Customer> findByIdCustomerAndCompany(Long idCustomer, Company company);
    
    /**
     * Count customers by company
     */
    long countByCompany(Company company);
    
    /**
     * Count active customers by company
     */
    long countByCompanyAndActiveTrue(Company company);
    
    // ========== Legacy/Global Methods (for system-level operations) ==========
    // These methods search across all companies - use with caution!
    
    /**
     * Find customer by username (case insensitive) - GLOBAL
     * @deprecated Use findByUsernameIgnoreCaseAndCompany for multi-tenant queries
     */
    Optional<Customer> findByUsernameIgnoreCase(String username);
    
    /**
     * Find customer by email (case insensitive) - GLOBAL
     * @deprecated Use findByEmailIgnoreCaseAndCompany for multi-tenant queries
     */
    Optional<Customer> findByEmailIgnoreCase(String email);
    
    /**
     * Find customer by username OR email (case insensitive) - GLOBAL
     * @deprecated Use findByUsernameOrEmailAndCompany for multi-tenant queries
     */
    Optional<Customer> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);
    
    /**
     * Check if username exists - GLOBAL
     * @deprecated Use existsByUsernameIgnoreCaseAndCompany for multi-tenant queries
     */
    boolean existsByUsernameIgnoreCase(String username);
    
    /**
     * Check if email exists - GLOBAL
     * @deprecated Use existsByEmailIgnoreCaseAndCompany for multi-tenant queries
     */
    boolean existsByEmailIgnoreCase(String email);
    
    /**
     * Check if phone exists - GLOBAL
     * @deprecated Use existsByPhoneAndCompany for multi-tenant queries
     */
    boolean existsByPhone(String phone);
    
    /**
     * Find all active customers - GLOBAL
     * @deprecated Use findByCompanyAndActiveTrue for multi-tenant queries
     */
    List<Customer> findByActiveTrue();
}
