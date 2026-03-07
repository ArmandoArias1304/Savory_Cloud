package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Category;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for Category business logic
 */
public interface CategoryService {

    /**
     * Get all categories ordered by display order
     * @return List of all categories
     */
    List<Category> getAllCategories();

    /**
     * Get all active categories ordered by display order
     * @return List of active categories
     */
    List<Category> getAllActiveCategories();

    /**
     * Get a category by its ID
     * @param id the category ID
     * @return Optional containing the category if found
     */
    Optional<Category> getCategoryById(Long id);

    /**
     * Get a category by its name
     * @param name the category name
     * @return Optional containing the category if found
     */
    Optional<Category> getCategoryByName(String name);

    /**
     * Create a new category
     * @param category the category to create
     * @return the created category
     * @throws IllegalArgumentException if category name already exists
     */
    Category createCategory(Category category);

    /**
     * Update an existing category
     * @param id the ID of the category to update
     * @param category the updated category data
     * @return the updated category
     * @throws IllegalArgumentException if category not found or name already exists
     */
    Category updateCategory(Long id, Category category);

    /**
     * Delete a category (soft delete by setting active to false)
     * @param id the ID of the category to delete
     * @throws IllegalArgumentException if category not found
     */
    void deleteCategory(Long id);

    /**
     * Hard delete a category from database
     * @param id the ID of the category to permanently delete
     * @throws IllegalArgumentException if category not found
     */
    void permanentlyDeleteCategory(Long id);

    /**
     * Activate a category
     * @param id the ID of the category to activate
     * @throws IllegalArgumentException if category not found
     */
    void activateCategory(Long id);

    /**
     * Check if a category name already exists
     * @param name the category name to check
     * @return true if exists, false otherwise
     */
    boolean categoryNameExists(String name);

    /**
     * Get count of active categories
     * @return number of active categories
     */
    long countActiveCategories();

    /**
     * Check dependencies before hard-deleting a Category.
     */
    java.util.Map<String, Object> checkHardDeleteDependencies(Long id);
}
