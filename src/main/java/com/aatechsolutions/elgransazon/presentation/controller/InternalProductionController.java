package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.application.service.ItemPreparationService;
import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemIngredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.ItemPreparation;
import com.aatechsolutions.elgransazon.domain.entity.PreparationStatus;
import com.aatechsolutions.elgransazon.presentation.dto.InternalItemForm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Internal production: dishes that are produced but never sold (buffet, staff meals).
 *
 * <p>Admin, manager and chef share the module because all three must be able to adjust both the
 * dish and its recipe. The chef has no access to {@code /admin/**}, which is why the route lives
 * outside it.</p>
 */
@Controller
@RequestMapping("/internal-production")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CHEF')")
public class InternalProductionController {

    private final ItemPreparationService preparationService;
    private final ItemMenuService itemMenuService;
    private final IngredientService ingredientService;
    private final CategoryService categoryService;
    private final EmployeeService employeeService;

    // ========== Listing ==========

    /**
     * Los dos filtros de la pantalla son independientes: {@code itemQuery} busca entre los
     * platillos internos y {@code historyQuery}/{@code historyStatus} entre las preparaciones.
     * Cada formulario arrastra el otro en un campo oculto, así que aplicar uno no borra el otro.
     */
    @GetMapping
    public String list(@RequestParam(required = false) String itemQuery,
                       @RequestParam(required = false) String historyQuery,
                       @RequestParam(required = false) String historyStatus,
                       Model model) {
        List<ItemMenu> allItems = preparationService.findInternalItems();
        List<ItemMenu> items = filterItems(allItems, itemQuery);
        List<ItemPreparation> today = preparationService.findTodayPreparations();

        String historyTerm = blankToNull(historyQuery);
        PreparationStatus status = parseStatus(historyStatus);
        List<ItemPreparation> recent = preparationService.searchPreparations(historyTerm, status, 40);

        // How many portions the current stock allows, per item. Shown next to "Registrar producción".
        Map<Long, Integer> maxPortions = new HashMap<>();
        for (ItemMenu item : items) {
            maxPortions.put(item.getIdItemMenu(), itemMenuService.getMaxAvailableQuantity(item.getIdItemMenu()));
        }

        model.addAttribute("items", items);
        model.addAttribute("allItemsCount", allItems.size());
        model.addAttribute("itemQuery", itemQuery == null ? "" : itemQuery.trim());
        model.addAttribute("historyQuery", historyTerm == null ? "" : historyTerm);
        model.addAttribute("historyStatus", status == null ? "" : status.name());
        model.addAttribute("historyFiltersActive", historyTerm != null || status != null);
        model.addAttribute("todayPreparations", today);
        model.addAttribute("recentPreparations", recent);
        model.addAttribute("todayQuantity", preparationService.totalProducedQuantity(today));

        // El platillo que más porciones movió hoy: es lo que se consulta al planear el buffet.
        // Cuenta solo los lotes vigentes; los cancelados no se prepararon.
        Map<String, Integer> portionsByItem = new LinkedHashMap<>();
        for (ItemPreparation batch : today) {
            if (batch.isPrepared()) {
                portionsByItem.merge(batch.getItemName(),
                        batch.getQuantity() == null ? 0 : batch.getQuantity(), Integer::sum);
            }
        }
        String topItemName = null;
        int topItemPortions = 0;
        for (Map.Entry<String, Integer> entry : portionsByItem.entrySet()) {
            if (entry.getValue() > topItemPortions) {
                topItemName = entry.getKey();
                topItemPortions = entry.getValue();
            }
        }
        model.addAttribute("topItemName", topItemName);
        model.addAttribute("topItemPortions", topItemPortions);
        model.addAttribute("maxPortions", maxPortions);
        // Producción interna ya no es un módulo del sidebar: cuelga del Menú, así que el
        // elemento resaltado del sidebar sigue siendo "Menú" mientras se está aquí.
        model.addAttribute("activeMenu", "menu");
        return "internal-production/list";
    }

    // ========== Listing filters ==========

    /**
     * Search by name, description or category. Accents and case are ignored so "camaron"
     * finds "Camarones a la diabla".
     */
    private List<ItemMenu> filterItems(List<ItemMenu> items, String query) {
        String term = blankToNull(query);
        if (term == null) {
            return items;
        }
        String needle = fold(term);
        return items.stream()
                .filter(item -> fold(searchableText(item)).contains(needle))
                .toList();
    }

    private String searchableText(ItemMenu item) {
        StringBuilder text = new StringBuilder(item.getName() == null ? "" : item.getName());
        if (item.getDescription() != null) {
            text.append(' ').append(item.getDescription());
        }
        if (item.getCategory() != null && item.getCategory().getName() != null) {
            text.append(' ').append(item.getCategory().getName());
        }
        return text.toString();
    }

    /** Lowercase without accents, so the search is forgiving with how people type. */
    private static String fold(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static PreparationStatus parseStatus(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return PreparationStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // Un enlace viejo con un estado desconocido no debe romper el listado: se ignora.
            return null;
        }
    }

    // ========== Item form ==========

    @GetMapping("/new")
    public String newForm(Model model) {
        InternalItemForm form = new InternalItemForm();
        form.getRecipe().add(new InternalItemForm.RecipeRow());
        return renderForm(model, form, false);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<ItemMenu> item = preparationService.findInternalItem(id);
        if (item.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "El platillo de producción interna no existe o no pertenece a esta empresa.");
            return "redirect:/internal-production";
        }

        InternalItemForm form = toForm(item.get());
        return renderForm(model, form, true);
    }

    @PostMapping
    public String create(@ModelAttribute("form") InternalItemForm form,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            ItemMenu created = preparationService.createInternalItem(toItem(form), toRecipe(form));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Platillo interno '" + created.getName() + "' creado. Ya se descuenta su receta al registrar producción.");
            return "redirect:/internal-production";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return renderForm(model, form, false);
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("form") InternalItemForm form,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            ItemMenu updated = preparationService.updateInternalItem(id, toItem(form), toRecipe(form));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Platillo interno '" + updated.getName() + "' actualizado.");
            return "redirect:/internal-production";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            form.setIdItemMenu(id);
            return renderForm(model, form, true);
        }
    }

    // ========== Production batches ==========

    @PostMapping("/{id}/prepare")
    public String registerPreparation(@PathVariable Long id,
                                      @RequestParam(value = "quantity", required = false) Integer quantity,
                                      Authentication authentication,
                                      RedirectAttributes redirectAttributes) {
        int portions = quantity != null ? quantity : 1;
        try {
            ItemPreparation preparation = preparationService.registerPreparation(
                    id, portions, currentEmployee(authentication));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Producción registrada: " + preparation.getQuantity() + " de '"
                            + preparation.getItemName() + "'. Se descontaron sus ingredientes.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Could not register internal production for item {}: {}", id, ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/internal-production";
    }

    @PostMapping("/preparations/{id}/cancel")
    public String cancelPreparation(@PathVariable Long id,
                                    @RequestParam(value = "reason", required = false) String reason,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        try {
            ItemPreparation cancelled = preparationService.cancelPreparation(
                    id, currentEmployee(authentication), reason);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Preparación de " + cancelled.getQuantity() + " de '"
                            + cancelled.getItemName() + "' cancelada. Sus ingredientes volvieron al inventario.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Could not cancel internal production batch {}: {}", id, ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/internal-production";
    }

    // ========== Helpers ==========

    private String renderForm(Model model, InternalItemForm form, boolean edit) {
        model.addAttribute("form", form);
        model.addAttribute("categories", categoryService.getAllActiveCategories());
        model.addAttribute("ingredients", ingredientService.findAll());
        model.addAttribute("isEdit", edit);
        model.addAttribute("formAction", edit
                ? "/internal-production/" + form.getIdItemMenu()
                : "/internal-production");
        model.addAttribute("activeMenu", "menu");
        return "internal-production/form";
    }

    private InternalItemForm toForm(ItemMenu item) {
        InternalItemForm form = new InternalItemForm();
        form.setIdItemMenu(item.getIdItemMenu());
        form.setName(item.getName());
        form.setDescription(item.getDescription());
        form.setCategoryId(item.getCategory() != null ? item.getCategory().getIdCategory() : null);
        for (ItemIngredient line : itemMenuService.getRecipe(item.getIdItemMenu())) {
            form.getRecipe().add(new InternalItemForm.RecipeRow(
                    line.getIngredient().getIdIngredient(), line.getQuantity()));
        }
        if (form.getRecipe().isEmpty()) {
            form.getRecipe().add(new InternalItemForm.RecipeRow());
        }
        return form;
    }

    /**
     * Build the item from the form. Price, flags and internal-only marker are forced by the
     * service; the form only carries name, description and category.
     */
    private ItemMenu toItem(InternalItemForm form) {
        String name = form.getName() != null ? form.getName().trim() : "";
        if (name.length() < 2) {
            throw new IllegalArgumentException("El nombre del platillo debe tener al menos 2 caracteres");
        }
        if (name.length() > 200) {
            throw new IllegalArgumentException("El nombre del platillo no puede exceder 200 caracteres");
        }
        if (form.getCategoryId() == null) {
            throw new IllegalArgumentException("Selecciona una categoría");
        }
        Category category = categoryService.getCategoryById(form.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("La categoría seleccionada no existe"));

        return ItemMenu.builder()
                .name(name)
                .description(form.getDescription())
                .category(category)
                .price(BigDecimal.ZERO)
                .active(true)
                .internalOnly(true)
                .build();
    }

    /**
     * Turn the form rows into recipe lines. The unit always comes from the ingredient (the
     * inventory owns it), so the form never sends a unit that could contradict the stock.
     */
    private List<ItemIngredient> toRecipe(InternalItemForm form) {
        List<ItemIngredient> recipe = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        for (InternalItemForm.RecipeRow row : form.getRecipe()) {
            if (row == null || row.getIngredientId() == null) {
                continue;
            }
            if (!seen.add(row.getIngredientId())) {
                throw new IllegalArgumentException("El mismo ingrediente está repetido en la receta");
            }

            Ingredient ingredient = ingredientService.findById(row.getIngredientId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Ingrediente no encontrado: " + row.getIngredientId()));

            BigDecimal quantity = row.getQuantity();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "La cantidad de '" + ingredient.getName() + "' debe ser mayor a 0");
            }

            recipe.add(ItemIngredient.builder()
                    .ingredient(ingredient)
                    .quantity(quantity.setScale(2, RoundingMode.HALF_UP))
                    .unit(ingredient.getUnitOfMeasure())
                    .build());
        }

        if (recipe.isEmpty()) {
            throw new IllegalArgumentException("Debe agregar al menos un ingrediente a la receta");
        }
        return recipe;
    }

    private Employee currentEmployee(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return employeeService.findByUsername(authentication.getName()).orElse(null);
    }
}
