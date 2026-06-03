package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.BusinessHoursService;
import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.ComplementService;
import com.aatechsolutions.elgransazon.application.service.ImageStorageService;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComplementRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controller for ItemMenu (Menu Items) management
 * Accessible by ADMIN and MANAGER roles
 */
@Controller
@RequestMapping("/admin/menu-items")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class ItemMenuController {

    private final ItemMenuService itemMenuService;
    private final CategoryService categoryService;
    private final IngredientService ingredientService;
    private final ImageStorageService imageStorageService;
    private final ComplementService complementService;
    private final ItemMenuComplementRepository itemMenuComplementRepository;
    private final BusinessHoursService businessHoursService;

    /**
     * Show list of all menu items
     */
    @GetMapping
    public String listMenuItems(Model model) {
        log.debug("Displaying menu items list");

        List<ItemMenu> menuItems = itemMenuService.findAllOrderByCategoryAndName();
        List<Category> categories = categoryService.getAllCategories();

        long totalCount = itemMenuService.countAll();
        long activeCount = itemMenuService.countActive();
        long availableCount = itemMenuService.countAvailable();
        long unavailableCount = itemMenuService.countUnavailable();

        model.addAttribute("menuItems", menuItems);
        model.addAttribute("categories", categories);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("availableCount", availableCount);
        model.addAttribute("unavailableCount", unavailableCount);

        return "admin/menu-items/list";
    }

    /**
     * Show form to create a new menu item
     */
    @GetMapping("/new")
    public String newMenuItemForm(Model model, RedirectAttributes redirectAttributes) {
        log.debug("Displaying new menu item form");

        // Verificar que existan categorías activas
        List<Category> categories = categoryService.getAllActiveCategories();
        if (categories.isEmpty()) {
            log.warn("No active categories found. Cannot create menu item.");
            redirectAttributes.addFlashAttribute("errorMessage", 
                "No hay categorías disponibles. Debes crear al menos una categoría antes de crear items del menú.");
            return "redirect:/admin/menu-items";
        }

        ItemMenu itemMenu = new ItemMenu();
        itemMenu.setActive(true);
        itemMenu.setAvailable(true);
        itemMenu.setRequiresPreparation(true); // ✅ Inicializar explícitamente
        
        // Set default availability schedule from business hours
        itemMenuService.setDefaultAvailabilitySchedule(itemMenu);
        List<Ingredient> ingredients = ingredientService.findAll();
        
        // Convertir ingredientes a DTOs simples para evitar referencias circulares
        List<Map<String, Object>> ingredientsDTO = ingredients.stream()
            .map(ing -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("idIngredient", ing.getIdIngredient());
                dto.put("name", ing.getName());
                dto.put("unitOfMeasure", ing.getUnitOfMeasure());
                dto.put("currentStock", ing.getCurrentStock());
                dto.put("categoryName", ing.getCategory() != null ? ing.getCategory().getName() : "");
                return dto;
            })
            .collect(java.util.stream.Collectors.toList());

        model.addAttribute("itemMenu", itemMenu);
        model.addAttribute("categories", categories);
        model.addAttribute("ingredients", ingredients);
        model.addAttribute("ingredientsDTO", ingredientsDTO);
        model.addAttribute("recipe", new ArrayList<ItemIngredient>());
        model.addAttribute("formAction", "/admin/menu-items");
        
        // Add combo data for new items
        model.addAttribute("comboItems", new ArrayList<>());
        List<ItemMenu> allNonComboItems = itemMenuService.findAll().stream()
            .filter(i -> !Boolean.TRUE.equals(i.getIsCombo()))
            .collect(Collectors.toList());
        model.addAttribute("availableComboChildItems", allNonComboItems);
        
        // Add availability data
        loadAvailabilityFormData(model, itemMenu);

        // Add active sauces (multi-tenant) for the sauces selection list (no item yet → no preselection)
        loadSaucesFormData(model, null);
        // Add active specialities (parallel to sauces)
        loadSpecialitiesFormData(model, null);

        // No size variants for a new item
        model.addAttribute("sizeItems", new ArrayList<>());

        return "admin/menu-items/form";
    }

    /**
     * Show form to edit an existing menu item
     */
    @GetMapping("/edit/{id}")
    public String editMenuItemForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Displaying edit form for menu item ID: {}", id);

        return itemMenuService.findById(id)
                .map(itemMenu -> {
                    List<Category> categories = categoryService.getAllCategories();
                    List<Ingredient> ingredients = ingredientService.findAll();
                    List<ItemIngredient> recipe = itemMenuService.getRecipe(id);
                    
                    // Convertir ingredientes a DTOs simples para evitar referencias circulares
                    List<Map<String, Object>> ingredientsDTO = ingredients.stream()
                        .map(ing -> {
                            Map<String, Object> dto = new HashMap<>();
                            dto.put("idIngredient", ing.getIdIngredient());
                            dto.put("name", ing.getName());
                            dto.put("unitOfMeasure", ing.getUnitOfMeasure());
                            dto.put("currentStock", ing.getCurrentStock());
                            dto.put("categoryName", ing.getCategory() != null ? ing.getCategory().getName() : "");
                            return dto;
                        })
                        .collect(java.util.stream.Collectors.toList());

                    model.addAttribute("itemMenu", itemMenu);
                    model.addAttribute("categories", categories);
                    model.addAttribute("ingredients", ingredients);
                    model.addAttribute("ingredientsDTO", ingredientsDTO);
                    model.addAttribute("recipe", recipe);
                    model.addAttribute("formAction", "/admin/menu-items/" + id);
                    
                    // Add combo items data if this item is a combo
                    if (Boolean.TRUE.equals(itemMenu.getIsCombo())) {
                        List<ItemMenuComboItem> comboItems = itemMenuService.getComboItems(id);
                        model.addAttribute("comboItems", comboItems);
                    } else {
                        model.addAttribute("comboItems", new ArrayList<>());
                    }
                    
                    // Add all non-combo items for combo selection dropdown
                    List<ItemMenu> allNonComboItems = itemMenuService.findAll().stream()
                        .filter(i -> !Boolean.TRUE.equals(i.getIsCombo()) && !i.getIdItemMenu().equals(id))
                        .collect(Collectors.toList());
                    model.addAttribute("availableComboChildItems", allNonComboItems);
                    
                    // Add availability data
                    loadAvailabilityFormData(model, itemMenu);

                    // Add active sauces (multi-tenant) and currently associated sauce IDs for the selection list
                    loadSaucesFormData(model, id);
                    // Add active specialities and currently associated speciality IDs (parallel to sauces)
                    loadSpecialitiesFormData(model, id);

                    // Add size variants for this item
                    model.addAttribute("sizeItems", itemMenuService.findSizeItems(id));

                    return "admin/menu-items/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Item del menú no encontrado");
                    return "redirect:/admin/menu-items";
                });
    }

    /**
     * Create a new menu item with recipe
     */
    @PostMapping
    public String createMenuItem(
            @Valid @ModelAttribute("itemMenu") ItemMenu itemMenu,
            BindingResult bindingResult,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam(value = "ingredientIds", required = false) List<Long> ingredientIds,
            @RequestParam(value = "quantities", required = false) List<BigDecimal> quantities,
            @RequestParam(value = "units", required = false) List<String> units,
            @RequestParam(value = "requiresPreparation", required = false) Boolean requiresPreparationParam,
            @RequestParam(value = "requiresBaristaPreparation", required = false) Boolean requiresBaristaPreparationParam,
            @RequestParam(value = "requiresParrilleroPreparation", required = false) Boolean requiresParrilleroPreparationParam,
            @RequestParam(value = "requiresIngredients", required = false) Boolean requiresIngredientsParam,
            @RequestParam(value = "isCombo", required = false) Boolean isComboParam,
            @RequestParam(value = "dineInOnly", required = false) Boolean dineInOnlyParam,
            @RequestParam(value = "comboItemIds", required = false) List<Long> comboItemIds,
            @RequestParam(value = "comboItemQuantities", required = false) List<Integer> comboItemQuantities,
            @RequestParam(value = "selectedSauceIds", required = false) List<Long> selectedSauceIds,
            @RequestParam(value = "selectedSpecialityIds", required = false) List<Long> selectedSpecialityIds,
            @RequestParam(value = "hasCustomSchedule", required = false) Boolean hasCustomSchedule,
            @RequestParam Map<String, String> allParams,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Creating new menu item: {}", itemMenu.getName());
        log.info("🔍 requiresPreparation received from form: {}", itemMenu.getRequiresPreparation());
        log.info("🔍 requiresPreparation as @RequestParam: {}", requiresPreparationParam);
        log.info("🔍 requiresBaristaPreparation received from form: {}", itemMenu.getRequiresBaristaPreparation());
        log.info("🔍 requiresBaristaPreparation as @RequestParam: {}", requiresBaristaPreparationParam);
        log.info("🔍 requiresIngredients: {}", requiresIngredientsParam);
        log.info("🔍 selectedSauceIds: {}", selectedSauceIds);
        log.info("🔍 hasCustomSchedule: {}", hasCustomSchedule);
        
        // Si el parámetro está presente, usarlo (para debug)
        if (requiresPreparationParam != null) {
            log.info("🔍 Using @RequestParam value for Chef: {}", requiresPreparationParam);
            itemMenu.setRequiresPreparation(requiresPreparationParam);
        }
        
        if (requiresBaristaPreparationParam != null) {
            log.info("🔍 Using @RequestParam value for Barista: {}", requiresBaristaPreparationParam);
            itemMenu.setRequiresBaristaPreparation(requiresBaristaPreparationParam);
        }

        if (requiresParrilleroPreparationParam != null) {
            log.info("🔍 Using @RequestParam value for Parrillero: {}", requiresParrilleroPreparationParam);
            itemMenu.setRequiresParrilleroPreparation(requiresParrilleroPreparationParam);
        }
        
        // Set isCombo on item
        itemMenu.setIsCombo(Boolean.TRUE.equals(isComboParam));
        
        // Set requiresIngredients on item (combos always force false)
        if (Boolean.TRUE.equals(itemMenu.getIsCombo())) {
            itemMenu.setRequiresIngredients(false);
        } else {
            itemMenu.setRequiresIngredients(Boolean.TRUE.equals(requiresIngredientsParam));
        }
        
        // Set dineInOnly on item (independent of requiresIngredients)
        itemMenu.setDineInOnly(Boolean.TRUE.equals(dineInOnlyParam));
        
        // Set hasCustomSchedule on item
        itemMenu.setHasCustomSchedule(Boolean.TRUE.equals(hasCustomSchedule));

        // Validate mutual exclusion: only one of Chef, Barista, Parrillero, or Combo can be selected
        int selectedOptions = 0;
        if (Boolean.TRUE.equals(itemMenu.getRequiresPreparation())) selectedOptions++;
        if (Boolean.TRUE.equals(itemMenu.getRequiresBaristaPreparation())) selectedOptions++;
        if (Boolean.TRUE.equals(itemMenu.getRequiresParrilleroPreparation())) selectedOptions++;
        if (Boolean.TRUE.equals(itemMenu.getIsCombo())) selectedOptions++;
        if (selectedOptions > 1) {
             model.addAttribute("errorMessage", "Solo puede seleccionar una opción: preparación por Chef, Barista, Parrillero o Combo.");
             List<ItemIngredient> recipe = buildRecipe(ingredientIds, quantities, units);
             loadFormData(model, itemMenu, recipe);
             loadAvailabilityFormData(model, itemMenu);
             return "admin/menu-items/form";
        }

        if (bindingResult.hasErrors()) {
            loadFormData(model, itemMenu, new ArrayList<>());
            loadAvailabilityFormData(model, itemMenu);
            return "admin/menu-items/form";
        }

        try {
            // Handle image upload
            if (imageFile != null && !imageFile.isEmpty()) {
                if (!imageStorageService.isValidImage(imageFile)) {
                    model.addAttribute("errorMessage", "Imagen inválida. Solo se permiten imágenes JPG, PNG, GIF o WEBP de máximo 5MB");
                    List<ItemIngredient> recipe = buildRecipe(ingredientIds, quantities, units);
                    loadFormData(model, itemMenu, recipe);
                    loadAvailabilityFormData(model, itemMenu);
                    return "admin/menu-items/form";
                }
                String imagePath = imageStorageService.saveImage(imageFile, "menu-items", itemMenu.getName());
                itemMenu.setImageUrl(imagePath);
            }

            // Build recipe from form data
            List<ItemIngredient> recipe = buildRecipe(ingredientIds, quantities, units);

            // Validate ingredients based on requiresIngredients flag
            if (!Boolean.TRUE.equals(itemMenu.getRequiresIngredients()) || Boolean.TRUE.equals(itemMenu.getIsCombo())) {
                // Items without recipe and Combo items must NOT have ingredients - force empty recipe
                recipe = new ArrayList<>();
            } else {
                // Items that require ingredients must have at least one
                if (recipe.isEmpty()) {
                    model.addAttribute("errorMessage", "Debe agregar al menos un ingrediente a la receta");
                    loadFormData(model, itemMenu, recipe);
                    loadAvailabilityFormData(model, itemMenu);
                    return "admin/menu-items/form";
                }
            }
            
            // Validate combo items if this is a combo
            if (Boolean.TRUE.equals(itemMenu.getIsCombo())) {
                if (comboItemIds == null || comboItemIds.isEmpty()) {
                    model.addAttribute("errorMessage", "Un combo debe tener al menos 1 item del menú asociado");
                    loadFormData(model, itemMenu, new ArrayList<>());
                    loadAvailabilityFormData(model, itemMenu);
                    return "admin/menu-items/form";
                }
                
                // Validate that all combo child items are active
                List<String> inactiveItems = new ArrayList<>();
                for (Long childId : comboItemIds) {
                    Optional<ItemMenu> childItem = itemMenuService.findById(childId);
                    if (childItem.isEmpty() || !Boolean.TRUE.equals(childItem.get().getActive())) {
                        inactiveItems.add(childItem.isPresent() ? childItem.get().getName() : "ID: " + childId);
                    }
                }
                
                if (!inactiveItems.isEmpty()) {
                    model.addAttribute("errorMessage", 
                        "No se puede crear el combo con items inactivos: " + String.join(", ", inactiveItems) + 
                        ". Por favor, active estos items o seléccione otros.");
                    loadFormData(model, itemMenu, recipe);
                    loadAvailabilityFormData(model, itemMenu);
                    return "admin/menu-items/form";
                }
            }
            
            ItemMenu created = itemMenuService.create(itemMenu, recipe);
            
            // Save combo items if this is a combo
            if (Boolean.TRUE.equals(itemMenu.getIsCombo()) && comboItemIds != null && !comboItemIds.isEmpty()) {
                itemMenuService.updateComboItems(created.getIdItemMenu(), comboItemIds, comboItemQuantities);
                log.info("Saved {} combo items for combo '{}'", comboItemIds.size(), created.getName());
            }
            
            // Save availability schedule only if custom schedule is enabled
            if (Boolean.TRUE.equals(hasCustomSchedule)) {
                Map<DayOfWeek, LocalTime[]> manualSchedule = extractManualSchedule(allParams);
                if (!manualSchedule.isEmpty()) {
                    itemMenuService.updateAvailabilityScheduleManual(created.getIdItemMenu(), manualSchedule);
                } else {
                    // Custom schedule enabled but no days selected - disable it
                    log.info("Custom schedule enabled but no days selected, disabling custom schedule for item {}", created.getIdItemMenu());
                    itemMenuService.clearAvailabilitySchedule(created.getIdItemMenu());
                }
            } else {
                // No custom schedule - clear any existing availability days
                itemMenuService.clearAvailabilitySchedule(created.getIdItemMenu());
            }
            
            // Associate the sauces explicitly selected in the form (if any)
            int saucesAssociated = 0;
            if (selectedSauceIds != null && !selectedSauceIds.isEmpty()) {
                saucesAssociated = syncSaucesForItem(created.getIdItemMenu(), selectedSauceIds);
                log.info("Associated {} sauces to item: {}", saucesAssociated, created.getName());
            }

            // Associate the specialities explicitly selected in the form (if any)
            int specialitiesAssociated = 0;
            if (selectedSpecialityIds != null && !selectedSpecialityIds.isEmpty()) {
                specialitiesAssociated = syncSpecialitiesForItem(created.getIdItemMenu(), selectedSpecialityIds);
                log.info("Associated {} specialities to item: {}", specialitiesAssociated, created.getName());
            }

            log.info("Menu item created successfully with ID: {}", created.getIdItemMenu());
            StringBuilder extra = new StringBuilder();
            if (saucesAssociated > 0) extra.append(" (").append(saucesAssociated).append(" salsas asociadas)");
            if (specialitiesAssociated > 0) extra.append(" (").append(specialitiesAssociated).append(" especialidades asociadas)");
            redirectAttributes.addFlashAttribute("successMessage",
                    "Item del menú '" + created.getName() + "' creado exitosamente" + extra.toString());
            return "redirect:/admin/menu-items";


        } catch (IllegalArgumentException e) {
            log.error("Validation error creating menu item: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            List<ItemIngredient> recipe = buildRecipe(ingredientIds, quantities, units);
            loadFormData(model, itemMenu, recipe);
            loadAvailabilityFormData(model, itemMenu);
            return "admin/menu-items/form";

        } catch (Exception e) {
            log.error("Error creating menu item", e);
            String friendlyMessage = GlobalExceptionHandler.extractConstraintMessages(e);
            model.addAttribute("errorMessage", friendlyMessage != null
                    ? friendlyMessage
                    : "Error al crear el item del menú: " + e.getMessage());
            List<ItemIngredient> recipe = buildRecipe(ingredientIds, quantities, units);
            loadFormData(model, itemMenu, recipe);
            loadAvailabilityFormData(model, itemMenu);
            return "admin/menu-items/form";
        }
    }

    /**
     * Update an existing menu item and its recipe
     */
    @PostMapping("/{id}")
    public String updateMenuItem(
            @PathVariable Long id,
            @Valid @ModelAttribute("itemMenu") ItemMenu itemMenu,
            BindingResult bindingResult,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam(value = "ingredientIds", required = false) List<Long> ingredientIds,
            @RequestParam(value = "quantities", required = false) List<String> quantities,
            @RequestParam(value = "units", required = false) List<String> units,
            @RequestParam(value = "requiresPreparation", required = false) Boolean requiresPreparationParam,
            @RequestParam(value = "requiresBaristaPreparation", required = false) Boolean requiresBaristaPreparationParam,
            @RequestParam(value = "requiresParrilleroPreparation", required = false) Boolean requiresParrilleroPreparationParam,
            @RequestParam(value = "requiresIngredients", required = false) Boolean requiresIngredientsParam,
            @RequestParam(value = "isCombo", required = false) Boolean isComboParam,
            @RequestParam(value = "dineInOnly", required = false) Boolean dineInOnlyParam,
            @RequestParam(value = "comboItemIds", required = false) List<Long> comboItemIds,
            @RequestParam(value = "comboItemQuantities", required = false) List<Integer> comboItemQuantities,
            @RequestParam(value = "selectedSauceIds", required = false) List<Long> selectedSauceIds,
            @RequestParam(value = "saucesSyncEnabled", required = false) Boolean saucesSyncEnabled,
            @RequestParam(value = "selectedSpecialityIds", required = false) List<Long> selectedSpecialityIds,
            @RequestParam(value = "specialitiesSyncEnabled", required = false) Boolean specialitiesSyncEnabled,
            @RequestParam(value = "hasCustomSchedule", required = false) Boolean hasCustomSchedule,
            @RequestParam Map<String, String> allParams,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Updating menu item with ID: {}", id);
        log.info("🔍 requiresPreparation received from form: {}", itemMenu.getRequiresPreparation());
        log.info("🔍 requiresPreparation as @RequestParam: {}", requiresPreparationParam);
        log.info("🔍 requiresBaristaPreparation received from form: {}", itemMenu.getRequiresBaristaPreparation());
        log.info("🔍 requiresBaristaPreparation as @RequestParam: {}", requiresBaristaPreparationParam);
        log.info("🔍 requiresParrilleroPreparation received from form: {}", itemMenu.getRequiresParrilleroPreparation());
        log.info("🔍 requiresParrilleroPreparation as @RequestParam: {}", requiresParrilleroPreparationParam);
        log.info("🔍 requiresIngredients: {}", requiresIngredientsParam);
        log.info("🔍 saucesSyncEnabled: {} | selectedSauceIds: {}", saucesSyncEnabled, selectedSauceIds);
        log.info("🔍 hasCustomSchedule: {}", hasCustomSchedule);
        
        // Si el parámetro está presente, usarlo (para debug)
        if (requiresPreparationParam != null) {
            log.info("🔍 Using @RequestParam value for Chef: {}", requiresPreparationParam);
            itemMenu.setRequiresPreparation(requiresPreparationParam);
        }
        
        if (requiresBaristaPreparationParam != null) {
            log.info("🔍 Using @RequestParam value for Barista: {}", requiresBaristaPreparationParam);
            itemMenu.setRequiresBaristaPreparation(requiresBaristaPreparationParam);
        }

        if (requiresParrilleroPreparationParam != null) {
            log.info("🔍 Using @RequestParam value for Parrillero: {}", requiresParrilleroPreparationParam);
            itemMenu.setRequiresParrilleroPreparation(requiresParrilleroPreparationParam);
        }
        
        // Set isCombo on item
        itemMenu.setIsCombo(Boolean.TRUE.equals(isComboParam));
        log.info("🔍 isCombo: {}", isComboParam);
        
        // Set requiresIngredients on item (combos always force false)
        if (Boolean.TRUE.equals(itemMenu.getIsCombo())) {
            itemMenu.setRequiresIngredients(false);
        } else {
            itemMenu.setRequiresIngredients(Boolean.TRUE.equals(requiresIngredientsParam));
        }
        
        // Set dineInOnly on item (independent of requiresIngredients)
        itemMenu.setDineInOnly(Boolean.TRUE.equals(dineInOnlyParam));
        
        // Set hasCustomSchedule on item
        itemMenu.setHasCustomSchedule(Boolean.TRUE.equals(hasCustomSchedule));

        // Validate mutual exclusion: only one of Chef, Barista, Parrillero, or Combo can be selected
        int selectedOptions = 0;
        if (Boolean.TRUE.equals(itemMenu.getRequiresPreparation())) selectedOptions++;
        if (Boolean.TRUE.equals(itemMenu.getRequiresBaristaPreparation())) selectedOptions++;
        if (Boolean.TRUE.equals(itemMenu.getRequiresParrilleroPreparation())) selectedOptions++;
        if (Boolean.TRUE.equals(itemMenu.getIsCombo())) selectedOptions++;
        if (selectedOptions > 1) {
             model.addAttribute("errorMessage", "Solo puede seleccionar una opción: preparación por Chef, Barista, Parrillero o Combo.");
             List<BigDecimal> quantitiesBD = convertQuantities(quantities);
             List<ItemIngredient> recipe = buildRecipe(ingredientIds, quantitiesBD, units);
             loadFormData(model, itemMenu, recipe);
             loadAvailabilityFormData(model, itemMenu);
             model.addAttribute("formAction", "/admin/menu-items/" + id);
             return "admin/menu-items/form";
        }

        if (bindingResult.hasErrors()) {
            loadFormData(model, itemMenu, itemMenuService.getRecipe(id));
            loadAvailabilityFormData(model, itemMenu);
            model.addAttribute("formAction", "/admin/menu-items/" + id);
            return "admin/menu-items/form";
        }

        try {
            // Handle image upload
            if (imageFile != null && !imageFile.isEmpty()) {
                if (!imageStorageService.isValidImage(imageFile)) {
                    model.addAttribute("errorMessage", "Imagen inválida. Solo se permiten imágenes JPG, PNG, GIF o WEBP de máximo 5MB");
                    loadFormData(model, itemMenu, itemMenuService.getRecipe(id));
                    loadAvailabilityFormData(model, itemMenu);
                    model.addAttribute("formAction", "/admin/menu-items/" + id);
                    return "admin/menu-items/form";
                }
                
                // Delete old image if exists
                itemMenuService.findById(id).ifPresent(existing -> {
                    if (existing.getImageUrl() != null && !existing.getImageUrl().isEmpty()) {
                        imageStorageService.deleteImage(existing.getImageUrl());
                    }
                });
                
                String imagePath = imageStorageService.saveImage(imageFile, "menu-items", itemMenu.getName());
                itemMenu.setImageUrl(imagePath);
            } else {
                // No new server-side file uploaded. The submitted URL may be:
                //  (a) empty   → user removed the existing image
                //  (b) same as existing → no change
                //  (c) different (Direct Upload) → user replaced the image, delete the old one
                String submittedUrl = itemMenu.getImageUrl();
                if (submittedUrl == null || submittedUrl.trim().isEmpty()) {
                    itemMenuService.findById(id).ifPresent(existing -> {
                        if (existing.getImageUrl() != null && !existing.getImageUrl().isEmpty()) {
                            imageStorageService.deleteImage(existing.getImageUrl());
                            log.info("Image removed for item ID: {}", id);
                        }
                    });
                    itemMenu.setImageUrl(null);
                } else {
                    itemMenuService.findById(id).ifPresent(existing -> {
                        if (existing.getImageUrl() != null && !existing.getImageUrl().isEmpty()
                                && !existing.getImageUrl().equals(submittedUrl)) {
                            imageStorageService.deleteImage(existing.getImageUrl());
                            log.info("Old image replaced via Direct Upload for item ID: {}", id);
                        }
                    });
                    itemMenu.setImageUrl(submittedUrl);
                }
            }

            // Build recipe from form data
            List<ItemIngredient> recipe = null;
            if (ingredientIds != null && !ingredientIds.isEmpty()) {
                // Convert String quantities to BigDecimal safely
                List<BigDecimal> quantitiesBD = convertQuantities(quantities);
                recipe = buildRecipe(ingredientIds, quantitiesBD, units);
            }

            // Validate ingredients based on requiresIngredients flag
            if (!Boolean.TRUE.equals(itemMenu.getRequiresIngredients()) || Boolean.TRUE.equals(itemMenu.getIsCombo())) {
                // Items without recipe and Combo items must NOT have ingredients - force empty recipe
                // (silently discard any submitted ingredients when converting away from requiresIngredients)
                recipe = new ArrayList<>();
            } else {
                // Items that require ingredients must have at least one
                if (recipe == null || recipe.isEmpty()) {
                    model.addAttribute("errorMessage", "Debe agregar al menos un ingrediente a la receta");
                    loadFormData(model, itemMenu, itemMenuService.getRecipe(id));
                    loadAvailabilityFormData(model, itemMenu);
                    model.addAttribute("formAction", "/admin/menu-items/" + id);
                    return "admin/menu-items/form";
                }
            }
            
            // Validate combo items if this is a combo
            if (Boolean.TRUE.equals(itemMenu.getIsCombo())) {
                if (comboItemIds == null || comboItemIds.isEmpty()) {
                    model.addAttribute("errorMessage", "Un combo debe tener al menos 1 item del menú asociado");
                    loadFormData(model, itemMenu, new ArrayList<>());
                    loadAvailabilityFormData(model, itemMenu);
                    model.addAttribute("formAction", "/admin/menu-items/" + id);
                    return "admin/menu-items/form";
                }
                
                // Validate that all combo child items are active
                List<String> inactiveItems = new ArrayList<>();
                for (Long childId : comboItemIds) {
                    Optional<ItemMenu> childItem = itemMenuService.findById(childId);
                    if (childItem.isEmpty() || !Boolean.TRUE.equals(childItem.get().getActive())) {
                        inactiveItems.add(childItem.isPresent() ? childItem.get().getName() : "ID: " + childId);
                    }
                }
                
                if (!inactiveItems.isEmpty()) {
                    model.addAttribute("errorMessage", 
                        "No se puede actualizar el combo con items inactivos: " + String.join(", ", inactiveItems) + 
                        ". Por favor, active estos items o seléccione otros.");
                    loadFormData(model, itemMenu, new ArrayList<>());
                    loadAvailabilityFormData(model, itemMenu);
                    model.addAttribute("formAction", "/admin/menu-items/" + id);
                    return "admin/menu-items/form";
                }
            }
            
            ItemMenu updated = itemMenuService.update(id, itemMenu, recipe);
            
            // Update combo items if this is a combo
            if (Boolean.TRUE.equals(itemMenu.getIsCombo()) && comboItemIds != null && !comboItemIds.isEmpty()) {
                itemMenuService.updateComboItems(id, comboItemIds, comboItemQuantities);
                log.info("Updated {} combo items for combo '{}'", comboItemIds.size(), updated.getName());
            }
            
            // Update availability schedule only if custom schedule is enabled
            if (Boolean.TRUE.equals(hasCustomSchedule)) {
                Map<DayOfWeek, LocalTime[]> manualSchedule = extractManualSchedule(allParams);
                if (!manualSchedule.isEmpty()) {
                    itemMenuService.updateAvailabilityScheduleManual(id, manualSchedule);
                } else {
                    // Custom schedule enabled but no days selected - disable it
                    log.info("Custom schedule enabled but no days selected, disabling custom schedule for item {}", id);
                    itemMenuService.clearAvailabilitySchedule(id);
                }
            } else {
                // No custom schedule - clear any existing availability days
                itemMenuService.clearAvailabilitySchedule(id);
            }
            
            // Sync sauces only if the user actually changed the selection (flag set by JS).
            // If the flag is absent/false we leave the existing sauce associations untouched.
            String saucesMessage = "";
            if (Boolean.TRUE.equals(saucesSyncEnabled)) {
                List<Long> desired = selectedSauceIds != null ? selectedSauceIds : new ArrayList<>();
                int[] diff = syncSaucesForItemDiff(id, desired);
                log.info("Synced sauces for item {}: +{} added, -{} removed", updated.getName(), diff[0], diff[1]);
                if (diff[0] > 0 || diff[1] > 0) {
                    saucesMessage = " (salsas: +" + diff[0] + " / -" + diff[1] + ")";
                }
            }

            // Sync specialities only if the user actually changed the selection (flag set by JS).
            String specialitiesMessage = "";
            if (Boolean.TRUE.equals(specialitiesSyncEnabled)) {
                List<Long> desired = selectedSpecialityIds != null ? selectedSpecialityIds : new ArrayList<>();
                int[] diff = syncSpecialitiesForItemDiff(id, desired);
                log.info("Synced specialities for item {}: +{} added, -{} removed", updated.getName(), diff[0], diff[1]);
                if (diff[0] > 0 || diff[1] > 0) {
                    specialitiesMessage = " (especialidades: +" + diff[0] + " / -" + diff[1] + ")";
                }
            }

            log.info("Menu item updated successfully: {}", updated.getIdItemMenu());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Item del menú '" + updated.getName() + "' actualizado exitosamente" + saucesMessage + specialitiesMessage);
            return "redirect:/admin/menu-items";

        } catch (IllegalArgumentException e) {
            log.error("Validation error updating menu item: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", e.getMessage());
            loadFormData(model, itemMenu, itemMenuService.getRecipe(id));
            loadAvailabilityFormData(model, itemMenu);
            model.addAttribute("formAction", "/admin/menu-items/" + id);
            return "admin/menu-items/form";

        } catch (Exception e) {
            log.error("Error updating menu item: {}", e.getMessage(), e);
            String friendlyMessage = GlobalExceptionHandler.extractConstraintMessages(e);
            model.addAttribute("errorMessage", friendlyMessage != null
                    ? friendlyMessage
                    : "Error al actualizar el item del menú: " + e.getMessage());
            loadFormData(model, itemMenu, itemMenuService.getRecipe(id));
            loadAvailabilityFormData(model, itemMenu);
            model.addAttribute("formAction", "/admin/menu-items/" + id);
            return "admin/menu-items/form";
        }
    }

    /**
     * Activate a menu item
     */
    @PostMapping("/{id}/activate")
    public String activateMenuItem(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Activating menu item with ID: {}", id);

        try {
            ItemMenu item = itemMenuService.activate(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Item '" + item.getName() + "' activado exitosamente");
        } catch (Exception e) {
            log.error("Error activating menu item", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al activar el item: " + e.getMessage());
        }

        return "redirect:/admin/menu-items";
    }

    /**
     * Deactivate a menu item
     */
    @PostMapping("/{id}/deactivate")
    public String deactivateMenuItem(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Deactivating menu item with ID: {}", id);

        try {
            ItemMenu item = itemMenuService.deactivate(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Item '" + item.getName() + "' desactivado exitosamente");
        } catch (Exception e) {
            log.error("Error deactivating menu item", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al desactivar el item: " + e.getMessage());
        }

        return "redirect:/admin/menu-items";
    }

    /**
     * Delete a menu item
     */
    @PostMapping("/{id}/delete")
    public String deleteMenuItem(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Deleting menu item with ID: {}", id);

        try {
            itemMenuService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Item del menú eliminado exitosamente");
        } catch (Exception e) {
            log.error("Error deleting menu item", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al eliminar el item: " + e.getMessage());
        }

        return "redirect:/admin/menu-items";
    }

    // ========== AJAX Endpoints ==========

    /**
     * Get ingredient details by ID (AJAX)
     * Returns unit of measure and other relevant info
     */
    @GetMapping("/ingredient/{ingredientId}")
    @ResponseBody
    public Map<String, Object> getIngredientDetails(@PathVariable Long ingredientId) {
        log.debug("Fetching ingredient details for ID: {}", ingredientId);
        
        Map<String, Object> response = new HashMap<>();
        try {
            Ingredient ingredient = ingredientService.findById(ingredientId)
                    .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado"));
            
            response.put("success", true);
            response.put("id", ingredient.getIdIngredient());
            response.put("name", ingredient.getName());
            response.put("unitOfMeasure", ingredient.getUnitOfMeasure());
            response.put("currentStock", ingredient.getCurrentStock());
            response.put("costPerUnit", ingredient.getCostPerUnit());
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return response;
    }

    /**
     * Get recipe for a menu item (AJAX)
     */
    @GetMapping("/{id}/recipe")
    @ResponseBody
    public List<Map<String, Object>> getRecipe(@PathVariable Long id) {
        log.debug("Fetching recipe for menu item ID: {}", id);
        
        List<ItemIngredient> recipe = itemMenuService.getRecipe(id);
        List<Map<String, Object>> response = new ArrayList<>();
        
        for (ItemIngredient item : recipe) {
            Map<String, Object> ingredientData = new HashMap<>();
            ingredientData.put("id", item.getIdItemIngredient());
            ingredientData.put("ingredientId", item.getIngredient().getIdIngredient());
            ingredientData.put("ingredientName", item.getIngredient().getName());
            ingredientData.put("quantity", item.getQuantity());
            ingredientData.put("unit", item.getUnit());
            ingredientData.put("currentStock", item.getIngredient().getCurrentStock());
            response.add(ingredientData);
        }
        
        return response;
    }

    /**
     * Get combo items for a combo menu item (AJAX)
     */
    @GetMapping("/{id}/combo-items")
    @ResponseBody
    public Map<String, Object> getComboItems(@PathVariable Long id) {
        log.debug("Fetching combo items for menu item ID: {}", id);
        
        Map<String, Object> response = new HashMap<>();
        try {
            List<ItemMenuComboItem> comboItems = itemMenuService.getComboItems(id);
            List<Map<String, Object>> items = new ArrayList<>();
            
            for (ItemMenuComboItem ci : comboItems) {
                Map<String, Object> itemData = new HashMap<>();
                itemData.put("id", ci.getIdComboItem());
                itemData.put("childItemId", ci.getChildMenu().getIdItemMenu());
                itemData.put("childItemName", ci.getChildMenu().getName());
                itemData.put("childItemPrice", ci.getChildMenu().getPrice());
                itemData.put("childItemImageUrl", ci.getChildMenu().getImageUrl());
                itemData.put("childRequiresPreparation", ci.getChildMenu().getRequiresPreparation());
                itemData.put("childRequiresBaristaPreparation", ci.getChildMenu().getRequiresBaristaPreparation());
                itemData.put("childRequiresParrilleroPreparation", ci.getChildMenu().getRequiresParrilleroPreparation());
                itemData.put("childActive", ci.getChildMenu().getActive());
                itemData.put("quantity", ci.getQuantity());
                itemData.put("displayOrder", ci.getDisplayOrder());
                items.add(itemData);
            }
            
            response.put("success", true);
            response.put("comboItems", items);
        } catch (Exception e) {
            log.error("Error fetching combo items for item {}: {}", id, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("comboItems", new ArrayList<>());
        }
        
        return response;
    }

    /**
     * Get all available (non-combo) items for combo child selection (AJAX)
     */
    @GetMapping("/available-for-combo")
    @ResponseBody
    public List<Map<String, Object>> getAvailableItemsForCombo(@RequestParam(required = false) Long excludeId) {
        log.debug("Fetching available items for combo selection, excluding ID: {}", excludeId);
        
        return itemMenuService.findAll().stream()
            .filter(item -> !Boolean.TRUE.equals(item.getIsCombo()))
            .filter(item -> Boolean.TRUE.equals(item.getActive()))
            .filter(item -> excludeId == null || !item.getIdItemMenu().equals(excludeId))
            .map(item -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", item.getIdItemMenu());
                dto.put("name", item.getName());
                dto.put("price", item.getPrice());
                dto.put("imageUrl", item.getImageUrl());
                dto.put("categoryName", item.getCategory() != null ? item.getCategory().getName() : "");
                dto.put("requiresPreparation", item.getRequiresPreparation());
                dto.put("requiresBaristaPreparation", item.getRequiresBaristaPreparation());
                dto.put("requiresParrilleroPreparation", item.getRequiresParrilleroPreparation());
                dto.put("requiresIngredients", item.getRequiresIngredients());
                dto.put("active", item.getActive());
                return dto;
            })
            .collect(Collectors.toList());
    }

    /**
     * Check stock availability (AJAX)
     */
    @GetMapping("/{id}/check-stock")
    @ResponseBody
    public Map<String, Object> checkStock(@PathVariable Long id, @RequestParam int quantity) {
        log.debug("Checking stock for menu item {} quantity {}", id, quantity);
        
        Map<String, Object> response = new HashMap<>();
        try {
            boolean hasStock = itemMenuService.hasEnoughStock(id, quantity);
            response.put("hasStock", hasStock);
            response.put("success", true);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return response;
    }

    /**
     * Get maximum available quantity for an item based on ingredient stock (AJAX)
     */
    @GetMapping("/{id}/max-quantity")
    @ResponseBody
    public Map<String, Object> getMaxQuantity(@PathVariable Long id) {
        log.debug("Getting max available quantity for menu item {}", id);
        
        Map<String, Object> response = new HashMap<>();
        try {
            int maxQuantity = itemMenuService.getMaxAvailableQuantity(id);
            response.put("maxQuantity", maxQuantity);
            response.put("success", true);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("maxQuantity", 0);
        }
        
        return response;
    }

    /**
     * Get item cost calculation (AJAX)
     */
    @GetMapping("/{id}/cost")
    @ResponseBody
    public Map<String, Object> getItemCost(@PathVariable Long id) {
        log.debug("Calculating cost for menu item ID: {}", id);
        
        Map<String, Object> response = new HashMap<>();
        try {
            ItemMenu item = itemMenuService.findByIdOrThrow(id);
            BigDecimal cost = itemMenuService.calculateIngredientsCost(id);
            BigDecimal profitMargin = item.calculateProfitMarginPercentage();
            
            response.put("cost", cost);
            response.put("price", item.getPrice());
            response.put("profitMargin", profitMargin);
            response.put("success", true);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return response;
    }

    // ========== Helper Methods ==========

    /**
     * Convert String quantities to BigDecimal safely
     */
    private List<BigDecimal> convertQuantities(List<String> quantities) {
        List<BigDecimal> quantitiesBD = new ArrayList<>();
        if (quantities != null) {
            for (String qty : quantities) {
                try {
                    if (qty != null && !qty.trim().isEmpty()) {
                        quantitiesBD.add(new BigDecimal(qty));
                    } else {
                        quantitiesBD.add(BigDecimal.ZERO);
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid quantity format: {}", qty);
                    quantitiesBD.add(BigDecimal.ZERO);
                }
            }
        }
        return quantitiesBD;
    }

    /**
     * Build recipe from form parameters
     */
    private List<ItemIngredient> buildRecipe(List<Long> ingredientIds, 
                                               List<BigDecimal> quantities, 
                                               List<String> units) {
        List<ItemIngredient> recipe = new ArrayList<>();
        
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return recipe;
        }
        
        // Track ingredient IDs to detect duplicates
        List<Long> seenIds = new ArrayList<>();
        
        for (int i = 0; i < ingredientIds.size(); i++) {
            Long ingredientId = ingredientIds.get(i);
            BigDecimal quantity = quantities != null && i < quantities.size() ? quantities.get(i) : BigDecimal.ZERO;
            String unit = units != null && i < units.size() ? units.get(i) : "";
            
            if (ingredientId != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
                // Check for duplicate ingredient
                if (seenIds.contains(ingredientId)) {
                    // Find ingredient name for better error message
                    Optional<Ingredient> duplicateIngredient = ingredientService.findById(ingredientId);
                    String ingredientName = duplicateIngredient.map(Ingredient::getName).orElse("ID: " + ingredientId);
                    throw new IllegalArgumentException(
                        "El ingrediente '" + ingredientName + "' está repetido en la receta. " +
                        "Si necesita más cantidad, aumente la cantidad en lugar de agregar el mismo ingrediente varias veces."
                    );
                }
                
                seenIds.add(ingredientId);
                
                Ingredient ingredient = new Ingredient();
                ingredient.setIdIngredient(ingredientId);
                
                ItemIngredient itemIngredient = ItemIngredient.builder()
                        .ingredient(ingredient)
                        .quantity(quantity)
                        .unit(unit)
                        .build();
                
                recipe.add(itemIngredient);
            }
        }
        
        return recipe;
    }

    /**
     * Load common form data
     */
    private void loadFormData(Model model, ItemMenu itemMenu, List<ItemIngredient> recipe) {
        List<Category> categories = categoryService.getAllCategories();
        List<Ingredient> ingredients = ingredientService.findAll();
        
        // Convertir ingredientes a DTOs simples para evitar referencias circulares
        List<Map<String, Object>> ingredientsDTO = ingredients.stream()
            .map(ing -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("idIngredient", ing.getIdIngredient());
                dto.put("name", ing.getName());
                dto.put("unitOfMeasure", ing.getUnitOfMeasure());
                dto.put("currentStock", ing.getCurrentStock());
                dto.put("categoryName", ing.getCategory() != null ? ing.getCategory().getName() : "");
                return dto;
            })
            .collect(java.util.stream.Collectors.toList());

        model.addAttribute("itemMenu", itemMenu);
        model.addAttribute("categories", categories);
        model.addAttribute("ingredients", ingredients);
        model.addAttribute("ingredientsDTO", ingredientsDTO);
        model.addAttribute("recipe", recipe);
        model.addAttribute("formAction", itemMenu.getIdItemMenu() != null ? 
                "/admin/menu-items/" + itemMenu.getIdItemMenu() : "/admin/menu-items");
        
        // Add combo items data
        if (itemMenu.getIdItemMenu() != null && Boolean.TRUE.equals(itemMenu.getIsCombo())) {
            List<ItemMenuComboItem> comboItems = itemMenuService.getComboItems(itemMenu.getIdItemMenu());
            model.addAttribute("comboItems", comboItems);
        } else {
            model.addAttribute("comboItems", new ArrayList<>());
        }
        
        // Add all non-combo items for combo selection dropdown
        List<ItemMenu> allNonComboItems = itemMenuService.findAll().stream()
            .filter(i -> !Boolean.TRUE.equals(i.getIsCombo()) && 
                         (itemMenu.getIdItemMenu() == null || !i.getIdItemMenu().equals(itemMenu.getIdItemMenu())))
            .collect(Collectors.toList());
        model.addAttribute("availableComboChildItems", allNonComboItems);

        // Add active sauces and currently associated sauce IDs (multi-tenant)
        loadSaucesFormData(model, itemMenu.getIdItemMenu());
        // Add active specialities and currently associated speciality IDs (multi-tenant)
        loadSpecialitiesFormData(model, itemMenu.getIdItemMenu());
    }

    /**
     * Load availability form data (business hours and selected days)
     */
    private void loadAvailabilityFormData(Model model, ItemMenu itemMenu) {
        // Get all business hours for the form
        List<BusinessHours> allBusinessHours = businessHoursService.getAllBusinessHours();
        
        // Build a map of day -> availability for quick lookup
        Map<DayOfWeek, ItemMenuAvailability> availabilityMap = new HashMap<>();
        if (itemMenu.getAvailabilityDays() != null) {
            for (ItemMenuAvailability avail : itemMenu.getAvailabilityDays()) {
                availabilityMap.put(avail.getDayOfWeek(), avail);
            }
        }
        
        // Build a list of all days with their business hours info
        List<Map<String, Object>> daysWithHours = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            Map<String, Object> dayInfo = new HashMap<>();
            dayInfo.put("day", day);
            dayInfo.put("displayName", day.getDisplayName());
            
            // Find business hours for this day
            Optional<BusinessHours> bh = allBusinessHours.stream()
                    .filter(h -> h.getDayOfWeek() == day)
                    .findFirst();
            
            if (bh.isPresent()) {
                dayInfo.put("isWorkDay", !bh.get().getIsClosed());
                dayInfo.put("openTime", bh.get().getOpenTime());
                dayInfo.put("closeTime", bh.get().getCloseTime());
            } else {
                dayInfo.put("isWorkDay", false);
                dayInfo.put("openTime", null);
                dayInfo.put("closeTime", null);
            }
            
            // Check if this day is selected for the item
            boolean isSelected = false;
            ItemMenuAvailability dayAvailability = availabilityMap.get(day);
            
            // Always add per-day times (null if not selected) to avoid Thymeleaf errors
            if (dayAvailability != null) {
                isSelected = true;
                // Add per-day times for custom schedule
                dayInfo.put("selectedStartTime", dayAvailability.getStartTime());
                dayInfo.put("selectedEndTime", dayAvailability.getEndTime());
            } else {
                // Set null values to avoid property-not-found errors in Thymeleaf
                dayInfo.put("selectedStartTime", null);
                dayInfo.put("selectedEndTime", null);
                // For new items or items without custom schedule, don't preselect days
            }
            dayInfo.put("isSelected", isSelected);
            
            daysWithHours.add(dayInfo);
        }
        
        model.addAttribute("daysWithHours", daysWithHours);
        model.addAttribute("allDaysOfWeek", DayOfWeek.values());
    }

    /**
     * Parse time string to LocalTime
     */
    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(timeStr.trim());
        } catch (Exception e) {
            log.warn("Failed to parse time: {}", timeStr);
            return null;
        }
    }

    /**
     * Extract manual schedule from form parameters.
     * Looks for parameters named manualDay_DAYNAME, manualStartTime_DAYNAME, manualEndTime_DAYNAME
     * @param allParams Map of all request parameters
     * @return Map of DayOfWeek to [startTime, endTime] array
     */
    private Map<DayOfWeek, LocalTime[]> extractManualSchedule(Map<String, String> allParams) {
        Map<DayOfWeek, LocalTime[]> schedule = new java.util.LinkedHashMap<>();
        
        for (DayOfWeek day : DayOfWeek.values()) {
            String dayName = day.name();
            String dayCheckKey = "manualDay_" + dayName;
            
            // Check if this day is selected (checkbox checked)
            if (allParams.containsKey(dayCheckKey)) {
                String startTimeStr = allParams.get("manualStartTime_" + dayName);
                String endTimeStr = allParams.get("manualEndTime_" + dayName);
                
                LocalTime startTime = parseTime(startTimeStr);
                LocalTime endTime = parseTime(endTimeStr);
                
                // Validate time range: startTime must be before endTime
                if (startTime != null && endTime != null && !startTime.isBefore(endTime)) {
                    throw new IllegalArgumentException(
                        String.format("Horario inválido para %s: La hora de inicio (%s) debe ser menor que la hora de fin (%s). Los horarios que cruzan medianoche no están soportados.",
                            day.getDisplayName(), startTime, endTime)
                    );
                }
                
                // Only add if at least the day is selected (times can be null = all day)
                schedule.put(day, new LocalTime[]{startTime, endTime});
                
                log.debug("Manual schedule for {}: {} - {}", dayName, startTime, endTime);
            }
        }
        
        log.info("Extracted manual schedule for {} days", schedule.size());
        return schedule;
    }

    /**
     * Add sauces from the given list that are not already associated to this item.
     * Used by CREATE: only adds, never removes.
     * @return number of newly associated sauces
     */
    private int syncSaucesForItem(Long itemMenuId, List<Long> selectedSauceIds) {
        if (selectedSauceIds == null || selectedSauceIds.isEmpty()) return 0;

        // Restrict to sauces that actually belong to this company (security against ID injection).
        java.util.Set<Long> validSauceIds = complementService.findAllActiveSauces().stream()
                .map(Complement::getIdComplement)
                .collect(Collectors.toSet());

        java.util.Set<Long> alreadyAssociated = itemMenuComplementRepository.findByItemMenuIdItemMenu(itemMenuId)
                .stream()
                .map(ic -> ic.getComplement().getIdComplement())
                .collect(Collectors.toSet());

        int count = 0;
        for (Long sauceId : selectedSauceIds) {
            if (!validSauceIds.contains(sauceId) || alreadyAssociated.contains(sauceId)) continue;
            // maxQuantity=1 for sauces, minQuantity=0 (optional)
            complementService.addComplementToItemMenu(itemMenuId, sauceId, 1, 0);
            count++;
        }
        return count;
    }

    /**
     * Reconcile the sauce associations of an item to match the desired selection.
     * Adds sauces present in the selection but not currently associated, and removes
     * sauces currently associated that are no longer in the selection. Only sauces
     * (isSauce=true) belonging to the current company are considered – non-sauce
     * complements associated to the item are left untouched.
     * @return int[]{added, removed}
     */
    private int[] syncSaucesForItemDiff(Long itemMenuId, List<Long> desiredSauceIds) {
        // Sauces of the current company only (multi-tenant safety).
        java.util.Map<Long, Complement> companySauces = complementService.findAllActiveSauces().stream()
                .collect(Collectors.toMap(Complement::getIdComplement, c -> c));

        // Currently associated sauce IDs for this item (filter by isSauce so we don't touch regular complements).
        java.util.Set<Long> currentlyAssociatedSauces = itemMenuComplementRepository.findByItemMenuIdItemMenu(itemMenuId)
                .stream()
                .map(ic -> ic.getComplement())
                .filter(c -> Boolean.TRUE.equals(c.getIsSauce()))
                .map(Complement::getIdComplement)
                .collect(Collectors.toSet());

        // Desired set, restricted to valid company sauces.
        java.util.Set<Long> desiredSet = desiredSauceIds.stream()
                .filter(companySauces::containsKey)
                .collect(Collectors.toSet());

        int added = 0;
        for (Long id : desiredSet) {
            if (!currentlyAssociatedSauces.contains(id)) {
                complementService.addComplementToItemMenu(itemMenuId, id, 1, 0);
                added++;
            }
        }

        int removed = 0;
        for (Long id : currentlyAssociatedSauces) {
            if (!desiredSet.contains(id)) {
                complementService.removeComplementFromItemMenu(itemMenuId, id);
                removed++;
            }
        }
        return new int[]{added, removed};
    }

    /**
     * Add the available sauces (and the IDs already associated to the item) to the model
     * so the form can render the scrollable sauce-selection list. Multi-tenant safe.
     */
    private void loadSaucesFormData(Model model, Long itemMenuId) {
        List<Complement> allActiveSauces = complementService.findAllActiveSauces();
        model.addAttribute("allActiveSauces", allActiveSauces);

        List<Long> associatedSauceIds;
        if (itemMenuId != null) {
            associatedSauceIds = itemMenuComplementRepository.findByItemMenuIdItemMenu(itemMenuId).stream()
                    .map(ic -> ic.getComplement())
                    .filter(c -> Boolean.TRUE.equals(c.getIsSauce()))
                    .map(Complement::getIdComplement)
                    .collect(Collectors.toList());
        } else {
            associatedSauceIds = new ArrayList<>();
        }
        model.addAttribute("associatedSauceIds", associatedSauceIds);
    }

    /**
     * Add sauces logic mirror for specialities: only adds, never removes.
     */
    private int syncSpecialitiesForItem(Long itemMenuId, List<Long> selectedSpecialityIds) {
        if (selectedSpecialityIds == null || selectedSpecialityIds.isEmpty()) return 0;

        java.util.Set<Long> validSpecialityIds = complementService.findAllActiveSpecialities().stream()
                .map(Complement::getIdComplement)
                .collect(Collectors.toSet());

        java.util.Set<Long> alreadyAssociated = itemMenuComplementRepository.findByItemMenuIdItemMenu(itemMenuId)
                .stream()
                .map(ic -> ic.getComplement().getIdComplement())
                .collect(Collectors.toSet());

        int count = 0;
        for (Long specId : selectedSpecialityIds) {
            if (!validSpecialityIds.contains(specId) || alreadyAssociated.contains(specId)) continue;
            complementService.addComplementToItemMenu(itemMenuId, specId, 1, 0);
            count++;
        }
        return count;
    }

    /**
     * Reconcile speciality associations for the item to match the desired selection.
     * Mirrors {@code syncSaucesForItemDiff} but operates on speciality complements only.
     */
    private int[] syncSpecialitiesForItemDiff(Long itemMenuId, List<Long> desiredSpecialityIds) {
        java.util.Map<Long, Complement> companySpecialities = complementService.findAllActiveSpecialities().stream()
                .collect(Collectors.toMap(Complement::getIdComplement, c -> c));

        java.util.Set<Long> currentlyAssociatedSpecialities = itemMenuComplementRepository.findByItemMenuIdItemMenu(itemMenuId)
                .stream()
                .map(ic -> ic.getComplement())
                .filter(c -> Boolean.TRUE.equals(c.getIsSpeciality()))
                .map(Complement::getIdComplement)
                .collect(Collectors.toSet());

        java.util.Set<Long> desiredSet = desiredSpecialityIds.stream()
                .filter(companySpecialities::containsKey)
                .collect(Collectors.toSet());

        int added = 0;
        for (Long id : desiredSet) {
            if (!currentlyAssociatedSpecialities.contains(id)) {
                complementService.addComplementToItemMenu(itemMenuId, id, 1, 0);
                added++;
            }
        }

        int removed = 0;
        for (Long id : currentlyAssociatedSpecialities) {
            if (!desiredSet.contains(id)) {
                complementService.removeComplementFromItemMenu(itemMenuId, id);
                removed++;
            }
        }
        return new int[]{added, removed};
    }

    /**
     * Mirror of {@code loadSaucesFormData} for specialities.
     */
    private void loadSpecialitiesFormData(Model model, Long itemMenuId) {
        List<Complement> allActiveSpecialities = complementService.findAllActiveSpecialities();
        model.addAttribute("allActiveSpecialities", allActiveSpecialities);

        List<Long> associatedSpecialityIds;
        if (itemMenuId != null) {
            associatedSpecialityIds = itemMenuComplementRepository.findByItemMenuIdItemMenu(itemMenuId).stream()
                    .map(ic -> ic.getComplement())
                    .filter(c -> Boolean.TRUE.equals(c.getIsSpeciality()))
                    .map(Complement::getIdComplement)
                    .collect(Collectors.toList());
        } else {
            associatedSpecialityIds = new ArrayList<>();
        }
        model.addAttribute("associatedSpecialityIds", associatedSpecialityIds);
    }

    // ========== Complement Management for ItemMenu ==========

    /**
     * Show complements management page for a menu item
     */
    @GetMapping("/{id}/complements")
    public String manageComplements(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Managing complements for menu item ID: {}", id);
        
        return itemMenuService.findById(id)
                .map(itemMenu -> {
                    // Get complements already associated with this item
                    List<ItemMenuComplement> itemComplements = itemMenuComplementRepository.findByItemMenuIdItemMenu(id);
                    
                    // Get IDs of complements already associated
                    List<Long> usedComplementIds = itemComplements.stream()
                            .map(ic -> ic.getComplement().getIdComplement())
                            .collect(Collectors.toList());
                    
                    // Get available complements (active ones not already associated) - filtered by company
                    List<Complement> availableComplements = complementService.findAllActive().stream()
                            .filter(c -> !usedComplementIds.contains(c.getIdComplement()))
                            .collect(Collectors.toList());
                    
                    model.addAttribute("itemMenu", itemMenu);
                    model.addAttribute("itemComplements", itemComplements);
                    model.addAttribute("availableComplements", availableComplements);
                    
                    return "admin/menu-items/complements";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Item del menú no encontrado");
                    return "redirect:/admin/menu-items";
                });
    }

    /**
     * Add complement to menu item
     */
    @PostMapping("/{id}/complements/add")
    public String addComplementToItem(
            @PathVariable Long id,
            @RequestParam Long complementId,
            @RequestParam(defaultValue = "3") Integer maxQuantity,
            RedirectAttributes redirectAttributes) {
        
        log.info("Adding complement {} to menu item {}", complementId, id);
        
        // Validate maxQuantity
        if (maxQuantity == null || maxQuantity < 1) {
            redirectAttributes.addFlashAttribute("errorMessage", "La cantidad máxima debe ser al menos 1");
            return "redirect:/admin/menu-items/" + id + "/complements";
        }
        
        try {
            complementService.addComplementToItemMenu(id, complementId, maxQuantity, 0);
            redirectAttributes.addFlashAttribute("successMessage", "Complemento agregado correctamente");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        
        return "redirect:/admin/menu-items/" + id + "/complements";
    }

    /**
     * Toggle complement active status for item
     */
    @PostMapping("/{itemId}/complements/{compId}/toggle")
    public String toggleComplementForItem(
            @PathVariable Long itemId,
            @PathVariable Long compId,
            RedirectAttributes redirectAttributes) {
        
        log.info("Toggling complement {} for item {}", compId, itemId);
        
        itemMenuComplementRepository.findByItemMenuIdItemMenuAndComplementIdComplement(itemId, compId)
                .ifPresent(ic -> {
                    ic.setActive(!ic.getActive());
                    itemMenuComplementRepository.save(ic);
                });
        
        redirectAttributes.addFlashAttribute("successMessage", "Estado del complemento actualizado");
        return "redirect:/admin/menu-items/" + itemId + "/complements";
    }

    /**
     * Remove complement from item
     */
    @PostMapping("/{itemId}/complements/{compId}/delete")
    public String removeComplementFromItem(
            @PathVariable Long itemId,
            @PathVariable Long compId,
            RedirectAttributes redirectAttributes) {
        
        log.info("Removing complement {} from item {}", compId, itemId);
        
        try {
            complementService.removeComplementFromItemMenu(itemId, compId);
            redirectAttributes.addFlashAttribute("successMessage", "Complemento eliminado del item");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        
        return "redirect:/admin/menu-items/" + itemId + "/complements";
    }

    /**
     * Get complements for item (AJAX - for order-menu.html)
     * Returns ALL complements (available and unavailable) with their current stock status
     * Unavailable items will be shown disabled in the UI
     */
    @GetMapping("/{id}/complements/available")
    @ResponseBody
    public Map<String, Object> getAvailableComplementsForItem(@PathVariable Long id) {
        log.debug("Getting all complements (available and unavailable) for item ID: {}", id);
        
        Map<String, Object> response = new HashMap<>();
        try {
            List<ItemMenuComplement> itemComplements = itemMenuComplementRepository
                    .findByItemMenuIdItemMenuAndActiveTrue(id);
            
            List<Map<String, Object>> complementsList = itemComplements.stream()
                    .filter(ic -> ic.getComplement().getActive())
                    .map(ic -> {
                        Complement complement = ic.getComplement();
                        // Dynamically calculate availability based on current stock
                        complement.updateAvailability();
                        
                        Map<String, Object> compData = new HashMap<>();
                        compData.put("id", complement.getIdComplement());
                        compData.put("name", complement.getName());
                        compData.put("description", complement.getDescription());
                        compData.put("extraPrice", complement.getExtraPrice());
                        compData.put("maxQuantity", ic.getMaxQuantity());
                        compData.put("available", complement.getAvailable());
                        return compData;
                    })
                    // Return ALL complements (available and unavailable)
                    // .filter(comp -> Boolean.TRUE.equals(comp.get("available")))
                    .collect(Collectors.toList());
            
            response.put("success", true);
            response.put("complements", complementsList);
        } catch (Exception e) {
            log.error("Error fetching complements for item {}: {}", id, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("complements", new ArrayList<>());
        }
        
        return response;
    }

    /**
     * Check dependencies before hard-deleting a menu item
     */
    @GetMapping("/{id}/check-delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkDeleteDependencies(@PathVariable Long id) {
        log.info("Checking hard-delete dependencies for item ID: {}", id);
        try {
            Map<String, Object> result = itemMenuService.checkHardDeleteDependencies(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error checking dependencies: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Permanently delete a menu item (hard delete)
     */
    @PostMapping("/{id}/hard-delete")
    public String hardDeleteItem(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.warn("Processing hard delete for item ID: {}", id);
        try {
            Map<String, Object> result = itemMenuService.hardDelete(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Item '" + result.get("itemName") + "' eliminado permanentemente de la base de datos.");
        } catch (IllegalStateException e) {
            log.error("Cannot hard-delete item: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error hard-deleting item: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al eliminar: " + e.getMessage());
        }
        return "redirect:/admin/menu-items";
    }

    // ========== Size Management Endpoints ==========

    /**
     * Get size variants for a menu item (AJAX)
     */
    @GetMapping("/{id}/size-items")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSizeItems(@PathVariable Long id) {
        log.debug("Getting size variants for item ID: {}", id);
        Map<String, Object> response = new HashMap<>();
        try {
            List<ItemMenu> sizeItems = itemMenuService.findSizeItems(id);
            List<Map<String, Object>> items = sizeItems.stream().map(si -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", si.getIdItemMenu());
                dto.put("name", si.getName());
                dto.put("sizeName", si.getSizeName());
                dto.put("price", si.getPrice());
                dto.put("available", si.getAvailable());
                dto.put("active", si.getActive());
                dto.put("imageUrl", si.getImageUrl());
                return dto;
            }).collect(Collectors.toList());
            response.put("success", true);
            response.put("sizeItems", items);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching size items for {}: {}", id, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Add a size variant to a menu item (AJAX)
     */
    @PostMapping("/{id}/size-items")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addSizeItem(
            @PathVariable Long id,
            @RequestParam("sizeName") String sizeName,
            @RequestParam("price") java.math.BigDecimal price) {
        log.info("Adding size variant '{}' to item ID: {}", sizeName, id);
        Map<String, Object> response = new HashMap<>();
        try {
            ItemMenu child = itemMenuService.addSizeItem(id, sizeName, price);
            response.put("success", true);
            response.put("message", "Tamaño '" + sizeName + "' agregado exitosamente");
            response.put("id", child.getIdItemMenu());
            response.put("name", child.getName());
            response.put("sizeName", child.getSizeName());
            response.put("price", child.getPrice());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Validation error adding size item: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error adding size item: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Error al agregar tamaño: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Detach a size variant from its parent (AJAX).
     * The child item is NOT deleted — it becomes a free-standing item.
     */
    @PostMapping("/{id}/size-items/{childId}/detach")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> detachSizeItem(
            @PathVariable Long id,
            @PathVariable Long childId) {
        log.info("Detaching size variant ID: {} from parent ID: {}", childId, id);
        Map<String, Object> response = new HashMap<>();
        try {
            itemMenuService.detachSizeItem(childId);
            response.put("success", true);
            response.put("message", "Tamaño desvinculado exitosamente. El item permanece en el menú como item independiente.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Validation error detaching size item: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error detaching size item: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Error al desvincular tamaño: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Update a size variant's sizeName and price (AJAX)
     */
    @PostMapping("/{id}/size-items/{childId}/update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateSizeItem(
            @PathVariable Long id,
            @PathVariable Long childId,
            @RequestParam("sizeName") String sizeName,
            @RequestParam("price") java.math.BigDecimal price) {
        log.info("Updating size variant ID: {} → sizeName='{}', price={}", childId, sizeName, price);
        Map<String, Object> response = new HashMap<>();
        try {
            itemMenuService.updateSizeItem(childId, sizeName, price);
            response.put("success", true);
            response.put("message", "Tamaño actualizado exitosamente");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Validation error updating size item: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error updating size item: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Error al actualizar tamaño: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Search free (unattached) items by name for linking as size variant (AJAX).
     * Only returns items with no parent, from the current company, excluding the parent itself.
     * q: search term (empty = return first 20)
     */
    @GetMapping("/{id}/available-for-linking")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> searchAvailableForLinking(
            @PathVariable Long id,
            @RequestParam(value = "q", defaultValue = "") String query) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<ItemMenu> items = itemMenuService.searchFreeItems(id, query);
            List<Map<String, Object>> dtos = items.stream().map(i -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", i.getIdItemMenu());
                dto.put("name", i.getName());
                dto.put("price", i.getPrice());
                dto.put("categoryName", i.getCategory() != null ? i.getCategory().getName() : "");
                return dto;
            }).collect(Collectors.toList());
            response.put("success", true);
            response.put("items", dtos);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error searching free items: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Error al buscar items: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Link an existing free item as a size variant of this parent (AJAX).
     */
    @PostMapping("/{id}/size-items/link")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> linkExistingItemAsSize(
            @PathVariable Long id,
            @RequestParam("childId") Long childId,
            @RequestParam("sizeName") String sizeName) {
        log.info("Linking item ID: {} as size variant of parent ID: {} with sizeName='{}'", childId, id, sizeName);
        Map<String, Object> response = new HashMap<>();
        try {
            ItemMenu linked = itemMenuService.linkExistingItemAsSize(id, childId, sizeName);
            response.put("success", true);
            response.put("message", "Item vinculado como tamaño '" + sizeName + "' exitosamente.");
            response.put("id", linked.getIdItemMenu());
            response.put("sizeName", linked.getSizeName());
            response.put("name", linked.getName());
            response.put("price", linked.getPrice());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Validation error linking size item: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error linking size item: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Error al vincular item: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
