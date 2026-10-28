package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.PaymentMethodCorrectionService;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.domain.repository.PaymentMethodCorrectionRepository;
import com.aatechsolutions.elgransazon.domain.repository.PaymentRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentMethodCorrectionServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentMethodCorrectionRepository correctionRepository =
            mock(PaymentMethodCorrectionRepository.class);
    private final PaymentMethodCorrectionService service =
            new PaymentMethodCorrectionService(orderRepository, paymentRepository, correctionRepository);
    private final Company company = new Company();

    @BeforeEach
    void setCompanyContext() {
        company.setIdCompany(3L);
        CompanyContext.setCurrentCompany(company);
    }

    @AfterEach
    void clearCompanyContext() {
        CompanyContext.clear();
    }

    @Test
    void correctsWholePaidOrderWithoutChangingTenderAmountsAndStoresAudit() {
        Order order = paidOrder();
        order.setPaymentMethod(PaymentMethodType.CASH);
        order.setPaymentTenders(new ArrayList<>(List.of(tender(order, null, PaymentMethodType.CASH, "125.50"))));
        when(orderRepository.findByIdOrderAndCompany(7L, company)).thenReturn(Optional.of(order));
        when(correctionRepository.save(any(PaymentMethodCorrection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentMethodCorrection correction = service.correct(
                7L, null, PaymentMethodType.CREDIT_CARD, "Se capturó efectivo por error", "admin");

        assertEquals(PaymentMethodType.CREDIT_CARD, order.getPaymentMethod());
        assertEquals(PaymentMethodType.CREDIT_CARD, order.getPaymentTenders().get(0).getPaymentMethod());
        assertEquals(new BigDecimal("125.50"), order.getPaymentTenders().get(0).getAmount());
        assertEquals("admin", order.getUpdatedBy());
        assertEquals("Pedido completo", correction.getScopeLabel());
        assertEquals("Efectivo $125.50", correction.getPreviousMethods());
        assertEquals("Tarjeta de Crédito $125.50", correction.getNewMethods());
        assertEquals("Se capturó efectivo por error", correction.getReason());
        verify(correctionRepository).save(correction);
    }

    @Test
    void correctsOnlyTheSelectedPaidAccountAndPreservesItsMixedTenderAmounts() {
        Order order = paidOrder();
        order.setStatus(OrderStatus.DELIVERED);
        order.setPaymentMethod(PaymentMethodType.CASH);
        Payment payment = new Payment();
        payment.setIdPayment(14L);
        payment.setCompany(company);
        payment.setOrder(order);
        payment.setAccountNumber(2);
        payment.setPersonLabel("Persona 2");
        payment.setPaymentMethod(PaymentMethodType.CASH);
        payment.setPaidAt(LocalDateTime.now());
        payment.setTotal(new BigDecimal("100.00"));
        payment.setPaymentTenders(new ArrayList<>(List.of(
                tender(order, payment, PaymentMethodType.CASH, "60.00"),
                tender(order, payment, PaymentMethodType.DEBIT_CARD, "40.00"))));
        order.setPayments(new ArrayList<>(List.of(payment)));
        when(orderRepository.findByIdOrderAndCompany(7L, company)).thenReturn(Optional.of(order));
        when(paymentRepository.findByIdPaymentAndCompany(14L, company)).thenReturn(Optional.of(payment));
        when(correctionRepository.save(any(PaymentMethodCorrection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentMethodCorrection correction = service.correct(
                7L, 14L, PaymentMethodType.TRANSFER, "Se eligió el método equivocado", "manager");

        assertEquals(PaymentMethodType.CASH, order.getPaymentMethod());
        assertEquals(List.of(PaymentMethodType.TRANSFER, PaymentMethodType.TRANSFER),
                payment.getPaymentTenders().stream().map(PaymentTender::getPaymentMethod).toList());
        assertEquals(List.of(new BigDecimal("60.00"), new BigDecimal("40.00")),
                payment.getPaymentTenders().stream().map(PaymentTender::getAmount).toList());
        assertEquals("Persona 2", correction.getScopeLabel());
        assertEquals("Persona 2", correction.getPayment().getPersonLabel());
        assertEquals("Transferencia $100.00", correction.getNewMethods());
        assertEquals("manager", correction.getChangedBy());
    }

    @Test
    void rejectsCorrectionForAnInvoicedOrder() {
        Order order = paidOrder();
        order.setFacturamaCfdiId("cfdi-1");
        when(orderRepository.findByIdOrderAndCompany(7L, company)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> service.correct(
                7L, null, PaymentMethodType.CREDIT_CARD, "Error de captura", "admin"));
    }

    private Order paidOrder() {
        Order order = new Order();
        order.setIdOrder(7L);
        order.setCompany(company);
        order.setStatus(OrderStatus.PAID);
        order.setTotal(new BigDecimal("125.50"));
        order.setPaymentTenders(new ArrayList<>());
        order.setPayments(new ArrayList<>());
        order.setPaymentMethodCorrections(new ArrayList<>());
        return order;
    }

    private PaymentTender tender(Order order, Payment payment, PaymentMethodType method, String amount) {
        PaymentTender tender = new PaymentTender();
        tender.setCompany(company);
        tender.setOrder(order);
        tender.setPayment(payment);
        tender.setPaymentMethod(method);
        tender.setAmount(new BigDecimal(amount));
        return tender;
    }
}
