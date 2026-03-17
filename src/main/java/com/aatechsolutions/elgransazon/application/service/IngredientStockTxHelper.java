package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Inner transaction helper for ingredient stock operations.
 *
 * <p>Exists as a separate Spring bean so that the {@code REQUIRES_NEW}
 * transactions actually propagate through the AOP proxy instead of being
 * bypassed by a self-call inside {@link IngredientStockService}.</p>
 *
 * <p>Each public method runs in its own isolated transaction:
 * it acquires a {@code SELECT FOR UPDATE} (pessimistic write) lock on the
 * ingredient row, applies the change, and commits immediately.  The short
 * lock window serialises concurrent deductions without keeping the outer
 * {@code create()} transaction's Hibernate session aware of the modified
 * row, which eliminates the {@code @Version} optimistic-lock conflicts.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngredientStockTxHelper {

    private final IngredientRepository ingredientRepository;

    /**
     * Deduct {@code quantityToDeduct} from ingredient stock inside a fresh
     * REQUIRES_NEW transaction with a pessimistic write lock.
     *
     * @throws IllegalArgumentException if the ingredient is not found
     * @throws IllegalStateException    if current stock is insufficient
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deduct(Long ingredientId, BigDecimal quantityToDeduct, String unit) {
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

        ingredient.setCurrentStock(currentStock.subtract(quantityToDeduct));
        ingredientRepository.save(ingredient);

        log.debug("Stock deducted: {} -{} {}. Remaining: {}",
                ingredient.getName(),
                quantityToDeduct.stripTrailingZeros().toPlainString(), unit,
                ingredient.getCurrentStock().stripTrailingZeros().toPlainString());
    }

    /**
     * Return {@code quantityToReturn} to ingredient stock inside a fresh
     * REQUIRES_NEW transaction with a pessimistic write lock.
     *
     * @throws IllegalArgumentException if the ingredient is not found
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void returnStock(Long ingredientId, BigDecimal quantityToReturn, String unit) {
        Ingredient ingredient = ingredientRepository.findByIdWithLock(ingredientId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ingrediente no encontrado con ID: " + ingredientId));

        BigDecimal currentStock = ingredient.getCurrentStock() != null
                ? ingredient.getCurrentStock()
                : BigDecimal.ZERO;

        BigDecimal newStock = currentStock.add(quantityToReturn);

        BigDecimal maxStock = ingredient.getMaxStock();
        if (maxStock != null && newStock.compareTo(maxStock) > 0) {
            ingredient.setMaxStock(newStock);
        }

        ingredient.setCurrentStock(newStock);
        ingredientRepository.save(ingredient);

        log.debug("Stock returned: {} +{} {}. New total: {}",
                ingredient.getName(),
                quantityToReturn.stripTrailingZeros().toPlainString(), unit,
                newStock.stripTrailingZeros().toPlainString());
    }
}
