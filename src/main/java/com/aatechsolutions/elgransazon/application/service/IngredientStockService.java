package com.aatechsolutions.elgransazon.application.service;

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

/**
 * Service for managing ingredient stock operations with proper transaction handling.
 * Uses PESSIMISTIC_WRITE lock to handle concurrent stock updates.
 * This ensures only one transaction can modify an ingredient at a time.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngredientStockService {

    private final IngredientRepository ingredientRepository;
    
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long BASE_RETRY_DELAY_MS = 100;

    /**
     * Return stock for an ingredient with retry mechanism.
     * Uses pessimistic locking to prevent concurrent modification issues.
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
                // Each attempt is in its own transaction with pessimistic lock
                returnStockInNewTransaction(ingredientId, quantityToReturn, unit);
                return; // Success - exit the retry loop
                
} catch (ObjectOptimisticLockingFailureException | PessimisticLockingFailureException e) {
                attempts++;
                lastException = e;
                log.warn("Lock conflict for ingredient {}, attempt {}/{}. Retrying...",
                        ingredientId, attempts, MAX_RETRY_ATTEMPTS);
                
                if (attempts >= MAX_RETRY_ATTEMPTS) {
                    log.error("Failed to update ingredient {} after {} attempts", ingredientId, MAX_RETRY_ATTEMPTS);
                    break;
                }
                
                // Exponential backoff with jitter to reduce contention
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
            "Error de concurrencia al actualizar el stock del ingrediente. Por favor intente de nuevo.", 
            lastException);
    }

    /**
     * Perform the actual stock return in a new transaction with pessimistic lock.
     * REQUIRES_NEW ensures this runs in an independent transaction.
     * PESSIMISTIC_WRITE lock ensures exclusive access to the ingredient.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void returnStockInNewTransaction(Long ingredientId, BigDecimal quantityToReturn, String unit) {
        // Fetch ingredient with pessimistic lock (SELECT FOR UPDATE)
        // This blocks other transactions until this one completes
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
            log.info("Stock return for ingredient '{}': updating maxStock from {} to {} (returned stock exceeds previous max)", 
                     ingredient.getName(), maxStock, newStock);
            ingredient.setMaxStock(newStock);
        }
        
        // Update current stock
        ingredient.setCurrentStock(newStock);
        
        // Save - the lock is held until transaction commits
        ingredientRepository.save(ingredient);

        log.debug("Stock returned for ingredient: {} ({} {}). New stock: {}", 
                 ingredient.getName(),
                 quantityToReturn.stripTrailingZeros().toPlainString(),
                 unit,
                 newStock.stripTrailingZeros().toPlainString());
    }

    /**
     * Deduct stock from an ingredient with retry mechanism.
     * Uses pessimistic locking to prevent concurrent modification issues.
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
                log.warn("Lock conflict for ingredient {} during deduction, attempt {}/{}. Retrying...", 
                        ingredientId, attempts, MAX_RETRY_ATTEMPTS);
                
                if (attempts >= MAX_RETRY_ATTEMPTS) {
                    log.error("Failed to deduct from ingredient {} after {} attempts", ingredientId, MAX_RETRY_ATTEMPTS);
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
            "Error de concurrencia al actualizar el stock del ingrediente. Por favor intente de nuevo.", 
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
                String.format("Stock insuficiente de '%s'. Requerido: %s %s, Disponible: %s %s",
                              ingredient.getName(),
                              quantityToDeduct.stripTrailingZeros().toPlainString(), unit,
                              currentStock.stripTrailingZeros().toPlainString(), unit));
        }

        BigDecimal newStock = currentStock.subtract(quantityToDeduct);
        ingredient.setCurrentStock(newStock);
        
        // Save - the lock is held until transaction commits
        ingredientRepository.save(ingredient);

        log.debug("Stock deducted for ingredient: {} ({} {}). New stock: {}", 
                 ingredient.getName(),
                 quantityToDeduct.stripTrailingZeros().toPlainString(),
                 unit,
                 newStock.stripTrailingZeros().toPlainString());
    }
}
