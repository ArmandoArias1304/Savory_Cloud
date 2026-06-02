package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.dto.ComplementDTO;
import com.aatechsolutions.elgransazon.application.dto.ComplementRequest;
import com.aatechsolutions.elgransazon.application.dto.ItemMenuComplementDTO;
import com.aatechsolutions.elgransazon.application.service.ComplementService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.domain.entity.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST Controller for Complement management
 * Provides CRUD operations for complements, their ingredients, and item associations
 */
@RestController
@RequestMapping("/api/complements")
@RequiredArgsConstructor
@Slf4j
public class ComplementController {

    private final ComplementService complementService;
    private final ItemMenuService itemMenuService;

    // ========== COMPLEMENT CRUD ==========

    /**
     * Get all active complements
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'CASHIER')")
    public ResponseEntity<List<ComplementDTO>> getAllComplements(
            @RequestParam(required = false, defaultValue = "false") Boolean onlyAvailable) {
        
        log.info("Getting all complements (onlyAvailable: {})", onlyAvailable);
        
        List<Complement> complements;
        if (Boolean.TRUE.equals(onlyAvailable)) {
            complements = complementService.findAllAvailable();
        } else {
            complements = complementService.findAllActive();
        }
        
        List<ComplementDTO> dtos = complements.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get complement by ID with ingredients
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER')")
    public ResponseEntity<ComplementDTO> getComplementById(@PathVariable Long id) {
        log.info("Getting complement by ID: {}", id);
        
        return complementService.findByIdWithIngredients(id)
                .map(c -> ResponseEntity.ok(toDTOWithIngredients(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new complement
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> createComplement(@Valid @RequestBody ComplementRequest request) {
        log.info("Creating new complement: {}", request.getName());
        
        try {
            Complement complement = Complement.builder()
                    .name(request.getName())
                    .description(request.getDescription())
                    .extraPrice(request.getExtraPrice())
                    .active(request.getActive() != null ? request.getActive() : true)
                    .available(true)
                    .build();
            
            Complement created = complementService.createComplement(complement);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(created));
        } catch (IllegalArgumentException e) {
            log.error("Error creating complement: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update an existing complement
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> updateComplement(
            @PathVariable Long id,
            @Valid @RequestBody ComplementRequest request) {
        
        log.info("Updating complement ID: {}", id);
        
        try {
            Complement updated = Complement.builder()
                    .name(request.getName())
                    .description(request.getDescription())
                    .extraPrice(request.getExtraPrice())
                    .active(request.getActive())
                    .build();
            
            Complement result = complementService.updateComplement(id, updated);
            
            return ResponseEntity.ok(toDTO(result));
        } catch (IllegalArgumentException e) {
            log.error("Error updating complement: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a complement (soft delete)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteComplement(@PathVariable Long id) {
        log.info("Deleting complement ID: {}", id);
        
        try {
            complementService.deleteComplement(id);
            return ResponseEntity.ok(Map.of("message", "Complemento eliminado correctamente"));
        } catch (IllegalArgumentException e) {
            log.error("Error deleting complement: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Search complements by name
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER')")
    public ResponseEntity<List<ComplementDTO>> searchComplements(@RequestParam String name) {
        log.info("Searching complements by name: {}", name);
        
        List<ComplementDTO> results = complementService.searchByName(name).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(results);
    }

    // ========== COMPLEMENT INGREDIENTS ==========

    /**
     * Add ingredient to complement recipe
     */
    @PostMapping("/{complementId}/ingredients")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> addIngredient(
            @PathVariable Long complementId,
            @Valid @RequestBody ComplementRequest.AddIngredientRequest request) {
        
        log.info("Adding ingredient {} to complement {}", request.getIngredientId(), complementId);
        
        try {
            ComplementIngredient ci = complementService.addIngredientToComplement(
                    complementId,
                    request.getIngredientId(),
                    request.getQuantity(),
                    request.getUnit()
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(toIngredientDTO(ci));
        } catch (IllegalArgumentException e) {
            log.error("Error adding ingredient: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Remove ingredient from complement recipe
     */
    @DeleteMapping("/{complementId}/ingredients/{ingredientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> removeIngredient(
            @PathVariable Long complementId,
            @PathVariable Long ingredientId) {
        
        log.info("Removing ingredient {} from complement {}", ingredientId, complementId);
        
        try {
            complementService.removeIngredientFromComplement(complementId, ingredientId);
            return ResponseEntity.ok(Map.of("message", "Ingrediente eliminado correctamente"));
        } catch (IllegalArgumentException e) {
            log.error("Error removing ingredient: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update ingredient quantity in complement recipe
     */
    @PutMapping("/{complementId}/ingredients/{ingredientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> updateIngredientQuantity(
            @PathVariable Long complementId,
            @PathVariable Long ingredientId,
            @RequestBody Map<String, BigDecimal> body) {
        
        BigDecimal newQuantity = body.get("quantity");
        if (newQuantity == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "quantity es requerido"));
        }
        
        log.info("Updating ingredient {} quantity in complement {} to {}", 
                 ingredientId, complementId, newQuantity);
        
        try {
            ComplementIngredient ci = complementService.updateIngredientQuantity(
                    complementId, ingredientId, newQuantity);
            return ResponseEntity.ok(toIngredientDTO(ci));
        } catch (IllegalArgumentException e) {
            log.error("Error updating ingredient quantity: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ========== ITEM MENU ASSOCIATIONS ==========

    /**
     * Get available complements for a menu item
     */
    @GetMapping("/item-menu/{itemMenuId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'CASHIER', 'CLIENT')")
    public ResponseEntity<List<ItemMenuComplementDTO>> getComplementsForItemMenu(
            @PathVariable Long itemMenuId) {
        
        log.info("Getting complements for item menu ID: {}", itemMenuId);
        
        List<ItemMenuComplement> complements = complementService.getAvailableComplementsForItemMenu(itemMenuId);
        
        List<ItemMenuComplementDTO> dtos = complements.stream()
                .map(this::toItemMenuComplementDTO)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get available complements for a menu item, separated into sauces and regular complements.
     * Also returns the maxSauces limit for this item.
     * Response format:
     * {
     *   "sauces": [...],           // List of sauce complements
     *   "complements": [...],      // List of regular complements (non-sauces)
     *   "maxSauces": 2,            // Maximum number of sauces allowed (null = unlimited)
     *   "totalSaucesAvailable": 5  // Total sauces available for this item
     * }
     */
    @GetMapping("/item-menu/{itemMenuId}/separated")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER', 'CASHIER', 'CLIENT')")
    public ResponseEntity<Map<String, Object>> getComplementsForItemMenuSeparated(
            @PathVariable Long itemMenuId) {
        
        log.info("Getting separated complements (sauces/specialities/regular) for item menu ID: {}", itemMenuId);
        
        // Get all complements for this item
        List<ItemMenuComplement> allComplements = complementService.getAvailableComplementsForItemMenu(itemMenuId);
        
        // Separate sauces, specialities, and regular complements
        List<ItemMenuComplementDTO> sauces = allComplements.stream()
                .filter(imc -> Boolean.TRUE.equals(imc.getComplement().getIsSauce()))
                .map(this::toItemMenuComplementDTO)
                .collect(Collectors.toList());

        List<ItemMenuComplementDTO> specialities = allComplements.stream()
                .filter(imc -> Boolean.TRUE.equals(imc.getComplement().getIsSpeciality()))
                .map(this::toItemMenuComplementDTO)
                .collect(Collectors.toList());

        List<ItemMenuComplementDTO> regularComplements = allComplements.stream()
                .filter(imc -> !Boolean.TRUE.equals(imc.getComplement().getIsSauce())
                        && !Boolean.TRUE.equals(imc.getComplement().getIsSpeciality()))
                .map(this::toItemMenuComplementDTO)
                .collect(Collectors.toList());
        
        // Get maxSauces / maxSpecialities from ItemMenu
        Integer maxSauces = null;
        Integer minSauces = null;
        Integer maxSpecialities = null;
        Integer minSpecialities = null;
        try {
            Optional<ItemMenu> itemOpt = itemMenuService.findById(itemMenuId);
            maxSauces = itemOpt.map(ItemMenu::getMaxSauces).orElse(null);
            minSauces = itemOpt.map(ItemMenu::getMinSauces).orElse(null);
            maxSpecialities = itemOpt.map(ItemMenu::getMaxSpecialities).orElse(null);
            minSpecialities = itemOpt.map(ItemMenu::getMinSpecialities).orElse(null);
        } catch (Exception e) {
            log.warn("Could not get sauce/speciality limits for item {}: {}", itemMenuId, e.getMessage());
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("sauces", sauces);
        response.put("specialities", specialities);
        response.put("complements", regularComplements);
        response.put("maxSauces", maxSauces);
        response.put("minSauces", minSauces);
        response.put("maxSpecialities", maxSpecialities);
        response.put("minSpecialities", minSpecialities);
        response.put("totalSaucesAvailable", sauces.size());
        response.put("totalSpecialitiesAvailable", specialities.size());
        
        log.info("Item {} has {} sauces, {} specialities, {} regular complements",
                itemMenuId, sauces.size(), specialities.size(), regularComplements.size());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Associate complement with item menu
     */
    @PostMapping("/item-menu")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> associateComplementToItemMenu(
            @Valid @RequestBody ComplementRequest.AssociateToItemRequest request) {
        
        log.info("Associating complement {} to item menu {}", 
                 request.getComplementId(), request.getItemMenuId());
        
        try {
            ItemMenuComplement imc = complementService.addComplementToItemMenu(
                    request.getItemMenuId(),
                    request.getComplementId(),
                    request.getMaxQuantity(),
                    request.getDisplayOrder()
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(toItemMenuComplementDTO(imc));
        } catch (IllegalArgumentException e) {
            log.error("Error associating complement: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Remove complement association from item menu
     */
    @DeleteMapping("/item-menu/{itemMenuId}/complement/{complementId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> removeComplementFromItemMenu(
            @PathVariable Long itemMenuId,
            @PathVariable Long complementId) {
        
        log.info("Removing complement {} from item menu {}", complementId, itemMenuId);
        
        try {
            complementService.removeComplementFromItemMenu(itemMenuId, complementId);
            return ResponseEntity.ok(Map.of("message", "Asociación eliminada correctamente"));
        } catch (IllegalArgumentException e) {
            log.error("Error removing complement association: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update complement configuration for item menu
     */
    @PutMapping("/item-menu/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> updateItemMenuComplementConfig(
            @PathVariable Long id,
            @Valid @RequestBody ComplementRequest.UpdateConfigRequest request) {
        
        log.info("Updating item menu complement config ID: {}", id);
        
        try {
            ItemMenuComplement updated = complementService.updateItemMenuComplement(
                    id,
                    request.getMaxQuantity(),
                    request.getDisplayOrder(),
                    request.getActive()
            );
            
            return ResponseEntity.ok(toItemMenuComplementDTO(updated));
        } catch (IllegalArgumentException e) {
            log.error("Error updating complement config: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ========== AVAILABILITY & STOCK ==========

    /**
     * Update availability for all complements
     */
    @PostMapping("/update-availability")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> updateAllAvailability() {
        log.info("Updating availability for all complements");
        
        complementService.updateAllComplementsAvailability();
        
        return ResponseEntity.ok(Map.of("message", "Disponibilidad actualizada correctamente"));
    }

    /**
     * Check stock for a complement
     */
    @GetMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAITER')")
    public ResponseEntity<Map<String, Object>> checkStock(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "1") Integer quantity) {
        
        log.info("Checking stock for complement {} (quantity: {})", id, quantity);
        
        boolean hasStock = complementService.hasEnoughStock(id, quantity);
        int maxPortions = complementService.calculateMaxPortions(id);
        
        Map<String, Object> result = new HashMap<>();
        result.put("hasEnoughStock", hasStock);
        result.put("maxPortions", maxPortions);
        result.put("requestedQuantity", quantity);
        
        return ResponseEntity.ok(result);
    }

    // ========== DTO CONVERTERS ==========

    private ComplementDTO toDTO(Complement complement) {
        return ComplementDTO.builder()
                .id(complement.getIdComplement())
                .name(complement.getName())
                .description(complement.getDescription())
                .extraPrice(complement.getExtraPrice())
                .active(complement.getActive())
                .available(complement.getAvailable())
                .maxPortions(complement.calculateMaxPortions())
                .estimatedCost(complement.calculateCost())
                .build();
    }

    private ComplementDTO toDTOWithIngredients(Complement complement) {
        ComplementDTO dto = toDTO(complement);
        
        if (complement.getIngredients() != null) {
            List<ComplementDTO.ComplementIngredientDTO> ingredientDTOs = complement.getIngredients().stream()
                    .map(this::toIngredientDTO)
                    .collect(Collectors.toList());
            dto.setIngredients(ingredientDTOs);
        }
        
        return dto;
    }

    private ComplementDTO.ComplementIngredientDTO toIngredientDTO(ComplementIngredient ci) {
        Ingredient ingredient = ci.getIngredient();
        
        return ComplementDTO.ComplementIngredientDTO.builder()
                .id(ci.getIdComplementIngredient())
                .ingredientId(ingredient.getIdIngredient())
                .ingredientName(ingredient.getName())
                .quantity(ci.getQuantity())
                .unit(ci.getUnit())
                .currentStock(ingredient.getCurrentStock())
                .hasEnoughStock(ci.hasEnoughStock(1))
                .build();
    }

    private ItemMenuComplementDTO toItemMenuComplementDTO(ItemMenuComplement imc) {
        Complement complement = imc.getComplement();
        ItemMenu itemMenu = imc.getItemMenu();
        
        return ItemMenuComplementDTO.builder()
                .id(imc.getIdItemMenuComplement())
                .itemMenuId(itemMenu.getIdItemMenu())
                .itemMenuName(itemMenu.getName())
                .complementId(complement.getIdComplement())
                .complementName(complement.getName())
                .complementExtraPrice(complement.getExtraPrice())
                .maxQuantity(imc.getMaxQuantity())
                .displayOrder(imc.getDisplayOrder())
                .active(imc.getActive())
                .available(complement.getAvailable()) // ✅ Use complement's availability, not ItemMenuComplement's
                .hasEnoughStock(imc.hasEnoughStock(1))
                .isSauce(complement.getIsSauce())
                .isSpeciality(complement.getIsSpeciality())
                .build();
    }
}
