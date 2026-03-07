package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

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

        // Add logo (centered)
        try {
            ClassPathResource imgResource = new ClassPathResource("static/images/LogoVariante.png");
            Image logo = new Image(ImageDataFactory.create(imgResource.getURL()));
            logo.setWidth(60);
            logo.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
            document.add(logo);
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

        for (OrderDetail detail : order.getOrderDetails()) {
            // Item name and quantity - with combo grouping
            String itemName = detail.getItemMenu().getName();
            boolean isComboParent = detail.isComboParent();
            boolean isComboChild = detail.isComboChild();
            if (isComboParent) {
                itemName = "[COMBO] " + itemName;
            } else if (isComboChild) {
                itemName = "  \u21B3 " + itemName;
            }
            Integer quantity = detail.getQuantity();
            BigDecimal unitPrice = detail.getUnitPrice(); // Original price with IVA
            BigDecimal promotionAppliedPrice = detail.getPromotionAppliedPrice(); // Discounted price with IVA per unit (or null)
            
            // Calculate original price (with IVA) for this line = unitPrice × quantity
            BigDecimal precioOriginalConIVA = unitPrice.multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP);
            
            // Calculate discounted price = promotionAppliedPrice × quantity (or same as original if no promo)
            // This is the subtotal stored in OrderDetail
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
                boolean hasDiscount = promotionAppliedPrice != null && promotionAppliedPrice.compareTo(unitPrice) < 0;
                String finalPriceText;
                com.itextpdf.kernel.colors.Color textColor;
                
                if (hasDiscount) {
                    if (precioFinalConIVA.compareTo(BigDecimal.ZERO) == 0) {
                        finalPriceText = "GRATIS";
                    } else {
                        finalPriceText = "$" + precioFinalConIVA.setScale(2, RoundingMode.HALF_UP).toString();
                    }
                    textColor = ColorConstants.GREEN;
                } else {
                    finalPriceText = "-";
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
                    String complementName = "  + " + odc.getComplement().getName();
                    Integer compQuantity = odc.getQuantity();
                    BigDecimal compTotal = odc.getSubtotal(); // unitPrice × quantity
                    
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
                    Cell compTotalCell = new Cell()
                            .add(new Paragraph("$" + compTotal.setScale(2, RoundingMode.HALF_UP).toString())
                                    .setFont(normalFont)
                                    .setFontSize(6)
                                    .setFontColor(ColorConstants.DARK_GRAY)
                                    .setTextAlignment(TextAlignment.RIGHT))
                            .setBorder(Border.NO_BORDER)
                            .setPadding(1);
                    
                    itemsTable.addCell(compNameCell);
                    itemsTable.addCell(compQtyCell);
                    itemsTable.addCell(compTotalCell);
                    
                    // If has promotions, add "-" for T. Final column (complements don't have discounts)
                    if (hasPromotions) {
                        Cell compFinalCell = new Cell()
                                .add(new Paragraph("-")
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

        // Calcular subtotal original sin IVA (antes de descuento) - INCLUYENDO COMPLEMENTOS
        BigDecimal taxMultiplier = order.getTaxRate().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal onePlusTax = BigDecimal.ONE.add(taxMultiplier);
        
        // Subtotal de items (precio original × cantidad) sin IVA
        BigDecimal subtotalItemsSinIVA = order.getOrderDetails().stream()
                .map(d -> d.getUnitPrice().multiply(BigDecimal.valueOf(d.getQuantity()))
                        .divide(onePlusTax, 10, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Subtotal de complementos sin IVA (using getComplementsTotal which correctly handles sauce × quantity)
        BigDecimal subtotalComplementosSinIVA = order.getOrderDetails().stream()
                .map(d -> d.getComplementsTotal().divide(onePlusTax, 10, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Subtotal original total sin IVA (items + complementos, antes de descuento)
        BigDecimal subtotalOriginalSinIVA = subtotalItemsSinIVA.add(subtotalComplementosSinIVA);

        BigDecimal taxAmount = order.getTaxAmount();
        BigDecimal total = order.getTotal();

                // Totals table
                Table totalsTable = new Table(new float[]{3, 2});
                totalsTable.setWidth(UnitValue.createPercentValue(100));
                totalsTable.setBorder(Border.NO_BORDER);

                // Subtotal (mostrar subtotal original sin IVA incluyendo complementos, antes de descuento)
                // Redondear a dos decimales con HALF_UP (consistente con Order.subtotal)
                BigDecimal subtotalRounded = subtotalOriginalSinIVA.setScale(2, RoundingMode.HALF_UP);
                addTotalRow(totalsTable, "Subtotal:", "$" + subtotalRounded.toPlainString(), normalFont, boldFont, false);

                // Fila de descuento (solo por promociones, no incluye complementos)
                // Usar Order.getDiscountWithoutTax() para consistencia con view.html
                if (order.hasDiscount()) {
                        BigDecimal descuentoValue = order.getDiscountWithoutTax();
                        addTotalRowColored(totalsTable, "Descuento:", "-$" + descuentoValue.toPlainString(), normalFont, boldFont, false, ColorConstants.RED);
                }

                // IVA (ya calculado sobre el subtotal real)
                addTotalRow(totalsTable, "IVA (" + order.getTaxRate() + "%):", "$" + taxAmount.toString(), normalFont, boldFont, false);

                // Tip (if any)
                BigDecimal totalWithTip = total;
                if (order.getTip() != null && order.getTip().compareTo(BigDecimal.ZERO) > 0) {
                        addTotalRow(totalsTable, "Propina:", "$" + order.getTip().toString(), normalFont, boldFont, false);
                        totalWithTip = total.add(order.getTip());
                }

                // Total (bold)
                addTotalRow(totalsTable, "TOTAL:", "$" + totalWithTip.setScale(2, RoundingMode.HALF_UP).toString(), boldFont, boldFont, true);

                document.add(totalsTable);

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
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        Paragraph dateTime = new Paragraph(order.getCreatedAt().format(formatter))
                .setFont(normalFont)
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
    private void addTotalRowColored(Table table, String label, String value, PdfFont valueFont, PdfFont labelFont, boolean isBold, com.itextpdf.kernel.colors.Color color) {
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
}
