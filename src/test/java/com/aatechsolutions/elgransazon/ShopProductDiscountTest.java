package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.ShopProduct;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Promotional pricing rules of the shop catalogue: a discount only exists when
 * it really lowers the regular price, and it never changes the stored price.
 */
class ShopProductDiscountTest {

    private ShopProduct product(String price, String discount) {
        return ShopProduct.builder()
                .name("Tarjeta QR")
                .price(price == null ? null : new BigDecimal(price))
                .discountPrice(discount == null ? null : new BigDecimal(discount))
                .build();
    }

    @Test
    @DisplayName("sin precio promocional se vende al precio normal")
    void withoutDiscountTheFinalPriceIsTheRegularOne() {
        ShopProduct product = product("250.00", null);

        assertFalse(product.isOnSale());
        assertEquals(0, new BigDecimal("250.00").compareTo(product.getFinalPrice()));
        assertEquals(0, product.getDiscountPercent());
        assertEquals(0, BigDecimal.ZERO.compareTo(product.getSavingsAmount()));
    }

    @Test
    @DisplayName("un precio menor activa la promoción y calcula el ahorro")
    void validDiscountActivatesThePromotion() {
        ShopProduct product = product("499.00", "399.20");

        assertTrue(product.isOnSale());
        assertEquals(0, new BigDecimal("399.20").compareTo(product.getFinalPrice()));
        assertEquals(20, product.getDiscountPercent());
        assertEquals(0, new BigDecimal("99.80").compareTo(product.getSavingsAmount()));
    }

    @Test
    @DisplayName("un descuento igual o mayor al precio no es una promoción")
    void discountMustBeLowerThanThePrice() {
        assertFalse(product("250.00", "250.00").isOnSale());
        assertFalse(product("250.00", "300.00").isOnSale());
        assertFalse(product("250.00", "0").isOnSale());
        assertEquals(0, new BigDecimal("250.00").compareTo(product("250.00", "300.00").getFinalPrice()));
    }

    @Test
    @DisplayName("un producto sin precio no revienta al calcular la promoción")
    void withoutPriceThereIsNoPromotion() {
        assertFalse(product(null, "199.00").isOnSale());
        assertEquals(0, product(null, "199.00").getDiscountPercent());
    }
}
