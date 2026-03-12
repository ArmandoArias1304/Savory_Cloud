package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.IngredientCategoryService;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.application.service.SupplierService;
import com.aatechsolutions.elgransazon.domain.entity.Complement;
import com.aatechsolutions.elgransazon.domain.entity.ComplementIngredient;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.IngredientCategory;
import com.aatechsolutions.elgransazon.domain.entity.IngredientStockHistory;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.Supplier;
import com.aatechsolutions.elgransazon.domain.repository.ComplementIngredientRepository;
import com.aatechsolutions.elgransazon.domain.repository.IngredientStockHistoryRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.io.font.constants.StandardFonts;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.util.HashMap;

/**
 * Controller for Ingredient management
 * Accessible by ADMIN and MANAGER roles
 */
@Controller
@RequestMapping("/admin/ingredients")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class IngredientController {

    private final IngredientService ingredientService;
    private final IngredientCategoryService categoryService;
    private final SupplierService supplierService;
    private final EmployeeService employeeService;
    private final ItemMenuService itemMenuService;
    private final DateTimeService dateTimeService;
    private final IngredientStockHistoryRepository stockHistoryRepository;
    private final ComplementIngredientRepository complementIngredientRepository;
    private final com.aatechsolutions.elgransazon.domain.repository.ItemMenuComboItemRepository itemMenuComboItemRepository;

    /**
     * List all ingredients with optional filters
     */
    @GetMapping
    public String listIngredients(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) Boolean active,
            Model model) {

        log.info("Listing ingredients with filters - search: {}, categoryId: {}, supplierId: {}, sortBy: {}, active: {}",
                search, categoryId, supplierId, sortBy, active);

        // Show all ingredients (active and inactive) if no filter is specified
        Boolean activeFilter = active;

        // Get filtered ingredients
        List<Ingredient> ingredients = ingredientService.searchWithAllFilters(
                search, categoryId, supplierId, sortBy, activeFilter);

        // Get statistics for alerts
        long lowStockCount = ingredientService.countLowStock();
        long outOfStockCount = ingredientService.countOutOfStock();
        
        // Get general statistics
        long activeCount = ingredientService.getActiveCount();
        long inactiveCount = ingredientService.getInactiveCount();
        long totalCount = activeCount + inactiveCount; // Total de ingredientes (activos + inactivos)

        // Get all categories for filter dropdown
        List<IngredientCategory> allCategories = categoryService.findAllActive();

        // Get all suppliers for filter dropdown
        List<Supplier> allSuppliers = supplierService.findAllActive();

        model.addAttribute("ingredients", ingredients);
        model.addAttribute("search", search);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("supplierId", supplierId);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("active", activeFilter);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("inactiveCount", inactiveCount);
        model.addAttribute("lowStockCount", lowStockCount);
        model.addAttribute("outOfStockCount", outOfStockCount);
        model.addAttribute("allCategories", allCategories);
        model.addAttribute("allSuppliers", allSuppliers);

        return "admin/ingredients/list";
    }

    /**
     * Show form to create a new ingredient
     */
    @GetMapping("/new")
    public String showCreateForm(Model model, RedirectAttributes redirectAttributes) {
        log.info("Showing ingredient create form");

        // Verificar que existan categorías activas
        List<IngredientCategory> categories = categoryService.findAllActive();
        if (categories.isEmpty()) {
            log.warn("No active ingredient categories found. Cannot create ingredient.");
            redirectAttributes.addFlashAttribute("errorMessage", 
                "No hay categorías de ingredientes disponibles. Debes crear al menos una categoría antes de crear ingredientes.");
            return "redirect:/admin/ingredients";
        }

        Ingredient ingredient = new Ingredient();
        ingredient.setActive(true);
        ingredient.setCurrency("MXN");

        model.addAttribute("ingredient", ingredient);
        model.addAttribute("isEdit", false);
        model.addAttribute("allCategories", categories);

        return "admin/ingredients/form";
    }

    /**
     * Create a new ingredient
     */
    @PostMapping
    public String createIngredient(
            @Valid @ModelAttribute("ingredient") Ingredient ingredient,
            @RequestParam Long categoryId,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {

        log.info("Creating ingredient: {} with categoryId: {}", ingredient.getName(), categoryId);

        // Manually set the category from categoryId
        try {
            IngredientCategory category = categoryService.findById(categoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));
            ingredient.setCategory(category);
        } catch (IllegalArgumentException e) {
            log.error("Category not found with id: {}", categoryId);
            result.rejectValue("category", "error.ingredient", "Debe seleccionar una categoría válida");
        }

        // Validate that for unit type "UN", stock values are integers
        if ("UN".equals(ingredient.getUnitOfMeasure())) {
            if (ingredient.getCurrentStock() != null && ingredient.getCurrentStock().stripTrailingZeros().scale() > 0) {
                model.addAttribute("errorMessage", "Para unidades (UN), el stock actual debe ser un número entero.");
                model.addAttribute("isEdit", false);
                model.addAttribute("allCategories", categoryService.findAllActive());
                return "admin/ingredients/form";
            }
            if (ingredient.getMinStock() != null && ingredient.getMinStock().stripTrailingZeros().scale() > 0) {
                model.addAttribute("errorMessage", "Para unidades (UN), el stock mínimo debe ser un número entero.");
                model.addAttribute("isEdit", false);
                model.addAttribute("allCategories", categoryService.findAllActive());
                return "admin/ingredients/form";
            }
            if (ingredient.getMaxStock() != null && ingredient.getMaxStock().stripTrailingZeros().scale() > 0) {
                model.addAttribute("errorMessage", "Para unidades (UN), el stock máximo debe ser un número entero.");
                model.addAttribute("isEdit", false);
                model.addAttribute("allCategories", categoryService.findAllActive());
                return "admin/ingredients/form";
            }
        }

        if (result.hasErrors()) {
            log.error("Validation errors creating ingredient");
            model.addAttribute("isEdit", false);
            model.addAttribute("allCategories", categoryService.findAllActive());
            return "admin/ingredients/form";
        }

        try {
            // Create the ingredient
            Ingredient savedIngredient = ingredientService.create(ingredient);
            
            // If the ingredient has initial stock and cost, create a history record
            if (savedIngredient.getCurrentStock() != null && 
                savedIngredient.getCurrentStock().compareTo(BigDecimal.ZERO) > 0 &&
                savedIngredient.getCostPerUnit() != null && 
                savedIngredient.getCostPerUnit().compareTo(BigDecimal.ZERO) > 0) {
                
                try {
                    String username = authentication.getName();
                    Employee currentEmployee = employeeService.findByUsername(username)
                            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
                    
                    // Create initial stock history record WITHOUT adding more stock
                    // (stock was already set during ingredient creation)
                    IngredientStockHistory initialHistory = IngredientStockHistory.builder()
                            .ingredient(savedIngredient)
                            .quantityAdded(savedIngredient.getCurrentStock())
                            .costPerUnit(savedIngredient.getCostPerUnit())
                            .previousStock(BigDecimal.ZERO)
                            .newStock(savedIngredient.getCurrentStock())
                            .addedBy(currentEmployee)
                            .build();
                    
                    stockHistoryRepository.save(initialHistory);
                    
                    log.info("Initial stock history record created for ingredient: {} - Stock: {} - Cost: ${}",
                            savedIngredient.getName(), 
                            savedIngredient.getCurrentStock(),
                            initialHistory.getTotalCost());
                } catch (Exception e) {
                    log.warn("Could not create initial stock history for ingredient {}: {}", 
                            savedIngredient.getName(), e.getMessage());
                    // Continue without failing the ingredient creation
                }
            }
            
            redirectAttributes.addFlashAttribute("successMessage", "Ingrediente creado exitosamente");
            return "redirect:/admin/ingredients";
        } catch (DataAccessException e) {
            // JPA wrapped validation errors (from @PrePersist/@PreUpdate)
            log.error("Data access error creating ingredient: {}", e.getMessage());
            String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            model.addAttribute("errorMessage", errorMsg);
            model.addAttribute("isEdit", false);
            model.addAttribute("allCategories", categoryService.findAllActive());
            return "admin/ingredients/form";
        } catch (IllegalStateException e) {
            // Stock validation errors from entity
            log.error("Stock validation error creating ingredient: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", false);
            model.addAttribute("allCategories", categoryService.findAllActive());
            return "admin/ingredients/form";
        } catch (IllegalArgumentException e) {
            log.error("Error creating ingredient: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", false);
            model.addAttribute("allCategories", categoryService.findAllActive());
            return "admin/ingredients/form";
        }
    }

    /**
     * Show form to edit an existing ingredient
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Showing ingredient edit form for id: {}", id);

        try {
            Ingredient ingredient = ingredientService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado"));

            // Validar que el ingrediente esté activo para poder editarlo
            if (!ingredient.getActive()) {
                log.warn("Attempted to edit inactive ingredient with id: {}", id);
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "No se puede editar un ingrediente inactivo. Primero debes activarlo.");
                return "redirect:/admin/ingredients";
            }

            model.addAttribute("ingredient", ingredient);
            model.addAttribute("isEdit", true);
            model.addAttribute("allCategories", categoryService.findAllActive());

            return "admin/ingredients/form";
        } catch (IllegalArgumentException e) {
            log.error("Ingredient not found with id: {}", id);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/ingredients";
        }
    }

    /**
     * Update an existing ingredient
     */
    @PostMapping("/{id}")
    public String updateIngredient(
            @PathVariable Long id,
            @Valid @ModelAttribute("ingredient") Ingredient ingredient,
            @RequestParam Long categoryId,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Updating ingredient with id: {} and categoryId: {}", id, categoryId);

        // Validar que el ingrediente esté activo antes de actualizarlo
        try {
            Ingredient existingIngredient = ingredientService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado"));
            
            if (!existingIngredient.getActive()) {
                log.warn("Attempted to update inactive ingredient with id: {}", id);
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "No se puede editar un ingrediente inactivo. Primero debes activarlo.");
                return "redirect:/admin/ingredients";
            }
        } catch (IllegalArgumentException e) {
            log.error("Ingredient not found with id: {}", id);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/ingredients";
        }

        // Set the id from path variable to ensure it's preserved
        ingredient.setIdIngredient(id);

        // Manually set the category from categoryId
        try {
            IngredientCategory category = categoryService.findById(categoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));
            ingredient.setCategory(category);
        } catch (IllegalArgumentException e) {
            log.error("Category not found with id: {}", categoryId);
            result.rejectValue("category", "error.ingredient", "Debe seleccionar una categoría válida");
        }

        // Validate that for unit type "UN", stock values are integers
        if ("UN".equals(ingredient.getUnitOfMeasure())) {
            if (ingredient.getMinStock() != null && ingredient.getMinStock().stripTrailingZeros().scale() > 0) {
                model.addAttribute("errorMessage", "Para unidades (UN), el stock mínimo debe ser un número entero.");
                model.addAttribute("isEdit", true);
                model.addAttribute("allCategories", categoryService.findAllActive());
                return "admin/ingredients/form";
            }
        }

        if (result.hasErrors()) {
            log.error("Validation errors updating ingredient");
            model.addAttribute("isEdit", true);
            model.addAttribute("allCategories", categoryService.findAllActive());
            return "admin/ingredients/form";
        }

        try {
            ingredientService.update(id, ingredient);
            redirectAttributes.addFlashAttribute("successMessage", "Ingrediente actualizado exitosamente");
            return "redirect:/admin/ingredients";
        } catch (DataAccessException e) {
            // JPA wrapped validation errors (from @PrePersist/@PreUpdate)
            log.error("Data access error updating ingredient: {}", e.getMessage());
            String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            ingredient.setIdIngredient(id); // Preserve ID for form
            model.addAttribute("errorMessage", errorMsg);
            model.addAttribute("isEdit", true);
            model.addAttribute("allCategories", categoryService.findAllActive());
            return "admin/ingredients/form";
        } catch (IllegalStateException e) {
            // Stock validation errors from entity
            log.error("Stock validation error updating ingredient: {}", e.getMessage());
            ingredient.setIdIngredient(id); // Preserve ID for form
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", true);
            model.addAttribute("allCategories", categoryService.findAllActive());
            return "admin/ingredients/form";
        } catch (IllegalArgumentException e) {
            log.error("Error updating ingredient: {}", e.getMessage());
            ingredient.setIdIngredient(id); // Preserve ID for form
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", true);
            model.addAttribute("allCategories", categoryService.findAllActive());
            return "admin/ingredients/form";
        }
    }

    /**
     * Get menu items and complements affected by an ingredient (AJAX)
     * Returns the count and names of menu items that will be deactivated
     * and complements that will become unavailable
     * Includes both regular items and combo items that contain child items using this ingredient
     */
    @GetMapping("/{id}/affected-items")
    @ResponseBody
    public Map<String, Object> getAffectedItems(@PathVariable Long id) {
        log.info("Getting affected items for ingredient ID: {}", id);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get ingredient name
            Ingredient ingredient = ingredientService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado"));
            
            // Get menu items that use this ingredient DIRECTLY
            List<ItemMenu> directlyAffectedItems = itemMenuService.findByIngredientId(id);
            
            // Filter only active items
            List<ItemMenu> activeDirectlyAffectedItems = directlyAffectedItems.stream()
                    .filter(ItemMenu::getActive)
                    .collect(java.util.stream.Collectors.toList());
            
            // Find combo items that contain any of the affected items as children
            java.util.Set<ItemMenu> affectedCombos = new java.util.HashSet<>();
            for (ItemMenu affectedItem : activeDirectlyAffectedItems) {
                // Find combos that contain this item as a child
                List<com.aatechsolutions.elgransazon.domain.entity.ItemMenuComboItem> comboItems = 
                        itemMenuComboItemRepository.findByChildMenuIdItemMenu(affectedItem.getIdItemMenu());
                
                for (com.aatechsolutions.elgransazon.domain.entity.ItemMenuComboItem comboItem : comboItems) {
                    try {
                        ItemMenu combo = comboItem.getComboMenu();
                        // Only add active combos that aren't already in the directly affected list
                        if (combo != null && Boolean.TRUE.equals(combo.getActive()) && !activeDirectlyAffectedItems.contains(combo)) {
                            affectedCombos.add(combo);
                        }
                    } catch (Exception e) {
                        // Handle orphaned records - combo was deleted but relation still exists
                        log.warn("Found orphaned combo item relation: combo item id {} references non-existent combo", 
                                 comboItem.getIdComboItem());
                        // Skip this combo item and continue
                    }
                }
            }
            
            // Get complements that use this ingredient
            List<ComplementIngredient> complementIngredients = 
                    complementIngredientRepository.findByIngredientIdIngredient(id);
            
            // Filter only active complements
            List<Complement> activeAffectedComplements = complementIngredients.stream()
                    .map(ComplementIngredient::getComplement)
                    .filter(c -> c != null && Boolean.TRUE.equals(c.getActive()))
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
            
            // Separate regular items and combos for the response
            List<String> regularItemNames = activeDirectlyAffectedItems.stream()
                    .filter(item -> !Boolean.TRUE.equals(item.getIsCombo()))
                    .map(ItemMenu::getName)
                    .collect(java.util.stream.Collectors.toList());
            
            List<String> directlyAffectedComboNames = activeDirectlyAffectedItems.stream()
                    .filter(item -> Boolean.TRUE.equals(item.getIsCombo()))
                    .map(ItemMenu::getName)
                    .collect(java.util.stream.Collectors.toList());
            
            List<String> indirectlyAffectedComboNames = affectedCombos.stream()
                    .map(ItemMenu::getName)
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
            
            response.put("success", true);
            response.put("ingredientName", ingredient.getName());
            response.put("regularItemCount", regularItemNames.size());
            response.put("regularItems", regularItemNames);
            response.put("directlyAffectedCombosCount", directlyAffectedComboNames.size());
            response.put("directlyAffectedCombos", directlyAffectedComboNames);
            response.put("indirectlyAffectedCombosCount", indirectlyAffectedComboNames.size());
            response.put("indirectlyAffectedCombos", indirectlyAffectedComboNames);
            response.put("totalItemsCount", regularItemNames.size() + directlyAffectedComboNames.size() + indirectlyAffectedComboNames.size());
            response.put("complementCount", activeAffectedComplements.size());
            response.put("complements", activeAffectedComplements.stream()
                    .map(Complement::getName)
                    .collect(java.util.stream.Collectors.toList()));
            
            log.info("Found {} regular items, {} direct combos, {} indirect combos, and {} complements affected by ingredient: {}", 
                    regularItemNames.size(), directlyAffectedComboNames.size(), 
                    indirectlyAffectedComboNames.size(), activeAffectedComplements.size(), ingredient.getName());
            
        } catch (Exception e) {
            log.error("Error getting affected items: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        
        return response;
    }

    /**
     * Deactivate an ingredient (soft delete)
     * Also deactivates all menu items that use this ingredient
     */
    @PostMapping("/{id}/delete")
    public String deleteIngredient(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Deactivating ingredient with id: {}", id);

        try {
            // Get affected menu items before deactivating
            List<ItemMenu> affectedItems = itemMenuService.findByIngredientId(id);
            List<ItemMenu> activeAffectedItems = affectedItems.stream()
                    .filter(ItemMenu::getActive)
                    .collect(java.util.stream.Collectors.toList());
            
            // Get complements that use this ingredient
            List<ComplementIngredient> complementIngredients = 
                    complementIngredientRepository.findByIngredientIdIngredient(id);
            
            // Get active complements that use this ingredient
            List<Complement> activeAffectedComplements = complementIngredients.stream()
                    .map(ComplementIngredient::getComplement)
                    .filter(c -> c != null && Boolean.TRUE.equals(c.getActive()))
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
            
            // Deactivate the ingredient (this also sets stock to zero)
            ingredientService.delete(id);
            
            // Update availability for affected menu items (they remain active but unavailable)
            // Since stock is now zero, items will show as "Sin Stock" in the menu
            for (ItemMenu item : activeAffectedItems) {
                itemMenuService.updateItemAvailability(item.getIdItemMenu());
            }
            
            // Note: Complement availability is calculated dynamically when loading the view
            // based on current ingredient stock, so no need to update them here
            
            log.info("Updated availability for {} menu items. {} complements will be affected dynamically.", 
                    activeAffectedItems.size(), activeAffectedComplements.size());
            
            StringBuilder message = new StringBuilder("Ingrediente desactivado exitosamente.");
            if (!activeAffectedItems.isEmpty()) {
                message.append(String.format(" %d items del menú ahora aparecen como 'Sin Stock'.", 
                        activeAffectedItems.size()));
            }
            if (!activeAffectedComplements.isEmpty()) {
                message.append(String.format(" %d complementos quedarán sin stock disponible.", 
                        activeAffectedComplements.size()));
            }
            redirectAttributes.addFlashAttribute("successMessage", message.toString());
            
        } catch (IllegalArgumentException e) {
            log.error("Error deactivating ingredient: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error deactivating ingredient: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al desactivar el ingrediente");
        }

        return "redirect:/admin/ingredients";
    }

    /**
     * Activate an ingredient
     */
    @PostMapping("/{id}/activate")
    public String activateIngredient(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Activating ingredient with id: {}", id);

        try {
            ingredientService.activate(id);
            redirectAttributes.addFlashAttribute("successMessage", "Ingrediente activado exitosamente");
        } catch (IllegalArgumentException e) {
            log.error("Error activating ingredient: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/ingredients";
    }

    /**
     * Add stock to an existing ingredient
     */
    @PostMapping("/{id}/add-stock")
    public String addStock(
            @PathVariable Long id,
            @RequestParam BigDecimal quantityToAdd,
            @RequestParam BigDecimal costPerUnit,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {

        log.info("Adding stock to ingredient ID: {} - Quantity: {} - Cost: ${}", id, quantityToAdd, costPerUnit);

        try {
            if (quantityToAdd.compareTo(BigDecimal.ZERO) <= 0) {
                redirectAttributes.addFlashAttribute("errorMessage", "La cantidad debe ser mayor a 0");
                return "redirect:/admin/ingredients/" + id + "/edit";
            }

            if (costPerUnit.compareTo(BigDecimal.ZERO) < 0) {
                redirectAttributes.addFlashAttribute("errorMessage", "El costo por unidad debe ser mayor o igual a 0");
                return "redirect:/admin/ingredients/" + id + "/edit";
            }

            // Get current ingredient to check unit and validate
            Ingredient ingredient = ingredientService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado"));
            
            // Validate that for unit type "UN", only integers are allowed
            if ("UN".equals(ingredient.getUnitOfMeasure()) && quantityToAdd.stripTrailingZeros().scale() > 0) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Para unidades (UN), solo se permiten números enteros. No puede agregar cantidades decimales de unidades.");
                return "redirect:/admin/ingredients/" + id + "/edit";
            }

            Employee currentEmployee = employeeService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            
            // Calculate what the new stock will be
            BigDecimal newStock = ingredient.getCurrentStock().add(quantityToAdd);
            
            // CRITICAL FIX: Validate that resulting stock meets minimum stock requirement
            if (ingredient.getMinStock() != null && newStock.compareTo(ingredient.getMinStock()) < 0) {
                BigDecimal shortfall = ingredient.getMinStock().subtract(ingredient.getCurrentStock());
                redirectAttributes.addFlashAttribute("errorMessage",
                        String.format("El stock resultante (%.2f %s) no alcanza el mínimo requerido (%.2f %s). " +
                                     "Debe agregar al menos %.2f %s para alcanzar el stock mínimo.",
                                newStock, ingredient.getUnitOfMeasure(),
                                ingredient.getMinStock(), ingredient.getUnitOfMeasure(),
                                shortfall, ingredient.getUnitOfMeasure()));
                return "redirect:/admin/ingredients/" + id + "/edit";
            }
            
            // Update maxStock if it's null, zero, or less than the new stock
            // This prevents validation errors when minStock > maxStock
            if (ingredient.getMaxStock() == null || 
                ingredient.getMaxStock().compareTo(BigDecimal.ZERO) == 0 || 
                newStock.compareTo(ingredient.getMaxStock()) > 0) {
                ingredient.setMaxStock(newStock);
                ingredientService.update(id, ingredient);
                log.info("Stock máximo actualizado automáticamente a {} para el ingrediente {}", 
                        newStock, ingredient.getName());
            }

            ingredientService.addStock(id, quantityToAdd, costPerUnit, currentEmployee);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Stock agregado exitosamente. Se ha registrado en el historial.");

        } catch (Exception e) {
            log.error("Error al agregar stock al ingrediente {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al agregar stock: " + e.getMessage());
        }

        return "redirect:/admin/ingredients/" + id + "/edit";
    }

    /**
     * Show stock history for an ingredient
     */
    @GetMapping("/{id}/stock-history")
    public String showStockHistory(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Showing stock history for ingredient ID: {}", id);

        try {
            Ingredient ingredient = ingredientService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado"));

            List<IngredientStockHistory> history = ingredientService.getStockHistory(id);
            BigDecimal totalCost = ingredientService.getTotalCostByIngredient(id);

            model.addAttribute("ingredient", ingredient);
            model.addAttribute("history", history);
            model.addAttribute("totalCost", totalCost);

            return "admin/ingredients/stock-history";

        } catch (Exception e) {
            log.error("Error al cargar historial de stock: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar el historial: " + e.getMessage());
            return "redirect:/admin/ingredients";
        }
    }

    /**
     * Download PDF report of ingredients based on stock status
     */
    @GetMapping("/download-stock-pdf")
    public ResponseEntity<byte[]> downloadStockPDF(
            @RequestParam(required = false) Boolean includeAll,
            @RequestParam(required = false) Boolean includeHealthy,
            @RequestParam(required = false) Boolean includeLow,
            @RequestParam(required = false) Boolean includeOut) {

        log.info("Generating stock PDF - includeAll: {}, includeHealthy: {}, includeLow: {}, includeOut: {}", 
                includeAll, includeHealthy, includeLow, includeOut);

        try {
            // Obtener todos los ingredientes activos
            List<Ingredient> allIngredients = ingredientService.searchWithAllFilters(null, null, null, null, true);
            List<Ingredient> filteredIngredients;

            // Si se solicita el inventario completo, incluir todos los ingredientes
            if (Boolean.TRUE.equals(includeAll)) {
                filteredIngredients = new ArrayList<>(allIngredients);
            } else {
                // Filtrar según los parámetros específicos
                filteredIngredients = new ArrayList<>();
                for (Ingredient ingredient : allIngredients) {
                    boolean shouldInclude = false;

                    if (Boolean.TRUE.equals(includeHealthy) && ingredient.isHealthyStock()) {
                        shouldInclude = true;
                    }
                    if (Boolean.TRUE.equals(includeLow) && ingredient.isLowStock()) {
                        shouldInclude = true;
                    }
                    if (Boolean.TRUE.equals(includeOut) && ingredient.isOutOfStock()) {
                        shouldInclude = true;
                    }

                    if (shouldInclude) {
                        filteredIngredients.add(ingredient);
                    }
                }
            }

            // Generar el PDF con diseño profesional
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.LETTER);
            document.setMargins(40, 40, 40, 40);

            // Colores del tema
            DeviceRgb primaryColor = new DeviceRgb(56, 224, 123); // #38e07b
            DeviceRgb primaryDark = new DeviceRgb(43, 200, 102); // #2bc866
            DeviceRgb darkColor = new DeviceRgb(45, 45, 45);
            DeviceRgb grayColor = new DeviceRgb(107, 114, 128);
            DeviceRgb lightGray = new DeviceRgb(249, 250, 251);

            // Fuentes
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            // ========== HEADER ==========
            Table headerTable = new Table(UnitValue.createPercentArray(new float[]{1, 3}));
            headerTable.setWidth(UnitValue.createPercentValue(100));
            headerTable.setBorder(Border.NO_BORDER);

            // Generar iniciales dinámicas según el tipo de descarga
            StringBuilder logoInitials = new StringBuilder("S"); // "S" de Stock
            if (Boolean.TRUE.equals(includeAll)) {
                // Solo "S" para inventario completo
            } else {
                // Agregar iniciales según los filtros
                if (Boolean.TRUE.equals(includeHealthy)) {
                    logoInitials.append("O"); // "O" de OK
                }
                if (Boolean.TRUE.equals(includeLow)) {
                    logoInitials.append("B"); // "B" de Bajo
                }
                if (Boolean.TRUE.equals(includeOut)) {
                    logoInitials.append("A"); // "A" de Agotado
                }
            }

            // Ajustar tamaño de fuente según la cantidad de letras
            int fontSize = logoInitials.length() <= 2 ? 36 : (logoInitials.length() == 3 ? 28 : 24);

            // Logo/Icon placeholder (left column)
            Cell logoCell = new Cell()
                    .add(new Paragraph(logoInitials.toString())
                            .setFont(boldFont)
                            .setFontSize(fontSize)
                            .setFontColor(ColorConstants.WHITE)
                            .setTextAlignment(TextAlignment.CENTER))
                    .setBackgroundColor(primaryColor)
                    .setWidth(60)
                    .setHeight(60)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setBorder(Border.NO_BORDER);
            headerTable.addCell(logoCell);

            // Title and subtitle (right column)
            Div titleDiv = new Div();
            titleDiv.add(new Paragraph("REPORTE DE INVENTARIO")
                    .setFont(boldFont)
                    .setFontSize(20)
                    .setFontColor(darkColor)
                    .setMarginBottom(2));
            titleDiv.add(new Paragraph("Control de Stock de Ingredientes")
                    .setFont(regularFont)
                    .setFontSize(11)
                    .setFontColor(grayColor));

            Cell titleCell = new Cell()
                    .add(titleDiv)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setBorder(Border.NO_BORDER)
                    .setPaddingLeft(15);
            headerTable.addCell(titleCell);

            document.add(headerTable);

            // Divider line
            document.add(new Paragraph()
                    .setBorderTop(new SolidBorder(primaryColor, 2))
                    .setMarginTop(10)
                    .setMarginBottom(20));

            // ========== FECHA Y FILTROS ==========
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            
            // Fecha de generación
            Paragraph dateTime = new Paragraph()
                    .add(new Paragraph("Fecha de generación: ")
                            .setFont(boldFont)
                            .setFontSize(10)
                            .setFontColor(darkColor))
                    .add(new Paragraph(dateTimeService.nowLocal().format(formatter))
                            .setFont(regularFont)
                            .setFontSize(10)
                            .setFontColor(grayColor))
                    .setMarginBottom(8);
            document.add(dateTime);

            // Filtros aplicados
            StringBuilder filtersApplied = new StringBuilder();
            List<String> filterNames = new ArrayList<>();
            if (Boolean.TRUE.equals(includeAll)) {
                filterNames.add("Inventario Completo");
            } else {
                if (Boolean.TRUE.equals(includeHealthy)) filterNames.add("Stock OK");
                if (Boolean.TRUE.equals(includeLow)) filterNames.add("Stock Bajo");
                if (Boolean.TRUE.equals(includeOut)) filterNames.add("Stock Agotado");
            }
            filtersApplied.append(String.join(", ", filterNames));

            Paragraph filters = new Paragraph()
                    .add(new Paragraph("Filtros aplicados: ")
                            .setFont(boldFont)
                            .setFontSize(10)
                            .setFontColor(darkColor))
                    .add(new Paragraph(filtersApplied.toString())
                            .setFont(regularFont)
                            .setFontSize(10)
                            .setFontColor(primaryDark))
                    .setMarginBottom(20);
            document.add(filters);

            // ========== RESUMEN ESTADÍSTICO ==========
            // Calcular estadísticas
            int totalIngredients = filteredIngredients.size();
            long healthyCount = filteredIngredients.stream().filter(Ingredient::isHealthyStock).count();
            long lowCount = filteredIngredients.stream().filter(Ingredient::isLowStock).count();
            long outCount = filteredIngredients.stream().filter(Ingredient::isOutOfStock).count();

            // Tabla de resumen
            Table summaryTable = new Table(new float[]{1, 1, 1, 1});
            summaryTable.setWidth(UnitValue.createPercentValue(100));
            summaryTable.setMarginBottom(20);

            addSummaryCard(summaryTable, boldFont, regularFont, "Total Ingredientes", 
                    String.valueOf(totalIngredients), primaryColor);
            addSummaryCard(summaryTable, boldFont, regularFont, "Stock OK", 
                    String.valueOf(healthyCount), new DeviceRgb(34, 197, 94)); // green-500
            addSummaryCard(summaryTable, boldFont, regularFont, "Stock Bajo", 
                    String.valueOf(lowCount), new DeviceRgb(251, 191, 36)); // yellow-400
            addSummaryCard(summaryTable, boldFont, regularFont, "Agotados", 
                    String.valueOf(outCount), new DeviceRgb(239, 68, 68)); // red-500

            document.add(summaryTable);

            // ========== SECCIÓN: DETALLE DE INGREDIENTES ==========
            addSectionTitle(document, boldFont, darkColor, primaryColor, "Detalle de Ingredientes");

            // ========== TABLA DE INGREDIENTES ==========
            float[] columnWidths = {3.5f, 2f, 1.5f, 1.5f, 1.5f, 2f};
            Table table = new Table(UnitValue.createPercentArray(columnWidths));
            table.setWidth(UnitValue.createPercentValue(100));

            // Encabezados con diseño moderno
            String[] headers = {"Ingrediente", "Categoría", "Stock Actual", "Stock Mín.", "Unidad", "Estado"};
            for (String header : headers) {
                Cell headerCell = new Cell()
                        .add(new Paragraph(header)
                                .setFont(boldFont)
                                .setFontSize(9)
                                .setFontColor(ColorConstants.WHITE))
                        .setBackgroundColor(primaryDark)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setPadding(8)
                        .setBorder(Border.NO_BORDER);
                table.addHeaderCell(headerCell);
            }

            // Filas de datos con alternancia de colores
            boolean alternate = false;
            for (Ingredient ingredient : filteredIngredients) {
                DeviceRgb rowColor = alternate ? lightGray : new DeviceRgb(255, 255, 255);
                
                // Nombre
                table.addCell(createStyledCell(ingredient.getName(), regularFont, 
                        TextAlignment.LEFT, rowColor, darkColor));
                
                // Categoría
                String categoryName = ingredient.getCategory() != null ? 
                        ingredient.getCategory().getName() : "Sin categoría";
                table.addCell(createStyledCell(categoryName, regularFont, 
                        TextAlignment.LEFT, rowColor, grayColor));
                
                // Stock Actual
                table.addCell(createStyledCell(ingredient.getCurrentStock().toString(), 
                        regularFont, TextAlignment.CENTER, rowColor, darkColor));
                
                // Stock Mínimo
                table.addCell(createStyledCell(ingredient.getMinStock().toString(), 
                        regularFont, TextAlignment.CENTER, rowColor, grayColor));
                
                // Unidad
                table.addCell(createStyledCell(ingredient.getUnitOfMeasure(), 
                        regularFont, TextAlignment.CENTER, rowColor, grayColor));
                
                // Estado con badge colorido
                table.addCell(createStatusBadge(ingredient, boldFont, rowColor));
                
                alternate = !alternate;
            }

            document.add(table);

            // ========== RESUMEN FINAL ==========
            document.add(new Paragraph("\n"));
            Table footerSummary = new Table(1);
            footerSummary.setWidth(UnitValue.createPercentValue(100));
            footerSummary.setBorder(Border.NO_BORDER);
            
            Cell summaryCell = new Cell()
                    .add(new Paragraph("RESUMEN: ")
                            .setFont(boldFont)
                            .setFontSize(10)
                            .setFontColor(darkColor)
                            .add(new Paragraph("Total de " + filteredIngredients.size() + 
                                    " ingrediente(s) en este reporte")
                                    .setFont(regularFont)
                                    .setFontColor(grayColor)))
                    .setBackgroundColor(lightGray)
                    .setPadding(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setBorder(Border.NO_BORDER);
            footerSummary.addCell(summaryCell);
            document.add(footerSummary);

            // ========== FOOTER ==========
            document.add(new Paragraph()
                    .setBorderTop(new SolidBorder(lightGray, 1))
                    .setMarginTop(20)
                    .setMarginBottom(10));

            Paragraph footer = new Paragraph()
                    .add(new Paragraph("Reporte generado el " + 
                            dateTimeService.nowLocal().format(formatter))
                            .setFont(regularFont)
                            .setFontSize(7)
                            .setFontColor(grayColor))
                    .setTextAlignment(TextAlignment.CENTER);
            document.add(footer);

            // Cerrar el documento
            document.close();

            // Configurar la respuesta HTTP
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.APPLICATION_PDF);
            String filename = "Reporte_Inventario_" + 
                    dateTimeService.nowLocal().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf";
            responseHeaders.setContentDispositionFormData("attachment", filename);

            return ResponseEntity.ok()
                    .headers(responseHeaders)
                    .body(baos.toByteArray());

        } catch (Exception e) {
            log.error("Error generating PDF: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Helper method to add summary cards
     */
    private void addSummaryCard(Table table, PdfFont boldFont, PdfFont regularFont, 
                                String label, String value, DeviceRgb color) {
        Cell cell = new Cell()
                .add(new Paragraph(value)
                        .setFont(boldFont)
                        .setFontSize(20)
                        .setFontColor(color)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(5))
                .add(new Paragraph(label)
                        .setFont(regularFont)
                        .setFontSize(9)
                        .setFontColor(new DeviceRgb(107, 114, 128))
                        .setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(new DeviceRgb(249, 250, 251))
                .setPadding(15)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER);
        table.addCell(cell);
    }

    /**
     * Helper method to add section titles
     */
    private void addSectionTitle(Document document, PdfFont boldFont, 
                                 DeviceRgb darkColor, DeviceRgb primaryColor, String title) {
        Div titleDiv = new Div();
        titleDiv.add(new Paragraph(title)
                .setFont(boldFont)
                .setFontSize(14)
                .setFontColor(darkColor)
                .setMarginBottom(5));
        titleDiv.add(new Paragraph()
                .setBorderBottom(new SolidBorder(primaryColor, 2))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(15));
        document.add(titleDiv);
    }

    /**
     * Helper method to create styled cells for the table
     */
    private Cell createStyledCell(String content, PdfFont font, TextAlignment alignment, 
                                  DeviceRgb bgColor, DeviceRgb textColor) {
        return new Cell()
                .add(new Paragraph(content)
                        .setFont(font)
                        .setFontSize(9)
                        .setFontColor(textColor))
                .setBackgroundColor(bgColor)
                .setTextAlignment(alignment)
                .setPadding(8)
                .setBorder(Border.NO_BORDER);
    }

    /**
     * Helper method to create status badge with colors
     */
    private Cell createStatusBadge(Ingredient ingredient, PdfFont boldFont, DeviceRgb bgColor) {
        String status = ingredient.getStockStatus();
        DeviceRgb badgeColor;
        DeviceRgb textColor;

        if (ingredient.isOutOfStock()) {
            badgeColor = new DeviceRgb(254, 226, 226); // red-100
            textColor = new DeviceRgb(220, 38, 38); // red-600
        } else if (ingredient.isLowStock()) {
            badgeColor = new DeviceRgb(254, 249, 195); // yellow-100
            textColor = new DeviceRgb(202, 138, 4); // yellow-600
        } else {
            badgeColor = new DeviceRgb(220, 252, 231); // green-100
            textColor = new DeviceRgb(22, 163, 74); // green-600
        }

        Paragraph badge = new Paragraph(status)
                .setFont(boldFont)
                .setFontSize(8)
                .setFontColor(textColor)
                .setBackgroundColor(badgeColor)
                .setPadding(4)
                .setPaddingLeft(8)
                .setPaddingRight(8)
                .setTextAlignment(TextAlignment.CENTER);

        return new Cell()
                .add(badge)
                .setBackgroundColor(bgColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setPadding(8)
                .setBorder(Border.NO_BORDER);
    }
}
