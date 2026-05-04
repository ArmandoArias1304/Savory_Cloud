package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;

/**
 * Service for generating ESC/POS raw tickets for thermal printers.
 * Mirrors the exact layout of TicketPdfService but uses native ESC/POS commands.
 *
 * Designed for 58mm printers (32 columns Font A) so it works on both 58mm and 80mm.
 * 80mm printers simply center the content with wider margins.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketEscPosService {

    private final SystemConfigurationService systemConfigurationService;
    private final GlobalSystemConfigService globalSystemConfigService;
    private final DateTimeService dateTimeService;
    private final CloudflareImagesUrlHelper cloudflareImagesUrlHelper;

    // Use 32-column layout (58mm Font A) for universal compatibility
    private static final int LINE_WIDTH = 32;
    private static final Charset CP1252 = Charset.forName("Cp1252");

    // ESC/POS commands
    private static final byte[] INIT            = {0x1B, 0x40};                // Initialize printer
    private static final byte[] ALIGN_CENTER    = {0x1B, 0x61, 0x01};         // Center alignment
    private static final byte[] ALIGN_RIGHT     = {0x1B, 0x61, 0x02};         // Right alignment
    private static final byte[] BOLD_ON         = {0x1B, 0x45, 0x01};         // Bold on
    private static final byte[] BOLD_OFF        = {0x1B, 0x45, 0x00};         // Bold off
    private static final byte[] DOUBLE_HEIGHT   = {0x1B, 0x21, 0x10};         // Double height
    private static final byte[] NORMAL_SIZE     = {0x1B, 0x21, 0x00};         // Normal size
    private static final byte[] FONT_B          = {0x1B, 0x4D, 0x01};         // Font B (smaller)
    private static final byte[] FONT_A          = {0x1B, 0x4D, 0x00};         // Font A (normal)
    private static final byte[] FEED_CUT        = {0x1D, 0x56, 0x00};         // Full cut
    private static final byte[] SET_CP1252      = {0x1B, 0x74, 0x10};         // Set code page CP1252
    private static final byte LF                = 0x0A;

    /**
     * Generate ESC/POS raw bytes for an order ticket.
     * @param order Order to generate ticket for
     * @return byte array containing ESC/POS commands
     */
    public byte[] generateTicket(Order order) throws IOException {
        log.info("Generating ESC/POS ticket for order: {}", order.getOrderNumber());

        ByteArrayOutputStream out = new ByteArrayOutputStream(2048);

        // Initialize printer and set code page for Spanish accents
        out.write(INIT);
        out.write(SET_CP1252);
        // Set line spacing to minimum (0 dots) to reduce top margin
        out.write(new byte[]{0x1B, 0x33, 0x00});

        // Get system configuration
        SystemConfiguration config = systemConfigurationService.getConfiguration();

        // ── Logo (raster image) ──
        int logoIntensity = config.getTicketLogoOpacity() != null ? config.getTicketLogoOpacity() : 50;
        writeLogo(out, config.getRestaurantLogoUrl(), logoIntensity);
        // Restore default line spacing after logo
        out.write(new byte[]{0x1B, 0x32});

        // ── Restaurant name (centered, bold, double height) ──
        out.write(ALIGN_CENTER);
        out.write(BOLD_ON);
        out.write(DOUBLE_HEIGHT);
        printLine(out, config.getRestaurantName());
        out.write(NORMAL_SIZE);
        out.write(BOLD_OFF);

        // ── Address (centered, Font B) ──
        out.write(FONT_B);
        printLine(out, config.getAddress());

        // ── RFC (centered, Font B) ──
        if (config.getRfc() != null && !config.getRfc().isBlank()) {
            printLine(out, "RFC: " + config.getRfc());
        }

        // ── Phone (centered, Font B) ──
        printLine(out, "Tel: " + config.getPhone());
        out.write(FONT_A);

        // ── Separator ──
        printSeparator(out);

        // ── Order number (centered, bold, double height only – no double width to avoid line overflow) ──
        out.write(BOLD_ON);
        out.write(DOUBLE_HEIGHT);
        printLine(out, "ORDEN: " + order.getOrderNumber());
        out.write(NORMAL_SIZE);
        out.write(BOLD_OFF);

        // ── Separator + Items header ──
        printSeparator(out);
        out.write(BOLD_ON);
        printLine(out, "DETALLE DEL PEDIDO");
        out.write(BOLD_OFF);

        // ── Check for promotions ──
        boolean hasPromotions = order.getOrderDetails().stream()
                .anyMatch(d -> d.getPromotionAppliedPrice() != null &&
                        d.getPromotionAppliedPrice().compareTo(d.getUnitPrice()) < 0);

        // ── Column headers (Font B for density, centered on paper) ──
        out.write(ALIGN_CENTER);
        out.write(FONT_B);
        out.write(BOLD_ON);
        if (hasPromotions) {
            // 4-col: Product(18) Qty(4) Total(10) T.Final(10) = 42 (full Font B width on 58mm)
            printLine(out, padColumns4("Producto", "Cant", "Total", "T.Final"));
        } else {
            // 3-col: Product(22) Qty(6) Total(14) = 42 (full Font B width on 58mm)
            printLine(out, padColumns3("Producto", "Cant", "Total"));
        }
        out.write(BOLD_OFF);

        // ── Pre-process: merge identical simple items ──
        List<OrderDetail> rawDetails = order.getOrderDetails();
        Map<String, int[]> mergedQty = new LinkedHashMap<>();
        Map<String, BigDecimal[]> mergedTotals = new LinkedHashMap<>();
        List<OrderDetail> ticketOrder = new ArrayList<>();

        for (OrderDetail detail : rawDetails) {
            boolean canMerge = !detail.isComboParent() && !detail.isComboChild()
                    && (detail.getSelectedComplements() == null || detail.getSelectedComplements().isEmpty());
            String mergeKey = detail.getItemMenu().getIdItemMenu() + "_" + detail.getUnitPrice().toPlainString();
            BigDecimal lineOriginal = detail.getUnitPrice()
                    .multiply(BigDecimal.valueOf(detail.getQuantity()));

            if (canMerge && mergedQty.containsKey(mergeKey)) {
                mergedQty.get(mergeKey)[0] += detail.getQuantity();
                mergedTotals.get(mergeKey)[0] = mergedTotals.get(mergeKey)[0].add(lineOriginal);
                mergedTotals.get(mergeKey)[1] = mergedTotals.get(mergeKey)[1].add(detail.getSubtotal());
            } else {
                if (canMerge) {
                    mergedQty.put(mergeKey, new int[]{detail.getQuantity()});
                    mergedTotals.put(mergeKey, new BigDecimal[]{lineOriginal, detail.getSubtotal()});
                }
                ticketOrder.add(detail);
            }
        }

        // ── Print each item ──
        for (OrderDetail detail : ticketOrder) {
            boolean isMerged = !detail.isComboParent() && !detail.isComboChild()
                    && (detail.getSelectedComplements() == null || detail.getSelectedComplements().isEmpty());
            String mergeKey = detail.getItemMenu().getIdItemMenu() + "_" + detail.getUnitPrice().toPlainString();

            String itemName = detail.getItemMenu().getName();
            boolean isComboParent = detail.isComboParent();
            boolean isComboChild = detail.isComboChild();
            if (isComboParent) {
                itemName = "[COMBO] " + itemName;
            } else if (isComboChild) {
                itemName = " > " + itemName;
            }

            Integer quantity;
            BigDecimal precioOriginalConIVA;
            BigDecimal precioFinalConIVA;
            if (isMerged && mergedQty.containsKey(mergeKey)) {
                quantity = mergedQty.get(mergeKey)[0];
                precioOriginalConIVA = mergedTotals.get(mergeKey)[0].setScale(2, RoundingMode.HALF_UP);
                precioFinalConIVA = mergedTotals.get(mergeKey)[1];
            } else {
                quantity = detail.getQuantity();
                precioOriginalConIVA = detail.getUnitPrice().multiply(BigDecimal.valueOf(quantity))
                        .setScale(2, RoundingMode.HALF_UP);
                precioFinalConIVA = detail.getSubtotal();
            }

            String totalStr = "$" + precioOriginalConIVA.toPlainString();

            if (hasPromotions) {
                boolean hasDiscount = precioFinalConIVA.compareTo(precioOriginalConIVA) < 0;
                String finalStr;
                if (hasDiscount) {
                    finalStr = precioFinalConIVA.compareTo(BigDecimal.ZERO) == 0
                            ? "GRATIS"
                            : "$" + precioFinalConIVA.setScale(2, RoundingMode.HALF_UP).toPlainString();
                } else {
                    finalStr = "-";
                }
                printLine(out, padColumns4(truncate(itemName, 14), String.valueOf(quantity), totalStr, finalStr));
            } else {
                printLine(out, padColumns3(truncate(itemName, 16), String.valueOf(quantity), totalStr));
            }

            // Comments
            String displayComments = detail.getDisplayComments();
            if (displayComments != null && !displayComments.isEmpty()) {
                String prefix = isComboChild ? "    -> " : "  -> ";
                printLine(out, truncate(prefix + displayComments, 42));
            }

            // Complements
            if (detail.getSelectedComplements() != null && !detail.getSelectedComplements().isEmpty()) {
                for (OrderDetailComplement odc : detail.getSelectedComplements()) {
                    String compName = "+ " + odc.getComplement().getName();
                    // For sauces, multiply by parent item quantity
                    int effectiveQty = odc.getQuantity();
                    BigDecimal effectiveTotal = odc.getSubtotal();
                    if (Boolean.TRUE.equals(odc.getComplement().getIsSauce())) {
                        effectiveQty = effectiveQty * detail.getQuantity();
                        effectiveTotal = effectiveTotal.multiply(BigDecimal.valueOf(detail.getQuantity()));
                    }
                    String compQty = String.valueOf(effectiveQty);
                    String compTotal = "$" + effectiveTotal.setScale(2, RoundingMode.HALF_UP).toPlainString();

                    String indent = isComboChild ? "    " : "  ";
                    if (hasPromotions) {
                        printLine(out, padColumns4(truncate(indent + compName, 14), compQty, compTotal, "-"));
                    } else {
                        printLine(out, padColumns3(truncate(indent + compName, 16), compQty, compTotal));
                    }
                }
            }
        }
        out.write(FONT_A);

        // ── Totals separator ──
        printSeparator(out);

        // ── Totals section ──
        // Subtotal e IVA INCLUYEN el costo de envío Y descuento por promoción (consistente con el CFDI).
        // El envío y el descuento se muestran como notas informativas después del TOTAL.
        BigDecimal taxAmount = order.getTaxAmount();
        BigDecimal total = order.getTotal();

        out.write(ALIGN_RIGHT);
        printTotalLine(out, "Subtotal:", order.getFormattedDisplaySubtotal(), false);

        if (order.hasOrderDiscount()) {
            printTotalLine(out, "Descuento de orden:", "-" + order.getFormattedOrderDiscountWithoutTax(), false);
        }

        printTotalLine(out, "IVA (" + order.getTaxRate() + "%):", "$" + taxAmount.toString(), false);

        // TOTAL (bold) - sin propina
        out.write(BOLD_ON);
        printTotalLine(out, "TOTAL:", "$" + total.setScale(2, RoundingMode.HALF_UP).toPlainString(), true);
        out.write(BOLD_OFF);

        // Tip below total (if any)
        if (order.getTip() != null && order.getTip().compareTo(BigDecimal.ZERO) > 0) {
            printTotalLine(out, "Propina:", "$" + order.getTip().toString(), false);
        }

        // Total in words (Mexican format: "Son: Quinientos pesos 00/100 M.N.")
        out.write(ALIGN_CENTER);
        out.write(FONT_B);
        printLine(out, totalEnLetra(total));

        // Nota informativa: del total, cuánto fue envío (DELIVERY only) — primer lugar
        if (order.getOrderType() == OrderType.DELIVERY
                && order.getDeliveryCost() != null
                && order.getDeliveryCost().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal envioGross = order.getDeliveryCost().setScale(2, RoundingMode.HALF_UP);
            printLine(out, "Incluye costo de envio de $" + envioGross.toPlainString());
        }

        // Nota informativa: descuento por promoción (incluido en subtotal/IVA) — segundo lugar
        if (order.hasDiscount()) {
            printLine(out, "Incluye descuento por promocion de " + order.getFormattedDiscountWithTax());
        }

        // Nota informativa: descuento aplicado al total de la orden (incluye IVA)
        if (order.hasOrderDiscount()) {
            printLine(out, "Descuento aplicado de " + order.getFormattedOrderDiscount());
        }
        out.write(FONT_A);

        // ── Order info (centered) ──
        out.write(ALIGN_CENTER);
        out.write(FONT_B);
        String paymentMethodStr = order.getPaymentMethod() != null ? order.getPaymentMethod().getDisplayName() : "N/A";
        printLine(out, "Tipo: " + order.getOrderType().getDisplayName() + " | Pago: " + paymentMethodStr);

        // Customer
        if (order.getCustomerName() != null && !order.getCustomerName().trim().isEmpty()) {
            printLine(out, "Cliente: " + order.getCustomerName());
        }

        // Served by
        String servedBy = order.getEmployee() != null ? order.getEmployee().getFullName() : config.getRestaurantName();
        printLine(out, "Atendido por: " + servedBy);
        out.write(FONT_A);

        // ── Date separator + date ──
        printSeparator(out);
        out.write(FONT_B);
        if (order.getStatus() == OrderStatus.PAID) {
            printLine(out, "Pagado: " + dateTimeService.formatToCompanyTime(order.getPaidAt(), "dd/MM/yyyy HH:mm"));
        } else {
            printLine(out, "Creada: " + dateTimeService.formatToCompanyTime(order.getCreatedAt(), "dd/MM/yyyy HH:mm"));
        }
        out.write(FONT_A);

        // ── Final separator ──
        printSeparator(out);

        // ── Thank you ──
        out.write(BOLD_ON);
        printLine(out, "\u00A1Gracias por su preferencia!");
        out.write(BOLD_OFF);
        out.write(FONT_B);
        printLine(out, "Esperamos volver a atenderle pronto");
        out.write(FONT_A);

        // ── Fiscal disclaimer / Autofactura billing info ──
        out.write(FONT_B);
        if (order.getAutofacturaKey() != null && !order.getAutofacturaKey().isBlank()) {
            // Order has an autofactura key — show QR code
            printSeparator(out);
            printLine(out, "Facture este ticket");
            printLine(out, "escaneando el codigo QR:");
            if (order.getSelfInvoiceUrl() != null) {
                writeQrCode(out, order.getSelfInvoiceUrl());
            }
            // Invoicing deadline legend (last day of payment month, in company timezone)
            java.time.LocalDate deadline = order.getInvoiceDeadline(
                    com.aatechsolutions.elgransazon.infrastructure.util.CompanyLocalTime.getZone());
            if (deadline != null) {
                String deadlineText = deadline.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                out.write(BOLD_ON);
                printLine(out, "Facture antes del " + deadlineText);
                out.write(BOLD_OFF);
            }
            printSeparator(out);
        } else {
            printLine(out, "Este no es un comprobante fiscal");
        }
        out.write(FONT_A);

        // ── System branding (small) ──
        out.write(FONT_B);
        String systemName = globalSystemConfigService.getConfiguration().getSystemName();
        printLine(out, "by " + systemName);
        out.write(FONT_A);

        // ── Feed and cut ──
        out.write(new byte[]{LF, LF, LF, LF});
        out.write(FEED_CUT);

        log.info("ESC/POS ticket generated successfully for order: {}", order.getOrderNumber());
        return out.toByteArray();
    }

    // ── Helper methods ──

    private void printLine(ByteArrayOutputStream out, String text) throws IOException {
        out.write(text.getBytes(CP1252));
        out.write(LF);
    }

    private void printSeparator(ByteArrayOutputStream out) throws IOException {
        out.write(ALIGN_CENTER);
        printLine(out, "--------------------------------");
    }

    private void printTotalLine(ByteArrayOutputStream out, String label, String value, boolean large) throws IOException {
        if (large) {
            // For TOTAL line, use full width
            int pad = LINE_WIDTH - label.length() - value.length();
            if (pad < 1) pad = 1;
            printLine(out, label + " ".repeat(pad) + value);
        } else {
            int pad = LINE_WIDTH - label.length() - value.length();
            if (pad < 1) pad = 1;
            printLine(out, label + " ".repeat(pad) + value);
        }
    }

    /**
     * 3-column layout: Name(16) Qty(10) Total(16) = 42 (full Font B width on 58mm)
     * Qty centered in the middle of the paper, Total flush right.
     */
    private String padColumns3(String name, String qty, String total) {
        int colName = 16;
        int colQty = 10;
        int colTotal = 16;
        return padRight(name, colName) + padCenter(qty, colQty) + padLeft(total, colTotal);
    }

    /**
     * 4-column layout: Name(14) Qty(8) Total(10) Final(10) = 42 (full Font B width on 58mm)
     * Qty centered in the middle of the paper, totals flush right.
     */
    private String padColumns4(String name, String qty, String total, String finalP) {
        int colName = 14;
        int colQty = 8;
        int colTotal = 10;
        int colFinal = 10;
        return padRight(name, colName) + padCenter(qty, colQty) + padLeft(total, colTotal) + padLeft(finalP, colFinal);
    }

    private String padRight(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }

    private String padLeft(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        return " ".repeat(width - s.length()) + s;
    }

    private String padCenter(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        int leftPad = (width - s.length()) / 2;
        int rightPad = width - s.length() - leftPad;
        return " ".repeat(leftPad) + s + " ".repeat(rightPad);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + ".";
    }

    /**
     * Convert a BigDecimal amount to Mexican legal text format.
     * Example: 1234.56 → "Son: Un mil doscientos treinta y cuatro pesos 56/100 M.N."
     */
    private String totalEnLetra(BigDecimal amount) {
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        long intPart = rounded.toBigInteger().longValue();
        int cents = rounded.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue();

        String words = integerToSpanish(intPart);
        words = Character.toUpperCase(words.charAt(0)) + words.substring(1);

        String pesosWord = (intPart == 1) ? "peso" : "pesos";
        return "Son: " + words + " " + pesosWord + " " + String.format("%02d", cents) + "/100 M.N.";
    }

    private String integerToSpanish(long n) {
        if (n == 0) return "cero";

        if (n >= 1_000_000) {
            long millions = n / 1_000_000;
            long remainder = n % 1_000_000;
            String prefix = (millions == 1) ? "un millon" : integerToSpanish(millions) + " millones";
            return remainder == 0 ? prefix : prefix + " " + integerToSpanish(remainder);
        }
        if (n >= 1000) {
            long thousands = n / 1000;
            long remainder = n % 1000;
            String prefix = (thousands == 1) ? "mil" : integerToSpanish(thousands) + " mil";
            return remainder == 0 ? prefix : prefix + " " + integerToSpanish(remainder);
        }
        if (n >= 100) {
            if (n == 100) return "cien";
            String[] centenas = {"", "ciento", "doscientos", "trescientos", "cuatrocientos",
                    "quinientos", "seiscientos", "setecientos", "ochocientos", "novecientos"};
            long remainder = n % 100;
            return remainder == 0 ? centenas[(int) (n / 100)] : centenas[(int) (n / 100)] + " " + integerToSpanish(remainder);
        }
        if (n >= 30) {
            String[] decenas = {"", "", "", "treinta", "cuarenta", "cincuenta",
                    "sesenta", "setenta", "ochenta", "noventa"};
            long remainder = n % 10;
            return remainder == 0 ? decenas[(int) (n / 10)] : decenas[(int) (n / 10)] + " y " + integerToSpanish(remainder);
        }
        if (n >= 20) {
            String[] veintes = {"veinte", "veintiun", "veintidos", "veintitres", "veinticuatro",
                    "veinticinco", "veintiseis", "veintisiete", "veintiocho", "veintinueve"};
            return veintes[(int) (n - 20)];
        }
        if (n >= 10) {
            String[] teens = {"diez", "once", "doce", "trece", "catorce", "quince",
                    "dieciseis", "diecisiete", "dieciocho", "diecinueve"};
            return teens[(int) (n - 10)];
        }
        String[] unidades = {"", "un", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve"};
        return unidades[(int) n];
    }

    /**
     * Download logo from Cloudinary, convert to monochrome raster, and write
     * using GS v 0 (raster bit image) command.
     * Falls back silently if logo is unavailable.
     */
    private void writeLogo(ByteArrayOutputStream out, String logoUrl, int intensityPercent) {
        if (logoUrl == null || logoUrl.isBlank()) return;
        try {
            // Request a small monochrome-friendly PNG from Cloudinary
            String pngUrl = cloudflareImagesUrlHelper.transform(logoUrl, "w=192,h=192,fit=contain,format=png,quality=85");
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.net.URL(pngUrl));
            if (img == null) return;

            // Scale to max 192px wide (fits 58mm at 203 DPI)
            int targetWidth = Math.min(img.getWidth(), 192);
            int targetHeight = (int) ((double) targetWidth / img.getWidth() * img.getHeight());
            java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(targetWidth, targetHeight,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2 = scaled.createGraphics();
            // Fill with white background so transparent areas don't print as black
            g2.setColor(java.awt.Color.WHITE);
            g2.fillRect(0, 0, targetWidth, targetHeight);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(img, 0, 0, targetWidth, targetHeight, null);
            g2.dispose();

            // Width must be a multiple of 8 for raster
            int rasterWidth = ((targetWidth + 7) / 8) * 8;
            int widthBytes = rasterWidth / 8;

            // Convert to monochrome bitmap (1 = black dot)
            // Intensity controls the threshold: higher intensity = higher threshold = MORE dots printed (darker)
            // 50% → threshold 128 (original), 75% → threshold 191, 100% → threshold 255 (max dark)
            int threshold = Math.min(255, (int) (intensityPercent * 2.55));
            byte[] rasterData = new byte[widthBytes * targetHeight];
            for (int y = 0; y < targetHeight; y++) {
                for (int x = 0; x < targetWidth; x++) {
                    int rgb = scaled.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int gVal = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    int gray = (r * 299 + gVal * 587 + b * 114) / 1000;
                    if (gray < threshold) { // dark pixel = print dot
                        int byteIndex = y * widthBytes + (x / 8);
                        int bitIndex = 7 - (x % 8);
                        rasterData[byteIndex] |= (byte) (1 << bitIndex);
                    }
                }
            }

            // GS v 0 — Print raster bit image
            out.write(ALIGN_CENTER);
            out.write(new byte[]{
                    0x1D, 0x76, 0x30, 0x00,             // GS v 0 m=0 (normal)
                    (byte) (widthBytes & 0xFF),          // xL
                    (byte) ((widthBytes >> 8) & 0xFF),   // xH
                    (byte) (targetHeight & 0xFF),        // yL
                    (byte) ((targetHeight >> 8) & 0xFF)  // yH
            });
            out.write(rasterData);

        } catch (Exception e) {
            log.warn("Could not render logo for ESC/POS ticket: {}", e.getMessage());
        }
    }

    /**
     * Generate a QR code and write it as a raster bit image using GS v 0.
     * Uses ZXing to generate the QR matrix, then converts to monochrome raster.
     */
    private void writeQrCode(ByteArrayOutputStream out, String text) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            java.util.EnumMap<EncodeHintType, Object> hints = new java.util.EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, 192, 192, hints);

            int qrWidth = matrix.getWidth();
            int qrHeight = matrix.getHeight();

            // Width must be a multiple of 8 for raster
            int rasterWidth = ((qrWidth + 7) / 8) * 8;
            int widthBytes = rasterWidth / 8;

            byte[] rasterData = new byte[widthBytes * qrHeight];
            for (int y = 0; y < qrHeight; y++) {
                for (int x = 0; x < qrWidth; x++) {
                    if (matrix.get(x, y)) { // dark module = print dot
                        int byteIndex = y * widthBytes + (x / 8);
                        int bitIndex = 7 - (x % 8);
                        rasterData[byteIndex] |= (byte) (1 << bitIndex);
                    }
                }
            }

            out.write(ALIGN_CENTER);
            out.write(new byte[]{
                    0x1D, 0x76, 0x30, 0x00,             // GS v 0 m=0 (normal)
                    (byte) (widthBytes & 0xFF),          // xL
                    (byte) ((widthBytes >> 8) & 0xFF),   // xH
                    (byte) (qrHeight & 0xFF),            // yL
                    (byte) ((qrHeight >> 8) & 0xFF)      // yH
            });
            out.write(rasterData);

        } catch (Exception e) {
            log.warn("Could not generate QR code for ESC/POS ticket: {}", e.getMessage());
        }
    }
}
