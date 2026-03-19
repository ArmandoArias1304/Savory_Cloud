package com.aatechsolutions.elgransazon.application.service;

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
 *
 * <p>Delegates the actual DB work to {@link IngredientStockTxHelper} which runs
 * each operation in its own {@code REQUIRES_NEW} transaction.  This avoids the
 * proxy self-call problem that would occur if the {@code @Transactional(REQUIRES_NEW)}
 * methods lived in this same class.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngredientStockService {

    private final IngredientStockTxHelper txHelper;

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long BASE_RETRY_DELAY_MS = 100;

    /**
     * Return stock for an ingredient with retry mechanism.
     * Delegates to {@link IngredientStockTxHelper#returnStock} which runs in its own
     * REQUIRES_NEW transaction with a PESSIMISTIC_WRITE lock.
     *
     * @param ingredientId    The ID of the ingredient to update
     * @param quantityToReturn The quantity to add back to stock
     * @param unit            The unit of measure (for logging)
     */
    public void returnStockWithRetry(Long ingredientId, BigDecimal quantityToReturn, String unit) {
        retry(() -> txHelper.returnStock(ingredientId, quantityToReturn, unit),
              "return stock for ingredient " + ingredientId);
    }

    /**
     * @deprecated Use {@link #returnStockWithRetry} instead.
     * Kept for backward compatibility; delegates to the TxHelper.
     */
    @Deprecated
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void returnStockInNewTransaction(Long ingredientId, BigDecimal quantityToReturn, String unit) {
        txHelper.returnStock(ingredientId, quantityToReturn, unit);
    }

    /**
     * Deduct stock from an ingredient with retry mechanism.
     * Delegates to {@link IngredientStockTxHelper#deduct} which runs in its own
     * REQUIRES_NEW transaction with a PESSIMISTIC_WRITE lock.
     *
     * @param ingredientId    The ID of the ingredient to update
     * @param quantityToDeduct The quantity to subtract from stock
     * @param unit            The unit of measure (for logging)
     * @throws IllegalStateException if stock is insufficient after acquiring the lock
     */
    public void deductStockWithRetry(Long ingredientId, BigDecimal quantityToDeduct, String unit) {
        retry(() -> txHelper.deduct(ingredientId, quantityToDeduct, unit),
              "deduct stock for ingredient " + ingredientId);
    }

    /**
     * @deprecated Use {@link #deductStockWithRetry} instead.
     * Kept for backward compatibility; delegates to the TxHelper.
     */
    @Deprecated
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deductStockInNewTransaction(Long ingredientId, BigDecimal quantityToDeduct, String unit) {
        txHelper.deduct(ingredientId, quantityToDeduct, unit);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Retry {@code action} up to {@link #MAX_RETRY_ATTEMPTS} times when a
     * pessimistic or optimistic lock conflict occurs.  Re-throws the last
     * conflict exception wrapped in an {@link IllegalStateException} if all
     * attempts are exhausted.
     *
     * <p>{@link IllegalStateException}s caused by business logic (e.g. "stock
     * insuficiente") are NOT retried — they propagate immediately.</p>
     */
    private void retry(Runnable action, String description) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                action.run();
                return; // success
            } catch (ObjectOptimisticLockingFailureException | PessimisticLockingFailureException e) {
                lastException = e;
                log.warn("Lock conflict on {}, attempt {}/{}", description, attempt, MAX_RETRY_ATTEMPTS);

                if (attempt == MAX_RETRY_ATTEMPTS) break;

                try {
                    long delay = BASE_RETRY_DELAY_MS * attempt + (long) (Math.random() * 50);
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Operación interrumpida", ie);
                }
            }
            // IllegalStateException (business errors like insufficient stock) propagates immediately
        }

        throw new IllegalStateException(
                "Error de concurrencia al actualizar el stock del ingrediente. Por favor intente de nuevo.",
                lastException);
    }
}
