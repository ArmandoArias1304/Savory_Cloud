package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.domain.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for PasswordResetToken entity
 * MULTI-TENANT: Tokens are scoped to a specific company
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    
    /**
     * Find token by its value (tokens are globally unique)
     */
    Optional<PasswordResetToken> findByToken(String token);
    
    // ========== MULTI-TENANT Methods ==========
    
    /**
     * Find token by customer and company
     */
    Optional<PasswordResetToken> findByCustomerAndCompany(Customer customer, Company company);
    
    /**
     * Delete tokens by customer and company
     */
    void deleteByCustomerAndCompany(Customer customer, Company company);
    
    // ========== Legacy Methods (for backwards compatibility) ==========
    
    /**
     * Delete tokens by customer
     * @deprecated Use deleteByCustomerAndCompany for multi-tenant queries
     */
    void deleteByCustomer(Customer customer);
}
