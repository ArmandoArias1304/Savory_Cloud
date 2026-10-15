package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.BusinessHoursService;
import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El menú visual del mesero recibe los tamaños de cada platillo, pero no como recuadros:
 * los hijos salen del grid y viajan al diálogo de su item padre.
 *
 * <p>Es el mismo reparto que ya hacen el menú del cliente y las pantallas de pedido. Si
 * el grid volviera a listar los hijos, el mismo platillo aparecería dos veces; si el mapa
 * dejara de enviarse, el diálogo no tendría nada que enseñar. Aquí se fijan las dos
 * mitades.</p>
 */
class WaiterMenuVariantsModelTest {

    private static final Long PIZZA = 1L;
    private static final Long MEDIANA = 3L;
    private static final Long GRANDE = 2L;

    private final ItemMenuService itemMenuService = mock(ItemMenuService.class);
    private final CategoryService categoryService = mock(CategoryService.class);
    private final SystemConfigurationService configurationService =
            mock(SystemConfigurationService.class);

    private final WaiterController controller = new WaiterController(
            mock(EmployeeService.class),
            mock(OrderRepository.class),
            itemMenuService,
            categoryService,
            configurationService,
            mock(BusinessHoursService.class),
            mock(DateTimeService.class));

    @Test
    @DisplayName("los tamaños salen del grid y entran al diálogo del item padre")
    @SuppressWarnings("unchecked")
    void sizesLeaveTheGridAndTravelToTheDialog() {
        Category pizzas = category(1L, "Pizzas");
        ItemMenu pizza = item(PIZZA, "Pizza", "149.00", pizzas);
        ItemMenu mediana = size(MEDIANA, "Pizza mediana", "Mediana", "199.00", pizza, pizzas);
        ItemMenu grande = size(GRANDE, "Pizza grande", "Grande", "249.00", pizza, pizzas);
        prepare(List.of(pizza, mediana, grande), pizzas);

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.viewMenu(model, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("waiter/menu/view");

        Map<Long, List<ItemMenu>> byCategory =
                (Map<Long, List<ItemMenu>>) model.getAttribute("itemsByCategory");
        assertThat(byCategory.get(1L))
                .as("el grid sólo lleva el item padre")
                .extracting(ItemMenu::getIdItemMenu)
                .containsExactly(PIZZA);

        Map<Long, List<Map<String, Object>>> sizes =
                (Map<Long, List<Map<String, Object>>>) model.getAttribute("sizeItemsDto");
        assertThat(sizes)
                .as("los tamaños cuelgan de su padre")
                .containsOnlyKeys(PIZZA);
        assertThat(sizes.get(PIZZA))
                .as("ordenados de la presentación más barata a la más cara")
                .extracting(dto -> dto.get("id"))
                .containsExactly(MEDIANA, GRANDE);
        assertThat(sizes.get(PIZZA).get(0))
                .containsEntry("sizeName", "Mediana")
                .containsEntry("name", "Pizza mediana")
                .containsEntry("price", new BigDecimal("199.00"));
    }

    @Test
    @DisplayName("un platillo sin tamaños deja el mapa vacío y sigue en el grid")
    @SuppressWarnings("unchecked")
    void withoutSizesTheMapStaysEmpty() {
        Category drinks = category(2L, "Bebidas");
        ItemMenu soda = item(9L, "Refresco", "35.00", drinks);
        prepare(List.of(soda), drinks);

        ExtendedModelMap model = new ExtendedModelMap();
        controller.viewMenu(model, new RedirectAttributesModelMap());

        Map<Long, List<ItemMenu>> byCategory =
                (Map<Long, List<ItemMenu>>) model.getAttribute("itemsByCategory");
        assertThat(byCategory.get(2L)).extracting(ItemMenu::getIdItemMenu).containsExactly(9L);

        Map<Long, List<Map<String, Object>>> sizes =
                (Map<Long, List<Map<String, Object>>>) model.getAttribute("sizeItemsDto");
        assertThat(sizes).isEmpty();
    }

    private void prepare(List<ItemMenu> items, Category... categories) {
        when(itemMenuService.findAvailableItems()).thenReturn(items);
        when(categoryService.getAllActiveCategories()).thenReturn(List.of(categories));
        when(configurationService.getConfiguration())
                .thenReturn(SystemConfiguration.builder().restaurantName("El Gran Sazon").build());
    }

    private Category category(Long id, String name) {
        return Category.builder().idCategory(id).name(name).build();
    }

    private ItemMenu item(Long id, String name, String price, Category category) {
        return ItemMenu.builder()
                .idItemMenu(id)
                .name(name)
                .description("Descripción de " + name)
                .price(new BigDecimal(price))
                .available(true)
                .category(category)
                .build();
    }

    private ItemMenu size(
            Long id, String name, String sizeName, String price, ItemMenu parent, Category category) {
        ItemMenu size = item(id, name, price, category);
        size.setSizeName(sizeName);
        size.setParentItem(parent);
        return size;
    }
}
