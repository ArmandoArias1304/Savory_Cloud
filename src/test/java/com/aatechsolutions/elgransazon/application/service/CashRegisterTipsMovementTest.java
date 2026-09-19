package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.CashRegisterMovement;
import com.aatechsolutions.elgransazon.domain.entity.CashRegisterMovementType;
import com.aatechsolutions.elgransazon.domain.entity.CashRegisterSession;
import com.aatechsolutions.elgransazon.domain.entity.CashRegisterStatus;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.PaymentTender;
import com.aatechsolutions.elgransazon.domain.repository.CashRegisterMovementRepository;
import com.aatechsolutions.elgransazon.domain.repository.CashRegisterSessionRepository;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.presentation.dto.CashRegisterSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Money in the drawer: "Pago" leaves it, "Entrada" and "Propinas efectivo" add to it.
 *
 * Order totals are stored without the tip, so cash tips reach the drawer on top of the
 * sale; registering them must raise the expected cash instead of lowering it.
 */
class CashRegisterTipsMovementTest {

    private static final String CASHIER = "ana";

    private CashRegisterMovementRepository movementRepository;
    private OrderRepository orderRepository;
    private CashRegisterService service;
    private Company company;
    private CashRegisterSession session;

    @BeforeEach
    void setUp() {
        CashRegisterSessionRepository sessionRepository = mock(CashRegisterSessionRepository.class);
        movementRepository = mock(CashRegisterMovementRepository.class);
        orderRepository = mock(OrderRepository.class);
        service = new CashRegisterService(sessionRepository, movementRepository, orderRepository,
                mock(DateTimeService.class));

        company = new Company();
        company.setIdCompany(1L);
        session = CashRegisterSession.builder()
                .id(1L)
                .company(company)
                .cashier(Employee.builder().idEmpleado(1L).username(CASHIER)
                        .nombre("Ana").apellido("Lopez").build())
                .status(CashRegisterStatus.OPEN)
                .openedAt(LocalDateTime.of(2026, 9, 12, 9, 0))
                .initialAmount(new BigDecimal("500.00"))
                .build();
    }

    private CashRegisterMovement movement(CashRegisterMovementType type, String amount) {
        return CashRegisterMovement.builder()
                .type(type)
                .concept(type.getDisplayName())
                .amount(new BigDecimal(amount))
                .occurredAt(LocalDateTime.of(2026, 9, 12, 12, 0))
                .build();
    }

    private void movements(CashRegisterMovement... movements) {
        when(movementRepository.findBySessionOrderByOccurredAtAsc(session)).thenReturn(List.of(movements));
    }

    /** One CASH sale of $100 with a $20 tip: the tip is NOT inside the order total. */
    private void oneCashSaleOfOneHundred() {
        Order order = Order.builder()
                .idOrder(7L)
                .orderNumber("ORD-20260912-001")
                .status(OrderStatus.PAID)
                .paymentMethod(PaymentMethodType.CASH)
                .total(new BigDecimal("100.00"))
                .tip(new BigDecimal("20.00"))
                .paidAt(LocalDateTime.of(2026, 9, 12, 11, 0))
                .build();
        when(orderRepository.findPaidByCollectorAndPaidAtRangeAndCompany(
                eq(CASHIER), any(), any(), eq(company))).thenReturn(List.of(order));
    }

    @Test
    @DisplayName("Pago resta, entrada y propinas efectivo suman al esperado en caja")
    void pagosRestanYEntradasYPropinasSuman() {
        oneCashSaleOfOneHundred();
        movements(
                movement(CashRegisterMovementType.EXPENSE, "80.00"),
                movement(CashRegisterMovementType.INCOME, "50.00"),
                movement(CashRegisterMovementType.TIPS, "30.00"));

        CashRegisterSummary summary = service.buildSummary(session);

        assertThat(summary.getTotalExpenses()).isEqualByComparingTo("80.00");
        assertThat(summary.getTotalIncomes()).isEqualByComparingTo("50.00");
        assertThat(summary.getTotalCashTips()).isEqualByComparingTo("30.00");
        assertThat(summary.getCashSales()).isEqualByComparingTo("100.00");
        // 500 fondo + 100 ventas en efectivo + 50 entradas + 30 propinas − 80 pagos
        assertThat(summary.getExpectedCash()).isEqualByComparingTo("600.00");
        // La propina del pedido solo se informa como referencia, no entra dos veces
        assertThat(summary.getTotalTips()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("Una propina capturada como movimiento sube el esperado, nunca lo baja")
    void cashTipsOnlyRaiseTheExpectedCash() {
        oneCashSaleOfOneHundred();
        movements(movement(CashRegisterMovementType.TIPS, "120.00"));

        CashRegisterSummary summary = service.buildSummary(session);

        assertThat(summary.getTotalCashTips()).isEqualByComparingTo("120.00");
        assertThat(summary.getExpectedCash()).isEqualByComparingTo("720.00");
    }

    @Test
    @DisplayName("Los movimientos de propina guardados antes del arreglo también suman")
    void legacyWithdrawalRowsAddAsWell() {
        oneCashSaleOfOneHundred();
        movements(movement(CashRegisterMovementType.WITHDRAWAL, "25.00"));

        CashRegisterSummary summary = service.buildSummary(session);

        assertThat(summary.getTotalCashTips()).isEqualByComparingTo("25.00");
        assertThat(summary.getExpectedCash()).isEqualByComparingTo("625.00");
    }

    @Test
    @DisplayName("El form ofrece los tres conceptos y no repite el valor legado")
    void theFormOffersEachConceptOnce() {
        assertThat(CashRegisterMovementType.selectable())
                .containsExactly(CashRegisterMovementType.EXPENSE, CashRegisterMovementType.INCOME,
                        CashRegisterMovementType.TIPS);
        assertThat(CashRegisterMovementType.EXPENSE.isCashOut()).isTrue();
        assertThat(CashRegisterMovementType.EXPENSE.getDisplayName()).isEqualTo("Pago");
        assertThat(CashRegisterMovementType.INCOME.isCashOut()).isFalse();
        assertThat(CashRegisterMovementType.INCOME.getDisplayName()).isEqualTo("Entrada");
        assertThat(CashRegisterMovementType.TIPS.isCashOut()).isFalse();
        assertThat(CashRegisterMovementType.TIPS.getDisplayName()).isEqualTo("Propinas efectivo");
    }

    @Test
    @DisplayName("Una venta mixta solo mete a caja el monto en efectivo, no el ticket completo")
    void mixedSaleContributesOnlyTheCashTenderToTheDrawer() {
        Order mixed = Order.builder()
                .idOrder(8L)
                .orderNumber("ORD-20260912-MIX")
                .status(OrderStatus.PAID)
                .paymentMethod(PaymentMethodType.DEBIT_CARD)
                .total(new BigDecimal("130.00"))
                .tip(new BigDecimal("10.00"))
                .paidAt(LocalDateTime.of(2026, 9, 12, 11, 0))
                .paymentTenders(new java.util.ArrayList<>())
                .payments(new java.util.ArrayList<>())
                .build();
        mixed.getPaymentTenders().add(PaymentTender.builder()
                .order(mixed)
                .paymentMethod(PaymentMethodType.CASH)
                .amount(new BigDecimal("50.00"))
                .build());
        mixed.getPaymentTenders().add(PaymentTender.builder()
                .order(mixed)
                .paymentMethod(PaymentMethodType.DEBIT_CARD)
                .amount(new BigDecimal("80.00"))
                .build());
        when(orderRepository.findPaidByCollectorAndPaidAtRangeAndCompany(
                eq(CASHIER), any(), any(), eq(company))).thenReturn(List.of(mixed));
        movements();

        CashRegisterSummary summary = service.buildSummary(session);

        assertThat(summary.getTotalSales()).isEqualByComparingTo("130.00");
        assertThat(summary.getCashSales()).isEqualByComparingTo("50.00");
        assertThat(summary.getSalesByMethod().get(PaymentMethodType.CASH)).isEqualByComparingTo("50.00");
        assertThat(summary.getSalesByMethod().get(PaymentMethodType.DEBIT_CARD)).isEqualByComparingTo("80.00");
        // 500 fondo + 50 efectivo (nunca los 130 del ticket)
        assertThat(summary.getExpectedCash()).isEqualByComparingTo("550.00");
        assertThat(summary.getTotalTips()).isEqualByComparingTo("10.00");
    }
}
