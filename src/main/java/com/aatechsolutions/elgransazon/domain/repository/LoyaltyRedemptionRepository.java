package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.LoyaltyRedemption;
import com.aatechsolutions.elgransazon.domain.entity.LoyaltyRedemptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoyaltyRedemptionRepository extends JpaRepository<LoyaltyRedemption, Long> {

    Optional<LoyaltyRedemption> findByQuoteToken(String quoteToken);

    /** All reward lines of one order (one row per redeemed reward). */
    List<LoyaltyRedemption> findAllByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    List<LoyaltyRedemption> findByCompanyAndStatusOrderByCreatedAtDesc(Company company,
                                                                       LoyaltyRedemptionStatus status);

    List<LoyaltyRedemption> findTop50ByCompanyOrderByCreatedAtDesc(Company company);

    /** Recent applied redemptions, used to list reward orders still to be closed. */
    List<LoyaltyRedemption> findTop10ByCompanyAndStatusOrderByCreatedAtDesc(Company company,
                                                                           LoyaltyRedemptionStatus status);

    long countByCompanyAndStatus(Company company, LoyaltyRedemptionStatus status);

    @Query("SELECT COALESCE(SUM(r.stampsSpent), 0) FROM LoyaltyRedemption r " +
           "WHERE r.company = :company AND r.status = :status")
    long sumStampsByCompanyAndStatus(@Param("company") Company company,
                                     @Param("status") LoyaltyRedemptionStatus status);
}
