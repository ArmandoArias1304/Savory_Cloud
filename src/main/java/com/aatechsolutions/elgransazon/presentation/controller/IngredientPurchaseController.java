package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.IngredientPurchaseService;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.IngredientPurchase;
import com.aatechsolutions.elgransazon.domain.entity.IngredientPurchaseStatus;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/admin/inventory/purchases")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class IngredientPurchaseController {

    private static final long MAX_XML_SIZE = 3L * 1024 * 1024;

    private final IngredientPurchaseService purchaseService;
    private final IngredientService ingredientService;
    private final EmployeeService employeeService;

    @GetMapping
    public String list(Model model) {
        List<IngredientPurchase> purchases = purchaseService.findAll();
        model.addAttribute("purchases", purchases);
        model.addAttribute("totalPurchaseCount", purchases.size());
        model.addAttribute("pendingPurchaseCount", purchases.stream()
                .filter(purchase -> purchase.getStatus() == IngredientPurchaseStatus.PENDING).count());
        model.addAttribute("confirmedPurchaseCount", purchases.stream()
                .filter(purchase -> purchase.getStatus() == IngredientPurchaseStatus.CONFIRMED).count());
        model.addAttribute("cancelledPurchaseCount", purchases.stream()
                .filter(purchase -> purchase.getStatus() == IngredientPurchaseStatus.CANCELLED).count());
        return "admin/inventory/purchases";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("xmlFile") MultipartFile file,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Selecciona un archivo XML para continuar");
            return "redirect:/admin/inventory/purchases";
        }
        if (file.getSize() > MAX_XML_SIZE) {
            redirectAttributes.addFlashAttribute("errorMessage", "El XML supera el límite de 3 MB");
            return "redirect:/admin/inventory/purchases";
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) {
            redirectAttributes.addFlashAttribute("errorMessage", "El archivo debe tener extensión .xml");
            return "redirect:/admin/inventory/purchases";
        }

        try {
            Employee employee = employeeService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            IngredientPurchase purchase = purchaseService.createDraft(file.getBytes(), employee);
            redirectAttributes.addFlashAttribute("successMessage",
                    "XML leído. Revisa los conceptos y relaciónalos con ingredientes.");
            return "redirect:/admin/inventory/purchases/" + purchase.getId();
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            log.warn("Could not import ingredient purchase XML: {}", exception.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/admin/inventory/purchases";
        } catch (Exception exception) {
            log.error("Could not import ingredient purchase XML", exception);
            redirectAttributes.addFlashAttribute("errorMessage", "No se pudo procesar la factura XML");
            return "redirect:/admin/inventory/purchases";
        }
    }

    @GetMapping("/{purchaseId}")
    public String review(@PathVariable Long purchaseId, Model model) {
        IngredientPurchase purchase = purchaseService.findPending(purchaseId);
        List<Ingredient> ingredients = ingredientService.findAllActive();
        model.addAttribute("purchase", purchase);
        model.addAttribute("ingredients", ingredients);
        return "admin/inventory/purchase-review";
    }

    @GetMapping("/{purchaseId}/details")
    public String details(@PathVariable Long purchaseId, Model model) {
        model.addAttribute("purchase", purchaseService.findDetails(purchaseId));
        return "admin/inventory/purchase-details";
    }

    @PostMapping("/{purchaseId}/confirm")
    public String confirm(@PathVariable Long purchaseId,
            @RequestParam("lineIds") List<Long> lineIds,
            @RequestParam("ingredientIds") List<Long> ingredientIds,
            @RequestParam("conversionFactors") List<@NotNull BigDecimal> conversionFactors,
            @RequestParam(defaultValue = "false") boolean includeVat,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            Employee employee = employeeService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            purchaseService.confirm(purchaseId, lineIds, ingredientIds, conversionFactors, includeVat, employee);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Compra confirmada y stock actualizado correctamente");
        } catch (Exception exception) {
            log.warn("Could not confirm ingredient purchase {}: {}", purchaseId, exception.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/admin/inventory/purchases/" + purchaseId;
        }
        return "redirect:/admin/inventory/purchases";
    }

    @PostMapping("/{purchaseId}/cancel")
    public String cancel(@PathVariable Long purchaseId,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            Employee employee = employeeService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            purchaseService.cancel(purchaseId, employee);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Factura cancelada; sus entradas de stock fueron anuladas.");
        } catch (Exception exception) {
            log.warn("Could not cancel ingredient purchase {}: {}", purchaseId, exception.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/admin/inventory/purchases/" + purchaseId + "/details";
    }
}