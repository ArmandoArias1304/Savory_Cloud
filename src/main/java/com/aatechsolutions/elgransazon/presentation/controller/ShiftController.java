package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.EmployeeShiftHistoryService;
import com.aatechsolutions.elgransazon.application.service.ShiftService;
import com.aatechsolutions.elgransazon.domain.entity.DayOfWeek;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Shift;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for Shift management
 * Accessible by ADMIN and MANAGER roles
 */
@Controller
@RequestMapping("/admin/shifts")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class ShiftController {

    private final ShiftService shiftService;
    private final EmployeeService employeeService;
    private final EmployeeShiftHistoryService historyService;

    /**
     * Configure date/time formatting for form binding
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(LocalTime.class, new java.beans.PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null || text.trim().isEmpty()) {
                    setValue(null);
                } else {
                    setValue(LocalTime.parse(text, DateTimeFormatter.ISO_LOCAL_TIME));
                }
            }
            
            @Override
            public String getAsText() {
                LocalTime value = (LocalTime) getValue();
                return (value != null) ? value.format(DateTimeFormatter.ISO_LOCAL_TIME) : "";
            }
        });
    }

    /**
     * Show list of all shifts
     */
    @GetMapping
    public String listShifts(Model model) {
        log.debug("Displaying shifts list");
        
        List<Shift> shifts = shiftService.getAllShifts();
        long activeCount = shiftService.countActiveShifts();
        long totalCount = shiftService.countAllShifts();
        
        model.addAttribute("shifts", shifts);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("totalCount", totalCount);
        
        return "admin/shifts/list";
    }

    /**
     * Show form to create a new shift
     */
    @GetMapping("/new")
    public String newShiftForm(Model model) {
        log.debug("Displaying new shift form");
        
        Shift shift = new Shift();
        shift.setActive(true); // Default to active
        
        model.addAttribute("shift", shift);
        model.addAttribute("allDays", Arrays.asList(DayOfWeek.values()));
        model.addAttribute("isEdit", false);
        
        return "admin/shifts/form";
    }

    /**
     * Show form to edit an existing shift
     */
    @GetMapping("/{id}/edit")
    public String editShiftForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Displaying edit form for shift ID: {}", id);
        
        return shiftService.getShiftById(id)
                .map(shift -> {
                    model.addAttribute("shift", shift);
                    model.addAttribute("allDays", Arrays.asList(DayOfWeek.values()));
                    model.addAttribute("isEdit", true);
                    return "admin/shifts/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Turno no encontrado");
                    return "redirect:/admin/shifts";
                });
    }

    /**
     * Create a new shift
     */
    @PostMapping
    public String createShift(
            @Valid @ModelAttribute("shift") Shift shift,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.info("Creating new shift: {}", shift.getName());
        
        if (bindingResult.hasErrors()) {
            model.addAttribute("allDays", Arrays.asList(DayOfWeek.values()));
            model.addAttribute("isEdit", false);
            return "admin/shifts/form";
        }
        
        try {
            Shift created = shiftService.createShift(shift);
            log.info("Shift created successfully with ID: {}", created.getId());
            
            redirectAttributes.addFlashAttribute("successMessage", 
                    "Turno '" + created.getName() + "' creado exitosamente");
            return "redirect:/admin/shifts";
            
        } catch (IllegalArgumentException e) {
            log.error("Validation error creating shift: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("allDays", Arrays.asList(DayOfWeek.values()));
            model.addAttribute("isEdit", false);
            return "admin/shifts/form";
            
        } catch (Exception e) {
            log.error("Error creating shift", e);
            model.addAttribute("errorMessage", "Error al crear el turno: " + e.getMessage());
            model.addAttribute("allDays", Arrays.asList(DayOfWeek.values()));
            model.addAttribute("isEdit", false);
            return "admin/shifts/form";
        }
    }

    /**
     * Update an existing shift
     */
    @PostMapping("/{id}")
    public String updateShift(
            @PathVariable Long id,
            @Valid @ModelAttribute("shift") Shift shift,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.info("Updating shift with ID: {}", id);
        
        if (bindingResult.hasErrors()) {
            model.addAttribute("allDays", Arrays.asList(DayOfWeek.values()));
            model.addAttribute("isEdit", true);
            return "admin/shifts/form";
        }
        
        try {
            Shift updated = shiftService.updateShift(id, shift);
            log.info("Shift updated successfully");
            
            redirectAttributes.addFlashAttribute("successMessage", 
                    "Turno '" + updated.getName() + "' actualizado exitosamente");
            return "redirect:/admin/shifts";
            
        } catch (IllegalArgumentException e) {
            log.error("Validation error updating shift: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("allDays", Arrays.asList(DayOfWeek.values()));
            model.addAttribute("isEdit", true);
            return "admin/shifts/form";
            
        } catch (Exception e) {
            log.error("Error updating shift", e);
            model.addAttribute("errorMessage", "Error al actualizar el turno: " + e.getMessage());
            model.addAttribute("allDays", Arrays.asList(DayOfWeek.values()));
            model.addAttribute("isEdit", true);
            return "admin/shifts/form";
        }
    }

    /**
     * Delete a shift
     */
    @PostMapping("/{id}/delete")
    public String deleteShift(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Deleting shift with ID: {}", id);
        
        try {
            shiftService.deleteShift(id);
            log.info("Shift deleted successfully");
            
            redirectAttributes.addFlashAttribute("successMessage", "Turno eliminado exitosamente");
            
        } catch (IllegalStateException e) {
            log.error("Cannot delete shift: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            
        } catch (Exception e) {
            log.error("Error deleting shift", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al eliminar el turno");
        }
        
        return "redirect:/admin/shifts";
    }

    /**
     * Activate a shift
     */
    @PostMapping("/{id}/activate")
    public String activateShift(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Activating shift with ID: {}", id);
        
        try {
            shiftService.activateShift(id);
            redirectAttributes.addFlashAttribute("successMessage", "Turno activado exitosamente");
        } catch (Exception e) {
            log.error("Error activating shift", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al activar el turno");
        }
        
        return "redirect:/admin/shifts";
    }

    /**
     * Deactivate a shift
     */
    @PostMapping("/{id}/deactivate")
    public String deactivateShift(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Deactivating shift with ID: {}", id);
        
        try {
            shiftService.deactivateShift(id);
            redirectAttributes.addFlashAttribute("successMessage", "Turno desactivado exitosamente");
        } catch (Exception e) {
            log.error("Error deactivating shift", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al desactivar el turno");
        }
        
        return "redirect:/admin/shifts";
    }

    /**
     * Show shift details with assigned employees
     */
    @GetMapping("/{id}")
    public String viewShift(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Viewing shift details for ID: {}", id);
        
        return shiftService.getShiftById(id)
                .map(shift -> {
                    List<Employee> assignedEmployees = shiftService.getEmployeesByShift(id);
                    long employeeCount = shiftService.countEmployeesInShift(id);
                    
                    model.addAttribute("shift", shift);
                    model.addAttribute("assignedEmployees", assignedEmployees);
                    model.addAttribute("employeeCount", employeeCount);
                    
                    return "admin/shifts/detail";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Turno no encontrado");
                    return "redirect:/admin/shifts";
                });
    }

    /**
     * Show form to assign employees to shift
     */
    @GetMapping("/{id}/assign")
    public String assignEmployeesForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Displaying employee assignment form for shift ID: {}", id);
        
        return shiftService.getShiftById(id)
                .map(shift -> {
                    // Get all active employees
                    List<Employee> allEmployees = employeeService.findAll().stream()
                            .filter(Employee::getEnabled)
                            .collect(Collectors.toList());
                    
                    // Get already assigned employees
                    List<Employee> assignedEmployees = shiftService.getEmployeesByShift(id);
                    
                    // Get available employees (not yet assigned)
                    List<Employee> availableEmployees = allEmployees.stream()
                            .filter(e -> !assignedEmployees.contains(e))
                            .collect(Collectors.toList());
                    
                    model.addAttribute("shift", shift);
                    model.addAttribute("availableEmployees", availableEmployees);
                    model.addAttribute("assignedEmployees", assignedEmployees);
                    
                    return "admin/shifts/assign-employees";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Turno no encontrado");
                    return "redirect:/admin/shifts";
                });
    }

    /**
     * Assign employees to a shift
     */
    @PostMapping("/{id}/assign")
    public String assignEmployees(
            @PathVariable Long id,
            @RequestParam(value = "employeeIds", required = false) List<Long> employeeIds,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        log.info("Assigning employees to shift ID: {}", id);
        
        if (employeeIds == null || employeeIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Debe seleccionar al menos un empleado");
            return "redirect:/admin/shifts/" + id + "/assign";
        }
        
        try {
            // Get current employee performing the action
            String username = authentication.getName();
            Long actionById = employeeService.findByUsername(username)
                    .map(Employee::getIdEmpleado)
                    .orElse(null);
            
            shiftService.assignEmployeesToShift(id, employeeIds, actionById);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                    employeeIds.size() + " empleado(s) asignado(s) al turno exitosamente");
            return "redirect:/admin/shifts/" + id;
            
        } catch (Exception e) {
            log.error("Error assigning employees to shift", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Error al asignar empleados: " + e.getMessage());
            return "redirect:/admin/shifts/" + id + "/assign";
        }
    }

    /**
     * Remove employees from a shift
     */
    @PostMapping("/{id}/remove")
    public String removeEmployees(
            @PathVariable Long id,
            @RequestParam(value = "employeeIds", required = false) List<Long> employeeIds,
            @RequestParam(value = "reason", required = false) String reason,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        log.info("Removing employees from shift ID: {}", id);
        
        if (employeeIds == null || employeeIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Debe seleccionar al menos un empleado");
            return "redirect:/admin/shifts/" + id;
        }
        
        try {
            // Get current employee performing the action
            String username = authentication.getName();
            Long actionById = employeeService.findByUsername(username)
                    .map(Employee::getIdEmpleado)
                    .orElse(null);
            
            shiftService.removeEmployeesFromShift(id, employeeIds, actionById, reason);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                    employeeIds.size() + " empleado(s) removido(s) del turno exitosamente");
            return "redirect:/admin/shifts/" + id;
            
        } catch (Exception e) {
            log.error("Error removing employees from shift", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Error al remover empleados: " + e.getMessage());
            return "redirect:/admin/shifts/" + id;
        }
    }

    /**
     * Show shift assignment history
     */
    @GetMapping("/{id}/history")
    public String viewShiftHistory(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Viewing history for shift ID: {}", id);
        
        return shiftService.getShiftById(id)
                .map(shift -> {
                    var history = historyService.getHistoryByShift(id);
                    
                    // Count assignments and removals
                    long assignedCount = history.stream()
                            .filter(h -> h.getAction().name().equals("ASSIGNED"))
                            .count();
                    long removedCount = history.stream()
                            .filter(h -> h.getAction().name().equals("REMOVED"))
                            .count();
                    
                    model.addAttribute("shift", shift);
                    model.addAttribute("history", history);
                    model.addAttribute("assignedCount", assignedCount);
                    model.addAttribute("removedCount", removedCount);
                    
                    return "admin/shifts/history";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Turno no encontrado");
                    return "redirect:/admin/shifts";
                });
    }
}
