package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.domain.entity.LoyaltyAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, Long> {

    Optional<LoyaltyAccount> findByCompanyAndCustomer(Company company, Customer customer);

    @Query("SELECT a FROM LoyaltyAccount a WHERE a.company = :company AND a.customer.idCustomer = :customerId")
    Optional<LoyaltyAccount> findByCompanyAndCustomerId(@Param("company") Company company,
                                                        @Param("customerId") Long customerId);

    /**
     * Locks the account row while stamps are earned or redeemed. Two cashiers
     * redeeming the last stamp at the same time serialize here.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM LoyaltyAccount a WHERE a.id = :id")
    Optional<LoyaltyAccount> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT a FROM LoyaltyAccount a WHERE a.company = :company ORDER BY a.balance DESC, a.lifetimeEarned DESC")
    List<LoyaltyAccount> findByCompanyOrderByBalance(@Param("company") Company company);

    long countByCompany(Company company);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM LoyaltyAccount a WHERE a.company = :company")
    long sumBalanceByCompany(@Param("company") Company company);
}
