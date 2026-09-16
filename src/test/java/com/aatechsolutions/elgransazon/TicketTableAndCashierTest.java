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
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
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
 * The payment ticket (both the full-order one and each split account) must tell the
 * customer which table the order belongs to and who collected that ticket, so a
 * charge can be traced back to the cashier/waiter who took the money.
 */
class TicketTableAndCashierTest {

    private SystemConfigurationService systemConfigurationService;
    private GlobalSystemConfigService globalSystemConfigService;
    private DateTimeService dateTimeService;

    @BeforeEach
    void setUp() {
        systemConfigurationService = mock(SystemConfigurationService.class);
        when(systemConfigurationService.getConfiguration()).thenReturn(SystemConfiguration.builder()
                .restaurantName("El Gran Sazón")
                .address("Calle 1 #23, Querétaro")
                .phone("4421234567")
                .rfc("AAA010101AAA")
                .taxRate(new BigDecimal("16.00"))
                .build());

        globalSystemConfigService = mock(GlobalSystemConfigService.class);
        when(globalSystemConfigService.getConfiguration()).thenReturn(
                GlobalSystemConfig.builder().systemName("SavoryCloud").build());

        dateTimeService = mock(DateTimeService.class);
        when(dateTimeService.formatToCompanyTime(any(LocalDateTime.class), anyString()))
                .thenReturn("12/09/2026 09:35");
        when(dateTimeService.nowLocal()).thenReturn(LocalDateTime.of(2026, 9, 12, 9, 35));
    }

    // ========== ESC/POS (thermal) ==========

    @Test
    @DisplayName("El ticket térmico del pedido imprime la mesa y quién cobró")
    void thermalOrderTicketShowsTableAndCollector() throws Exception {
        String ticket = escPosPrint(order(OrderType.DINE_IN, table(7)));

        assertTrue(ticket.contains("Mesa: 7"), ticket);
        assertTrue(ticket.contains("Cobrado por: Ana Cobradora"), ticket);
        assertTrue(ticket.contains("Atendido por: Luis Mesero"), ticket);
    }

    @Test
    @DisplayName("Sin mesa (para llevar) el ticket no imprime mesa, pero sí al cobrador")
    void thermalOrderTicketOmitsTableWhenThereIsNone() throws Exception {
        String ticket = escPosPrint(order(OrderType.TAKEOUT, null));

        assertFalse(ticket.contains("Mesa:"), ticket);
        assertTrue(ticket.contains("Cobrado por: Ana Cobradora"), ticket);
    }

    @Test
    @DisplayName("El ticket térmico de una cuenta imprime la mesa y al cajero que cobró ESA cuenta")
    void thermalAccountTicketShowsTableAndItsOwnCollector() throws Exception {
        Payment payment = account(employee("Carlos", "Cajero"));
        String ticket = escPosPrint(payment);

        assertTrue(ticket.contains("Mesa: 7"), ticket);
        assertTrue(ticket.contains("Cobrado por: Carlos Cajero"), ticket);
        assertFalse(ticket.contains("Ana Cobradora"), ticket);
    }

    @Test
    @DisplayName("Una cuenta sin cobrador propio usa el del pedido")
    void thermalAccountTicketFallsBackToTheOrderCollector() throws Exception {
        Payment payment = account(null);
        String ticket = escPosPrint(payment);

        assertTrue(ticket.contains("Cobrado por: Ana Cobradora"), ticket);
    }

    // ========== PDF ==========

    @Test
    @DisplayName("El ticket PDF del pedido imprime la mesa y quién cobró")
    void pdfOrderTicketShowsTableAndCollector() throws Exception {
        String ticket = pdfPrint(order(OrderType.DINE_IN, table(7)));

        assertTrue(ticket.contains("Mesa: 7"), ticket);
        assertTrue(ticket.contains("Cobrado por: Ana Cobradora"), ticket);
    }

    @Test
    @DisplayName("El ticket PDF de una cuenta imprime la mesa y al cobrador de esa cuenta")
    void pdfAccountTicketShowsTableAndItsOwnCollector() throws Exception {
        String ticket = pdfPrint(account(employee("Carlos", "Cajero")));

        assertTrue(ticket.contains("Mesa: 7"), ticket);
        assertTrue(ticket.contains("Cobrado por: Carlos Cajero"), ticket);
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

    private RestaurantTable table(int number) {
        return RestaurantTable.builder().tableNumber(number).capacity(4).build();
    }

    private Employee employee(String nombre, String apellido) {
        return Employee.builder().nombre(nombre).apellido(apellido).build();
    }

    private Order order(OrderType type, RestaurantTable table) {
        OrderDetail detail = OrderDetail.builder()
                .itemName("Café americano")
                .quantity(1)
                .unitPrice(new BigDecimal("35.00"))
                .subtotal(new BigDecimal("35.00"))
                .build();

        return Order.builder()
                .idOrder(76L)
                .orderNumber("ORD-20260912-001")
                .orderType(type)
                .status(OrderStatus.PAID)
                .table(table)
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

    private Payment account(Employee collector) {
        Order order = order(OrderType.DINE_IN, table(7));
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
                .paidBy(collector)
                .createdAt(LocalDateTime.of(2026, 9, 12, 9, 30))
                .paidAt(LocalDateTime.of(2026, 9, 12, 9, 35))
                .paymentDetails(new ArrayList<>(List.of(detail)))
                .build();
        order.setPayments(new ArrayList<>(List.of(payment)));
        return payment;
    }
}
