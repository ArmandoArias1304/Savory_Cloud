package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.application.dto.MenuItemCost;
import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemIngredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenuComboItem;
import com.aatechsolutions.elgransazon.domain.repository.ItemIngredientRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComboItemRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Costeo por platillo.
 *
 * <p>Lo que se fija aquí: el costo es la suma de la receta valuada con el costo por unidad de
 * receta de cada ingrediente, el margen es sobre el precio de venta, y un platillo cuyo costo no
 * está completo nunca se pinta como saludable. Además, que un ingrediente comprado en kg y
 * recetado en gramos con rendimiento del 80 % cueste lo que realmente cuesta, y que un combo sume
 * sus hijos en vez de aparecer en cero.</p>
 */
class MenuCostingServiceTest {

    private ItemMenuRepository menuRepository;
    private ItemIngredientRepository recipeRepository;
    private ItemMenuComboItemRepository comboRepository;
    private MenuCostingService service;

    private Company company;
    private Category category;

    @BeforeEach
    void setUp() {
        menuRepository = mock(ItemMenuRepository.class);
        recipeRepository = mock(ItemIngredientRepository.class);
        comboRepository = mock(ItemMenuComboItemRepository.class);
        service = new MenuCostingService(menuRepository, recipeRepository, comboRepository);

        company = Company.builder().idCompany(1L).slug("ambar").name("Ambar").build();
        CompanyContext.setCurrentCompany(company);

        category = Category.builder().idCategory(3L).name("Platos fuertes").build();
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("suma la receta, resta el costo del precio y calcula el margen")
    void sumsTheRecipeAndComputesTheMargin() {
        ItemMenu burger = item(10L, "Hamburguesa", "100.00");
        Ingredient beef = ingredient(1L, "Carne", "40.00");
        Ingredient bread = ingredient(2L, "Pan", "5.00");

        stub(burger, line(burger, beef, "0.50", "KG"), line(burger, bread, "2.00", "UN"));

        MenuItemCost row = only(singleRow());

        assertThat(row.getCost()).isEqualByComparingTo("30.00");
        assertThat(row.getProfit()).isEqualByComparingTo("70.00");
        assertThat(row.getMarginPercentage()).isEqualByComparingTo("70.0");
        assertThat(row.getFoodCostPercentage()).isEqualByComparingTo("30.0");
        assertThat(row.getSuggestedPriceAtMargin(new BigDecimal("60"))).isEqualByComparingTo("75.00");
        assertThat(row.getEstimatedProfitAtMargin(new BigDecimal("60"))).isEqualByComparingTo("45.00");
        assertThat(row.getSuggestedPriceAtMargin(new BigDecimal("50"))).isEqualByComparingTo("60.00");
        assertThat(row.getMarginBand()).isEqualTo("green");
        assertThat(row.isIncomplete()).isFalse();
        assertThat(row.getLines()).hasSize(2);
        // El desglose va de mayor a menor aporte: la carne (20) antes que el pan (10).
        assertThat(row.getLines().get(0).getIngredientName()).isEqualTo("Carne");
        assertThat(row.getLines().get(0).getSubtotal()).isEqualByComparingTo("20.00");
        assertThat(row.getLines().get(0).getYieldPercentage()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("el IVA no se cuenta como utilidad y el precio se puede mostrar con o sin impuesto")
    void excludesTaxFromProfitAndMargin() {
        MenuItemCost row = MenuItemCost.builder()
                .price(new BigDecimal("116.00"))
                .cost(new BigDecimal("40.00"))
                .hasRecipe(true)
                .build();
        BigDecimal taxRate = new BigDecimal("16.00");

        assertThat(row.getPriceForTaxMode(taxRate, true)).isEqualByComparingTo("116.00");
        assertThat(row.getPriceForTaxMode(taxRate, false)).isEqualByComparingTo("100.00");
        assertThat(row.getProfitBeforeTax(taxRate)).isEqualByComparingTo("60.00");
        assertThat(row.getMarginBeforeTax(taxRate)).isEqualByComparingTo("60.0");
        assertThat(row.getFoodCostPercentageBeforeTax(taxRate)).isEqualByComparingTo("40.0");
    }

    @Test
    @DisplayName("comprar, inventariar y recetar en kg con 80 % de rendimiento da el costo real")
    void inventoryUnitAndYieldGiveTheRealCost() {
        ItemMenu steak = item(15L, "Bistec", "120.00");
        // 1 kg a $48 con 80 % de rendimiento → $60 por kg utilizable.
        Ingredient beef = ingredient(9L, "Carne", "48.00", "80.00");

        stub(steak, line(steak, beef, "0.10", "KG"));

        MenuItemCost row = only(singleRow());

        assertThat(row.getCost()).isEqualByComparingTo("6.00");
        assertThat(row.getProfit()).isEqualByComparingTo("114.00");
        assertThat(row.getMarginPercentage()).isEqualByComparingTo("95.0");
        assertThat(row.getLines().get(0).getUnitCostLabel()).isEqualTo("60.00");
    }

    @Test
    @DisplayName("un combo suma sus platillos hijos en vez de valer cero")
    void aComboAddsUpItsChildren() {
        ItemMenu combo = item(13L, "Combo familiar", "250.00");
        combo.setIsCombo(true);
        ItemMenu burger = item(14L, "Hamburguesa", "100.00");
        ItemMenu soda = item(15L, "Refresco", "35.00");

        Ingredient beef = ingredient(1L, "Carne", "40.00");
        Ingredient bread = ingredient(2L, "Pan", "5.00");
        Ingredient bottle = ingredient(3L, "Refresco embotellado", "12.00");

        when(menuRepository.findActiveForCosting(eq(company), any(), any(), any()))
                .thenReturn(page(List.of(combo)));
        when(recipeRepository.findByMenuItemIds(eq(company), anyCollection())).thenReturn(List.of(
                line(burger, beef, "0.50", "KG"),
                line(burger, bread, "2.00", "UN"),
                line(soda, bottle, "1.00", "UN")));
        when(comboRepository.findByComboMenuIds(eq(company), anyCollection())).thenReturn(List.of(
                comboItem(combo, burger, 2),
                comboItem(combo, soda, 1)));

        MenuItemCost row = only(singleRow());

        // Hamburguesa 30 × 2 + refresco 12 = 72.
        assertThat(row.getCost()).isEqualByComparingTo("72.00");
        assertThat(row.getProfit()).isEqualByComparingTo("178.00");
        assertThat(row.getMarginPercentage()).isEqualByComparingTo("71.2");
        assertThat(row.getMarginLabel()).isEqualTo("Rentable");
        assertThat(row.isCombo()).isTrue();
        assertThat(row.isIncomplete()).isFalse();
        assertThat(row.getLines()).hasSize(2);
        assertThat(row.getLines().get(0).getIngredientName()).isEqualTo("Hamburguesa");
        assertThat(row.getLines().get(0).getSubtotal()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("un combo con un hijo sin receta queda incompleto")
    void aComboWithAChildWithoutRecipeIsIncomplete() {
        ItemMenu combo = item(16L, "Combo", "150.00");
        combo.setIsCombo(true);
        ItemMenu mystery = item(17L, "Postre sin receta", "30.00");

        when(menuRepository.findActiveForCosting(eq(company), any(), any(), any()))
                .thenReturn(page(List.of(combo)));
        when(recipeRepository.findByMenuItemIds(eq(company), anyCollection())).thenReturn(List.of());
        when(comboRepository.findByComboMenuIds(eq(company), anyCollection())).thenReturn(List.of(comboItem(combo, mystery, 1)));

        MenuItemCost row = only(singleRow());

        assertThat(row.getCost()).isEqualByComparingTo("0.00");
        assertThat(row.getIngredientsWithoutCost()).isEqualTo(1);
        assertThat(row.isIncomplete()).isTrue();
        assertThat(row.getMarginBand()).isEqualTo("gray");
    }

    @Test
    @DisplayName("un ingrediente sin costo marca el platillo como incompleto, no como margen alto")
    void anIngredientWithoutCostMarksTheDishAsIncomplete() {
        ItemMenu dish = item(11L, "Ensalada", "80.00");
        Ingredient lettuce = ingredient(3L, "Lechuga", "10.00");
        Ingredient dressing = ingredient(4L, "Aderezo", null);

        stub(dish, line(dish, lettuce, "1.00", "KG"), line(dish, dressing, "0.20", "L"));

        MenuItemCost row = only(singleRow());

        assertThat(row.getIngredientsWithoutCost()).isEqualTo(1);
        assertThat(row.isIncomplete()).isTrue();
        assertThat(row.getMarginBand()).isEqualTo("gray");
        assertThat(row.getMarginLabel()).isEqualTo("Costo incompleto");
        assertThat(row.getLines())
                .filteredOn(line -> line.isMissingCost())
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.getIngredientName()).isEqualTo("Aderezo");
                    assertThat(line.getSubtotal()).isEqualByComparingTo("0.00");
                });
    }

    @Test
    @DisplayName("un precio en cero (cortesía) no inventa margen")
    void aZeroPriceDoesNotInventAMargin() {
        ItemMenu courtesy = item(12L, "Café de cortesía", "0.00");
        Ingredient coffee = ingredient(5L, "Café", "120.00");

        stub(courtesy, line(courtesy, coffee, "0.01", "KG"));

        MenuItemCost row = only(singleRow());

        assertThat(row.getCost()).isEqualByComparingTo("1.20");
        assertThat(row.getProfit()).isEqualByComparingTo("-1.20");
        assertThat(row.getMarginPercentage()).isNull();
        assertThat(row.getMarginBand()).isEqualTo("gray");
        assertThat(row.getMarginLabel()).isEqualTo("Sin calcular");
        assertThat(row.isIncomplete()).isFalse();
    }

    @Test
    @DisplayName("los platillos sin receta quedan marcados como incompletos")
    void aDishWithoutRecipeIsIncomplete() {
        ItemMenu soda = item(18L, "Refresco", "35.00");
        soda.setRequiresIngredients(false);

        stub(soda);

        MenuItemCost row = only(singleRow());

        assertThat(row.isHasRecipe()).isFalse();
        assertThat(row.isIncomplete()).isTrue();
        assertThat(row.getCost()).isEqualByComparingTo("0.00");
        assertThat(row.getMarginLabel()).isEqualTo("Sin receta");
    }

    @Test
    @DisplayName("el padre muestra sus hijos y cada hijo permite cambiar entre variantes")
    void parentAndChildExposeTheirMenuVariants() {
        ItemMenu parent = item(40L, "Hamburguesa", "80.00");
        ItemMenu small = item(41L, "Hamburguesa chica", "80.00");
        small.setSizeName("Chica");
        small.setParentItem(parent);
        ItemMenu large = item(42L, "Hamburguesa grande", "120.00");
        large.setSizeName("Grande");
        large.setParentItem(parent);

        when(menuRepository.findActiveForCosting(eq(company), any(), any(), any()))
                .thenReturn(page(List.of(parent, small, large)));
        when(recipeRepository.findByMenuItemIds(eq(company), anyCollection())).thenReturn(List.of());

        List<MenuItemCost> rows = pageRows();
        MenuItemCost parentCost = rows.stream().filter(row -> row.getIdItemMenu().equals(40L)).findFirst().orElseThrow();
        MenuItemCost smallCost = rows.stream().filter(row -> row.getIdItemMenu().equals(41L)).findFirst().orElseThrow();

        assertThat(parentCost.getRelatedVariants())
                .extracting(MenuItemCost.Variant::getIdItemMenu)
                .containsExactlyInAnyOrder(41L, 42L);
        assertThat(smallCost.getParentItemId()).isEqualTo(40L);
        assertThat(smallCost.getRelatedVariants())
                .extracting(MenuItemCost.Variant::getIdItemMenu)
                .containsExactlyInAnyOrder(40L, 41L, 42L);
        assertThat(smallCost.getRelatedVariants())
                .filteredOn(MenuItemCost.Variant::isSelected)
                .singleElement()
                .extracting(MenuItemCost.Variant::getIdItemMenu)
                .isEqualTo(41L);
    }

    @Test
    @DisplayName("la página conserva el orden alfabético de la base de datos")
    void preservesDatabasePageOrdering() {
        ItemMenu healthy = item(20L, "Saludable", "100.00");
        ItemMenu tight = item(21L, "Ajustado", "100.00");
        ItemMenu losing = item(22L, "Bajo", "100.00");

        Ingredient cheap = ingredient(6L, "Barato", "20.00");
        Ingredient mid = ingredient(7L, "Medio", "45.00");
        Ingredient pricey = ingredient(8L, "Caro", "80.00");

        when(menuRepository.findActiveForCosting(eq(company), any(), any(), any()))
                .thenReturn(page(List.of(tight, losing, healthy)));
        when(recipeRepository.findByMenuItemIds(eq(company), anyCollection())).thenReturn(List.of(
                line(healthy, cheap, "1.00", "UN"),
                line(tight, mid, "1.00", "UN"),
                line(losing, pricey, "1.00", "UN")));

        List<MenuItemCost> rows = pageRows();

        assertThat(rows).extracting(MenuItemCost::getName).containsExactly("Ajustado", "Bajo", "Saludable");
        assertThat(rows).extracting(MenuItemCost::getMarginLabel)
                .containsExactly("Moderado", "Bajo", "Rentable");
    }

    @Test
    @DisplayName("un platillo borrado no aparece en el costeo")
    void aDeletedDishIsExcluded() {
        ItemMenu deleted = item(30L, "Eliminado", "100.00");
        deleted.setDeleted(true);
        ItemMenu active = item(31L, "Vigente", "100.00");

        when(menuRepository.findActiveForCosting(eq(company), any(), any(), any()))
                .thenReturn(page(List.of(active)));
        when(recipeRepository.findByMenuItemIds(eq(company), anyCollection())).thenReturn(List.of());

        List<MenuItemCost> rows = pageRows();

        assertThat(rows).extracting(MenuItemCost::getName).containsExactly("Vigente");
    }

    // ========== Helpers ==========

    private List<MenuItemCost> singleRow() {
        return pageRows();
    }

    private List<MenuItemCost> pageRows() {
        return service.calculateForCurrentCompany(PageRequest.of(0, 20), null, null).getContent();
    }

    private MenuItemCost only(List<MenuItemCost> rows) {
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private void stub(ItemMenu item, ItemIngredient... lines) {
        when(menuRepository.findActiveForCosting(eq(company), any(), any(), any()))
                .thenReturn(page(List.of(item)));
        when(recipeRepository.findByMenuItemIds(eq(company), anyCollection())).thenReturn(new ArrayList<>(List.of(lines)));
    }

    private PageImpl<ItemMenu> page(List<ItemMenu> items) {
        return new PageImpl<>(items, PageRequest.of(0, 20), items.size());
    }

    private ItemMenu item(Long id, String name, String price) {
        return ItemMenu.builder()
                .idItemMenu(id)
                .company(company)
                .name(name)
                .price(new BigDecimal(price))
                .category(category)
                .active(true)
                .available(true)
                .internalOnly(false)
                .isCombo(false)
                .requiresIngredients(true)
                .build();
    }

    private Ingredient ingredient(Long id, String name, String cost) {
        return ingredient(id, name, cost, "100.00");
    }

    private Ingredient ingredient(Long id, String name, String cost, String yield) {
        return Ingredient.builder()
                .idIngredient(id)
                .company(company)
                .name(name)
                .unitOfMeasure("KG")
                .currentStock(new BigDecimal("100.00"))
                .minStock(BigDecimal.ZERO)
                .costPerUnit(cost == null ? null : new BigDecimal(cost))
                .yieldPercentage(new BigDecimal(yield))
                .active(true)
                .build();
    }

    private ItemIngredient line(ItemMenu item, Ingredient ingredient, String quantity, String unit) {
        return ItemIngredient.builder()
                .itemMenu(item)
                .ingredient(ingredient)
                .quantity(new BigDecimal(quantity))
                .unit(unit)
                .build();
    }

    private ItemMenuComboItem comboItem(ItemMenu combo, ItemMenu child, int quantity) {
        return ItemMenuComboItem.builder()
                .comboMenu(combo)
                .childMenu(child)
                .quantity(quantity)
                .displayOrder(0)
                .build();
    }
}
