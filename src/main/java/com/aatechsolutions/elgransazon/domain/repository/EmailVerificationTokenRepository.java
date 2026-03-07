package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.domain.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for EmailVerificationToken entity
 * MULTI-TENANT: Tokens are scoped to a specific company
 */
@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    
    /**
     * Find token by its value (tokens are globally unique)
     */
    Optional<EmailVerificationToken> findByToken(String token);
    
    // ========== MULTI-TENANT Methods ==========
    
    /**
     * Find token by customer and company
     */
    Optional<EmailVerificationToken> findByCustomerAndCompany(Customer customer, Company company);
    
    /**
     * Delete tokens by customer and company
     */
    void deleteByCustomerAndCompany(Customer customer, Company company);
    
    // ========== Legacy Methods (for backwards compatibility) ==========
    
    /**
     * Find token by customer
     * @deprecated Use findByCustomerAndCompany for multi-tenant queries
     */
    Optional<EmailVerificationToken> findByCustomer(Customer customer);
    
    /**
     * Delete tokens by customer
     * @deprecated Use deleteByCustomerAndCompany for multi-tenant queries
     */
    void deleteByCustomer(Customer customer);
}
