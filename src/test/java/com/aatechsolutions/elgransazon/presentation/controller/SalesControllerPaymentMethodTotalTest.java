package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.FacturamaService;
import com.aatechsolutions.elgransazon.application.service.InvoiceLinkService;
import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.PaymentTender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sales view filter by payment method: the money of a mixed collection must be split by method.
 *
 * <p>A $600 sale paid $300 debit + $300 credit has to report $300 when filtering debit. Reporting
 * its whole total would count the same sale once per method it used, so filtering debit and then
 * credit would add up to $1,200 for a $600 sale.</p>
 */
class SalesControllerPaymentMethodTotalTest {

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

    private Order sale(long id, String number, String total, String tip) {
        return Order.builder()
                .idOrder(id)
                .orderNumber(number)
                .status(OrderStatus.PAID)
                .total(new BigDecimal(total))
                .tip(tip != null ? new BigDecimal(tip) : BigDecimal.ZERO)
                .paidAt(LocalDateTime.of(2026, 10, 1, 20, 30))
                .createdAt(LocalDateTime.of(2026, 10, 1, 20, 0))
                .orderDetails(new ArrayList<>())
                .payments(new ArrayList<>())
                .build();
    }

    /** One portion of a whole-order collection ({@code payment == null} = tenders of the ticket). */
    private void tender(Order order, PaymentMethodType method, String amount) {
        order.getPaymentTenders().add(PaymentTender.builder()
                .order(order)
                .payment(null)
                .paymentMethod(method)
                .amount(new BigDecimal(amount))
                .build());
    }

    private Model listed(PaymentMethodType method) {
        Model model = new ExtendedModelMap();
        controller.listSales(null, null, null, method, null, 1, model);
        return model;
    }

    private BigDecimal money(Model model, String attribute) {
        return (BigDecimal) model.getAttribute(attribute);
    }

    @SuppressWarnings("unchecked")
    private BigDecimal rowAmount(Model model, long orderId) {
        Map<Long, BigDecimal> amounts = (Map<Long, BigDecimal>) model.getAttribute("methodAmounts");
        return amounts.get(orderId);
    }

    @Test
    @DisplayName("Un pedido de $600 en débito y crédito reporta $300 al filtrar débito")
    void mixedCollectionReportsOnlyTheFilteredMethod() {
        Order mixed = sale(1L, "ORD-1", "600.00", "0.00");
        tender(mixed, PaymentMethodType.CREDIT_CARD, "300.00");
        tender(mixed, PaymentMethodType.DEBIT_CARD, "300.00");
        when(orderService.findByStatus(OrderStatus.PAID)).thenReturn(List.of(mixed));

        Model debit = listed(PaymentMethodType.DEBIT_CARD);

        assertThat(money(debit, "totalSalesWithoutTip")).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(money(debit, "totalSales")).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(rowAmount(debit, 1L)).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(debit.getAttribute("methodFilterActive")).isEqualTo(true);
    }

    @Test
    @DisplayName("Los filtros de cada método suman el total de la venta, sin duplicarla")
    void perMethodTotalsAddUpToTheSaleOnce() {
        Order mixed = sale(1L, "ORD-1", "600.00", "0.00");
        tender(mixed, PaymentMethodType.CREDIT_CARD, "300.00");
        tender(mixed, PaymentMethodType.DEBIT_CARD, "300.00");
        when(orderService.findByStatus(OrderStatus.PAID)).thenReturn(List.of(mixed));

        BigDecimal debit = money(listed(PaymentMethodType.DEBIT_CARD), "totalSalesWithoutTip");
        BigDecimal credit = money(listed(PaymentMethodType.CREDIT_CARD), "totalSalesWithoutTip");

        assertThat(credit).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(debit.add(credit)).isEqualByComparingTo(new BigDecimal("600.00"));
    }

    @Test
    @DisplayName("Un cobro en cuatro métodos reparte el monto, no lo repite cuatro veces")
    void fourMethodCollectionSplitsEveryPortion() {
        Order mixed = sale(1L, "ORD-1", "600.00", "0.00");
        tender(mixed, PaymentMethodType.CASH, "150.00");
        tender(mixed, PaymentMethodType.CREDIT_CARD, "150.00");
        tender(mixed, PaymentMethodType.DEBIT_CARD, "150.00");
        tender(mixed, PaymentMethodType.TRANSFER, "150.00");
        when(orderService.findByStatus(OrderStatus.PAID)).thenReturn(List.of(mixed));

        BigDecimal sum = BigDecimal.ZERO;
        for (PaymentMethodType method : PaymentMethodType.values()) {
            BigDecimal portion = money(listed(method), "totalSalesWithoutTip");
            assertThat(portion).isEqualByComparingTo(new BigDecimal("150.00"));
            sum = sum.add(portion);
        }

        assertThat(sum).isEqualByComparingTo(new BigDecimal("600.00"));
    }

    @Test
    @DisplayName("Las propinas quedan fuera del filtro por método y la pantalla se marca como filtrada")
    void tipsStayOutOfTheMethodFilteredTotals() {
        Order mixed = sale(1L, "ORD-1", "600.00", "50.00");
        tender(mixed, PaymentMethodType.CREDIT_CARD, "300.00");
        tender(mixed, PaymentMethodType.DEBIT_CARD, "300.00");
        when(orderService.findByStatus(OrderStatus.PAID)).thenReturn(List.of(mixed));

        Model debit = listed(PaymentMethodType.DEBIT_CARD);

        assertThat(money(debit, "totalTips")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(money(debit, "totalSales")).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    @DisplayName("Sin filtro los totales siguen siendo los de la venta completa, con propina")
    void withoutFilterTotalsStayWhole() {
        Order mixed = sale(1L, "ORD-1", "600.00", "50.00");
        tender(mixed, PaymentMethodType.CREDIT_CARD, "300.00");
        tender(mixed, PaymentMethodType.DEBIT_CARD, "300.00");
        when(orderService.findByStatus(OrderStatus.PAID)).thenReturn(List.of(mixed));

        Model all = listed(null);

        assertThat(all.getAttribute("methodFilterActive")).isEqualTo(false);
        assertThat(money(all, "totalSalesWithoutTip")).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(money(all, "totalTips")).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(money(all, "totalSales")).isEqualByComparingTo(new BigDecimal("650.00"));
    }

    @Test
    @DisplayName("Una venta vieja sin porciones usa su método declarado por el total completo")
    void legacySaleWithoutTendersFallsBackToItsDeclaredMethod() {
        Order legacy = sale(1L, "ORD-1", "600.00", "0.00");
        legacy.setPaymentMethod(PaymentMethodType.DEBIT_CARD);
        when(orderService.findByStatus(OrderStatus.PAID)).thenReturn(List.of(legacy));

        assertThat(money(listed(PaymentMethodType.DEBIT_CARD), "totalSalesWithoutTip"))
                .isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(listed(PaymentMethodType.CASH).getAttribute("sales")).isEqualTo(List.of());
    }
}
