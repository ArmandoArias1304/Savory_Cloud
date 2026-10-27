package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.LoyaltyAccount;
import com.aatechsolutions.elgransazon.domain.entity.LoyaltyStampEvent;
import com.aatechsolutions.elgransazon.domain.entity.LoyaltyStampEventType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoyaltyStampEventRepository extends JpaRepository<LoyaltyStampEvent, Long> {

    @EntityGraph(attributePaths = {"order", "redemption.reward"})
    List<LoyaltyStampEvent> findTop50ByAccountOrderByCreatedAtDesc(LoyaltyAccount account);

    boolean existsByOrder_IdOrderAndEventType(Long orderId, LoyaltyStampEventType eventType);

    boolean existsByRedemption_IdAndEventType(Long redemptionId, LoyaltyStampEventType eventType);

    @Query("SELECT COALESCE(SUM(e.stamps), 0) FROM LoyaltyStampEvent e " +
           "WHERE e.account = :account AND e.eventType = :type")
    long sumByAccountAndType(@Param("account") LoyaltyAccount account,
                             @Param("type") LoyaltyStampEventType type);

    @Query("SELECT COALESCE(SUM(e.stamps), 0) FROM LoyaltyStampEvent e " +
           "WHERE e.account = :account AND e.eventType = :type AND e.createdAt >= :from")
    long sumStampsSince(@Param("account") LoyaltyAccount account,
                        @Param("type") LoyaltyStampEventType type,
                        @Param("from") LocalDateTime from);

    @Query("SELECT e FROM LoyaltyStampEvent e " +
           "WHERE e.company = :company AND e.createdAt >= :from ORDER BY e.createdAt DESC")
    List<LoyaltyStampEvent> findRecentByCompany(@Param("company") Company company,
                                                @Param("from") LocalDateTime from);

    @Query("SELECT COUNT(e) FROM LoyaltyStampEvent e " +
           "WHERE e.company = :company AND e.eventType = :type AND e.createdAt >= :from")
    long countByCompanyAndTypeSince(@Param("company") Company company,
                                    @Param("type") LoyaltyStampEventType type,
                                    @Param("from") LocalDateTime from);

    @Query("SELECT COALESCE(SUM(ABS(e.stamps)), 0) FROM LoyaltyStampEvent e " +
           "WHERE e.company = :company AND e.eventType = :type AND e.createdAt >= :from")
    long sumStampsByCompanyAndTypeSince(@Param("company") Company company,
                                        @Param("type") LoyaltyStampEventType type,
                                        @Param("from") LocalDateTime from);
}
