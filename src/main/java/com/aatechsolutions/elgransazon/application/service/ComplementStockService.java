package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.ComplementIngredient;
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
 *
 * <p>Delegates the actual DB work to {@link IngredientStockTxHelper} which runs
 * each operation in its own {@code REQUIRES_NEW} transaction with a
 * {@code PESSIMISTIC_WRITE} lock.  This avoids the proxy self-call problem that
 * would occur if the {@code @Transactional(REQUIRES_NEW)} methods lived in this
 * same class.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplementStockService {

    private final IngredientStockTxHelper txHelper;

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long BASE_RETRY_DELAY_MS = 100;

    /**
     * Deduct stock for a complement's ingredients with retry mechanism.
     *
     * @param complementIngredients List of complement ingredients to deduct from
     * @param complementQuantity    Number of complement portions to deduct
     * @throws IllegalStateException if any ingredient has insufficient stock
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
     *
     * @param complementIngredients List of complement ingredients to return to
     * @param complementQuantity    Number of complement portions to return
     */
    public void returnStockForComplement(List<ComplementIngredient> complementIngredients, int complementQuantity) {
        for (ComplementIngredient ci : complementIngredients) {
            BigDecimal quantityToReturn = ci.getQuantity().multiply(BigDecimal.valueOf(complementQuantity));
            Long ingredientId = ci.getIngredient().getIdIngredient();
            returnStockWithRetry(ingredientId, quantityToReturn, ci.getUnit());
        }
    }

    /**
     * Deduct stock from an ingredient with retry on lock conflicts.
     * Delegates to {@link IngredientStockTxHelper#deduct} (REQUIRES_NEW + SELECT FOR UPDATE).
     *
     * @throws IllegalStateException if stock is insufficient (propagates immediately, no retry)
     */
    public void deductStockWithRetry(Long ingredientId, BigDecimal quantityToDeduct, String unit) {
        retry(() -> txHelper.deduct(ingredientId, quantityToDeduct, unit),
              "deduct complement stock for ingredient " + ingredientId,
              "Error de concurrencia al descontar stock para complemento. Por favor intente de nuevo.");
    }

    /**
     * Return stock to an ingredient with retry on lock conflicts.
     * Delegates to {@link IngredientStockTxHelper#returnStock} (REQUIRES_NEW + SELECT FOR UPDATE).
     */
    public void returnStockWithRetry(Long ingredientId, BigDecimal quantityToReturn, String unit) {
        retry(() -> txHelper.returnStock(ingredientId, quantityToReturn, unit),
              "return complement stock for ingredient " + ingredientId,
              "Error de concurrencia al devolver stock de complemento. Por favor intente de nuevo.");
    }

    // -----------------------------------------------------------------------
    // Deprecated delegating wrappers (kept for backward compatibility)
    // -----------------------------------------------------------------------

    /** @deprecated Delegate to {@link #deductStockWithRetry} instead. */
    @Deprecated
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deductStockInNewTransaction(Long ingredientId, BigDecimal quantityToDeduct, String unit) {
        txHelper.deduct(ingredientId, quantityToDeduct, unit);
    }

    /** @deprecated Delegate to {@link #returnStockWithRetry} instead. */
    @Deprecated
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void returnStockInNewTransaction(Long ingredientId, BigDecimal quantityToReturn, String unit) {
        txHelper.returnStock(ingredientId, quantityToReturn, unit);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Retry {@code action} up to {@link #MAX_RETRY_ATTEMPTS} times when a lock
     * conflict occurs.  Business-logic {@link IllegalStateException}s propagate
     * immediately without retry.
     */
    private void retry(Runnable action, String description, String concurrencyMessage) {
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
            // IllegalStateException (e.g. "stock insuficiente") propagates immediately
        }

        throw new IllegalStateException(concurrencyMessage, lastException);
    }
}

