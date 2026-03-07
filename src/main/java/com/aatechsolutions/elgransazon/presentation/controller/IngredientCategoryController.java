package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.IngredientCategoryService;
import com.aatechsolutions.elgransazon.domain.entity.IngredientCategory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Controller for IngredientCategory management
 * Accessible by ADMIN and MANAGER roles
 */
@Controller
@RequestMapping("/admin/ingredient-categories")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class IngredientCategoryController {

    private final IngredientCategoryService categoryService;

    /**
     * List all ingredient categories with optional filters
     */
    @GetMapping
    public String listCategories(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            Model model) {

        log.info("Listing ingredient categories with filters - search: {}, active: {}", search, active);

        // Default to showing only active categories if no filter is specified
        Boolean activeFilter = (active != null) ? active : true;

        List<IngredientCategory> categories = categoryService.searchWithFilters(search, activeFilter);

        // Get statistics
        long activeCount = categoryService.getActiveCount();
        long inactiveCount = categoryService.getInactiveCount();
        long totalCount = activeCount + inactiveCount; // Total de categorías (activas + inactivas)

        model.addAttribute("categories", categories);
        model.addAttribute("search", search);
        model.addAttribute("active", activeFilter);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("inactiveCount", inactiveCount);

        return "admin/ingredient-categories/list";
    }

    /**
     * Show form to create a new category
     */
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        log.info("Showing ingredient category create form");

        model.addAttribute("category", new IngredientCategory());
        model.addAttribute("isEdit", false);

        return "admin/ingredient-categories/form";
    }

    /**
     * Create a new category
     */
    @PostMapping
    public String createCategory(
            @Valid @ModelAttribute("category") IngredientCategory category,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Creating ingredient category: {}", category.getName());

        if (result.hasErrors()) {
            log.error("Validation errors creating category");
            model.addAttribute("isEdit", false);
            return "admin/ingredient-categories/form";
        }

        try {
            categoryService.create(category);
            redirectAttributes.addFlashAttribute("successMessage", "Categoría de ingrediente creada exitosamente");
            return "redirect:/admin/ingredient-categories";
        } catch (IllegalArgumentException e) {
            log.error("Error creating category: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", false);
            return "admin/ingredient-categories/form";
        }
    }

    /**
     * Show form to edit an existing category
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Showing ingredient category edit form for id: {}", id);

        try {
            IngredientCategory category = categoryService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));

            model.addAttribute("category", category);
            model.addAttribute("isEdit", true);

            return "admin/ingredient-categories/form";
        } catch (IllegalArgumentException e) {
            log.error("Category not found with id: {}", id);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/ingredient-categories";
        }
    }

    /**
     * Update an existing category
     */
    @PostMapping("/{id}")
    public String updateCategory(
            @PathVariable Long id,
            @Valid @ModelAttribute("category") IngredientCategory category,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Updating ingredient category with id: {}", id);

        if (result.hasErrors()) {
            log.error("Validation errors updating category");
            model.addAttribute("isEdit", true);
            return "admin/ingredient-categories/form";
        }

        try {
            categoryService.update(id, category);
            redirectAttributes.addFlashAttribute("successMessage", "Categoría de ingrediente actualizada exitosamente");
            return "redirect:/admin/ingredient-categories";
        } catch (IllegalArgumentException e) {
            log.error("Error updating category: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", true);
            return "admin/ingredient-categories/form";
        }
    }

    /**
     * Deactivate a category (soft delete)
     */
    @PostMapping("/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Deactivating ingredient category with id: {}", id);

        try {
            categoryService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Categoría de ingrediente desactivada exitosamente");
        } catch (IllegalArgumentException e) {
            log.error("Error deactivating category: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/ingredient-categories";
    }

    /**
     * Activate a category
     */
    @PostMapping("/{id}/activate")
    public String activateCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Activating ingredient category with id: {}", id);

        try {
            categoryService.activate(id);
            redirectAttributes.addFlashAttribute("successMessage", "Categoría de ingrediente activada exitosamente");
        } catch (IllegalArgumentException e) {
            log.error("Error activating category: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/ingredient-categories";
    }

    // ========== HARD DELETE ==========

    /**
     * Check dependencies before hard-deleting an ingredient category
     */
    @GetMapping("/{id}/check-delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkDeleteDependencies(@PathVariable Long id) {
        log.info("Checking hard-delete dependencies for ingredient category ID: {}", id);
        try {
            Map<String, Object> result = categoryService.checkHardDeleteDependencies(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error checking dependencies: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Permanently delete an ingredient category (hard delete)
     */
    @PostMapping("/{id}/hard-delete")
    public String hardDeleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.warn("Processing hard delete for ingredient category ID: {}", id);
        try {
            categoryService.hardDelete(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Categoría de ingrediente eliminada permanentemente de la base de datos.");
        } catch (IllegalStateException e) {
            log.error("Cannot hard-delete ingredient category: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error hard-deleting ingredient category: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al eliminar: " + e.getMessage());
        }
        return "redirect:/admin/ingredient-categories";
    }
}
