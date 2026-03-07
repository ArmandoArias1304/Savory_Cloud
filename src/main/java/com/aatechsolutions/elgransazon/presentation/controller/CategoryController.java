package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.domain.entity.Category;
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

import java.util.Map;

/**
 * Controller for managing category operations
 * Accessible by ADMIN and MANAGER roles
 */
@Controller
@RequestMapping("/admin/categories")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Display list of all categories
     */
    @GetMapping
    public String listCategories(Model model) {
        log.debug("Displaying categories list");
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("activeCount", categoryService.countActiveCategories());
        return "admin/categories/list";
    }

    /**
     * Show form to create a new category
     */
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        log.debug("Displaying create category form");
        model.addAttribute("category", new Category());
        model.addAttribute("isEdit", false);
        return "admin/categories/form";
    }

    /**
     * Process the creation of a new category
     */
    @PostMapping
    public String createCategory(
            @Valid @ModelAttribute("category") Category category,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        log.info("Processing category creation: {}", category.getName());

        // Check for validation errors
        if (bindingResult.hasErrors()) {
            log.warn("Validation errors on category creation");
            model.addAttribute("isEdit", false);
            return "admin/categories/form";
        }

        try {
            // Check if category name already exists
            if (categoryService.categoryNameExists(category.getName())) {
                bindingResult.rejectValue("name", "error.category", "El nombre de la categoría ya existe");
                model.addAttribute("isEdit", false);
                return "admin/categories/form";
            }

            // Create category
            categoryService.createCategory(category);
            
            log.info("Category created successfully: {}", category.getName());
            redirectAttributes.addFlashAttribute("successMessage", "¡Categoría creada exitosamente!");
            return "redirect:/admin/categories";

        } catch (Exception e) {
            log.error("Error creating category: {}", e.getMessage());
            model.addAttribute("errorMessage", "Error al crear la categoría: " + e.getMessage());
            model.addAttribute("isEdit", false);
            return "admin/categories/form";
        }
    }

    /**
     * Show form to edit an existing category
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Displaying edit form for category id: {}", id);

        return categoryService.getCategoryById(id)
                .map(category -> {
                    model.addAttribute("category", category);
                    model.addAttribute("isEdit", true);
                    return "admin/categories/form";
                })
                .orElseGet(() -> {
                    log.warn("Category not found with id: {}", id);
                    redirectAttributes.addFlashAttribute("errorMessage", "Categoría no encontrada");
                    return "redirect:/admin/categories";
                });
    }

    /**
     * Process the update of an existing category
     */
    @PostMapping("/{id}")
    public String updateCategory(
            @PathVariable Long id,
            @Valid @ModelAttribute("category") Category category,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        log.info("Processing category update for id: {}", id);

        // Check for validation errors
        if (bindingResult.hasErrors()) {
            log.warn("Validation errors on category update");
            model.addAttribute("isEdit", true);
            return "admin/categories/form";
        }

        try {
            // Update category
            categoryService.updateCategory(id, category);
            
            log.info("Category updated successfully: {}", id);
            redirectAttributes.addFlashAttribute("successMessage", "¡Categoría actualizada exitosamente!");
            return "redirect:/admin/categories";

        } catch (IllegalArgumentException e) {
            log.error("Error updating category: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", true);
            return "admin/categories/form";
        }
    }

    /**
     * Soft delete a category (set active to false)
     */
    @PostMapping("/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Processing category deletion for id: {}", id);

        try {
            categoryService.deleteCategory(id);
            redirectAttributes.addFlashAttribute("successMessage", "¡Categoría desactivada exitosamente!");
        } catch (IllegalArgumentException e) {
            log.error("Error deleting category: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/categories";
    }

    /**
     * Activate a category
     */
    @PostMapping("/{id}/activate")
    public String activateCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Processing category activation for id: {}", id);

        try {
            categoryService.activateCategory(id);
            redirectAttributes.addFlashAttribute("successMessage", "¡Categoría activada exitosamente!");
        } catch (IllegalArgumentException e) {
            log.error("Error activating category: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/categories";
    }

    /**
     * Permanently delete a category (hard delete)
     */
    @PostMapping("/{id}/permanent-delete")
    public String permanentlyDeleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.warn("Processing permanent deletion for category id: {}", id);

        try {
            categoryService.permanentlyDeleteCategory(id);
            redirectAttributes.addFlashAttribute("successMessage", "¡Categoría eliminada permanentemente!");
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error permanently deleting category: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/categories";
    }

    /**
     * Check dependencies before hard-deleting a category
     */
    @GetMapping("/{id}/check-delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkDeleteDependencies(@PathVariable Long id) {
        log.info("Checking hard-delete dependencies for category ID: {}", id);
        try {
            Map<String, Object> result = categoryService.checkHardDeleteDependencies(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error checking dependencies: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
