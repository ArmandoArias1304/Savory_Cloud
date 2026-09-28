package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.dto.MenuStyle;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Style of the printed menu: the two rules that are not negotiable from the dialog — the footer
 * (always "Restaurante by Savory Cloud") and the sheet color (white or a light tint) — plus the
 * defaults the reader of a stored configuration gets.
 */
class MenuStyleTest {

    @Test
    void theFooterIsTheRestaurantNamePlusTheBrand() {
        assertEquals("Quinta El Paraíso by Savory Cloud", MenuStyle.footerFor("Quinta El Paraíso"));
        assertEquals("Quinta El Paraíso by Savory Cloud", MenuStyle.footerFor("  Quinta El Paraíso  "));
    }

    @Test
    void theFooterFallsBackToTheBrandWhenThereIsNoRestaurantName() {
        assertEquals(MenuStyle.BRAND, MenuStyle.footerFor(null));
        assertEquals(MenuStyle.BRAND, MenuStyle.footerFor("   "));
    }

    @Test
    void aVeryLongRestaurantNameIsCutBeforeItOverflowsTheFooter() {
        String footer = MenuStyle.footerFor("Restaurante de la Esquina Familiar del Puerto de Veracruz");

        assertEquals(MenuStyle.MAX_FOOTER_LENGTH, footer.length());
        assertTrue(footer.startsWith("Restaurante de la Esquina"), "el pie quedó irreconocible");
    }

    /** El estilo no lleva el pie: el diálogo no puede reescribirlo ni siquiera mandándolo. */
    @Test
    void anOldFooterParameterIsIgnored() {
        MenuStyle saved = MenuStyle.from(configuration());

        MenuStyle requested = MenuStyle.fromParams(Map.of("footerText", "Carta de la casa"), saved);

        assertEquals(saved, requested, "el parámetro del pie cambió el estilo guardado");
    }

    @Test
    void theSheetColorComesFromTheConfiguration() {
        SystemConfiguration config = configuration();
        config.setMenuPageColor("#FDF6E3");

        assertEquals("#fdf6e3", MenuStyle.from(config).pageColor());
    }

    @Test
    void theSheetColorCanBeChangedFromTheRequestAndBadValuesAreIgnored() {
        MenuStyle saved = MenuStyle.from(configuration());

        assertEquals("#eef4fb", MenuStyle.fromParams(Map.of("pageColor", "#eef4fb"), saved).pageColor());
        assertEquals("#eef4fb", MenuStyle.fromParams(Map.of("pageColor", "EEF4FB"), saved).pageColor());
        assertEquals(saved.pageColor(), MenuStyle.fromParams(Map.of("pageColor", "azul"), saved).pageColor());
    }

    @Test
    void whitePaperIsNotPaintedAtAll() {
        assertFalse(MenuStyle.defaults().hasTintedPaper(), "el papel blanco no debería pintarse");
        assertFalse(MenuStyle.fromParams(Map.of("pageColor", "#ffffff"), MenuStyle.defaults()).hasTintedPaper());
        assertTrue(MenuStyle.fromParams(Map.of("pageColor", "#f6efe4"), MenuStyle.defaults()).hasTintedPaper());
    }

    /**
     * Los tonos que se ofrecen son claros a propósito: el texto de la carta es oscuro, así que un
     * papel oscuro la haría ilegible (y gastaría mucha tinta).
     */
    @Test
    void everySheetColorPresetIsLight() {
        assertTrue(MenuStyle.pageColorPresets().size() >= 4, "faltan tonos de hoja");

        for (MenuStyle.PageColorPreset preset : MenuStyle.pageColorPresets()) {
            assertTrue(preset.color().matches("^#[0-9a-f]{6}$"), "tono inválido: " + preset.color());
            assertTrue(luminance(preset.color()) > 0.85f,
                    "el tono '" + preset.label() + "' es demasiado oscuro para imprimir texto encima");
            assertFalse(preset.label().isBlank(), "un tono se quedó sin nombre");
        }
    }

    private static float luminance(String color) {
        int red = Integer.parseInt(color.substring(1, 3), 16);
        int green = Integer.parseInt(color.substring(3, 5), 16);
        int blue = Integer.parseInt(color.substring(5, 7), 16);
        return (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255f;
    }

    private SystemConfiguration configuration() {
        SystemConfiguration config = new SystemConfiguration();
        config.setRestaurantName("Quinta El Paraíso");
        return config;
    }
}
