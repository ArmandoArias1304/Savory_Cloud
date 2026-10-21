package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.ItemIngredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.ItemPreparation;
import com.aatechsolutions.elgransazon.domain.entity.PreparationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Internal production (buffet dishes, staff meals).
 *
 * <p>These items are never sold: they have a recipe and are produced in batches. Registering a
 * batch discounts the recipe from stock; cancelling it returns the exact same amounts once.</p>
 */
public interface ItemPreparationService {

    /** Internal items of the current company, ordered by name. */
    List<ItemMenu> findInternalItems();

    /** Internal item of the current company, or empty when it does not exist / is not internal. */
    java.util.Optional<ItemMenu> findInternalItem(Long itemMenuId);

    /** Create an internal item with its recipe. */
    ItemMenu createInternalItem(ItemMenu item, List<ItemIngredient> recipe);

    /** Update an internal item and replace its recipe. */
    ItemMenu updateInternalItem(Long itemMenuId, ItemMenu item, List<ItemIngredient> recipe);

    /** Most recent batches of the current company, newest first. */
    List<ItemPreparation> findRecentPreparations(int limit);

    /**
     * Recent batches of the current company filtered by text and/or status, newest first.
     *
     * @param search dish name or employee who registered it; blank or {@code null} means all
     * @param status {@link PreparationStatus} to keep; {@code null} means both
     */
    List<ItemPreparation> searchPreparations(String search, PreparationStatus status, int limit);

    /** Batches produced today (company timezone, both ends inclusive). */
    List<ItemPreparation> findTodayPreparations();

    /**
     * Batches produced inside a local-date range (company timezone, both ends inclusive), used by
     * the downloadable reports to count what was cooked in the same period as the sales.
     *
     * <p>A missing bound means "no limit on that side"; when both are {@code null} every batch of
     * the company is returned. A single bound is treated as that one day, mirroring how the sales
     * reports behave when only {@code startDate} arrives.</p>
     */
    List<ItemPreparation> findPreparationsForReport(LocalDate from, LocalDate to);

    /**
     * Register a produced batch: discounts {@code quantity × recipe} from stock.
     *
     * @param itemMenuId internal item
     * @param quantity   portions produced (must be >= 1)
     * @param employee   who produced it
     * @return the saved batch
     * @throws IllegalArgumentException when the item does not exist or is not internal
     * @throws IllegalStateException    when the recipe is missing or stock is insufficient
     */
    ItemPreparation registerPreparation(Long itemMenuId, int quantity, Employee employee);

    /**
     * Cancel a batch and return its ingredients to stock, exactly once.
     *
     * @throws IllegalStateException when the batch was already cancelled
     */
    ItemPreparation cancelPreparation(Long preparationId, Employee employee, String reason);

    /** Sum of the estimated ingredient cost of the given batches (cancelled ones count zero). */
    BigDecimal totalEstimatedCost(Collection<ItemPreparation> preparations);

    /** Sum of the portions that still count as produced (cancelled batches count zero). */
    int totalProducedQuantity(Collection<ItemPreparation> preparations);
}
