package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Customer;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for Customer management
 * MULTI-TENANT: Customers are now scoped per company
 */
public interface CustomerService {
    
    // ========== MULTI-TENANT Methods (use these for new code) ==========
    
    /**
     * Find all customers for a specific company
     */
    List<Customer> findAllByCompany(Company company);
    
    /**
     * Find all active customers for a specific company
     */
    List<Customer> findAllActiveByCompany(Company company);
    
    /**
     * Find customer by ID and company
     */
    Optional<Customer> findByIdAndCompany(Long id, Company company);
    
    /**
     * Find customer by email and company
     */
    Optional<Customer> findByEmailAndCompany(String email, Company company);
    
    /**
     * Find customer by username and company
     */
    Optional<Customer> findByUsernameAndCompany(String username, Company company);
    
    /**
     * Find customer by username or email and company
     */
    Optional<Customer> findByUsernameOrEmailAndCompany(String usernameOrEmail, Company company);
    
    /**
     * Create new customer for a specific company
     */
    Customer create(Customer customer, Company company);
    
    /**
     * Update existing customer (must belong to company)
     */
    Customer update(Long id, Customer customer, Company company);
    
    /**
     * Delete customer (must belong to company)
     */
    void delete(Long id, Company company);
    
    /**
     * Check if username exists within company
     */
    boolean existsByUsernameAndCompany(String username, Company company);
    
    /**
     * Check if email exists within company
     */
    boolean existsByEmailAndCompany(String email, Company company);
    
    /**
     * Check if phone exists within company
     */
    boolean existsByPhoneAndCompany(String phone, Company company);
    
    /**
     * Check if username exists in employees table for a specific company
     */
    boolean usernameExistsInEmployees(String username, Company company);
    
    /**
     * Update last access timestamp
     */
    void updateLastAccess(String email, Company company);
    
    /**
     * Activate customer (must belong to company)
     */
    Customer activate(Long id, Company company);
    
    /**
     * Deactivate customer (must belong to company)
     */
    Customer deactivate(Long id, Company company);
    
    /**
     * Count customers for a specific company
     */
    long countByCompany(Company company);
    
    /**
     * Count active customers for a specific company
     */
    long countActiveByCompany(Company company);
    
    // ========== Legacy Methods (deprecated - for backwards compatibility) ==========
    
    /**
     * @deprecated Use findAllByCompany for multi-tenant queries
     */
    @Deprecated
    List<Customer> findAll();
    
    /**
     * @deprecated Use findAllActiveByCompany for multi-tenant queries
     */
    @Deprecated
    List<Customer> findAllActive();
    
    /**
     * @deprecated Use findByIdAndCompany for multi-tenant queries
     */
    @Deprecated
    Optional<Customer> findById(Long id);
    
    /**
     * @deprecated Use findByEmailAndCompany for multi-tenant queries
     */
    @Deprecated
    Optional<Customer> findByEmail(String email);
    
    /**
     * @deprecated Use findByUsernameAndCompany for multi-tenant queries
     */
    @Deprecated
    Optional<Customer> findByUsername(String username);
    
    /**
     * @deprecated Use findByUsernameOrEmailAndCompany for multi-tenant queries
     */
    @Deprecated
    Optional<Customer> findByUsernameOrEmail(String usernameOrEmail);
    
    /**
     * @deprecated Use create(Customer, Company) for multi-tenant operations
     */
    @Deprecated
    Customer create(Customer customer);
    
    /**
     * @deprecated Use update(Long, Customer, Company) for multi-tenant operations
     */
    @Deprecated
    Customer update(Long id, Customer customer);
    
    /**
     * @deprecated Use delete(Long, Company) for multi-tenant operations
     */
    @Deprecated
    void delete(Long id);
    
    /**
     * @deprecated Use existsByUsernameAndCompany for multi-tenant queries
     */
    @Deprecated
    boolean existsByUsername(String username);
    
    /**
     * @deprecated Use existsByEmailAndCompany for multi-tenant queries
     */
    @Deprecated
    boolean existsByEmail(String email);
    
    /**
     * @deprecated Use existsByPhoneAndCompany for multi-tenant queries
     */
    @Deprecated
    boolean existsByPhone(String phone);
    
    /**
     * @deprecated Use usernameExistsInEmployees(String, Company) for multi-tenant queries
     */
    @Deprecated
    boolean usernameExistsInEmployees(String username);
    
    /**
     * @deprecated Use updateLastAccess(String, Company) for multi-tenant operations
     */
    @Deprecated
    void updateLastAccess(String email);
    
    /**
     * @deprecated Use activate(Long, Company) for multi-tenant operations
     */
    @Deprecated
    Customer activate(Long id);
    
    /**
     * @deprecated Use deactivate(Long, Company) for multi-tenant operations
     */
    @Deprecated
    Customer deactivate(Long id);
}
