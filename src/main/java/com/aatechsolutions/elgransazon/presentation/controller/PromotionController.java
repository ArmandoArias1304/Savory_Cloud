package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.ImageStorageService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.application.service.PromotionService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.Promotion;
import com.aatechsolutions.elgransazon.domain.entity.PromotionType;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for Promotion management
 * Handles CRUD operations for promotional offers
 */
@Controller
@RequestMapping("/{role}/promotions")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CASHIER', 'ROLE_WAITER')")
@RequiredArgsConstructor
@Slf4j
public class PromotionController {

    private final PromotionService promotionService;
    private final ItemMenuService itemMenuService;
    private final ImageStorageService imageStorageService;

    /**
     * Show list of all promotions
     */
    @GetMapping
    public String listPromotions(
            @PathVariable String role,
            @RequestParam(required = false) PromotionType type,
            @RequestParam(required = false) Boolean active,
            Model model) {
        
        log.debug("Displaying promotions list. Type: {}, Active: {}", type, active);

        List<Promotion> promotions;
        
        // Apply filters
        if (type != null) {
            promotions = promotionService.findByType(type);
            if (active != null) {
                boolean isActive = active;
                promotions = promotions.stream()
                    .filter(p -> p.getActive() == isActive)
                    .collect(Collectors.toList());
            }
        } else if (active != null) {
            if (active) {
                promotions = promotionService.findActivePromotions();
            } else {
                promotions = promotionService.findAll().stream()
                    .filter(p -> !p.getActive())
                    .collect(Collectors.toList());
            }
        } else {
            promotions = promotionService.findAllOrderedByPriority();
        }

        // Statistics
        long totalCount = promotionService.findAll().size();
        long activeCount = promotionService.countActivePromotions();
        long endingSoonCount = promotionService.findPromotionsEndingSoon(7).size();

        model.addAttribute("promotions", promotions);
        model.addAttribute("promotionTypes", PromotionType.values());
        model.addAttribute("selectedType", type);
        model.addAttribute("selectedActive", active);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("endingSoonCount", endingSoonCount);

        return role + "/promotions/list";
    }

    /**
     * Show form to create a new promotion
     */
    @GetMapping("/new")
    public String newPromotionForm(@PathVariable String role, Model model) {
        log.debug("Displaying new promotion form");

        Promotion promotion = new Promotion();
        promotion.setActive(true);
        promotion.setPriority(1);

        List<ItemMenu> menuItems = itemMenuService.findAllActive();
        
        // Group items by category for better UX
        Map<String, List<ItemMenu>> itemsByCategory = menuItems.stream()
            .collect(Collectors.groupingBy(item -> 
                item.getCategory() != null ? item.getCategory().getName() : "Sin Categoría"
            ));

        // Create a map with Spanish day names
        Map<String, String> daysOfWeekMap = new LinkedHashMap<>();
        daysOfWeekMap.put("MONDAY", "Lunes");
        daysOfWeekMap.put("TUESDAY", "Martes");
        daysOfWeekMap.put("WEDNESDAY", "Miércoles");
        daysOfWeekMap.put("THURSDAY", "Jueves");
        daysOfWeekMap.put("FRIDAY", "Viernes");
        daysOfWeekMap.put("SATURDAY", "Sábado");
        daysOfWeekMap.put("SUNDAY", "Domingo");

        model.addAttribute("promotion", promotion);
        model.addAttribute("promotionTypes", PromotionType.values());
        model.addAttribute("allDaysOfWeek", DayOfWeek.values());
        model.addAttribute("daysOfWeekMap", daysOfWeekMap);
        model.addAttribute("menuItems", menuItems);
        model.addAttribute("itemsByCategory", itemsByCategory);
        model.addAttribute("formAction", "/" + role + "/promotions");

        return role + "/promotions/form";
    }

    /**
     * Show form to edit an existing promotion
     */
    @GetMapping("/edit/{id}")
    public String editPromotionForm(@PathVariable String role, @PathVariable Long id, 
            Model model, 
            RedirectAttributes redirectAttributes) {
        
        log.debug("Displaying edit form for promotion ID: {}", id);

        return promotionService.findById(id)
            .map(promotion -> {
                List<ItemMenu> menuItems = itemMenuService.findAllActive();
                
                Map<String, List<ItemMenu>> itemsByCategory = menuItems.stream()
                    .collect(Collectors.groupingBy(item -> 
                        item.getCategory() != null ? item.getCategory().getName() : "Sin Categoría"
                    ));

                // Get selected item IDs for pre-selecting in form
                List<Long> selectedItemIds = promotion.getItems().stream()
                    .map(ItemMenu::getIdItemMenu)
                    .collect(Collectors.toList());

                // Create a map with Spanish day names
                Map<String, String> daysOfWeekMap = new LinkedHashMap<>();
                daysOfWeekMap.put("MONDAY", "Lunes");
                daysOfWeekMap.put("TUESDAY", "Martes");
                daysOfWeekMap.put("WEDNESDAY", "Miércoles");
                daysOfWeekMap.put("THURSDAY", "Jueves");
                daysOfWeekMap.put("FRIDAY", "Viernes");
                daysOfWeekMap.put("SATURDAY", "Sábado");
                daysOfWeekMap.put("SUNDAY", "Domingo");

                model.addAttribute("promotion", promotion);
                model.addAttribute("promotionTypes", PromotionType.values());
                model.addAttribute("allDaysOfWeek", DayOfWeek.values());
                model.addAttribute("daysOfWeekMap", daysOfWeekMap);
                model.addAttribute("menuItems", menuItems);
                model.addAttribute("itemsByCategory", itemsByCategory);
                model.addAttribute("selectedItemIds", selectedItemIds);
                model.addAttribute("selectedDays", promotion.getValidDaysSet());
                model.addAttribute("formAction", "/admin/promotions/" + id);

                return role + "/promotions/form";
            })
            .orElseGet(() -> {
                redirectAttributes.addFlashAttribute("error", "Promoción no encontrada");
                return "redirect:/" + role + "/promotions";
            });
    }

    /**
     * Create a new promotion
     */
    @PostMapping
    public String createPromotion(@PathVariable String role, @Valid @ModelAttribute("promotion") Promotion promotion,
            BindingResult bindingResult,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam(value = "itemIds", required = false) List<Long> itemIds,
            @RequestParam(value = "daysOfWeek", required = false) List<String> daysOfWeek,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Creating new promotion: {}", promotion.getName());

        // Convert days of week to comma-separated string
        if (daysOfWeek != null && !daysOfWeek.isEmpty()) {
            promotion.setValidDays(String.join(",", daysOfWeek));
        } else {
            bindingResult.rejectValue("validDays", "error.promotion", "Debe seleccionar al menos un día de la semana");
        }

        // Validate items
        if (itemIds == null || itemIds.isEmpty()) {
            bindingResult.rejectValue("items", "error.promotion", "Debe seleccionar al menos un item del menú");
        }

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors creating promotion: {}", bindingResult.getAllErrors());
            loadFormData(model, promotion, role);
            return role + "/promotions/form";
        }

        try {
            // Handle image upload
            if (imageFile != null && !imageFile.isEmpty()) {
                if (!imageStorageService.isValidImage(imageFile)) {
                    model.addAttribute("error", "Imagen inválida. Solo se permiten imágenes JPG, PNG, GIF o WEBP de máximo 5MB");
                    loadFormData(model, promotion, role);
                    return role + "/promotions/form";
                }
                String imageUrl = imageStorageService.saveImage(imageFile, "promotions", promotion.getName());
                promotion.setImageUrl(imageUrl);
            }

            // Add selected items
            if (itemIds != null) {
                for (Long itemId : itemIds) {
                    itemMenuService.findById(itemId).ifPresent(promotion::addItem);
                }
            }

            // Validate fixed discount amount (if applicable)
            if (promotion.getPromotionType() == PromotionType.FIXED_AMOUNT_DISCOUNT) {
                Map<String, Object> validationResult = promotionService.validateFixedDiscountAmount(promotion);
                boolean isValid = (boolean) validationResult.get("valid");
                
                if (!isValid) {
                    @SuppressWarnings("unchecked")
                    List<String> invalidItems = (List<String>) validationResult.get("invalidItems");
                    String errorMsg = String.format(
                        "El descuento de $%.2f es mayor que el precio de los siguientes items: %s. " +
                        "El descuento fijo no puede ser mayor al precio del item.",
                        promotion.getDiscountAmount(),
                        String.join(", ", invalidItems)
                    );
                    
                    log.warn("Fixed discount validation failed: {}", errorMsg);
                    model.addAttribute("error", errorMsg);
                    loadFormData(model, promotion, role);
                    return role + "/promotions/form";
                }
            }

            promotionService.save(promotion);
            redirectAttributes.addFlashAttribute("success", 
                "Promoción '" + promotion.getName() + "' creada exitosamente");
            return "redirect:/" + role + "/promotions";

        } catch (IllegalArgumentException e) {
            log.error("Error creating promotion: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            loadFormData(model, promotion, role);
            return role + "/promotions/form";
        } catch (Exception e) {
            log.error("Unexpected error creating promotion", e);
            model.addAttribute("error", "Error inesperado al crear la promoción");
            loadFormData(model, promotion, role);
            return role + "/promotions/form";
        }
    }

    /**
     * Update an existing promotion
     */
    @PostMapping("/{id}")
    public String updatePromotion(@PathVariable String role, @PathVariable Long id,
            @Valid @ModelAttribute("promotion") Promotion promotion,
            BindingResult bindingResult,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam(value = "itemIds", required = false) List<Long> itemIds,
            @RequestParam(value = "daysOfWeek", required = false) List<String> daysOfWeek,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Updating promotion with ID: {}", id);

        // Convert days of week to comma-separated string
        if (daysOfWeek != null && !daysOfWeek.isEmpty()) {
            promotion.setValidDays(String.join(",", daysOfWeek));
        } else {
            bindingResult.rejectValue("validDays", "error.promotion", "Debe seleccionar al menos un día de la semana");
        }

        // Validate items
        if (itemIds == null || itemIds.isEmpty()) {
            bindingResult.rejectValue("items", "error.promotion", "Debe seleccionar al menos un item del menú");
        }

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors updating promotion: {}", bindingResult.getAllErrors());
            model.addAttribute("formAction", "/admin/promotions/" + id);
            loadFormData(model, promotion, role);
            return role + "/promotions/form";
        }

        try {
            // Get existing promotion
            Promotion existing = promotionService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("promoción no encontrada"));

            // Handle image upload
            if (imageFile != null && !imageFile.isEmpty()) {
                if (!imageStorageService.isValidImage(imageFile)) {
                    model.addAttribute("error", "Imagen inválida. Solo se permiten imágenes JPG, PNG, GIF o WEBP de máximo 5MB");
                    model.addAttribute("formAction", "/admin/promotions/" + id);
                    loadFormData(model, promotion, role);
                    return role + "/promotions/form";
                }
                // Delete old image if exists
                if (existing.getImageUrl() != null && !existing.getImageUrl().isEmpty()) {
                    imageStorageService.deleteImage(existing.getImageUrl());
                }
                String imageUrl = imageStorageService.saveImage(imageFile, "promotions", promotion.getName());
                existing.setImageUrl(imageUrl);
            } else {
                // No new server-side file uploaded. Possible cases:
                //  (a) empty submittedUrl  → user removed image (delete old)
                //  (b) same as existing    → no change
                //  (c) different URL       → Direct Upload replaced the image (delete old)
                String submittedUrl = promotion.getImageUrl();
                if (submittedUrl == null || submittedUrl.trim().isEmpty()) {
                    if (existing.getImageUrl() != null && !existing.getImageUrl().isEmpty()) {
                        imageStorageService.deleteImage(existing.getImageUrl());
                        log.info("Image removed for promotion ID: {}", id);
                    }
                    existing.setImageUrl(null);
                } else if (!submittedUrl.equals(existing.getImageUrl())) {
                    if (existing.getImageUrl() != null && !existing.getImageUrl().isEmpty()) {
                        imageStorageService.deleteImage(existing.getImageUrl());
                        log.info("Old image replaced via Direct Upload for promotion ID: {}", id);
                    }
                    existing.setImageUrl(submittedUrl);
                }
                // else: image not changed — existing keeps its current URL
            }

            // Update fields
            existing.setName(promotion.getName());
            existing.setDescription(promotion.getDescription());
            existing.setPromotionType(promotion.getPromotionType());
            existing.setBuyQuantity(promotion.getBuyQuantity());
            existing.setPayQuantity(promotion.getPayQuantity());
            existing.setDiscountPercentage(promotion.getDiscountPercentage());
            existing.setDiscountAmount(promotion.getDiscountAmount());
            existing.setMinQuantityForFixedDiscount(promotion.getMinQuantityForFixedDiscount());
            existing.setStartDate(promotion.getStartDate());
            existing.setEndDate(promotion.getEndDate());
            existing.setValidDays(promotion.getValidDays());
            existing.setActive(promotion.getActive());
            existing.setPriority(promotion.getPriority());

            // Update items
            existing.clearItems();
            if (itemIds != null) {
                for (Long itemId : itemIds) {
                    itemMenuService.findById(itemId).ifPresent(existing::addItem);
                }
            }

            // Validate fixed discount amount (if applicable)
            if (existing.getPromotionType() == PromotionType.FIXED_AMOUNT_DISCOUNT) {
                Map<String, Object> validationResult = promotionService.validateFixedDiscountAmount(existing);
                boolean isValid = (boolean) validationResult.get("valid");
                
                if (!isValid) {
                    @SuppressWarnings("unchecked")
                    List<String> invalidItems = (List<String>) validationResult.get("invalidItems");
                    String errorMsg = String.format(
                        "El descuento de $%.2f es mayor que el precio de los siguientes items: %s. " +
                        "El descuento fijo no puede ser mayor al precio del item.",
                        existing.getDiscountAmount(),
                        String.join(", ", invalidItems)
                    );
                    
                    log.warn("Fixed discount validation failed during update: {}", errorMsg);
                    model.addAttribute("error", errorMsg);
                    model.addAttribute("formAction", "/admin/promotions/" + id);
                    loadFormData(model, promotion, role);
                    return role + "/promotions/form";
                }
            }

            promotionService.save(existing);
            redirectAttributes.addFlashAttribute("success", 
                "promoción '" + existing.getName() + "' actualizada exitosamente");
            return "redirect:/" + role + "/promotions";

        } catch (IllegalArgumentException e) {
            log.error("Error updating promotion: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            model.addAttribute("formAction", "/admin/promotions/" + id);
            loadFormData(model, promotion, role);
            return role + "/promotions/form";
        } catch (Exception e) {
            log.error("Unexpected error updating promotion", e);
            model.addAttribute("error", "Error inesperado al actualizar la promoción");
            model.addAttribute("formAction", "/admin/promotions/" + id);
            loadFormData(model, promotion, role);
            return role + "/promotions/form";
        }
    }

    /**
     * Activate a promotion
     */
    @PostMapping("/{id}/activate")
    public String activatePromotion(@PathVariable String role, @PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Activating promotion with ID: {}", id);

        try {
            Promotion promotion = promotionService.activate(id);
            redirectAttributes.addFlashAttribute("success", 
                "promoción '" + promotion.getName() + "' activada exitosamente");
        } catch (Exception e) {
            log.error("Error activating promotion", e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/" + role + "/promotions";
    }

    /**
     * Deactivate a promotion
     */
    @PostMapping("/{id}/deactivate")
    public String deactivatePromotion(@PathVariable String role, @PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Deactivating promotion with ID: {}", id);

        try {
            Promotion promotion = promotionService.deactivate(id);
            redirectAttributes.addFlashAttribute("success", 
                "promoción '" + promotion.getName() + "' desactivada exitosamente");
        } catch (Exception e) {
            log.error("Error deactivating promotion", e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/" + role + "/promotions";
    }

    /**
     * Delete a promotion
     */
    @PostMapping("/{id}/delete")
    public String deletePromotion(@PathVariable String role, @PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Deleting promotion with ID: {}", id);

        try {
            Promotion promotion = promotionService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("promoción no encontrada"));
            
            String name = promotion.getName();
            promotionService.deleteById(id);
            
            redirectAttributes.addFlashAttribute("success", 
                "promoción '" + name + "' eliminada exitosamente");
        } catch (Exception e) {
            log.error("Error deleting promotion", e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/" + role + "/promotions";
    }

    // ========== AJAX Endpoints ==========

    /**
     * Get promotions for a specific item (AJAX)
     */
    @GetMapping("/items/{itemId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPromotionsForItem(@PathVariable Long itemId) {
        log.debug("Getting promotions for item ID: {}", itemId);

        try {
            List<Promotion> promotions = promotionService.findActivePromotionsByItemId(itemId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("promotions", promotions.stream()
                .map(this::convertToMap)
                .collect(Collectors.toList()));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting promotions for item", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Calculate discounted price (AJAX)
     */
    @PostMapping("/calculate-discount")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> calculateDiscount(
            @RequestParam Long promotionId,
            @RequestParam BigDecimal originalPrice,
            @RequestParam int quantity) {
        
        log.debug("Calculating discount for promotion ID: {}, price: {}, quantity: {}", 
            promotionId, originalPrice, quantity);

        try {
            Promotion promotion = promotionService.findById(promotionId)
                .orElseThrow(() -> new IllegalArgumentException("promoción no encontrada"));

            BigDecimal discountedPrice = promotionService.calculateDiscountedPrice(originalPrice, promotion, quantity);
            BigDecimal savings = promotionService.calculateSavings(originalPrice, promotion, quantity);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("originalTotal", originalPrice.multiply(BigDecimal.valueOf(quantity)));
            response.put("discountedTotal", discountedPrice);
            response.put("savings", savings);
            response.put("promotionLabel", promotion.getDisplayLabel());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error calculating discount", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get active promotions as JSON for AJAX calls
     */
    @GetMapping("/active-json")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getActivePromotionsJson() {
        log.debug("Getting active promotions as JSON");
        
        try {
            List<Promotion> activePromotions = promotionService.findActivePromotions();
            
            // Get company context once outside the stream for efficiency
            final Company currentCompany = CompanyContext.requireCurrentCompany();
            
            List<Map<String, Object>> response = activePromotions.stream()
                .map(promo -> {
                    Map<String, Object> promoMap = new HashMap<>();
                    promoMap.put("id", promo.getIdPromotion());
                    promoMap.put("name", promo.getName());
                    promoMap.put("description", promo.getDescription());
                    promoMap.put("promotionType", promo.getPromotionType().name());
                    promoMap.put("displayLabel", promo.getDisplayLabel());
                    promoMap.put("buyQuantity", promo.getBuyQuantity());
                    promoMap.put("payQuantity", promo.getPayQuantity());
                    promoMap.put("discountPercentage", promo.getDiscountPercentage());
                    promoMap.put("discountAmount", promo.getDiscountAmount());
                    promoMap.put("minQuantityForFixedDiscount", promo.getEffectiveMinQuantityForFixedDiscount());
                    promoMap.put("priority", promo.getPriority());
                    
                    // Get item IDs for this promotion (filtered by current company and distinct)
                    List<Long> itemIds = promo.getItems().stream()
                        .filter(item -> item.getCompany() != null && currentCompany.getIdCompany().equals(item.getCompany().getIdCompany()))
                        .map(ItemMenu::getIdItemMenu)
                        .distinct()
                        .collect(Collectors.toList());
                    promoMap.put("itemIds", itemIds);
                    
                    return promoMap;
                })
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting active promotions JSON", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========== Helper Methods ==========

    /**
     * Load common form data
     */
    private void loadFormData(Model model, Promotion promotion, String role) {
        List<ItemMenu> menuItems = itemMenuService.findAllActive();
        
        Map<String, List<ItemMenu>> itemsByCategory = menuItems.stream()
            .collect(Collectors.groupingBy(item -> 
                item.getCategory() != null ? item.getCategory().getName() : "Sin Categoría"
            ));

        // Create a map with Spanish day names
        Map<String, String> daysOfWeekMap = new LinkedHashMap<>();
        daysOfWeekMap.put("MONDAY", "Lunes");
        daysOfWeekMap.put("TUESDAY", "Martes");
        daysOfWeekMap.put("WEDNESDAY", "Miércoles");
        daysOfWeekMap.put("THURSDAY", "Jueves");
        daysOfWeekMap.put("FRIDAY", "Viernes");
        daysOfWeekMap.put("SATURDAY", "Sábado");
        daysOfWeekMap.put("SUNDAY", "Domingo");

        model.addAttribute("promotion", promotion);
        model.addAttribute("promotionTypes", PromotionType.values());
        model.addAttribute("allDaysOfWeek", DayOfWeek.values());
        model.addAttribute("daysOfWeekMap", daysOfWeekMap);
        model.addAttribute("menuItems", menuItems);
        model.addAttribute("itemsByCategory", itemsByCategory);
        
        if (!model.containsAttribute("formAction")) {
            model.addAttribute("formAction", "/" + role + "/promotions");
        }
    }

    /**
     * Convert Promotion entity to Map for JSON response
     */
    private Map<String, Object> convertToMap(Promotion promotion) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", promotion.getIdPromotion());
        map.put("name", promotion.getName());
        map.put("description", promotion.getDescription());
        map.put("type", promotion.getPromotionType().name());
        map.put("displayLabel", promotion.getDisplayLabel());
        map.put("priority", promotion.getPriority());
        map.put("buyQuantity", promotion.getBuyQuantity());
        map.put("payQuantity", promotion.getPayQuantity());
        map.put("discountPercentage", promotion.getDiscountPercentage());
        map.put("discountAmount", promotion.getDiscountAmount());
        return map;
    }
}
