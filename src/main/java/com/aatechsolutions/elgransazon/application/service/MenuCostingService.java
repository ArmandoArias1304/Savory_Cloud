package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.application.dto.MenuItemCost;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemIngredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenuComboItem;
import com.aatechsolutions.elgransazon.domain.repository.ItemIngredientRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComboItemRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Costeo de los platillos a partir de su receta y del costo promedio de cada ingrediente.
 *
 * <p>Se cargan los platillos de una sola página y las recetas/combos necesarios para esos
 * platillos, evitando cargar toda la carta cuando se consulta una página.</p>
 *
 * <p>El costo de cada ingrediente sale de {@link Ingredient#getCostPerRecipeUnit()}: ya considera
 * el rendimiento. Los combos suman sus platillos hijos (con
 * tope de anidamiento), y lo que <b>no</b> suma este cálculo, a propósito: complementos (se cobran
 * aparte y los elige el cliente), merma operativa y mano de obra.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MenuCostingService {

    /** Los combos anidados más allá de este nivel no se costean: evita ciclos y costos absurdos. */
    private static final int MAX_COMBO_DEPTH = 3;

    private final ItemMenuRepository itemMenuRepository;
    private final ItemIngredientRepository itemIngredientRepository;
    private final ItemMenuComboItemRepository itemMenuComboItemRepository;

    @Transactional(readOnly = true)
    public Page<MenuItemCost> calculateForCurrentCompany(Pageable pageable,
                                                          String categoryName,
                                                          String name) {
        Company company = CompanyContext.requireCurrentCompany();
        Page<ItemMenu> itemPage = itemMenuRepository.findActiveForCosting(
                company, blankToNull(categoryName), blankToNull(name), pageable);
        List<ItemMenu> items = itemPage.getContent();
        if (items.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, itemPage.getTotalElements());
        }

        List<MenuItemCost> rows = calculateItems(company, items);
        log.debug("Costeo calculado para página {} de {} platillos de la empresa {}",
                itemPage.getNumber() + 1, rows.size(), company.getIdCompany());
        return new PageImpl<>(rows, pageable, itemPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Optional<MenuItemCost> calculateDishForCurrentCompany(Long itemId) {
        Company company = CompanyContext.requireCurrentCompany();
        return itemMenuRepository.findActiveCostingItemById(company, itemId)
                .map(item -> calculateItems(company, List.of(item)).get(0));
    }

    private List<MenuItemCost> calculateItems(Company company, List<ItemMenu> items) {
        Set<Long> recipeItemIds = items.stream()
                .map(ItemMenu::getIdItemMenu)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> comboIdsToLoad = items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsCombo()))
                .map(ItemMenu::getIdItemMenu)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, List<ItemMenuComboItem>> combos = new LinkedHashMap<>();
        for (int depth = 0; depth < MAX_COMBO_DEPTH && !comboIdsToLoad.isEmpty(); depth++) {
            List<ItemMenuComboItem> loadedCombos =
                    itemMenuComboItemRepository.findByComboMenuIds(company, comboIdsToLoad);
            Set<Long> nestedComboIds = new LinkedHashSet<>();
            for (ItemMenuComboItem comboItem : loadedCombos) {
                if (comboItem.getComboMenu() == null) {
                    continue;
                }
                combos.computeIfAbsent(comboItem.getComboMenu().getIdItemMenu(), ignored -> new ArrayList<>())
                        .add(comboItem);
                ItemMenu child = comboItem.getChildMenu();
                if (child != null) {
                    recipeItemIds.add(child.getIdItemMenu());
                    if (Boolean.TRUE.equals(child.getIsCombo())) {
                        nestedComboIds.add(child.getIdItemMenu());
                    }
                }
            }
            comboIdsToLoad = nestedComboIds;
        }

        Map<Long, List<ItemIngredient>> recipes = itemIngredientRepository.findByMenuItemIds(company, recipeItemIds)
                .stream()
                .filter(line -> line.getItemMenu() != null)
                .collect(Collectors.groupingBy(
                        line -> line.getItemMenu().getIdItemMenu(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        Set<Long> parentIds = items.stream()
                .map(item -> item.getParentItem() == null
                        ? item.getIdItemMenu()
                        : item.getParentItem().getIdItemMenu())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ItemMenu> variantItemsById = new LinkedHashMap<>();
        items.forEach(item -> variantItemsById.put(item.getIdItemMenu(), item));
        itemMenuRepository.findActiveCostingVariants(company, List.copyOf(parentIds))
                .forEach(item -> variantItemsById.putIfAbsent(item.getIdItemMenu(), item));
        items.stream()
                .map(ItemMenu::getParentItem)
                .filter(parent -> parent != null && Boolean.TRUE.equals(parent.getActive())
                        && !Boolean.TRUE.equals(parent.getDeleted())
                        && !Boolean.TRUE.equals(parent.getInternalOnly()))
                .forEach(parent -> variantItemsById.putIfAbsent(parent.getIdItemMenu(), parent));

        List<MenuItemCost> rows = items.stream()
                .map(item -> build(item, new ArrayList<>(variantItemsById.values()), recipes, combos))
                .toList();
        return rows;
    }

    @Transactional(readOnly = true)
    public List<String> findCategoriesForCurrentCompany() {
        return itemMenuRepository.findActiveCostingCategories(CompanyContext.requireCurrentCompany());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private MenuItemCost build(ItemMenu item,
                               List<ItemMenu> activeItems,
                               Map<Long, List<ItemIngredient>> recipes,
                               Map<Long, List<ItemMenuComboItem>> combos) {
        boolean combo = Boolean.TRUE.equals(item.getIsCombo());
        List<MenuItemCost.Line> lines = new ArrayList<>();
        BigDecimal cost = BigDecimal.ZERO;
        int ingredientsWithoutCost = 0;

        if (combo) {
            for (ItemMenuComboItem child : combos.getOrDefault(item.getIdItemMenu(), List.of())) {
                ItemMenu childItem = child.getChildMenu();
                ChildCost childCost = costOfChild(childItem, recipes, combos, 1);
                int quantity = child.getQuantity() != null && child.getQuantity() > 0
                        ? child.getQuantity()
                        : 1;
                BigDecimal subtotal = childCost.cost()
                        .multiply(BigDecimal.valueOf(quantity))
                        .setScale(2, RoundingMode.HALF_UP);
                cost = cost.add(subtotal);
                ingredientsWithoutCost += childCost.missing();

                lines.add(MenuItemCost.Line.builder()
                        .ingredientName(childItem != null ? childItem.getName() : "Platillo eliminado")
                        .quantity(BigDecimal.valueOf(quantity))
                        .unit("pza")
                        .unitCost(childCost.cost().setScale(2, RoundingMode.HALF_UP))
                        .subtotal(subtotal)
                        .missingCost(!childCost.hasRecipe() || childCost.missing() > 0)
                        .build());
            }
        } else {
            for (ItemIngredient line : recipes.getOrDefault(item.getIdItemMenu(), List.of())) {
                Ingredient ingredient = line.getIngredient();
                BigDecimal quantity = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ZERO;
                BigDecimal unitCost = ingredient != null
                        ? ingredient.getCostPerRecipeUnit()
                        : BigDecimal.ZERO;

                // Un costo en cero no es un ingrediente gratis: es un ingrediente sin costo capturado.
                boolean missingCost = unitCost.compareTo(BigDecimal.ZERO) <= 0;
                if (missingCost) {
                    ingredientsWithoutCost++;
                }

                BigDecimal subtotal = quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
                cost = cost.add(subtotal);

                lines.add(MenuItemCost.Line.builder()
                        .ingredientName(ingredient != null ? ingredient.getName() : "Ingrediente eliminado")
                        .quantity(quantity)
                        .unit(line.getUnit())
                        .unitCost(unitCost)
                        .subtotal(subtotal)
                        .yieldPercentage(ingredient != null ? ingredient.getYieldPercentage() : null)
                        .missingCost(missingCost)
                        .build());
            }
        }

        cost = cost.setScale(2, RoundingMode.HALF_UP);
        BigDecimal price = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
        BigDecimal profit = price.subtract(cost);
        BigDecimal margin = price.compareTo(BigDecimal.ZERO) > 0
                ? profit.multiply(BigDecimal.valueOf(100)).divide(price, 1, RoundingMode.HALF_UP)
                : null;

        // El desglose se ordena por aporte al costo: lo que más pesa aparece primero.
        List<MenuItemCost.Line> orderedLines = lines.stream()
                .sorted(Comparator.comparing(MenuItemCost.Line::getSubtotal).reversed()
                        .thenComparing(MenuItemCost.Line::getIngredientName))
                .toList();

        return MenuItemCost.builder()
                .idItemMenu(item.getIdItemMenu())
                .name(item.getName())
                .categoryName(item.getCategory() != null ? item.getCategory().getName() : "Sin categoría")
                .imageUrl(item.getImageUrl())
                .sizeName(item.getSizeName())
                .parentName(item.getParentItem() != null ? item.getParentItem().getName() : null)
                .parentItemId(item.getParentItem() != null ? item.getParentItem().getIdItemMenu() : null)
                .relatedVariants(relatedVariants(item, activeItems))
                .price(price)
                .cost(cost)
                .profit(profit)
                .marginPercentage(margin)
                .combo(combo)
                .hasRecipe(!lines.isEmpty())
                .ingredientsWithoutCost(ingredientsWithoutCost)
                .lines(orderedLines)
                .build();
    }

    private List<MenuItemCost.Variant> relatedVariants(ItemMenu item, List<ItemMenu> activeItems) {
        Long parentId = item.getParentItem() != null
                ? item.getParentItem().getIdItemMenu()
                : item.getIdItemMenu();

        return activeItems.stream()
                .filter(candidate -> {
                    if (item.getParentItem() == null) {
                        return candidate.getParentItem() != null
                                && parentId.equals(candidate.getParentItem().getIdItemMenu());
                    }
                    return parentId.equals(candidate.getIdItemMenu())
                            || (candidate.getParentItem() != null
                            && parentId.equals(candidate.getParentItem().getIdItemMenu()));
                })
                .map(candidate -> MenuItemCost.Variant.builder()
                        .idItemMenu(candidate.getIdItemMenu())
                        .name(candidate.getName())
                        .sizeName(candidate.getSizeName())
                        .price(candidate.getPrice())
                        .selected(candidate.getIdItemMenu().equals(item.getIdItemMenu()))
                        .build())
                .toList();
    }

    /**
     * Costo de un platillo que forma parte de un combo. Un combo anidado suma a sus propios hijos;
     * un platillo normal suma su receta. {@code missing} cuenta lo que no se pudo valuar y
     * {@code hasRecipe} dice si había algo que sumar.
     */
    private ChildCost costOfChild(ItemMenu item,
                                  Map<Long, List<ItemIngredient>> recipes,
                                  Map<Long, List<ItemMenuComboItem>> combos,
                                  int depth) {
        if (item == null) {
            return new ChildCost(BigDecimal.ZERO, 1, false);
        }

        if (Boolean.TRUE.equals(item.getIsCombo())) {
            List<ItemMenuComboItem> children = combos.getOrDefault(item.getIdItemMenu(), List.of());
            if (depth >= MAX_COMBO_DEPTH || children.isEmpty()) {
                return new ChildCost(BigDecimal.ZERO, 1, false);
            }

            BigDecimal total = BigDecimal.ZERO;
            int missing = 0;
            boolean complete = true;
            for (ItemMenuComboItem child : children) {
                ChildCost childCost = costOfChild(child.getChildMenu(), recipes, combos, depth + 1);
                int quantity = child.getQuantity() != null && child.getQuantity() > 0
                        ? child.getQuantity()
                        : 1;
                total = total.add(childCost.cost().multiply(BigDecimal.valueOf(quantity)));
                missing += childCost.missing();
                complete = complete && childCost.hasRecipe() && childCost.missing() == 0;
            }
            return new ChildCost(total, missing, complete);
        }

        List<ItemIngredient> lines = recipes.getOrDefault(item.getIdItemMenu(), List.of());
        if (lines.isEmpty()) {
            return new ChildCost(BigDecimal.ZERO, 1, false);
        }

        BigDecimal total = BigDecimal.ZERO;
        int missing = 0;
        for (ItemIngredient line : lines) {
            Ingredient ingredient = line.getIngredient();
            BigDecimal quantity = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ZERO;
            BigDecimal unitCost = ingredient != null ? ingredient.getCostPerRecipeUnit() : BigDecimal.ZERO;
            if (unitCost.compareTo(BigDecimal.ZERO) <= 0) {
                missing++;
            }
            total = total.add(quantity.multiply(unitCost));
        }
        return new ChildCost(total, missing, true);
    }

    private record ChildCost(BigDecimal cost, int missing, boolean hasRecipe) {
    }
}
