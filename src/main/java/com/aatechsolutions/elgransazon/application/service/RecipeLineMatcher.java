package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.ItemIngredient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compara recetas por ingrediente y cantidad, sin importar el orden de las líneas.
 *
 * <p>Se usa para decidir si una variante de tamaño venía copiando la receta del padre: en ese caso
 * debe seguir sus cambios. Una variante con receta propia —el "Grande" que lleva más carne— no
 * coincide y se deja intacta.</p>
 */
final class RecipeLineMatcher {

    private RecipeLineMatcher() {
    }

    /** Foto de la receta como {@code idIngrediente -> cantidad total}. */
    static Map<Long, BigDecimal> snapshot(List<ItemIngredient> lines) {
        Map<Long, BigDecimal> totals = new HashMap<>();
        if (lines == null) {
            return totals;
        }
        for (ItemIngredient line : lines) {
            if (line.getIngredient() == null || line.getIngredient().getIdIngredient() == null) {
                continue;
            }
            BigDecimal quantity = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ZERO;
            totals.merge(line.getIngredient().getIdIngredient(), quantity, BigDecimal::add);
        }
        return totals;
    }

    /** true cuando las líneas son exactamente la receta de la foto (mismos ingredientes y cantidades). */
    static boolean matches(Map<Long, BigDecimal> snapshot, List<ItemIngredient> lines) {
        Map<Long, BigDecimal> expected = snapshot != null ? snapshot : Map.of();
        Map<Long, BigDecimal> actual = snapshot(lines);

        if (!expected.keySet().equals(actual.keySet())) {
            return false;
        }
        for (Map.Entry<Long, BigDecimal> entry : expected.entrySet()) {
            // compareTo y no equals: 0.5 y 0.50 son la misma cantidad aunque BigDecimal no lo crea.
            if (entry.getValue().compareTo(actual.get(entry.getKey())) != 0) {
                return false;
            }
        }
        return true;
    }
}
