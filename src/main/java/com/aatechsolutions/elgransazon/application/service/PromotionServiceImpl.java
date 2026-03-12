package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Promotion;
import com.aatechsolutions.elgransazon.domain.entity.PromotionType;
import com.aatechsolutions.elgransazon.domain.repository.PromotionRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of PromotionService
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;
    private final DateTimeService dateTimeService;

    @Override
    public List<Promotion> findAll() {
        log.debug("Finding all promotions");
        Company currentCompany = CompanyContext.requireCurrentCompany();
        // MULTI-TENANT: Use direct repository method instead of filtering in memory
        return promotionRepository.findByCompany(currentCompany);
    }

    @Override
    public Optional<Promotion> findById(Long id) {
        log.debug("Finding promotion by ID: {}", id);
        Company currentCompany = CompanyContext.requireCurrentCompany();
        Optional<Promotion> promotion = promotionRepository.findById(id);
        if (promotion.isPresent()) {
            if (!currentCompany.equals(promotion.get().getCompany())) {
                return Optional.empty();
            }
        }
        return promotion;
    }

    @Override
    @Transactional
    public Promotion save(Promotion promotion) {
        log.info("Saving promotion: {}", promotion.getName());
        
        // Multi-tenant: Set company on entity
        Company currentCompany = CompanyContext.requireCurrentCompany();
        promotion.setCompany(currentCompany);
        
        // Validate configuration before saving
        if (!isValidConfiguration(promotion)) {
            throw new IllegalArgumentException("Configuración de promoción inválida para el tipo seleccionado");
        }
        
        // Validate date range
        if (promotion.getStartDate().isAfter(promotion.getEndDate())) {
            throw new IllegalArgumentException("La fecha de inicio debe ser anterior o igual a la fecha de fin");
        }
        
        // Check for duplicate name (excluding current promotion if updating)
        Long excludeId = promotion.getIdPromotion();
        if (existsByName(promotion.getName(), excludeId)) {
            throw new IllegalArgumentException("Ya existe una promoción con ese nombre");
        }
        
        return promotionRepository.save(promotion);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        log.info("Deleting promotion with ID: {}", id);
        
        // Multi-tenant: Verify company ownership before delete
        Company currentCompany = CompanyContext.requireCurrentCompany();
        Promotion promotion = promotionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Promoción no encontrada con ID: " + id));
        
        if (!currentCompany.equals(promotion.getCompany())) {
            throw new IllegalArgumentException("Promoción no encontrada con ID: " + id);
        }
        
        promotionRepository.deleteById(id);
    }

    @Override
    public List<Promotion> findActivePromotions() {
        log.debug("Finding active promotions for today");
        LocalDate today = dateTimeService.todayLocal();
        Company currentCompany = CompanyContext.requireCurrentCompany();
        
        // Get promotions that are within date range, active, and filtered by company at SQL level
        // Use company-filtered query for better performance and security
        List<Promotion> promotions = promotionRepository.findActivePromotionsForDateByCompany(currentCompany, today);
        
        // Filter by valid day of week (validDays field)
        return promotions.stream()
            .filter(promotion -> promotion.isValidForDay(today.getDayOfWeek()))
            .toList();
    }

    @Override
    public List<Promotion> findByType(PromotionType type) {
        log.debug("Finding promotions by type: {}", type);
        Company currentCompany = CompanyContext.requireCurrentCompany();
        List<Promotion> promotions = promotionRepository.findByPromotionType(type);
        return promotions.stream()
            .filter(p -> currentCompany.equals(p.getCompany()))
            .toList();
    }

    @Override
    public List<Promotion> findActiveByType(PromotionType type) {
        log.debug("Finding active promotions by type: {}", type);
        Company currentCompany = CompanyContext.requireCurrentCompany();
        return promotionRepository.findByPromotionType(type).stream()
            .filter(promotion -> promotion.isValidNow())
            .filter(promotion -> currentCompany.equals(promotion.getCompany()))
            .toList();
    }

    @Override
    public List<Promotion> findPromotionsByItemId(Long itemId) {
        log.debug("Finding promotions for item ID: {}", itemId);
        Company currentCompany = CompanyContext.requireCurrentCompany();
        List<Promotion> promotions = promotionRepository.findPromotionsByItemId(itemId);
        return promotions.stream()
            .filter(p -> currentCompany.equals(p.getCompany()))
            .toList();
    }

    @Override
    public List<Promotion> findActivePromotionsByItemId(Long itemId) {
        log.debug("Finding active promotions for item ID: {}", itemId);
        LocalDate today = dateTimeService.todayLocal();
        Company currentCompany = CompanyContext.requireCurrentCompany();
        
        // Get promotions for item that are within date range and active
        List<Promotion> promotions = promotionRepository.findActivePromotionsByItemId(itemId, today);
        
        // Filter by valid day of week (validDays field) and company
        return promotions.stream()
            .filter(promotion -> promotion.isValidForDay(today.getDayOfWeek()))
            .filter(promotion -> currentCompany.equals(promotion.getCompany()))
            .toList();
    }

    @Override
    public boolean isPromotionValidNow(Long promotionId) {
        log.debug("Checking if promotion is valid now: {}", promotionId);
        Company currentCompany = CompanyContext.requireCurrentCompany();
        
        return promotionRepository.findById(promotionId)
                .filter(p -> currentCompany.equals(p.getCompany()))
                .map(Promotion::isValidNow)
                .orElse(false);
    }

    @Override
    public boolean isPromotionValidForDate(Promotion promotion, LocalDate date) {
        if (promotion == null || date == null) {
            return false;
        }
        
        boolean withinDateRange = !date.isBefore(promotion.getStartDate()) 
                                  && !date.isAfter(promotion.getEndDate());
        boolean validForDay = promotion.isValidForDay(date.getDayOfWeek());
        boolean isActive = Boolean.TRUE.equals(promotion.getActive());
        
        return isActive && withinDateRange && validForDay;
    }

    @Override
    public BigDecimal calculateDiscountedPrice(BigDecimal originalPrice, Promotion promotion, int quantity) {
        if (promotion == null || originalPrice == null || quantity <= 0) {
            return originalPrice != null ? originalPrice.multiply(BigDecimal.valueOf(quantity)) : BigDecimal.ZERO;
        }
        
        // CRITICAL: Validate promotion is valid NOW (date range + day of week)
        if (!promotion.isValidNow()) {
            log.warn("Attempted to apply invalid promotion: {} (not valid for today)", promotion.getName());
            return originalPrice.multiply(BigDecimal.valueOf(quantity)); // Return full price without discount
        }
        
        return promotion.calculateDiscountedPrice(originalPrice, quantity);
    }

    @Override
    public BigDecimal calculateSavings(BigDecimal originalPrice, Promotion promotion, int quantity) {
        if (promotion == null || originalPrice == null || quantity <= 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal originalTotal = originalPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal discountedTotal = calculateDiscountedPrice(originalPrice, promotion, quantity);
        
        return originalTotal.subtract(discountedTotal);
    }

    @Override
    public Optional<Promotion> getBestPromotionForItem(Long itemId) {
        log.debug("Finding best promotion for item ID: {}", itemId);
        
        List<Promotion> activePromotions = findActivePromotionsByItemId(itemId);
        
        if (activePromotions.isEmpty()) {
            return Optional.empty();
        }
        
        // For simplicity, we'll use priority as the main criterion
        // In a real scenario, you might want to calculate actual savings
        return activePromotions.stream()
                .max(Comparator.comparing(Promotion::getPriority));
    }

    @Override
    @Transactional
    public Promotion activate(Long id) {
        log.info("Activating promotion with ID: {}", id);
        
        // Multi-tenant: Verify company ownership
        Company currentCompany = CompanyContext.requireCurrentCompany();
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promoción no encontrada con ID: " + id));
        
        if (!currentCompany.equals(promotion.getCompany())) {
            throw new IllegalArgumentException("Promoción no encontrada con ID: " + id);
        }
        
        promotion.setActive(true);
        promotion.setCompany(currentCompany);
        return promotionRepository.save(promotion);
    }

    @Override
    @Transactional
    public Promotion deactivate(Long id) {
        log.info("Deactivating promotion with ID: {}", id);
        
        // Multi-tenant: Verify company ownership
        Company currentCompany = CompanyContext.requireCurrentCompany();
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promoción no encontrada con ID: " + id));
        
        if (!currentCompany.equals(promotion.getCompany())) {
            throw new IllegalArgumentException("Promoción no encontrada con ID: " + id);
        }
        
        promotion.setActive(false);
        promotion.setCompany(currentCompany);
        return promotionRepository.save(promotion);
    }

    @Override
    public List<Promotion> findPromotionsEndingSoon(int days) {
        log.debug("Finding promotions ending in next {} days", days);
        
        LocalDate today = dateTimeService.todayLocal();
        LocalDate endDate = today.plusDays(days);
        Company currentCompany = CompanyContext.requireCurrentCompany();
        
        List<Promotion> promotions = promotionRepository.findPromotionsEndingSoon(today, endDate);
        return promotions.stream()
            .filter(p -> currentCompany.equals(p.getCompany()))
            .toList();
    }

    @Override
    public long countActivePromotions() {
        Company currentCompany = CompanyContext.requireCurrentCompany();
        return promotionRepository.countActivePromotionsByCompany(currentCompany, dateTimeService.todayLocal());
    }

    @Override
    public boolean isValidConfiguration(Promotion promotion) {
        if (promotion == null || promotion.getPromotionType() == null) {
            return false;
        }
        
        return promotion.isValidConfiguration();
    }

    @Override
    public boolean existsByName(String name, Long excludeId) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        Company currentCompany = CompanyContext.requireCurrentCompany();
        return promotionRepository.existsByNameIgnoreCaseAndCompanyExcludingId(name.trim(), currentCompany, excludeId);
    }

    @Override
    public List<Promotion> findAllOrderedByPriority() {
        log.debug("Finding all promotions ordered by priority");
        Company currentCompany = CompanyContext.requireCurrentCompany();
        return promotionRepository.findByCompanyOrderByPriorityDescNameAsc(currentCompany);
    }

    @Override
    public java.util.Map<String, Object> validateFixedDiscountAmount(Promotion promotion) {
        log.debug("Validating fixed discount amount for promotion: {}", promotion.getName());
        
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        java.util.List<String> invalidItems = new java.util.ArrayList<>();
        
        // Only validate for FIXED_AMOUNT_DISCOUNT type
        if (promotion.getPromotionType() == PromotionType.FIXED_AMOUNT_DISCOUNT 
            && promotion.getDiscountAmount() != null) {
            
            BigDecimal discountAmount = promotion.getDiscountAmount();
            
            // Check each item in the promotion
            for (com.aatechsolutions.elgransazon.domain.entity.ItemMenu item : promotion.getItems()) {
                if (item.getPrice().compareTo(discountAmount) < 0) {
                    // Discount is greater than item price - this is invalid
                    invalidItems.add(String.format("%s ($%.2f)", 
                        item.getName(), 
                        item.getPrice()));
                    log.warn("Invalid discount amount for item '{}': discount ${} > price ${}",
                        item.getName(), discountAmount, item.getPrice());
                }
            }
        }
        
        result.put("valid", invalidItems.isEmpty());
        result.put("invalidItems", invalidItems);
        result.put("discountAmount", promotion.getDiscountAmount());
        
        return result;
    }
}
