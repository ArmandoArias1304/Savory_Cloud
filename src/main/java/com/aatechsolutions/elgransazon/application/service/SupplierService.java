package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Supplier;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for Supplier management
 */
public interface SupplierService {

    /**
     * Find all suppliers ordered by name
     */
    List<Supplier> findAll();

    /**
     * Find supplier by ID
     */
    Optional<Supplier> findById(Long id);

    /**
     * Create a new supplier
     */
    Supplier create(Supplier supplier);

    /**
     * Update an existing supplier
     */
    Supplier update(Long id, Supplier supplier);

    /**
     * Soft delete (deactivate) a supplier
     */
    void delete(Long id);

    /**
     * Activate a supplier
     */
    void activate(Long id);

    /**
     * Search suppliers with filters
     */
    List<Supplier> searchWithFilters(String search, Integer rating, Long categoryId, Boolean active);

    /**
     * Find suppliers by category ID
     */
    List<Supplier> findByCategoryId(Long categoryId);

    /**
     * Get active supplier count
     */
    long getActiveCount();

    /**
     * Get inactive supplier count
     */
    long getInactiveCount();

    /**
     * Find all active suppliers ordered by name
     */
    List<Supplier> findAllActive();

    /**
     * Permanently delete a supplier from the database
     * This will remove all category associations and delete the supplier record
     */
    void permanentDelete(Long id);
}
