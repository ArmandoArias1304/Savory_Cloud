package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.domain.repository.PaymentRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.presentation.dto.SplitAccountDTO;
import com.aatechsolutions.elgransazon.presentation.dto.SplitItemDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Orchestrates the split-bill payment flow.
 *
 * A DELIVERED order is divided into N per-person accounts (Payment rows).
 * Each account carries its own items (PaymentDetail rows), totals, tip,
 * payment method, collector and autofactura key/QR. Items are assigned to
 * people in WHOLE units (an order line of qty 4 means 4 assignable units);
 * per-product quantities are never divided — a product cannot be split for
 * SAT-compliant tickets. The order itself only transitions to PAID once
 * every account has been paid (the caller invokes {@code changeStatus(PAID)}
 * afterwards), which frees the table and updates monthly employee stats
 * exactly once.
 *
 * Money invariants guaranteed here:
 *   Σ account.total       == order.total          (last account absorbs residual)
 *   Σ account.total       == order.total − already-collected units  when the
 *                           order already has partial (departing-guest) charges
 *   Σ account.deliveryCost== order.deliveryCost   (pro-rata, residual on last)
 *   Σ account.orderDiscount == order.orderDiscount(pro-rata, residual on last)
 *   Σ account.tip         == order.tip            (set on the order by the caller)
 *
 * Account folios: ORD-YYYYMMDD-NNN-XX (parent order number + 2-digit account).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SplitPaymentService {

    private static final int QTY_SCALE = 4;
    private static final BigDecimal EPSILON = new BigDecimal("0.0001");
    private static final int MAX_ACCOUNTS = 10;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final FacturamaService facturamaService;

    /**
     * Create the per-person Payment rows for a split bill and attach them to the order.
     *
     * @param order        DELIVERED order being paid (its total/discount/delivery must be final)
     * @param mode         ITEMS (per-person assignment); EQUAL was removed because
     *                     a product cannot be divided (SAT)
     * @param accounts     per-person data (method, tip, items)
     * @param paidBy       employee collecting the payments
     * @param username     audit user
     * @param zeroCashTips force tip = 0 for CASH accounts (restaurant rule; not for delivery)
     * @param baseUrl      request base URL for autofactura self-invoice links
     * @return the created payments (with IDs), ordered by account number
     */
    @Transactional
    public List<Payment> createSplitPayments(Order order, SplitMode mode,
                                             List<SplitAccountDTO> accounts,
                                             Employee paidBy, String username,
                                             boolean zeroCashTips, String baseUrl) {
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException("Solo se pueden pagar órdenes con estado ENTREGADO. Estado actual: " + order.getStatus().getDisplayName());
        }
        // Only charge items already delivered: if new items were added later and are
        // still PENDING/IN_PREPARATION/READY, the order must wait until they are ENTREGADO.
        if (!order.isReadyToCharge()) {
            throw new IllegalStateException("Aún hay ítems sin entregar en este pedido. Solo se cobran los ítems con estado ENTREGADO.");
        }
        if (accounts == null || accounts.size() < 1 || accounts.size() > MAX_ACCOUNTS) {
            throw new IllegalArgumentException("Debe indicar entre 1 y " + MAX_ACCOUNTS + " persona(s)");
        }
        if (mode != SplitMode.ITEMS) {
            throw new IllegalArgumentException(
                    "El modo 'Partes iguales' ya no está disponible: un producto no puede dividirse. "
                            + "Use la división por persona (asignar ítems completos) o realice un solo pago.");
        }

        Company company = CompanyContext.requireCurrentCompany();
        int n = accounts.size();
        boolean isDelivery = order.getOrderType() == OrderType.DELIVERY;

        // ---------- 1. Normalize accounts ----------
        for (int i = 0; i < n; i++) {
            SplitAccountDTO acc = accounts.get(i);
            acc.setIndex(i + 1);
            if (acc.getTip() == null) {
                acc.setTip(BigDecimal.ZERO);
            }
            validateTip(acc);
            if (acc.getPaymentMethod() == null || acc.getPaymentMethod().isBlank()) {
                // Delivery flow does not choose a method per person: use the order's.
                if (order.getPaymentMethod() != null) {
                    acc.setPaymentMethod(order.getPaymentMethod().name());
                } else {
                    throw new IllegalArgumentException(acc.getDisplayLabel() + ": debe seleccionar un método de pago");
                }
            }
            if (zeroCashTips && PaymentMethodType.valueOf(acc.getPaymentMethod()) == PaymentMethodType.CASH) {
                acc.setTip(BigDecimal.ZERO);
            }
        }

        // ---------- 2. Prepare the splittable lines (combo children have $0 price → skipped;
        // cancelled lines are never charged) ----------
        List<OrderDetail> lines = order.getOrderDetails().stream()
                .filter(d -> !d.isComboChild())
                .filter(d -> d.getItemStatus() != OrderStatus.CANCELLED)
                .toList();
        if (lines.isEmpty()) {
            throw new IllegalStateException("La orden no tiene ítems que dividir");
        }

        // ---------- 3. Quantity matrix [account][line] ----------
        // Whole units per person only: a product is never divided (e.g. a line
        // of qty 4 offers units 0..4 to assign).
        BigDecimal[][] qty = new BigDecimal[n][lines.size()];
        if (mode == SplitMode.ITEMS) {
            // map orderDetailId → list index
            Map<Long, Integer> lineIndex = new HashMap<>();
            for (int li = 0; li < lines.size(); li++) {
                lineIndex.put(lines.get(li).getIdOrderDetail(), li);
            }
            BigDecimal[] assignedSum = new BigDecimal[lines.size()];
            Arrays.fill(assignedSum, BigDecimal.ZERO);
            for (int ai = 0; ai < n; ai++) {
                SplitAccountDTO acc = accounts.get(ai);
                if (acc.getItems() == null || acc.getItems().isEmpty()) {
                    throw new IllegalArgumentException(acc.getDisplayLabel() + " no tiene ítems asignados");
                }
                for (SplitItemDTO item : acc.getItems()) {
                    Integer li = lineIndex.get(item.getOrderDetailId());
                    if (li == null) {
                        throw new IllegalArgumentException(acc.getDisplayLabel() + ": ítem no válido en la asignación");
                    }
                    if (qty[ai][li] != null) {
                        throw new IllegalArgumentException(acc.getDisplayLabel()
                                + ": ítem duplicado en la asignación ('" + lines.get(li).getDisplayName() + "')");
                    }
                    BigDecimal q = item.getQuantity();
                    OrderDetail line = lines.get(li);
                    BigDecimal totalQty = line.getRemainingQuantity();
                    if (q == null || q.compareTo(BigDecimal.ZERO) <= 0
                            || q.compareTo(new BigDecimal("99999.9999")) > 0) {
                        throw new IllegalArgumentException(acc.getDisplayLabel()
                                + ": cantidad inválida para '" + line.getDisplayName() + "'");
                    }
                    // Per-person assignment is in whole units (each order line holds
                    // integer quantities, e.g. 4 Cokes in one line → units 0..4).
                    if (q.stripTrailingZeros().scale() > 0) {
                        throw new IllegalArgumentException(acc.getDisplayLabel()
                                + ": la cantidad de '" + line.getDisplayName()
                                + "' debe ser en unidades enteras (asignada: " + q.toPlainString() + ")");
                    }
                    if (q.compareTo(totalQty) > 0) {
                        throw new IllegalArgumentException(acc.getDisplayLabel()
                                + ": no puede asignar más de " + totalQty.toPlainString()
                                + " unidad(es) de '" + line.getDisplayName() + "' (asignadas: "
                                + q.toPlainString() + ")");
                    }
                    qty[ai][li] = q.setScale(QTY_SCALE, RoundingMode.HALF_UP);
                    assignedSum[li] = assignedSum[li].add(qty[ai][li]);
                }
                boolean hasItems = Arrays.stream(qty[ai]).anyMatch(q -> q != null && q.compareTo(BigDecimal.ZERO) > 0);
                if (!hasItems) {
                    throw new IllegalArgumentException(acc.getDisplayLabel() + " no tiene ítems asignados");
                }
            }
            // Every line must be fully assigned (all remaining units)
            for (int li = 0; li < lines.size(); li++) {
                OrderDetail d = lines.get(li);
                BigDecimal totalQty = d.getRemainingQuantity();
                BigDecimal sum = assignedSum[li];
                if (sum == null || sum.subtract(totalQty).abs().compareTo(EPSILON) > 0) {
                    throw new IllegalArgumentException("La cantidad asignada de '" + d.getDisplayName()
                            + "' no coincide con la cantidad de la orden (asignada: "
                            + (sum != null ? sum.toPlainString() : "0") + ", orden: " + totalQty.toPlainString() + ")");
                }
            }
        }

        // ---------- 4. Per-account raw line totals (incl. complements), 2 decimals ----------
        // [account][line]
        BigDecimal[][] lineTotal = new BigDecimal[n][lines.size()];
        // [account] raw gross (items + complements), 2 decimals
        BigDecimal[] accountGross = new BigDecimal[n];
        Arrays.fill(accountGross, BigDecimal.ZERO);

        for (int li = 0; li < lines.size(); li++) {
            OrderDetail d = lines.get(li);            BigDecimal detailQty = BigDecimal.valueOf(d.getQuantity());
            BigDecimal effectiveUnitPrice = (d.getPromotionAppliedPrice() != null)
                    ? d.getPromotionAppliedPrice()
                    : d.getUnitPrice();
            // For combo parents this includes the complements of the $0-priced
            // children (the paid complements of a combo live on its children).
            BigDecimal compTotal = order.getEffectiveComplementsTotal(d);

            // Last account WITH quantity of this line absorbs the complement residual
            int lastWithQty = -1;
            for (int ai = n - 1; ai >= 0; ai--) {
                if (qty[ai][li] != null && qty[ai][li].compareTo(BigDecimal.ZERO) > 0) {
                    lastWithQty = ai;
                    break;
                }
            }
            BigDecimal assignedComp = BigDecimal.ZERO;
            for (int ai = 0; ai < n; ai++) {
                BigDecimal q = qty[ai][li] != null ? qty[ai][li] : BigDecimal.ZERO;
                BigDecimal itemTotal = effectiveUnitPrice.multiply(q).setScale(2, RoundingMode.HALF_UP);
                BigDecimal compShare;
                if (compTotal.compareTo(BigDecimal.ZERO) == 0 || q.compareTo(BigDecimal.ZERO) == 0) {
                    compShare = BigDecimal.ZERO;
                } else if (ai == lastWithQty) {
                    compShare = compTotal.subtract(assignedComp).max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP);
                } else {
                    compShare = compTotal.multiply(q).divide(detailQty, 2, RoundingMode.HALF_UP);
                    assignedComp = assignedComp.add(compShare);
                }
                lineTotal[ai][li] = itemTotal.add(compShare);
                accountGross[ai] = accountGross[ai].add(lineTotal[ai][li]);
            }
        }

        // ---------- 5. Distribute delivery cost and order discount (pro-rata, residual on last) ----------
        BigDecimal orderTotal = order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO;
        BigDecimal deliveryCost = (isDelivery && order.getDeliveryCost() != null)
                ? order.getDeliveryCost() : BigDecimal.ZERO;
        BigDecimal orderDiscount = order.getOrderDiscount() != null ? order.getOrderDiscount() : BigDecimal.ZERO;
        // Captured percentage (scenario 2/3): applied to each account's own gross
        // instead of prorating the stored amount, so every item is discounted
        // individually and the last account absorbs the rounding residual.
        boolean percentMode = order.hasOrderDiscountPercent();
        BigDecimal orderDiscountPercent = percentMode ? order.getOrderDiscountPercent() : null;
        BigDecimal discountFactor = order.getOrderDiscountFactor();

        BigDecimal sumGross = Arrays.stream(accountGross).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal[] deliveryShare = new BigDecimal[n];
        BigDecimal[] discountShare = new BigDecimal[n];
        Arrays.fill(deliveryShare, BigDecimal.ZERO);
        Arrays.fill(discountShare, BigDecimal.ZERO);

        if (sumGross.compareTo(BigDecimal.ZERO) > 0) {
            // Delivery cost share
            BigDecimal assignedDelivery = BigDecimal.ZERO;
            for (int ai = 0; ai < n; ai++) {
                if (deliveryCost.compareTo(BigDecimal.ZERO) == 0) {
                    deliveryShare[ai] = BigDecimal.ZERO;
                } else if (ai == n - 1) {
                    deliveryShare[ai] = deliveryCost.subtract(assignedDelivery).max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP);
                } else {
                    deliveryShare[ai] = deliveryCost.multiply(accountGross[ai])
                            .divide(sumGross, 2, RoundingMode.HALF_UP);
                    assignedDelivery = assignedDelivery.add(deliveryShare[ai]);
                }
            }
            // Discount share (pro-rata over gross + delivery)
            BigDecimal sumBasis = sumGross.add(deliveryCost);
            BigDecimal assignedDiscount = BigDecimal.ZERO;
            for (int ai = 0; ai < n; ai++) {
                if (orderDiscount.compareTo(BigDecimal.ZERO) == 0) {
                    discountShare[ai] = BigDecimal.ZERO;
                } else if (ai == n - 1) {
                    discountShare[ai] = orderDiscount.subtract(assignedDiscount).max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP);
                } else {
                    BigDecimal basis = accountGross[ai].add(deliveryShare[ai]);
                    discountShare[ai] = orderDiscount.multiply(basis)
                            .divide(sumBasis, 2, RoundingMode.HALF_UP);
                    assignedDiscount = assignedDiscount.add(discountShare[ai]);
                }
            }
        } else if (orderTotal.compareTo(BigDecimal.ZERO) > 0) {
            // Degenerate: all items free but order total > 0 (e.g. only delivery cost).
            // Fall back to an equal split of delivery cost.
            BigDecimal assignedDelivery = BigDecimal.ZERO;
            for (int ai = 0; ai < n; ai++) {
                if (ai == n - 1) {
                    deliveryShare[ai] = deliveryCost.subtract(assignedDelivery).max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP);
                } else {
                    deliveryShare[ai] = deliveryCost.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
                    assignedDelivery = assignedDelivery.add(deliveryShare[ai]);
                }
            }
        }

        // Amount already collected (departing guests / earlier accounts). Each
        // Payment row carries the discount that applied when it was charged, so
        // summing their totals is exact even when the discount was set midway
        // through the bill. The final settlement anchors its residual on what is
        // STILL owed, so already-collected units are never charged again.
        BigDecimal collectedAmount = order.getCollectedAmount();
        BigDecimal remainingAnchor = orderTotal.subtract(collectedAmount).max(BigDecimal.ZERO);

        // ---------- 6. Final per-account totals (last account absorbs the global residual) ----------
        BigDecimal[] accountTotal = new BigDecimal[n];
        BigDecimal accumulated = BigDecimal.ZERO;
        if (percentMode) {
            // Percentage mode: discount each account's own gross (items + envío).
            for (int ai = 0; ai < n - 1; ai++) {
                BigDecimal grossWithDelivery = accountGross[ai].add(deliveryShare[ai]);
                accountTotal[ai] = grossWithDelivery.multiply(discountFactor)
                        .setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
                discountShare[ai] = grossWithDelivery.subtract(accountTotal[ai])
                        .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                accumulated = accumulated.add(accountTotal[ai]);
            }
            accountTotal[n - 1] = remainingAnchor.subtract(accumulated).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal lastGross = accountGross[n - 1].add(deliveryShare[n - 1]);
            discountShare[n - 1] = lastGross.subtract(accountTotal[n - 1])
                    .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        } else {
            for (int ai = 0; ai < n - 1; ai++) {
                accountTotal[ai] = accountGross[ai].add(deliveryShare[ai]).subtract(discountShare[ai])
                        .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                accumulated = accumulated.add(accountTotal[ai]);
            }
            accountTotal[n - 1] = remainingAnchor.subtract(accumulated).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal taxMultiplier = (order.getTaxRate() != null && order.getTaxRate().compareTo(BigDecimal.ZERO) > 0)
                ? BigDecimal.ONE.add(order.getTaxRate().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP))
                : BigDecimal.ONE;

        // ---------- 7. Build and persist the Payment rows ----------
        boolean autofacturaReady = facturamaService.getConfigForCurrentCompany()
                .map(FacturamaConfig::isReady).orElse(false);
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        String orderNumber = order.getOrderNumber();
        // Account folios continue from the payments this order already has
        // (departing guests, earlier splits), so a settlement batch never
        // regenerates a folio that already exists (uk_payment_folio_company).
        int baseAccount = (order.getPayments() != null ? order.getPayments().size() : 0);

        List<Payment> payments = new ArrayList<>();
        for (int ai = 0; ai < n; ai++) {
            SplitAccountDTO acc = accounts.get(ai);
            int accountNumber = baseAccount + ai + 1;
            PaymentMethodType method;
            try {
                method = PaymentMethodType.valueOf(acc.getPaymentMethod());
            } catch (Exception e) {
                throw new IllegalArgumentException(acc.getDisplayLabel() + ": método de pago no válido");
            }

            Payment payment = Payment.builder()
                    .company(company)
                    .order(order)
                    .accountNumber(accountNumber)
                    .paymentFolio(orderNumber + "-" + String.format("%02d", accountNumber))
                    .splitMode(mode)
                    .splitCount(n)
                    .personLabel(acc.getDisplayLabel())
                    .subtotal(accountTotal[ai].divide(taxMultiplier, 2, RoundingMode.HALF_UP))
                    .taxRate(order.getTaxRate())
                    .taxAmount(accountTotal[ai].subtract(
                            accountTotal[ai].divide(taxMultiplier, 2, RoundingMode.HALF_UP)))
                    .orderDiscount(discountShare[ai])
                    .orderDiscountPercent(orderDiscountPercent)
                    .deliveryCost(deliveryShare[ai])
                    .total(accountTotal[ai])
                    .tip(acc.getTip())
                    .paymentMethod(method)
                    .paidBy(paidBy)
                    .paidAt(nowUtc)
                    .createdBy(username)
                    .updatedBy(username)
                    .build();

            // PaymentDetail rows
            for (int li = 0; li < lines.size(); li++) {
                BigDecimal q = qty[ai][li];
                if (q == null || q.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                OrderDetail d = lines.get(li);
                BigDecimal effectiveUnitPrice = (d.getPromotionAppliedPrice() != null)
                        ? d.getPromotionAppliedPrice()
                        : d.getUnitPrice();
                PaymentDetail pd = PaymentDetail.builder()
                        .payment(payment)
                        .orderDetail(d)
                        .itemName(d.getDisplayName())
                        .quantity(q)
                        .unitPrice(effectiveUnitPrice)
                        .subtotal(effectiveUnitPrice.multiply(q).setScale(2, RoundingMode.HALF_UP))
                        .complementSubtotal(lineTotal[ai][li]
                                .subtract(effectiveUnitPrice.multiply(q).setScale(2, RoundingMode.HALF_UP))
                                .max(BigDecimal.ZERO))
                        .total(lineTotal[ai][li])
                        .complementDetails(buildComplementDetails(order, d))
                        .comments(d.getDisplayComments())
                        .comboGroupId(d.getComboGroupId())
                        .isComboParentSnapshot(d.getIsComboParentSnapshot())
                        .build();
                payment.addPaymentDetail(pd);
                // Track units charged so the open-order flow never charges them twice
                d.addPaidQuantity(q);
            }

            // Autofactura key + self-invoice URL per account (only when billing is configured)
            if (autofacturaReady && accountTotal[ai].compareTo(BigDecimal.ZERO) > 0) {
                try {
                    String autofacturaKey = UUID.randomUUID().toString();
                    String selfInvoiceUrl = baseUrl + "/autofactura/" + autofacturaKey;
                    payment.setAutofacturaKey(autofacturaKey);
                    payment.setSelfInvoiceUrl(selfInvoiceUrl);
                } catch (Exception ex) {
                    log.warn("Autofactura key generation failed (non-blocking): {}", ex.getMessage());
                }
            }

            payments.add(payment);
            order.getPayments().add(payment);
        }

        // Persist the payments directly (Payment → PaymentDetail cascade) so the
        // generated IDs (IDENTITY) are assigned to the SAME instances before we
        // return them. Persisting via cascade from the order would merge a
        // detached order into managed copies, leaving the returned payments with
        // null IDs (breaking the caller's print-ticket flash attributes).
        // Keep the order-level tip in sync with ALL accounts of this order
        // (including earlier departing-guest/split charges), so tip views and
        // reports reflect every tip collected, not just this batch.
        order.setTip(totalTips(order));
        paymentRepository.saveAll(payments);
        paymentRepository.flush();
        orderRepository.save(order);
        log.info("Split payment created for order {}: {} accounts, total ${}, paid by {}",
                orderNumber, n,
                Arrays.stream(accountTotal).reduce(BigDecimal.ZERO, BigDecimal::add), username);

        return payments;
    }

    /**
     * Charge a departing guest on an OPEN order (partial collection,
     * "pay as they leave").
     *
     * The order may still be PENDING / IN_PREPARATION / READY / DELIVERED: only
     * the lines already ENTREGADO (per-item status DELIVERED) with units that
     * have NOT been charged yet can be assigned to the leaving person(s), in
     * whole units. Charged units are marked as paid (OrderDetail.paidQuantity)
     * but the line STAYS on the open order so the rest of the party keeps
     * ordering; the pending items are untouched. When the last person settles
     * and nothing remains, the caller transitions the order to PAID.
     *
     * No delivery cost / order discount proration happens here: partial charges
     * are dine-in/takeout per-item collections.
     *
     * @return the created payments (with IDs), one per departing guest
     */
    @Transactional
    public List<Payment> collectDepartingGuests(Order order, List<SplitAccountDTO> accounts,
                                                Employee paidBy, String username,
                                                boolean zeroCashTips, String baseUrl) {
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("El pedido ya fue pagado o cancelado");
        }
        if (order.getOrderType() == OrderType.DELIVERY) {
            throw new IllegalStateException("El cobro por persona que se va solo aplica en pedidos de mesa o para llevar");
        }
        if (!order.hasChargeableDeliveredItems()) {
            throw new IllegalStateException("No hay ítems ENTREGADOS pendientes de cobro en este pedido");
        }
        if (accounts == null || accounts.size() < 1 || accounts.size() > MAX_ACCOUNTS) {
            throw new IllegalArgumentException("Debe indicar entre 1 y " + MAX_ACCOUNTS + " persona(s)");
        }

        Company company = CompanyContext.requireCurrentCompany();
        int n = accounts.size();

        // ---------- 1. Normalize accounts ----------
        for (int i = 0; i < n; i++) {
            SplitAccountDTO acc = accounts.get(i);
            acc.setIndex(i + 1);
            if (acc.getTip() == null) {
                acc.setTip(BigDecimal.ZERO);
            }
            validateTip(acc);
            if (acc.getPaymentMethod() == null || acc.getPaymentMethod().isBlank()) {
                if (order.getPaymentMethod() != null) {
                    acc.setPaymentMethod(order.getPaymentMethod().name());
                } else {
                    throw new IllegalArgumentException(acc.getDisplayLabel() + ": debe seleccionar un método de pago");
                }
            }
            PaymentMethodType method;
            try {
                method = PaymentMethodType.valueOf(acc.getPaymentMethod());
            } catch (Exception e) {
                throw new IllegalArgumentException(acc.getDisplayLabel() + ": método de pago no válido");
            }
            if (zeroCashTips && method == PaymentMethodType.CASH) {
                acc.setTip(BigDecimal.ZERO);
            }
        }

        // ---------- 2. Chargeable lines: ENTREGADO with units not charged yet ----------
        List<OrderDetail> lines = order.getOrderDetails().stream()
                .filter(d -> !d.isComboChild())
                .filter(d -> d.getItemStatus() == OrderStatus.DELIVERED)
                .filter(OrderDetail::hasRemainingQuantity)
                .toList();
        if (lines.isEmpty()) {
            throw new IllegalStateException("No hay ítems ENTREGADOS pendientes de cobro en este pedido");
        }

        // ---------- 3. Whole-unit assignment (subset allowed) ----------
        Map<Long, Integer> lineIndex = new HashMap<>();
        for (int li = 0; li < lines.size(); li++) {
            lineIndex.put(lines.get(li).getIdOrderDetail(), li);
        }
        BigDecimal[][] qty = new BigDecimal[n][lines.size()];
        // Units already claimed across ALL leaving guests in this submission, so
        // two guests can never both be charged for the same last unit.
        BigDecimal[] claimed = new BigDecimal[lines.size()];
        Arrays.fill(claimed, BigDecimal.ZERO);
        for (int ai = 0; ai < n; ai++) {
            SplitAccountDTO acc = accounts.get(ai);
            if (acc.getItems() == null || acc.getItems().isEmpty()) {
                throw new IllegalArgumentException(acc.getDisplayLabel() + " no tiene ítems ENTREGADOS asignados");
            }
            boolean hasItems = false;
            for (SplitItemDTO item : acc.getItems()) {
                Integer li = lineIndex.get(item.getOrderDetailId());
                if (li == null) {
                    throw new IllegalArgumentException(acc.getDisplayLabel() + ": ítem no válido en la asignación");
                }
                OrderDetail line = lines.get(li);
                if (qty[ai][li] != null) {
                    throw new IllegalArgumentException(acc.getDisplayLabel()
                            + ": ítem duplicado en la asignación ('" + line.getDisplayName() + "')");
                }
                BigDecimal q = item.getQuantity();
                BigDecimal remaining = line.getRemainingQuantity();
                if (q == null || q.compareTo(BigDecimal.ZERO) <= 0
                        || q.compareTo(new BigDecimal("99999.9999")) > 0) {
                    throw new IllegalArgumentException(acc.getDisplayLabel()
                            + ": cantidad inválida para '" + line.getDisplayName() + "'");
                }
                if (q.stripTrailingZeros().scale() > 0) {
                    throw new IllegalArgumentException(acc.getDisplayLabel()
                            + ": la cantidad de '" + line.getDisplayName()
                            + "' debe ser en unidades enteras (asignada: " + q.toPlainString() + ")");
                }
                BigDecimal available = remaining.subtract(claimed[li]).max(BigDecimal.ZERO);
                if (q.compareTo(available) > 0) {
                    throw new IllegalArgumentException(acc.getDisplayLabel()
                            + ": no puede cobrar más de " + available.toPlainString()
                            + " unidad(es) pendiente(s) de '" + line.getDisplayName()
                            + "' (asignadas: " + q.toPlainString() + ")");
                }
                qty[ai][li] = q.setScale(QTY_SCALE, RoundingMode.HALF_UP);
                claimed[li] = claimed[li].add(q);
                hasItems = true;
            }
            if (!hasItems) {
                throw new IllegalArgumentException(acc.getDisplayLabel() + " no tiene ítems ENTREGADOS asignados");
            }
        }

        // ---------- 4. Per-account totals (items + pro-rated complement share) ----------
        BigDecimal[] accountTotal = new BigDecimal[n];
        Arrays.fill(accountTotal, BigDecimal.ZERO);
        BigDecimal[][] lineTotal = new BigDecimal[n][lines.size()];
        for (int li = 0; li < lines.size(); li++) {            OrderDetail d = lines.get(li);
            BigDecimal effectiveUnitPrice = (d.getPromotionAppliedPrice() != null)
                    ? d.getPromotionAppliedPrice()
                    : d.getUnitPrice();
            // For combo parents this includes the complements of the $0-priced
            // children (the paid complements of a combo live on its children).
            BigDecimal compTotal = order.getEffectiveComplementsTotal(d);
            BigDecimal detailQty = BigDecimal.valueOf(d.getQuantity());
            for (int ai = 0; ai < n; ai++) {
                BigDecimal q = qty[ai][li];
                if (q == null || q.compareTo(BigDecimal.ZERO) == 0) {
                    lineTotal[ai][li] = BigDecimal.ZERO;
                    continue;
                }
                BigDecimal itemTotal = effectiveUnitPrice.multiply(q).setScale(2, RoundingMode.HALF_UP);
                BigDecimal compShare = (compTotal.compareTo(BigDecimal.ZERO) == 0)
                        ? BigDecimal.ZERO
                        : compTotal.multiply(q).divide(detailQty, 2, RoundingMode.HALF_UP);
                lineTotal[ai][li] = itemTotal.add(compShare);
                accountTotal[ai] = accountTotal[ai].add(lineTotal[ai][li]);
            }
        }

        // Percentage discount (locked from the first departing-guest collection):
        // applied to each account's own items individually; each account records
        // its share so its ticket shows the global discount.
        boolean percentMode = order.hasOrderDiscountPercent();
        BigDecimal discountFactor = order.getOrderDiscountFactor();
        BigDecimal orderDiscountPercent = percentMode ? order.getOrderDiscountPercent() : null;
        BigDecimal[] accountDiscount = new BigDecimal[n];
        Arrays.fill(accountDiscount, BigDecimal.ZERO);
        if (percentMode) {
            for (int ai = 0; ai < n; ai++) {
                BigDecimal gross = accountTotal[ai];
                BigDecimal discounted = gross.multiply(discountFactor)
                        .setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
                accountDiscount[ai] = gross.subtract(discounted)
                        .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                accountTotal[ai] = discounted;
            }
        }

        BigDecimal taxMultiplier = (order.getTaxRate() != null && order.getTaxRate().compareTo(BigDecimal.ZERO) > 0)
                ? BigDecimal.ONE.add(order.getTaxRate().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP))
                : BigDecimal.ONE;
        boolean autofacturaReady = facturamaService.getConfigForCurrentCompany()
                .map(FacturamaConfig::isReady).orElse(false);
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        String orderNumber = order.getOrderNumber();
        int baseAccount = (order.getPayments() != null ? order.getPayments().size() : 0);

        // ---------- 5. Build and persist Payment rows (order stays open) ----------
        List<Payment> payments = new ArrayList<>();
        for (int ai = 0; ai < n; ai++) {
            SplitAccountDTO acc = accounts.get(ai);
            int accountNumber = baseAccount + ai + 1;
            PaymentMethodType method = PaymentMethodType.valueOf(acc.getPaymentMethod());

            Payment payment = Payment.builder()
                    .company(company)
                    .order(order)
                    .accountNumber(accountNumber)
                    .paymentFolio(orderNumber + "-" + String.format("%02d", accountNumber))
                    .splitMode(SplitMode.ITEMS)
                    .splitCount(n)
                    .personLabel(acc.getDisplayLabel())
                    .subtotal(accountTotal[ai].divide(taxMultiplier, 2, RoundingMode.HALF_UP))
                    .taxRate(order.getTaxRate())
                    .taxAmount(accountTotal[ai].subtract(
                            accountTotal[ai].divide(taxMultiplier, 2, RoundingMode.HALF_UP)))
                    .orderDiscount(accountDiscount[ai])
                    .orderDiscountPercent(orderDiscountPercent)
                    .deliveryCost(BigDecimal.ZERO)
                    .total(accountTotal[ai])
                    .tip(acc.getTip())
                    .paymentMethod(method)
                    .paidBy(paidBy)
                    .paidAt(nowUtc)
                    .createdBy(username)
                    .updatedBy(username)
                    .build();

            for (int li = 0; li < lines.size(); li++) {
                BigDecimal q = qty[ai][li];
                if (q == null || q.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                OrderDetail d = lines.get(li);
                BigDecimal effectiveUnitPrice = (d.getPromotionAppliedPrice() != null)
                        ? d.getPromotionAppliedPrice()
                        : d.getUnitPrice();
                PaymentDetail det = PaymentDetail.builder()
                        .payment(payment)
                        .orderDetail(d)
                        .itemName(d.getDisplayName())
                        .quantity(q)
                        .unitPrice(effectiveUnitPrice)
                        .subtotal(effectiveUnitPrice.multiply(q).setScale(2, RoundingMode.HALF_UP))
                        .complementSubtotal(lineTotal[ai][li]
                                .subtract(effectiveUnitPrice.multiply(q).setScale(2, RoundingMode.HALF_UP))
                                .max(BigDecimal.ZERO))
                        .total(lineTotal[ai][li])
                        .complementDetails(buildComplementDetails(order, d))
                        .comments(d.getDisplayComments())
                        .comboGroupId(d.getComboGroupId())
                        .isComboParentSnapshot(d.getIsComboParentSnapshot())
                        .build();
                payment.addPaymentDetail(det);
                // Mark the units as charged on the open order
                d.addPaidQuantity(q);
            }

            if (autofacturaReady && accountTotal[ai].compareTo(BigDecimal.ZERO) > 0) {
                try {
                    String autofacturaKey = UUID.randomUUID().toString();
                    payment.setAutofacturaKey(autofacturaKey);
                    payment.setSelfInvoiceUrl(baseUrl + "/autofactura/" + autofacturaKey);
                } catch (Exception ex) {
                    log.warn("Autofactura key generation failed (non-blocking): {}", ex.getMessage());
                }
            }

            payments.add(payment);
            order.getPayments().add(payment);
        }

        // Accumulate the tip on the order as guests pay, so tip views and
        // reports reflect the departing-guest charges too (the order stays
        // open, but the aggregate must survive until the final settlement).
        order.setTip(totalTips(order));
        paymentRepository.saveAll(payments);
        paymentRepository.flush();
        orderRepository.save(order);
        log.info("Departing-guest collection for order {}: {} account(s), total ${}, paid by {}",
                orderNumber, n,
                Arrays.stream(accountTotal).reduce(BigDecimal.ZERO, BigDecimal::add), username);

        return payments;
    }

    /**
     * Build a human-readable complement summary for a line, e.g. "Extra queso x2, Aderezo x1".
     * For a combo parent the paid complements live on the $0-priced children, so
     * their selections are listed too, tagged with the child name, e.g.
     * "Arrachera x1 (Pizza Atrevida), Salsa BBQ x2 (Pizza Atrevida)".
     */
    private String buildComplementDetails(Order order, OrderDetail d) {
        StringBuilder sb = new StringBuilder();
        if (d.getSelectedComplements() != null) {
            for (OrderDetailComplement odc : d.getSelectedComplements()) {
                appendComplement(sb, odc, null);
            }
        }
        if (d.isComboParent() && d.getComboGroupId() != null
                && order != null && order.getOrderDetails() != null) {
            String group = d.getComboGroupId();
            for (OrderDetail child : order.getOrderDetails()) {
                if (!child.isComboChild() || !group.equals(child.getComboGroupId())) {
                    continue;
                }
                if (child.getSelectedComplements() == null || child.getSelectedComplements().isEmpty()) {
                    continue;
                }
                for (OrderDetailComplement odc : child.getSelectedComplements()) {
                    appendComplement(sb, odc, child.getDisplayName());
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private void appendComplement(StringBuilder sb, OrderDetailComplement odc, String parentName) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(odc.getComplementName());
        if (odc.getQuantity() != null && odc.getQuantity() > 1) {
            sb.append(" x").append(odc.getQuantity());
        }
        if (parentName != null && !parentName.isBlank()) {
            sb.append(" (").append(parentName).append(")");
        }
    }

    /**
     * Sum of the tips of every Payment row already attached to the order
     * (previous charges included).
     */
    private BigDecimal totalTips(Order order) {
        return order.getPayments().stream()
                .map(p -> p.getTip() != null ? p.getTip() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateTip(SplitAccountDTO acc) {
        BigDecimal tip = acc.getTip();
        if (tip.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(acc.getDisplayLabel() + ": la propina no puede ser negativa");
        }
        if (tip.compareTo(new BigDecimal("999999.99")) > 0) {
            throw new IllegalArgumentException(acc.getDisplayLabel() + ": la propina no puede ser mayor a $999,999.99");
        }
        if (tip.scale() > 2) {
            throw new IllegalArgumentException(acc.getDisplayLabel() + ": la propina solo permite hasta 2 decimales");
        }
    }
}