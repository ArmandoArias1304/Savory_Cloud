package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.application.service.IngredientWasteService;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.IngredientWasteReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

@Controller
@RequestMapping("/admin/inventory/waste")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class IngredientWasteController {

    private final IngredientService ingredientService;
    private final IngredientWasteService ingredientWasteService;
    private final EmployeeService employeeService;

    @GetMapping
    public String showWastePage(Model model) {
        model.addAttribute("ingredients", ingredientService.findAllActive());
        model.addAttribute("wasteRecords", ingredientWasteService.findRecent(100));
        model.addAttribute("wasteReasons", Arrays.asList(IngredientWasteReason.values()));
        model.addAttribute("today", LocalDate.now());
        return "admin/inventory/waste";
    }

    @PostMapping
    public String recordWaste(@RequestParam Long ingredientId,
                              @RequestParam BigDecimal quantity,
                              @RequestParam IngredientWasteReason reason,
                              @RequestParam LocalDate occurredOn,
                              @RequestParam(required = false) String note,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            Employee employee = employeeService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("No se encontró el empleado actual."));
            ingredientWasteService.recordWaste(
                    ingredientId, quantity, reason, occurredOn, note, employee);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Merma registrada. El inventario se actualizó y el costo quedó guardado.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("No se pudo registrar la merma del ingrediente {}.", ingredientId, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ocurrió un error al guardar la merma. Inténtalo de nuevo.");
        }
        return "redirect:/admin/inventory/waste";
    }
}
