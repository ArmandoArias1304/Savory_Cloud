package com.aatechsolutions.elgransazon.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopWhatsAppLinkTest {

    @Test
    @DisplayName("normaliza el teléfono y agrega la lada de México a 10 dígitos")
    void normalizesPhoneNumbers() {
        assertEquals("525512345678", ShopWhatsAppLink.normalizePhone("55 1234 5678"));
        assertEquals("525512345678", ShopWhatsAppLink.normalizePhone("+52 (55) 1234-5678"));
        assertEquals("5215512345678", ShopWhatsAppLink.normalizePhone("00 52 1 55 1234 5678"));
        assertNull(ShopWhatsAppLink.normalizePhone("   "));
        assertNull(ShopWhatsAppLink.normalizePhone(null));
    }

    @Test
    @DisplayName("valida que el número tenga lada suficiente")
    void validatesPhoneLength() {
        assertTrue(ShopWhatsAppLink.isValidPhone("55 1234 5678"));
        assertTrue(ShopWhatsAppLink.isValidPhone("+52 1 55 1234 5678"));
        org.junit.jupiter.api.Assertions.assertFalse(ShopWhatsAppLink.isValidPhone("12345"));
        org.junit.jupiter.api.Assertions.assertFalse(ShopWhatsAppLink.isValidPhone("sin numero"));
        org.junit.jupiter.api.Assertions.assertFalse(ShopWhatsAppLink.isValidPhone(null));
    }

    @Test
    @DisplayName("el mensaje lleva restaurante, producto y precio")
    void buildsAPrefilledMessage() {
        String url = ShopWhatsAppLink.build(
                "55 1234 5678",
                "El Gran Sazón",
                "Tarjeta QR para mesa",
                "$ 1,250.00");

        assertTrue(url.startsWith("https://wa.me/525512345678?text="));

        String message = URLDecoder.decode(url.substring(url.indexOf("text=") + 5), StandardCharsets.UTF_8);
        assertTrue(message.contains("El Gran Sazón"));
        assertTrue(message.contains("Tarjeta QR para mesa"));
        assertTrue(message.contains("$ 1,250.00"));
        assertTrue(message.contains("¿Me pueden dar más información?"));
    }

    @Test
    @DisplayName("sin número configurado no se genera enlace")
    void withoutNumberThereIsNoLink() {
        assertNull(ShopWhatsAppLink.build(null, "Restaurante", "Producto", "$ 10.00"));
        assertNull(ShopWhatsAppLink.build("", "Restaurante", "Producto", "$ 10.00"));
    }

    @Test
    @DisplayName("un restaurante desconocido se describe genéricamente")
    void unknownRestaurantFallsBackToAGenericName() {
        String url = ShopWhatsAppLink.build("525512345678", null, "Producto", "$ 10.00");
        String message = URLDecoder.decode(url.substring(url.indexOf("text=") + 5), StandardCharsets.UTF_8);
        assertTrue(message.contains("soy de un restaurante"));
    }

    @Test
    @DisplayName("el precio se muestra en formato mexicano")
    void formatsPrices() {
        assertEquals("$ 1,250.50", ShopWhatsAppLink.formatPrice(new BigDecimal("1250.5"), "MXN"));
        assertEquals("", ShopWhatsAppLink.formatPrice(null, "MXN"));
    }
}
