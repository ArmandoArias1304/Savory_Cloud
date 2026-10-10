package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.InventoryObjectCategoryService;
import com.aatechsolutions.elgransazon.domain.entity.InventoryObjectCategory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Controller for InventoryObjectCategory management
 * Accessible by ADMIN and MANAGER roles
 */
@Controller
@RequestMapping("/admin/inventory/object-categories")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class InventoryObjectCategoryController {

    private final InventoryObjectCategoryService categoryService;

    /**
     * List all object categories with optional filters
     */
    @GetMapping
    public String listCategories(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            Model model) {

        log.info("Listing object categories with filters - search: {}, active: {}", search, active);

        Boolean activeFilter = (active != null) ? active : true;

        List<InventoryObjectCategory> categories = categoryService.searchWithFilters(search, activeFilter);

        long activeCount = categoryService.getActiveCount();
        long inactiveCount = categoryService.getInactiveCount();

        model.addAttribute("categories", categories);
        model.addAttribute("search", search);
        model.addAttribute("active", activeFilter);
        model.addAttribute("totalCount", activeCount + inactiveCount);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("inactiveCount", inactiveCount);

        return "admin/inventory/object-categories/list";
    }

    /**
     * Show form to create a new object category
     */
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        log.info("Showing object category create form");

        model.addAttribute("category", new InventoryObjectCategory());
        model.addAttribute("isEdit", false);

        return "admin/inventory/object-categories/form";
    }

    /**
     * Create a new object category
     */
    @PostMapping
    public String createCategory(
            @Valid @ModelAttribute("category") InventoryObjectCategory category,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Creating object category: {}", category.getName());

        if (result.hasErrors()) {
            log.error("Validation errors creating object category");
            model.addAttribute("isEdit", false);
            return "admin/inventory/object-categories/form";
        }

        try {
            categoryService.create(category);
            redirectAttributes.addFlashAttribute("successMessage", "Categoría de objetos creada exitosamente");
            return "redirect:/admin/inventory/object-categories";
        } catch (IllegalArgumentException e) {
            log.error("Error creating object category: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", false);
            return "admin/inventory/object-categories/form";
        }
    }

    /**
     * Show form to edit an existing object category
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Showing object category edit form for id: {}", id);

        try {
            InventoryObjectCategory category = categoryService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Categoría de objetos no encontrada"));

            model.addAttribute("category", category);
            model.addAttribute("isEdit", true);

            return "admin/inventory/object-categories/form";
        } catch (IllegalArgumentException e) {
            log.error("Object category not found with id: {}", id);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/inventory/object-categories";
        }
    }

    /**
     * Update an existing object category
     */
    @PostMapping("/{id}")
    public String updateCategory(
            @PathVariable Long id,
            @Valid @ModelAttribute("category") InventoryObjectCategory category,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Updating object category with id: {}", id);

        if (result.hasErrors()) {
            log.error("Validation errors updating object category");
            model.addAttribute("isEdit", true);
            return "admin/inventory/object-categories/form";
        }

        try {
            categoryService.update(id, category);
            redirectAttributes.addFlashAttribute("successMessage", "Categoría de objetos actualizada exitosamente");
            return "redirect:/admin/inventory/object-categories";
        } catch (IllegalArgumentException e) {
            log.error("Error updating object category: {}", e.getMessage());
            category.setIdCategory(id);
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", true);
            return "admin/inventory/object-categories/form";
        }
    }

    /**
     * Deactivate an object category (soft delete)
     */
    @PostMapping("/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Deactivating object category with id: {}", id);

        try {
            categoryService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Categoría de objetos desactivada exitosamente");
        } catch (Exception e) {
            log.error("Error deactivating object category: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/inventory/object-categories";
    }

    /**
     * Activate an object category
     */
    @PostMapping("/{id}/activate")
    public String activateCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Activating object category with id: {}", id);

        try {
            categoryService.activate(id);
            redirectAttributes.addFlashAttribute("successMessage", "Categoría de objetos activada exitosamente");
        } catch (Exception e) {
            log.error("Error activating object category: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/inventory/object-categories";
    }

    /**
     * Check dependencies before hard-deleting an object category
     */
    @GetMapping("/{id}/check-delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkDeleteDependencies(@PathVariable Long id) {
        log.info("Checking hard-delete dependencies for object category ID: {}", id);
        try {
            return ResponseEntity.ok(categoryService.checkHardDeleteDependencies(id));
        } catch (Exception e) {
            log.error("Error checking dependencies: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Permanently delete an object category (hard delete)
     */
    @PostMapping("/{id}/hard-delete")
    public String hardDeleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.warn("Processing hard delete for object category ID: {}", id);

        try {
            categoryService.hardDelete(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Categoría de objetos eliminada permanentemente de la base de datos.");
        } catch (IllegalStateException e) {
            log.error("Cannot hard-delete object category: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error hard-deleting object category: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al eliminar: " + e.getMessage());
        }

        return "redirect:/admin/inventory/object-categories";
    }
}
