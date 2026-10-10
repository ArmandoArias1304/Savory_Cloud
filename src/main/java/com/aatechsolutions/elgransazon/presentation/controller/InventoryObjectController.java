package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.InventoryObjectCategoryService;
import com.aatechsolutions.elgransazon.application.service.InventoryObjectService;
import com.aatechsolutions.elgransazon.domain.entity.InventoryObject;
import com.aatechsolutions.elgransazon.domain.entity.InventoryObjectCategory;
import com.aatechsolutions.elgransazon.domain.entity.ObjectCondition;
import com.aatechsolutions.elgransazon.domain.entity.ObjectUnit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controller for the object inventory (restaurant assets: tables, televisions,
 * pots, remote controls, etc.)
 * Accessible by ADMIN and MANAGER roles. Permanent deletion is restricted to ADMIN.
 */
@Controller
@RequestMapping("/admin/inventory/objects")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class InventoryObjectController {

    private final InventoryObjectService objectService;
    private final InventoryObjectCategoryService categoryService;

    /**
     * List all objects with optional filters
     */
    @GetMapping
    public String listObjects(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ObjectCondition condition,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Boolean active,
            Model model) {

        log.info("Listing inventory objects - search: {}, categoryId: {}, condition: {}, location: {}, active: {}",
                search, categoryId, condition, location, active);

        List<InventoryObject> objects =
                objectService.searchWithFilters(search, categoryId, condition, location, active);

        model.addAttribute("objects", objects);
        model.addAttribute("search", search);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("condition", condition);
        model.addAttribute("location", location);
        model.addAttribute("active", active);
        model.addAttribute("allCategories", categoryService.findAll());
        model.addAttribute("conditions", ObjectCondition.values());
        model.addAttribute("units", ObjectUnit.values());

        model.addAttribute("totalCount", objectService.getTotalCount());
        model.addAttribute("activeCount", objectService.getActiveCount());
        model.addAttribute("inactiveCount", objectService.getInactiveCount());
        model.addAttribute("damagedCount", objectService.getDamagedCount());
        model.addAttribute("inventoryValue", objectService.getInventoryValue());

        return "admin/inventory/objects/list";
    }

    /**
     * Show form to create a new object
     */
    @GetMapping("/new")
    public String showCreateForm(Model model, RedirectAttributes redirectAttributes) {
        log.info("Showing inventory object create form");

        List<InventoryObjectCategory> categories = categoryService.findAllActive();
        if (categories.isEmpty()) {
            log.warn("No active object categories found. Cannot create object.");
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No hay categorías de objetos disponibles. Debes crear al menos una categoría antes de crear objetos.");
            return "redirect:/admin/inventory/object-categories";
        }

        InventoryObject object = new InventoryObject();
        object.setActive(true);
        object.setCurrency("MXN");
        object.setUnit(ObjectUnit.PIEZA);
        object.setCondition(ObjectCondition.BUENO);
        object.setQuantity(BigDecimal.ONE);

        model.addAttribute("object", object);
        model.addAttribute("isEdit", false);
        model.addAttribute("allCategories", categories);
        model.addAttribute("conditions", ObjectCondition.values());
        model.addAttribute("units", ObjectUnit.values());

        return "admin/inventory/objects/form";
    }

    /**
     * Create a new object
     */
    @PostMapping
    public String createObject(
            @Valid @ModelAttribute("object") InventoryObject object,
            BindingResult result,
            @RequestParam(required = false) Long categoryId,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Creating inventory object: {} with categoryId: {}", object.getName(), categoryId);

        resolveCategory(object, categoryId, result);

        if (result.hasErrors()) {
            log.error("Validation errors creating inventory object");
            prepareFormModel(model, false);
            return "admin/inventory/objects/form";
        }

        try {
            objectService.create(object);
            redirectAttributes.addFlashAttribute("successMessage", "Objeto creado exitosamente");
            return "redirect:/admin/inventory/objects";
        } catch (DataAccessException e) {
            log.error("Data access error creating object: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            prepareFormModel(model, false);
            return "admin/inventory/objects/form";
        } catch (IllegalArgumentException e) {
            log.error("Error creating object: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            prepareFormModel(model, false);
            return "admin/inventory/objects/form";
        }
    }

    /**
     * Show form to edit an existing object
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Showing inventory object edit form for id: {}", id);

        try {
            InventoryObject object = objectService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Objeto no encontrado"));

            model.addAttribute("object", object);
            model.addAttribute("isEdit", true);
            prepareFormModel(model, true);

            return "admin/inventory/objects/form";
        } catch (IllegalArgumentException e) {
            log.error("Object not found with id: {}", id);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/inventory/objects";
        }
    }

    /**
     * Update an existing object
     */
    @PostMapping("/{id}")
    public String updateObject(
            @PathVariable Long id,
            @Valid @ModelAttribute("object") InventoryObject object,
            BindingResult result,
            @RequestParam(required = false) Long categoryId,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Updating inventory object with id: {}", id);

        object.setIdObject(id);
        resolveCategory(object, categoryId, result);

        if (result.hasErrors()) {
            log.error("Validation errors updating inventory object");
            prepareFormModel(model, true);
            return "admin/inventory/objects/form";
        }

        try {
            objectService.update(id, object);
            redirectAttributes.addFlashAttribute("successMessage", "Objeto actualizado exitosamente");
            return "redirect:/admin/inventory/objects";
        } catch (DataAccessException e) {
            log.error("Data access error updating object: {}", e.getMessage());
            object.setIdObject(id);
            model.addAttribute("errorMessage", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            prepareFormModel(model, true);
            return "admin/inventory/objects/form";
        } catch (IllegalArgumentException e) {
            log.error("Error updating object: {}", e.getMessage());
            object.setIdObject(id);
            model.addAttribute("errorMessage", e.getMessage());
            prepareFormModel(model, true);
            return "admin/inventory/objects/form";
        }
    }

    /**
     * Deactivate an object (soft delete)
     */
    @PostMapping("/{id}/delete")
    public String deleteObject(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Deactivating inventory object with id: {}", id);

        try {
            objectService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Objeto desactivado exitosamente. Ya no se cuenta como parte del inventario activo.");
        } catch (Exception e) {
            log.error("Error deactivating object: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/inventory/objects";
    }

    /**
     * Activate an object
     */
    @PostMapping("/{id}/activate")
    public String activateObject(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Activating inventory object with id: {}", id);

        try {
            objectService.activate(id);
            redirectAttributes.addFlashAttribute("successMessage", "Objeto activado exitosamente");
        } catch (Exception e) {
            log.error("Error activating object: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/inventory/objects";
    }

    /**
     * Permanently delete an object from the database (ADMIN only)
     */
    @PostMapping("/{id}/hard-delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String hardDeleteObject(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.warn("Processing hard delete for inventory object ID: {}", id);

        try {
            objectService.hardDelete(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Objeto eliminado permanentemente de la base de datos.");
        } catch (Exception e) {
            log.error("Error hard-deleting object: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al eliminar: " + e.getMessage());
        }

        return "redirect:/admin/inventory/objects";
    }

    /**
     * Resolve the selected category and register a field error when it is invalid
     */
    private void resolveCategory(InventoryObject object, Long categoryId, BindingResult result) {
        if (categoryId == null) {
            result.rejectValue("category", "error.object", "Debe seleccionar una categoría válida");
            return;
        }
        try {
            InventoryObjectCategory category = categoryService.findById(categoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));
            object.setCategory(category);
        } catch (IllegalArgumentException e) {
            log.error("Object category not found with id: {}", categoryId);
            result.rejectValue("category", "error.object", "Debe seleccionar una categoría válida");
        }
    }

    /**
     * Load the data required by the object form
     */
    private void prepareFormModel(Model model, boolean isEdit) {
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("allCategories", categoryService.findAllActive());
        model.addAttribute("conditions", ObjectCondition.values());
        model.addAttribute("units", ObjectUnit.values());
    }
}
