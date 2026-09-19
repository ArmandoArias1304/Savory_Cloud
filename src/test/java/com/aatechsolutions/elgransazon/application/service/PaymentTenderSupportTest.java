package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.PaymentTender;
import com.aatechsolutions.elgransazon.presentation.dto.PaymentTenderDTO;
import com.aatechsolutions.elgransazon.util.PaymentTenderSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTenderSupportTest {

    @Test
    void fitToTotal_absorbsOneCentOnLargestTender() {
        List<PaymentTenderSupport.TenderLine> fitted = PaymentTenderSupport.fitToTotal(
                List.of(
                        new PaymentTenderSupport.TenderLine(PaymentMethodType.CASH, new BigDecimal("50.00")),
                        new PaymentTenderSupport.TenderLine(PaymentMethodType.DEBIT_CARD, new BigDecimal("80.00"))),
                new BigDecimal("130.01"));

        assertEquals(2, fitted.size());
        assertEquals(0, new BigDecimal("50.00").compareTo(fitted.get(0).getAmount()));
        assertEquals(0, new BigDecimal("80.01").compareTo(fitted.get(1).getAmount()));
        assertEquals(0, new BigDecimal("130.01").compareTo(PaymentTenderSupport.sum(fitted)));
    }

    @Test
    void fitToTotal_rejectsGapLargerThanOneCent() {
        List<PaymentTenderSupport.TenderLine> mix = List.of(
                new PaymentTenderSupport.TenderLine(PaymentMethodType.CASH, new BigDecimal("50.00")),
                new PaymentTenderSupport.TenderLine(PaymentMethodType.CREDIT_CARD, new BigDecimal("80.00")));

        IllegalArgumentException under = assertThrows(IllegalArgumentException.class,
                () -> PaymentTenderSupport.fitToTotal(mix, new BigDecimal("129.00")));
        assertTrue(under.getMessage().contains("debe ser exactamente el total"));

        IllegalArgumentException over = assertThrows(IllegalArgumentException.class,
                () -> PaymentTenderSupport.fitToTotal(mix, new BigDecimal("131.00")));
        assertTrue(over.getMessage().contains("debe ser exactamente el total"));
    }

    @Test
    void resolveSubmitted_rejectsDisabledMethod() {
        Set<PaymentMethodType> allowed = EnumSet.of(PaymentMethodType.CASH, PaymentMethodType.TRANSFER);

        List<PaymentTenderSupport.TenderLine> mix = PaymentTenderSupport.resolveSubmitted(
                "[{\"method\":\"CASH\",\"amount\":50},{\"method\":\"DEBIT_CARD\",\"amount\":80}]",
                PaymentMethodType.CASH,
                new BigDecimal("130.00"));

        assertThrows(IllegalStateException.class,
                () -> PaymentTenderSupport.assertMethodsAllowed(mix, allowed, null));
    }

    @Test
    void resolveSubmitted_acceptsMixedWithinAllowedMethods() {
        Set<PaymentMethodType> allowed = EnumSet.of(
                PaymentMethodType.CASH, PaymentMethodType.TRANSFER,
                PaymentMethodType.DEBIT_CARD, PaymentMethodType.CREDIT_CARD);

        List<PaymentTenderSupport.TenderLine> tenders = PaymentTenderSupport.resolveSubmitted(
                "[{\"method\":\"CASH\",\"amount\":50},{\"method\":\"DEBIT_CARD\",\"amount\":80}]",
                PaymentMethodType.CASH,
                new BigDecimal("130.00"));

        PaymentTenderSupport.assertMethodsAllowed(tenders, allowed, null);
        assertEquals(2, tenders.size());
        assertEquals(0, new BigDecimal("130.00").compareTo(PaymentTenderSupport.sum(tenders)));
        assertEquals(PaymentMethodType.CASH, tenders.get(0).getMethod());
        assertEquals(PaymentMethodType.DEBIT_CARD, tenders.get(1).getMethod());
        assertTrue(PaymentTenderSupport.isMixed(tenders));
        assertFalse(PaymentTenderSupport.isCashOnly(tenders));
        assertEquals(PaymentMethodType.DEBIT_CARD, PaymentTenderSupport.satPaymentFormMethod(tenders),
                "SAT uses the method of the largest amount, not 99");
    }

    @Test
    void resolveSubmitted_fallsBackToSingleMethodWhenJsonBlank() {
        List<PaymentTenderSupport.TenderLine> tenders = PaymentTenderSupport.resolveSubmitted(
                "",
                PaymentMethodType.TRANSFER,
                new BigDecimal("75.50"));

        assertEquals(1, tenders.size());
        assertEquals(PaymentMethodType.TRANSFER, tenders.get(0).getMethod());
        assertEquals(0, new BigDecimal("75.50").compareTo(tenders.get(0).getAmount()));
        assertEquals("03", satForm(PaymentTenderSupport.satPaymentFormMethod(tenders)));
    }

    @Test
    void applyToOrder_replacesLegacySingleMethodWithTenders() {
        Order order = sampleOrder(PaymentMethodType.CASH, "130.00");

        PaymentTenderSupport.applyToOrder(
                order,
                List.of(
                        new PaymentTenderSupport.TenderLine(PaymentMethodType.CASH, new BigDecimal("50.00")),
                        new PaymentTenderSupport.TenderLine(PaymentMethodType.TRANSFER, new BigDecimal("80.00"))));

        assertEquals(2, order.getPaymentTenders().size());
        assertEquals(PaymentMethodType.TRANSFER, order.getPaymentMethod(),
                "the primary method is the largest tender");
        assertTrue(order.usesPaymentMethod(PaymentMethodType.CASH));
        assertTrue(order.usesPaymentMethod(PaymentMethodType.TRANSFER));
        assertFalse(order.usesPaymentMethod(PaymentMethodType.DEBIT_CARD));
        assertTrue(order.hasMixedPaymentMethods());
        assertEquals("Efectivo + Transferencia", order.getPaymentMethodsDisplay());
        assertEquals(0, new BigDecimal("50.00").compareTo(
                PaymentTenderSupport.amountOf(PaymentTenderSupport.collected(order), PaymentMethodType.CASH)));
        assertEquals(0, new BigDecimal("80.00").compareTo(
                PaymentTenderSupport.amountOf(PaymentTenderSupport.collected(order), PaymentMethodType.TRANSFER)));
    }

    @Test
    void collected_fallsBackToLegacySingleMethodWhenNoTenders() {
        Order order = sampleOrder(PaymentMethodType.CREDIT_CARD, "75.50");
        List<PaymentTenderSupport.TenderLine> lines = PaymentTenderSupport.collected(order);

        assertEquals(1, lines.size());
        assertEquals(PaymentMethodType.CREDIT_CARD, lines.get(0).getMethod());
        assertEquals(0, new BigDecimal("75.50").compareTo(lines.get(0).getAmount()));
        assertTrue(order.usesPaymentMethod(PaymentMethodType.CREDIT_CARD));
        assertFalse(order.hasMixedPaymentMethods());
    }

    @Test
    void totalsByMethod_sumToOrderTotalsWithoutDoubleCounting() {
        Order mixed = sampleOrder(PaymentMethodType.CASH, "200.00");
        mixed.getPaymentTenders().clear();
        mixed.getPaymentTenders().add(tender(mixed, PaymentMethodType.CASH, "50.00"));
        mixed.getPaymentTenders().add(tender(mixed, PaymentMethodType.DEBIT_CARD, "70.00"));
        mixed.getPaymentTenders().add(tender(mixed, PaymentMethodType.CREDIT_CARD, "80.00"));

        Order cashOnly = sampleOrder(PaymentMethodType.CASH, "30.00");

        Map<PaymentMethodType, BigDecimal> amounts = PaymentTenderSupport.totalsByMethod(List.of(mixed, cashOnly));
        BigDecimal total = amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, new BigDecimal("230.00").compareTo(total));
        assertEquals(0, new BigDecimal("80.00").compareTo(amounts.get(PaymentMethodType.CASH)));
        assertEquals(0, new BigDecimal("70.00").compareTo(amounts.get(PaymentMethodType.DEBIT_CARD)));
        assertEquals(0, new BigDecimal("80.00").compareTo(amounts.get(PaymentMethodType.CREDIT_CARD)));

        Map<String, Long> counts = PaymentTenderSupport.orderCountsByMethod(List.of(mixed, cashOnly));
        assertEquals(2L, counts.get("Efectivo"));
        assertEquals(1L, counts.get("Tarjeta de Débito"));
        assertEquals(1L, counts.get("Tarjeta de Crédito"));
    }

    @Test
    void fromDtos_mergesDuplicateMethodsAndDropsZeros() {
        List<PaymentTenderSupport.TenderLine> lines = PaymentTenderSupport.fromDtos(List.of(
                new PaymentTenderDTO("CASH", new BigDecimal("20.00")),
                new PaymentTenderDTO("CASH", new BigDecimal("30.00")),
                new PaymentTenderDTO("DEBIT_CARD", BigDecimal.ZERO)));

        assertEquals(1, lines.size());
        assertEquals(PaymentMethodType.CASH, lines.get(0).getMethod());
        assertEquals(0, new BigDecimal("50.00").compareTo(lines.get(0).getAmount()));
    }

    private static String satForm(PaymentMethodType method) {
        if (method == null) {
            return "99";
        }
        return switch (method) {
            case CASH -> "01";
            case CREDIT_CARD -> "04";
            case DEBIT_CARD -> "28";
            case TRANSFER -> "03";
        };
    }

    private static PaymentTender tender(Order order, PaymentMethodType method, String amount) {
        return PaymentTender.builder()
                .company(order.getCompany())
                .order(order)
                .paymentMethod(method)
                .amount(new BigDecimal(amount))
                .build();
    }

    private static Order sampleOrder(PaymentMethodType method, String total) {
        Company company = Company.builder().idCompany(1L).name("Test").slug("test").build();
        return Order.builder()
                .idOrder(11L)
                .company(company)
                .orderNumber("ORD-MIX")
                .status(OrderStatus.READY)
                .paymentMethod(method)
                .subtotal(new BigDecimal(total))
                .taxAmount(BigDecimal.ZERO)
                .total(new BigDecimal(total))
                .paymentTenders(new ArrayList<>())
                .payments(new ArrayList<>())
                .build();
    }
}
