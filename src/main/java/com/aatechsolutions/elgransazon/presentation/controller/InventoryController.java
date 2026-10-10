package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.application.service.InventoryObjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Landing page of the inventory module.
 * Lets the user choose between the ingredient inventory and the object inventory.
 * Accessible by ADMIN and MANAGER roles
 */
@Controller
@RequestMapping("/admin/inventory")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {

    private final IngredientService ingredientService;
    private final InventoryObjectService inventoryObjectService;

    /**
     * Inventory hub: two big buttons, one per inventory type
     */
    @GetMapping
    public String showInventoryHub(Model model) {
        log.info("Showing inventory hub");

        model.addAttribute("ingredientActiveCount", ingredientService.getActiveCount());
        model.addAttribute("ingredientLowStockCount", ingredientService.countLowStock());
        model.addAttribute("ingredientOutOfStockCount", ingredientService.countOutOfStock());

        model.addAttribute("objectTotalCount", inventoryObjectService.getTotalCount());
        model.addAttribute("objectActiveCount", inventoryObjectService.getActiveCount());
        model.addAttribute("objectDamagedCount", inventoryObjectService.getDamagedCount());
        model.addAttribute("objectInventoryValue", inventoryObjectService.getInventoryValue());

        return "admin/inventory/index";
    }
}
