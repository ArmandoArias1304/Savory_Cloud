package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;

/**
 * Service for generating PDF tickets for orders
 * Optimized for thermal printers (58mm or 80mm)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketPdfService {

    private final SystemConfigurationService systemConfigurationService;
    private final GlobalSystemConfigService globalSystemConfigService;
    private final DateTimeService dateTimeService;
    private final CloudflareImagesUrlHelper cloudflareImagesUrlHelper;

    // Ticket width in points (58mm = 164 points, 80mm = 226 points)
    private static final float TICKET_WIDTH = 226f; // 80mm
    private static final float MARGIN = 10f;

    /**
     * Generate PDF ticket for an order
     * @param order Order to generate ticket for
     * @return byte array containing the PDF
     */
    public byte[] generateTicket(Order order) throws IOException {
        log.info("Generating PDF ticket for order: {}", order.getOrderNumber());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Create PDF with custom page size (ticket size)
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        
        // Calculate page height dynamically based on content
        float estimatedHeight = calculateEstimatedHeight(order);
        PageSize pageSize = new PageSize(TICKET_WIDTH, estimatedHeight);
        pdfDoc.setDefaultPageSize(pageSize);
        
        Document document = new Document(pdfDoc);
        document.setMargins(MARGIN, MARGIN, MARGIN, MARGIN);

        // Get system configuration
        SystemConfiguration config = systemConfigurationService.getConfiguration();

        // Create fonts
        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont normalFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        // Add logo from company's restaurantLogoUrl (centered)
        // iText does not support WebP, so force PNG conversion via Cloudinary transformation
        try {
            String logoUrl = config.getRestaurantLogoUrl();
            if (logoUrl != null && !logoUrl.isBlank()) {
                String pdfLogoUrl = cloudflareImagesUrlHelper.transform(logoUrl, "w=200,h=200,fit=contain,format=png,quality=85");
                Image logo = new Image(ImageDataFactory.create(new java.net.URL(pdfLogoUrl)));
                logo.setWidth(60);
                logo.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
                document.add(logo);
            }
        } catch (Exception e) {
            log.warn("Could not load logo image: {}", e.getMessage());
        }

        // Restaurant name (centered, bold)
        Paragraph restaurantName = new Paragraph(config.getRestaurantName())
                .setFont(boldFont)
                .setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(5);
        document.add(restaurantName);

        // Address (centered)
        Paragraph address = new Paragraph(config.getAddress())
                .setFont(normalFont)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(2);
        document.add(address);

        // RFC (centered, below address)
        if (config.getRfc() != null && !config.getRfc().isBlank()) {
            Paragraph rfc = new Paragraph("RFC: " + config.getRfc())
                    .setFont(normalFont)
                    .setFontSize(8)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(2);
            document.add(rfc);
        }

        // Phone (centered)
        Paragraph phone = new Paragraph("Tel: " + config.getPhone())
                .setFont(normalFont)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(2);
        document.add(phone);

        // Separator line
        document.add(new Paragraph("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(5)
                .setMarginBottom(5));

        // Order number (bold, larger)
        Paragraph orderNum = new Paragraph("ORDEN: " + order.getOrderNumber())
                .setFont(boldFont)
                .setFontSize(12)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(5);
        document.add(orderNum);

        // Items separator
        document.add(new Paragraph("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(3)
                .setMarginBottom(3));

        // Items header
        Paragraph itemsHeader = new Paragraph("DETALLE DEL PEDIDO")
                .setFont(boldFont)
                .setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(5);
        document.add(itemsHeader);

        // Check if any item has a promotion (to decide whether to show discount column)
        boolean hasPromotions = order.getOrderDetails().stream()
                .anyMatch(d -> d.getPromotionAppliedPrice() != null && 
                               d.getPromotionAppliedPrice().compareTo(d.getUnitPrice()) < 0);

        // Order items table - with or without discount column
        Table itemsTable;
        if (hasPromotions) {
            // 4 columns: Item, Qty, Total, T. Final
            itemsTable = new Table(new float[]{2.5f, 0.8f, 1.5f, 1.2f});
            itemsTable.setWidth(UnitValue.createPercentValue(100));
            itemsTable.setBorder(Border.NO_BORDER);
            
            // Header row
            itemsTable.addCell(new Cell()
                    .add(new Paragraph("Producto").setFont(boldFont).setFontSize(7))
                    .setBorder(Border.NO_BORDER).setPadding(1));
            itemsTable.addCell(new Cell()
                    .add(new Paragraph("Cant").setFont(boldFont).setFontSize(7).setTextAlignment(TextAlignment.CENTER))
                    .setBorder(Border.NO_BORDER).setPadding(1));
            itemsTable.addCell(new Cell()
                    .add(new Paragraph("Total").setFont(boldFont).setFontSize(7).setTextAlignment(TextAlignment.RIGHT))
                    .setBorder(Border.NO_BORDER).setPadding(1));
            itemsTable.addCell(new Cell()
                    .add(new Paragraph("T. Final").setFont(boldFont).setFontSize(7).setTextAlignment(TextAlignment.RIGHT))
                    .setBorder(Border.NO_BORDER).setPadding(1));
        } else {
            // 3 columns: Nombre del producto, Cant, Total (no promotions)
            itemsTable = new Table(new float[]{3, 1, 2});
            itemsTable.setWidth(UnitValue.createPercentValue(100));
            itemsTable.setBorder(Border.NO_BORDER);
            
            // Header row for non-promotion table
            itemsTable.addCell(new Cell()
                    .add(new Paragraph("Producto").setFont(boldFont).setFontSize(7))
                    .setBorder(Border.NO_BORDER).setPadding(1));
            itemsTable.addCell(new Cell()
                    .add(new Paragraph("Cant").setFont(boldFont).setFontSize(7).setTextAlignment(TextAlignment.CENTER))
                    .setBorder(Border.NO_BORDER).setPadding(1));
            itemsTable.addCell(new Cell()
                    .add(new Paragraph("Total").setFont(boldFont).setFontSize(7).setTextAlignment(TextAlignment.RIGHT))
                    .setBorder(Border.NO_BORDER).setPadding(1));
        }

        List<OrderDetail> ticketOrder = order.getOrderDetails();

        for (OrderDetail detail : ticketOrder) {
            // Item name and quantity - with combo grouping
            String itemName = detail.getDisplayName();
            boolean isComboParent = detail.isComboParent();
            boolean isComboChild = detail.isComboChild();
            if (isComboParent) {
                itemName = "[COMBO] " + itemName;
            } else if (isComboChild) {
                itemName = "  \u21B3 " + itemName;
            }

            // Use original values
            Integer quantity = detail.getQuantity();
            BigDecimal precioOriginalConIVA = detail.getUnitPrice().multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal precioFinalConIVA = detail.getSubtotal();
            
            Cell nameCell = new Cell()
                    .add(new Paragraph(itemName)
                            .setFont(normalFont)
                            .setFontSize(7))
                    .setBorder(Border.NO_BORDER)
                    .setPadding(1);
            if (isComboChild) {
                nameCell.setPaddingLeft(8);
            }
            
            Cell qtyCell = new Cell()
                    .add(new Paragraph(quantity.toString())
                            .setFont(normalFont)
                            .setFontSize(7)
                            .setTextAlignment(TextAlignment.CENTER))
                    .setBorder(Border.NO_BORDER)
                    .setPadding(1);
            
            // Price cell shows original price (unitPrice × quantity)
            Cell priceCell = new Cell()
                    .add(new Paragraph("$" + precioOriginalConIVA.toString())
                            .setFont(normalFont)
                            .setFontSize(7)
                            .setTextAlignment(TextAlignment.RIGHT))
                    .setBorder(Border.NO_BORDER)
                    .setPadding(1);
            
            itemsTable.addCell(nameCell);
            itemsTable.addCell(qtyCell);
            itemsTable.addCell(priceCell);
            
            // Add discounted price cell if promotions exist
            if (hasPromotions) {
                boolean hasDiscount = precioFinalConIVA.compareTo(precioOriginalConIVA) < 0;
                String finalPriceText;
                com.itextpdf.kernel.colors.Color textColor;
                
                if (hasDiscount) {
                    if (precioFinalConIVA.compareTo(BigDecimal.ZERO) == 0) {
                        finalPriceText = "GRATIS";
                    } else {
                        finalPriceText = "$" + precioFinalConIVA.setScale(2, RoundingMode.HALF_UP).toString();
                    }
                    textColor = new DeviceRgb(22, 163, 74); // green-600 (darker green)
                } else {
                    finalPriceText = "$" + precioOriginalConIVA.toString();
                    textColor = ColorConstants.BLACK;
                }
                
                Cell discountCell = new Cell()
                        .add(new Paragraph(finalPriceText)
                                .setFont(normalFont)
                                .setFontSize(7)
                                .setTextAlignment(TextAlignment.RIGHT)
                                .setFontColor(textColor))
                        .setBorder(Border.NO_BORDER)
                        .setPadding(1);
                itemsTable.addCell(discountCell);
            }

            // Add comments if any (strip legacy [Combo: ...] prefix)
            String displayComments = detail.getDisplayComments();
            if (displayComments != null && !displayComments.isEmpty()) {
                int colspan = hasPromotions ? 4 : 3;
                Cell commentCell = new Cell(1, colspan)
                        .add(new Paragraph("  → " + displayComments)
                                .setFont(normalFont)
                                .setFontSize(6)
                                .setFontColor(ColorConstants.DARK_GRAY))
                        .setBorder(Border.NO_BORDER)
                        .setPadding(0)
                        .setPaddingLeft(isComboChild ? 12 : 5);
                itemsTable.addCell(commentCell);
            }
            
            // Add complements if any
            if (detail.getSelectedComplements() != null && !detail.getSelectedComplements().isEmpty()) {
                for (OrderDetailComplement odc : detail.getSelectedComplements()) {
                    String complementName = "  + " + odc.getComplementName();
                    Integer compQuantity = odc.getQuantity();
                    BigDecimal compTotal = odc.getSubtotal();

                    // Complement name cell (indented, extra for combo children)
                    Cell compNameCell = new Cell()
                            .add(new Paragraph(complementName)
                                    .setFont(normalFont)
                                    .setFontSize(6)
                                    .setFontColor(ColorConstants.DARK_GRAY))
                            .setBorder(Border.NO_BORDER)
                            .setPadding(1)
                            .setPaddingLeft(isComboChild ? 12 : 5);
                    
                    // Complement quantity cell
                    Cell compQtyCell = new Cell()
                            .add(new Paragraph(compQuantity.toString())
                                    .setFont(normalFont)
                                    .setFontSize(6)
                                    .setFontColor(ColorConstants.DARK_GRAY)
                                    .setTextAlignment(TextAlignment.CENTER))
                            .setBorder(Border.NO_BORDER)
                            .setPadding(1);
                    
                    // Complement total cell
                    String compTotalStr = "$" + compTotal.setScale(2, RoundingMode.HALF_UP).toString();
                    Cell compTotalCell = new Cell()
                            .add(new Paragraph(compTotalStr)
                                    .setFont(normalFont)
                                    .setFontSize(6)
                                    .setFontColor(ColorConstants.DARK_GRAY)
                                    .setTextAlignment(TextAlignment.RIGHT))
                            .setBorder(Border.NO_BORDER)
                            .setPadding(1);
                    
                    itemsTable.addCell(compNameCell);
                    itemsTable.addCell(compQtyCell);
                    itemsTable.addCell(compTotalCell);
                    
                    // If has promotions, add the repeated total for T. Final column (complements don't have discounts)
                    if (hasPromotions) {
                        Cell compFinalCell = new Cell()
                                .add(new Paragraph(compTotalStr)
                                        .setFont(normalFont)
                                        .setFontSize(6)
                                        .setFontColor(ColorConstants.DARK_GRAY)
                                        .setTextAlignment(TextAlignment.RIGHT))
                                .setBorder(Border.NO_BORDER)
                                .setPadding(1);
                        itemsTable.addCell(compFinalCell);
                    }
                }
            }
        }

        document.add(itemsTable);

        // Totals separator
        document.add(new Paragraph("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(3)
                .setMarginBottom(3));

        // Subtotal e IVA INCLUYEN el costo de envío Y descuento por promoción (consistente con el CFDI).
        // - order.getSubtotal()  = subtotal sin IVA, incluyendo envío, después de descuento
        // - order.getTaxAmount() = IVA total, incluyendo el IVA del envío, después de descuento
        // El envío y el descuento se muestran como notas informativas después del TOTAL.
        BigDecimal taxAmount = order.getTaxAmount();
        BigDecimal total = order.getTotal();

                // Totals table
                Table totalsTable = new Table(new float[]{3, 2});
                totalsTable.setWidth(UnitValue.createPercentValue(100));
                totalsTable.setBorder(Border.NO_BORDER);

                // Subtotal: sin IVA, ANTES del descuento de orden (incluye envío sin IVA y descuento por promoción).
                addTotalRow(totalsTable, "Subtotal:", order.getFormattedDisplaySubtotal(), normalFont, boldFont, false);

                // Descuento de orden sin IVA (cuando aplica). Aparece debajo del Subtotal y por encima del IVA.
                if (order.hasOrderDiscount()) {
                    addTotalRow(totalsTable, "Descuento de orden:", "-" + order.getFormattedOrderDiscountWithoutTax(), normalFont, boldFont, false);
                }

                // IVA total (incluye IVA de envío y el IVA del descuento de orden permanece dentro de esta línea)
                addTotalRow(totalsTable, "IVA (" + order.getTaxRate() + "%):", "$" + taxAmount.toString(), normalFont, boldFont, false);

                // Total (bold) - sin propina
                addTotalRow(totalsTable, "TOTAL:", "$" + total.setScale(2, RoundingMode.HALF_UP).toString(), boldFont, boldFont, true);

                /* Tip below total (if any)
                if (order.getTip() != null && order.getTip().compareTo(BigDecimal.ZERO) > 0) {
                        addTotalRow(totalsTable, "Propina:", "$" + order.getTip().toString(), normalFont, boldFont, false);
                }*/

                document.add(totalsTable);

        // Total in words (Mexican format)
        Paragraph totalEnLetraParagraph = new Paragraph(totalEnLetra(total))
                .setFont(normalFont)
                .setFontSize(7)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(2)
                .setMarginBottom(3);
        document.add(totalEnLetraParagraph);

        // Nota informativa: del total, cuánto fue envío (DELIVERY only) — primer lugar
        if (order.getOrderType() == OrderType.DELIVERY
                && order.getDeliveryCost() != null
                && order.getDeliveryCost().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal envioGross = order.getDeliveryCost().setScale(2, RoundingMode.HALF_UP);
            Paragraph envioNote = new Paragraph("Incluye costo de envío de $" + envioGross.toPlainString())
                    .setFont(normalFont)
                    .setFontSize(7)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(2);
            document.add(envioNote);
        }

        // Nota informativa: descuento por promoción (incluido en subtotal/IVA) — segundo lugar
        if (order.hasDiscount()) {
            Paragraph descuentoNote = new Paragraph("Incluye descuento por promoción de " + order.getFormattedDiscountWithTax())
                    .setFont(normalFont)
                    .setFontSize(7)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(2);
            document.add(descuentoNote);
        }

        // Nota informativa: descuento aplicado al total de la orden (incluye IVA)
        if (order.hasOrderDiscount()) {
            Paragraph orderDiscountNote = new Paragraph("Descuento aplicado de " + order.getFormattedOrderDiscount())
                    .setFont(normalFont)
                    .setFontSize(7)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(2);
            document.add(orderDiscountNote);
        }

        // Order type and payment method in one line
        Paragraph orderInfoParagraph = new Paragraph()
                .add(new Text("Tipo: ").setFont(boldFont))
                .add(new Text(order.getOrderType().getDisplayName()).setFont(normalFont))
                .add(new Text(" | ").setFont(normalFont))
                .add(new Text("Pago: ").setFont(boldFont))
                .add(new Text(order.getPaymentMethod() != null ? order.getPaymentMethod().getDisplayName() : "N/A").setFont(normalFont))
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(5);
        document.add(orderInfoParagraph);

        // Customer name (if available)
        if (order.getCustomerName() != null && !order.getCustomerName().trim().isEmpty()) {
            Paragraph customer = new Paragraph()
                    .add(new Text("Cliente: ").setFont(boldFont))
                    .add(new Text(order.getCustomerName()).setFont(normalFont))
                    .setFontSize(8)
                    .setTextAlignment(TextAlignment.CENTER);
            document.add(customer);
        }

        // Served by (if customer order, show restaurant name)
        String servedBy = order.getEmployee() != null ? order.getEmployee().getFullName() : config.getRestaurantName();
        Paragraph employee = new Paragraph()
                .add(new Text("Atendido por: ").setFont(boldFont))
                .add(new Text(servedBy).setFont(normalFont))
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(5);
        document.add(employee);

        // Date and time separator
        document.add(new Paragraph("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(5)
                .setMarginBottom(3));

        // Date and time
        Paragraph dateTime;
        if (order.getStatus() == OrderStatus.PAID) {
         dateTime = new Paragraph("Pagado: " + dateTimeService.formatToCompanyTime(order.getPaidAt(), "dd/MM/yyyy HH:mm"));
        } else {
            dateTime = new Paragraph("Creada: " + dateTimeService.formatToCompanyTime(order.getCreatedAt(), "dd/MM/yyyy HH:mm"));
        }
        dateTime.setFont(normalFont)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(5);
        document.add(dateTime);

        // Final separator
        document.add(new Paragraph("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(5)
                .setMarginBottom(5));

        // Thank you message
        Paragraph thankYou = new Paragraph("¡Gracias por su preferencia!")
                .setFont(boldFont)
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(5);
        document.add(thankYou);

        Paragraph visitAgain = new Paragraph("Esperamos volver a atenderle pronto")
                .setFont(normalFont)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(2)
                .setMarginBottom(5);
        document.add(visitAgain);

        // Fiscal disclaimer / Autofactura billing info
        if (order.getAutofacturaKey() != null && !order.getAutofacturaKey().isBlank()) {
            // Order has an autofactura key — show billing section with QR code
            document.add(new Paragraph("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(3)
                    .setMarginBottom(3));

            Paragraph billingHint = new Paragraph("Facture este ticket escaneando el código QR:")
                    .setFont(normalFont)
                    .setFontSize(7)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(2);
            document.add(billingHint);

            // Generate QR code with the autofactura URL
            if (order.getSelfInvoiceUrl() != null) {
                try {
                    byte[] qrBytes = generateQrCodePng(order.getSelfInvoiceUrl(), 150);
                    Image qrImage = new Image(ImageDataFactory.create(qrBytes));
                    qrImage.setWidth(80);
                    qrImage.setHeight(80);
                    qrImage.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
                    qrImage.setMarginTop(3);
                    qrImage.setMarginBottom(3);
                    document.add(qrImage);
                } catch (Exception e) {
                    log.warn("Could not generate QR code for autofactura: {}", e.getMessage());
                }
            }

            // Invoicing deadline legend (last day of payment month, in company timezone)
            java.time.LocalDate deadline = order.getInvoiceDeadline(
                    com.aatechsolutions.elgransazon.infrastructure.util.CompanyLocalTime.getZone());
            if (deadline != null) {
                String deadlineText = deadline.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                Paragraph deadlineLegend = new Paragraph("Facture antes del " + deadlineText)
                        .setFont(boldFont)
                        .setFontSize(7)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginTop(2)
                        .setMarginBottom(2);
                document.add(deadlineLegend);
            }

            document.add(new Paragraph("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(3)
                    .setMarginBottom(3));
        } else {
            Paragraph fiscalDisclaimer = new Paragraph("Este no es un comprobante fiscal")
                    .setFont(normalFont)
                    .setFontSize(7)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(3);
            document.add(fiscalDisclaimer);
        }

        // System branding footer (GLOBAL - not per-company)
        String systemName = globalSystemConfigService.getConfiguration().getSystemName();
        Paragraph systemBranding = new Paragraph("by " + systemName)
                .setFont(normalFont)
                .setFontSize(6)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY)
                .setMarginBottom(10);
        document.add(systemBranding);

        document.close();

        log.info("PDF ticket generated successfully for order: {}", order.getOrderNumber());
        return baos.toByteArray();
    }

    /**
     * Add a row to the totals table
     */
    private void addTotalRow(Table table, String label, String value, PdfFont valueFont, PdfFont labelFont, boolean isBold) {
        int fontSize = isBold ? 10 : 8;
        
        Cell labelCell = new Cell()
                .add(new Paragraph(label)
                        .setFont(labelFont)
                        .setFontSize(fontSize))
                .setBorder(Border.NO_BORDER)
                .setPadding(2)
                .setTextAlignment(TextAlignment.RIGHT);
        
        Cell valueCell = new Cell()
                .add(new Paragraph(value)
                        .setFont(valueFont)
                        .setFontSize(fontSize))
                .setBorder(Border.NO_BORDER)
                .setPadding(2)
                .setTextAlignment(TextAlignment.RIGHT);
        
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    /**
     * Add a row to the totals table with a specific color
     */
    /*private void addTotalRowColored(Table table, String label, String value, PdfFont valueFont, PdfFont labelFont, boolean isBold, com.itextpdf.kernel.colors.Color color) {
        int fontSize = isBold ? 10 : 8;
        
        Cell labelCell = new Cell()
                .add(new Paragraph(label)
                        .setFont(labelFont)
                        .setFontSize(fontSize)
                        .setFontColor(color))
                .setBorder(Border.NO_BORDER)
                .setPadding(2)
                .setTextAlignment(TextAlignment.RIGHT);
        
        Cell valueCell = new Cell()
                .add(new Paragraph(value)
                        .setFont(valueFont)
                        .setFontSize(fontSize)
                        .setFontColor(color))
                .setBorder(Border.NO_BORDER)
                .setPadding(2)
                .setTextAlignment(TextAlignment.RIGHT);
        
        table.addCell(labelCell);
        table.addCell(valueCell);
    }*/

    /**
     * Convert a BigDecimal amount to Mexican legal text format.
     * Example: 1234.56 -> "Son: Un mil doscientos treinta y cuatro pesos 56/100 M.N."
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
     * Calculate estimated height for the PDF page
     */
    private float calculateEstimatedHeight(Order order) {
        float baseHeight = 420f; // Base height for header, footer, etc. (increased for header row)
        float itemHeight = 15f; // Height per item
        float commentHeight = 10f; // Height for comments
        float complementHeight = 10f; // Height per complement
        float discountRowHeight = 15f; // Height for discount row in totals
        
        // Check if any item has a promotion
        boolean hasPromotions = order.getOrderDetails().stream()
                .anyMatch(d -> d.getPromotionAppliedPrice() != null && 
                               d.getPromotionAppliedPrice().compareTo(d.getUnitPrice()) < 0);
        
        if (hasPromotions) {
            baseHeight += discountRowHeight; // Add space for the discount row in totals
        }
        
        for (OrderDetail detail : order.getOrderDetails()) {
            baseHeight += itemHeight;
            if (detail.getComments() != null && !detail.getComments().trim().isEmpty()) {
                baseHeight += commentHeight;
            }
            // Add height for each complement
            if (detail.getSelectedComplements() != null) {
                baseHeight += detail.getSelectedComplements().size() * complementHeight;
            }
        }
        
        return baseHeight;
    }

    /**
     * Generate a QR code as a PNG byte array.
     */
    private byte[] generateQrCodePng(String text, int size) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new java.util.EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size, hints);

        int width = matrix.getWidth();
        int height = matrix.getHeight();
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }
}
