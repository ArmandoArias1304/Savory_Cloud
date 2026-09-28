package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.CloudflareImagesUrlHelper;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.GlobalSystemConfigService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.application.service.TicketEscPosService;
import com.aatechsolutions.elgransazon.application.service.TicketPdfService;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.Payment;
import com.aatechsolutions.elgransazon.domain.entity.PaymentDetail;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The two legends at the bottom of the ticket ("¡Gracias por su preferencia!" and
 * "Esperamos volver a atenderle pronto") are editable from the system configuration.
 * They must reach the thermal ticket and the PDF ticket, and a blank value must keep
 * printing the text the ticket has always printed.
 */
class TicketFooterLegendTest {

    private static final String CUSTOM_LINE_1 = "Pide tu factura aqui";
    private static final String CUSTOM_LINE_2 = "Conserva tu ticket";

    private SystemConfigurationService systemConfigurationService;
    private GlobalSystemConfigService globalSystemConfigService;
    private DateTimeService dateTimeService;

    private void configure(String line1, String line2) {
        systemConfigurationService = mock(SystemConfigurationService.class);
        when(systemConfigurationService.getConfiguration()).thenReturn(SystemConfiguration.builder()
                .restaurantName("El Gran Sazón")
                .address("Calle 1 #23, Querétaro")
                .phone("4421234567")
                .rfc("AAA010101AAA")
                .taxRate(new BigDecimal("16.00"))
                .ticketFooterLine1(line1)
                .ticketFooterLine2(line2)
                .build());

        globalSystemConfigService = mock(GlobalSystemConfigService.class);
        when(globalSystemConfigService.getConfiguration()).thenReturn(
                GlobalSystemConfig.builder().systemName("SavoryCloud").build());

        dateTimeService = mock(DateTimeService.class);
        when(dateTimeService.formatToCompanyTime(any(LocalDateTime.class), anyString()))
                .thenReturn("12/09/2026 09:35");
        when(dateTimeService.nowLocal()).thenReturn(LocalDateTime.of(2026, 9, 12, 9, 35));
    }

    // ========== Leyendas configurables ==========

    @Test
    @DisplayName("El ticket térmico del pedido imprime las leyendas configuradas")
    void thermalOrderTicketUsesConfiguredLegends() throws Exception {
        configure(CUSTOM_LINE_1, CUSTOM_LINE_2);

        String ticket = escPosPrint(order());

        assertTrue(ticket.contains(CUSTOM_LINE_1), ticket);
        assertTrue(ticket.contains(CUSTOM_LINE_2), ticket);
        assertFalse(ticket.contains(SystemConfiguration.DEFAULT_TICKET_FOOTER_LINE_1), ticket);
    }

    @Test
    @DisplayName("El ticket térmico de una cuenta dividida también usa las leyendas configuradas")
    void thermalAccountTicketUsesConfiguredLegends() throws Exception {
        configure(CUSTOM_LINE_1, CUSTOM_LINE_2);

        String ticket = escPosPrint(account());

        assertTrue(ticket.contains(CUSTOM_LINE_1), ticket);
        assertTrue(ticket.contains(CUSTOM_LINE_2), ticket);
    }

    @Test
    @DisplayName("El ticket PDF imprime las leyendas configuradas (pedido y cuenta)")
    void pdfTicketsUseConfiguredLegends() throws Exception {
        configure(CUSTOM_LINE_1, CUSTOM_LINE_2);

        String orderTicket = pdfPrint(order());
        assertTrue(orderTicket.contains(CUSTOM_LINE_1), orderTicket);
        assertTrue(orderTicket.contains(CUSTOM_LINE_2), orderTicket);

        String accountTicket = pdfPrint(account());
        assertTrue(accountTicket.contains(CUSTOM_LINE_1), accountTicket);
        assertTrue(accountTicket.contains(CUSTOM_LINE_2), accountTicket);
    }

    // ========== Respaldo cuando la leyenda está vacía ==========

    @Test
    @DisplayName("Sin leyendas configuradas se imprime el texto de siempre")
    void blankLegendsFallBackToTheBuiltInText() throws Exception {
        configure(null, "   ");

        String orderTicket = escPosPrint(order());
        assertTrue(orderTicket.contains(SystemConfiguration.DEFAULT_TICKET_FOOTER_LINE_1), orderTicket);
        assertTrue(orderTicket.contains(SystemConfiguration.DEFAULT_TICKET_FOOTER_LINE_2), orderTicket);

        String pdfTicket = pdfPrint(order());
        assertTrue(pdfTicket.contains(SystemConfiguration.DEFAULT_TICKET_FOOTER_LINE_1), pdfTicket);
        assertTrue(pdfTicket.contains(SystemConfiguration.DEFAULT_TICKET_FOOTER_LINE_2), pdfTicket);
    }

    @Test
    @DisplayName("La configuración vacía resuelve las leyendas por defecto")
    void configurationResolvesBlankLegends() {
        SystemConfiguration empty = SystemConfiguration.builder().build();

        assertTrue(empty.getTicketFooterLine1()
                .equals(SystemConfiguration.DEFAULT_TICKET_FOOTER_LINE_1));
        assertTrue(empty.getTicketFooterLine2()
                .equals(SystemConfiguration.DEFAULT_TICKET_FOOTER_LINE_2));

        SystemConfiguration custom = SystemConfiguration.builder()
                .ticketFooterLine1("  " + CUSTOM_LINE_1 + "  ")
                .ticketFooterLine2(CUSTOM_LINE_2)
                .build();
        assertTrue(custom.getTicketFooterLine1().equals(CUSTOM_LINE_1),
                "the legend should be trimmed and returned as configured");
        assertTrue(custom.getTicketFooterLine2().equals(CUSTOM_LINE_2));
    }

    // ========== Helpers ==========

    private String escPosPrint(Order order) throws Exception {
        return new String(escPosService().generateTicket(order), Charset.forName("Cp1252"));
    }

    private String escPosPrint(Payment payment) throws Exception {
        return new String(escPosService().generateTicket(payment), Charset.forName("Cp1252"));
    }

    private String pdfPrint(Order order) throws Exception {
        return extractText(pdfService().generateTicket(order));
    }

    private String pdfPrint(Payment payment) throws Exception {
        return extractText(pdfService().generateTicket(payment));
    }

    private String extractText(byte[] pdf) throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= pdfDoc.getNumberOfPages(); page++) {
                text.append(PdfTextExtractor.getTextFromPage(pdfDoc.getPage(page))).append('\n');
            }
            return text.toString();
        }
    }

    private TicketEscPosService escPosService() {
        return new TicketEscPosService(systemConfigurationService, globalSystemConfigService,
                dateTimeService, mock(CloudflareImagesUrlHelper.class));
    }

    private TicketPdfService pdfService() {
        return new TicketPdfService(systemConfigurationService, globalSystemConfigService,
                dateTimeService, mock(CloudflareImagesUrlHelper.class));
    }

    private Employee employee(String nombre, String apellido) {
        return Employee.builder().nombre(nombre).apellido(apellido).build();
    }

    private Order order() {
        OrderDetail detail = OrderDetail.builder()
                .itemName("Café americano")
                .quantity(1)
                .unitPrice(new BigDecimal("35.00"))
                .subtotal(new BigDecimal("35.00"))
                .build();

        return Order.builder()
                .idOrder(76L)
                .orderNumber("ORD-20260912-001")
                .orderType(OrderType.DINE_IN)
                .status(OrderStatus.PAID)
                .employee(employee("Luis", "Mesero"))
                .paidBy(employee("Ana", "Cobradora"))
                .paymentMethod(PaymentMethodType.CASH)
                .createdAt(LocalDateTime.of(2026, 9, 12, 9, 30))
                .paidAt(LocalDateTime.of(2026, 9, 12, 9, 35))
                .subtotal(new BigDecimal("30.17"))
                .taxAmount(new BigDecimal("4.83"))
                .taxRate(new BigDecimal("16.00"))
                .total(new BigDecimal("35.00"))
                .orderDetails(new ArrayList<>(List.of(detail)))
                .build();
    }

    private Payment account() {
        Order order = order();
        PaymentDetail detail = PaymentDetail.builder()
                .itemName("Café americano")
                .quantity(BigDecimal.ONE)
                .unitPrice(new BigDecimal("35.00"))
                .subtotal(new BigDecimal("35.00"))
                .total(new BigDecimal("35.00"))
                .build();

        Payment payment = Payment.builder()
                .idPayment(100L)
                .order(order)
                .accountNumber(2)
                .paymentFolio("ORD-20260912-001-02")
                .personLabel("Persona 2")
                .subtotal(new BigDecimal("30.17"))
                .taxAmount(new BigDecimal("4.83"))
                .taxRate(new BigDecimal("16.00"))
                .total(new BigDecimal("35.00"))
                .deliveryCost(BigDecimal.ZERO)
                .paymentMethod(PaymentMethodType.CASH)
                .paidBy(employee("Carlos", "Cajero"))
                .createdAt(LocalDateTime.of(2026, 9, 12, 9, 30))
                .paidAt(LocalDateTime.of(2026, 9, 12, 9, 35))
                .paymentDetails(new ArrayList<>(List.of(detail)))
                .build();
        order.setPayments(new ArrayList<>(List.of(payment)));
        return payment;
    }
}
