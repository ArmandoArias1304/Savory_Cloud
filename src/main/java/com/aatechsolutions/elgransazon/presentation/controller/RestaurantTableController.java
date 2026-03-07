package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.RestaurantTableService;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.domain.entity.TableStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

/**
 * Controller for Restaurant Table management
 * Accessible by ADMIN and MANAGER roles
 */
@Controller
@RequestMapping("/admin/tables")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class RestaurantTableController {

    private final RestaurantTableService tableService;

    /**
     * Show list of all tables
     */
    @GetMapping
    public String listTables(Model model) {
        log.debug("Displaying tables list");

        List<RestaurantTable> tables = tableService.findAllOrderByTableNumber();
        List<String> locations = tableService.getDistinctLocations();

        long totalCount = tableService.countAll();
        long availableCount = tableService.countByStatus(TableStatus.AVAILABLE);
        long occupiedCount = tableService.countByStatus(TableStatus.OCCUPIED);
        long outOfServiceCount = tableService.countByStatus(TableStatus.OUT_OF_SERVICE);

        model.addAttribute("tables", tables);
        model.addAttribute("locations", locations);
        model.addAttribute("allStatuses", TableStatus.values());
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("availableCount", availableCount);
        model.addAttribute("occupiedCount", occupiedCount);
        model.addAttribute("outOfServiceCount", outOfServiceCount);

        return "admin/tables/list";
    }

    /**
     * Show form to create a new table
     */
    @GetMapping("/new")
    public String newTableForm(Model model) {
        log.debug("Displaying new table form");

        RestaurantTable table = new RestaurantTable();
        table.setStatus(TableStatus.AVAILABLE);
        
        // Generate next consecutive table number automatically
        Integer nextTableNumber = tableService.getNextTableNumber();
        table.setTableNumber(nextTableNumber);
        log.debug("Auto-generated next table number: {}", nextTableNumber);

        model.addAttribute("table", table);
        model.addAttribute("allStatuses", TableStatus.values());
        model.addAttribute("isEdit", false);
        model.addAttribute("formAction", "/admin/tables");

        return "admin/tables/form";
    }

    /**
     * Show form to edit an existing table
     */
    @GetMapping("/{id}/edit")
    public String editTableForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Displaying edit form for table ID: {}", id);

        return tableService.findById(id)
                .map(table -> {
                    model.addAttribute("table", table);
                    model.addAttribute("allStatuses", TableStatus.values());
                    model.addAttribute("isEdit", true);
                    model.addAttribute("formAction", "/admin/tables/" + id);
                    return "admin/tables/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Mesa no encontrada");
                    return "redirect:/admin/tables";
                });
    }

    /**
     * Create a new table
     */
    @PostMapping
    public String createTable(
            @Valid @ModelAttribute("table") RestaurantTable table,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Creating new table: {}", table.getTableNumber());

        if (bindingResult.hasErrors()) {
            model.addAttribute("allStatuses", TableStatus.values());
            model.addAttribute("isEdit", false);
            model.addAttribute("formAction", "/admin/tables");
            return "admin/tables/form";
        }

        try {
            String currentUsername = authentication.getName();
            RestaurantTable created = tableService.create(table, currentUsername);

            log.info("Table created successfully with ID: {}", created.getId());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Mesa #" + created.getTableNumber() + " creada exitosamente");
            return "redirect:/admin/tables";

        } catch (IllegalArgumentException e) {
            log.error("Validation error creating table: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("allStatuses", TableStatus.values());
            model.addAttribute("isEdit", false);
            model.addAttribute("formAction", "/admin/tables");
            return "admin/tables/form";

        } catch (Exception e) {
            log.error("Error creating table", e);
            model.addAttribute("errorMessage", "Error al crear la mesa: " + e.getMessage());
            model.addAttribute("allStatuses", TableStatus.values());
            model.addAttribute("isEdit", false);
            model.addAttribute("formAction", "/admin/tables");
            return "admin/tables/form";
        }
    }

    /**
     * Update an existing table
     */
    @PostMapping("/{id}")
    public String updateTable(
            @PathVariable Long id,
            @Valid @ModelAttribute("table") RestaurantTable table,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Updating table with ID: {}", id);

        if (bindingResult.hasErrors()) {
            model.addAttribute("allStatuses", TableStatus.values());
            model.addAttribute("isEdit", true);
            model.addAttribute("formAction", "/admin/tables/" + id);
            return "admin/tables/form";
        }

        try {
            String currentUsername = authentication.getName();
            RestaurantTable updated = tableService.update(id, table, currentUsername);

            log.info("Table updated successfully: {}", updated.getId());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Mesa #" + updated.getTableNumber() + " actualizada exitosamente");
            return "redirect:/admin/tables";

        } catch (IllegalArgumentException e) {
            log.error("Validation error updating table: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("allStatuses", TableStatus.values());
            model.addAttribute("isEdit", true);
            model.addAttribute("formAction", "/admin/tables/" + id);
            return "admin/tables/form";

        } catch (Exception e) {
            log.error("Error updating table", e);
            model.addAttribute("errorMessage", "Error al actualizar la mesa: " + e.getMessage());
            model.addAttribute("allStatuses", TableStatus.values());
            model.addAttribute("isEdit", true);
            model.addAttribute("formAction", "/admin/tables/" + id);
            return "admin/tables/form";
        }
    }

    /**
     * Change table status (AJAX)
     */
    @PostMapping("/{id}/change-status")
    @ResponseBody
    public Map<String, Object> changeTableStatus(
            @PathVariable Long id,
            @RequestParam String status,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            TableStatus newStatus = TableStatus.valueOf(status);
            String currentUsername = authentication.getName();
            
            RestaurantTable updated = tableService.changeStatus(id, newStatus, currentUsername);

            response.put("success", true);
            response.put("message", "Estado actualizado correctamente");
            response.put("newStatus", updated.getStatus().name());
            response.put("newStatusDisplay", updated.getStatusDisplayName());

        } catch (IllegalArgumentException e) {
            log.error("Error changing table status: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error changing table status", e);
            response.put("success", false);
            response.put("message", "Error al cambiar el estado de la mesa");
        }

        return response;
    }

    /**
     * Get table details for modal display (AJAX)
     */
    @GetMapping("/{id}/details")
    @ResponseBody
    public Map<String, Object> getTableDetails(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();

        try {
            RestaurantTable table = tableService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));

            Map<String, Object> tableData = new HashMap<>();
            tableData.put("id", table.getId());
            tableData.put("tableNumber", table.getTableNumber());
            tableData.put("capacity", table.getCapacity());
            tableData.put("capacityDisplay", table.getCapacityDisplay());
            tableData.put("location", table.getLocationDisplay());
            tableData.put("status", table.getStatus().name());
            tableData.put("statusDisplay", table.getStatusDisplayName());
            tableData.put("isAvailable", table.isAvailable());
            tableData.put("isOccupied", table.isOccupied()); // Now returns status == OCCUPIED
            tableData.put("comments", table.getComments() != null ? table.getComments() : "Sin comentarios");
            tableData.put("createdBy", table.getCreatedBy() != null ? table.getCreatedBy() : "Desconocido");
            tableData.put("updatedBy", table.getUpdatedBy() != null ? table.getUpdatedBy() : "Desconocido");
            tableData.put("createdAt", table.getFormattedCreatedAt());
            tableData.put("updatedAt", table.getFormattedUpdatedAt());

            response.put("success", true);
            response.put("table", tableData);

        } catch (Exception e) {
            log.error("Error fetching table details", e);
            response.put("success", false);
            response.put("message", "Error al cargar los detalles de la mesa");
        }

        return response;
    }

    /**
     * Check if table can be used for an order (AJAX)
     * This checks if table is AVAILABLE and not blocked by upcoming reservations
     */
    @GetMapping("/{id}/can-use-for-order")
    @ResponseBody
    public Map<String, Object> canUseForOrder(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean canUse = tableService.canTableBeUsedForOrder(id);
            boolean isBlocked = tableService.isTableBlockedByReservation(id);
            RestaurantTable table = tableService.findByIdOrThrow(id);
            
            response.put("success", true);
            response.put("canUseForOrder", canUse);
            response.put("isBlockedByReservation", isBlocked);
            response.put("status", table.getStatusDisplayName());
            
            if (isBlocked) {
                response.put("message", "Mesa bloqueada por reservación próxima dentro del tiempo de consumo promedio");
            }
        } catch (Exception e) {
            log.error("Error checking table availability: {}", id, e);
            response.put("success", false);
            response.put("message", e.getMessage());
        }

        return response;
    }
}
