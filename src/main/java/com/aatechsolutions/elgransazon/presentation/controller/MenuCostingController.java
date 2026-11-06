package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.dto.MenuItemCost;
import com.aatechsolutions.elgransazon.application.service.MenuCostingService;
import com.aatechsolutions.elgransazon.application.service.CloudflareImagesUrlHelper;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pantalla de costeo por platillo (solo ADMIN y GERENTE).
 *
 * <p>Muestra el costo de la receta con el costo promedio de cada ingrediente, la utilidad y el
 * margen por platillo, con el desglose ingrediente por ingrediente.</p>
 */
@Controller
@RequestMapping("/admin/menu-items/costing")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class MenuCostingController {

    private static final int PAGE_SIZE = 20;

    private final MenuCostingService menuCostingService;
    private final CloudflareImagesUrlHelper cloudflareImagesUrlHelper;
    private final SystemConfigurationService systemConfigurationService;

    @GetMapping
    public String costing(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(required = false) String category,
                          @RequestParam(required = false) String name,
                          @RequestParam(required = false) Long dishId,
                          @RequestParam(defaultValue = "all") String view,
                          @RequestParam(defaultValue = "con") String ivaMode,
                          Model model) {
        BigDecimal taxRate = systemConfigurationService.getTaxRate();
        String selectedIvaMode = "sin".equals(ivaMode) ? "sin" : "con";
        String selectedView = List.of("all", "category", "dish").contains(view) ? view : "all";
        int requestedPage = Math.max(page, 0);
        String categoryFilter = "category".equals(selectedView) && category != null && !category.isBlank()
                ? category.trim() : null;
        String nameFilter = "dish".equals(selectedView) && name != null && !name.isBlank()
                ? name.trim() : null;
        Page<MenuItemCost> costingPage = menuCostingService.calculateForCurrentCompany(
                PageRequest.of(requestedPage, PAGE_SIZE), categoryFilter, nameFilter);
        if (costingPage.getTotalPages() > 0 && requestedPage >= costingPage.getTotalPages()) {
            requestedPage = costingPage.getTotalPages() - 1;
            costingPage = menuCostingService.calculateForCurrentCompany(
                    PageRequest.of(requestedPage, PAGE_SIZE), categoryFilter, nameFilter);
        }
        List<MenuItemCost> rows = costingPage.getContent();
        List<MenuItemCost> detailRows = new ArrayList<>(rows);
        if (dishId != null && detailRows.stream().noneMatch(row -> dishId.equals(row.getIdItemMenu()))) {
            menuCostingService.calculateDishForCurrentCompany(dishId)
                    .ifPresent(detailRows::add);
        }

        long incomplete = rows.stream().filter(MenuItemCost::isIncomplete).count();
        long red = rows.stream().filter(row -> "red".equals(row.getMarginBandBeforeTax(taxRate))).count();

        List<BigDecimal> margins = rows.stream()
                .filter(row -> !row.isIncomplete())
                .map(row -> row.getMarginBeforeTax(taxRate))
                .filter(Objects::nonNull)
                .toList();
        BigDecimal averageMargin = margins.isEmpty()
                ? null
                : margins.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(margins.size()), 1, RoundingMode.HALF_UP);

        model.addAttribute("rows", rows);
        model.addAttribute("detailRows", detailRows);
        model.addAttribute("selectedDishId", dishId == null ? "" : dishId.toString());
        model.addAttribute("categories", menuCostingService.findCategoriesForCurrentCompany());
        model.addAttribute("totalItems", costingPage.getTotalElements());
        model.addAttribute("incompleteCount", incomplete);
        model.addAttribute("redCount", red);
        model.addAttribute("averageMargin", averageMargin);
        model.addAttribute("taxRate", taxRate);
        model.addAttribute("ivaMode", selectedIvaMode);
        model.addAttribute("currentPage", requestedPage);
        model.addAttribute("totalPages", costingPage.getTotalPages());
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("pageStart", costingPage.getTotalElements() == 0 ? 0 : (long) requestedPage * PAGE_SIZE + 1);
        model.addAttribute("pageEnd", Math.min((long) (requestedPage + 1) * PAGE_SIZE, costingPage.getTotalElements()));
        model.addAttribute("categoryFilter", categoryFilter == null ? "" : categoryFilter);
        model.addAttribute("nameFilter", nameFilter == null ? "" : nameFilter);
        model.addAttribute("viewMode", selectedView);

        log.debug("Pantalla de costeo: {} platillos, {} incompletos, margen promedio {}",
                costingPage.getTotalElements(), incomplete, averageMargin);
        return "admin/menu-items/costing";
    }

    @GetMapping("/search")
    @ResponseBody
    public CostingSearchResponse search(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(required = false) String name) {
        int requestedPage = Math.max(page, 0);
        String nameFilter = name == null || name.isBlank() ? null : name.trim();
        Page<MenuItemCost> results = menuCostingService.calculateForCurrentCompany(
                PageRequest.of(requestedPage, PAGE_SIZE), null, nameFilter);
        if (results.getTotalPages() > 0 && requestedPage >= results.getTotalPages()) {
            requestedPage = results.getTotalPages() - 1;
            results = menuCostingService.calculateForCurrentCompany(
                    PageRequest.of(requestedPage, PAGE_SIZE), null, nameFilter);
        }

        List<CostingSearchItem> items = results.getContent().stream()
                .map(row -> new CostingSearchItem(
                        row.getIdItemMenu(),
                        row.getName(),
                        row.getCategoryName(),
                        cloudflareImagesUrlHelper.thumb(row.getImageUrl()),
                        row.getPrice(),
                        row.getCost(),
                        row.isIncomplete(),
                        row.isHasRecipe()))
                .toList();
        return new CostingSearchResponse(
                items,
                requestedPage,
                results.getTotalPages(),
                results.getTotalElements());
    }

    public record CostingSearchResponse(
            List<CostingSearchItem> items,
            int currentPage,
            int totalPages,
            long totalItems) {
    }

    public record CostingSearchItem(
            Long id,
            String name,
            String category,
            String imageUrl,
            BigDecimal price,
            BigDecimal cost,
            boolean incomplete,
            boolean hasRecipe) {
    }
}
