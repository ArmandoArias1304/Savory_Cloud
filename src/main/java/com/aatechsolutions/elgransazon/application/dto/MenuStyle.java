package com.aatechsolutions.elgransazon.application.dto;

import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Style of the printed menu ("carta") that the restaurant hands to its guests.
 *
 * <p>It is the bridge between three worlds: the stored configuration
 * ({@link SystemConfiguration}, nullable columns with built-in defaults), the
 * customization dialog of the menu view (query parameters while previewing) and the
 * PDF generator ({@code MenuPdfService}), which only sees this immutable object.</p>
 *
 * <p>Everything here is defensive on purpose: unknown fonts, out-of-range sizes and
 * malformed colors fall back to the built-in design instead of breaking the print.</p>
 *
 * @param fontFamily         typography of titles, categories and body
 * @param fontSize           base body size in points (8-16); all other sizes scale from it
 * @param primaryColor       hex color (#rrggbb) of the title, category headers and prices
 * @param accentColor        hex color (#rrggbb) of descriptions, separators and the footer
 * @param pageColor          hex color (#rrggbb) painted as the sheet background; white means
 *                           "plain paper" and nothing is painted at all
 * @param paperSize          sheet size
 * @param columns            1 or 2 columns for the body
 * @param showDescriptions   print the short description under each dish name
 * @param showImages         print the dish photo (downloaded from its image URL)
 * @param showPrices         print prices (turn off for a table carta with no prices)
 * @param includeUnavailable include dishes flagged as "agotado"
 * @param showQr             print a QR code that opens the digital menu
 *
 * <p>The footer of the sheets is deliberately <strong>not</strong> part of the style: it is always
 * {@link #footerFor(String)} (the restaurant name plus the system brand) and the generator builds
 * it from the configuration, so it cannot be edited from the dialog nor stored stale.</p>
 */
public record MenuStyle(
        FontFamily fontFamily,
        int fontSize,
        String primaryColor,
        String accentColor,
        String pageColor,
        PaperSize paperSize,
        int columns,
        boolean showDescriptions,
        boolean showImages,
        boolean showPrices,
        boolean includeUnavailable,
        boolean showQr) {

    public static final int MIN_FONT_SIZE = 8;
    public static final int MAX_FONT_SIZE = 16;
    public static final int MAX_COLUMNS = 2;
    public static final int MAX_FOOTER_LENGTH = 60;

    /** System brand printed after the restaurant name in the footer of every sheet. */
    public static final String BRAND = "Savory Cloud";
    private static final String FOOTER_SUFFIX = " by " + BRAND;

    /**
     * Footer of every sheet: the restaurant name followed by the system brand, for example
     * "Quinta El Paraíso by Savory Cloud". It is fixed on purpose — the establishment cannot
     * rewrite it, because it doubles as the system's own signature — so the generator and the
     * dialog both build it from the configuration instead of storing it.
     */
    public static String footerFor(String restaurantName) {
        String name = restaurantName == null ? "" : restaurantName.trim();
        String footer = name.isEmpty() ? BRAND : name + FOOTER_SUFFIX;
        return footer.length() > MAX_FOOTER_LENGTH ? footer.substring(0, MAX_FOOTER_LENGTH) : footer;
    }

    /**
     * Typography options offered in the customization dialog.
     * MODERNA, CLASICA, REDONDA and ELEGANTE use the open fonts bundled in
     * {@code src/main/resources/fonts} (SIL Open Font License); ESTANDAR uses the
     * fourteen standard PDF fonts, which need no embedding at all.
     */
    public enum FontFamily {
        MODERNA("Moderna (Lato)", "Sans limpia, la más neutra"),
        CLASICA("Clásica (PT Serif)", "Serif elegante, tipo bistro"),
        REDONDA("Redonda (Varela Round)", "Redonda y amigable, informal"),
        ELEGANTE("Elegante (título manuscrito)", "Título y categorías manuscritos, cuerpo moderno"),
        ESTANDAR("Estándar (Helvetica)", "Sobria, sin adornos, la más segura al imprimir");

        private final String label;
        private final String description;

        FontFamily(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }

        /** Resolve a stored/requested value; unknown or blank falls back. */
        public static FontFamily parse(String raw, FontFamily fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            String value = raw.trim().toUpperCase(Locale.ROOT);
            for (FontFamily family : values()) {
                if (family.name().equals(value)) {
                    return family;
                }
            }
            return fallback;
        }
    }

    /** Sheet sizes offered in the customization dialog, with their printing margin. */
    public enum PaperSize {
        LETTER("Carta (Letter)", 612f, 792f, 36f),
        A4("A4", 595f, 842f, 36f),
        HALF_LETTER("Media carta", 396f, 612f, 28f),
        A5("A5 (cuartilla)", 420f, 595f, 28f);

        private final String label;
        private final float width;
        private final float height;
        private final float margin;

        PaperSize(String label, float width, float height, float margin) {
            this.label = label;
            this.width = width;
            this.height = height;
            this.margin = margin;
        }

        public String getLabel() {
            return label;
        }

        public float getWidth() {
            return width;
        }

        public float getHeight() {
            return height;
        }

        /** Printing margin in points (about 13 mm on Letter/A4, 10 mm on the small sheets). */
        public float getMargin() {
            return margin;
        }

        public static PaperSize parse(String raw, PaperSize fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            String value = raw.trim().toUpperCase(Locale.ROOT);
            for (PaperSize size : values()) {
                if (size.name().equals(value)) {
                    return size;
                }
            }
            return fallback;
        }
    }

    /**
     * Light sheet colors offered next to the picker. They are all pale on purpose: the text of
     * the carta is dark, so a dark "paper" would make the menu unreadable (and drink ink).
     */
    public static List<PageColorPreset> pageColorPresets() {
        return List.of(
                new PageColorPreset("Blanco", "#ffffff"),
                new PageColorPreset("Crema", "#fdf6e3"),
                new PageColorPreset("Arena", "#f6efe4"),
                new PageColorPreset("Menta", "#eef7f0"),
                new PageColorPreset("Cielo", "#eef4fb"),
                new PageColorPreset("Gris claro", "#f3f4f6"));
    }

    /** One swatch of the sheet color picker. */
    public record PageColorPreset(String label, String color) {
    }

    /** The built-in design: what a restaurant that never customized the carta gets. */
    public static MenuStyle defaults() {
        return new MenuStyle(
                FontFamily.parse(SystemConfiguration.DEFAULT_MENU_FONT_FAMILY, FontFamily.MODERNA),
                SystemConfiguration.DEFAULT_MENU_FONT_SIZE,
                SystemConfiguration.DEFAULT_MENU_PRIMARY_COLOR,
                SystemConfiguration.DEFAULT_MENU_ACCENT_COLOR,
                SystemConfiguration.DEFAULT_MENU_PAGE_COLOR,
                PaperSize.parse(SystemConfiguration.DEFAULT_MENU_PAPER_SIZE, PaperSize.LETTER),
                SystemConfiguration.DEFAULT_MENU_COLUMNS,
                true,
                false,
                true,
                false,
                false);
    }

    /**
     * Style stored for a company. The configuration getters already resolve null to
     * the built-in values, so this never fails and never returns null fields.
     */
    public static MenuStyle from(SystemConfiguration config) {
        if (config == null) {
            return defaults();
        }
        MenuStyle base = defaults();
        return new MenuStyle(
                FontFamily.parse(config.getMenuFontFamily(), base.fontFamily()),
                clamp(config.getMenuFontSize(), MIN_FONT_SIZE, MAX_FONT_SIZE, base.fontSize()),
                normalizeColor(config.getMenuPrimaryColor(), base.primaryColor()),
                normalizeColor(config.getMenuAccentColor(), base.accentColor()),
                normalizeColor(config.getMenuPageColor(), base.pageColor()),
                PaperSize.parse(config.getMenuPaperSize(), base.paperSize()),
                clamp(config.getMenuColumns(), 1, MAX_COLUMNS, base.columns()),
                config.getMenuShowDescriptions(),
                config.getMenuShowImages(),
                config.getMenuShowPrices(),
                config.getMenuIncludeUnavailable(),
                config.getMenuShowQr());
    }

    /**
     * Style built from the query parameters of a preview/print request. Missing values
     * keep the fallback (which is the saved style), so an empty parameter map prints
     * exactly what is stored. Unknown parameters (an old {@code footerText}, for instance)
     * are ignored.
     */
    public static MenuStyle fromParams(Map<String, String> params, MenuStyle fallback) {
        MenuStyle base = fallback == null ? defaults() : fallback;
        if (params == null || params.isEmpty()) {
            return base;
        }
        return new MenuStyle(
                FontFamily.parse(params.get("fontFamily"), base.fontFamily()),
                clamp(parseInt(params.get("fontSize"), base.fontSize()), MIN_FONT_SIZE, MAX_FONT_SIZE, base.fontSize()),
                normalizeColor(params.get("primaryColor"), base.primaryColor()),
                normalizeColor(params.get("accentColor"), base.accentColor()),
                normalizeColor(params.get("pageColor"), base.pageColor()),
                PaperSize.parse(params.get("paperSize"), base.paperSize()),
                clamp(parseInt(params.get("columns"), base.columns()), 1, MAX_COLUMNS, base.columns()),
                parseBoolean(params.get("showDescriptions"), base.showDescriptions()),
                parseBoolean(params.get("showImages"), base.showImages()),
                parseBoolean(params.get("showPrices"), base.showPrices()),
                parseBoolean(params.get("includeUnavailable"), base.includeUnavailable()),
                parseBoolean(params.get("showQr"), base.showQr()));
    }

    /**
     * Size of the restaurant name on the cover. It is deliberately much larger than the
     * body so moving the size slider is obvious at a glance.
     */
    public float coverTitleSize() {
        return fontSize + 22f;
    }

    /** Size of the slogan on the cover, scaled from the base body size. */
    public float coverSloganSize() {
        return fontSize + 2f;
    }

    /** Size of a category header, scaled from the base body size. */
    public float categorySize() {
        return fontSize + 4f;
    }

    /** Size of secondary text (descriptions, size variants, contact line, footer). */
    public float smallSize() {
        return Math.max(6.5f, fontSize - 2f);
    }

    /** Whether the sheet keeps the paper white, in which case nothing is painted behind the text. */
    public boolean hasTintedPaper() {
        return pageColor != null && !"#ffffff".equalsIgnoreCase(pageColor);
    }

    // ==================== helpers ====================

    /**
     * Keeps a value inside its supported range. Out-of-range values fall back to the
     * safe default instead of being silently truncated, so a bad stored value always
     * prints the built-in design rather than an unreadable one.
     */
    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "true", "on", "1", "yes", "si", "sí" -> true;
            case "false", "off", "0", "no" -> false;
            default -> fallback;
        };
    }

    /**
     * Accepts "#rrggbb", "#rgb" or the same without the hash, and returns lowercase
     * "#rrggbb". Anything else keeps the fallback, so a typo can never produce a PDF
     * with an unreadable color.
     */
    private static String normalizeColor(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim();
        if (!value.startsWith("#")) {
            value = "#" + value;
        }
        if (value.matches("^#[0-9a-fA-F]{3}$")) {
            char r = value.charAt(1);
            char g = value.charAt(2);
            char b = value.charAt(3);
            value = new StringBuilder("#").append(r).append(r).append(g).append(g).append(b).append(b).toString();
        }
        return value.matches("^#[0-9a-fA-F]{6}$") ? value.toLowerCase(Locale.ROOT) : fallback;
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
