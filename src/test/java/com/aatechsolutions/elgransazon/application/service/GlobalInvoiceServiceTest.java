package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.Payment;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.domain.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Factura global exclusions: a restaurant that contracted the billing service after its
 * accountant had already invoiced some operations outside the system must be able to
 * exclude those tickets (and re-include them later).
 *
 * The service builds the ticket list of both panels and translates the date range from the
 * company timezone into the UTC range used by the pending/excluded queries.
 */
class GlobalInvoiceServiceTest {

    private static final String PROGRAMMER = "programador";
    private static final LocalDate FROM = LocalDate.of(2026, 9, 24);
    private static final LocalDate TO = LocalDate.of(2026, 9, 24);

    private OrderRepository orderRepository;
    private PaymentRepository paymentRepository;
    private GlobalInvoiceService service;
    private Company company;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        service = new GlobalInvoiceService(orderRepository, paymentRepository);
        company = Company.builder()
                .idCompany(1L)
                .slug("resto")
                .name("Restaurante Prueba")
                .timezone("America/Mexico_City")
                .build();
    }

    @Test
    void pendingTicketsUseThePendingQueriesForOrdersAndSplitAccounts() {
        Order order = Order.builder()
                .idOrder(7L)
                .orderNumber("ORD-20260924-001")
                .status(OrderStatus.PAID)
                .total(new BigDecimal("116.00"))
                .paymentMethod(PaymentMethodType.CASH)
                .paidAt(LocalDateTime.of(2026, 9, 24, 18, 30))
                .build();
        Payment account = Payment.builder()
                .idPayment(9L)
                .paymentFolio("ORD-20260924-002-01")
                .total(new BigDecimal("58.00"))
                .paymentMethod(PaymentMethodType.CREDIT_CARD)
                .paidAt(LocalDateTime.of(2026, 9, 24, 19, 0))
                .build();

        when(orderRepository.findPaidOrdersPendingGlobalInvoiceByDateRange(eq(company), any(), any()))
                .thenReturn(List.of(order));
        when(paymentRepository.findPaidPendingGlobalInvoiceByDateRange(eq(company), any(), any()))
                .thenReturn(List.of(account));

        List<Map<String, Object>> tickets = service.findPendingTickets(company, FROM, TO);

        assertThat(tickets).hasSize(2);

        Map<String, Object> ticket = tickets.get(0);
        assertThat(ticket.get("key")).isEqualTo("ORDER:7");
        assertThat(ticket.get("type")).isEqualTo("ORDER");
        assertThat(ticket.get("id")).isEqualTo(7L);
        assertThat(ticket.get("folio")).isEqualTo("ORD-20260924-001");
        assertThat(ticket.get("date")).isEqualTo("2026-09-24");
        assertThat(ticket.get("total")).isEqualTo(new BigDecimal("116.00"));
        assertThat(ticket.get("paymentMethod")).isEqualTo(order.getPaymentMethodsDisplay());

        Map<String, Object> splitTicket = tickets.get(1);
        assertThat(splitTicket.get("key")).isEqualTo("PAYMENT:9");
        assertThat(splitTicket.get("type")).isEqualTo("PAYMENT");
        assertThat(splitTicket.get("folio")).isEqualTo("ORD-20260924-002-01");
        assertThat(splitTicket.get("total")).isEqualTo(new BigDecimal("58.00"));

        // The pending list must never come from the excluded queries.
        verify(orderRepository, never()).findPaidOrdersExcludedFromGlobalInvoiceByDateRange(any(), any(), any());
        verify(paymentRepository, never()).findPaidExcludedFromGlobalInvoiceByDateRange(any(), any(), any());
        assertThat(service.sumTotals(tickets)).isEqualTo(new BigDecimal("174.00"));
    }

    @Test
    void excludedTicketsUseTheExcludedQueriesAndShareTheSameTicketShape() {
        Order order = Order.builder()
                .idOrder(3L)
                .orderNumber("ORD-20260924-003")
                .status(OrderStatus.PAID)
                .total(new BigDecimal("250.00"))
                .paymentMethod(PaymentMethodType.CASH)
                .paidAt(LocalDateTime.of(2026, 9, 24, 20, 0))
                .build();

        when(orderRepository.findPaidOrdersExcludedFromGlobalInvoiceByDateRange(eq(company), any(), any()))
                .thenReturn(List.of(order));
        when(paymentRepository.findPaidExcludedFromGlobalInvoiceByDateRange(eq(company), any(), any()))
                .thenReturn(List.of());

        List<Map<String, Object>> tickets = service.findExcludedTickets(company, FROM, TO);

        assertThat(tickets).hasSize(1);
        assertThat(tickets.get(0).get("key")).isEqualTo("ORDER:3");
        assertThat(service.sumTotals(tickets)).isEqualTo(new BigDecimal("250.00"));

        verify(orderRepository, never()).findPaidOrdersPendingGlobalInvoiceByDateRange(any(), any(), any());
    }

    @Test
    void setExclusionSplitsOrderAndAccountKeysAndFlagsThem() {
        when(orderRepository.updateGlobalInvoiceExclusion(eq(company), eq(List.of(1L, 5L)), eq(true),
                eq(PROGRAMMER), any(), any())).thenReturn(2);
        when(paymentRepository.updateGlobalInvoiceExclusion(eq(company), eq(List.of(2L)), eq(true),
                eq(PROGRAMMER), any(), any())).thenReturn(1);

        GlobalInvoiceService.ExclusionResult result = service.setExclusion(
                company, PROGRAMMER, FROM, TO,
                List.of("ORDER:1", "PAYMENT:2", "ORDER:5", "basura", "PAYMENT:x"), true);

        assertThat(result.excluded()).isTrue();
        assertThat(result.orderCount()).isEqualTo(2);
        assertThat(result.paymentCount()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(3);
    }

    @Test
    void setExclusionWithEmptySelectionIsRejectedWithoutTouchingTheDatabase() {
        assertThatThrownBy(() -> service.setExclusion(company, PROGRAMMER, FROM, TO, List.of(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos una operación");

        assertThatThrownBy(() -> service.setExclusion(company, PROGRAMMER, FROM, TO, null, true))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(orderRepository, paymentRepository);
    }

    @Test
    void reInclusionPassesFalseAndSkipsAlreadyInvoicedTickets() {
        // The repository only updates tickets that are still pending, so an already invoiced
        // one is skipped and never counted.
        when(orderRepository.updateGlobalInvoiceExclusion(eq(company), eq(List.of(4L)), eq(false),
                eq(PROGRAMMER), any(), any())).thenReturn(1);

        GlobalInvoiceService.ExclusionResult result = service.setExclusion(
                company, PROGRAMMER, FROM, TO, List.of("ORDER:4"), false);

        assertThat(result.excluded()).isFalse();
        assertThat(result.total()).isEqualTo(1);
        // No account was selected: the payment update must not even run.
        verify(paymentRepository, never()).updateGlobalInvoiceExclusion(any(), any(), anyBoolean(), any(), any(), any());
    }

    @Test
    void rangeIsConvertedFromTheCompanyTimezoneToUtc() {
        assertThat(service.startOfRangeUtc(company, FROM)).isEqualTo(LocalDateTime.of(2026, 9, 24, 6, 0));
        assertThat(service.endOfRangeUtc(company, FROM)).isEqualTo(LocalDateTime.of(2026, 9, 25, 6, 0));
    }

    @Test
    void periodicityIsDailyFullMonthOrNullForTheExclusionPanel() {
        assertThat(service.resolvePeriodicity(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 24)))
                .isEqualTo("01");
        assertThat(service.resolvePeriodicity(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .isEqualTo("04");
        // A free range cannot be billed as a global invoice, but it is valid for exclusions.
        assertThat(service.resolvePeriodicity(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 24)))
                .isNull();

        assertThatThrownBy(() -> service.resolvePeriodicity(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("menor o igual");
    }
}
