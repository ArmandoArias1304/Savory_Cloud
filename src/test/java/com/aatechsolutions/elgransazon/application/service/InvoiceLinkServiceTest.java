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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Self-invoice links generated after the payment was collected: a restaurant that contracted
 * the billing service later still has PAID sales without a link, so their reprinted ticket has
 * no QR. The service must create the missing links (one per invoiceable unit) and never touch a
 * sale that already has a link, was invoiced individually or was amparada by a factura global.
 */
class InvoiceLinkServiceTest {

    private static final String BASE_URL = "https://resto.example.com";
    private static final Long ORDER_ID = 7L;

    private OrderRepository orderRepository;
    private PaymentRepository paymentRepository;
    private InvoiceLinkService service;
    private Company company;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        service = new InvoiceLinkService(orderRepository, paymentRepository);
        company = Company.builder()
                .idCompany(1L)
                .slug("resto")
                .name("Restaurante Prueba")
                .timezone("America/Mexico_City")
                .build();
    }

    private Order paidOrder() {
        return Order.builder()
                .idOrder(ORDER_ID)
                .orderNumber("ORD-20260924-007")
                .status(OrderStatus.PAID)
                .total(new BigDecimal("232.00"))
                .paymentMethod(PaymentMethodType.CASH)
                .paidAt(LocalDateTime.of(2026, 9, 24, 18, 30))
                .build();
    }

    private Payment account(long id, String folio) {
        return Payment.builder()
                .idPayment(id)
                .paymentFolio(folio)
                .total(new BigDecimal("58.00"))
                .paymentMethod(PaymentMethodType.CASH)
                .paidAt(LocalDateTime.of(2026, 9, 24, 19, 0))
                .build();
    }

    private void givenOrder(Order order) {
        when(orderRepository.findByIdOrderAndCompany(ORDER_ID, company)).thenReturn(Optional.of(order));
    }

    @Test
    void wholeOrderSaleGetsItsLink() {
        Order order = paidOrder();
        givenOrder(order);
        when(paymentRepository.findByOrderIdOrderByAccountNumberAsc(ORDER_ID)).thenReturn(List.of());

        InvoiceLinkService.InvoiceLinkResult result = service.generateForSale(ORDER_ID, company, BASE_URL);

        assertThat(result.generated()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        assertThat(result.links()).singleElement()
                .satisfies(link -> {
                    assertThat(link.type()).isEqualTo(InvoiceLinkService.TYPE_ORDER);
                    assertThat(link.id()).isEqualTo(ORDER_ID);
                    assertThat(link.folio()).isEqualTo("ORD-20260924-007");
                    assertThat(link.url()).isEqualTo(BASE_URL + "/autofactura/" + order.getAutofacturaKey());
                });
        assertThat(order.getAutofacturaKey()).isEqualTo(order.getAutofacturaKey().trim());
        assertThat(UUID.fromString(order.getAutofacturaKey())).isNotNull();
        assertThat(order.getSelfInvoiceUrl()).isEqualTo(BASE_URL + "/autofactura/" + order.getAutofacturaKey());
    }

    @Test
    void saleThatAlreadyHasALinkIsSkippedAndNotOverwritten() {
        Order order = paidOrder();
        order.setAutofacturaKey("existing-key");
        order.setSelfInvoiceUrl(BASE_URL + "/autofactura/existing-key");
        givenOrder(order);
        when(paymentRepository.findByOrderIdOrderByAccountNumberAsc(ORDER_ID)).thenReturn(List.of());

        InvoiceLinkService.InvoiceLinkResult result = service.generateForSale(ORDER_ID, company, BASE_URL);

        assertThat(result.generated()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.links()).isEmpty();
        assertThat(order.getAutofacturaKey()).isEqualTo("existing-key");
        assertThat(order.getSelfInvoiceUrl()).isEqualTo(BASE_URL + "/autofactura/existing-key");
    }

    @Test
    void saleAlreadyInvoicedOrExcludedIsNotLinked() {
        Order invoicedIndividually = paidOrder();
        invoicedIndividually.setFacturamaCfdiId("cfdi-123");
        givenOrder(invoicedIndividually);
        when(paymentRepository.findByOrderIdOrderByAccountNumberAsc(ORDER_ID)).thenReturn(List.of());

        InvoiceLinkService.InvoiceLinkResult individual =
                service.generateForSale(ORDER_ID, company, BASE_URL);
        assertThat(individual.generated()).isZero();
        assertThat(invoicedIndividually.getAutofacturaKey()).isNull();

        Order coveredByGlobalInvoice = paidOrder();
        coveredByGlobalInvoice.setFacturaGlobalCfdiId("global-456");
        givenOrder(coveredByGlobalInvoice);

        assertThat(service.generateForSale(ORDER_ID, company, BASE_URL).generated()).isZero();
        assertThat(coveredByGlobalInvoice.getAutofacturaKey()).isNull();

        Order excluded = paidOrder();
        excluded.setFacturaGlobalExcluida(true);
        givenOrder(excluded);

        assertThat(service.generateForSale(ORDER_ID, company, BASE_URL).generated()).isZero();
        assertThat(excluded.getAutofacturaKey()).isNull();
    }

    @Test
    void eachSplitAccountGetsItsOwnLink() {
        Order order = paidOrder();
        givenOrder(order);

        Payment alreadyLinked = account(1L, "ORD-20260924-007-01");
        alreadyLinked.setAutofacturaKey("account-key");
        alreadyLinked.setSelfInvoiceUrl(BASE_URL + "/autofactura/account-key");

        Payment invoiced = account(2L, "ORD-20260924-007-02");
        invoiced.setFacturamaCfdiId("cfdi-account");

        Payment pending = account(3L, "ORD-20260924-007-03");

        when(paymentRepository.findByOrderIdOrderByAccountNumberAsc(ORDER_ID))
                .thenReturn(List.of(alreadyLinked, invoiced, pending));

        InvoiceLinkService.InvoiceLinkResult result = service.generateForSale(ORDER_ID, company, BASE_URL);

        assertThat(result.generated()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(result.links()).singleElement()
                .satisfies(link -> {
                    assertThat(link.type()).isEqualTo(InvoiceLinkService.TYPE_PAYMENT);
                    assertThat(link.id()).isEqualTo(3L);
                    assertThat(link.folio()).isEqualTo("ORD-20260924-007-03");
                    assertThat(link.url()).isEqualTo(BASE_URL + "/autofactura/" + pending.getAutofacturaKey());
                });
        // The split bill is invoiced per account: the order itself never gets a link.
        assertThat(order.getAutofacturaKey()).isNull();
        assertThat(alreadyLinked.getAutofacturaKey()).isEqualTo("account-key");
        assertThat(invoiced.getAutofacturaKey()).isNull();
    }

    @Test
    void baseUrlWithTrailingSlashDoesNotDuplicateTheSeparator() {
        Order order = paidOrder();
        givenOrder(order);
        when(paymentRepository.findByOrderIdOrderByAccountNumberAsc(ORDER_ID)).thenReturn(List.of());

        service.generateForSale(ORDER_ID, company, BASE_URL + "/");

        assertThat(order.getSelfInvoiceUrl()).startsWith(BASE_URL + "/autofactura/");
        assertThat(order.getSelfInvoiceUrl()).doesNotContain("//autofactura");
    }

    @Test
    void unknownSaleIsRejected() {
        when(orderRepository.findByIdOrderAndCompany(ORDER_ID, company)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateForSale(ORDER_ID, company, BASE_URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No se encontró la venta");
    }
}
