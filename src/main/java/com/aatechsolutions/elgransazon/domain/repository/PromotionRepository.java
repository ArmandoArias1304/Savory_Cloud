package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Promotion;
import com.aatechsolutions.elgransazon.domain.entity.PromotionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Promotion entity
 * Provides database access methods for promotional offers
 */
@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    /**
     * Find all active promotions
     */
    List<Promotion> findByActiveTrue();

    /**
     * Find promotions by type
     */
    List<Promotion> findByPromotionType(PromotionType promotionType);

    /**
     * Find active promotions by type
     */
    List<Promotion> findByActiveTrueAndPromotionType(PromotionType promotionType);

    /**
     * Find promotions that are currently valid (active and within date range)
     * @param today Current date
     */
    @Query("SELECT p FROM Promotion p WHERE p.active = true " +
           "AND p.startDate <= :today AND p.endDate >= :today " +
           "ORDER BY p.name ASC")
    List<Promotion> findActivePromotionsForDate(@Param("today") LocalDate today);

    /**
     * Find promotions for a specific item
     * @param itemId The menu item ID
     */
    @Query("SELECT p FROM Promotion p JOIN p.items i " +
           "WHERE i.idItemMenu = :itemId " +
           "ORDER BY p.name ASC")
    List<Promotion> findPromotionsByItemId(@Param("itemId") Long itemId);

    /**
     * Find active promotions for a specific item
     * @param itemId The menu item ID
     * @param today Current date
     */
    @Query("SELECT p FROM Promotion p JOIN p.items i " +
           "WHERE i.idItemMenu = :itemId " +
           "AND p.active = true " +
           "AND p.startDate <= :today AND p.endDate >= :today " +
           "ORDER BY p.name ASC")
    List<Promotion> findActivePromotionsByItemId(
        @Param("itemId") Long itemId, 
        @Param("today") LocalDate today
    );

    /**
     * Find promotions ending soon (within next N days)
     * @param today Current date
     * @param endDate Future date (today + N days)
     */
    @Query("SELECT p FROM Promotion p WHERE p.active = true " +
           "AND p.endDate >= :today AND p.endDate <= :endDate " +
           "ORDER BY p.endDate ASC")
    List<Promotion> findPromotionsEndingSoon(
        @Param("today") LocalDate today, 
        @Param("endDate") LocalDate endDate
    );

    /**
     * Count active promotions
     */
    @Query("SELECT COUNT(p) FROM Promotion p WHERE p.active = true " +
           "AND p.startDate <= :today AND p.endDate >= :today")
    long countActivePromotions(@Param("today") LocalDate today);

    /**
     * Count total promotions
     */
    long count();

    /**
     * Find promotions by name (case-insensitive, partial match)
     */
    @Query("SELECT p FROM Promotion p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')) " +
           "ORDER BY p.name ASC")
    List<Promotion> findByNameContainingIgnoreCase(@Param("name") String name);

    /**
     * Check if a promotion name already exists (for unique validation)
     * @param name Promotion name
     * @param excludeId ID to exclude from check (for updates)
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Promotion p " +
           "WHERE LOWER(p.name) = LOWER(:name) AND (:excludeId IS NULL OR p.idPromotion != :excludeId)")
    boolean existsByNameIgnoreCaseExcludingId(@Param("name") String name, @Param("excludeId") Long excludeId);

    /**
     * Find promotions ordered by name
     */
    List<Promotion> findAllByOrderByNameAsc();

    // ========== Multi-Tenant Methods (by Company) ==========

    /**
     * Find all promotions by company
     */
    List<Promotion> findByCompany(Company company);

    /**
     * Find promotion by ID and company (for security validation)
     */
    Optional<Promotion> findByIdPromotionAndCompany(Long idPromotion, Company company);

    /**
     * Find all active promotions by company
     */
    List<Promotion> findByActiveTrueAndCompany(Company company);

    /**
     * Find active promotions for date by company
     */
    @Query("SELECT p FROM Promotion p WHERE p.company = :company AND p.active = true " +
           "AND p.startDate <= :today AND p.endDate >= :today " +
           "ORDER BY p.name ASC")
    List<Promotion> findActivePromotionsForDateByCompany(@Param("company") Company company, @Param("today") LocalDate today);

    /**
     * Find all promotions by company ordered by name
     */
    List<Promotion> findByCompanyOrderByNameAsc(Company company);

    /**
     * Check if a promotion name already exists for a company (for unique validation)
     * @param name Promotion name
     * @param company Company
     * @param excludeId ID to exclude from check (for updates)
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Promotion p " +
           "WHERE LOWER(p.name) = LOWER(:name) AND p.company = :company AND (:excludeId IS NULL OR p.idPromotion != :excludeId)")
    boolean existsByNameIgnoreCaseAndCompanyExcludingId(@Param("name") String name, @Param("company") Company company, @Param("excludeId") Long excludeId);

    /**
     * Count active promotions by company
     */
    @Query("SELECT COUNT(p) FROM Promotion p WHERE p.company = :company AND p.active = true " +
           "AND p.startDate <= :today AND p.endDate >= :today")
    long countActivePromotionsByCompany(@Param("company") Company company, @Param("today") LocalDate today);

    // ========== Migration Helper Methods ==========

    /**
     * Find all promotions without company (for data migration)
     */
    List<Promotion> findByCompanyIsNull();
}
