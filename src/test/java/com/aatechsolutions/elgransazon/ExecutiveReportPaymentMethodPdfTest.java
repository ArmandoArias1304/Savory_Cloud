package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.ReportPdfService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.PaymentTender;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.util.PaymentTenderSupport;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The executive report must split the money of a mixed collection: a sale paid with several
 * methods has to show, next to each method, only the amount collected with it — never the whole
 * ticket in every method row.
 *
 * <p>The real iText PDF is generated and its text read back, so what is checked is what the
 * client actually gets on the printed page.</p>
 */
class ExecutiveReportPaymentMethodPdfTest {

    /** Amounts that cannot collide with a Tailwind class or with another figure in the report. */
    private static final String SALE_TOTAL = "777.77";
    private static final String DEBIT_PORTION = "333.33";
    private static final String CREDIT_PORTION = "444.44";

    private ReportPdfService reportPdfService;

    @BeforeEach
    void setUp() {
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(systemConfigurationService.getConfiguration()).thenReturn(
                SystemConfiguration.builder().restaurantName("El Gran Sazón").build());

        DateTimeService dateTimeService = mock(DateTimeService.class);
        when(dateTimeService.nowLocal()).thenReturn(LocalDateTime.of(2026, 10, 1, 20, 30));

        reportPdfService = new ReportPdfService(
                systemConfigurationService, mock(EmployeeRepository.class), dateTimeService);
    }

    private Order mixedSale() {
        Order order = Order.builder()
                .idOrder(1L)
                .orderNumber("ORD-20261001-001")
                .status(OrderStatus.PAID)
                .total(new BigDecimal(SALE_TOTAL))
                .tip(BigDecimal.ZERO)
                .paidAt(LocalDateTime.of(2026, 10, 1, 20, 30))
                .createdAt(LocalDateTime.of(2026, 10, 1, 20, 0))
                .orderDetails(new ArrayList<>())
                .payments(new ArrayList<>())
                .build();
        order.getPaymentTenders().add(PaymentTender.builder()
                .order(order)
                .payment(null)
                .paymentMethod(PaymentMethodType.CREDIT_CARD)
                .amount(new BigDecimal(CREDIT_PORTION))
                .build());
        order.getPaymentTenders().add(PaymentTender.builder()
                .order(order)
                .payment(null)
                .paymentMethod(PaymentMethodType.DEBIT_CARD)
                .amount(new BigDecimal(DEBIT_PORTION))
                .build());
        return order;
    }

    /** Text of the generated PDF, split into lines so a row can be inspected as a whole. */
    private List<String> linesOf(byte[] pdf) throws Exception {
        StringBuilder text = new StringBuilder();
        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                text.append(PdfTextExtractor.getTextFromPage(document.getPage(page))).append('\n');
            }
        }
        return List.of(text.toString().split("\\R"));
    }

    private String rowOf(List<String> lines, String method) {
        return lines.stream()
                .filter(line -> line.contains(method))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no encontré la fila de " + method + " en el PDF:\n" + String.join("\n", lines)));
    }

    @Test
    @DisplayName("Cada método del reporte ejecutivo lleva solo el monto cobrado con él")
    void executiveReportSplitsTheMixedCollectionByMethod() throws Exception {
        List<Order> paidOrders = List.of(mixedSale());

        byte[] pdf = reportPdfService.generateExecutiveReport(
                paidOrders,
                "2026-10-01", "2026-10-01",
                new BigDecimal(SALE_TOTAL),
                1L,
                Map.of(),
                List.of(),
                PaymentTenderSupport.orderCountsByMethod(paidOrders),
                List.of(),
                List.of(),
                BigDecimal.ZERO);

        List<String> lines = linesOf(pdf);

        String debitRow = rowOf(lines, "Débito");
        String creditRow = rowOf(lines, "Crédito");

        assertTrue(debitRow.contains(DEBIT_PORTION),
                "la fila de débito debe traer solo su parte, fila=" + debitRow);
        assertFalse(debitRow.contains(SALE_TOTAL),
                "la fila de débito no debe traer el total del pedido, fila=" + debitRow);

        assertTrue(creditRow.contains(CREDIT_PORTION),
                "la fila de crédito debe traer solo su parte, fila=" + creditRow);
        assertFalse(creditRow.contains(SALE_TOTAL),
                "la fila de crédito no debe traer el total del pedido, fila=" + creditRow);
    }

    @Test
    @DisplayName("El reporte explica que una orden mixta aparece en cada método que usó")
    void executiveReportExplainsHowMixedOrdersAreCounted() throws Exception {
        List<Order> paidOrders = List.of(mixedSale());

        byte[] pdf = reportPdfService.generateExecutiveReport(
                paidOrders,
                "2026-10-01", "2026-10-01",
                new BigDecimal(SALE_TOTAL),
                1L,
                Map.of(),
                List.of(),
                PaymentTenderSupport.orderCountsByMethod(paidOrders),
                List.of(),
                List.of(),
                BigDecimal.ZERO);

        String text = String.join("\n", linesOf(pdf));

        assertTrue(text.contains("aparece en cada metodo que uso"),
                "el PDF debe aclarar que la orden se cuenta en cada método que utilizó");
        assertTrue(text.contains("por monto cobrado, no por recuento de ordenes"),
                "el PDF debe aclarar que el total y el % son por monto, no por número de órdenes");
    }
}
