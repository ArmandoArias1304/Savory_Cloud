package com.aatechsolutions.elgransazon.util;

import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El reparto que usan las cinco pantallas de carta: un recuadro por platillo y sus
 * tamaños en el mapa que viaja al diálogo.
 *
 * <p>Antes cada pantalla hacía su propio reparto —o ninguno—, así que el mismo
 * platillo podía salir dos veces en una y bien en otra. Aquí se fija el criterio
 * en un solo lugar: qué es un tamaño, en qué orden sale y qué datos lleva.</p>
 */
class MenuSizeVariantsTest {

    private static final Long PIZZA = 1L;
    private static final Long MEDIANA = 3L;
    private static final Long GRANDE = 2L;

    private final Category pizzas = Category.builder().idCategory(1L).name("Pizzas").build();

    @Test
    @DisplayName("el grid se queda con los platillos y deja fuera sus tamaños")
    void rootsKeepsTheDishes() {
        ItemMenu pizza = item(PIZZA, "Pizza", "149.00");
        ItemMenu mediana = size(MEDIANA, "Pizza mediana", "Mediana", "199.00", pizza);
        ItemMenu grande = size(GRANDE, "Pizza grande", "Grande", "249.00", pizza);
        ItemMenu refresco = item(9L, "Refresco", "35.00");

        List<ItemMenu> roots = MenuSizeVariants.roots(List.of(pizza, mediana, grande, refresco));

        assertThat(roots).extracting(ItemMenu::getIdItemMenu).containsExactly(PIZZA, 9L);
    }

    @Test
    @DisplayName("los tamaños cuelgan de su padre y van del más barato al más caro")
    void sizesHangFromTheirParentCheapestFirst() {
        ItemMenu pizza = item(PIZZA, "Pizza", "149.00");
        ItemMenu grande = size(GRANDE, "Pizza grande", "Grande", "249.00", pizza);
        ItemMenu mediana = size(MEDIANA, "Pizza mediana", "Mediana", "199.00", pizza);

        Map<Long, List<Map<String, Object>>> sizes =
                MenuSizeVariants.byParentItem(List.of(grande, pizza, mediana));

        assertThat(sizes).containsOnlyKeys(PIZZA);
        assertThat(sizes.get(PIZZA))
                .extracting(dto -> dto.get("id"))
                .containsExactly(MEDIANA, GRANDE);
        assertThat(sizes.get(PIZZA).get(0))
                .containsEntry("name", "Pizza mediana")
                .containsEntry("sizeName", "Mediana")
                .containsEntry("price", new BigDecimal("199.00"));
    }

    @Test
    @DisplayName("un tamaño sin etiqueta propia se anuncia con el nombre del item")
    void aSizeWithoutItsOwnLabelUsesItsName() {
        ItemMenu pizza = item(PIZZA, "Pizza", "149.00");
        ItemMenu sinEtiqueta = size(GRANDE, "Pizza familiar", null, "299.00", pizza);

        Map<Long, List<Map<String, Object>>> sizes =
                MenuSizeVariants.byParentItem(List.of(pizza, sinEtiqueta));

        assertThat(sizes.get(PIZZA).get(0))
                .containsEntry("sizeName", "Pizza familiar")
                .containsEntry("available", true);
    }

    @Test
    @DisplayName("un platillo sin tamaños no aparece en el mapa")
    void aDishWithoutSizesIsNotInTheMap() {
        assertThat(MenuSizeVariants.byParentItem(List.of(item(9L, "Refresco", "35.00")))).isEmpty();
    }

    private ItemMenu item(Long id, String name, String price) {
        return ItemMenu.builder()
                .idItemMenu(id)
                .name(name)
                .description("Descripción de " + name)
                .price(new BigDecimal(price))
                .available(true)
                .category(pizzas)
                .build();
    }

    private ItemMenu size(Long id, String name, String sizeName, String price, ItemMenu parent) {
        ItemMenu size = item(id, name, price);
        size.setSizeName(sizeName);
        size.setParentItem(parent);
        return size;
    }
}
