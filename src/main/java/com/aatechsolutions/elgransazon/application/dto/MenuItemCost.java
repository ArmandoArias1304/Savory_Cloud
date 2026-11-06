package com.aatechsolutions.elgransazon.application.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Una fila de la pantalla de costeo: qué cuesta preparar un platillo y cuánto deja.
 *
 * <p>El costo sale de la receta valuada con el costo promedio ponderado de cada ingrediente
 * ({@code Ingredient.costPerUnit}); el margen es {@code (precio − costo) / precio}.</p>
 *
 * <p>Un platillo puede estar <b>incompleto</b> y entonces su margen no es confiable: los platillos
 * sin receta tienen costo cero, y una receta con ingredientes sin costo produce un número más bajo
 * que el real. En esos casos se marca como incompleto y el
 * semáforo se pinta gris en vez de verde, para no dar una falsa tranquilidad.</p>
 */
@Getter
@Builder
public class MenuItemCost {

    private final Long idItemMenu;
    private final String name;
    private final String categoryName;
    private final String imageUrl;
    private final String sizeName;
    private final String parentName;
    private final Long parentItemId;
    private final List<Variant> relatedVariants;

    private final BigDecimal price;
    private final BigDecimal cost;
    private final BigDecimal profit;
    private final BigDecimal marginPercentage;

    private final boolean combo;
    private final boolean hasRecipe;
    private final int ingredientsWithoutCost;
    private final List<Line> lines;

    /** A selectable parent or size variant shown from the current dish detail. */
    @Getter
    @Builder
    public static class Variant {
        private final Long idItemMenu;
        private final String name;
        private final String sizeName;
        private final BigDecimal price;
        private final boolean selected;
    }

    /** Una línea del desglose: cuánto aporta cada ingrediente al costo del platillo. */
    @Getter
    @Builder
    public static class Line {
        private final String ingredientName;
        private final BigDecimal quantity;
        private final String unit;
        private final BigDecimal unitCost;
        private final BigDecimal subtotal;
        private final BigDecimal yieldPercentage;
        private final boolean missingCost;

        /** Costo unitario sin ceros de relleno: 0.048 en vez de 0.05. */
        public String getUnitCostLabel() {
            if (unitCost == null) {
                return "0.00";
            }
            BigDecimal trimmed = unitCost.stripTrailingZeros();
            if (trimmed.scale() < 2) {
                trimmed = trimmed.setScale(2);
            }
            return trimmed.toPlainString();
        }
    }

    /** El margen no se puede calcular (cortesía de precio cero) o el costo está incompleto. */
    public boolean isIncomplete() {
        return !hasRecipe || ingredientsWithoutCost > 0;
    }

    /** Parte del precio de venta que representa el costo de preparación. */
    public BigDecimal getFoodCostPercentage() {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return cost.multiply(BigDecimal.valueOf(100))
                .divide(price, 1, RoundingMode.HALF_UP);
    }

    public BigDecimal getPriceForTaxMode(BigDecimal taxRate, boolean includeTax) {
        if (includeTax) {
            return price;
        }
        return getPriceWithoutTax(taxRate);
    }

    public BigDecimal getPriceWithoutTax(BigDecimal taxRate) {
        if (price == null || taxRate == null || taxRate.compareTo(BigDecimal.ZERO) <= 0) {
            return price;
        }
        BigDecimal multiplier = BigDecimal.ONE.add(
                taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
        return price.divide(multiplier, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getProfitBeforeTax(BigDecimal taxRate) {
        BigDecimal priceBeforeTax = getPriceWithoutTax(taxRate);
        return priceBeforeTax != null && cost != null ? priceBeforeTax.subtract(cost) : null;
    }

    public BigDecimal getMarginBeforeTax(BigDecimal taxRate) {
        BigDecimal priceBeforeTax = getPriceWithoutTax(taxRate);
        BigDecimal profitBeforeTax = getProfitBeforeTax(taxRate);
        if (priceBeforeTax == null || profitBeforeTax == null
                || priceBeforeTax.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return profitBeforeTax.multiply(BigDecimal.valueOf(100))
                .divide(priceBeforeTax, 1, RoundingMode.HALF_UP);
    }

    public BigDecimal getFoodCostPercentageBeforeTax(BigDecimal taxRate) {
        BigDecimal priceBeforeTax = getPriceWithoutTax(taxRate);
        if (cost == null || priceBeforeTax == null || priceBeforeTax.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return cost.multiply(BigDecimal.valueOf(100))
                .divide(priceBeforeTax, 1, RoundingMode.HALF_UP);
    }

    public int getMarginBarPercentageBeforeTax(BigDecimal taxRate) {
        BigDecimal margin = getMarginBeforeTax(taxRate);
        if (margin == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, margin.intValue()));
    }

    public String getMarginBandBeforeTax(BigDecimal taxRate) {
        BigDecimal margin = getMarginBeforeTax(taxRate);
        if (isIncomplete() || margin == null) {
            return "gray";
        }
        if (margin.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "green";
        }
        if (margin.compareTo(BigDecimal.valueOf(40)) >= 0) {
            return "amber";
        }
        return "red";
    }

    public String getMarginLabelBeforeTax(BigDecimal taxRate) {
        return marginLabel(getMarginBandBeforeTax(taxRate));
    }

    /** Valor visual del semáforo, limitado al rango de la barra. */
    public BigDecimal getMarginBarPercentage() {
        if (marginPercentage == null || marginPercentage.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return marginPercentage.min(BigDecimal.valueOf(100));
    }

    /** Precio requerido para alcanzar el margen bruto solicitado (0 a menos de 100 %). */
    public BigDecimal getSuggestedPriceAtMargin(BigDecimal targetMargin) {
        if (isIncomplete() || cost == null || cost.compareTo(BigDecimal.ZERO) <= 0
                || targetMargin == null || targetMargin.compareTo(BigDecimal.ZERO) < 0
                || targetMargin.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return null;
        }
        BigDecimal priceShare = BigDecimal.valueOf(100).subtract(targetMargin);
        return cost.multiply(BigDecimal.valueOf(100))
                .divide(priceShare, 2, RoundingMode.HALF_UP);
    }

    /** Ganancia bruta estimada con el precio sugerido para el margen solicitado. */
    public BigDecimal getEstimatedProfitAtMargin(BigDecimal targetMargin) {
        BigDecimal suggestedPrice = getSuggestedPriceAtMargin(targetMargin);
        return suggestedPrice != null ? suggestedPrice.subtract(cost) : null;
    }

    /** Semáforo: verde ≥ 60 %, ámbar 40–60 %, rojo < 40 %, gris cuando no hay dato confiable. */
    public String getMarginBand() {
        if (isIncomplete() || marginPercentage == null) {
            return "gray";
        }
        if (marginPercentage.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "green";
        }
        if (marginPercentage.compareTo(BigDecimal.valueOf(40)) >= 0) {
            return "amber";
        }
        return "red";
    }

    public String getMarginLabel() {
        return marginLabel(getMarginBand());
    }

    private String marginLabel(String band) {
        return switch (band) {
            case "green" -> "Rentable";
            case "amber" -> "Moderado";
            case "red" -> "Bajo";
            default -> !hasRecipe ? "Sin receta"
                    : ingredientsWithoutCost > 0 ? "Costo incompleto" : "Sin calcular";
        };
    }
}
