package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.application.dto.LoyaltyOpenOrderView;
import com.aatechsolutions.elgransazon.application.dto.LoyaltyRedeemLine;
import com.aatechsolutions.elgransazon.application.dto.LoyaltyRedemptionResult;
import com.aatechsolutions.elgransazon.application.dto.LoyaltyRewardOrderView;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.CustomerRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComboItemRepository;
import com.aatechsolutions.elgransazon.domain.repository.LoyaltyRedemptionRepository;
import com.aatechsolutions.elgransazon.domain.repository.LoyaltyRewardRepository;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns catalog rewards into real order lines at $0.00.
 *
 * <p>Staff build a basket with one or more rewards. The service reads every
 * item and its price from the menu, validates stock through the normal order
 * pipeline (so comanda, kitchen/barista routing and inventory keep working),
 * locks the stamp account, deducts the whole basket once and then creates one
 * {@link LoyaltyRedemption} row per reward for the audit trail.</p>
 *
 * <p>Two destinations are supported:</p>
 * <ul>
 *   <li><b>Standalone order</b> (cashier, admin, manager): a new $0.00 takeout
 *       order that the cashier closes without collecting money.</li>
 *   <li><b>Open bill</b> (waiter at a table): the free lines are added to the
 *       table's current order, so the kitchen knows where they go and the
 *       normal payment flow closes the bill.</li>
 * </ul>
 *
 * <p>If anything fails, the transaction rolls back and the customer keeps their
 * stamps. Cancelling the order returns every line's stamps exactly once.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyRedemptionService {

    private final LoyaltyRewardRepository rewardRepository;
    private final LoyaltyRedemptionRepository redemptionRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final ItemMenuComboItemRepository comboItemRepository;
    private final LoyaltyAccountService accountService;
    private final LoyaltyCycleService cycleService;
    private final LoyaltyLedgerService ledgerService;
    private final FeatureAccessService featureAccessService;
    private final SystemConfigurationService systemConfigurationService;
    private final EmployeeService employeeService;
    private final DateTimeService dateTimeService;
    private final @Lazy OrderServiceImpl adminOrderService;

    /**
     * Backwards-compatible single-reward redemption. Kept for callers that only
     * redeem one reward; the staff screen uses {@link #redeemBasket}.
     */
    @Transactional
    public LoyaltyRedemptionResult redeem(Company company,
                                          Long customerId,
                                          Long rewardId,
                                          int rewardQuantity,
                                          String username) {
        return redeemBasket(company, customerId,
                List.of(new LoyaltyRedeemLine(rewardId, rewardQuantity)), null, username);
    }

    /**
     * Redeems a whole basket of rewards for one customer.
     *
     * @param company         current restaurant
     * @param customerId      registered customer who owns the stamp account
     * @param requestedLines  rewards and quantities selected on screen
     * @param attachToOrderId open order to attach the free lines to, or
     *                        {@code null} to create a standalone $0.00 order
     * @param username        employee performing the redemption
     */
    @Transactional
    public LoyaltyRedemptionResult redeemBasket(Company company,
                                                Long customerId,
                                                List<LoyaltyRedeemLine> requestedLines,
                                                Long attachToOrderId,
                                                String username) {
        featureAccessService.requireEnabled(company, FeatureKey.LOYALTY);
        if (!cycleService.processAndCheck(company)) {
            throw new IllegalStateException(
                    "El periodo de sellos venció. Un administrador debe configurar una nueva fecha "
                            + "o quitar el límite para iniciar un nuevo periodo.");
        }

        Map<Long, Integer> quantities = normalize(requestedLines);

        Customer customer = customerRepository.findByIdCustomerAndCompany(customerId, company)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
        if (!customer.isActive()) {
            throw new IllegalStateException("El cliente está desactivado");
        }

        // Validate every reward against the menu before touching the balance.
        List<RewardLine> lines = new ArrayList<>(quantities.size());
        int stampsSpent = 0;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            LoyaltyReward reward = rewardRepository.findByIdAndCompany(entry.getKey(), company)
                    .orElseThrow(() -> new IllegalArgumentException("Recompensa no encontrada"));
            if (!reward.isActive()) {
                throw new IllegalStateException(
                        "La recompensa '" + reward.getName() + "' está desactivada");
            }

            ItemMenu item = reward.getItemMenu();
            if (item == null) {
                throw new IllegalStateException(
                        "La recompensa '" + reward.getName() + "' no tiene un producto configurado");
            }
            if (!Boolean.TRUE.equals(item.getActive()) || Boolean.TRUE.equals(item.getInternalOnly())) {
                throw new IllegalStateException(
                        "El producto de la recompensa '" + reward.getName() + "' ya no está disponible");
            }
            if (Boolean.TRUE.equals(item.getIsCombo())) {
                // Combos are valid rewards: their children go to the kitchen exactly like
                // a normal combo order, with the whole group at $0.00.
                requireActiveComboChildren(item, reward.getName());
            }

            int quantity = entry.getValue();
            int lineStamps = Math.multiplyExact(reward.safeStampsCost(), quantity);
            int units = Math.multiplyExact(reward.safeFreeQuantity(), quantity);

            lines.add(new RewardLine(reward, item, quantity, lineStamps, units));
            stampsSpent = Math.addExact(stampsSpent, lineStamps);
        }

        Order targetOrder = resolveTargetOrder(company, customer, attachToOrderId);
        validateDineInRule(lines, targetOrder);

        LoyaltyAccount account = accountService.getOrCreate(company, customer);
        account = accountService.lock(account.getId());
        if (!account.isActive()) {
            throw new IllegalStateException("La tarjeta de fidelización del cliente está desactivada");
        }
        if (account.safeBalance() < stampsSpent) {
            throw new IllegalStateException(
                    "El cliente no tiene sellos suficientes. Necesita " + stampsSpent
                            + " y tiene " + account.safeBalance() + ".");
        }

        PaymentMethodType paymentMethod = null;
        if (targetOrder == null) {
            paymentMethod = firstEnabledPaymentMethod();
            if (paymentMethod == null) {
                throw new IllegalStateException(
                        "No hay métodos de pago habilitados. Configura uno antes de canjear recompensas.");
            }
        }

        Employee employee = employeeService.findByUsername(username).orElse(null);

        // Deduct first: if order creation fails, the transaction returns the stamps.
        account.redeem(stampsSpent);
        accountService.save(account);

        List<OrderDetail> details = new ArrayList<>(lines.size());
        for (RewardLine line : lines) {
            details.addAll(buildRewardDetails(line));
        }

        Order createdOrder;
        if (targetOrder != null) {
            createdOrder = adminOrderService.addItemsToExistingOrder(
                    targetOrder.getIdOrder(), details, username);
        } else {
            Order detailOrder = Order.builder()
                    .orderType(OrderType.TAKEOUT)
                    .status(OrderStatus.PENDING)
                    .customer(customer)
                    .customerName(customer.getFullName())
                    .customerPhone(customer.getPhone())
                    .paymentMethod(paymentMethod)
                    .deliveryCost(BigDecimal.ZERO)
                    .createdBy(username)
                    .build();
            detailOrder.setEmployee(employee);
            createdOrder = adminOrderService.create(detailOrder, details);
        }

        List<LoyaltyRedemption> saved = new ArrayList<>(lines.size());
        for (RewardLine line : lines) {
            LoyaltyRedemption redemption = LoyaltyRedemption.builder()
                    .company(company)
                    .account(account)
                    .reward(line.reward())
                    .stampsSpent(line.stamps())
                    .rewardQuantity(line.quantity())
                    .quoteToken(UUID.randomUUID().toString())
                    .expiresAt(LocalDateTime.now().plusMinutes(15))
                    .createdBy(username)
                    .build();
            redemption.markApplied(createdOrder.getIdOrder(), username);
            saved.add(redemptionRepository.save(redemption));
        }

        createdOrder.setLoyaltyRedemption(saved.get(0));
        orderRepository.save(createdOrder);

        // One ledger row per reward keeps the audit trail readable; the balance
        // snapshot is the same for the whole basket because stamps move once.
        for (int i = 0; i < saved.size(); i++) {
            RewardLine line = lines.get(i);
            ledgerService.record(company, account, createdOrder, saved.get(i),
                    LoyaltyStampEventType.REDEEM, -line.stamps(), account.safeBalance(),
                    "Recompensa: " + line.reward().getName()
                            + (line.quantity() > 1 ? " ×" + line.quantity() : ""),
                    username);
        }

        log.info("Loyalty: customer {} redeemed {} reward line(s) for {} stamps (order {}, attached: {})",
                customer.getIdCustomer(), saved.size(), stampsSpent, createdOrder.getOrderNumber(),
                targetOrder != null);

        LoyaltyRedemption first = saved.get(0);
        return new LoyaltyRedemptionResult(
                first.getId(),
                createdOrder.getIdOrder(),
                createdOrder.getOrderNumber(),
                summarize(lines),
                stampsSpent,
                account.safeBalance(),
                first.getAppliedAt());
    }

    /**
     * Returns the stamps of every reward line of a cancelled order. Called from
     * the normal order-cancellation transaction.
     */
    @Transactional
    public void restoreForCancelledOrder(Long orderId, String username) {
        List<LoyaltyRedemption> redemptions = redemptionRepository.findAllByOrderId(orderId).stream()
                .filter(LoyaltyRedemption::isApplied)
                .toList();
        if (redemptions.isEmpty()) {
            return;
        }

        LoyaltyAccount account = accountService.lock(redemptions.get(0).getAccount().getId());
        for (LoyaltyRedemption redemption : redemptions) {
            account.restore(redemption.getStampsSpent());

            redemption.cancel("Pedido " + orderId + " cancelado");
            redemptionRepository.save(redemption);

            ledgerService.record(redemption.getCompany(), account, null, redemption,
                    LoyaltyStampEventType.REVERSE, redemption.getStampsSpent(),
                    account.safeBalance(), "Reversión por cancelación del pedido", username);
        }
        accountService.save(account);

        log.info("Loyalty: {} stamps restored to account {} after cancelling order {} ({} line(s))",
                redemptions.stream().mapToInt(LoyaltyRedemption::getStampsSpent).sum(),
                account.getId(), orderId, redemptions.size());
    }

    /**
     * Reward orders of this restaurant that are not closed yet, newest first.
     * Several redemptions of the same order are grouped into one row.
     */
    @Transactional(readOnly = true)
    public List<LoyaltyRewardOrderView> pendingRewardOrders(Company company) {
        Map<Long, List<LoyaltyRedemption>> byOrder = new LinkedHashMap<>();
        for (LoyaltyRedemption redemption : redemptionRepository
                .findTop10ByCompanyAndStatusOrderByCreatedAtDesc(company, LoyaltyRedemptionStatus.APPLIED)) {
            if (redemption.getOrderId() == null) {
                continue;
            }
            byOrder.computeIfAbsent(redemption.getOrderId(), key -> new ArrayList<>())
                    .add(redemption);
        }

        List<LoyaltyRewardOrderView> result = new ArrayList<>(byOrder.size());
        for (Map.Entry<Long, List<LoyaltyRedemption>> entry : byOrder.entrySet()) {
            List<LoyaltyRedemption> redemptions = entry.getValue();
            adminOrderService.findById(entry.getKey())
                    .filter(order -> order.getStatus() != OrderStatus.PAID
                            && order.getStatus() != OrderStatus.CANCELLED)
                    // A bill that also has paid items is collected at the normal
                    // payment screen; only pure $0 redemptions are closed here.
                    .filter(order -> order.getTotal() == null
                            || order.getTotal().compareTo(BigDecimal.ZERO) == 0)
                    .ifPresent(order -> result.add(new LoyaltyRewardOrderView(
                            order.getIdOrder(),
                            order.getOrderNumber(),
                            redemptions.get(0).getAccount().getCustomer().getFullName(),
                            summarizeRedemptions(redemptions),
                            order.getStatus().name(),
                            order.getStatus().getDisplayName(),
                            order.getStatus() == OrderStatus.READY
                                    || order.getStatus() == OrderStatus.DELIVERED)));
        }
        return result;
    }

    /**
     * Open dine-in bills of the current restaurant, for the waiter's "attach to
     * the table" selector.
     */
    @Transactional(readOnly = true)
    public List<LoyaltyOpenOrderView> openOrdersForStaff(Company company) {
        return orderRepository.findOpenDineInOrdersByCompany(company).stream()
                .map(order -> new LoyaltyOpenOrderView(
                        order.getIdOrder(),
                        order.getOrderNumber(),
                        order.getTable() != null
                                ? "Mesa " + order.getTable().getTableNumber()
                                : "Sin mesa",
                        order.getCustomer() != null
                                ? order.getCustomer().getFullName()
                                : (order.getCustomerName() != null && !order.getCustomerName().isBlank()
                                        ? order.getCustomerName()
                                        : "Cliente"),
                        order.getStatus().getDisplayName(),
                        order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO))
                .toList();
    }

    /**
     * Closes a standalone reward order without collecting money.
     *
     * <p>A reward order totals $0.00, so it cannot go through the payment screen
     * (tenders must be positive). The cashier hands the free item over and then
     * closes the order from the loyalty screen once it is READY or DELIVERED.
     * Only reward orders of the current restaurant can be closed here, and only
     * while they have no balance to collect.</p>
     */
    @Transactional
    public void settleRewardOrder(Company company, Long orderId, String username) {
        featureAccessService.requireEnabled(company, FeatureKey.LOYALTY);

        Order order = adminOrderService.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

        if (order.getCompany() == null || company == null
                || !company.getIdCompany().equals(order.getCompany().getIdCompany())) {
            throw new IllegalArgumentException("El pedido no pertenece a este restaurante");
        }
        if (!redemptionRepository.existsByOrderId(orderId)) {
            throw new IllegalArgumentException("El pedido no es una recompensa de fidelización");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("El pedido está cancelado");
        }
        if (order.getStatus() == OrderStatus.PAID) {
            return;
        }
        if (order.getTotal() != null && order.getTotal().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException(
                    "El pedido tiene saldo por cobrar. Cóbralo desde la pantalla de pago.");
        }
        if (order.getStatus() != OrderStatus.READY && order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                    "El pedido todavía está en preparación. Podrás cerrarlo cuando esté listo.");
        }

        // Attribute the zero collection to the cashier who closes it, so the order
        // history and the cash-register summary point to a real person.
        employeeService.findByUsername(username).ifPresent(employee -> {
            order.setPaidBy(employee);
            order.setUpdatedBy(username);
        });

        if (order.getStatus() == OrderStatus.READY) {
            adminOrderService.changeStatus(orderId, OrderStatus.DELIVERED, username);
        }
        adminOrderService.changeStatus(orderId, OrderStatus.PAID, username);

        log.info("Loyalty: reward order {} settled at $0.00 by {}",
                order.getOrderNumber(), username);
    }

    @Transactional(readOnly = true)
    public List<LoyaltyRedemption> recent(Company company) {
        return redemptionRepository.findTop50ByCompanyOrderByCreatedAtDesc(company);
    }

    @Transactional(readOnly = true)
    public long countApplied(Company company) {
        return redemptionRepository.countByCompanyAndStatus(company, LoyaltyRedemptionStatus.APPLIED);
    }

    @Transactional(readOnly = true)
    public long stampsRedeemed(Company company) {
        return redemptionRepository.sumStampsByCompanyAndStatus(company, LoyaltyRedemptionStatus.APPLIED);
    }

    @Transactional(readOnly = true)
    public String formattedDate(LocalDateTime utc) {
        return dateTimeService.formatToCompanyTime(utc, "dd/MM/yyyy HH:mm");
    }

    // ========== Internals ==========

    /**
     * Builds the $0.00 lines of one reward. A regular item is one line; a combo is
     * expanded into its parent (the combo itself, READY and used as the grouping row)
     * plus one line per child, each routed to chef/barista/parrilla by its own flags
     * and with its ingredient stock deducted by the normal order pipeline.
     */
    private List<OrderDetail> buildRewardDetails(RewardLine line) {
        String comment = "Recompensa de fidelización: " + line.reward().getName();

        if (!Boolean.TRUE.equals(line.item().getIsCombo())) {
            return List.of(OrderDetail.builder()
                    .itemMenu(line.item())
                    .itemName(line.item().getName())
                    .quantity(line.units())
                    .unitPrice(BigDecimal.ZERO)
                    .subtotal(BigDecimal.ZERO)
                    .promotionAppliedPrice(BigDecimal.ZERO)
                    .comments(comment)
                    .itemStatus(OrderStatus.PENDING)
                    .stockDeducted(true)
                    .build());
        }

        List<ItemMenuComboItem> comboItems = requireActiveComboChildren(
                line.item(), line.reward().getName());
        String comboGroupId = "loyalty_" + UUID.randomUUID();
        List<OrderDetail> details = new ArrayList<>(comboItems.size() + 1);

        // Parent row: the combo price ($0.00) and the grouping id. It is never sent to
        // a preparation area; the children below carry the real work.
        details.add(OrderDetail.builder()
                .itemMenu(line.item())
                .itemName(line.item().getName())
                .quantity(line.units())
                .unitPrice(BigDecimal.ZERO)
                .subtotal(BigDecimal.ZERO)
                .promotionAppliedPrice(BigDecimal.ZERO)
                .comments(comment)
                .itemStatus(OrderStatus.READY)
                .stockDeducted(true)
                .comboGroupId(comboGroupId)
                .build());

        for (ItemMenuComboItem comboChild : comboItems) {
            ItemMenu childItem = comboChild.getChildMenu();
            int childQuantity = comboChild.getQuantity() != null ? comboChild.getQuantity() : 1;
            details.add(OrderDetail.builder()
                    .itemMenu(childItem)
                    .itemName(childItem.getName())
                    .quantity(Math.multiplyExact(childQuantity, line.units()))
                    .unitPrice(BigDecimal.ZERO)
                    .subtotal(BigDecimal.ZERO)
                    .promotionAppliedPrice(BigDecimal.ZERO)
                    .comments(comment)
                    .itemStatus(OrderStatus.PENDING)
                    .stockDeducted(true)
                    .comboGroupId(comboGroupId)
                    .build());
        }
        return details;
    }

    /**
     * Combo children must exist and be active, otherwise the kitchen would receive an
     * incomplete combo. Returns the configured children in display order.
     */
    private List<ItemMenuComboItem> requireActiveComboChildren(ItemMenu combo, String rewardName) {
        List<ItemMenuComboItem> children = comboItemRepository
                .findByComboMenuIdItemMenuOrderByDisplayOrderAsc(combo.getIdItemMenu());
        if (children.isEmpty()) {
            throw new IllegalStateException(
                    "El combo de la recompensa '" + rewardName + "' no tiene artículos configurados");
        }
        for (ItemMenuComboItem child : children) {
            ItemMenu childItem = child.getChildMenu();
            if (childItem == null || !Boolean.TRUE.equals(childItem.getActive())) {
                String childName = childItem != null ? childItem.getName() : "sin nombre";
                throw new IllegalStateException(
                        "El combo de la recompensa '" + rewardName + "' incluye '" + childName
                                + "', que ya no está disponible");
            }
        }
        return children;
    }

    private Map<Long, Integer> normalize(List<LoyaltyRedeemLine> requestedLines) {
        if (requestedLines == null || requestedLines.isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos una recompensa para canjear");
        }

        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (LoyaltyRedeemLine line : requestedLines) {
            if (line == null || line.rewardId() == null) {
                throw new IllegalArgumentException("La selección de recompensas no es válida");
            }
            if (line.quantity() < 1 || line.quantity() > LoyaltyRedeemLine.MAX_QUANTITY_PER_REWARD) {
                throw new IllegalArgumentException("La cantidad de recompensas debe estar entre 1 y "
                        + LoyaltyRedeemLine.MAX_QUANTITY_PER_REWARD);
            }

            int merged = quantities.merge(line.rewardId(), line.quantity(), Integer::sum);
            if (merged > LoyaltyRedeemLine.MAX_QUANTITY_PER_REWARD) {
                throw new IllegalArgumentException(
                        "No se puede canjear la misma recompensa más de "
                                + LoyaltyRedeemLine.MAX_QUANTITY_PER_REWARD + " veces en un canje");
            }
        }

        if (quantities.size() > LoyaltyRedeemLine.MAX_DISTINCT_REWARDS) {
            throw new IllegalArgumentException("Un canje no puede incluir más de "
                    + LoyaltyRedeemLine.MAX_DISTINCT_REWARDS + " recompensas distintas");
        }
        return quantities;
    }

    private Order resolveTargetOrder(Company company, Customer customer, Long attachToOrderId) {
        if (attachToOrderId == null) {
            return null;
        }

        Order order = adminOrderService.findById(attachToOrderId)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta seleccionada no existe"));
        if (order.getCompany() == null
                || !company.getIdCompany().equals(order.getCompany().getIdCompany())) {
            throw new IllegalArgumentException("La cuenta no pertenece a este restaurante");
        }
        if (!order.canAcceptNewItems()) {
            throw new IllegalStateException(
                    "La cuenta ya no acepta artículos (" + order.getStatus().getDisplayName() + ")");
        }
        if (order.getCustomer() != null
                && !order.getCustomer().getIdCustomer().equals(customer.getIdCustomer())) {
            throw new IllegalStateException(
                    "La cuenta de " + describeOrder(order) + " pertenece a otro cliente");
        }
        return order;
    }

    private void validateDineInRule(List<RewardLine> lines, Order targetOrder) {
        boolean dineIn = targetOrder != null && targetOrder.getOrderType() == OrderType.DINE_IN;
        if (dineIn) {
            return;
        }
        for (RewardLine line : lines) {
            if (Boolean.TRUE.equals(line.item().getDineInOnly())) {
                throw new IllegalStateException(
                        "El producto de la recompensa '" + line.reward().getName()
                                + "' solo está disponible para consumo en el establecimiento. "
                                + "Canjéala en una cuenta de mesa abierta.");
            }
        }
    }

    private String describeOrder(Order order) {
        return order.getTable() != null
                ? "la mesa " + order.getTable().getTableNumber()
                : "el pedido " + order.getOrderNumber();
    }

    private String summarize(List<RewardLine> lines) {
        RewardLine first = lines.get(0);
        String firstLabel = first.reward().getName()
                + (first.quantity() > 1 ? " ×" + first.quantity() : "");
        if (lines.size() == 1) {
            return firstLabel;
        }
        return firstLabel + " +" + (lines.size() - 1) + " más";
    }

    private String summarizeRedemptions(List<LoyaltyRedemption> redemptions) {
        LoyaltyRedemption first = redemptions.get(0);
        String firstLabel = first.getReward().getName()
                + (first.safeRewardQuantity() > 1 ? " ×" + first.safeRewardQuantity() : "");
        if (redemptions.size() == 1) {
            return firstLabel;
        }
        return firstLabel + " +" + (redemptions.size() - 1) + " más";
    }

    private PaymentMethodType firstEnabledPaymentMethod() {
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        if (config == null || config.getPaymentMethods() == null) {
            return null;
        }
        if (Boolean.TRUE.equals(config.getPaymentMethods().get(PaymentMethodType.CASH))) {
            return PaymentMethodType.CASH;
        }
        return config.getPaymentMethods().entrySet().stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /**
     * One validated reward line of the basket.
     */
    private record RewardLine(LoyaltyReward reward,
                              ItemMenu item,
                              int quantity,
                              int stamps,
                              int units) {
    }
}
