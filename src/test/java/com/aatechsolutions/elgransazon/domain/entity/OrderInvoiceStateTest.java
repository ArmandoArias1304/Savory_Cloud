package com.aatechsolutions.elgransazon.domain.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Facturada / no facturada" of a sale, as shown in the sales view.
 *
 * A whole ticket is one invoiceable unit; a split bill is one unit per account. A sale counts
 * as invoiced only when every unit has a fiscal receipt: an individual CFDI, a factura global
 * that amparó it, or an operation invoiced OUTSIDE the system (which is why it was excluded
 * from the global invoice).
 */
class OrderInvoiceStateTest {

    private Order order(String number) {
        return Order.builder()
                .idOrder(1L)
                .orderNumber(number)
                .status(OrderStatus.PAID)
                .total(new BigDecimal("116.00"))
                .createdAt(LocalDateTime.of(2026, 9, 24, 20, 0))
                .paidAt(LocalDateTime.of(2026, 9, 24, 20, 30))
                .orderDetails(new ArrayList<>())
                .payments(new ArrayList<>())
                .build();
    }

    private Payment account(long id) {
        return Payment.builder()
                .idPayment(id)
                .paymentFolio("ORD-20260924-007-0" + id)
                .total(new BigDecimal("58.00"))
                .paidAt(LocalDateTime.of(2026, 9, 24, 21, 0))
                .build();
    }

    @Test
    void wholeTicketIsNotInvoicedUntilItHasAReceipt() {
        Order sale = order("ORD-1");

        assertThat(sale.isInvoiced()).isFalse();
        assertThat(sale.isFullyInvoiced()).isFalse();
        assertThat(sale.isPartiallyInvoiced()).isFalse();
        assertThat(sale.getInvoiceableUnitCount()).isEqualTo(1);
        assertThat(sale.getInvoicedUnitCount()).isZero();
    }

    @Test
    void individualCfdiOrGlobalInvoiceMarkTheWholeTicketAsInvoiced() {
        Order invoicedIndividually = order("ORD-2");
        invoicedIndividually.setFacturamaCfdiId("cfdi-1");
        assertThat(invoicedIndividually.isInvoiced()).isTrue();
        assertThat(invoicedIndividually.isFullyInvoiced()).isTrue();
        assertThat(invoicedIndividually.getInvoicedUnitCount()).isEqualTo(1);

        Order coveredByGlobal = order("ORD-3");
        coveredByGlobal.setFacturaGlobalCfdiId("global-1");
        assertThat(coveredByGlobal.isFullyInvoiced()).isTrue();
    }

    @Test
    void operationInvoicedOutsideTheSystemCountsAsInvoiced() {
        Order excluded = order("ORD-4");
        excluded.setFacturaGlobalExcluida(true);

        assertThat(excluded.isInvoiced()).isTrue();
        assertThat(excluded.isFullyInvoiced()).isTrue();
        assertThat(excluded.canGenerateInvoiceLink()).isFalse();
    }

    @Test
    void splitBillCountsEachAccountAsItsOwnUnit() {
        Order split = order("ORD-5");
        Payment first = account(1L);
        Payment second = account(2L);
        Payment third = account(3L);
        split.getPayments().add(first);
        split.getPayments().add(second);
        split.getPayments().add(third);

        assertThat(split.getInvoiceableUnitCount()).isEqualTo(3);
        assertThat(split.getInvoicedUnitCount()).isZero();
        assertThat(split.isFullyInvoiced()).isFalse();
        assertThat(split.isPartiallyInvoiced()).isFalse();

        first.setFacturamaCfdiId("cfdi-account-1");

        assertThat(split.getInvoicedUnitCount()).isEqualTo(1);
        assertThat(split.isPartiallyInvoiced()).isTrue();
        assertThat(split.isFullyInvoiced()).isFalse();

        second.setFacturaGlobalCfdiId("global-account-2");
        third.setFacturaGlobalExcluida(true);

        assertThat(split.getInvoicedUnitCount()).isEqualTo(3);
        assertThat(split.isFullyInvoiced()).isTrue();
        assertThat(split.isPartiallyInvoiced()).isFalse();
    }

    @Test
    void linkCanOnlyBeGeneratedForUnitsWithoutReceipt() {
        Order sale = order("ORD-6");
        assertThat(sale.canGenerateInvoiceLink()).isTrue();

        sale.setAutofacturaKey("key");
        assertThat(sale.canGenerateInvoiceLink()).isFalse();

        Order split = order("ORD-7");
        Payment linked = account(1L);
        linked.setAutofacturaKey("key");
        Payment pending = account(2L);
        split.getPayments().add(linked);
        split.getPayments().add(pending);

        assertThat(split.hasInvoiceLinkPending()).isTrue();
        assertThat(pending.canGenerateInvoiceLink()).isTrue();

        pending.setFacturamaCfdiId("cfdi-2");
        assertThat(split.hasInvoiceLinkPending()).isFalse();
    }
}
