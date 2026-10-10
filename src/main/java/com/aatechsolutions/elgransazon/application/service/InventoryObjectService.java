package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.InventoryObject;
import com.aatechsolutions.elgransazon.domain.entity.ObjectCondition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for InventoryObject management (restaurant assets: tables,
 * televisions, pots, remotes, etc.)
 */
public interface InventoryObjectService {

    /**
     * Search objects of the current company with optional filters
     */
    List<InventoryObject> searchWithFilters(String search, Long categoryId, ObjectCondition condition,
                                            String location, Boolean active);

    /**
     * Find an object of the current company by ID
     */
    Optional<InventoryObject> findById(Long id);

    /**
     * Create a new object for the current company
     */
    InventoryObject create(InventoryObject object);

    /**
     * Update an existing object of the current company
     */
    InventoryObject update(Long id, InventoryObject objectDetails);

    /**
     * Soft delete (deactivate) an object
     */
    void delete(Long id);

    /**
     * Activate an object
     */
    void activate(Long id);

    /**
     * Permanently delete an object (ADMIN only at controller level)
     */
    void hardDelete(Long id);

    /**
     * Total number of objects (active + inactive) of the current company
     */
    long getTotalCount();

    /**
     * Active objects of the current company
     */
    long getActiveCount();

    /**
     * Inactive (soft deleted) objects of the current company
     */
    long getInactiveCount();

    /**
     * Objects reported as damaged of the current company
     */
    long getDamagedCount();

    /**
     * Sum of quantity * purchase cost of every object of the current company
     */
    BigDecimal getInventoryValue();
}
