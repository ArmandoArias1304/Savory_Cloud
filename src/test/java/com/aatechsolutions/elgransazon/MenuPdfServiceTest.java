package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.dto.MenuStyle;
import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.CloudflareImagesUrlHelper;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.application.service.MenuPdfService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.PathRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;
import com.itextpdf.kernel.pdf.canvas.parser.listener.ITextExtractionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the printed menu (carta) PDF: what prints, what is left out, the order of the
 * categories and that the page footer carries the brand on every page.
 *
 * <p>Assertions read the real generated PDF with {@code PdfTextExtractor}, so they cover
 * the whole pipeline (bundled fonts, colors, columns, header and footer) instead of only
 * the data preparation.</p>
 */
class MenuPdfServiceTest {

    private static final String RESTAURANT = "Quinta El Paraíso";

    private MenuPdfService service;
    private SystemConfigurationService systemConfigurationService;

    @BeforeEach
    void setUp() {
        systemConfigurationService = mock(SystemConfigurationService.class);
        CategoryService categoryService = mock(CategoryService.class);
        ItemMenuService itemMenuService = mock(ItemMenuService.class);
        CloudflareImagesUrlHelper imagesHelper = mock(CloudflareImagesUrlHelper.class);
        when(imagesHelper.transform(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        when(systemConfigurationService.getConfiguration()).thenReturn(configuration());
        when(categoryService.getAllActiveCategories()).thenReturn(categories());
        when(itemMenuService.findAllForPrintedMenu()).thenReturn(items());

        service = new MenuPdfService(systemConfigurationService, categoryService, itemMenuService, imagesHelper);
    }

    @Test
    void printsHeaderCategoriesDishesAndPrices() throws IOException {
        String text = textOf(service.generateMenuPdf(MenuStyle.defaults(), null));

        assertTrue(text.contains(RESTAURANT), "falta el nombre del restaurante");
        assertTrue(text.contains("Av. Reforma 123"), "falta la dirección");
        assertTrue(text.contains("Tel. 9611234567"), "falta el teléfono");
        assertTrue(text.contains("ENTRADAS") && text.contains("POSTRES") && text.contains("BEBIDAS"),
                "faltan los encabezados de categoría");
        assertTrue(text.contains("Ensalada César"), "falta un platillo");
        assertTrue(text.contains("$120.00"), "falta el precio del platillo");
        assertTrue(text.contains("$1,250.00"), "el precio con miles no usa separador");
        assertTrue(text.contains("Lechuga, pollo y aderezo"), "falta la descripción del platillo");
    }

    @Test
    void respectsTheCategoryOrderOfTheMenu() throws IOException {
        String text = textOf(service.generateMenuPdf(MenuStyle.defaults(), null));

        int postres = text.indexOf("POSTRES");
        int bebidas = text.indexOf("BEBIDAS");
        int entradas = text.indexOf("ENTRADAS");
        assertTrue(postres >= 0 && bebidas >= 0 && entradas >= 0, "faltan categorías en la carta");
        assertTrue(postres < bebidas && bebidas < entradas,
                "la carta no respeta el orden guardado de las categorías");
    }

    @Test
    void leavesOutInactiveDeletedAndUnavailableDishes() throws IOException {
        String text = textOf(service.generateMenuPdf(MenuStyle.defaults(), null));

        assertFalse(text.contains("Guacamole"), "se imprimió un platillo inactivo");
        assertFalse(text.contains("Totopos"), "se imprimió un platillo eliminado");
        assertFalse(text.contains("Papas a la francesa"), "se imprimió un platillo agotado");
    }

    @Test
    void includesUnavailableDishesWhenAskedFor() throws IOException {
        MenuStyle style = withDefaults().includeUnavailable(true).build();

        String text = textOf(service.generateMenuPdf(style, null));

        assertTrue(text.contains("Papas a la francesa"), "no se imprimió el platillo agotado aunque se pidió");
    }

    @Test
    void printsSizesOfADishAsOneLineWithTheirPrices() throws IOException {
        String text = textOf(service.generateMenuPdf(MenuStyle.defaults(), null));

        assertTrue(text.contains("Chico $70.00"), "falta el precio del tamaño chico");
        assertTrue(text.contains("Mediano $90.00"), "falta el precio del tamaño mediano");
        assertEquals(1, countOccurrences(text, "Chocoflan"),
                "el platillo con tamaños se imprimió más de una vez");
    }

    @Test
    void hidesPricesWhenTheStyleSaysSo() throws IOException {
        MenuStyle style = withDefaults().showPrices(false).build();

        String text = textOf(service.generateMenuPdf(style, null));

        assertFalse(text.contains("$120.00"), "se imprimieron precios aunque el estilo los desactiva");
        assertTrue(text.contains("Ensalada César"), "faltó el platillo sin precio");
        assertTrue(text.contains("Chico") && text.contains("Mediano"), "faltaron los tamaños sin precio");
        assertFalse(text.contains("Chico $70.00"), "los tamaños imprimieron precio");
    }

    @Test
    void hidesDescriptionsWhenTheStyleSaysSo() throws IOException {
        MenuStyle style = withDefaults().showDescriptions(false).build();

        String text = textOf(service.generateMenuPdf(style, null));

        assertFalse(text.contains("Lechuga, pollo y aderezo"), "se imprimió la descripción desactivada");
    }

    @Test
    void printsTheBrandFooterAndPageNumberOnEveryPage() throws IOException {
        // A long menu for sure: 60 platillos do not fit in one sheet of media carta.
        ItemMenuService manyItems = mock(ItemMenuService.class);
        when(manyItems.findAllForPrintedMenu()).thenReturn(manyDishes(60));
        MenuPdfService longMenuService = new MenuPdfService(systemConfigurationService,
                categoryServiceWithCategories(), manyItems, mock(CloudflareImagesUrlHelper.class));

        MenuStyle style = withDefaults().columns(2).paperSize(MenuStyle.PaperSize.HALF_LETTER).build();
        byte[] pdf = longMenuService.generateMenuPdf(style, null);

        int pages = pageCount(pdf);
        assertTrue(pages >= 2, "60 platillos no caben en una sola hoja");

        String text = textOf(pdf);
        assertEquals(pages, countOccurrences(text, "Savory Cloud"),
                "el pie con la marca no sale en todas las hojas");
        for (int page = 1; page <= pages; page++) {
            assertTrue(text.contains("Página " + page), "falta el número de página " + page);
        }
    }

    @Test
    void neverLeavesADishSplitBetweenTwoPages() throws IOException {
        List<ItemMenu> items = manyDishes(40);
        ItemMenuService manyItems = mock(ItemMenuService.class);
        when(manyItems.findAllForPrintedMenu()).thenReturn(items);
        MenuPdfService longMenuService = new MenuPdfService(systemConfigurationService,
                categoryServiceWithCategories(), manyItems, mock(CloudflareImagesUrlHelper.class));

        List<String> pages = pagesOf(longMenuService.generateMenuPdf(MenuStyle.defaults(), null));
        assertTrue(pages.size() >= 2, "se esperaban varias hojas");

        for (ItemMenu item : items) {
            int namePage = pageOf(pages, Pattern.compile(Pattern.quote(item.getName()) + "\\b"));
            int descriptionPage = pageOf(pages, Pattern.compile(Pattern.quote(item.getDescription()) + "\\b"));
            assertTrue(namePage > 0 && descriptionPage > 0, "no se encontró '" + item.getName() + "' en la carta");
            assertEquals(namePage, descriptionPage,
                    "el platillo '" + item.getName() + "' quedó partido entre dos hojas");
        }
    }

    @Test
    void printsATwoColumnCartaWithTheHeaderOnce() throws IOException {
        MenuStyle style = withDefaults().columns(2).build();

        List<String> pages = pagesOf(service.generateMenuPdf(style, null));

        // El nombre del restaurante sale una vez como título de portada (más el pie de esa
        // hoja), así que el encabezado no se repite al pasar a dos columnas.
        assertEquals(2, countOccurrences(pages.get(0), RESTAURANT),
                "la portada repitió el encabezado");
        assertTrue(pages.get(1).contains("Ensalada César") && pages.get(1).contains("Café americano"),
                "la carta a dos columnas perdió platillos");
    }

    /**
     * El pie no es editable: siempre es el nombre del restaurante con la marca del sistema, y
     * va en todas las hojas (antes era un texto que se podía reescribir desde el diálogo).
     */
    @Test
    void everySheetCarriesTheRestaurantBrandFooter() throws IOException {
        List<String> pages = pagesOf(service.generateMenuPdf(MenuStyle.defaults(), null));
        String footer = RESTAURANT + " by Savory Cloud";

        assertTrue(pages.size() >= 2, "la carta no tiene varias hojas");
        for (int page = 1; page <= pages.size(); page++) {
            assertEquals(1, countOccurrences(pages.get(page - 1), footer),
                    "la hoja " + page + " no lleva el pie con el nombre del restaurante");
        }
    }

    @Test
    void paintsTheChosenSheetColorOnEverySheet() throws IOException {
        MenuStyle style = withDefaults().pageColor("#fdf6e3").build();

        byte[] pdf = service.generateMenuPdf(style, null);
        int pages = pageCount(pdf);

        assertTrue(pages >= 2, "la carta no tiene varias hojas");
        for (int page = 1; page <= pages; page++) {
            List<Color> fills = filledColorsOf(pdf, page);
            assertEquals(1, fills.size(),
                    "la hoja " + page + " no tiene exactamente un fondo pintado");
            assertTrue(isColor(fills.get(0), 253, 246, 227),
                    "la hoja " + page + " no se pintó con el color de hoja elegido");
        }
    }

    /** Sin color de hoja el papel queda blanco: no se pinta nada detrás del texto. */
    @Test
    void paintsNothingWhenTheSheetStaysWhite() throws IOException {
        byte[] pdf = service.generateMenuPdf(MenuStyle.defaults(), null);

        for (int page = 1; page <= pageCount(pdf); page++) {
            assertTrue(filledColorsOf(pdf, page).isEmpty(),
                    "la hoja " + page + " se pintó aunque el papel es blanco");
        }
    }

    @Test
    void generatesAPdfWithEveryFontFamily() throws IOException {
        for (MenuStyle.FontFamily family : MenuStyle.FontFamily.values()) {
            MenuStyle style = withDefaults().fontFamily(family).build();

            byte[] pdf = service.generateMenuPdf(style, null);

            assertNotNull(pdf);
            assertTrue(pdf.length > 1000, "el PDF de " + family + " salió vacío");
            String text = textOf(pdf);
            assertTrue(text.contains(RESTAURANT), "el PDF de " + family + " no imprimió el nombre");
            assertTrue(text.contains("Ensalada César"), "el PDF de " + family + " no imprimió los platillos");
        }
    }

    @Test
    void printsTheDigitalMenuQrWhenAskedFor() throws IOException {
        MenuStyle style = withDefaults().showQr(true).build();

        String text = textOf(service.generateMenuPdf(style, "https://quinta.localhost:8080/home/menu"));

        assertTrue(text.contains("Menú digital"), "falta el texto del QR del menú digital");
        assertTrue(text.contains(RESTAURANT), "el encabezado con QR perdió el nombre del restaurante");
    }

    @Test
    void doesNotFailWhenTheLogoOrThePhotosCannotBeLoaded() throws IOException {
        when(systemConfigurationService.getConfiguration()).thenReturn(configurationWithBrokenLogo());

        byte[] pdf = service.generateMenuPdf(MenuStyle.defaults(), null);

        String text = textOf(pdf);
        assertTrue(text.contains(RESTAURANT), "un logo roto tumbó el encabezado");
        assertTrue(text.contains("Ensalada César"), "un logo roto tumbó la carta");
    }

    @Test
    void printsAMessageWhenThereIsNothingToPrint() throws IOException {
        ItemMenuService emptyItems = mock(ItemMenuService.class);
        when(emptyItems.findAllForPrintedMenu()).thenReturn(List.of());
        MenuPdfService emptyMenuService = new MenuPdfService(systemConfigurationService,
                mock(CategoryService.class), emptyItems, mock(CloudflareImagesUrlHelper.class));

        String text = textOf(emptyMenuService.generateMenuPdf(MenuStyle.defaults(), null));

        assertTrue(text.contains("todavía no tiene platillos activos"), "no avisó de una carta vacía");
    }

    @Test
    void printsACoverPageAndTheMenuStartsOnTheSecondPage() throws IOException {
        List<String> pages = pagesOf(service.generateMenuPdf(MenuStyle.defaults(), null));

        assertTrue(pages.size() >= 2, "la carta no tiene portada");
        String cover = pages.get(0);
        assertTrue(cover.contains(RESTAURANT) && cover.contains("Cocina de autor")
                        && cover.contains("Av. Reforma 123") && cover.contains("Tel. 9611234567"),
                "la portada no lleva el nombre, el eslogan ni los datos del restaurante");
        assertFalse(cover.contains("ENTRADAS") || cover.contains("POSTRES") || cover.contains("BEBIDAS"),
                "el menú empezó en la portada");
        assertFalse(cover.contains("Ensalada César"), "la portada llevó platillos");
        assertTrue(pages.get(1).contains("Ensalada César"), "el menú no empezó en la segunda hoja");
    }

    @Test
    void alignsEveryDishWithDotLeadersUpToItsPrice() throws IOException {
        String text = textOf(service.generateMenuPdf(MenuStyle.defaults(), null));

        assertTrue(Pattern.compile(Pattern.quote("Ensalada César") + " \\.{5,} \\$120\\.00")
                        .matcher(text).find(),
                "el nombre del platillo no queda enlazado con puntos hasta su precio");

        String withoutPrices = textOf(service.generateMenuPdf(withDefaults().showPrices(false).build(), null));
        assertFalse(withoutPrices.contains("....."), "se imprimieron puntos aunque los precios están ocultos");
    }

    @Test
    void printsTheDigitalMenuQrOnTheCoverOnlyWhenAskedFor() throws IOException {
        String url = "https://quinta.localhost:8080/home/menu";

        List<String> pages = pagesOf(service.generateMenuPdf(withDefaults().showQr(true).build(), url));
        assertTrue(pages.get(0).contains("Menú digital"), "el QR no salió en la portada");
        for (int page = 2; page <= pages.size(); page++) {
            assertFalse(pages.get(page - 1).contains("Menú digital"),
                    "el QR se repitió en la hoja " + page);
        }

        String withoutQr = textOf(service.generateMenuPdf(MenuStyle.defaults(), url));
        assertFalse(withoutQr.contains("Menú digital"), "se imprimió el QR aunque no se pidió");
    }

    @Test
    void aBiggerFontSizePrintsBiggerTextAndUsesMoreRoom() throws IOException {
        byte[] small = service.generateMenuPdf(withDefaults().fontSize(8).build(), null);
        byte[] big = service.generateMenuPdf(withDefaults().fontSize(16).build(), null);

        float smallTitle = printedFontSize(small, 1, RESTAURANT);
        float bigTitle = printedFontSize(big, 1, RESTAURANT);
        assertTrue(smallTitle > 0 && bigTitle > 0, "no se encontró el nombre en la portada");
        assertTrue(bigTitle > smallTitle,
                "el tamaño de la letra no cambió la portada: " + smallTitle + " -> " + bigTitle);

        List<String> smallPages = pagesOf(small);
        List<String> bigPages = pagesOf(big);
        int smallDishPage = pageOf(smallPages, Pattern.compile(Pattern.quote("Ensalada César")));
        int bigDishPage = pageOf(bigPages, Pattern.compile(Pattern.quote("Ensalada César")));
        assertTrue(smallDishPage > 0 && bigDishPage > 0, "no se encontró un platillo en la carta");
        assertTrue(printedFontSize(big, bigDishPage, "Ensalada César")
                        > printedFontSize(small, smallDishPage, "Ensalada César"),
                "el tamaño de la letra no cambió los platillos");
        assertTrue(bigPages.size() >= smallPages.size(), "una letra más grande no ocupó más hojas");
    }

    // ==================== fixtures ====================

    private CategoryService categoryServiceWithCategories() {
        CategoryService categoryService = mock(CategoryService.class);
        when(categoryService.getAllActiveCategories()).thenReturn(categories());
        return categoryService;
    }

    /** Long list of dishes spread over the three categories, used to force several pages. */
    private List<ItemMenu> manyDishes(int count) {
        List<Category> categories = categories();
        List<ItemMenu> items = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Category category = categories.get(index % categories.size());
            items.add(item(1000L + index, "Platillo " + (index + 1), "99.00", category,
                    true, true, false, true, "Descripción del platillo " + (index + 1)));
        }
        return items;
    }

    private SystemConfiguration configuration() {
        SystemConfiguration config = new SystemConfiguration();
        config.setRestaurantName(RESTAURANT);
        config.setSlogan("Cocina de autor");
        config.setAddress("Av. Reforma 123");
        config.setPhone("9611234567");
        config.setEmail("hola@quinta.mx");
        config.setTaxRate(new BigDecimal("16.00"));
        return config;
    }

    private SystemConfiguration configurationWithBrokenLogo() {
        SystemConfiguration config = configuration();
        config.setRestaurantLogoUrl("https://example.invalid/logo.png");
        return config;
    }

    private List<Category> categories() {
        List<Category> categories = new ArrayList<>();
        categories.add(category(1L, "Postres", "Dulce final"));
        categories.add(category(2L, "Bebidas", null));
        categories.add(category(3L, "Entradas", null));
        return categories;
    }

    private Category category(Long id, String name, String description) {
        return Category.builder()
                .idCategory(id)
                .name(name)
                .description(description)
                .active(true)
                .build();
    }

    private List<ItemMenu> items() {
        List<ItemMenu> items = new ArrayList<>();

        // Entradas (category 3): one plato fuerte de precio alto, una activa con descripción,
        // una inactiva, una eliminada y una agotada.
        items.add(item(100L, "Ensalada César", "120.00", categories().get(2),
                true, true, false, true, "Lechuga, pollo y aderezo"));
        items.add(item(101L, "Corte rib eye", "1250.00", categories().get(2), true, true, false, true, null));
        items.add(item(102L, "Guacamole", "95.00", categories().get(2), false, true, false, true, null));
        items.add(item(103L, "Totopos", "60.00", categories().get(2), true, true, true, true, null));
        items.add(item(104L, "Papas a la francesa", "70.00", categories().get(2), true, false, false, true, null));

        // Postres (category 1): un platillo con dos tamaños.
        ItemMenu chocoflan = item(200L, "Chocoflan", "60.00", categories().get(0), true, true, false, true, null);
        items.add(chocoflan);
        items.add(sizeItem(201L, "Chocoflan chico", "70.00", chocoflan, "Chico"));
        items.add(sizeItem(202L, "Chocoflan mediano", "90.00", chocoflan, "Mediano"));

        // Bebidas (category 2)
        items.add(item(300L, "Café americano", "35.00", categories().get(1), true, true, false, true, null));

        return items;
    }

    private ItemMenu item(Long id, String name, String price, Category category, boolean active,
                          boolean available, boolean deleted, boolean requiresPreparation, String description) {
        return ItemMenu.builder()
                .idItemMenu(id)
                .name(name)
                .description(description)
                .price(new BigDecimal(price))
                .category(category)
                .active(active)
                .available(available)
                .deleted(deleted)
                .requiresPreparation(requiresPreparation)
                .build();
    }

    private ItemMenu sizeItem(Long id, String name, String price, ItemMenu parent, String sizeName) {
        return ItemMenu.builder()
                .idItemMenu(id)
                .name(name)
                .price(new BigDecimal(price))
                .category(parent.getCategory())
                .parentItem(parent)
                .sizeName(sizeName)
                .active(true)
                .available(true)
                .deleted(false)
                .build();
    }

    /** Builder seeded with the built-in style so tests only override what they care about. */
    private MenuStyleBuilder withDefaults() {
        return new MenuStyleBuilder(MenuStyle.defaults());
    }

    // ==================== pdf helpers ====================

    private static String textOf(byte[] pdfBytes) throws IOException {
        PdfDocument pdf = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdfBytes)));
        StringBuilder text = new StringBuilder();
        try {
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                text.append(PdfTextExtractor.getTextFromPage(pdf.getPage(page))).append('\n');
            }
        } finally {
            pdf.close();
        }
        return text.toString();
    }

    /** Text of each page, in order (index 0 is page 1). */
    private static List<String> pagesOf(byte[] pdfBytes) throws IOException {
        PdfDocument pdf = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdfBytes)));
        List<String> pages = new ArrayList<>();
        try {
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                pages.add(PdfTextExtractor.getTextFromPage(pdf.getPage(page)));
            }
        } finally {
            pdf.close();
        }
        return pages;
    }

    /** 1-based number of the first page whose text matches, or 0 when it does not appear. */
    private static int pageOf(List<String> pages, Pattern pattern) {
        for (int index = 0; index < pages.size(); index++) {
            if (pattern.matcher(pages.get(index)).find()) {
                return index + 1;
            }
        }
        return 0;
    }

    /** Font size actually used to draw a piece of text on a page, read from the PDF content. */
    private static float printedFontSize(byte[] pdfBytes, int pageNumber, String text) throws IOException {
        PdfDocument pdf = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdfBytes)));
        try {
            FontSizeProbe probe = new FontSizeProbe();
            new PdfCanvasProcessor(probe).processPageContent(pdf.getPage(pageNumber));
            return probe.sizeOf(text);
        } finally {
            pdf.close();
        }
    }

    /** Collects the font size of every text run of a page, to prove the slider really scales. */
    private static final class FontSizeProbe implements ITextExtractionStrategy {

        private final Map<String, Float> sizes = new HashMap<>();

        @Override
        public void eventOccurred(IEventData data, EventType type) {
            if (type == EventType.RENDER_TEXT) {
                TextRenderInfo info = (TextRenderInfo) data;
                sizes.put(info.getText().trim(), info.getFontSize());
            }
        }

        @Override
        public String getResultantText() {
            return "";
        }

        /** Null means every event is of interest. */
        @Override
        public Set<EventType> getSupportedEvents() {
            return null;
        }

        private float sizeOf(String text) {
            return sizes.getOrDefault(text, -1f);
        }
    }

    /**
     * Colors actually used to fill a path on a page, read from the real content stream. The
     * carta fills nothing else, so this is exactly the painted sheet background.
     */
    private static List<Color> filledColorsOf(byte[] pdfBytes, int pageNumber) throws IOException {
        PdfDocument pdf = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdfBytes)));
        try {
            List<Color> colors = new ArrayList<>();
            new PdfCanvasProcessor(new IEventListener() {
                @Override
                public void eventOccurred(IEventData data, EventType type) {
                    if (type == EventType.RENDER_PATH && data instanceof PathRenderInfo path
                            && path.getOperation() == PathRenderInfo.FILL && path.getFillColor() != null) {
                        colors.add(path.getFillColor());
                    }
                }

                @Override
                public Set<EventType> getSupportedEvents() {
                    return Set.of(EventType.RENDER_PATH);
                }
            }).processPageContent(pdf.getPage(pageNumber));
            return colors;
        } finally {
            pdf.close();
        }
    }

    private static boolean isColor(Color actual, int red, int green, int blue) {
        if (!(actual instanceof DeviceRgb rgb)) {
            return false;
        }
        float[] expected = new DeviceRgb(red, green, blue).getColorValue();
        float[] values = rgb.getColorValue();
        for (int index = 0; index < values.length; index++) {
            if (Math.abs(values[index] - expected[index]) > 0.01f) {
                return false;
            }
        }
        return true;
    }

    private static int pageCount(byte[] pdfBytes) throws IOException {
        PdfDocument pdf = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdfBytes)));
        try {
            return pdf.getNumberOfPages();
        } finally {
            pdf.close();
        }
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }

    /** Small builder so each test overrides only the style field it exercises. */
    private static final class MenuStyleBuilder {
        private MenuStyle.FontFamily fontFamily;
        private int fontSize;
        private String primaryColor;
        private String accentColor;
        private String pageColor;
        private MenuStyle.PaperSize paperSize;
        private int columns;
        private boolean showDescriptions;
        private boolean showImages;
        private boolean showPrices;
        private boolean includeUnavailable;
        private boolean showQr;

        private MenuStyleBuilder(MenuStyle base) {
            this.fontFamily = base.fontFamily();
            this.fontSize = base.fontSize();
            this.primaryColor = base.primaryColor();
            this.accentColor = base.accentColor();
            this.pageColor = base.pageColor();
            this.paperSize = base.paperSize();
            this.columns = base.columns();
            this.showDescriptions = base.showDescriptions();
            this.showImages = base.showImages();
            this.showPrices = base.showPrices();
            this.includeUnavailable = base.includeUnavailable();
            this.showQr = base.showQr();
        }

        private MenuStyleBuilder fontFamily(MenuStyle.FontFamily value) {
            this.fontFamily = value;
            return this;
        }

        private MenuStyleBuilder fontSize(int value) {
            this.fontSize = value;
            return this;
        }

        private MenuStyleBuilder paperSize(MenuStyle.PaperSize value) {
            this.paperSize = value;
            return this;
        }

        private MenuStyleBuilder pageColor(String value) {
            this.pageColor = value;
            return this;
        }

        private MenuStyleBuilder columns(int value) {
            this.columns = value;
            return this;
        }

        private MenuStyleBuilder showDescriptions(boolean value) {
            this.showDescriptions = value;
            return this;
        }

        private MenuStyleBuilder showPrices(boolean value) {
            this.showPrices = value;
            return this;
        }

        private MenuStyleBuilder includeUnavailable(boolean value) {
            this.includeUnavailable = value;
            return this;
        }

        private MenuStyleBuilder showQr(boolean value) {
            this.showQr = value;
            return this;
        }

        private MenuStyle build() {
            return new MenuStyle(fontFamily, fontSize, primaryColor, accentColor, pageColor, paperSize, columns,
                    showDescriptions, showImages, showPrices, includeUnavailable, showQr);
        }
    }
}
