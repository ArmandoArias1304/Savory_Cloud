package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.ShopProductServiceImpl;
import com.aatechsolutions.elgransazon.domain.entity.ShopPricingMode;
import com.aatechsolutions.elgransazon.domain.entity.ShopProduct;
import com.aatechsolutions.elgransazon.domain.repository.ShopProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * What actually reaches the database when a product is published: a range
 * keeps both bounds, a quote stores no amount, and the price note only lives
 * where it makes sense.
 */
@ExtendWith(MockitoExtension.class)
class ShopProductServicePricingTest {

    @Mock
    private ShopProductRepository shopProductRepository;

    private ShopProductServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShopProductServiceImpl(shopProductRepository);
        when(shopProductRepository.save(any(ShopProduct.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ShopProduct product(ShopPricingMode mode) {
        return ShopProduct.builder()
                .name("Capacitación")
                .pricingMode(mode)
                .price(new BigDecimal("200.00"))
                .build();
    }

    @Test
    @DisplayName("un rango válido conserva mínimo, máximo y su nota")
    void aValidRangeIsStored() {
        ShopProduct range = product(ShopPricingMode.RANGE);
        range.setMaxPrice(new BigDecimal("500.00"));
        range.setPriceNote("  Depende de la distancia  ");

        ShopProduct saved = service.create(range);

        assertEquals(ShopPricingMode.RANGE, saved.getPricingMode());
        assertEquals(0, new BigDecimal("200.00").compareTo(saved.getPrice()));
        assertEquals(0, new BigDecimal("500.00").compareTo(saved.getMaxPrice()));
        assertEquals("Depende de la distancia", saved.getPriceNote());
    }

    @Test
    @DisplayName("un rango sin máximo cae a precio fijo para no mostrar un precio roto")
    void aRangeWithoutMaximumFallsBackToAFixedPrice() {
        ShopProduct range = product(ShopPricingMode.RANGE);
        range.setMaxPrice(new BigDecimal("150.00"));

        ShopProduct saved = service.create(range);

        assertEquals(ShopPricingMode.FIXED, saved.getPricingMode());
        assertNull(saved.getMaxPrice());
        assertEquals(0, new BigDecimal("200.00").compareTo(saved.getPrice()));
    }

    @Test
    @DisplayName("un precio a evaluar guarda 0 y no publica montos ni promoción")
    void aQuoteStoresNoAmount() {
        ShopProduct quote = product(ShopPricingMode.QUOTE);
        quote.setMaxPrice(new BigDecimal("500.00"));
        quote.setDiscountPrice(new BigDecimal("150.00"));
        quote.setPriceNote("Se cotiza según el número de empleados");

        ShopProduct saved = service.create(quote);

        assertTrue(saved.isQuote());
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.getPrice()));
        assertNull(saved.getMaxPrice());
        assertNull(saved.getDiscountPrice());
        assertEquals("Se cotiza según el número de empleados", saved.getPriceNote());
    }

    @Test
    @DisplayName("un precio fijo limpia el rango y la nota que ya no aplican")
    void aFixedPriceClearsTheRangeFields() {
        ShopProduct fixed = product(ShopPricingMode.FIXED);
        fixed.setMaxPrice(new BigDecimal("500.00"));
        fixed.setPriceNote("Depende de la distancia");
        fixed.setDiscountPrice(new BigDecimal("150.00"));

        ShopProduct saved = service.create(fixed);

        assertEquals(ShopPricingMode.FIXED, saved.getPricingMode());
        assertNull(saved.getMaxPrice());
        assertNull(saved.getPriceNote());
        assertEquals(0, new BigDecimal("150.00").compareTo(saved.getDiscountPrice()));
    }
}
