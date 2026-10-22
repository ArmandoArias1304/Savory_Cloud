package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.ShopPricingMode;
import com.aatechsolutions.elgransazon.domain.entity.ShopProduct;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Not every product has a single price: a training costs 200 in the city and
 * 500 outside it, and some services are quoted per order. These rules decide
 * what the store is allowed to show.
 */
class ShopProductPricingTest {

    private ShopProduct product(ShopPricingMode mode, String min, String max) {
        return ShopProduct.builder()
                .name("Capacitación")
                .pricingMode(mode)
                .price(min == null ? null : new BigDecimal(min))
                .maxPrice(max == null ? null : new BigDecimal(max))
                .build();
    }

    @Test
    @DisplayName("sin tipo de precio el producto es de precio fijo")
    void defaultsToAFixedPrice() {
        ShopProduct product = ShopProduct.builder()
                .name("Tarjeta QR")
                .price(new BigDecimal("250.00"))
                .build();

        assertEquals(ShopPricingMode.FIXED, product.getPricingModeOrDefault());
        assertTrue(product.isFixedPrice());
        assertFalse(product.isPriceRange());
        assertFalse(product.isQuote());
        assertEquals("Precio fijo", product.getPricingLabel());
    }

    @Test
    @DisplayName("un rango publica mínimo y máximo sin promoción")
    void aRangeKeepsBothBounds() {
        ShopProduct product = product(ShopPricingMode.RANGE, "200.00", "500.00");
        product.setDiscountPrice(new BigDecimal("180.00"));

        assertTrue(product.isPriceRange());
        assertFalse(product.isFixedPrice());
        assertFalse(product.isQuote());
        assertEquals(0, new BigDecimal("200.00").compareTo(product.getPrice()));
        assertEquals(0, new BigDecimal("500.00").compareTo(product.getMaxPrice()));
        assertEquals("Rango de precios", product.getPricingLabel());
        assertFalse(product.isOnSale(), "un rango nunca se anuncia como promoción");
    }

    @Test
    @DisplayName("un precio a evaluar no muestra ningún monto")
    void aQuoteHasNoPublishedAmount() {
        ShopProduct product = product(ShopPricingMode.QUOTE, "0", null);

        assertTrue(product.isQuote());
        assertFalse(product.isFixedPrice());
        assertFalse(product.isPriceRange());
        assertEquals("Precio a evaluar", product.getPricingLabel());
        assertFalse(product.isOnSale());
    }

    @Test
    @DisplayName("un precio fijo sí puede tener promoción")
    void aFixedPriceCanBeOnSale() {
        ShopProduct product = product(ShopPricingMode.FIXED, "499.00", null);
        product.setDiscountPrice(new BigDecimal("399.00"));

        assertTrue(product.isOnSale());
        assertEquals(0, new BigDecimal("399.00").compareTo(product.getFinalPrice()));
        assertEquals(20, product.getDiscountPercent());
    }
}
