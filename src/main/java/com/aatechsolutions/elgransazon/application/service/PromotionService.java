package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Promotion;
import com.aatechsolutions.elgransazon.domain.entity.PromotionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for Promotion management
 */
public interface PromotionService {

    /**
     * Find all promotions
     */
    List<Promotion> findAll();

    /**
     * Find promotion by ID
     */
    Optional<Promotion> findById(Long id);

    /**
     * Save (create or update) a promotion
     */
    Promotion save(Promotion promotion);

    /**
     * Delete a promotion by ID
     */
    void deleteById(Long id);

    /**
     * Find all active promotions
     */
    List<Promotion> findActivePromotions();

    /**
     * Find promotions by type
     */
    List<Promotion> findByType(PromotionType type);

    /**
     * Find active promotions by type
     */
    List<Promotion> findActiveByType(PromotionType type);

    /**
     * Find promotions for a specific item
     */
    List<Promotion> findPromotionsByItemId(Long itemId);

    /**
     * Find active promotions for a specific item
     */
    List<Promotion> findActivePromotionsByItemId(Long itemId);

    /**
     * Check if a promotion is valid right now (date and day of week)
     */
    boolean isPromotionValidNow(Long promotionId);

    /**
     * Check if a promotion is valid for a specific date and day
     */
    boolean isPromotionValidForDate(Promotion promotion, LocalDate date);

    /**
     * Calculate discounted price for an item with a promotion
     */
    BigDecimal calculateDiscountedPrice(BigDecimal originalPrice, Promotion promotion, int quantity);

    /**
     * Calculate savings from a promotion
     */
    BigDecimal calculateSavings(BigDecimal originalPrice, Promotion promotion, int quantity);

    /**
     * Get the best promotion for an item (highest savings)
     */
    Optional<Promotion> getBestPromotionForItem(Long itemId);

    /**
     * Activate a promotion
     */
    Promotion activate(Long id);

    /**
     * Deactivate a promotion
     */
    Promotion deactivate(Long id);

    /**
     * Find promotions ending soon (within next N days)
     */
    List<Promotion> findPromotionsEndingSoon(int days);

    /**
     * Count active promotions
     */
    long countActivePromotions();

    /**
     * Validate promotion configuration
     */
    boolean isValidConfiguration(Promotion promotion);

    /**
     * Check if promotion name already exists
     */
    boolean existsByName(String name, Long excludeId);

    /**
     * Find all promotions ordered by priority
     */
    List<Promotion> findAllOrderedByPriority();

    /**
     * Validate that fixed discount amount is not greater than item prices
     * @param promotion The promotion to validate
     * @return Map with validation results: "valid" (boolean) and "invalidItems" (list of item names)
     */
    java.util.Map<String, Object> validateFixedDiscountAmount(Promotion promotion);
}
