package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.ItemIngredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenuComboItem;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for ItemMenu management
 */
public interface ItemMenuService {

    /**
     * Find all menu items
     */
    List<ItemMenu> findAll();

    /**
     * Find all menu items ordered by name
     */
    List<ItemMenu> findAllOrderByName();

    /**
     * Find all menu items ordered by category and name
     */
    List<ItemMenu> findAllOrderByCategoryAndName();

    /**
     * Find menu item by ID
     */
    Optional<ItemMenu> findById(Long id);

    /**
     * Find menu item by name
     */
    Optional<ItemMenu> findByName(String name);

    /**
     * Find all menu items by category ID
     */
    List<ItemMenu> findByCategoryId(Long categoryId);

    /**
     * Find all active menu items by category ID
     */
    List<ItemMenu> findActiveByCategoryId(Long categoryId);

    /**
     * Find all active menu items
     */
    List<ItemMenu> findAllActive();

    /**
     * Find all available menu items (active and with enough stock)
     */
    List<ItemMenu> findAvailableItems();

    /**
     * Find items with low stock (active but unavailable)
     */
    List<ItemMenu> findItemsWithLowStock();

    /**
     * Search menu items by name
     */
    List<ItemMenu> searchByName(String searchTerm);

    /**
     * Find menu items by category and availability
     */
    List<ItemMenu> findByCategoryIdAndAvailability(Long categoryId, Boolean available);

    /**
     * Create a new menu item with its recipe
     */
    ItemMenu create(ItemMenu item, List<ItemIngredient> recipe);

    /**
     * Update an existing menu item and its recipe
     */
    ItemMenu update(Long id, ItemMenu item, List<ItemIngredient> recipe);

    /**
     * Activate a menu item
     */
    ItemMenu activate(Long id);

    /**
     * Deactivate a menu item
     */
    ItemMenu deactivate(Long id);

    /**
     * Deactivate multiple menu items by their IDs
     */
    void deactivateMultiple(List<Long> ids);

    /**
     * Delete a menu item (and its recipe)
     */
    void delete(Long id);

    /**
     * Find all menu items that use a specific ingredient
     */
    List<ItemMenu> findByIngredientId(Long ingredientId);

    // ========== Recipe Management ==========

    /**
     * Get all ingredients for a menu item (recipe)
     */
    List<ItemIngredient> getRecipe(Long itemMenuId);

    /**
     * Add an ingredient to a menu item's recipe
     */
    ItemIngredient addIngredientToRecipe(Long itemMenuId, ItemIngredient ingredient);

    /**
     * Remove an ingredient from a menu item's recipe
     */
    void removeIngredientFromRecipe(Long itemMenuId, Long ingredientId);

    /**
     * Update the entire recipe of a menu item
     */
    void updateRecipe(Long itemMenuId, List<ItemIngredient> newRecipe);

    /**
     * Clear all ingredients from a menu item's recipe
     */
    void clearRecipe(Long itemMenuId);

    // ========== Stock & Availability Management ==========

    /**
     * Check if there's enough stock to prepare a quantity of items
     */
    boolean hasEnoughStock(Long itemMenuId, int quantity);

    /**
     * Get the maximum quantity of an item that can be prepared
     * based on current ingredient stock levels
     */
    int getMaxAvailableQuantity(Long itemMenuId);

    /**
     * Update the availability of a menu item based on current stock
     */
    void updateItemAvailability(Long itemMenuId);

    /**
     * Update availability for all active menu items
     */
    void updateAllItemsAvailability();

    // ========== Sales Methods (Ready but not used yet) ==========

    /**
     * Sell a menu item - deducts ingredients from stock
     * THIS METHOD IS READY BUT NOT USED YET - For future sales module
     */
    void sellItem(Long itemMenuId, int quantity);

    /**
     * Calculate the total cost of ingredients for a menu item
     */
    java.math.BigDecimal calculateIngredientsCost(Long itemMenuId);

    // ========== Statistics ==========

    /**
     * Count all menu items
     */
    long countAll();

    /**
     * Count active menu items
     */
    long countActive();

    /**
     * Count available menu items
     */
    long countAvailable();

    /**
     * Count unavailable menu items (active but no stock)
     */
    long countUnavailable();

    /**
     * Count menu items by category
     */
    long countByCategoryId(Long categoryId);

    /**
     * Count active menu items by category
     */
    long countActiveByCategoryId(Long categoryId);

    /**
     * Count available menu items by category
     */
    long countAvailableByCategoryId(Long categoryId);

    /**
     * Check if menu item exists by name
     */
    boolean existsByName(String name);

    /**
     * Check if menu item exists by name excluding a specific id
     */
    boolean existsByNameAndIdNot(String name, Long excludeId);

    /**
     * Find menu item by ID or throw exception
     */
    ItemMenu findByIdOrThrow(Long id);

    // ========== Availability Schedule Management ==========

    /**
     * Check if a menu item is available at a specific day and time.
     * This validates against both the item's availability schedule and business hours.
     * @param itemMenuId The menu item ID
     * @param day The day to check
     * @param time The time to check
     * @return true if the item is available at that day and time
     */
    boolean isItemAvailableAt(Long itemMenuId, com.aatechsolutions.elgransazon.domain.entity.DayOfWeek day, java.time.LocalTime time);

    /**
     * Check if a menu item is currently available (now).
     * @param itemMenuId The menu item ID
     * @return true if the item is available right now
     */
    boolean isItemAvailableNow(Long itemMenuId);

    /**
     * Get all days when a menu item is available
     * @param itemMenuId The menu item ID
     * @return List of available days
     */
    java.util.List<com.aatechsolutions.elgransazon.domain.entity.DayOfWeek> getAvailableDays(Long itemMenuId);

    /**
     * Update the availability schedule for a menu item with custom per-day times
     * Each day can have its own specific start and end time
     * @param itemMenuId The menu item ID
     * @param daySchedules Map of day to [startTime, endTime] arrays
     */
    void updateAvailabilityScheduleManual(Long itemMenuId, java.util.Map<com.aatechsolutions.elgransazon.domain.entity.DayOfWeek, java.time.LocalTime[]> daySchedules);

    /**
     * Clear all availability schedule for a menu item (no custom schedule = available all day)
     * @param itemMenuId The menu item ID
     */
    void clearAvailabilitySchedule(Long itemMenuId);

    /**
     * Set default availability schedule for a new menu item based on business hours
     * @param item The menu item to set defaults for
     */
    void setDefaultAvailabilitySchedule(ItemMenu item);

    /**
     * Find all menu items available at a specific day and time
     * @param day The day to check
     * @param time The time to check
     * @return List of available menu items
     */
    java.util.List<ItemMenu> findAvailableItemsAt(com.aatechsolutions.elgransazon.domain.entity.DayOfWeek day, java.time.LocalTime time);

    /**
     * Find all menu items currently available (now)
     * Considers both stock availability and schedule availability
     * @return List of currently available items
     */
    java.util.List<ItemMenu> findCurrentlyAvailableItems();

    // ========== Combo Management ==========

    /**
     * Get all combo items (children) for a combo ItemMenu
     */
    java.util.List<ItemMenuComboItem> getComboItems(Long comboMenuId);

    /**
     * Update the combo items for a combo ItemMenu
     */
    void updateComboItems(Long comboMenuId, java.util.List<Long> childItemIds, java.util.List<Integer> quantities);

    /**
     * Clear all combo items from a combo ItemMenu
     */
    void clearComboItems(Long comboMenuId);

    /**
     * Validate that an item's availability schedule is within business hours
     * @param availableDays Days to validate
     * @param startTime Start time to validate
     * @param endTime End time to validate
     * @throws IllegalArgumentException if schedule is outside business hours
     */
    void validateAvailabilityWithinBusinessHours(java.util.List<com.aatechsolutions.elgransazon.domain.entity.DayOfWeek> availableDays,
                                                  java.time.LocalTime startTime, java.time.LocalTime endTime);

    /**
     * Permanently delete a menu item from the database.
     * Blocked if the item has order history.
     */
    java.util.Map<String, Object> hardDelete(Long id);

    /**
     * Check dependencies before hard-deleting an ItemMenu.
     */
    java.util.Map<String, Object> checkHardDeleteDependencies(Long id);
}
