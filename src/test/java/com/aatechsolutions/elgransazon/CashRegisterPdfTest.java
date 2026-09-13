package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.ReportPdfService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.CashRegisterMovement;
import com.aatechsolutions.elgransazon.domain.entity.CashRegisterMovementType;
import com.aatechsolutions.elgransazon.domain.entity.CashRegisterSession;
import com.aatechsolutions.elgransazon.domain.entity.CashRegisterStatus;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.presentation.dto.CashRegisterSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The cash-register PDF must actually be generated: this exercises the real
 * iText pipeline (fonts, tables, accents) instead of only rendering the pages.
 */
class CashRegisterPdfTest {

    private ReportPdfService reportPdfService;

    @BeforeEach
    void setUp() {
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        SystemConfiguration config = SystemConfiguration.builder()
                .restaurantName("El Gran Sazón")
                .build();
        when(systemConfigurationService.getConfiguration()).thenReturn(config);

        DateTimeService dateTimeService = mock(DateTimeService.class);
        when(dateTimeService.nowLocal()).thenReturn(LocalDateTime.of(2026, 9, 12, 19, 40));

        reportPdfService = new ReportPdfService(
                systemConfigurationService, mock(EmployeeRepository.class), dateTimeService);
    }

    private CashRegisterSession session() {
        return CashRegisterSession.builder()
                .id(1L)
                .status(CashRegisterStatus.CLOSED)
                .openedAt(LocalDateTime.of(2026, 9, 12, 9, 0))
                .closedAt(LocalDateTime.of(2026, 9, 12, 19, 0))
                .initialAmount(new BigDecimal("500.00"))
                .countedAmount(new BigDecimal("1234.56"))
                .build();
    }

    private CashRegisterSummary summary() {
        Map<PaymentMethodType, BigDecimal> byMethod = new LinkedHashMap<>();
        byMethod.put(PaymentMethodType.CASH, new BigDecimal("900.00"));
        byMethod.put(PaymentMethodType.CREDIT_CARD, new BigDecimal("300.00"));
        byMethod.put(PaymentMethodType.TRANSFER, BigDecimal.ZERO);
        return CashRegisterSummary.builder()
                .initialAmount(new BigDecimal("500.00"))
                .totalSales(new BigDecimal("1200.00"))
                .totalTips(new BigDecimal("45.50"))
                .salesCount(7)
                .salesByMethod(byMethod)
                .totalExpenses(new BigDecimal("180.00"))
                .totalIncomes(BigDecimal.ZERO)
                .totalWithdrawals(new BigDecimal("50.00"))
                .cashSales(new BigDecimal("900.00"))
                .expectedCash(new BigDecimal("1170.00"))
                .countedAmount(new BigDecimal("1234.56"))
                .difference(new BigDecimal("64.56"))
                .build();
    }

    private List<CashRegisterMovement> movements() {
        return List.of(
                CashRegisterMovement.builder()
                        .id(1L).type(CashRegisterMovementType.EXPENSE).concept("Hielo")
                        .amount(new BigDecimal("20.00")).occurredAt(LocalDateTime.of(2026, 9, 12, 11, 0))
                        .build(),
                CashRegisterMovement.builder()
                        .id(2L).type(CashRegisterMovementType.WITHDRAWAL).concept("Retiro a bóveda")
                        .amount(new BigDecimal("50.00")).notes("Autorizado por gerencia")
                        .occurredAt(LocalDateTime.of(2026, 9, 12, 18, 0))
                        .build());
    }

    @Test
    void generatesAValidPdfWithAccents() throws Exception {
        byte[] pdf = reportPdfService.generateCashRegisterReport(
                session(), summary(), movements(), "Ana López", "12/09/2026 09:00", "12/09/2026 19:00");

        assertTrue(pdf.length > 1000, "the PDF should have content, size=" + pdf.length);
        String header = new String(pdf, 0, Math.min(pdf.length, 8), StandardCharsets.ISO_8859_1);
        assertTrue(header.startsWith("%PDF-"), "should be a real PDF, header=" + header);
    }

    @Test
    void generatesAPdfWithoutMovementsNorCountedCash() throws Exception {
        CashRegisterSummary open = CashRegisterSummary.builder()
                .initialAmount(new BigDecimal("200.00"))
                .totalSales(BigDecimal.ZERO)
                .totalTips(BigDecimal.ZERO)
                .salesCount(0)
                .salesByMethod(Map.of())
                .totalExpenses(BigDecimal.ZERO)
                .totalIncomes(BigDecimal.ZERO)
                .totalWithdrawals(BigDecimal.ZERO)
                .cashSales(BigDecimal.ZERO)
                .expectedCash(new BigDecimal("200.00"))
                .build();

        byte[] pdf = reportPdfService.generateCashRegisterReport(
                session(), open, List.of(), "Ana López", "12/09/2026 09:00", "Aún abierta");

        assertTrue(pdf.length > 1000, "the PDF should have content even with no movements");
    }
}
