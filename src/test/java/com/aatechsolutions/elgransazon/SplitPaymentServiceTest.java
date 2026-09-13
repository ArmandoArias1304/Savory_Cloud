package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.FacturamaService;
import com.aatechsolutions.elgransazon.application.service.SplitPaymentService;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.domain.repository.PaymentRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.presentation.dto.SplitAccountDTO;
import com.aatechsolutions.elgransazon.presentation.dto.SplitItemDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the money invariants of the split-bill math:
 *   Σ account.total == order.total
 *   Σ account.deliveryCost == order.deliveryCost
 *   Σ account.orderDiscount == order.orderDiscount
 *   account folios follow ORD-YYYYMMDD-NNN-XX
 *   assigned quantities per line == line quantity (ITEMS mode)
 */
class SplitPaymentServiceTest {

    private SplitPaymentService service;
    private Company company;

    @BeforeEach
    void setUp() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        FacturamaService facturamaService = mock(FacturamaService.class);
        when(facturamaService.getConfigForCurrentCompany()).thenReturn(Optional.empty());
        service = new SplitPaymentService(orderRepository, paymentRepository, facturamaService);

        company = Company.builder().idCompany(1L).name("Test").slug("test").build();
        CompanyContext.setCurrentCompany(company);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private Order buildOrder() {
        // Line 1: 2 × $100 (no complements)
        OrderDetail d1 = OrderDetail.builder()
                .idOrderDetail(1L)
                .itemName("Plato 1")
                .quantity(2)
                .unitPrice(new BigDecimal("100.00"))
                .subtotal(new BigDecimal("200.00"))
                .itemStatus(OrderStatus.DELIVERED)
                .selectedComplements(new ArrayList<>())
                .build();
        // Line 2: 1 × $80 + complement $12 (2 × $6)
        OrderDetail d2 = OrderDetail.builder()
                .idOrderDetail(2L)
                .itemName("Plato 2")
                .quantity(1)
                .unitPrice(new BigDecimal("80.00"))
                .subtotal(new BigDecimal("80.00"))
                .itemStatus(OrderStatus.DELIVERED)
                .selectedComplements(new ArrayList<>())
                .build();
        OrderDetailComplement comp = OrderDetailComplement.builder()
                .idOrderDetailComplement(1L)
                .complementName("Extra queso")
                .quantity(2)
                .unitPrice(new BigDecimal("6.00"))
                .subtotal(new BigDecimal("12.00"))
                .build();
        d2.addComplement(comp);
        // Line 3: combo parent, 1 × $40
        OrderDetail d3 = OrderDetail.builder()
                .idOrderDetail(3L)
                .itemName("Combo")
                .quantity(1)
                .unitPrice(new BigDecimal("40.00"))
                .subtotal(new BigDecimal("40.00"))
                .comboGroupId("COMBO-1")
                .isComboParentSnapshot(true)
                .itemStatus(OrderStatus.DELIVERED)
                .selectedComplements(new ArrayList<>())
                .build();

        Order order = Order.builder()
                .idOrder(10L)
                .company(company)
                .orderNumber("ORD-20260906-001")
                .orderType(OrderType.DELIVERY)
                .status(OrderStatus.DELIVERED)
                .taxRate(new BigDecimal("16.00"))
                .deliveryCost(new BigDecimal("25.00"))
                .orderDiscount(new BigDecimal("10.00"))
                .paymentMethod(PaymentMethodType.CASH)
                .orderDetails(new ArrayList<>())
                .build();
        order.addOrderDetail(d1);
        order.addOrderDetail(d2);
        order.addOrderDetail(d3);
        order.recalculateAmounts();
        return order;
    }

    private List<SplitAccountDTO> equalAccounts(int n) {
        List<SplitAccountDTO> accounts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            SplitAccountDTO acc = new SplitAccountDTO();
            acc.setIndex(i + 1);
            acc.setPersonLabel("Persona " + (i + 1));
            acc.setPaymentMethod("CASH");
            acc.setTip(BigDecimal.ZERO);
            accounts.add(acc);
        }
        return accounts;
    }

    @Test
    void equalSplitIsRejected() {
        // SAT: a product cannot be divided, so "Partes iguales" (fractional
        // per-person quantities) is no longer allowed — the caller must use
        // per-person assignment of whole items or a single payment.
        Order order = buildOrder();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createSplitPayments(
                        order, SplitMode.EQUAL, equalAccounts(2), null, "tester", true, "https://test.local"));
        assertTrue(ex.getMessage().contains("Partes iguales"));
        assertTrue(order.getPayments().isEmpty(), "no payments may be created for a rejected split");
    }

    @Test
    void itemSplitByWholeUnitsPreservesTotals() {
        Order order = buildOrder();
        // 2 people: person 1 takes 1 unit of line 1 (qty 2), the whole line 2 and line 3;
        // person 2 takes the remaining unit of line 1.
        SplitAccountDTO p1 = new SplitAccountDTO();
        p1.setIndex(1);
        p1.setPersonLabel("Persona 1");
        p1.setPaymentMethod("CREDIT_CARD");
        p1.setTip(new BigDecimal("5.00"));
        p1.setItems(List.of(
                item(1L, "1"),
                item(2L, "1"),
                item(3L, "1")));

        SplitAccountDTO p2 = new SplitAccountDTO();
        p2.setIndex(2);
        p2.setPersonLabel("Persona 2");
        p2.setPaymentMethod("DEBIT_CARD");
        p2.setTip(BigDecimal.ZERO);
        p2.setItems(List.of(item(1L, "1")));

        List<Payment> payments = service.createSplitPayments(
                order, SplitMode.ITEMS, List.of(p1, p2), null, "tester", true, "https://test.local");

        assertEquals(2, payments.size());
        assertEquals("ORD-20260906-001-01", payments.get(0).getPaymentFolio());
        assertEquals("ORD-20260906-001-02", payments.get(1).getPaymentFolio());

        BigDecimal sumTotals = payments.stream().map(Payment::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumDelivery = payments.stream().map(Payment::getDeliveryCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumDiscount = payments.stream().map(Payment::getOrderDiscount).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(order.getTotal().setScale(2, RoundingMode.HALF_UP), sumTotals.setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("25.00"), sumDelivery);
        assertEquals(new BigDecimal("10.00"), sumDiscount);

        // Person 1 pays more than person 2 (they took more items)
        assertTrue(payments.get(0).getTotal().compareTo(payments.get(1).getTotal()) > 0);

        // Whole-unit splits: person 1 line = 1 unit of the qty-2 line
        PaymentDetail line1OfP1 = payments.get(0).getPaymentDetails().stream()
                .filter(pd -> pd.getOrderDetail().getIdOrderDetail().equals(1L))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("1"), line1OfP1.getQuantity().stripTrailingZeros());
        assertEquals(new BigDecimal("100.00"), line1OfP1.getTotal());

        // Line 1 qty (2) is fully covered: 1 + 1
        BigDecimal line1Sum = payments.stream()
                .flatMap(p -> p.getPaymentDetails().stream())
                .filter(pd -> pd.getOrderDetail().getIdOrderDetail().equals(1L))
                .map(PaymentDetail::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("2.0000"), line1Sum.setScale(4, RoundingMode.HALF_UP));

        // Complements of line 2 stay with person 1 (whole line)
        PaymentDetail d2OfP1 = payments.get(0).getPaymentDetails().stream()
                .filter(pd -> pd.getOrderDetail().getIdOrderDetail().equals(2L))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("12.00"), d2OfP1.getComplementSubtotal());
        assertNotNull(d2OfP1.getComplementDetails());
        assertTrue(d2OfP1.getComplementDetails().contains("Extra queso"));
    }

    @Test
    void fractionalItemAssignmentIsRejected() {
        Order order = buildOrder();
        // Line 1 has integer qty 2 → halves are not allowed in per-person mode
        SplitAccountDTO p1 = new SplitAccountDTO();
        p1.setIndex(1);
        p1.setPaymentMethod("CASH");
        p1.setItems(List.of(item(1L, "1.5")));
        SplitAccountDTO p2 = new SplitAccountDTO();
        p2.setIndex(2);
        p2.setPaymentMethod("CASH");
        p2.setItems(List.of(item(1L, "0.5")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.createSplitPayments(order, SplitMode.ITEMS, List.of(p1, p2), null, "tester", true, "https://x"));
        assertTrue(ex.getMessage().contains("unidades enteras"), ex.getMessage());
    }

    @Test
    void overAssignedItemIsRejected() {
        Order order = buildOrder();
        // Person 1 claims 3 of a line that only has 2 units
        SplitAccountDTO p1 = new SplitAccountDTO();
        p1.setIndex(1);
        p1.setPaymentMethod("CASH");
        p1.setItems(List.of(item(1L, "3")));
        SplitAccountDTO p2 = new SplitAccountDTO();
        p2.setIndex(2);
        p2.setPaymentMethod("CASH");
        p2.setItems(List.of(item(1L, "1")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.createSplitPayments(order, SplitMode.ITEMS, List.of(p1, p2), null, "tester", true, "https://x"));
        assertTrue(ex.getMessage().contains("no puede asignar más de"), ex.getMessage());
    }

    @Test
    void unassignedItemsAreRejected() {
        Order order = buildOrder();
        SplitAccountDTO p1 = new SplitAccountDTO();
        p1.setIndex(1);
        p1.setPaymentMethod("CASH");
        p1.setItems(List.of(item(1L, "1")));
        SplitAccountDTO p2 = new SplitAccountDTO();
        p2.setIndex(2);
        p2.setPaymentMethod("CASH");
        p2.setItems(List.of(item(1L, "1")));

        // Lines 2 and 3 are never assigned → must throw
        assertThrows(IllegalArgumentException.class, () ->
                service.createSplitPayments(order, SplitMode.ITEMS, List.of(p1, p2), null, "tester", true, "https://x"));
    }

    @Test
    void orderWithUndeliveredItemCannotBeCharged() {
        Order order = buildOrder();
        // A new item was added later and is still PENDING → cannot charge yet
        order.getOrderDetails().get(0).setItemStatus(OrderStatus.PENDING);

        SplitAccountDTO p1 = new SplitAccountDTO();
        p1.setIndex(1);
        p1.setPaymentMethod("CASH");
        p1.setItems(List.of(item(1L, "1")));
        SplitAccountDTO p2 = new SplitAccountDTO();
        p2.setIndex(2);
        p2.setPaymentMethod("CASH");
        p2.setItems(List.of(item(1L, "1"), item(2L, "1"), item(3L, "1")));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.createSplitPayments(order, SplitMode.ITEMS, List.of(p1, p2), null, "tester", true, "https://x"));
        assertTrue(ex.getMessage().contains("sin entregar"), ex.getMessage());
    }

    /**
     * Open dine-in order: line 1 (2 × $100) and line 2 (1 × $80) already
     * ENTREGADO; line 3 (1 × $50) still PENDING (extra round being prepared).
     */
    private Order buildDineInOpenOrder() {
        OrderDetail d1 = OrderDetail.builder()
                .idOrderDetail(1L)
                .itemName("Coca-Cola")
                .quantity(2)
                .unitPrice(new BigDecimal("100.00"))
                .subtotal(new BigDecimal("200.00"))
                .itemStatus(OrderStatus.DELIVERED)
                .selectedComplements(new ArrayList<>())
                .build();
        OrderDetail d2 = OrderDetail.builder()
                .idOrderDetail(2L)
                .itemName("Hamburguesa")
                .quantity(1)
                .unitPrice(new BigDecimal("80.00"))
                .subtotal(new BigDecimal("80.00"))
                .itemStatus(OrderStatus.DELIVERED)
                .selectedComplements(new ArrayList<>())
                .build();
        OrderDetail d3 = OrderDetail.builder()
                .idOrderDetail(3L)
                .itemName("Postre")
                .quantity(1)
                .unitPrice(new BigDecimal("50.00"))
                .subtotal(new BigDecimal("50.00"))
                .itemStatus(OrderStatus.PENDING)
                .selectedComplements(new ArrayList<>())
                .build();

        Order order = Order.builder()
                .idOrder(20L)
                .company(company)
                .orderNumber("ORD-20260906-020")
                .orderType(OrderType.DINE_IN)
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethodType.CREDIT_CARD)
                .orderDetails(new ArrayList<>())
                .build();
        order.addOrderDetail(d1);
        order.addOrderDetail(d2);
        order.addOrderDetail(d3);
        return order;
    }

    @Test
    void departingGuestPaysOnlyDeliveredItemsAndOrderStaysOpen() {
        Order order = buildDineInOpenOrder();
        // The guest leaving consumed 1 of the 2 Cokes + the hamburguesa; the
        // other Coke and the pending postre stay on the open order.
        SplitAccountDTO leaver = new SplitAccountDTO();
        leaver.setIndex(1);
        leaver.setPersonLabel("Persona que se va");
        leaver.setPaymentMethod("CREDIT_CARD");
        leaver.setTip(BigDecimal.ZERO);
        leaver.setItems(List.of(item(1L, "1"), item(2L, "1")));

        List<Payment> payments = service.collectDepartingGuests(
                order, List.of(leaver), null, "tester", true, "https://test.local");

        assertEquals(1, payments.size());
        assertEquals("ORD-20260906-020-01", payments.get(0).getPaymentFolio());
        assertEquals(new BigDecimal("180.00"), payments.get(0).getTotal().setScale(2, RoundingMode.HALF_UP),
                "100 (1 Coke) + 80 (burger)");

        // The charged line keeps the unpaid unit on the order; pending line untouched
        OrderDetail coke = order.getOrderDetails().get(0);
        assertEquals(new BigDecimal("1.0000"), coke.getPaidQuantityOrZero().setScale(4, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("1.0000"), coke.getRemainingQuantity().setScale(4, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("1.0000"), order.getOrderDetails().get(1).getPaidQuantityOrZero().setScale(4, RoundingMode.HALF_UP));
        assertEquals(BigDecimal.ZERO, order.getOrderDetails().get(2).getPaidQuantityOrZero());

        // Order stays open and the payment is attached to it
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(1, order.getPayments().size());
    }

    @Test
    void fullSplitAfterDepartureChargesOnlyTheRemainingUnits() {
        Order order = buildDineInOpenOrder();
        order.recalculateAmounts(); // total 330.00 (2×100 + 80 + 50)
        assertEquals(new BigDecimal("330.00"), order.getTotal().setScale(2, RoundingMode.HALF_UP));

        // A guest leaves: 1 Coke + the burger (180.00); the order stays open
        SplitAccountDTO leaver = new SplitAccountDTO();
        leaver.setIndex(1);
        leaver.setPersonLabel("Persona que se va");
        leaver.setPaymentMethod("CREDIT_CARD");
        leaver.setItems(List.of(item(1L, "1"), item(2L, "1")));
        service.collectDepartingGuests(order, List.of(leaver), null, "tester", true, "https://x");

        // The order exposes what was collected and what is still owed
        assertEquals(new BigDecimal("180.00"), order.getCollectedAmount().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("150.00"), order.getRemainingTotal().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("1.0000"),
                order.getOrderDetails().get(0).getPaidQuantityOrZero().setScale(4, RoundingMode.HALF_UP));
        assertFalse(order.getOrderDetails().get(0).isFullyPaid()); // 1 of 2 Cokes paid
        assertTrue(order.getOrderDetails().get(1).isFullyPaid());  // burger fully paid

        // The rest of the party's order is delivered: settle the remaining units
        order.setStatus(OrderStatus.DELIVERED);
        order.getOrderDetails().get(2).setItemStatus(OrderStatus.DELIVERED);

        SplitAccountDTO p1 = new SplitAccountDTO();
        p1.setIndex(1);
        p1.setPaymentMethod("CREDIT_CARD");
        p1.setItems(List.of(item(1L, "1"))); // remaining Coke
        SplitAccountDTO p2 = new SplitAccountDTO();
        p2.setIndex(2);
        p2.setPaymentMethod("DEBIT_CARD");
        p2.setItems(List.of(item(3L, "1"))); // postre

        List<Payment> payments = service.createSplitPayments(
                order, SplitMode.ITEMS, List.of(p1, p2), null, "tester", true, "https://x");

        // Account folios continue from the payments already on the order: the
        // departing guest took -01, so the settlement is -02 and -03 (never a
        // duplicate that would violate uk_payment_folio_company).
        assertEquals("ORD-20260906-020-02", payments.get(0).getPaymentFolio());
        assertEquals("ORD-20260906-020-03", payments.get(1).getPaymentFolio());
        assertEquals(2, payments.get(0).getAccountNumber());
        assertEquals(3, payments.get(1).getAccountNumber());

        BigDecimal sumTotals = payments.stream().map(Payment::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("150.00"), sumTotals.setScale(2, RoundingMode.HALF_UP),
                "only the remaining units are charged: 100 (Coke) + 50 (postre)");
        assertEquals(new BigDecimal("100.00"), payments.get(0).getTotal().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("50.00"), payments.get(1).getTotal().setScale(2, RoundingMode.HALF_UP));

        // Every unit of every line is now charged exactly once (never twice)
        assertEquals(new BigDecimal("2.0000"),
                order.getOrderDetails().get(0).getPaidQuantityOrZero().setScale(4, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("1.0000"),
                order.getOrderDetails().get(1).getPaidQuantityOrZero().setScale(4, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("1.0000"),
                order.getOrderDetails().get(2).getPaidQuantityOrZero().setScale(4, RoundingMode.HALF_UP));
    }

    @Test
    void equalSplitAfterDepartureIsRejected() {
        // Even after departing-guest charges the remaining guests must settle
        // per person (whole items) or in one payment; parts-equal is rejected.
        Order order = buildDineInOpenOrder();
        order.recalculateAmounts();

        SplitAccountDTO leaver = new SplitAccountDTO();
        leaver.setIndex(1);
        leaver.setPaymentMethod("CREDIT_CARD");
        leaver.setItems(List.of(item(1L, "1"), item(2L, "1")));
        service.collectDepartingGuests(order, List.of(leaver), null, "tester", true, "https://x");

        order.setStatus(OrderStatus.DELIVERED);
        order.getOrderDetails().get(2).setItemStatus(OrderStatus.DELIVERED);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createSplitPayments(
                        order, SplitMode.EQUAL, equalAccounts(2), null, "tester", true, "https://x"));
        assertTrue(ex.getMessage().contains("Partes iguales"));
        assertEquals(1, order.getPayments().size(), "only the departing-guest charge exists");
    }

    @Test
    void tipsAccumulateAcrossDepartingGuestsAndFinalSettlement() {
        // Regression: tips from departing-guest/split charges must add up on the
        // order (order.tip), so tip views/reports show every tip collected, not
        // only the tip of the last charge.
        Order order = buildDineInOpenOrder();
        order.recalculateAmounts(); // total 330.00

        // 1st guest leaves: 1 Coke + burger (180) with $10 tip
        SplitAccountDTO g1 = new SplitAccountDTO();
        g1.setIndex(1);
        g1.setPersonLabel("Persona que se va 1");
        g1.setPaymentMethod("CREDIT_CARD");
        g1.setTip(new BigDecimal("10.00"));
        g1.setItems(List.of(item(1L, "1"), item(2L, "1")));
        service.collectDepartingGuests(order, List.of(g1), null, "tester", true, "https://x");
        assertEquals(new BigDecimal("10.00"), order.getTip().setScale(2, RoundingMode.HALF_UP),
                "tip of the first departing guest is reflected on the order");

        // 2nd guest leaves: remaining Coke (100) with $15 tip
        SplitAccountDTO g2 = new SplitAccountDTO();
        g2.setIndex(1);
        g2.setPersonLabel("Persona que se va 2");
        g2.setPaymentMethod("CREDIT_CARD");
        g2.setTip(new BigDecimal("15.00"));
        g2.setItems(List.of(item(1L, "1")));
        service.collectDepartingGuests(order, List.of(g2), null, "tester", true, "https://x");
        assertEquals(new BigDecimal("25.00"), order.getTip().setScale(2, RoundingMode.HALF_UP),
                "tips accumulate as each guest pays");

        // Last item delivered; the rest of the party settles globally with $100 tip
        order.setStatus(OrderStatus.DELIVERED);
        order.getOrderDetails().get(2).setItemStatus(OrderStatus.DELIVERED);
        SplitAccountDTO global = new SplitAccountDTO();
        global.setIndex(1);
        global.setPersonLabel("Cuenta");
        global.setPaymentMethod("CREDIT_CARD");
        global.setTip(new BigDecimal("100.00"));
        global.setItems(List.of(item(3L, "1"))); // postre
        service.createSplitPayments(order, SplitMode.ITEMS, List.of(global), null, "tester", true, "https://x");

        assertEquals(new BigDecimal("125.00"), order.getTip().setScale(2, RoundingMode.HALF_UP),
                "final settlement keeps the tips of all earlier charges (10 + 15 + 100)");
    }

    @Test
    void departureCantDoubleChargeTheSameLastUnit() {
        Order order = buildDineInOpenOrder();
        // Line 1 has only 2 remaining units; two guests both claim 2 → the
        // second claim must be rejected (only 0 left for them).
        SplitAccountDTO g1 = new SplitAccountDTO();
        g1.setIndex(1);
        g1.setPaymentMethod("CREDIT_CARD");
        g1.setItems(List.of(item(1L, "2")));
        SplitAccountDTO g2 = new SplitAccountDTO();
        g2.setIndex(2);
        g2.setPaymentMethod("CREDIT_CARD");
        g2.setItems(List.of(item(1L, "1")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.collectDepartingGuests(order, List.of(g1, g2), null, "tester", true, "https://x"));
        assertTrue(ex.getMessage().contains("no puede cobrar más de 0"), ex.getMessage());
        assertEquals(BigDecimal.ZERO, order.getOrderDetails().get(0).getPaidQuantityOrZero());
    }

    @Test
    void departureRejectsPendingItemsAndFractionalQuantities() {
        // Pending (not delivered) item cannot be charged to the leaving guest
        Order order1 = buildDineInOpenOrder();
        SplitAccountDTO leaver = new SplitAccountDTO();
        leaver.setIndex(1);
        leaver.setPaymentMethod("CREDIT_CARD");
        leaver.setItems(List.of(item(3L, "1"))); // postre still PENDING

        IllegalArgumentException pendingEx = assertThrows(IllegalArgumentException.class, () ->
                service.collectDepartingGuests(order1, List.of(leaver), null, "tester", true, "https://x"));
        assertTrue(pendingEx.getMessage().contains("ítem no válido"), pendingEx.getMessage());

        // Fractional whole-unit assignment is rejected too
        Order order2 = buildDineInOpenOrder();
        SplitAccountDTO leaver2 = new SplitAccountDTO();
        leaver2.setIndex(1);
        leaver2.setPaymentMethod("CREDIT_CARD");
        leaver2.setItems(List.of(item(1L, "1.5")));

        IllegalArgumentException fracEx = assertThrows(IllegalArgumentException.class, () ->
                service.collectDepartingGuests(order2, List.of(leaver2), null, "tester", true, "https://x"));
        assertTrue(fracEx.getMessage().contains("unidades enteras"), fracEx.getMessage());
    }

    @Test
    void departureRejectedOnDeliveryOrPaidOrders() {
        Order delivery = buildOrder(); // DELIVERY order type
        SplitAccountDTO p = new SplitAccountDTO();
        p.setIndex(1);
        p.setPaymentMethod("CASH");
        p.setItems(List.of(item(1L, "1")));
        IllegalStateException deliveryEx = assertThrows(IllegalStateException.class, () ->
                service.collectDepartingGuests(delivery, List.of(p), null, "tester", true, "https://x"));
        assertTrue(deliveryEx.getMessage().contains("solo aplica en pedidos de mesa"), deliveryEx.getMessage());

        Order paid = buildDineInOpenOrder();
        paid.setStatus(OrderStatus.PAID);
        IllegalStateException paidEx = assertThrows(IllegalStateException.class, () ->
                service.collectDepartingGuests(paid, List.of(p), null, "tester", true, "https://x"));
        assertTrue(paidEx.getMessage().contains("ya fue pagado"), paidEx.getMessage());
    }

    @Test
    void cashTipsAreZeroedWhenFlagged() {
        Order order = buildOrder();
        SplitAccountDTO p1 = new SplitAccountDTO();
        p1.setIndex(1);
        p1.setPaymentMethod("CASH");
        p1.setTip(new BigDecimal("10.00"));
        p1.setItems(List.of(item(1L, "1"), item(2L, "1"), item(3L, "1")));
        SplitAccountDTO p2 = new SplitAccountDTO();
        p2.setIndex(2);
        p2.setPaymentMethod("CASH");
        p2.setTip(new BigDecimal("7.50"));
        p2.setItems(List.of(item(1L, "1")));

        List<Payment> payments = service.createSplitPayments(
                order, SplitMode.ITEMS, List.of(p1, p2), null, "tester", true, "https://x");

        assertEquals(BigDecimal.ZERO, payments.get(0).getTip());
        assertEquals(BigDecimal.ZERO, payments.get(1).getTip());
    }

    /**
     * Open dine-in order with a COMBO whose paid complement lives on the child:
     *   - combo parent 1 × $100 (DELIVERED, no complements of its own)
     *   - child "Pizza" 1 × $0 (DELIVERED) + complement Arrachera $100
     *   - postre 1 × $50 (PENDING, extra round being prepared)
     * Total = 250.00.
     */
    private Order buildComboDepartureOrder() {
        OrderDetail comboParent = OrderDetail.builder()
                .idOrderDetail(1L)
                .itemName("Combo Pareja")
                .quantity(1)
                .unitPrice(new BigDecimal("100.00"))
                .subtotal(new BigDecimal("100.00"))
                .comboGroupId("COMBO-1")
                .isComboParentSnapshot(true)
                .itemStatus(OrderStatus.DELIVERED)
                .selectedComplements(new ArrayList<>())
                .build();
        OrderDetail comboChild = OrderDetail.builder()
                .idOrderDetail(2L)
                .itemName("Pizza Atrevida")
                .quantity(1)
                .unitPrice(BigDecimal.ZERO)
                .subtotal(BigDecimal.ZERO)
                .comboGroupId("COMBO-1")
                .isComboParentSnapshot(false)
                .itemStatus(OrderStatus.DELIVERED)
                .selectedComplements(new ArrayList<>())
                .build();
        OrderDetailComplement arrachera = OrderDetailComplement.builder()
                .idOrderDetailComplement(1L)
                .complementName("Arrachera")
                .quantity(1)
                .unitPrice(new BigDecimal("100.00"))
                .subtotal(new BigDecimal("100.00"))
                .build();
        comboChild.addComplement(arrachera);
        OrderDetail postre = OrderDetail.builder()
                .idOrderDetail(3L)
                .itemName("Postre")
                .quantity(1)
                .unitPrice(new BigDecimal("50.00"))
                .subtotal(new BigDecimal("50.00"))
                .itemStatus(OrderStatus.PENDING)
                .selectedComplements(new ArrayList<>())
                .build();

        Order order = Order.builder()
                .idOrder(30L)
                .company(company)
                .orderNumber("ORD-20260906-030")
                .orderType(OrderType.DINE_IN)
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethodType.CREDIT_CARD)
                .orderDetails(new ArrayList<>())
                .build();
        order.addOrderDetail(comboParent);
        order.addOrderDetail(comboChild);
        order.addOrderDetail(postre);
        order.recalculateAmounts();
        return order;
    }

    @Test
    void departingGuestPaysComboIncludingChildComplements() {
        // Regression: a combo's paid complements live on its $0-priced children.
        // Charging the combo to the departing guest must include them (100 combo
        // + 100 Arrachera = 200), not just the combo price.
        Order order = buildComboDepartureOrder();
        assertEquals(new BigDecimal("250.00"), order.getTotal().setScale(2, RoundingMode.HALF_UP));

        SplitAccountDTO leaver = new SplitAccountDTO();
        leaver.setIndex(1);
        leaver.setPersonLabel("Persona que se va");
        leaver.setPaymentMethod("CREDIT_CARD");
        leaver.setTip(BigDecimal.ZERO);
        leaver.setItems(List.of(item(1L, "1"))); // the combo

        List<Payment> payments = service.collectDepartingGuests(
                order, List.of(leaver), null, "tester", true, "https://test.local");

        assertEquals(1, payments.size());
        assertEquals(new BigDecimal("200.00"), payments.get(0).getTotal().setScale(2, RoundingMode.HALF_UP),
                "combo price (100) + child complement (100)");

        PaymentDetail comboPd = payments.get(0).getPaymentDetails().stream()
                .filter(pd -> pd.getOrderDetail().getIdOrderDetail().equals(1L))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("100.00"), comboPd.getSubtotal());
        assertEquals(new BigDecimal("100.00"), comboPd.getComplementSubtotal(),
                "the child's complement is charged on the combo line");
        assertEquals(new BigDecimal("200.00"), comboPd.getTotal());
        assertNotNull(comboPd.getComplementDetails());
        assertTrue(comboPd.getComplementDetails().contains("Arrachera"), comboPd.getComplementDetails());
        assertTrue(comboPd.getComplementDetails().contains("Pizza Atrevida"), comboPd.getComplementDetails());

        // The order knows the full 200 was collected (no double charge later)
        assertEquals(new BigDecimal("200.00"), order.getCollectedAmount().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("50.00"), order.getRemainingTotal().setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void comboChildComplementsNotChargedTwiceAtFinalSettlement() {
        Order order = buildComboDepartureOrder();

        SplitAccountDTO leaver = new SplitAccountDTO();
        leaver.setIndex(1);
        leaver.setPersonLabel("Persona que se va");
        leaver.setPaymentMethod("CREDIT_CARD");
        leaver.setItems(List.of(item(1L, "1"))); // combo with Arrachera
        service.collectDepartingGuests(order, List.of(leaver), null, "tester", true, "https://x");
        assertEquals(new BigDecimal("200.00"), order.getCollectedAmount().setScale(2, RoundingMode.HALF_UP));

        // The last postre is delivered and the rest of the party settles
        order.setStatus(OrderStatus.DELIVERED);
        order.getOrderDetails().get(2).setItemStatus(OrderStatus.DELIVERED);

        SplitAccountDTO remaining = new SplitAccountDTO();
        remaining.setIndex(1);
        remaining.setPaymentMethod("CREDIT_CARD");
        remaining.setItems(List.of(item(3L, "1"))); // postre only

        List<Payment> payments = service.createSplitPayments(
                order, SplitMode.ITEMS, List.of(remaining), null, "tester", true, "https://x");

        BigDecimal sumTotals = payments.stream().map(Payment::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("50.00"), sumTotals.setScale(2, RoundingMode.HALF_UP),
                "the Arrachera (100) was already collected from the departing guest");
        assertEquals(new BigDecimal("50.00"), payments.get(0).getTotal().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("0.00"), order.getRemainingTotal().setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void itemSplitChargesComboChildComplementsToTheComboOwner() {
        // Full delivered order: combo (100 + Arrachera 100 on the child) + Coca 30.
        Order order = buildComboDepartureOrder();
        order.setStatus(OrderStatus.DELIVERED);
        order.getOrderDetails().get(2).setItemStatus(OrderStatus.DELIVERED);
        OrderDetail coca = OrderDetail.builder()
                .idOrderDetail(4L)
                .itemName("Coca-Cola")
                .quantity(1)
                .unitPrice(new BigDecimal("30.00"))
                .subtotal(new BigDecimal("30.00"))
                .itemStatus(OrderStatus.DELIVERED)
                .selectedComplements(new ArrayList<>())
                .build();
        order.addOrderDetail(coca);
        order.recalculateAmounts();
        assertEquals(new BigDecimal("280.00"), order.getTotal().setScale(2, RoundingMode.HALF_UP));

        SplitAccountDTO p1 = new SplitAccountDTO();
        p1.setIndex(1);
        p1.setPersonLabel("Persona 1");
        p1.setPaymentMethod("CREDIT_CARD");
        p1.setItems(List.of(item(1L, "1"))); // combo
        SplitAccountDTO p2 = new SplitAccountDTO();
        p2.setIndex(2);
        p2.setPersonLabel("Persona 2");
        p2.setPaymentMethod("DEBIT_CARD");
        p2.setItems(List.of(item(4L, "1"), item(3L, "1"))); // coca + postre

        List<Payment> payments = service.createSplitPayments(
                order, SplitMode.ITEMS, List.of(p1, p2), null, "tester", true, "https://x");

        assertEquals(new BigDecimal("200.00"), payments.get(0).getTotal().setScale(2, RoundingMode.HALF_UP),
                "the combo owner pays the child complement, not the last account");
        assertEquals(new BigDecimal("80.00"), payments.get(1).getTotal().setScale(2, RoundingMode.HALF_UP),
                "coca 30 + postre 50, without absorbing the combo complement");
        BigDecimal sumTotals = payments.stream().map(Payment::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(order.getTotal().setScale(2, RoundingMode.HALF_UP), sumTotals.setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void splitBillItemsExposeEffectiveComboComplements() {
        Order order = buildComboDepartureOrder();
        // Only the combo parent is offered (children excluded), with the
        // child's complement folded into its comps so the editor totals match
        // what the backend charges.
        List<java.util.Map<String, Object>> items = order.getSplitBillItems();
        assertEquals(2, items.size()); // combo parent + postre (pending but listed)
        java.util.Map<String, Object> combo = items.get(0);
        assertEquals(1L, combo.get("id"));
        assertEquals(new BigDecimal("1"), ((BigDecimal) combo.get("qty")).stripTrailingZeros());
        assertEquals(new BigDecimal("100.00"), combo.get("comps"),
                "effective comps include the Arrachera on the combo child");
        assertEquals(Boolean.TRUE, combo.get("delivered"));
        assertEquals(3L, items.get(1).get("id")); // postre
        assertFalse(items.stream().anyMatch(i -> i.get("id").equals(2L)),
                "combo children are never offered for assignment");
    }

    @Test
    void percentDiscountAppliesToEachAccountAndSnapshotsPercent() {
        // Scenario 3: split bill with a global percentage discount. The captured %
        // is applied to each account individually and the last account absorbs the
        // residual so Σ accounts == order.total.
        Order order = buildOrder();
        order.setOrderDiscount(BigDecimal.ZERO);
        order.setOrderDiscountPercent(new BigDecimal("10.00"));
        order.setOrderDiscount(order.resolveDiscountAmountForPercent(new BigDecimal("10.00")));
        order.recalculateAmounts();
        // gross = items 332 + envío 25 = 357 → 10% = 35.70 → total 321.30
        assertEquals(new BigDecimal("35.70"), order.getOrderDiscount().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("321.30"), order.getTotal().setScale(2, RoundingMode.HALF_UP));

        SplitAccountDTO p1 = new SplitAccountDTO();
        p1.setIndex(1);
        p1.setPaymentMethod("CREDIT_CARD");
        p1.setItems(List.of(item(1L, "1"), item(2L, "1"), item(3L, "1")));
        SplitAccountDTO p2 = new SplitAccountDTO();
        p2.setIndex(2);
        p2.setPaymentMethod("DEBIT_CARD");
        p2.setItems(List.of(item(1L, "1")));

        List<Payment> payments = service.createSplitPayments(
                order, SplitMode.ITEMS, List.of(p1, p2), null, "tester", true, "https://x");

        BigDecimal sumTotals = payments.stream().map(Payment::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumDiscount = payments.stream().map(Payment::getOrderDiscount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(order.getTotal().setScale(2, RoundingMode.HALF_UP), sumTotals.setScale(2, RoundingMode.HALF_UP),
                "Σ cuentas == total de la orden");
        assertEquals(new BigDecimal("35.70"), sumDiscount.setScale(2, RoundingMode.HALF_UP));
        payments.forEach(p -> assertEquals("10.00",
                p.getOrderDiscountPercent().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                "each account snapshots the captured percentage"));
        assertTrue(payments.get(0).getTotal().compareTo(payments.get(1).getTotal()) > 0);
    }

    @Test
    void departureWithPercentLocksAndAppliesToEachCollection() {
        // Scenario 2: someone leaves with a discount → percentage only, locked from
        // the first collection; the same % applies to the remaining settlement.
        Order order = buildDineInOpenOrder();
        order.setOrderDiscountPercent(new BigDecimal("10.00"));
        order.setOrderDiscountLocked(true);
        order.recalculateAmounts();
        // gross 330 → 10% = 33 → total 297
        assertEquals(new BigDecimal("33.00"), order.getOrderDiscount().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("297.00"), order.getTotal().setScale(2, RoundingMode.HALF_UP));

        SplitAccountDTO leaver = new SplitAccountDTO();
        leaver.setIndex(1);
        leaver.setPersonLabel("Persona que se va");
        leaver.setPaymentMethod("CREDIT_CARD");
        leaver.setItems(List.of(item(1L, "1"), item(2L, "1")));

        List<Payment> departure = service.collectDepartingGuests(
                order, List.of(leaver), null, "tester", true, "https://x");
        // 180 gross − 10% = 162
        assertEquals(new BigDecimal("162.00"), departure.get(0).getTotal().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("18.00"), departure.get(0).getOrderDiscount().setScale(2, RoundingMode.HALF_UP));
        assertEquals("10.00", departure.get(0).getOrderDiscountPercent().setScale(2, RoundingMode.HALF_UP).toPlainString());
        assertEquals(new BigDecimal("162.00"), order.getCollectedAmount().setScale(2, RoundingMode.HALF_UP));
        // remaining gross 150 − 10% = 135
        assertEquals(new BigDecimal("135.00"), order.getRemainingTotal().setScale(2, RoundingMode.HALF_UP));

        // The rest is delivered and settled with the SAME fixed percentage.
        order.setStatus(OrderStatus.DELIVERED);
        order.getOrderDetails().get(2).setItemStatus(OrderStatus.DELIVERED);
        SplitAccountDTO p1 = new SplitAccountDTO();
        p1.setIndex(1);
        p1.setPaymentMethod("CREDIT_CARD");
        p1.setItems(List.of(item(1L, "1")));
        SplitAccountDTO p2 = new SplitAccountDTO();
        p2.setIndex(2);
        p2.setPaymentMethod("DEBIT_CARD");
        p2.setItems(List.of(item(3L, "1")));

        List<Payment> settlement = service.createSplitPayments(
                order, SplitMode.ITEMS, List.of(p1, p2), null, "tester", true, "https://x");
        BigDecimal sumTotals = settlement.stream().map(Payment::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("135.00"), sumTotals.setScale(2, RoundingMode.HALF_UP),
                "the fixed 10% applies per item to the remaining units");
        assertEquals(new BigDecimal("0.00"), order.getRemainingTotal().setScale(2, RoundingMode.HALF_UP));
        settlement.forEach(p -> assertEquals("10.00",
                p.getOrderDiscountPercent().setScale(2, RoundingMode.HALF_UP).toPlainString()));
    }

    private SplitItemDTO item(long detailId, String qty) {
        SplitItemDTO it = new SplitItemDTO();
        it.setOrderDetailId(detailId);
        it.setQuantity(new BigDecimal(qty));
        return it;
    }
}