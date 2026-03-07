package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.IngredientCategory;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for IngredientCategory management
 */
public interface IngredientCategoryService {

    /**
     * Find all categories ordered by name
     */
    List<IngredientCategory> findAll();

    /**
     * Find all active categories ordered by name
     */
    List<IngredientCategory> findAllActive();

    /**
     * Find category by ID
     */
    Optional<IngredientCategory> findById(Long id);

    /**
     * Create a new category
     */
    IngredientCategory create(IngredientCategory category);

    /**
     * Update an existing category
     */
    IngredientCategory update(Long id, IngredientCategory category);

    /**
     * Soft delete (deactivate) a category
     */
    void delete(Long id);

    /**
     * Activate a category
     */
    void activate(Long id);

    /**
     * Search categories with filters
     */
    List<IngredientCategory> searchWithFilters(String search, Boolean active);

    /**
     * Get active category count
     */
    long getActiveCount();

    /**
     * Get inactive category count
     */
    long getInactiveCount();

    /**
     * Permanently delete an ingredient category from the database.
     * Blocked if the category has ingredients.
     */
    void hardDelete(Long id);

    /**
     * Check dependencies before hard-deleting an IngredientCategory.
     */
    java.util.Map<String, Object> checkHardDeleteDependencies(Long id);
}
