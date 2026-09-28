package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.FacturamaService;
import com.aatechsolutions.elgransazon.application.service.InvoiceLinkService;
import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sales view filter by invoice status: the new column must be filterable, and a split bill with
 * only some accounts invoiced counts as NOT invoiced (there is still something to invoice).
 */
class SalesControllerInvoiceFilterTest {

    private OrderService orderService;
    private SalesController controller;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        controller = new SalesController(
                orderService,
                mock(EmployeeService.class),
                mock(DateTimeService.class),
                mock(InvoiceLinkService.class),
                mock(FacturamaService.class));
    }

    private Order sale(long id, String number) {
        return Order.builder()
                .idOrder(id)
                .orderNumber(number)
                .status(OrderStatus.PAID)
                .total(new BigDecimal("116.00"))
                .paidAt(LocalDateTime.of(2026, 9, 24, 20, 30))
                .createdAt(LocalDateTime.of(2026, 9, 24, 20, 0))
                .orderDetails(new ArrayList<>())
                .payments(new ArrayList<>())
                .build();
    }

    private Payment account(Order order, long id) {
        Payment payment = Payment.builder()
                .idPayment(id)
                .paymentFolio(order.getOrderNumber() + "-0" + id)
                .total(new BigDecimal("58.00"))
                .paidAt(LocalDateTime.of(2026, 9, 24, 21, 0))
                .build();
        order.getPayments().add(payment);
        return payment;
    }

    private List<Order> listed(SalesController.InvoiceStatusFilter filter) {
        Model model = new ExtendedModelMap();
        controller.listSales(null, null, null, null, filter, 1, model);
        @SuppressWarnings("unchecked")
        List<Order> sales = (List<Order>) model.getAttribute("sales");
        return sales;
    }

    private List<Order> threeSales() {
        Order invoicedIndividually = sale(1L, "ORD-1");
        invoicedIndividually.setFacturamaCfdiId("cfdi-1");

        Order coveredByGlobal = sale(2L, "ORD-2");
        coveredByGlobal.setFacturaGlobalCfdiId("global-1");

        Order pending = sale(3L, "ORD-3");

        when(orderService.findByStatus(OrderStatus.PAID))
                .thenReturn(List.of(invoicedIndividually, coveredByGlobal, pending));
        return List.of(invoicedIndividually, coveredByGlobal, pending);
    }

    @Test
    @DisplayName("Sin filtro se listan todas las ventas")
    void withoutFilterEverySaleIsListed() {
        threeSales();

        assertThat(listed(null)).hasSize(3);
    }

    @Test
    @DisplayName("El filtro Facturadas deja solo las ventas con comprobante")
    void invoicedFilterKeepsOnlyInvoicedSales() {
        threeSales();

        assertThat(listed(SalesController.InvoiceStatusFilter.INVOICED))
                .extracting(Order::getOrderNumber)
                .containsExactly("ORD-1", "ORD-2");
    }

    @Test
    @DisplayName("El filtro No facturadas deja las ventas sin comprobante")
    void notInvoicedFilterKeepsPendingSales() {
        threeSales();

        assertThat(listed(SalesController.InvoiceStatusFilter.NOT_INVOICED))
                .extracting(Order::getOrderNumber)
                .containsExactly("ORD-3");
    }

    @Test
    @DisplayName("Una cuenta dividida a medias sigue contando como no facturada")
    void partiallyInvoicedSplitBillCountsAsNotInvoiced() {
        Order split = sale(4L, "ORD-4");
        account(split, 1L).setFacturamaCfdiId("cfdi-account-1");
        account(split, 2L);
        Order pending = sale(5L, "ORD-5");

        when(orderService.findByStatus(OrderStatus.PAID)).thenReturn(List.of(split, pending));

        assertThat(listed(SalesController.InvoiceStatusFilter.NOT_INVOICED))
                .extracting(Order::getOrderNumber)
                .containsExactly("ORD-4", "ORD-5");
        assertThat(listed(SalesController.InvoiceStatusFilter.INVOICED)).isEmpty();
    }

    @Test
    @DisplayName("Una cuenta dividida con todas sus cuentas facturadas cuenta como facturada")
    void fullyInvoicedSplitBillCountsAsInvoiced() {
        Order split = sale(6L, "ORD-6");
        account(split, 1L).setFacturamaCfdiId("cfdi-account-1");
        account(split, 2L).setFacturaGlobalCfdiId("global-account-2");

        when(orderService.findByStatus(OrderStatus.PAID)).thenReturn(List.of(split));

        assertThat(listed(SalesController.InvoiceStatusFilter.INVOICED))
                .extracting(Order::getOrderNumber)
                .containsExactly("ORD-6");
        assertThat(listed(SalesController.InvoiceStatusFilter.NOT_INVOICED)).isEmpty();
    }
}
