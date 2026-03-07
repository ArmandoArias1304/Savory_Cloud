package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.*;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing Complements and their relationships with ItemMenu
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ComplementService {

    private final ComplementRepository complementRepository;
    private final ComplementIngredientRepository complementIngredientRepository;
    private final ItemMenuComplementRepository itemMenuComplementRepository;
    private final IngredientRepository ingredientRepository;
    private final ItemMenuRepository itemMenuRepository;
    private final OrderDetailComplementRepository orderDetailComplementRepository;

    // ========== COMPLEMENT CRUD ==========

    /**
     * Create a new complement
     */
    public Complement createComplement(Complement complement) {
        log.info("Creating complement: {}", complement.getName());
        
        // Set company from context
        Company company = CompanyContext.requireCurrentCompany();
        complement.setCompany(company);
        
        // Validate name uniqueness within company
        if (complementRepository.existsByNameIgnoreCaseAndCompany(complement.getName(), company)) {
            throw new IllegalArgumentException("Ya existe un complemento con el nombre: " + complement.getName());
        }
        
        complement.setActive(true);
        complement.setAvailable(true);
        
        return complementRepository.save(complement);
    }

    /**
     * Update an existing complement
     */
    public Complement updateComplement(Long id, Complement updated) {
        log.info("Updating complement with ID: {}", id);
        
        // Validate company ownership
        Company company = CompanyContext.requireCurrentCompany();
        Complement existing = complementRepository.findByIdComplementAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Complemento no encontrado con ID: " + id));
        
        // Check name uniqueness if name is being changed (within company)
        if (!existing.getName().equalsIgnoreCase(updated.getName()) &&
            complementRepository.existsByNameIgnoreCaseAndCompany(updated.getName(), company)) {
            throw new IllegalArgumentException("Ya existe un complemento con el nombre: " + updated.getName());
        }
        
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setExtraPrice(updated.getExtraPrice());
        existing.setImageUrl(updated.getImageUrl());
        existing.setActive(updated.getActive());
        existing.setIsSauce(updated.getIsSauce() != null ? updated.getIsSauce() : false);
        
        log.info("Updated complement '{}' - isSauce: {}", existing.getName(), existing.getIsSauce());
        
        // Update availability based on ingredient stock
        existing.updateAvailability();
        
        return complementRepository.save(existing);
    }

    /**
     * Delete a complement
     */
    public void deleteComplement(Long id) {
        log.info("Deleting complement with ID: {}", id);
        
        // Validate company ownership
        Company company = CompanyContext.requireCurrentCompany();
        Complement complement = complementRepository.findByIdComplementAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Complemento no encontrado con ID: " + id));
        
        // Check if complement is used in any orders
        // For now, just soft delete by setting active = false
        complement.setActive(false);
        complementRepository.save(complement);
        
        log.info("Complement '{}' has been deactivated", complement.getName());
    }

    /**
     * Hard delete a complement (only if not used in orders)
     */
    public Map<String, Object> hardDeleteComplement(Long id) {
        log.info("Hard deleting complement with ID: {}", id);

        // Validate company ownership
        Company company = CompanyContext.requireCurrentCompany();
        Complement complement = complementRepository.findByIdComplementAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Complemento no encontrado con ID: " + id));

        long orderUsage = orderDetailComplementRepository.countUsageOfComplement(id);
        if (orderUsage > 0) {
            throw new IllegalStateException(
                    "No se puede eliminar el complemento '" + complement.getName() +
                    "' porque está usado en " + orderUsage + " orden(es).");
        }

        // Delete ingredient associations first
        complementIngredientRepository.deleteByComplementIdComplement(id);

        // Delete item menu associations
        itemMenuComplementRepository.deleteByComplementIdComplement(id);

        // Delete the complement
        complementRepository.delete(complement);

        log.info("Complement '{}' has been permanently deleted", complement.getName());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Complemento '" + complement.getName() + "' eliminado permanentemente");
        return result;
    }

    /**
     * Check dependencies before hard-deleting a Complement.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> checkHardDeleteDependencies(Long id) {
        // Validate company ownership
        Company company = CompanyContext.requireCurrentCompany();
        Complement complement = complementRepository.findByIdComplementAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Complemento no encontrado con ID: " + id));

        long orderUsageCount = orderDetailComplementRepository.countUsageOfComplement(id);
        int ingredientCount = complement.getIngredients() != null ? complement.getIngredients().size() : 0;

        List<ItemMenuComplement> menuLinks = itemMenuComplementRepository.findByComplementIdComplement(id);
        List<String> menuItemNames = menuLinks.stream()
                .map(imc -> imc.getItemMenu().getName())
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("complementName", complement.getName());
        result.put("orderUsageCount", orderUsageCount);
        result.put("ingredientCount", ingredientCount);
        result.put("menuItemCount", menuItemNames.size());
        result.put("menuItemNames", menuItemNames);
        result.put("canDelete", orderUsageCount == 0);
        return result;
    }

    // ========== COMPLEMENT QUERIES ==========

    /**
     * Find complement by ID
     */
    @Transactional(readOnly = true)
    public Optional<Complement> findById(Long id) {
        Company company = CompanyContext.requireCurrentCompany();
        return complementRepository.findByIdComplementAndCompany(id, company);
    }

    /**
     * Find complement by ID with ingredients loaded (filtered by company)
     */
    @Transactional(readOnly = true)
    public Optional<Complement> findByIdWithIngredients(Long id) {
        Company company = CompanyContext.requireCurrentCompany();
        return complementRepository.findByIdWithIngredientsAndCompany(id, company);
    }

    /**
     * Find all complements (active and inactive) with their ingredients loaded (filtered by company)
     */
    @Transactional(readOnly = true)
    public List<Complement> findAllWithIngredients() {
        Company company = CompanyContext.requireCurrentCompany();
        return complementRepository.findAllWithIngredientsAndCompany(company);
    }

    /**
     * Find all active complements
     */
    @Transactional(readOnly = true)
    public List<Complement> findAllActive() {
        Company company = CompanyContext.requireCurrentCompany();
        return complementRepository.findByActiveTrueAndCompany(company);
    }

    /**
     * Find all active sauces (filtered by company)
     */
    @Transactional(readOnly = true)
    public List<Complement> findAllActiveSauces() {
        Company company = CompanyContext.requireCurrentCompany();
        return complementRepository.findByActiveTrueAndIsSauceTrueAndCompany(company);
    }

    /**
     * Find all available complements (active and in stock)
     */
    @Transactional(readOnly = true)
    public List<Complement> findAllAvailable() {
        Company company = CompanyContext.requireCurrentCompany();
        return complementRepository.findByActiveTrueAndAvailableTrueAndCompany(company);
    }

    /**
     * Search complements by name
     */
    @Transactional(readOnly = true)
    public List<Complement> searchByName(String name) {
        return complementRepository.findByNameContainingIgnoreCaseAndActiveTrue(name);
    }

    // ========== COMPLEMENT INGREDIENTS ==========

    /**
     * Add an ingredient to a complement's recipe
     */
    public ComplementIngredient addIngredientToComplement(Long complementId, Long ingredientId, 
                                                          BigDecimal quantity, String unit) {
        log.info("Adding ingredient {} to complement {} with quantity {} {}", 
                 ingredientId, complementId, quantity, unit);
        
        // Validate company ownership
        Company company = CompanyContext.requireCurrentCompany();
        Complement complement = complementRepository.findByIdComplementAndCompany(complementId, company)
                .orElseThrow(() -> new IllegalArgumentException("Complemento no encontrado"));
        
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado"));
        
        // Block adding inactive ingredients to complements
        if (!Boolean.TRUE.equals(ingredient.getActive())) {
            throw new IllegalArgumentException("No se puede asociar un ingrediente desactivado al complemento");
        }
        
        // Validate that for unit type "UN", quantity must be an integer
        if ("UN".equals(ingredient.getUnitOfMeasure())) {
            if (quantity.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException(
                    "Para ingredientes de tipo Unidad (UN), la cantidad debe ser un número entero. "
                    + "Por favor, ingrese una cantidad sin decimales (ej: 1, 2, 3)."
                );
            }
        }
        
        // Check if relationship already exists
        if (complementIngredientRepository.existsByComplementIdComplementAndIngredientIdIngredient(
                complementId, ingredientId)) {
            throw new IllegalArgumentException("Este ingrediente ya está asociado al complemento");
        }
        
        ComplementIngredient ci = ComplementIngredient.builder()
                .complement(complement)
                .ingredient(ingredient)
                .quantity(quantity)
                .unit(unit != null ? unit : ingredient.getUnitOfMeasure())
                .build();
        
        complement.addIngredient(ci);
        complementRepository.save(complement);
        
        // Update complement availability
        complement.updateAvailability();
        complementRepository.save(complement);
        
        return ci;
    }

    /**
     * Remove an ingredient from a complement's recipe
     */
    public void removeIngredientFromComplement(Long complementId, Long ingredientId) {
        log.info("Removing ingredient {} from complement {}", ingredientId, complementId);
        
        // Validate company ownership
        Company company = CompanyContext.requireCurrentCompany();
        Complement complement = complementRepository.findByIdComplementAndCompany(complementId, company)
                .orElseThrow(() -> new IllegalArgumentException("Complemento no encontrado"));
        
        ComplementIngredient ci = complementIngredientRepository
                .findByComplementIdComplementAndIngredientIdIngredient(complementId, ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("El ingrediente no está asociado al complemento"));
        
        complement.removeIngredient(ci);
        complementRepository.save(complement);
        
        // Update complement availability
        complement.updateAvailability();
        complementRepository.save(complement);
    }

    /**
     * Update ingredient quantity in complement recipe
     */
    public ComplementIngredient updateIngredientQuantity(Long complementId, Long ingredientId, 
                                                          BigDecimal newQuantity) {
        log.info("Updating ingredient {} quantity in complement {} to {}", 
                 ingredientId, complementId, newQuantity);
        
        ComplementIngredient ci = complementIngredientRepository
                .findByComplementIdComplementAndIngredientIdIngredient(complementId, ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("El ingrediente no está asociado al complemento"));
        
        // Validate that for unit type "UN", quantity must be an integer
        Ingredient ingredient = ci.getIngredient();
        if ("UN".equals(ingredient.getUnitOfMeasure())) {
            if (newQuantity.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException(
                    "Para ingredientes de tipo Unidad (UN), la cantidad debe ser un número entero. "
                    + "Por favor, ingrese una cantidad sin decimales (ej: 1, 2, 3)."
                );
            }
        }
        
        ci.setQuantity(newQuantity);
        ComplementIngredient saved = complementIngredientRepository.save(ci);
        
        // Update complement availability
        Complement complement = ci.getComplement();
        complement.updateAvailability();
        complementRepository.save(complement);
        
        return saved;
    }

    // ========== ITEM MENU COMPLEMENTS ==========

    /**
     * Associate a complement with a menu item
     * @param itemMenuId ID of the menu item
     * @param complementId ID of the complement
     * @param maxQuantity Maximum quantity allowed (must be >= 1)
     * @param displayOrder Order for displaying in UI
     */
    public ItemMenuComplement addComplementToItemMenu(Long itemMenuId, Long complementId, 
                                                       Integer maxQuantity, Integer displayOrder) {
        log.info("Adding complement {} to item menu {}", complementId, itemMenuId);
        
        // Validate maxQuantity
        if (maxQuantity == null || maxQuantity < 1) {
            throw new IllegalArgumentException("La cantidad máxima debe ser al menos 1");
        }
        
        // Validate company ownership
        Company company = CompanyContext.requireCurrentCompany();
        
        ItemMenu itemMenu = itemMenuRepository.findByIdItemMenuAndCompany(itemMenuId, company)
                .orElseThrow(() -> new IllegalArgumentException("Item de menú no encontrado"));
        
        Complement complement = complementRepository.findByIdComplementAndCompany(complementId, company)
                .orElseThrow(() -> new IllegalArgumentException("Complemento no encontrado"));
        
        // Block adding inactive complements to menu items
        if (!Boolean.TRUE.equals(complement.getActive())) {
            throw new IllegalArgumentException("No se puede asociar un complemento desactivado al item de menú");
        }
        
        // Check if relationship already exists
        if (itemMenuComplementRepository.existsByItemMenuIdItemMenuAndComplementIdComplement(
                itemMenuId, complementId)) {
            throw new IllegalArgumentException("Este complemento ya está asociado al item de menú");
        }
        
        // IMPORTANT: Sauces can only be selected once (maxQuantity = 1)
        Integer finalMaxQuantity = maxQuantity;
        if (Boolean.TRUE.equals(complement.getIsSauce())) {
            finalMaxQuantity = 1;
            log.info("Complement {} is a sauce, forcing maxQuantity to 1", complementId);
        }
        
        ItemMenuComplement imc = ItemMenuComplement.builder()
                .itemMenu(itemMenu)
                .complement(complement)
                .maxQuantity(finalMaxQuantity)
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .active(true)
                .build();
        
        return itemMenuComplementRepository.save(imc);
    }

    /**
     * Remove complement association from a menu item
     */
    public void removeComplementFromItemMenu(Long itemMenuId, Long complementId) {
        log.info("Removing complement {} from item menu {}", complementId, itemMenuId);
        
        ItemMenuComplement imc = itemMenuComplementRepository
                .findByItemMenuIdItemMenuAndComplementIdComplement(itemMenuId, complementId)
                .orElseThrow(() -> new IllegalArgumentException("La asociación no existe"));
        
        itemMenuComplementRepository.delete(imc);
    }

    /**
     * Update complement configuration for a menu item
     * @param id ID of the ItemMenuComplement
     * @param maxQuantity New maximum quantity (must be >= 1 if provided)
     * @param displayOrder New display order
     * @param active New active status
     */
    public ItemMenuComplement updateItemMenuComplement(Long id, Integer maxQuantity,
                                                        Integer displayOrder, Boolean active) {
        log.info("Updating item menu complement with ID: {}", id);
        
        ItemMenuComplement imc = itemMenuComplementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Asociación no encontrada"));
        
        if (maxQuantity != null) {
            if (maxQuantity < 1) {
                throw new IllegalArgumentException("La cantidad máxima debe ser al menos 1");
            }
            imc.setMaxQuantity(maxQuantity);
        }
        if (displayOrder != null) imc.setDisplayOrder(displayOrder);
        if (active != null) imc.setActive(active);
        
        return itemMenuComplementRepository.save(imc);
    }

    /**
     * Get all complements for a menu item (both available and unavailable)
     * Updates availability status dynamically based on current stock
     * This allows frontend to show unavailable items as disabled
     */
    @Transactional(readOnly = true)
    public List<ItemMenuComplement> getAvailableComplementsForItemMenu(Long itemMenuId) {
        // Get all active complements (don't filter by availability)
        List<ItemMenuComplement> complements = itemMenuComplementRepository
                .findActiveComplementsForItemMenuWithDetails(itemMenuId);
        
        // Update availability status for each complement based on current stock
        for (ItemMenuComplement imc : complements) {
            Complement complement = imc.getComplement();
            complement.updateAvailability();
        }
        
        return complements;
    }

    // ========== AVAILABILITY ==========

    /**
     * Update availability for all complements based on ingredient stock
     */
    public void updateAllComplementsAvailability() {
        log.info("Updating availability for all complements");
        
        List<Complement> allActive = complementRepository.findAllActiveWithIngredients();
        for (Complement complement : allActive) {
            complement.updateAvailability();
            complementRepository.save(complement);
        }
        
        log.info("Updated availability for {} complements", allActive.size());
    }

    /**
     * Update availability for a specific complement
     */
    public void updateComplementAvailability(Long complementId) {
        Complement complement = complementRepository.findByIdWithIngredients(complementId)
                .orElseThrow(() -> new IllegalArgumentException("Complemento no encontrado"));
        
        complement.updateAvailability();
        complementRepository.save(complement);
    }

    // ========== STOCK MANAGEMENT ==========

    /**
     * Check if there's enough stock for a complement selection
     * @param complementId The complement ID
     * @param quantity Quantity needed
     * @return true if there's enough stock
     */
    @Transactional(readOnly = true)
    public boolean hasEnoughStock(Long complementId, int quantity) {
        Complement complement = complementRepository.findByIdWithIngredients(complementId)
                .orElseThrow(() -> new IllegalArgumentException("Complemento no encontrado"));
        
        return complement.hasEnoughStock(quantity);
    }

    /**
     * Calculate maximum available portions for a complement
     */
    @Transactional(readOnly = true)
    public int calculateMaxPortions(Long complementId) {
        Complement complement = complementRepository.findByIdWithIngredients(complementId)
                .orElseThrow(() -> new IllegalArgumentException("Complemento no encontrado"));
        
        return complement.calculateMaxPortions();
    }
}
