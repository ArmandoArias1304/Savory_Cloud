package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.ShopBillingType;
import com.aatechsolutions.elgransazon.domain.entity.ShopProduct;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shop product is either bought once or charged every month on top of the
 * restaurant subscription. These rules are what the store prints next to the
 * price, so they are pinned here.
 */
class ShopProductBillingTest {

    private ShopProduct product(ShopBillingType billingType) {
        return ShopProduct.builder()
                .name("WiFi Social")
                .price(new BigDecimal("150.00"))
                .billingType(billingType)
                .build();
    }

    @Test
    @DisplayName("sin forma de cobro el producto es de pago único")
    void defaultsToOneTimePayment() {
        ShopProduct product = ShopProduct.builder()
                .name("Tarjeta QR")
                .price(new BigDecimal("250.00"))
                .build();

        assertEquals(ShopBillingType.ONE_TIME, product.getBillingTypeOrDefault());
        assertFalse(product.isMonthly());
        assertEquals("Pago único", product.getBillingLabel());
        assertEquals("", product.getBillingPeriodSuffix());
    }

    @Test
    @DisplayName("un producto mensual se suma a la mensualidad y el precio lo dice")
    void monthlyProductsShowThePeriod() {
        ShopProduct product = product(ShopBillingType.MONTHLY);

        assertTrue(product.isMonthly());
        assertEquals("Mensual", product.getBillingLabel());
        assertEquals(" / mes", product.getBillingPeriodSuffix());
        assertTrue(product.getBillingDescription().contains("mensualidad"));
    }

    @Test
    @DisplayName("un producto de pago único aclara que se cobra una sola vez")
    void oneTimeProductsExplainTheSingleCharge() {
        ShopProduct product = product(ShopBillingType.ONE_TIME);

        assertFalse(product.isMonthly());
        assertEquals("Pago único", product.getBillingLabel());
        assertEquals("", product.getBillingPeriodSuffix());
        assertTrue(product.getBillingDescription().contains("una sola vez"));
    }

    @Test
    @DisplayName("las filas viejas sin tipo de cobro se leen como pago único")
    void legacyRowsWithoutBillingTypeAreOneTime() {
        ShopProduct legacy = product(null);

        assertEquals(ShopBillingType.ONE_TIME, legacy.getBillingTypeOrDefault());
        assertFalse(legacy.isMonthly());
        // The store never shows an empty billing label.
        assertEquals("Pago único", legacy.getBillingLabel());
    }
}
