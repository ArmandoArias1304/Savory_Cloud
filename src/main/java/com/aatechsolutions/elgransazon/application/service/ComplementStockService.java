package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.ComplementIngredient;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for managing complement stock operations with proper transaction handling.
 * Uses PESSIMISTIC_WRITE lock to handle concurrent stock updates.
 * This ensures only one transaction can modify an ingredient at a time.
 * 
 * This mirrors the IngredientStockService pattern but is specifically for
 * complement ingredient deductions and returns.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplementStockService {

    private final IngredientRepository ingredientRepository;
    
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long BASE_RETRY_DELAY_MS = 100;

    /**
     * Deduct stock for a complement's ingredients with retry mechanism.
     * Uses pessimistic locking to prevent concurrent modification issues.
     * 
     * @param complementIngredients List of complement ingredients to deduct from
     * @param complementQuantity Number of complement portions to deduct
     */
    public void deductStockForComplement(List<ComplementIngredient> complementIngredients, int complementQuantity) {
        for (ComplementIngredient ci : complementIngredients) {
            BigDecimal quantityToDeduct = ci.getQuantity().multiply(BigDecimal.valueOf(complementQuantity));
            Long ingredientId = ci.getIngredient().getIdIngredient();
            
            deductStockWithRetry(ingredientId, quantityToDeduct, ci.getUnit());
        }
    }

    /**
     * Return stock for a complement's ingredients with retry mechanism.
     * Uses pessimistic locking to prevent concurrent modification issues.
     * 
     * @param complementIngredients List of complement ingredients to return to
     * @param complementQuantity Number of complement portions to return
     */
    public void returnStockForComplement(List<ComplementIngredient> complementIngredients, int complementQuantity) {
        for (ComplementIngredient ci : complementIngredients) {
            BigDecimal quantityToReturn = ci.getQuantity().multiply(BigDecimal.valueOf(complementQuantity));
            Long ingredientId = ci.getIngredient().getIdIngredient();
            
            returnStockWithRetry(ingredientId, quantityToReturn, ci.getUnit());
        }
    }

    /**
     * Deduct stock from an ingredient with retry mechanism.
     * 
     * @param ingredientId The ID of the ingredient to update
     * @param quantityToDeduct The quantity to subtract from stock
     * @param unit The unit of measure (for logging)
     */
    public void deductStockWithRetry(Long ingredientId, BigDecimal quantityToDeduct, String unit) {
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                deductStockInNewTransaction(ingredientId, quantityToDeduct, unit);
                return; // Success
                
            } catch (ObjectOptimisticLockingFailureException | PessimisticLockingFailureException e) {
                attempts++;
                lastException = e;
                log.warn("Lock conflict for complement ingredient {} during deduction, attempt {}/{}. Retrying...", 
                        ingredientId, attempts, MAX_RETRY_ATTEMPTS);
                
                if (attempts >= MAX_RETRY_ATTEMPTS) {
                    log.error("Failed to deduct complement ingredient {} after {} attempts", ingredientId, MAX_RETRY_ATTEMPTS);
                    break;
                }
                
                try {
                    long delay = BASE_RETRY_DELAY_MS * attempts + (long)(Math.random() * 100);
                    log.debug("Waiting {}ms before retry...", delay);
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Operación interrumpida", ie);
                }
            }
        }
        
        throw new IllegalStateException(
            "Error de concurrencia al descontar stock para complemento. Por favor intente de nuevo.", 
            lastException);
    }

    /**
     * Return stock to an ingredient with retry mechanism.
     * 
     * @param ingredientId The ID of the ingredient to update
     * @param quantityToReturn The quantity to add back to stock
     * @param unit The unit of measure (for logging)
     */
    public void returnStockWithRetry(Long ingredientId, BigDecimal quantityToReturn, String unit) {
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                returnStockInNewTransaction(ingredientId, quantityToReturn, unit);
                return; // Success
                
            } catch (ObjectOptimisticLockingFailureException | PessimisticLockingFailureException e) {
                attempts++;
                lastException = e;
                log.warn("Lock conflict for complement ingredient {}, attempt {}/{}. Retrying...",
                        ingredientId, attempts, MAX_RETRY_ATTEMPTS);
                
                if (attempts >= MAX_RETRY_ATTEMPTS) {
                    log.error("Failed to update complement ingredient {} after {} attempts", ingredientId, MAX_RETRY_ATTEMPTS);
                    break;
                }
                
                try {
                    long delay = BASE_RETRY_DELAY_MS * attempts + (long)(Math.random() * 100);
                    log.debug("Waiting {}ms before retry...", delay);
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Operación interrumpida", ie);
                }
            }
        }
        
        throw new IllegalStateException(
            "Error de concurrencia al devolver stock de complemento. Por favor intente de nuevo.", 
            lastException);
    }

    /**
     * Perform the actual stock deduction in a new transaction with pessimistic lock.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deductStockInNewTransaction(Long ingredientId, BigDecimal quantityToDeduct, String unit) {
        // Fetch ingredient with pessimistic lock (SELECT FOR UPDATE)
        Ingredient ingredient = ingredientRepository.findByIdWithLock(ingredientId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Ingrediente no encontrado con ID: " + ingredientId));
        
        BigDecimal currentStock = ingredient.getCurrentStock() != null 
            ? ingredient.getCurrentStock() 
            : BigDecimal.ZERO;

        if (currentStock.compareTo(quantityToDeduct) < 0) {
            throw new IllegalStateException(
                String.format("Stock insuficiente de '%s' para complemento. Requerido: %s %s, Disponible: %s %s",
                              ingredient.getName(),
                              quantityToDeduct.stripTrailingZeros().toPlainString(), unit,
                              currentStock.stripTrailingZeros().toPlainString(), unit));
        }

        BigDecimal newStock = currentStock.subtract(quantityToDeduct);
        ingredient.setCurrentStock(newStock);
        
        ingredientRepository.save(ingredient);

        log.debug("Stock deducted for complement ingredient: {} ({} {}). New stock: {}", 
                 ingredient.getName(),
                 quantityToDeduct.stripTrailingZeros().toPlainString(),
                 unit,
                 newStock.stripTrailingZeros().toPlainString());
    }

    /**
     * Perform the actual stock return in a new transaction with pessimistic lock.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void returnStockInNewTransaction(Long ingredientId, BigDecimal quantityToReturn, String unit) {
        // Fetch ingredient with pessimistic lock (SELECT FOR UPDATE)
        Ingredient ingredient = ingredientRepository.findByIdWithLock(ingredientId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Ingrediente no encontrado con ID: " + ingredientId));
        
        BigDecimal currentStock = ingredient.getCurrentStock() != null 
            ? ingredient.getCurrentStock() 
            : BigDecimal.ZERO;

        BigDecimal newStock = currentStock.add(quantityToReturn);
        
        // If returned stock would exceed maxStock, update maxStock to match
        BigDecimal maxStock = ingredient.getMaxStock();
        if (maxStock != null && newStock.compareTo(maxStock) > 0) {
            log.info("Complement stock return for ingredient '{}': updating maxStock from {} to {} (returned stock exceeds previous max)", 
                     ingredient.getName(), maxStock, newStock);
            ingredient.setMaxStock(newStock);
        }
        
        ingredient.setCurrentStock(newStock);
        
        ingredientRepository.save(ingredient);

        log.debug("Stock returned for complement ingredient: {} ({} {}). New stock: {}", 
                 ingredient.getName(),
                 quantityToReturn.stripTrailingZeros().toPlainString(),
                 unit,
                 newStock.stripTrailingZeros().toPlainString());
    }
}
