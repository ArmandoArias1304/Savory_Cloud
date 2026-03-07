package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.IngredientCategoryService;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.application.service.SupplierService;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.IngredientCategory;
import com.aatechsolutions.elgransazon.domain.entity.Supplier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Controller for Supplier management
 * Accessible by ADMIN and MANAGER roles
 */
@Controller
@RequestMapping("/admin/suppliers")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class SupplierController {

    private final SupplierService supplierService;
    private final IngredientCategoryService categoryService;
    private final IngredientService ingredientService;

    /**
     * List all suppliers with optional filters
     */
    @GetMapping
    public String listSuppliers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long ingredientId,
            @RequestParam(required = false) Boolean active,
            Model model) {

        log.info("Listing suppliers with filters - search: {}, rating: {}, categoryId: {}, ingredientId: {}, active: {}",
                search, rating, categoryId, ingredientId, active);

        // Show all suppliers (active and inactive) by default if no filter is specified
        Boolean activeFilter = active;

        List<Supplier> suppliers;
        String filterMessage = null;

        // Special case: Filter by ingredient (show suppliers for that ingredient's category)
        if (ingredientId != null) {
            try {
                Ingredient ingredient = ingredientService.findById(ingredientId)
                        .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado"));

                if (ingredient.getCategory() == null) {
                    model.addAttribute("errorMessage", "El ingrediente no tiene una categoría asignada");
                    suppliers = Collections.emptyList();
                } else {
                    // Get suppliers for this ingredient's category
                    suppliers = supplierService.findByCategoryId(ingredient.getCategory().getIdCategory());

                    // Apply additional search filter if provided
                    if (search != null && !search.trim().isEmpty()) {
                        final String searchLower = search.toLowerCase();
                        suppliers = suppliers.stream()
                                .filter(s -> s.getName().toLowerCase().contains(searchLower) ||
                                           (s.getContactPerson() != null && s.getContactPerson().toLowerCase().contains(searchLower)) ||
                                           (s.getEmail() != null && s.getEmail().toLowerCase().contains(searchLower)))
                                .toList();
                    }

                    filterMessage = "Mostrando proveedores que surten: " + ingredient.getName() +
                                  " (Categoría: " + ingredient.getCategory().getName() + ")";
                    model.addAttribute("ingredientName", ingredient.getName());
                    model.addAttribute("categoryName", ingredient.getCategory().getName());
                }
            } catch (Exception e) {
                log.error("Error filtering suppliers by ingredient: {}", e.getMessage());
                model.addAttribute("errorMessage", "Error al filtrar proveedores: " + e.getMessage());
                suppliers = Collections.emptyList();
            }
        } else {
            // Normal filtering
            suppliers = supplierService.searchWithFilters(search, rating, categoryId, activeFilter);
        }

        // Get statistics
        long activeCount = supplierService.getActiveCount();
        long inactiveCount = supplierService.getInactiveCount();
        long totalCount = activeCount + inactiveCount; // Total de proveedores (activos + inactivos)

        // Get all categories for filter dropdown
        List<IngredientCategory> allCategories = categoryService.findAllActive();

        model.addAttribute("suppliers", suppliers);
        model.addAttribute("search", search);
        model.addAttribute("rating", rating);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("ingredientId", ingredientId);
        model.addAttribute("active", activeFilter);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("inactiveCount", inactiveCount);
        model.addAttribute("allCategories", allCategories);
        model.addAttribute("filterMessage", filterMessage);

        return "admin/suppliers/list";
    }

    /**
     * Show form to create a new supplier
     */
    @GetMapping("/new")
    public String showCreateForm(Model model, RedirectAttributes redirectAttributes) {
        log.info("Showing supplier create form");

        // Verificar que existan categorías activas
        List<IngredientCategory> categories = categoryService.findAllActive();
        if (categories.isEmpty()) {
            log.warn("No active ingredient categories found. Cannot create supplier.");
            redirectAttributes.addFlashAttribute("errorMessage", 
                "No hay categorías de ingredientes disponibles. Debes crear al menos una categoría antes de crear proveedores.");
            return "redirect:/admin/suppliers";
        }
        
        model.addAttribute("supplier", new Supplier());
        model.addAttribute("isEdit", false);
        model.addAttribute("allCategories", categories);
        
        return "admin/suppliers/form";
    }

    /**
     * Create a new supplier
     */
    @PostMapping
    public String createSupplier(
            @Valid @ModelAttribute("supplier") Supplier supplier,
            BindingResult result,
            @RequestParam(required = false) List<Long> categoryIds,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Creating supplier: {}", supplier.getName());

        if (result.hasErrors()) {
            log.error("Validation errors creating supplier");
            model.addAttribute("isEdit", false);
            model.addAttribute("allCategories", categoryService.findAllActive());
            return "admin/suppliers/form";
        }

        try {
            // Set categories
            if (categoryIds != null && !categoryIds.isEmpty()) {
                Set<IngredientCategory> categories = new HashSet<>();
                for (Long categoryId : categoryIds) {
                    categoryService.findById(categoryId).ifPresent(categories::add);
                }
                supplier.setCategories(categories);
            }

            supplierService.create(supplier);
            redirectAttributes.addFlashAttribute("successMessage", "Proveedor creado exitosamente");
            return "redirect:/admin/suppliers";
        } catch (IllegalArgumentException e) {
            log.error("Error creating supplier: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", false);
            model.addAttribute("allCategories", categoryService.findAllActive());
            return "admin/suppliers/form";
        }
    }

    /**
     * Show form to edit an existing supplier
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Showing supplier edit form for id: {}", id);

        try {
            Supplier supplier = supplierService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Proveedor no encontrado"));

            model.addAttribute("supplier", supplier);
            model.addAttribute("isEdit", true);
            model.addAttribute("allCategories", categoryService.findAllActive());

            return "admin/suppliers/form";
        } catch (IllegalArgumentException e) {
            log.error("Supplier not found with id: {}", id);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/suppliers";
        }
    }

    /**
     * Update an existing supplier
     */
    @PostMapping("/{id}")
    public String updateSupplier(
            @PathVariable Long id,
            @Valid @ModelAttribute("supplier") Supplier supplier,
            BindingResult result,
            @RequestParam(required = false) List<Long> categoryIds,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Updating supplier with id: {}", id);

        if (result.hasErrors()) {
            log.error("Validation errors updating supplier");
            model.addAttribute("isEdit", true);
            model.addAttribute("allCategories", categoryService.findAllActive());
            return "admin/suppliers/form";
        }

        try {
            // Set categories
            if (categoryIds != null && !categoryIds.isEmpty()) {
                Set<IngredientCategory> categories = new HashSet<>();
                for (Long categoryId : categoryIds) {
                    categoryService.findById(categoryId).ifPresent(categories::add);
                }
                supplier.setCategories(categories);
            } else {
                supplier.setCategories(new HashSet<>());
            }

            supplierService.update(id, supplier);
            redirectAttributes.addFlashAttribute("successMessage", "Proveedor actualizado exitosamente");
            return "redirect:/admin/suppliers";
        } catch (IllegalArgumentException e) {
            log.error("Error updating supplier: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", true);
            model.addAttribute("allCategories", categoryService.findAllActive());
            return "admin/suppliers/form";
        }
    }

    /**
     * Deactivate a supplier (soft delete)
     */
    @PostMapping("/{id}/delete")
    public String deleteSupplier(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Deactivating supplier with id: {}", id);

        try {
            supplierService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Proveedor desactivado exitosamente");
        } catch (IllegalArgumentException e) {
            log.error("Error deactivating supplier: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/suppliers";
    }

    /**
     * Activate a supplier
     */
    @PostMapping("/{id}/activate")
    public String activateSupplier(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Activating supplier with id: {}", id);

        try {
            supplierService.activate(id);
            redirectAttributes.addFlashAttribute("successMessage", "Proveedor activado exitosamente");
        } catch (IllegalArgumentException e) {
            log.error("Error activating supplier: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/suppliers";
    }

    /**
     * Permanently delete a supplier from database
     */
    @PostMapping("/{id}/permanent-delete")
    public String permanentDeleteSupplier(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Permanently deleting supplier with id: {}", id);

        try {
            supplierService.permanentDelete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Proveedor eliminado permanentemente de la base de datos");
        } catch (IllegalArgumentException e) {
            log.error("Error permanently deleting supplier: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/suppliers";
    }
}
