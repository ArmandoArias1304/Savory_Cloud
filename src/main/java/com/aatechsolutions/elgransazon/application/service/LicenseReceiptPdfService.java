package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.LicenseEvent;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.entity.SystemLicense;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class LicenseReceiptPdfService {

    private static final DeviceRgb BRAND = new DeviceRgb(20, 83, 45);
    private static final DeviceRgb ACCENT = new DeviceRgb(22, 163, 74);
    private static final DeviceRgb ACCENT_SOFT = new DeviceRgb(240, 253, 244);
    private static final DeviceRgb INK = new DeviceRgb(15, 23, 42);
    private static final DeviceRgb MUTED = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb SURFACE = new DeviceRgb(248, 250, 252);
    private static final DeviceRgb LINE = new DeviceRgb(226, 232, 240);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-MX"));
    private final RestTemplate restTemplate;
    private final CloudflareImagesUrlHelper imageUrlHelper;

    public byte[] generateReceipt(LicenseEvent event, SystemLicense license,
                                  SystemConfiguration restaurantConfig,
                                  GlobalSystemConfig systemConfig) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

        String systemName = systemName(systemConfig);
        String systemSlogan = systemSlogan(systemConfig);
        String folio = "LIC-" + event.getId();
        LocalDateTime issuedAt = LocalDateTime.now();

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(output));
             Document document = new Document(pdf, PageSize.LETTER)) {
            document.setMargins(34, 46, 34, 46);
            document.setFont(regular);

            addIssuerHeader(document, systemConfig, systemName, systemSlogan,
                    folio, issuedAt, regular, bold);
            addAmountSummary(document, event, regular, bold);

            addSectionTitle(document, "01", "Datos del pago", bold);
            addPaymentDetails(document, event, regular, bold);

            addSectionTitle(document, "02", "Datos del cliente", bold);
            addCustomerDetails(document, license, regular, bold);

            addSectionTitle(document, "03", "Datos del restaurante", bold);
            addRestaurantDetails(document, restaurantConfig, license, regular, bold);

            addSectionTitle(document, "04", "Detalle del pago", bold);
            addPaymentDetailTable(document, event, systemName, regular, bold);

            addNote(document, systemName, regular, bold);
            document.add(new Paragraph(systemName + " · " + systemSlogan
                            + " · Documento generado electrónicamente el " + formatDate(issuedAt))
                    .setFont(regular).setFontSize(7).setFontColor(MUTED)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFixedPosition(1, 46, 18, PageSize.LETTER.getWidth() - 92));
        }
        return output.toByteArray();
    }

    private void addIssuerHeader(Document document, GlobalSystemConfig systemConfig,
                                 String systemName, String systemSlogan, String folio,
                                 LocalDateTime issuedAt, PdfFont regular, PdfFont bold) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{3, 2}))
                .useAllAvailableWidth();

        Cell brand = new Cell().setBorder(Border.NO_BORDER).setPadding(0).setPaddingRight(12);
        byte[] systemLogo = loadSystemLogo(systemConfig != null ? systemConfig.getSystemLogoUrl() : null);
        if (systemLogo != null) {
            try {
                brand.add(new Image(ImageDataFactory.create(systemLogo))
                        .scaleToFit(120, 38).setMarginBottom(4));
            } catch (Exception e) {
                log.warn("No se pudo incrustar el logo en el recibo de pago: {}", e.getMessage());
            }
        }
        brand.add(new Paragraph(printable(systemName)).setFont(bold).setFontSize(14)
                .setFontColor(BRAND).setMargin(0));
        if (systemSlogan != null && !systemSlogan.isBlank()) {
            brand.add(new Paragraph(systemSlogan).setFont(regular).setFontSize(8)
                    .setFontColor(MUTED).setMarginTop(1));
        }
        header.addCell(brand);

        Cell meta = new Cell().setBorder(Border.NO_BORDER).setPadding(0)
                .setTextAlignment(TextAlignment.RIGHT);
        meta.add(new Paragraph("RECIBO DE PAGO").setFont(bold).setFontSize(19)
                .setFontColor(INK).setMargin(0));
        meta.add(new Paragraph("Folio " + folio).setFont(bold).setFontSize(9)
                .setFontColor(BRAND).setMarginTop(3));
        meta.add(new Paragraph("Emitido el " + formatDate(issuedAt)).setFont(regular)
                .setFontSize(8).setFontColor(MUTED).setMarginTop(1));
        header.addCell(meta);

        document.add(header);
        document.add(rule(BRAND, 2).setMarginTop(3).setMarginBottom(0));
    }

    private void addAmountSummary(Document document, LicenseEvent event,
                                  PdfFont regular, PdfFont bold) {
        Table summary = new Table(UnitValue.createPercentArray(new float[]{7, 3}))
                .useAllAvailableWidth();

        Cell amount = new Cell().setBorder(new SolidBorder(LINE, 0.8f))
                .setBackgroundColor(SURFACE).setPadding(13);
        amount.add(new Paragraph("TOTAL PAGADO").setFont(bold).setFontSize(8)
                .setFontColor(MUTED).setMargin(0));
        amount.add(new Paragraph(formatCurrency(event.getAmount())).setFont(bold).setFontSize(25)
                .setFontColor(BRAND).setMarginTop(2).setMarginBottom(1));
        amount.add(new Paragraph("Pesos mexicanos (MXN)").setFont(regular).setFontSize(8)
                .setFontColor(MUTED).setMargin(0));
        summary.addCell(amount);

        Cell status = new Cell().setBorder(new SolidBorder(LINE, 0.8f))
                .setBackgroundColor(ACCENT_SOFT).setPadding(13)
                .setTextAlignment(TextAlignment.CENTER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        status.add(new Paragraph("ESTADO DEL PAGO").setFont(bold).setFontSize(8)
                .setFontColor(MUTED).setMargin(0));
        status.add(new Paragraph("PAGADO").setFont(bold).setFontSize(16)
                .setFontColor(ACCENT).setMarginTop(2).setMarginBottom(1));
        status.add(new Paragraph("Periodo: " + monthsLabel(event.getMonths()))
                .setFont(regular).setFontSize(8.5f).setFontColor(INK).setMargin(0));
        summary.addCell(status);

        document.add(summary.setMarginTop(12));
    }

    private void addPaymentDetails(Document document, LicenseEvent event,
                                   PdfFont regular, PdfFont bold) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth().setKeepTogether(true);
        addFieldRow(table,
                "Fecha de pago", formatDate(event.getEventDate()),
                "Tipo de movimiento", movementLabel(event),
                regular, bold);
        addFieldRow(table,
                "Periodo contratado", monthsLabel(event.getMonths()),
                "Forma de pago", paymentMethodLabel(event.getPaymentMethod()),
                regular, bold);
        addFieldRow(table,
                "Registrado por", printable(event.getPerformedBy()),
                "Moneda", "MXN · Peso mexicano",
                regular, bold);
        document.add(table);
    }

    private void addCustomerDetails(Document document, SystemLicense license,
                                    PdfFont regular, PdfFont bold) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth().setKeepTogether(true);
        addFieldRow(table,
                "Nombre", printable(license != null ? license.getOwnerName() : null),
                "RFC", printable(license != null ? license.getOwnerRfc() : null),
                regular, bold);
        addFieldRow(table,
                "Correo", printable(license != null ? license.getOwnerEmail() : null),
                "Teléfono", printable(license != null ? license.getOwnerPhone() : null),
                regular, bold);
        document.add(table);
    }

    private void addRestaurantDetails(Document document, SystemConfiguration config,
                                      SystemLicense license, PdfFont regular, PdfFont bold) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth().setKeepTogether(true);
        addFieldRow(table,
                "Nombre", restaurantName(license, config),
                "RFC", config != null ? printable(config.getRfc()) : "No registrado",
                regular, bold);
        table.addCell(fieldCell("Dirección",
                config != null ? printable(config.getAddress()) : "No registrado", 2, regular, bold)
                .setPaddingRight(0));
        table.addCell(fieldCell("Contacto", restaurantContact(config), 2, regular, bold)
                .setPaddingRight(0));
        document.add(table);
    }

    private void addPaymentDetailTable(Document document, LicenseEvent event,
                                       String systemName, PdfFont regular, PdfFont bold) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{6, 2, 2}))
                .useAllAvailableWidth().setKeepTogether(true);

        Cell conceptHeader = detailHeader("CONCEPTO", TextAlignment.LEFT, bold).setPaddingLeft(0);
        Cell periodHeader = detailHeader("PERIODO", TextAlignment.CENTER, bold);
        Cell amountHeader = detailHeader("IMPORTE", TextAlignment.RIGHT, bold).setPaddingRight(0);
        table.addCell(conceptHeader);
        table.addCell(periodHeader);
        table.addCell(amountHeader);

        Cell concept = detailBody(paymentConcept(event, systemName), TextAlignment.LEFT, regular)
                .setPaddingLeft(0);
        Cell period = detailBody(monthsLabel(event.getMonths()), TextAlignment.CENTER, regular);
        Cell amount = detailBody(formatCurrency(event.getAmount()), TextAlignment.RIGHT, bold)
                .setFontColor(BRAND).setPaddingRight(0);
        table.addCell(concept);
        table.addCell(period);
        table.addCell(amount);

        Cell totalLabel = new Cell(1, 2)
                .add(new Paragraph("TOTAL PAGADO").setFont(bold).setFontSize(8).setFontColor(INK))
                .setTextAlignment(TextAlignment.RIGHT)
                .setBorder(Border.NO_BORDER).setBorderTop(new SolidBorder(BRAND, 1.2f))
                .setPaddingTop(6).setPaddingBottom(6).setPaddingRight(8);
        table.addCell(totalLabel);

        Cell totalValue = new Cell()
                .add(new Paragraph(formatCurrency(event.getAmount()))
                        .setFont(bold).setFontSize(11).setFontColor(BRAND))
                .setTextAlignment(TextAlignment.RIGHT)
                .setBorder(Border.NO_BORDER).setBorderTop(new SolidBorder(BRAND, 1.2f))
                .setPaddingTop(6).setPaddingBottom(6);
        table.addCell(totalValue);

        document.add(table);
    }

    private void addSectionTitle(Document document, String number, String title, PdfFont bold) {
        document.add(new Paragraph()
                .add(new Text(number).setFont(bold).setFontSize(8).setFontColor(ACCENT))
                .add(new Text("   " + title.toUpperCase()).setFont(bold).setFontSize(8.5f)
                        .setFontColor(BRAND))
                .setBorderBottom(new SolidBorder(LINE, 0.7f))
                .setPaddingBottom(3)
                .setMarginTop(11).setMarginBottom(4));
    }

    private void addFieldRow(Table table, String leftLabel, String leftValue,
                             String rightLabel, String rightValue,
                             PdfFont regular, PdfFont bold) {
        table.addCell(fieldCell(leftLabel, leftValue, regular, bold).setPaddingRight(16));
        table.addCell(fieldCell(rightLabel, rightValue, regular, bold)
                .setPaddingLeft(16).setPaddingRight(0));
    }

    private Cell fieldCell(String label, String value, PdfFont regular, PdfFont bold) {
        return fieldCell(label, value, 1, regular, bold);
    }

    private Cell fieldCell(String label, String value, int colspan, PdfFont regular, PdfFont bold) {
        Cell cell = new Cell(1, colspan).setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(LINE, 0.6f))
                .setPaddingTop(4).setPaddingBottom(4)
                .setPaddingLeft(0).setPaddingRight(0);
        cell.add(new Paragraph(label.toUpperCase()).setFont(regular).setFontSize(7)
                .setFontColor(MUTED).setMargin(0));
        cell.add(new Paragraph(printable(value)).setFont(bold).setFontSize(9)
                .setFontColor(INK).setMarginTop(1).setMarginBottom(0));
        return cell;
    }

    private Cell detailHeader(String text, TextAlignment alignment, PdfFont bold) {
        return new Cell()
                .add(new Paragraph(text).setFont(bold).setFontSize(7.5f).setFontColor(MUTED))
                .setTextAlignment(alignment).setBackgroundColor(SURFACE)
                .setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(LINE, 0.8f))
                .setPaddingTop(6).setPaddingBottom(6)
                .setPaddingLeft(8).setPaddingRight(8);
    }

    private Cell detailBody(String text, TextAlignment alignment, PdfFont font) {
        return new Cell()
                .add(new Paragraph(printable(text)).setFont(font).setFontSize(9).setFontColor(INK))
                .setTextAlignment(alignment)
                .setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(LINE, 0.5f))
                .setPaddingTop(8).setPaddingBottom(8)
                .setPaddingLeft(8).setPaddingRight(8);
    }

    private void addNote(Document document, String systemName, PdfFont regular, PdfFont bold) {
        Div note = new Div().setBackgroundColor(SURFACE)
                .setBorder(new SolidBorder(LINE, 0.6f))
                .setPadding(9).setMarginTop(12);
        note.add(new Paragraph("DOCUMENTO INFORMATIVO").setFont(bold).setFontSize(7.5f)
                .setFontColor(MUTED).setMargin(0));
        note.add(new Paragraph("Este recibo acredita el pago registrado en " + systemName
                + " y no sustituye una factura fiscal. Conserve este documento para cualquier aclaración.")
                .setFont(regular).setFontSize(7.5f).setFontColor(MUTED)
                .setMarginTop(2).setMarginBottom(0));
        document.add(note);
    }

    private LineSeparator rule(DeviceRgb color, float width) {
        LineSeparator separator = new LineSeparator(new SolidLine(width));
        separator.setStrokeColor(color);
        return separator;
    }

    private String systemName(GlobalSystemConfig config) {
        if (config != null && config.getSystemName() != null && !config.getSystemName().isBlank()) {
            return config.getSystemName().trim();
        }
        return "SavoryCloud";
    }

    private String systemSlogan(GlobalSystemConfig config) {
        if (config != null && config.getSystemSlogan() != null && !config.getSystemSlogan().isBlank()) {
            return config.getSystemSlogan().trim();
        }
        return "Sistema de Gestión Restaurantera";
    }

    private String movementLabel(LicenseEvent event) {
        if (event.getEventType() == null) {
            return "No especificado";
        }
        return switch (event.getEventType()) {
            case CREATED -> "Alta de licencia";
            case RENEWED -> "Renovación de licencia";
            default -> event.getEventTypeDisplayName();
        };
    }

    private String paymentConcept(LicenseEvent event, String systemName) {
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            return event.getDescription();
        }
        return event.getEventType() == LicenseEvent.EventType.CREATED
                ? "Alta de licencia " + systemName
                : "Renovación de licencia " + systemName;
    }

    private String paymentMethodLabel(PaymentMethodType paymentMethod) {
        return paymentMethod != null ? paymentMethod.getDisplayName() : "No registrada";
    }

    private String monthsLabel(Integer months) {
        if (months == null || months <= 0) {
            return "No especificado";
        }
        return months + (months == 1 ? " mes" : " meses");
    }

    private byte[] loadSystemLogo(String configuredUrl) {
        byte[] configuredLogo = loadRemoteLogo(configuredUrl);
        if (configuredLogo != null) {
            return configuredLogo;
        }
        try {
            try (var input = new ClassPathResource("static/images/savoryCloudLogo.png").getInputStream()) {
                return input.readAllBytes();
            }
        } catch (IOException e) {
            log.warn("No se pudo cargar el logo local de SavoryCloud: {}", e.getMessage());
            return null;
        }
    }

    private byte[] loadRemoteLogo(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(source);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !("imagedelivery.net".equalsIgnoreCase(host)
                    || "res.cloudinary.com".equalsIgnoreCase(host))) {
                log.warn("Se omitió un logo con URL fuera de los proveedores de imágenes permitidos.");
                return null;
            }
            String url = "imagedelivery.net".equalsIgnoreCase(host)
                    ? imageUrlHelper.transform(source, "w=360,h=180,fit=contain,quality=85,format=png")
                    : source;
            return restTemplate.getForObject(url, byte[].class);
        } catch (Exception e) {
            log.warn("No se pudo descargar un logo para el recibo: {}", e.getMessage());
            return null;
        }
    }

    private String restaurantName(SystemLicense license, SystemConfiguration config) {
        if (config != null && config.getRestaurantName() != null && !config.getRestaurantName().isBlank()) {
            return config.getRestaurantName();
        }
        if (license != null && license.getRestaurantName() != null && !license.getRestaurantName().isBlank()) {
            return license.getRestaurantName();
        }
        if (license != null && license.getCompany() != null && license.getCompany().getName() != null) {
            return license.getCompany().getName();
        }
        return "Restaurante";
    }

    private String restaurantContact(SystemConfiguration config) {
        if (config == null) {
            return "No registrado";
        }
        String phone = config.getPhone();
        String email = config.getEmail();
        if (phone == null || phone.isBlank()) {
            return printable(email);
        }
        if (email == null || email.isBlank()) {
            return phone;
        }
        return phone + " · " + email;
    }

    private String formatCurrency(Double amount) {
        BigDecimal value = BigDecimal.valueOf(amount != null ? amount : 0);
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-MX"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "$" + format.format(value);
    }

    private String formatDate(LocalDateTime date) {
        return date != null ? DATE_FORMAT.format(date) : "No registrada";
    }

    private String printable(String value) {
        return value != null && !value.isBlank() ? value : "No registrado";
    }
}
