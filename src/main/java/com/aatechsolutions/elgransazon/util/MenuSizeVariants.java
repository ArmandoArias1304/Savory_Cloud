package com.aatechsolutions.elgransazon.util;

import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reparto de los tamaños de un platillo para las pantallas que enseñan la carta
 * (menú público del local y menú visual de mesero, cajero, cocina y reparto).
 *
 * <p>Un platillo con tamaños son varios items en la base: el padre y un hijo por
 * tamaño. Dibujarlos todos en el grid hacía que el mismo platillo apareciera
 * varias veces y que ninguna tarjeta dijera que se puede pedir de otra forma, así
 * que estas pantallas muestran un recuadro por platillo ({@link #roots}) y sus
 * tamaños se ven dentro del diálogo del padre, que los recibe como un mapa plano
 * listo para el JavaScript ({@link #byParentItem}).</p>
 *
 * <p>Es la misma pieza que usan las pantallas de pedido para su selector de
 * tamaños: un solo lugar donde se decide qué es un tamaño y en qué orden sale.</p>
 */
public final class MenuSizeVariants {

    private MenuSizeVariants() {
    }

    /**
     * Los items que se dibujan en el grid: los que no son tamaño de ningún otro.
     * Un tamaño nunca es una tarjeta por su cuenta; se alcanza desde su padre.
     */
    public static List<ItemMenu> roots(List<ItemMenu> items) {
        return items.stream()
                .filter(item -> item.getParentItem() == null)
                .toList();
    }

    /**
     * Los tamaños de cada item padre, como {@code idItemMenu -> lista de datos planos}
     * y del más barato al más caro. Se envían datos planos y no las entidades para no
     * arrastrar relaciones lazy hasta el navegador.
     */
    public static Map<Long, List<Map<String, Object>>> byParentItem(List<ItemMenu> items) {
        Map<Long, List<Map<String, Object>>> sizes = new LinkedHashMap<>();
        items.stream()
                .filter(item -> item.getParentItem() != null)
                .sorted(Comparator.comparing(
                        ItemMenu::getPrice, Comparator.nullsFirst(Comparator.naturalOrder())))
                .forEach(child -> sizes
                        .computeIfAbsent(
                                child.getParentItem().getIdItemMenu(), key -> new ArrayList<>())
                        .add(toDto(child)));
        return sizes;
    }

    private static Map<String, Object> toDto(ItemMenu item) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", item.getIdItemMenu());
        dto.put("name", item.getName());
        dto.put("sizeName", item.getSizeName() != null ? item.getSizeName() : item.getName());
        dto.put("price", item.getPrice());
        dto.put("available", item.getAvailable());
        dto.put("imageUrl", item.getImageUrl());
        dto.put("description", item.getDescription());
        return dto;
    }
}
