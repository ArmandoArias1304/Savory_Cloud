package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.InventoryObjectCategory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service interface for InventoryObjectCategory management
 */
public interface InventoryObjectCategoryService {

    /**
     * Find all categories of the current company ordered by name
     */
    List<InventoryObjectCategory> findAll();

    /**
     * Find all active categories of the current company ordered by name
     */
    List<InventoryObjectCategory> findAllActive();

    /**
     * Find a category of the current company by ID
     */
    Optional<InventoryObjectCategory> findById(Long id);

    /**
     * Create a new category for the current company
     */
    InventoryObjectCategory create(InventoryObjectCategory category);

    /**
     * Update an existing category of the current company
     */
    InventoryObjectCategory update(Long id, InventoryObjectCategory category);

    /**
     * Soft delete (deactivate) a category
     */
    void delete(Long id);

    /**
     * Activate a category
     */
    void activate(Long id);

    /**
     * Search categories of the current company with filters
     */
    List<InventoryObjectCategory> searchWithFilters(String search, Boolean active);

    /**
     * Get active category count of the current company
     */
    long getActiveCount();

    /**
     * Get inactive category count of the current company
     */
    long getInactiveCount();

    /**
     * Permanently delete a category. Blocked if the category still has objects.
     */
    void hardDelete(Long id);

    /**
     * Check dependencies before hard-deleting a category
     */
    Map<String, Object> checkHardDeleteDependencies(Long id);
}
