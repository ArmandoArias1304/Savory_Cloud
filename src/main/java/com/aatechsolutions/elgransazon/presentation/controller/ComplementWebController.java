package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.ComplementService;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.domain.entity.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Web Controller for Complement management views
 */
@Controller
@RequestMapping("/admin/complements")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class ComplementWebController {

    private final ComplementService complementService;
    private final IngredientService ingredientService;

    /**
     * List all complements
     */
    @GetMapping
    public String listComplements(Model model) {
        log.info("Listing all complements");
        
        // Get all complements with their ingredients loaded (for availability calculation) - filtered by company
        List<Complement> allComplements = complementService.findAllWithIngredients();
        
        // Dynamically calculate availability for each complement based on current stock
        for (Complement complement : allComplements) {
            complement.updateAvailability();
        }
        
        long totalCount = allComplements.size();
        long availableCount = allComplements.stream().filter(c -> c.getActive() && c.getAvailable()).count();
        long unavailableCount = allComplements.stream().filter(c -> c.getActive() && !c.getAvailable()).count();
        
        model.addAttribute("complements", allComplements);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("availableCount", availableCount);
        model.addAttribute("unavailableCount", unavailableCount);
        
        return "admin/complements/list";
    }

    /**
     * Show form to create a new complement
     */
    @GetMapping("/new")
    public String newComplementForm(Model model) {
        log.debug("Displaying new complement form");
        
        Complement complement = new Complement();
        complement.setActive(true);
        complement.setAvailable(true);
        complement.setExtraPrice(BigDecimal.ZERO);
        
        model.addAttribute("complement", complement);
        model.addAttribute("formAction", "/admin/complements");
        
        return "admin/complements/form";
    }

    /**
     * Show form to edit an existing complement
     */
    @GetMapping("/edit/{id}")
    public String editComplementForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Displaying edit form for complement ID: {}", id);
        
        return complementService.findById(id)
                .map(complement -> {
                    model.addAttribute("complement", complement);
                    model.addAttribute("formAction", "/admin/complements/" + id);
                    return "admin/complements/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Complemento no encontrado");
                    return "redirect:/admin/complements";
                });
    }

    /**
     * Create a new complement
     */
    @PostMapping
    public String createComplement(
            @Valid @ModelAttribute("complement") Complement complement,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        
        log.info("Creating new complement: {}", complement.getName());
        
        if (bindingResult.hasErrors()) {
            return "admin/complements/form";
        }
        
        try {
            Complement created = complementService.createComplement(complement);
            redirectAttributes.addFlashAttribute("successMessage", 
                "Complemento '" + created.getName() + "' creado exitosamente");
            return "redirect:/admin/complements/" + created.getIdComplement() + "/ingredients";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/complements/new";
        }
    }

    /**
     * Update an existing complement
     */
    @PostMapping("/{id}")
    public String updateComplement(
            @PathVariable Long id,
            @Valid @ModelAttribute("complement") Complement complement,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        
        log.info("Updating complement ID: {}", id);
        
        if (bindingResult.hasErrors()) {
            return "admin/complements/form";
        }
        
        try {
            Complement updated = complementService.updateComplement(id, complement);
            redirectAttributes.addFlashAttribute("successMessage", 
                "Complemento '" + updated.getName() + "' actualizado exitosamente");
            return "redirect:/admin/complements";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/complements/edit/" + id;
        }
    }

    /**
     * Toggle complement active status
     */
    @PostMapping("/{id}/toggle-active")
    public String toggleActive(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Toggling active status for complement ID: {}", id);
        
        return complementService.findByIdWithIngredients(id)
                .map(complement -> {
                    complement.setActive(!complement.getActive());
                    complementService.updateComplement(id, complement);
                    String status = complement.getActive() ? "activado" : "desactivado";
                    redirectAttributes.addFlashAttribute("successMessage", 
                        "Complemento '" + complement.getName() + "' " + status);
                    return "redirect:/admin/complements";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Complemento no encontrado");
                    return "redirect:/admin/complements";
                });
    }

    /**
     * Delete a complement
     */
    @PostMapping("/{id}/delete")
    public String deleteComplement(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Deleting complement ID: {}", id);
        
        try {
            complementService.deleteComplement(id);
            redirectAttributes.addFlashAttribute("successMessage", "Complemento eliminado correctamente");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        
        return "redirect:/admin/complements";
    }

    // ========== INGREDIENTS MANAGEMENT ==========

    /**
     * Show ingredients management page for a complement
     */
    @GetMapping("/{id}/ingredients")
    public String manageIngredients(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Managing ingredients for complement ID: {}", id);
        
        return complementService.findByIdWithIngredients(id)
                .map(complement -> {
                    // Dynamically calculate availability based on current stock
                    complement.updateAvailability();
                    
                    // Get ingredients already in the complement
                    List<Long> usedIngredientIds = complement.getIngredients().stream()
                            .map(ci -> ci.getIngredient().getIdIngredient())
                            .collect(Collectors.toList());
                    
                    // Get available ingredients (not already used) - filtered by company
                    List<Ingredient> availableIngredients = ingredientService.findAllActive().stream()
                            .filter(ing -> !usedIngredientIds.contains(ing.getIdIngredient()))
                            .collect(Collectors.toList());
                    
                    model.addAttribute("complement", complement);
                    model.addAttribute("ingredients", complement.getIngredients());
                    model.addAttribute("availableIngredients", availableIngredients);
                    
                    return "admin/complements/ingredients";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Complemento no encontrado");
                    return "redirect:/admin/complements";
                });
    }

    /**
     * Add ingredient to complement
     */
    @PostMapping("/{id}/ingredients/add")
    public String addIngredient(
            @PathVariable Long id,
            @RequestParam Long ingredientId,
            @RequestParam BigDecimal quantity,
            @RequestParam(required = false) String unit,
            RedirectAttributes redirectAttributes) {
        
        log.info("Adding ingredient {} to complement {}", ingredientId, id);
        
        try {
            complementService.addIngredientToComplement(id, ingredientId, quantity, unit);
            redirectAttributes.addFlashAttribute("successMessage", "Ingrediente agregado correctamente");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        
        return "redirect:/admin/complements/" + id + "/ingredients";
    }

    /**
     * Update ingredient quantity
     */
    @PostMapping("/{complementId}/ingredients/{ingredientId}/update")
    public String updateIngredientQuantity(
            @PathVariable Long complementId,
            @PathVariable Long ingredientId,
            @RequestParam BigDecimal quantity,
            RedirectAttributes redirectAttributes) {
        
        log.info("Updating ingredient {} quantity in complement {} to {}", ingredientId, complementId, quantity);
        
        try {
            complementService.updateIngredientQuantity(complementId, ingredientId, quantity);
            redirectAttributes.addFlashAttribute("successMessage", "Cantidad actualizada correctamente");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        
        return "redirect:/admin/complements/" + complementId + "/ingredients";
    }

    /**
     * Remove ingredient from complement
     */
    @PostMapping("/{complementId}/ingredients/{ingredientId}/delete")
    public String removeIngredient(
            @PathVariable Long complementId,
            @PathVariable Long ingredientId,
            RedirectAttributes redirectAttributes) {
        
        log.info("Removing ingredient {} from complement {}", ingredientId, complementId);
        
        try {
            complementService.removeIngredientFromComplement(complementId, ingredientId);
            redirectAttributes.addFlashAttribute("successMessage", "Ingrediente eliminado correctamente");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        
        return "redirect:/admin/complements/" + complementId + "/ingredients";
    }

    // ========== HARD DELETE ==========

    /**
     * Check dependencies before hard-deleting a complement
     */
    @GetMapping("/{id}/check-delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkDeleteDependencies(@PathVariable Long id) {
        log.info("Checking hard-delete dependencies for complement ID: {}", id);
        try {
            Map<String, Object> result = complementService.checkHardDeleteDependencies(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error checking dependencies: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Permanently delete a complement (hard delete)
     */
    @PostMapping("/{id}/hard-delete")
    public String hardDeleteComplement(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.warn("Processing hard delete for complement ID: {}", id);
        try {
            complementService.hardDeleteComplement(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Complemento eliminado permanentemente de la base de datos.");
        } catch (IllegalStateException e) {
            log.error("Cannot hard-delete complement: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error hard-deleting complement: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al eliminar: " + e.getMessage());
        }
        return "redirect:/admin/complements";
    }
}
