package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.application.dto.MenuStyle;
import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.util.QrCodeGenerator;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.kernel.utils.PdfMerger;
import com.itextpdf.layout.ColumnDocumentRenderer;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates the printed menu ("carta") that the restaurant hands to its guests.
 *
 * <p>Structure: <strong>page 1 is the cover</strong> (logo, restaurant name, slogan, address
 * and, when enabled, the QR to the digital menu) and the menu starts on <strong>page 2</strong>,
 * grouped by category in the order stored through the drag &amp; drop of the categories view.
 * Every page is painted with the sheet color and carries a small footer with the restaurant
 * brand ("Restaurante by Savory Cloud", fixed) and "Página X de N".</p>
 *
 * <p>How it is built, and why:</p>
 * <ul>
 *   <li>The cover and the menu are generated as two documents and merged with
 *       {@link PdfMerger}. iText keeps one renderer per document, so the cover could not be
 *       laid out full width and the two-column menu in the same pass: switching renderers
 *       mid document made the columns land on top of the cover. Two documents solve it while
 *       keeping each one simple (and the footers, which need the total page count, are
 *       stamped after the merge).</li>
 *   <li>Prices are aligned with dot leaders ("Ensalada .......... $160.00") computed from the
 *       real font metrics, so the dots always end right where the price starts; a dish with a
 *       very long name simply prints without leaders.</li>
 *   <li>The logo is fitted with {@code scaleToFit}, which keeps the aspect ratio, so banners
 *       and square logos both print complete.</li>
 *   <li>Size variants of a dish (chico / mediano / grande) print as one extra line under their
 *       parent instead of as separate dishes, with all of their prices.</li>
 *   <li>The sheet color is painted on the merged document, in the same content stream as the
 *       footer and before the rest of the page, so it always stays behind the text; plain white
 *       is skipped because paper is already white.</li>
 *   <li>Nothing here writes to the database: it only reads configuration, categories and items,
 *       and availability is <em>not</em> recalculated, so printing a carta never changes the
 *       menu.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MenuPdfService {

    // Bundled open fonts (SIL Open Font License). See src/main/resources/fonts.
    private static final String LATO_REGULAR = "/fonts/lato/Lato-Regular.ttf";
    private static final String LATO_BOLD = "/fonts/lato/Lato-Bold.ttf";
    private static final String PT_SERIF_REGULAR = "/fonts/ptserif/PT_Serif-Web-Regular.ttf";
    private static final String PT_SERIF_BOLD = "/fonts/ptserif/PT_Serif-Web-Bold.ttf";
    private static final String VARELA_ROUND = "/fonts/varelaround/VarelaRound-Regular.ttf";
    private static final String PACIFICO = "/fonts/pacifico/Pacifico-Regular.ttf";

    /** Dish names stay dark neutral so the carta reads well in black and white. */
    private static final DeviceRgb NAME_COLOR = new DeviceRgb(31, 41, 55);

    private static final float COLUMN_GAP = 22f;
    private static final float PHOTO_WIDTH = 40f;
    private static final String VARIANT_SEPARATOR = "  ·  ";

    /** Name column share of the body width: with prices, without prices, with photos. */
    private static final float NAME_SHARE_WITH_PRICE = 0.76f;
    private static final float NAME_SHARE_WITH_PHOTO = 0.63f;
    private static final float PHOTO_SHARE = 0.11f;
    private static final float PRICE_SHARE = 0.24f;

    /** Room left before the cell edge so the dots never wrap to the next line. */
    private static final float LEADER_SAFETY = 6f;
    /** Fewer dots than this look like a mistake, so no leaders are drawn at all. */
    private static final int MIN_LEADER_DOTS = 3;

    /** Font files read from the classpath once per JVM; a PdfFont is built per document. */
    private static final Map<String, byte[]> FONT_BYTES = new ConcurrentHashMap<>();

    private final SystemConfigurationService systemConfigurationService;
    private final CategoryService categoryService;
    private final ItemMenuService itemMenuService;
    private final CloudflareImagesUrlHelper cloudflareImagesUrlHelper;

    /**
     * Builds the PDF carta of the current company.
     *
     * @param style          typography, size, colors, paper and content toggles
     * @param digitalMenuUrl URL encoded in the QR when the style asks for it (nullable)
     * @return the PDF bytes
     * @throws IOException if any of the documents cannot be written
     */
    public byte[] generateMenuPdf(MenuStyle style, String digitalMenuUrl) throws IOException {
        MenuStyle effective = style == null ? MenuStyle.defaults() : style;
        SystemConfiguration config = systemConfigurationService.getConfiguration();

        log.info("Generating menu PDF: font={}, size={}, paper={}, columns={}",
                effective.fontFamily(), effective.fontSize(), effective.paperSize(), effective.columns());

        MenuContent content = groupContent(itemMenuService.findAllForPrintedMenu(), effective);
        List<Category> categories = categoryService.getAllActiveCategories();

        DeviceRgb primary = rgb(effective.primaryColor(), new DeviceRgb(31, 41, 55));
        DeviceRgb accent = rgb(effective.accentColor(), new DeviceRgb(107, 114, 128));
        DeviceRgb softAccent = mixWithWhite(accent, 0.55f);
        DeviceRgb sheet = rgb(effective.pageColor(), new DeviceRgb(255, 255, 255));

        byte[] cover = buildCover(effective, config, digitalMenuUrl, primary, accent, softAccent);
        byte[] body = buildBody(effective, content, categories, primary, accent, softAccent);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfDocument merged = new PdfDocument(new PdfWriter(out));
        PdfDocument coverSource = new PdfDocument(new PdfReader(new ByteArrayInputStream(cover)));
        PdfDocument bodySource = new PdfDocument(new PdfReader(new ByteArrayInputStream(body)));
        try {
            PdfMerger merger = new PdfMerger(merged).setCloseSourceDocuments(false);
            merger.merge(coverSource, 1, coverSource.getNumberOfPages());
            merger.merge(bodySource, 1, bodySource.getNumberOfPages());
            // El pie sale de la configuración, no del estilo: así la firma del sistema no se
            // puede reescribir desde el diálogo y nunca queda guardada desactualizada.
            stampSheetColorAndFooters(merged, effective, softAccent, sheet,
                    MenuStyle.footerFor(config == null ? null : config.getRestaurantName()));
            merger.close();
        } finally {
            coverSource.close();
            bodySource.close();
            if (!merged.isClosed()) {
                merged.close();
            }
        }
        return out.toByteArray();
    }

    // ==================== document 1: the cover (page 1) ====================

    private byte[] buildCover(MenuStyle style, SystemConfiguration config, String digitalMenuUrl,
                              DeviceRgb primary, DeviceRgb accent, DeviceRgb softAccent) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
        pdfDoc.setDefaultPageSize(pageSize(style));

        float margin = style.paperSize().getMargin();
        Document document = new Document(pdfDoc);
        document.setMargins(margin, margin, margin + footerRoom(style), margin);

        Fonts fonts = loadFonts(style.fontFamily());
        float contentWidth = style.paperSize().getWidth() - 2 * margin;

        Div cover = new Div();
        cover.setTextAlignment(TextAlignment.CENTER);

        // Air on top so the cover sits in the middle of the sheet. It is smaller when the QR
        // block is printed, because the QR already fills the lower half.
        boolean withQr = style.showQr() && digitalMenuUrl != null && !digitalMenuUrl.isBlank();
        cover.add(spacer(style.paperSize().getHeight() * (withQr ? 0.07f : 0.16f)));

        Image logo = loadLogo(config, style, contentWidth);
        if (logo != null) {
            logo.setHorizontalAlignment(HorizontalAlignment.CENTER);
            logo.setMarginBottom(style.fontSize() * 1.6f);
            cover.add(logo);
        }

        cover.add(new Paragraph(config == null ? "" : nullSafe(config.getRestaurantName()))
                .setFont(fonts.titles())
                .setFontSize(style.coverTitleSize())
                .setFontColor(primary)
                .setTextAlignment(TextAlignment.CENTER)
                .setMargin(0));

        if (config != null && notBlank(config.getSlogan())) {
            cover.add(new Paragraph(config.getSlogan().trim())
                    .setFont(fonts.regular())
                    .setFontSize(style.coverSloganSize())
                    .setFontColor(accent)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(style.fontSize() * 0.5f));
        }

        cover.add(new LineSeparator(new SolidLine(1.1f))
                .setStrokeColor(primary)
                .setWidth(UnitValue.createPercentValue(45))
                .setHorizontalAlignment(HorizontalAlignment.CENTER)
                .setMarginTop(style.fontSize() * 1.6f)
                .setMarginBottom(style.fontSize() * 1.3f));

        String contact = contactLine(config);
        if (!contact.isBlank()) {
            cover.add(new Paragraph(contact)
                    .setFont(fonts.regular())
                    .setFontSize(style.fontSize())
                    .setFontColor(accent)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMargin(0));
        }

        if (withQr) {
            cover.add(spacer(style.paperSize().getHeight() * 0.05f));
            cover.add(buildQrBlock(digitalMenuUrl, fonts, style, softAccent,
                    Math.min(contentWidth * 0.24f, 140f)));
        }

        document.add(cover);
        document.close();
        return baos.toByteArray();
    }

    private Div buildQrBlock(String url, Fonts fonts, MenuStyle style, DeviceRgb color, float width) {
        Div block = new Div();
        block.setTextAlignment(TextAlignment.CENTER);
        try {
            Image qr = new Image(ImageDataFactory.create(QrCodeGenerator.png(url, 320)));
            qr.scaleToFit(Math.max(60f, width), Math.max(60f, width));
            qr.setHorizontalAlignment(HorizontalAlignment.CENTER);
            block.add(qr);
        } catch (Exception e) {
            log.warn("Could not generate the digital menu QR: {}", e.getMessage());
        }
        block.add(new Paragraph("Menú digital")
                .setFont(fonts.bold())
                .setFontSize(Math.max(7f, style.smallSize()))
                .setFontColor(color)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(5f)
                .setMarginBottom(0));
        block.add(new Paragraph("Escanéame para verlo en tu celular")
                .setFont(fonts.regular())
                .setFontSize(Math.max(6f, style.smallSize() - 1f))
                .setFontColor(color)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(1f));
        return block;
    }

    /**
     * Loads the restaurant logo and fits it inside a box without cropping it: the image keeps
     * its aspect ratio, so a banner and a square logo both come out complete.
     */
    private Image loadLogo(SystemConfiguration config, MenuStyle style, float contentWidth) {
        String logoUrl = config == null ? null : config.getRestaurantLogoUrl();
        if (logoUrl == null || logoUrl.isBlank()) {
            return null;
        }
        try {
            String pdfLogoUrl = cloudflareImagesUrlHelper.transform(logoUrl,
                    "w=800,h=800,fit=contain,format=png,quality=90");
            Image logo = new Image(ImageDataFactory.create(new java.net.URL(pdfLogoUrl)));
            logo.scaleToFit(contentWidth * 0.6f, style.paperSize().getHeight() * 0.24f);
            return logo;
        } catch (Exception e) {
            log.warn("Could not load the restaurant logo for the carta: {}", e.getMessage());
            return null;
        }
    }

    private String contactLine(SystemConfiguration config) {
        List<String> parts = new ArrayList<>();
        if (config == null) {
            return "";
        }
        if (notBlank(config.getAddress())) {
            parts.add(config.getAddress().trim());
        }
        if (notBlank(config.getPhone())) {
            parts.add("Tel. " + config.getPhone().trim());
        }
        return String.join("  ·  ", parts);
    }

    // ==================== document 2: the menu (from page 2) ====================

    private byte[] buildBody(MenuStyle style, MenuContent content, List<Category> categories,
                             DeviceRgb primary, DeviceRgb accent, DeviceRgb softAccent) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
        pdfDoc.setDefaultPageSize(pageSize(style));

        float margin = style.paperSize().getMargin();
        float footerRoom = footerRoom(style);
        Document document = new Document(pdfDoc);
        document.setMargins(margin, margin, margin + footerRoom, margin);

        Fonts fonts = loadFonts(style.fontFamily());
        boolean twoColumns = style.columns() > 1;
        if (twoColumns) {
            document.setRenderer(new ColumnDocumentRenderer(document, columnAreas(style, margin, footerRoom)));
        }

        float contentWidth = style.paperSize().getWidth() - 2 * margin;
        float columnWidth = twoColumns ? (contentWidth - COLUMN_GAP) / 2f : contentWidth;

        boolean printedSomething = false;
        for (Category category : categories) {
            List<ItemMenu> categoryItems = content.itemsByCategory().get(category.getIdCategory());
            if (categoryItems == null || categoryItems.isEmpty()) {
                continue;
            }
            addCategoryHeader(document, fonts, style, category, primary, softAccent);
            addItems(document, fonts, style, categoryItems, content.variantsByParent(),
                    columnWidth, primary, accent, softAccent);
            printedSomething = true;
        }

        if (!printedSomething) {
            document.add(new Paragraph("El menú todavía no tiene platillos activos para imprimir.")
                    .setFont(fonts.regular())
                    .setFontSize(style.fontSize())
                    .setFontColor(accent)
                    .setMarginTop(12));
        }

        document.close();
        return baos.toByteArray();
    }

    private void addCategoryHeader(Document document, Fonts fonts, MenuStyle style, Category category,
                                   DeviceRgb primary, DeviceRgb softAccent) {
        String name = nullSafe(category.getName());
        if (style.fontFamily() != MenuStyle.FontFamily.ELEGANTE) {
            name = name.toUpperCase(new Locale("es", "MX"));
        }
        document.add(new Paragraph(name)
                .setFont(fonts.titles())
                .setFontSize(style.categorySize())
                .setFontColor(primary)
                .setMarginTop(style.fontSize() * 0.9f)
                .setMarginBottom(1f)
                .setKeepWithNext(true));

        if (style.showDescriptions() && notBlank(category.getDescription())) {
            document.add(new Paragraph(category.getDescription().trim())
                    .setFont(fonts.regular())
                    .setFontSize(style.smallSize())
                    .setFontColor(softAccent)
                    .setMarginTop(0)
                    .setMarginBottom(3f)
                    .setKeepWithNext(true));
        }

        document.add(new LineSeparator(new SolidLine(0.5f))
                .setStrokeColor(softAccent)
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(1f)
                .setMarginBottom(style.fontSize() * 0.35f));
    }

    private void addItems(Document document, Fonts fonts, MenuStyle style, List<ItemMenu> items,
                          Map<Long, List<ItemMenu>> variantsByParent, float columnWidth,
                          DeviceRgb primary, DeviceRgb accent, DeviceRgb softAccent) {
        List<Image> photos = new ArrayList<>();
        if (style.showImages()) {
            for (ItemMenu item : items) {
                photos.add(loadItemImage(item));
            }
        }
        boolean withPhotos = photos.stream().anyMatch(photo -> photo != null);

        float[] widths = withPhotos
                ? new float[] {PHOTO_SHARE * 100f, NAME_SHARE_WITH_PHOTO * 100f, PRICE_SHARE * 100f}
                : style.showPrices()
                        ? new float[] {NAME_SHARE_WITH_PRICE * 100f, PRICE_SHARE * 100f}
                        : new float[] {100f};

        // Width actually available for the name, so the dot leaders end exactly where the
        // price column starts.
        float nameWidth = columnWidth * widths[0] / 100f;

        Table table = new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth();
        table.setMarginBottom(style.fontSize() * 0.4f);

        for (int index = 0; index < items.size(); index++) {
            ItemMenu item = items.get(index);
            List<ItemMenu> variants = variantsByParent.get(item.getIdItemMenu());

            if (withPhotos) {
                Image photo = photos.isEmpty() ? null : photos.get(index);
                Cell photoCell = borderlessCell().setVerticalAlignment(VerticalAlignment.TOP);
                if (photo != null) {
                    photoCell.add(photo);
                }
                table.addCell(photoCell);
            }

            Cell nameCell = borderlessCell()
                    .setVerticalAlignment(VerticalAlignment.TOP)
                    .setKeepTogether(true);
            nameCell.add(leaderParagraph(fonts, style, item, nameWidth, softAccent));

            if (style.showDescriptions() && notBlank(item.getDescription())) {
                nameCell.add(new Paragraph(item.getDescription().trim())
                        .setFont(fonts.regular())
                        .setFontSize(style.smallSize())
                        .setFontColor(accent)
                        .setMargin(0)
                        .setMarginBottom(1f));
            }

            String variantsLine = variantsLine(style, variants);
            if (!variantsLine.isBlank()) {
                nameCell.add(new Paragraph(variantsLine)
                        .setFont(fonts.regular())
                        .setFontSize(style.smallSize())
                        .setFontColor(accent)
                        .setMargin(0));
            }
            table.addCell(nameCell);

            if (style.showPrices()) {
                table.addCell(borderlessCell()
                        .setVerticalAlignment(VerticalAlignment.TOP)
                        .add(new Paragraph(money(item.getPrice()))
                                .setFont(fonts.bold())
                                .setFontSize(style.fontSize())
                                .setFontColor(primary)
                                .setTextAlignment(TextAlignment.RIGHT)
                                .setMargin(0)));
            }
        }
        document.add(table);
    }

    /**
     * Dish name followed by dot leaders up to the price column: the classic printed menu look
     * ("Ensalada César ............ $160.00"). The number of dots comes from the real font
     * metrics and a dish whose name is too long prints without leaders instead of breaking
     * the line.
     */
    private Paragraph leaderParagraph(Fonts fonts, MenuStyle style, ItemMenu item, float nameWidth,
                                      DeviceRgb softAccent) {
        Paragraph paragraph = new Paragraph()
                .setMargin(0)
                .setMarginBottom(1f)
                .add(new Text(nullSafe(item.getName()))
                        .setFont(fonts.bold())
                        .setFontSize(style.fontSize())
                        .setFontColor(NAME_COLOR));

        if (!style.showPrices()) {
            return paragraph;
        }

        int dots = leaderDots(fonts, style, nullSafe(item.getName()), nameWidth);
        if (dots > 0) {
            paragraph.add(new Text(" " + ".".repeat(dots))
                    .setFont(fonts.regular())
                    .setFontSize(style.fontSize())
                    .setFontColor(softAccent));
        }
        return paragraph;
    }

    private int leaderDots(Fonts fonts, MenuStyle style, String name, float nameWidth) {
        float dotWidth = fonts.regular().getWidth(".", style.fontSize());
        if (dotWidth <= 0) {
            return 0;
        }
        float nameTextWidth = fonts.bold().getWidth(name, style.fontSize());
        float spaceWidth = fonts.regular().getWidth(" ", style.fontSize());
        float free = nameWidth - nameTextWidth - spaceWidth - LEADER_SAFETY;
        int dots = (int) Math.floor(free / dotWidth);
        return dots >= MIN_LEADER_DOTS ? dots : 0;
    }

    /**
     * One line with every size of the dish, e.g. "Chico $80.00  ·  Mediano $95.00".
     * Sizes print with their price because in this model each size is its own item.
     */
    private String variantsLine(MenuStyle style, List<ItemMenu> variants) {
        if (variants == null || variants.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>(variants.size());
        for (ItemMenu variant : variants) {
            String label = notBlank(variant.getSizeName())
                    ? variant.getSizeName().trim()
                    : nullSafe(variant.getName());
            parts.add(style.showPrices() ? label + " " + money(variant.getPrice()) : label);
        }
        return String.join(VARIANT_SEPARATOR, parts);
    }

    private Image loadItemImage(ItemMenu item) {
        if (!notBlank(item.getImageUrl())) {
            return null;
        }
        try {
            String url = cloudflareImagesUrlHelper.transform(item.getImageUrl(),
                    "w=160,h=160,fit=cover,format=png,quality=80");
            Image photo = new Image(ImageDataFactory.create(new java.net.URL(url)));
            photo.scaleToFit(PHOTO_WIDTH, PHOTO_WIDTH);
            photo.setMarginRight(4f);
            return photo;
        } catch (Exception e) {
            log.debug("Skipping dish photo for '{}': {}", item.getName(), e.getMessage());
            return null;
        }
    }

    // ==================== footers ====================

    /**
     * Stamps what belongs to every sheet of the merged document: the sheet color (drawn first,
     * so it stays behind the text), the footer with the restaurant brand centered and
     * "Página X de N" at the right edge, over a hairline rule in the carta's accent color.
     *
     * <p>It runs after the merge because only then the total page count is known, and it draws
     * the sheet color and the footer in a single content stream so the order is guaranteed: the
     * background can never end up covering the text.</p>
     */
    private void stampSheetColorAndFooters(PdfDocument pdfDoc, MenuStyle style, DeviceRgb color, DeviceRgb sheet,
                                           String footerText) {
        int totalPages = pdfDoc.getNumberOfPages();
        float margin = style.paperSize().getMargin();
        float fontSize = Math.max(6.5f, style.smallSize() * 0.9f);
        PdfFont font;
        try {
            font = loadFonts(style.fontFamily()).regular();
        } catch (RuntimeException e) {
            log.warn("Could not load a font for the carta footer: {}", e.getMessage());
            return;
        }

        for (int pageNumber = 1; pageNumber <= totalPages; pageNumber++) {
            PdfPage page = pdfDoc.getPage(pageNumber);
            Rectangle size = page.getPageSize();
            float baseline = margin + fontSize * 0.9f;
            float ruleY = baseline + fontSize * 1.5f;

            PdfCanvas canvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdfDoc);
            canvas.saveState();

            // Tinted sheet: filled before anything else of this stream. Plain white is skipped
            // because the paper is already white and it would only add ink and bytes.
            if (style.hasTintedPaper()) {
                canvas.setFillColor(sheet);
                canvas.rectangle(0, 0, size.getWidth(), size.getHeight());
                canvas.fill();
            }

            canvas.setLineWidth(0.4f);
            canvas.setStrokeColor(color);
            canvas.moveTo(margin, ruleY);
            canvas.lineTo(size.getWidth() - margin, ruleY);
            canvas.stroke();

            canvas.setFillColor(color);
            canvas.beginText();
            canvas.setFontAndSize(font, fontSize);
            canvas.moveText((size.getWidth() - font.getWidth(footerText, fontSize)) / 2f, baseline);
            canvas.showText(footerText);
            canvas.endText();

            String pageLabel = "Página " + pageNumber + " de " + totalPages;
            canvas.beginText();
            canvas.setFontAndSize(font, fontSize);
            canvas.moveTo(size.getWidth() - margin - font.getWidth(pageLabel, fontSize), baseline);
            canvas.showText(pageLabel);
            canvas.endText();
            canvas.restoreState();
        }
    }

    // ==================== content preparation ====================

    /**
     * Groups the company items into what actually prints: only active, non-deleted dishes
     * (plus unavailable ones when the style asks for them), grouped by category and with the
     * size variants kept aside to be printed under their parent.
     */
    private MenuContent groupContent(List<ItemMenu> items, MenuStyle style) {
        Map<Long, List<ItemMenu>> itemsByCategory = new LinkedHashMap<>();
        Map<Long, List<ItemMenu>> variantsByParent = new HashMap<>();
        Set<Long> parentsToPrint = new HashSet<>();

        if (items != null) {
            for (ItemMenu item : items) {
                if (Boolean.TRUE.equals(item.getDeleted()) || !Boolean.TRUE.equals(item.getActive())) {
                    continue;
                }
                if (!style.includeUnavailable() && !Boolean.TRUE.equals(item.getAvailable())) {
                    continue;
                }
                if (item.getParentItem() != null) {
                    Long parentId = item.getParentItem().getIdItemMenu();
                    if (parentId != null) {
                        variantsByParent.computeIfAbsent(parentId, key -> new ArrayList<>()).add(item);
                    }
                    continue;
                }
                if (item.getCategory() == null || item.getCategory().getIdCategory() == null) {
                    continue;
                }
                parentsToPrint.add(item.getIdItemMenu());
                itemsByCategory.computeIfAbsent(item.getCategory().getIdCategory(), key -> new ArrayList<>()).add(item);
            }
        }

        // Variants of a dish that did not pass the filters are dropped with it.
        variantsByParent.keySet().retainAll(parentsToPrint);

        Comparator<ItemMenu> byName = Comparator.comparing(
                item -> item.getName() == null ? "" : item.getName().toLowerCase(new Locale("es", "MX")));
        Comparator<ItemMenu> byPrice = Comparator.comparing(
                item -> item.getPrice() == null ? BigDecimal.ZERO : item.getPrice());

        itemsByCategory.values().forEach(list -> list.sort(byName));
        variantsByParent.values().forEach(list -> list.sort(byPrice.thenComparing(byName)));
        return new MenuContent(itemsByCategory, variantsByParent);
    }

    private record MenuContent(Map<Long, List<ItemMenu>> itemsByCategory,
                               Map<Long, List<ItemMenu>> variantsByParent) {
    }

    private record Fonts(PdfFont regular, PdfFont bold, PdfFont display) {
        private PdfFont titles() {
            return display != null ? display : bold;
        }
    }

    // ==================== layout helpers ====================

    private PageSize pageSize(MenuStyle style) {
        return new PageSize(style.paperSize().getWidth(), style.paperSize().getHeight());
    }

    private float footerRoom(MenuStyle style) {
        return style.smallSize() * 3.2f;
    }

    private Cell borderlessCell() {
        return new Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(0)
                .setPaddingTop(2f)
                .setPaddingBottom(2f);
    }

    /** Empty row of an exact height, used for the air of the cover. */
    private Table spacer(float height) {
        Table table = new Table(UnitValue.createPercentArray(new float[] {100f})).useAllAvailableWidth();
        table.setMargin(0);
        table.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(0).setHeight(height));
        return table;
    }

    private Rectangle[] columnAreas(MenuStyle style, float margin, float footerRoom) {
        float width = style.paperSize().getWidth() - 2 * margin;
        float height = style.paperSize().getHeight() - margin * 2 - footerRoom;
        float columnWidth = (width - COLUMN_GAP) / 2f;
        float bottom = margin + footerRoom;
        return new Rectangle[] {
                new Rectangle(margin, bottom, columnWidth, height),
                new Rectangle(margin + columnWidth + COLUMN_GAP, bottom, columnWidth, height)
        };
    }

    // ==================== fonts and colors ====================

    private Fonts loadFonts(MenuStyle.FontFamily family) {
        try {
            return switch (family) {
                case ESTANDAR -> new Fonts(standard(StandardFonts.HELVETICA),
                        standard(StandardFonts.HELVETICA_BOLD), null);
                case CLASICA -> new Fonts(embedded(PT_SERIF_REGULAR), embedded(PT_SERIF_BOLD), null);
                case REDONDA -> new Fonts(embedded(VARELA_ROUND), embedded(VARELA_ROUND), null);
                case ELEGANTE -> new Fonts(embedded(LATO_REGULAR), embedded(LATO_BOLD), embedded(PACIFICO));
                case MODERNA -> new Fonts(embedded(LATO_REGULAR), embedded(LATO_BOLD), null);
            };
        } catch (IOException e) {
            log.warn("Could not load the {} fonts for the carta, falling back to Helvetica: {}",
                    family, e.getMessage());
            try {
                return new Fonts(standard(StandardFonts.HELVETICA),
                        standard(StandardFonts.HELVETICA_BOLD), null);
            } catch (IOException fatal) {
                throw new IllegalStateException("No PDF font available", fatal);
            }
        }
    }

    private PdfFont embedded(String path) throws IOException {
        byte[] bytes = FONT_BYTES.computeIfAbsent(path, key -> {
            try (InputStream in = MenuPdfService.class.getResourceAsStream(key)) {
                return in == null ? new byte[0] : in.readAllBytes();
            } catch (IOException e) {
                log.warn("Could not read font {}: {}", key, e.getMessage());
                return new byte[0];
            }
        });
        if (bytes.length == 0) {
            throw new IOException("Font not found on the classpath: " + path);
        }
        return PdfFontFactory.createFont(bytes, PdfEncodings.IDENTITY_H);
    }

    private PdfFont standard(String name) throws IOException {
        return PdfFontFactory.createFont(name, PdfEncodings.WINANSI);
    }

    /**
     * Reverses a "#rrggbb" color. Values are already normalized by {@link MenuStyle}, and
     * anything unexpected keeps the fallback, so the PDF never fails on a color.
     */
    private static DeviceRgb rgb(String hex, DeviceRgb fallback) {
        if (hex == null || !hex.matches("^#[0-9a-fA-F]{6}$")) {
            return fallback;
        }
        return new DeviceRgb(
                Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16));
    }

    /** Mixes a color with white; the thin rules and the dot leaders print better as a light gray. */
    private static DeviceRgb mixWithWhite(DeviceRgb color, float white) {
        float[] values = color.getColorValue();
        float keep = 1f - Math.max(0f, Math.min(1f, white));
        return new DeviceRgb(
                values[0] * keep + (1f - keep),
                values[1] * keep + (1f - keep),
                values[2] * keep + (1f - keep));
    }

    /** Prices always print with dot decimals and comma thousands, whatever the server locale is. */
    private static String money(BigDecimal amount) {
        BigDecimal value = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        return String.format(Locale.US, "$%,.2f", value);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
