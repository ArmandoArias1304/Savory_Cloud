package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.*;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import java.util.ArrayList;
import java.util.HashMap;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of ItemMenuService
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ItemMenuServiceImpl implements ItemMenuService {

    private final ItemMenuRepository itemMenuRepository;
    private final ItemIngredientRepository itemIngredientRepository;
    private final IngredientRepository ingredientRepository;
    private final CategoryRepository categoryRepository;
    private final ItemMenuAvailabilityRepository itemMenuAvailabilityRepository;
    private final BusinessHoursService businessHoursService;
    private final ItemMenuComboItemRepository itemMenuComboItemRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final DateTimeService dateTimeService;
    private final com.aatechsolutions.elgransazon.domain.repository.ItemMenuComplementRepository itemMenuComplementRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Validates that minSauces does not exceed maxSauces (when both are defined and > 0).
     * Throws IllegalArgumentException if the range is inconsistent.
     */
    private void validateSaucesRange(Integer minSauces, Integer maxSauces) {
        if (minSauces != null && maxSauces != null
                && minSauces > 0 && maxSauces > 0
                && minSauces > maxSauces) {
            throw new IllegalArgumentException(
                "El número mínimo de salsas (" + minSauces + ") no puede ser mayor al máximo (" + maxSauces + ").");
        }
    }

    private void validateSpecialitiesRange(Integer minSpecialities, Integer maxSpecialities) {
        if (minSpecialities != null && maxSpecialities != null
                && minSpecialities > 0 && maxSpecialities > 0
                && minSpecialities > maxSpecialities) {
            throw new IllegalArgumentException(
                "El número mínimo de especialidades (" + minSpecialities + ") no puede ser mayor al máximo (" + maxSpecialities + ").");
        }
    }

    @Override
    public List<ItemMenu> findAll() {
        log.debug("Fetching all menu items");
        Company company = CompanyContext.requireCurrentCompany();
        return itemMenuRepository.findByCompany(company);
    }

    @Override
    public List<ItemMenu> findAllOrderByName() {
        log.debug("Fetching all menu items ordered by name");
        Company company = CompanyContext.requireCurrentCompany();
        return itemMenuRepository.findAllByCompanyOrderByName(company);
    }

    @Override
    @Transactional
    public List<ItemMenu> findAllOrderByCategoryAndName() {
        log.debug("Fetching all menu items ordered by category and name");
        Company company = CompanyContext.requireCurrentCompany();
        List<ItemMenu> items = itemMenuRepository.findAllByCompanyOrderByCategoryAndNameWithParent(company);
        
        // Update availability for all items based on current ingredient stock
        for (ItemMenu item : items) {
            item.updateAvailability();
        }
        
        // Save updated availability status
        itemMenuRepository.saveAll(items);
        
        return items;
    }

    @Override
    public Optional<ItemMenu> findById(Long id) {
        log.debug("Finding menu item by ID: {}", id);
        Company company = CompanyContext.requireCurrentCompany();
        return itemMenuRepository.findByIdItemMenuAndCompany(id, company);
    }

    @Override
    public Optional<ItemMenu> findByName(String name) {
        log.debug("Finding menu item by name: {}", name);
        Company company = CompanyContext.requireCurrentCompany();
        return itemMenuRepository.findByNameAndCompany(name, company);
    }

    @Override
    public List<ItemMenu> findByCategoryId(Long categoryId) {
        log.debug("Finding menu items by category ID: {}", categoryId);
        Company company = CompanyContext.requireCurrentCompany();
        return itemMenuRepository.findByCategoryIdAndCompany(categoryId, company);
    }

    @Override
    public List<ItemMenu> findActiveByCategoryId(Long categoryId) {
        log.debug("Finding active menu items by category ID: {}", categoryId);
        Company company = CompanyContext.requireCurrentCompany();
        return itemMenuRepository.findActiveByCategoryIdAndCompany(categoryId, company);
    }

    @Override
    public List<ItemMenu> findAllActive() {
        log.debug("Finding all active menu items");
        Company company = CompanyContext.requireCurrentCompany();
        return itemMenuRepository.findByActiveTrueAndCompany(company);
    }

    @Override
    public List<ItemMenu> findAvailableItems() {
        log.debug("Finding all available menu items (including out of stock)");
        // Changed: Now returns ALL active items, regardless of stock
        // Frontend will show "AGOTADO" badge for items without stock
        Company company = CompanyContext.requireCurrentCompany();
        return itemMenuRepository.findByActiveTrueAndCompany(company);
    }

    @Override
    public List<ItemMenu> findItemsWithLowStock() {
        log.debug("Finding menu items with low stock");
        return itemMenuRepository.findItemsWithLowStock();
    }

    @Override
    public List<ItemMenu> findByIngredientId(Long ingredientId) {
        log.debug("Finding menu items that use ingredient ID: {}", ingredientId);
        List<ItemIngredient> itemIngredients = itemIngredientRepository.findByIngredientId(ingredientId);
        return itemIngredients.stream()
                .map(ItemIngredient::getItemMenu)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<ItemMenu> searchByName(String searchTerm) {
        log.debug("Searching menu items by name: {}", searchTerm);
        return itemMenuRepository.searchByName(searchTerm);
    }

    @Override
    public List<ItemMenu> findByCategoryIdAndAvailability(Long categoryId, Boolean available) {
        log.debug("Finding menu items by category {} and availability {}", categoryId, available);
        return itemMenuRepository.findByCategoryIdAndAvailability(categoryId, available);
    }

    @Override
    @Transactional
    public ItemMenu create(ItemMenu item, List<ItemIngredient> recipe) {
        log.info("Creating new menu item: {}", item.getName());
        log.info("🔍 requiresPreparation value before save: {}", item.getRequiresPreparation());

        // Set company from context
        Company company = CompanyContext.requireCurrentCompany();
        item.setCompany(company);

        // Validate unique name for this company
        if (itemMenuRepository.existsByNameAndCompany(item.getName(), company)) {
            throw new IllegalArgumentException("Ya existe un item del menú con el nombre: " + item.getName());
        }

        // Validate and load category
        if (item.getCategory() == null || item.getCategory().getIdCategory() == null) {
            throw new IllegalArgumentException("La categoría es requerida");
        }
        Category category = categoryRepository.findById(item.getCategory().getIdCategory())
                .orElseThrow(() -> new IllegalArgumentException("La categoría especificada no existe"));

        // Validate category is active
        if (!Boolean.TRUE.equals(category.getActive())) {
            throw new IllegalArgumentException("No se puede crear un item en la categoría '" + category.getName() + "' porque está desactivada. Por favor, active la categoría primero.");
        }

        // Set relationships
        item.setCategory(category);

        // Set timestamps
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());

        // Set defaults
        if (item.getActive() == null) {
            item.setActive(true);
        }
        if (item.getAvailable() == null) {
            item.setAvailable(true);
        }

        // Validate sauces range (min cannot exceed max)
        validateSaucesRange(item.getMinSauces(), item.getMaxSauces());
        validateSpecialitiesRange(item.getMinSpecialities(), item.getMaxSpecialities());

        // Save the item first
        ItemMenu saved = itemMenuRepository.save(item);
        log.info("Menu item created with ID: {}", saved.getIdItemMenu());
        log.info("🔍 requiresPreparation value after save: {}", saved.getRequiresPreparation());

        // Save the recipe if provided
        if (recipe != null && !recipe.isEmpty()) {
            log.info("Saving recipe with {} ingredients", recipe.size());
            for (ItemIngredient ingredient : recipe) {
                // Validate ingredient exists
                if (ingredient.getIngredient() == null || ingredient.getIngredient().getIdIngredient() == null) {
                    throw new IllegalArgumentException("ID de ingrediente inválido en la receta");
                }
                
                Ingredient ing = ingredientRepository.findById(ingredient.getIngredient().getIdIngredient())
                        .orElseThrow(() -> new IllegalArgumentException(
                            "Ingrediente no encontrado con ID: " + ingredient.getIngredient().getIdIngredient()));
                
                ingredient.setIngredient(ing);
                ingredient.setItemMenu(saved);
                ingredient.setCreatedAt(LocalDateTime.now());
                
                itemIngredientRepository.save(ingredient);
            }
        }

        // Update availability based on stock
        updateItemAvailability(saved.getIdItemMenu());

        return saved;
    }

    @Override
    @Transactional
    public ItemMenu update(Long id, ItemMenu item, List<ItemIngredient> recipe) {
        log.info("Updating menu item with ID: {}", id);
        log.info("🔍 requiresPreparation value received: {}", item.getRequiresPreparation());

        // Require company context for updates
        Company company = CompanyContext.requireCurrentCompany();

        // Find existing item filtered by company
        ItemMenu existing = itemMenuRepository.findByIdItemMenuAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Item del menú no encontrado con ID: " + id));

        // Validate unique name if changed (filtered by company)
        if (!existing.getName().equals(item.getName()) && 
            itemMenuRepository.existsByNameAndCompany(item.getName(), company)) {
            throw new IllegalArgumentException("Ya existe un item del menú con el nombre: " + item.getName());
        }

        // Validate and load category if changed
        if (item.getCategory() != null && item.getCategory().getIdCategory() != null) {
            Category category = categoryRepository.findById(item.getCategory().getIdCategory())
                    .orElseThrow(() -> new IllegalArgumentException("La categoría especificada no existe"));
            
            // Validate category is active
            if (!Boolean.TRUE.equals(category.getActive())) {
                throw new IllegalArgumentException("No se puede asignar el item a la categoría '" + category.getName() + "' porque está desactivada. Por favor, active la categoría primero.");
            }
            
            existing.setCategory(category);
        }

        // Track if item was combo before update (for cleanup)
        boolean existingWasCombo = Boolean.TRUE.equals(existing.getIsCombo());

        // Update fields
        existing.setName(item.getName());
        existing.setDescription(item.getDescription());
        existing.setPrice(item.getPrice());
        existing.setImageUrl(item.getImageUrl());
        existing.setActive(item.getActive());
        existing.setRequiresPreparation(item.getRequiresPreparation());
        existing.setRequiresBaristaPreparation(item.getRequiresBaristaPreparation());
        existing.setRequiresParrilleroPreparation(item.getRequiresParrilleroPreparation());
        existing.setRequiresIngredients(item.getRequiresIngredients());
        existing.setIsCombo(item.getIsCombo());
        existing.setDineInOnly(item.getDineInOnly());
        existing.setMaxSauces(item.getMaxSauces());
        existing.setMinSauces(item.getMinSauces());
        validateSaucesRange(existing.getMinSauces(), existing.getMaxSauces());
        existing.setMaxSpecialities(item.getMaxSpecialities());
        existing.setMinSpecialities(item.getMinSpecialities());
        validateSpecialitiesRange(existing.getMinSpecialities(), existing.getMaxSpecialities());
        existing.setSizeName(item.getSizeName());
        existing.setUpdatedAt(LocalDateTime.now());

        // If switching FROM combo to another type, clear combo items
        if (Boolean.TRUE.equals(existingWasCombo) && !Boolean.TRUE.equals(item.getIsCombo())) {
            log.info("Item '{}' is switching from Combo to another type. Clearing combo items.", existing.getName());
            itemMenuComboItemRepository.deleteByComboMenuIdItemMenu(id);
            itemMenuComboItemRepository.flush();
        }

        ItemMenu updated = itemMenuRepository.save(existing);
        log.info("🔍 requiresPreparation value after save: {}", updated.getRequiresPreparation());
        log.info("🔍 requiresBaristaPreparation value after save: {}", updated.getRequiresBaristaPreparation());

        // Update recipe if provided
        if (recipe != null) {
            updateRecipe(id, recipe);
        }

        log.info("Menu item updated successfully: {}", id);
        return updated;
    }

    @Override
    @Transactional
    public ItemMenu activate(Long id) {
        log.info("Activating menu item with ID: {}", id);

        // Require company context for activation
        Company company = CompanyContext.requireCurrentCompany();
        ItemMenu item = itemMenuRepository.findByIdItemMenuAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Item del menú no encontrado con ID: " + id));
        item.setActive(true);
        item.setUpdatedAt(LocalDateTime.now());

        // Update availability
        updateItemAvailability(id);

        ItemMenu updated = itemMenuRepository.save(item);
        log.info("Menu item activated successfully: {}", id);
        return updated;
    }

    @Override
    @Transactional
    public ItemMenu deactivate(Long id) {
        log.info("Deactivating menu item with ID: {}", id);

        // Require company context for deactivation
        Company company = CompanyContext.requireCurrentCompany();
        ItemMenu item = itemMenuRepository.findByIdItemMenuAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Item del menú no encontrado con ID: " + id));
        item.setActive(false);
        item.setUpdatedAt(LocalDateTime.now());

        ItemMenu updated = itemMenuRepository.save(item);
        log.info("Menu item deactivated successfully: {}", id);
        return updated;
    }

    @Override
    @Transactional
    public void deactivateMultiple(List<Long> ids) {
        log.info("Deactivating {} menu items", ids.size());
        
        for (Long id : ids) {
            try {
                deactivate(id);
            } catch (Exception e) {
                log.error("Error deactivating menu item with ID: {}", id, e);
            }
        }
        
        log.info("Finished deactivating menu items");
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("Deleting menu item with ID: {}", id);

        // Require company context for deletion
        Company company = CompanyContext.requireCurrentCompany();
        ItemMenu item = itemMenuRepository.findByIdItemMenuAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Item del menú no encontrado con ID: " + id));
        
        // Recipe will be deleted automatically due to CASCADE
        itemMenuRepository.delete(item);
        
        log.info("Menu item deleted successfully: {}", id);
    }

    // ========== Recipe Management ==========

    @Override
    public List<ItemIngredient> getRecipe(Long itemMenuId) {
        log.debug("Getting recipe for menu item ID: {}", itemMenuId);
        return itemIngredientRepository.findByItemMenuId(itemMenuId);
    }

    @Override
    @Transactional
    public ItemIngredient addIngredientToRecipe(Long itemMenuId, ItemIngredient ingredient) {
        log.info("Adding ingredient to recipe of menu item ID: {}", itemMenuId);

        ItemMenu item = findByIdOrThrow(itemMenuId);
        
        // Validate ingredient exists
        Ingredient ing = ingredientRepository.findById(ingredient.getIngredient().getIdIngredient())
                .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado"));

        // Check if ingredient already exists in recipe
        Optional<ItemIngredient> existing = itemIngredientRepository.findByItemMenuIdAndIngredientId(
            itemMenuId, ing.getIdIngredient());
        
        if (existing.isPresent()) {
            throw new IllegalArgumentException(
                "El ingrediente '" + ing.getName() + "' ya está en la receta");
        }

        ingredient.setItemMenu(item);
        ingredient.setIngredient(ing);
        ingredient.setCreatedAt(LocalDateTime.now());

        ItemIngredient saved = itemIngredientRepository.save(ingredient);
        
        // Update item availability
        updateItemAvailability(itemMenuId);
        
        log.info("Ingredient added to recipe successfully");
        return saved;
    }

    @Override
    @Transactional
    public void removeIngredientFromRecipe(Long itemMenuId, Long ingredientId) {
        log.info("Removing ingredient {} from recipe of menu item {}", ingredientId, itemMenuId);

        ItemIngredient itemIngredient = itemIngredientRepository
                .findByItemMenuIdAndIngredientId(itemMenuId, ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado en la receta"));

        itemIngredientRepository.delete(itemIngredient);
        
        // Update item availability
        updateItemAvailability(itemMenuId);
        
        log.info("Ingredient removed from recipe successfully");
    }

    @Override
    @Transactional
    public void updateRecipe(Long itemMenuId, List<ItemIngredient> newRecipe) {
        log.info("Updating entire recipe for menu item ID: {}", itemMenuId);

        ItemMenu item = findByIdOrThrow(itemMenuId);

        // Delete old recipe
        List<ItemIngredient> oldRecipe = itemIngredientRepository.findByItemMenuId(itemMenuId);
        if (!oldRecipe.isEmpty()) {
            itemIngredientRepository.deleteAll(oldRecipe);
            // Force flush to execute DELETE before INSERT to avoid constraint violations
            itemIngredientRepository.flush();
            log.debug("Deleted {} old recipe items", oldRecipe.size());
        }

        // Save new recipe
        if (newRecipe != null && !newRecipe.isEmpty()) {
            for (ItemIngredient ingredient : newRecipe) {
                Ingredient ing = ingredientRepository.findById(ingredient.getIngredient().getIdIngredient())
                        .orElseThrow(() -> new IllegalArgumentException(
                            "Ingrediente no encontrado con ID: " + ingredient.getIngredient().getIdIngredient()));
                
                ingredient.setIngredient(ing);
                ingredient.setItemMenu(item);
                ingredient.setCreatedAt(LocalDateTime.now());
                
                itemIngredientRepository.save(ingredient);
            }
            log.debug("Saved {} new recipe items", newRecipe.size());
        }

        // Update availability
        updateItemAvailability(itemMenuId);
        
        log.info("Recipe updated successfully");
    }

    @Override
    @Transactional
    public void clearRecipe(Long itemMenuId) {
        log.info("Clearing recipe for menu item ID: {}", itemMenuId);

        List<ItemIngredient> recipe = itemIngredientRepository.findByItemMenuId(itemMenuId);
        itemIngredientRepository.deleteAll(recipe);
        
        log.info("Recipe cleared successfully");
    }

    // ========== Stock & Availability Management ==========

    @Override
    public boolean hasEnoughStock(Long itemMenuId, int quantity) {
        log.debug("Checking stock for menu item {} quantity {}", itemMenuId, quantity);

        ItemMenu item = findByIdOrThrow(itemMenuId);
        return item.hasEnoughStock(quantity);
    }

    @Override
    public int getMaxAvailableQuantity(Long itemMenuId) {
        log.debug("Getting max available quantity for menu item {}", itemMenuId);

        ItemMenu item = findByIdOrThrow(itemMenuId);
        return item.getMaxAvailableQuantity();
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void updateItemAvailability(Long itemMenuId) {
        log.debug("Updating availability for menu item ID: {}", itemMenuId);

        // IMPORTANT: This method MUST run in REQUIRES_NEW propagation.
        //
        // Stock mutations (deduct/return) are performed in REQUIRES_NEW sub-transactions
        // by IngredientStockService and commit independently. However, MySQL's default
        // REPEATABLE_READ isolation means any *outer* transaction that started BEFORE
        // those sub-tx commits will keep seeing the original snapshot — even after a
        // JPA refresh — and would therefore recompute `available` against the stale
        // pre-mutation stock value.
        //
        // By starting our own fresh transaction here we get a new snapshot that sees
        // every committed stock change, so `hasEnoughStock(1)` reflects reality and
        // `ItemMenu.available` is persisted with the correct value.
        ItemMenu item = findByIdOrThrow(itemMenuId);
        item.updateAvailability(); // Method in entity
        itemMenuRepository.save(item);
    }

    @Override
    @Transactional
    public void updateAllItemsAvailability() {
        log.info("Updating availability for all active menu items");

        List<ItemMenu> allItems = itemMenuRepository.findByActiveTrue();
        for (ItemMenu item : allItems) {
            item.updateAvailability();
        }
        itemMenuRepository.saveAll(allItems);
        
        log.info("Updated availability for {} items", allItems.size());
    }

    // ========== Sales Methods (Ready but not used yet) ==========

    /**
     * ⭐ MÉTODO PARA VENTAS - LISTO PERO NO SE USA AÚN
     * Este método será usado por el módulo de ventas en el futuro
     */
    @Override
    @Transactional
    public void sellItem(Long itemMenuId, int quantity) {
        log.info("Processing sale: Menu item {} x {} units", itemMenuId, quantity);

        // 1. Get the item
        ItemMenu item = findByIdOrThrow(itemMenuId);
        
        // 2. Validate item is active and available
        if (!item.getActive()) {
            throw new IllegalStateException("El item no está activo: " + item.getName());
        }
        if (!item.getAvailable()) {
            throw new IllegalStateException("El item no está disponible: " + item.getName());
        }
        
        // 3. Verify enough stock
        if (!hasEnoughStock(itemMenuId, quantity)) {
            throw new IllegalStateException(
                "No hay ingredientes suficientes para preparar " + quantity + " " + item.getName());
        }
        
        // 4. Deduct ingredients from stock
        List<ItemIngredient> recipe = getRecipe(itemMenuId);
        for (ItemIngredient itemIngredient : recipe) {
            try {
                // Deduct from stock (method in ItemIngredient entity)
                BigDecimal newStock = itemIngredient.deductFromStock(quantity);
                
                // Save the ingredient with the new stock
                ingredientRepository.save(itemIngredient.getIngredient());
                
                log.info("Deducted {} {} of '{}'. New stock: {}", 
                         itemIngredient.getQuantity().multiply(BigDecimal.valueOf(quantity)),
                         itemIngredient.getUnit(),
                         itemIngredient.getIngredientName(),
                         newStock);
                         
            } catch (Exception e) {
                log.error("Error deducting ingredient: {}", e.getMessage());
                throw new RuntimeException("Error al descontar del inventario: " + e.getMessage());
            }
        }
        
        // 5. Update item availability
        updateItemAvailability(itemMenuId);
        
        log.info("Sale processed successfully: {} x {} units", item.getName(), quantity);
    }

    @Override
    public BigDecimal calculateIngredientsCost(Long itemMenuId) {
        log.debug("Calculating ingredients cost for menu item ID: {}", itemMenuId);

        ItemMenu item = findByIdOrThrow(itemMenuId);
        return item.calculateIngredientsCost();
    }

    // ========== Statistics ==========

    @Override
    public long countAll() {
        return itemMenuRepository.count();
    }

    @Override
    public long countActive() {
        return itemMenuRepository.findByActiveTrue().size();
    }

    @Override
    public long countAvailable() {
        return itemMenuRepository.countAvailable();
    }

    @Override
    public long countUnavailable() {
        return itemMenuRepository.countUnavailable();
    }

    @Override
    public long countByCategoryId(Long categoryId) {
        return itemMenuRepository.countByCategoryId(categoryId);
    }

    @Override
    public long countActiveByCategoryId(Long categoryId) {
        return itemMenuRepository.countActiveByCategoryId(categoryId);
    }

    @Override
    public long countAvailableByCategoryId(Long categoryId) {
        return itemMenuRepository.countAvailableByCategoryId(categoryId);
    }

    @Override
    public boolean existsByName(String name) {
        Company company = CompanyContext.requireCurrentCompany();
        return itemMenuRepository.existsByNameAndCompany(name, company);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, Long excludeId) {
        Company company = CompanyContext.requireCurrentCompany();
        return itemMenuRepository.existsByNameAndCompanyAndIdNot(name, company, excludeId);
    }

    @Override
    public ItemMenu findByIdOrThrow(Long id) {
        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Item del menú no encontrado con ID: " + id));
    }

    // ========== Availability Schedule Management ==========

    @Override
    @Transactional(readOnly = true)
    public boolean isItemAvailableAt(Long itemMenuId, DayOfWeek day, LocalTime time) {
        log.debug("Checking if item {} is available on {} at {}", itemMenuId, day, time);
        
        ItemMenu item = findByIdOrThrow(itemMenuId);
        
        // First check if item is active and has stock
        if (!item.getActive() || !item.getAvailable()) {
            return false;
        }
        
        // If no custom schedule is configured, item is always available
        // (restaurant hours validation happens at order creation level)
        if (item.getHasCustomSchedule() == null || !item.getHasCustomSchedule()) {
            log.debug("Item {} has no custom schedule, available during all business hours", itemMenuId);
            return true;
        }
        
        // Get the specific availability for this day
        Optional<ItemMenuAvailability> availabilityOpt = itemMenuAvailabilityRepository
                .findByItemMenuIdAndDayOfWeek(itemMenuId, day);
        
        // Check if this day is configured as available
        if (availabilityOpt.isEmpty()) {
            log.debug("Item {} is not configured for {}", itemMenuId, day);
            return false;
        }
        
        ItemMenuAvailability availability = availabilityOpt.get();
        
        // Check time availability using the DAY-SPECIFIC times (which are already auto-adjusted)
        LocalTime startTime = availability.getStartTime();
        LocalTime endTime = availability.getEndTime();
        
        if (startTime != null && endTime != null && time != null) {
            if (time.isBefore(startTime) || time.isAfter(endTime)) {
                log.debug("Item {} is not available at {} (available {} - {})", 
                         itemMenuId, time, startTime, endTime);
                return false;
            }
        }
        
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isItemAvailableNow(Long itemMenuId) {
        LocalDateTime now = dateTimeService.nowLocal();
        java.time.DayOfWeek javaDayOfWeek = now.getDayOfWeek();
        DayOfWeek customDay = DayOfWeek.valueOf(javaDayOfWeek.name());
        LocalTime currentTime = now.toLocalTime();
        
        return isItemAvailableAt(itemMenuId, customDay, currentTime);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DayOfWeek> getAvailableDays(Long itemMenuId) {
        log.debug("Getting available days for item {}", itemMenuId);
        return itemMenuAvailabilityRepository.findAvailableDaysByItemMenuId(itemMenuId);
    }

    @Override
    @Transactional
    public void updateAvailabilityScheduleManual(Long itemMenuId, Map<DayOfWeek, LocalTime[]> daySchedules) {
        log.info("Updating custom availability schedule for item {} with {} days", itemMenuId, daySchedules.size());
        
        ItemMenu item = findByIdOrThrow(itemMenuId);
        
        // Delete existing availability records
        itemMenuAvailabilityRepository.deleteByItemMenuId(itemMenuId);
        itemMenuAvailabilityRepository.flush();
        
        // Save new availability records with specific times per day
        if (daySchedules != null && !daySchedules.isEmpty()) {
            for (Map.Entry<DayOfWeek, LocalTime[]> entry : daySchedules.entrySet()) {
                DayOfWeek day = entry.getKey();
                LocalTime[] times = entry.getValue();
                LocalTime startTime = times != null && times.length > 0 ? times[0] : null;
                LocalTime endTime = times != null && times.length > 1 ? times[1] : null;
                
                // Get business hours for validation
                Optional<BusinessHours> businessHoursOpt = businessHoursService.getBusinessHoursForDay(day);
                
                if (businessHoursOpt.isEmpty()) {
                    log.warn("No business hours found for day {}, skipping", day.getDisplayName());
                    continue;
                }
                
                BusinessHours bh = businessHoursOpt.get();
                if (bh.getIsClosed()) {
                    log.warn("Day {} is marked as closed, skipping", day.getDisplayName());
                    continue;
                }
                
                // Validate and clamp times within business hours
                LocalTime effectiveStartTime = startTime;
                LocalTime effectiveEndTime = endTime;
                boolean wasAdjusted = false;
                
                // If no start time provided, use restaurant open time
                if (effectiveStartTime == null) {
                    effectiveStartTime = bh.getOpenTime();
                } else if (effectiveStartTime.isBefore(bh.getOpenTime())) {
                    effectiveStartTime = bh.getOpenTime();
                    wasAdjusted = true;
                    log.info("Day {}: Adjusted start time to {} (restaurant opens at {})", 
                             day.getDisplayName(), effectiveStartTime, bh.getOpenTime());
                }
                
                // If no end time provided, use restaurant close time
                if (effectiveEndTime == null) {
                    effectiveEndTime = bh.getCloseTime();
                } else if (effectiveEndTime.isAfter(bh.getCloseTime())) {
                    effectiveEndTime = bh.getCloseTime();
                    wasAdjusted = true;
                    log.info("Day {}: Adjusted end time to {} (restaurant closes at {})", 
                             day.getDisplayName(), effectiveEndTime, bh.getCloseTime());
                }
                
                ItemMenuAvailability availability = ItemMenuAvailability.builder()
                        .itemMenu(item)
                        .dayOfWeek(day)
                        .startTime(effectiveStartTime)
                        .endTime(effectiveEndTime)
                        .wasAutoAdjusted(wasAdjusted)
                        .build();
                itemMenuAvailabilityRepository.save(availability);
                
                log.debug("Saved custom schedule for {}: {} - {}", day.getDisplayName(), effectiveStartTime, effectiveEndTime);
            }
        }
        
        // Update item with custom schedule enabled
        item.setHasCustomSchedule(true);
        item.setAvailabilityStartTime(null);
        item.setAvailabilityEndTime(null);
        item.setUpdatedAt(LocalDateTime.now());
        itemMenuRepository.save(item);
        
        log.info("Custom availability schedule updated successfully for item {}", itemMenuId);
    }

    @Override
    @Transactional
    public void clearAvailabilitySchedule(Long itemMenuId) {
        log.info("Clearing availability schedule for item {}", itemMenuId);
        
        ItemMenu item = findByIdOrThrow(itemMenuId);
        
        // Delete all existing availability records
        itemMenuAvailabilityRepository.deleteByItemMenuId(itemMenuId);
        itemMenuAvailabilityRepository.flush();
        
        // Update item to have no custom schedule
        item.setHasCustomSchedule(false);
        item.setAvailabilityStartTime(null);
        item.setAvailabilityEndTime(null);
        item.setUpdatedAt(LocalDateTime.now());
        itemMenuRepository.save(item);
        
        log.info("Availability schedule cleared for item {}. Item will be available during all business hours.", itemMenuId);
    }

    @Override
    @Transactional
    public void setDefaultAvailabilitySchedule(ItemMenu item) {
        log.info("Setting default availability schedule for item: {}", item.getName());
        
        // By default, no custom schedule - item is available all day
        item.setHasCustomSchedule(false);
        item.setAvailabilityStartTime(null);
        item.setAvailabilityEndTime(null);
        
        // Clear any existing availability days
        if (item.getAvailabilityDays() != null) {
            item.getAvailabilityDays().clear();
        }
        
        log.info("Default availability set: item {} will be available during all business hours", item.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemMenu> findAvailableItemsAt(DayOfWeek day, LocalTime time) {
        log.debug("Finding items available on {} at {}", day, time);
        
        // Get all active items
        List<ItemMenu> activeItems = itemMenuRepository.findByActiveTrue();
        
        // Filter by availability schedule
        return activeItems.stream()
                .filter(item -> item.isAvailableAt(day, time))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemMenu> findCurrentlyAvailableItems() {
        LocalDateTime now = dateTimeService.nowLocal();
        java.time.DayOfWeek javaDayOfWeek = now.getDayOfWeek();
        DayOfWeek customDay = DayOfWeek.valueOf(javaDayOfWeek.name());
        LocalTime currentTime = now.toLocalTime();
        
        return findAvailableItemsAt(customDay, currentTime);
    }

    @Override
    public void validateAvailabilityWithinBusinessHours(List<DayOfWeek> availableDays,
                                                         LocalTime startTime, LocalTime endTime) {
        log.debug("Validating availability schedule against business hours");
        
        if (availableDays == null || availableDays.isEmpty()) {
            return; // No validation needed if no days specified
        }
        
        for (DayOfWeek day : availableDays) {
            Optional<BusinessHours> businessHours = businessHoursService.getBusinessHoursForDay(day);
            
            // Only throw error for truly invalid scenarios (closed days or non-existent days)
            if (businessHours.isEmpty()) {
                throw new IllegalArgumentException(
                    "El día " + day.getDisplayName() + " no es un día laborable del restaurante");
            }
            
            BusinessHours bh = businessHours.get();
            
            if (bh.getIsClosed()) {
                throw new IllegalArgumentException(
                    "El restaurante está cerrado el día " + day.getDisplayName());
            }
            
            // Time conflicts are now auto-adjusted, just log warnings for info
            if (startTime != null && startTime.isBefore(bh.getOpenTime())) {
                log.info("Day {}: Start time {} will be auto-adjusted to {} (restaurant opens)", 
                         day.getDisplayName(), startTime, bh.getOpenTime());
            }
            
            if (endTime != null && endTime.isAfter(bh.getCloseTime())) {
                log.info("Day {}: End time {} will be auto-adjusted to {} (restaurant closes)", 
                         day.getDisplayName(), endTime, bh.getCloseTime());
            }
            
            // Validate start before end (this is still an error)
            if (startTime != null && endTime != null && !startTime.isBefore(endTime)) {
                throw new IllegalArgumentException(
                    "La hora de inicio debe ser anterior a la hora de fin");
            }
        }
        
        log.debug("Availability schedule validation passed");
    }

    // ========== Combo Management Methods ==========

    @Override
    @Transactional(readOnly = true)
    public java.util.List<ItemMenuComboItem> getComboItems(Long comboMenuId) {
        return itemMenuComboItemRepository.findByComboMenuIdItemMenuOrderByDisplayOrderAsc(comboMenuId);
    }

    @Override
    @Transactional
    public void updateComboItems(Long comboMenuId, java.util.List<Long> childItemIds, java.util.List<Integer> quantities) {
        ItemMenu comboMenu = itemMenuRepository.findById(comboMenuId)
                .orElseThrow(() -> new RuntimeException("Combo menu item not found: " + comboMenuId));

        if (!Boolean.TRUE.equals(comboMenu.getIsCombo())) {
            throw new IllegalStateException("Item '" + comboMenu.getName() + "' is not a combo type");
        }

        // Clear existing combo items
        itemMenuComboItemRepository.deleteByComboMenuIdItemMenu(comboMenuId);
        itemMenuComboItemRepository.flush();

        if (childItemIds == null || childItemIds.isEmpty()) {
            log.info("Cleared all combo items for combo '{}'", comboMenu.getName());
            return;
        }

        // Validate and create new combo items
        java.util.List<ItemMenuComboItem> newComboItems = new ArrayList<>();
        for (int i = 0; i < childItemIds.size(); i++) {
            Long childId = childItemIds.get(i);
            int quantity = (quantities != null && i < quantities.size()) ? quantities.get(i) : 1;

            if (childId.equals(comboMenuId)) {
                throw new IllegalArgumentException("A combo cannot contain itself as a child item");
            }

            ItemMenu childMenu = itemMenuRepository.findById(childId)
                    .orElseThrow(() -> new RuntimeException("Child menu item not found: " + childId));

            if (Boolean.TRUE.equals(childMenu.getIsCombo())) {
                throw new IllegalArgumentException("Cannot add another combo ('" + childMenu.getName() + "') as a child item. Nested combos are not supported.");
            }

            ItemMenuComboItem comboItem = new ItemMenuComboItem();
            comboItem.setComboMenu(comboMenu);
            comboItem.setChildMenu(childMenu);
            comboItem.setQuantity(quantity);
            comboItem.setDisplayOrder(i + 1);
            newComboItems.add(comboItem);
        }

        itemMenuComboItemRepository.saveAll(newComboItems);
        log.info("Updated combo '{}' with {} child items", comboMenu.getName(), newComboItems.size());
    }

    @Override
    @Transactional
    public void clearComboItems(Long comboMenuId) {
        itemMenuComboItemRepository.deleteByComboMenuIdItemMenu(comboMenuId);
        itemMenuComboItemRepository.flush();
        log.info("Cleared all combo items for combo menu ID: {}", comboMenuId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> checkHardDeleteDependencies(Long id) {
        ItemMenu item = findByIdOrThrow(id);

        long orderCount = orderDetailRepository.countByItemMenuIdItemMenu(id);
        List<ItemMenuComboItem> comboMemberships = itemMenuComboItemRepository.findByChildMenuIdItemMenu(id);
        int comboCount = comboMemberships.size();
        long sizeCount = itemMenuRepository.countSizeItemsByParentId(id);

        Map<String, Object> result = new HashMap<>();
        result.put("itemName", item.getName());
        result.put("orderCount", orderCount);
        result.put("comboCount", comboCount);
        result.put("sizeCount", sizeCount);
        result.put("ingredientCount", item.getIngredients() != null ? item.getIngredients().size() : 0);
        result.put("complementCount", item.getAvailableComplements() != null ? item.getAvailableComplements().size() : 0);
        result.put("canDelete", orderCount == 0);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> hardDelete(Long id) {
        log.warn("HARD DELETING menu item with ID: {}", id);
        Map<String, Object> result = checkHardDeleteDependencies(id);

        if (!(boolean) result.get("canDelete")) {
            throw new IllegalStateException(
                    "No se puede eliminar permanentemente el item '" + result.get("itemName") +
                    "' porque tiene " + result.get("orderCount") + " ventas registradas en el historial.");
        }

        // 1. Delink from combos where this item is a child
        itemMenuComboItemRepository.deleteByChildMenuIdItemMenu(id);
        itemMenuComboItemRepository.flush();
        log.info("Delinked from {} combos", result.get("comboCount"));

        // 2. Detach size children (set parentItem = null, sizeName = null)
        List<ItemMenu> sizeChildren = itemMenuRepository.findSizeItemsByParentId(id);
        for (ItemMenu child : sizeChildren) {
            child.setParentItem(null);
            child.setSizeName(null);
            child.setUpdatedAt(java.time.LocalDateTime.now());
        }
        if (!sizeChildren.isEmpty()) {
            itemMenuRepository.saveAll(sizeChildren);
            itemMenuRepository.flush();
            log.info("Detached {} size children from parent item ID: {}", sizeChildren.size(), id);
        }

        // Clear persistence context to avoid stale references after JPQL bulk delete
        entityManager.clear();

        // 2. Re-fetch entity with clean persistence context, then delete
        ItemMenu item = findByIdOrThrow(id);
        itemMenuRepository.delete(item);
        itemMenuRepository.flush();

        log.info("Menu item '{}' permanently deleted from DB", result.get("itemName"));
        return result;
    }

    // ========== Size Management ==========

    @Override
    public List<ItemMenu> findSizeItems(Long parentId) {
        log.debug("Finding size variants for parent item ID: {}", parentId);
        return itemMenuRepository.findSizeItemsByParentId(parentId);
    }

    @Override
    @Transactional
    public ItemMenu addSizeItem(Long parentId, String sizeName, java.math.BigDecimal price) {
        log.info("Adding size variant '{}' (price={}) to parent item ID: {}", sizeName, price, parentId);

        Company company = CompanyContext.requireCurrentCompany();
        ItemMenu parent = itemMenuRepository.findByIdItemMenuAndCompany(parentId, company)
                .orElseThrow(() -> new IllegalArgumentException("Item del menú padre no encontrado con ID: " + parentId));

        // No grandchildren: parent itself cannot be a child
        if (parent.getParentItem() != null) {
            throw new IllegalArgumentException(
                "El item '" + parent.getName() + "' ya es un tamaño del item '" + parent.getParentItem().getName() +
                "'. Los items hijos no pueden tener sus propios tamaños.");
        }

        // Validate max 10 total (parent counts as 1 → max 9 children)
        long existingChildren = itemMenuRepository.countSizeItemsByParentId(parentId);
        if (existingChildren >= 9) {
            throw new IllegalArgumentException(
                "El item '" + parent.getName() + "' ya tiene el máximo de 9 tamaños adicionales (10 en total incluyendo el padre).");
        }

        // Build child name: "parentName sizeName"
        String childName = parent.getName() + " " + sizeName.trim();

        // Validate unique name for this company
        if (itemMenuRepository.existsByNameAndCompany(childName, company)) {
            throw new IllegalArgumentException("Ya existe un item del menú con el nombre: " + childName);
        }

        // Create child item copying configuration from parent
        ItemMenu child = ItemMenu.builder()
                .company(company)
                .name(childName)
                .sizeName(sizeName.trim())
                .parentItem(parent)
                .price(price)
                .category(parent.getCategory())
                .requiresPreparation(parent.getRequiresPreparation())
                .requiresBaristaPreparation(parent.getRequiresBaristaPreparation())
                .requiresParrilleroPreparation(parent.getRequiresParrilleroPreparation())
                .requiresIngredients(parent.getRequiresIngredients())
                .isCombo(parent.getIsCombo())
                .dineInOnly(parent.getDineInOnly())
                .maxSauces(parent.getMaxSauces())
                .minSauces(parent.getMinSauces())
                .maxSpecialities(parent.getMaxSpecialities())
                .minSpecialities(parent.getMinSpecialities())
                .hasCustomSchedule(parent.getHasCustomSchedule())
                .availabilityStartTime(parent.getAvailabilityStartTime())
                .availabilityEndTime(parent.getAvailabilityEndTime())
                .active(parent.getActive())
                .available(true)
                // description and imageUrl intentionally NOT copied
                .build();

        child.setCreatedAt(java.time.LocalDateTime.now());
        child.setUpdatedAt(java.time.LocalDateTime.now());
        ItemMenu savedChild = itemMenuRepository.save(child);

        // Copy recipe (ingredient list) from parent
        List<com.aatechsolutions.elgransazon.domain.entity.ItemIngredient> parentRecipe =
                itemIngredientRepository.findByItemMenuId(parentId);
        for (com.aatechsolutions.elgransazon.domain.entity.ItemIngredient pi : parentRecipe) {
            com.aatechsolutions.elgransazon.domain.entity.ItemIngredient ci =
                    com.aatechsolutions.elgransazon.domain.entity.ItemIngredient.builder()
                    .itemMenu(savedChild)
                    .ingredient(pi.getIngredient())
                    .quantity(pi.getQuantity())
                    .unit(pi.getUnit())
                    .build();
            ci.setCreatedAt(java.time.LocalDateTime.now());
            itemIngredientRepository.save(ci);
        }

        // Copy complement associations from parent
        List<com.aatechsolutions.elgransazon.domain.entity.ItemMenuComplement> parentComplements =
                itemMenuComplementRepository.findByItemMenuIdItemMenu(parentId);
        for (com.aatechsolutions.elgransazon.domain.entity.ItemMenuComplement pc : parentComplements) {
            com.aatechsolutions.elgransazon.domain.entity.ItemMenuComplement cc =
                    com.aatechsolutions.elgransazon.domain.entity.ItemMenuComplement.builder()
                    .itemMenu(savedChild)
                    .complement(pc.getComplement())
                    .maxQuantity(pc.getMaxQuantity())
                    .displayOrder(pc.getDisplayOrder())
                    .active(pc.getActive())
                    .build();
            cc.setCreatedAt(java.time.LocalDateTime.now());
            itemMenuComplementRepository.save(cc);
        }

        // Copy availability days from parent
        List<com.aatechsolutions.elgransazon.domain.entity.ItemMenuAvailability> parentDays =
                itemMenuAvailabilityRepository.findByItemMenuId(parentId);
        for (com.aatechsolutions.elgransazon.domain.entity.ItemMenuAvailability pd : parentDays) {
            com.aatechsolutions.elgransazon.domain.entity.ItemMenuAvailability cd =
                    com.aatechsolutions.elgransazon.domain.entity.ItemMenuAvailability.builder()
                    .itemMenu(savedChild)
                    .dayOfWeek(pd.getDayOfWeek())
                    .build();
            itemMenuAvailabilityRepository.save(cd);
        }

        // Copy combo items from parent (when parent is a combo)
        if (Boolean.TRUE.equals(parent.getIsCombo())) {
            List<com.aatechsolutions.elgransazon.domain.entity.ItemMenuComboItem> parentComboItems =
                    itemMenuComboItemRepository.findByComboMenuIdItemMenuOrderByDisplayOrderAsc(parentId);
            for (com.aatechsolutions.elgransazon.domain.entity.ItemMenuComboItem pc : parentComboItems) {
                com.aatechsolutions.elgransazon.domain.entity.ItemMenuComboItem cc =
                        com.aatechsolutions.elgransazon.domain.entity.ItemMenuComboItem.builder()
                        .comboMenu(savedChild)
                        .childMenu(pc.getChildMenu())
                        .quantity(pc.getQuantity())
                        .displayOrder(pc.getDisplayOrder())
                        .build();
                itemMenuComboItemRepository.save(cc);
            }
        }

        log.info("Size variant '{}' created with ID: {}", childName, savedChild.getIdItemMenu());
        return savedChild;
    }

    @Override
    @Transactional
    public void detachSizeItem(Long childId) {
        log.info("Detaching size variant ID: {} from its parent", childId);

        Company company = CompanyContext.requireCurrentCompany();
        ItemMenu child = itemMenuRepository.findByIdItemMenuAndCompany(childId, company)
                .orElseThrow(() -> new IllegalArgumentException("Item del menú no encontrado con ID: " + childId));

        if (child.getParentItem() == null) {
            throw new IllegalArgumentException("El item con ID " + childId + " no es un tamaño vinculado a ningún padre.");
        }

        child.setParentItem(null);
        child.setSizeName(null);
        child.setUpdatedAt(java.time.LocalDateTime.now());
        itemMenuRepository.save(child);

        log.info("Size variant ID: {} successfully detached", childId);
    }

    @Override
    @Transactional
    public void updateSizeItem(Long childId, String newSizeName, java.math.BigDecimal newPrice) {
        log.info("Updating size variant ID: {} → sizeName='{}', price={}", childId, newSizeName, newPrice);

        Company company = CompanyContext.requireCurrentCompany();
        ItemMenu child = itemMenuRepository.findByIdItemMenuAndCompany(childId, company)
                .orElseThrow(() -> new IllegalArgumentException("Item del menú no encontrado con ID: " + childId));

        if (child.getParentItem() == null) {
            throw new IllegalArgumentException("El item con ID " + childId + " no es un tamaño vinculado a ningún padre.");
        }

        // Update name: rebuild from parent name + new sizeName
        String parentName = child.getParentItem().getName();
        String newChildName = parentName + " " + newSizeName.trim();

        // Validate unique name (exclude this child)
        if (!child.getName().equals(newChildName) && itemMenuRepository.existsByNameAndCompany(newChildName, company)) {
            throw new IllegalArgumentException("Ya existe un item del menú con el nombre: " + newChildName);
        }

        child.setName(newChildName);
        child.setSizeName(newSizeName.trim());
        if (newPrice != null && newPrice.compareTo(java.math.BigDecimal.ZERO) > 0) {
            child.setPrice(newPrice);
        }
        child.setUpdatedAt(java.time.LocalDateTime.now());
        itemMenuRepository.save(child);

        log.info("Size variant ID: {} updated successfully", childId);
    }

    @Override
    public java.util.List<ItemMenu> searchFreeItems(Long excludeId, String query) {
        Company company = CompanyContext.requireCurrentCompany();
        String q = (query == null ? "" : query.trim().toLowerCase());
        return itemMenuRepository.findFreeItemsByCompany(company).stream()
                .filter(i -> !i.getIdItemMenu().equals(excludeId))
                .filter(i -> q.isEmpty() || i.getName().toLowerCase().contains(q))
                .limit(20)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional
    public ItemMenu linkExistingItemAsSize(Long parentId, Long childId, String sizeName) {
        log.info("Linking existing item ID: {} as size variant of parent ID: {} with sizeName='{}'", childId, parentId, sizeName);

        Company company = CompanyContext.requireCurrentCompany();

        ItemMenu parent = itemMenuRepository.findByIdItemMenuAndCompany(parentId, company)
                .orElseThrow(() -> new IllegalArgumentException("Item padre no encontrado con ID: " + parentId));

        ItemMenu child = itemMenuRepository.findByIdItemMenuAndCompany(childId, company)
                .orElseThrow(() -> new IllegalArgumentException("Item a vincular no encontrado con ID: " + childId));

        if (child.getParentItem() != null) {
            throw new IllegalArgumentException(
                "El item '" + child.getName() + "' ya está vinculado al padre: " + child.getParentItem().getName() +
                ". Desvincúlalo primero.");
        }

        if (parent.getParentItem() != null) {
            throw new IllegalArgumentException("El item padre no puede ser a su vez un tamaño de otro item.");
        }

        // Validate that the child to be linked has no children of its own
        long childsChildren = itemMenuRepository.countSizeItemsByParentId(childId);
        if (childsChildren > 0) {
            throw new IllegalArgumentException(
                "El item '" + child.getName() + "' tiene " + childsChildren + " tamaño(s) vinculado(s). " +
                "No se puede vincular un item que ya es padre de otros tamaños.");
        }

        long existingChildren = itemMenuRepository.countSizeItemsByParentId(parentId);
        if (existingChildren >= 9) {
            throw new IllegalArgumentException(
                "El item '" + parent.getName() + "' ya tiene el máximo de 9 tamaños adicionales.");
        }

        String newChildName = parent.getName() + " " + sizeName.trim();
        // Allow re-linking the same item (name might already be correct)
        if (!child.getName().equals(newChildName) && itemMenuRepository.existsByNameAndCompany(newChildName, company)) {
            throw new IllegalArgumentException("Ya existe un item del menú con el nombre: " + newChildName);
        }

        child.setParentItem(parent);
        child.setSizeName(sizeName.trim());
        child.setName(newChildName);
        child.setUpdatedAt(java.time.LocalDateTime.now());
        ItemMenu saved = itemMenuRepository.save(child);

        log.info("Item ID: {} linked as size '{}' of parent '{}' (ID: {})", childId, sizeName, parent.getName(), parentId);
        return saved;
    }
}
