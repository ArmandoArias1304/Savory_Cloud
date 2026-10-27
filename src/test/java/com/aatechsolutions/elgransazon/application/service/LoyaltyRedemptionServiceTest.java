package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.application.dto.LoyaltyRedeemLine;
import com.aatechsolutions.elgransazon.application.dto.LoyaltyRedemptionResult;
import com.aatechsolutions.elgransazon.application.dto.LoyaltyRewardOrderView;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.CustomerRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComboItemRepository;
import com.aatechsolutions.elgransazon.domain.repository.LoyaltyRedemptionRepository;
import com.aatechsolutions.elgransazon.domain.repository.LoyaltyRewardRepository;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoyaltyRedemptionServiceTest {

    @Mock private LoyaltyRewardRepository rewardRepository;
    @Mock private LoyaltyRedemptionRepository redemptionRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ItemMenuComboItemRepository comboItemRepository;
    @Mock private LoyaltyAccountService accountService;
    @Mock private LoyaltyCycleService cycleService;
    @Mock private LoyaltyLedgerService ledgerService;
    @Mock private FeatureAccessService featureAccessService;
    @Mock private SystemConfigurationService systemConfigurationService;
    @Mock private EmployeeService employeeService;
    @Mock private DateTimeService dateTimeService;
    @Mock private OrderServiceImpl adminOrderService;

    private LoyaltyRedemptionService service;
    private Company company;
    private Customer customer;
    private ItemMenu item;
    private LoyaltyReward reward;
    private LoyaltyAccount account;

    @BeforeEach
    void setUp() {
        service = new LoyaltyRedemptionService(rewardRepository, redemptionRepository,
                customerRepository, orderRepository, comboItemRepository, accountService,
                cycleService, ledgerService, featureAccessService, systemConfigurationService, employeeService,
                dateTimeService, adminOrderService);

        company = Company.builder().idCompany(1L).build();
        customer = Customer.builder().idCustomer(2L).fullName("Ana").phone("5512345678")
                .active(true).build();
        item = ItemMenu.builder().idItemMenu(3L).name("Café americano")
                .price(new BigDecimal("35.00")).active(true).internalOnly(false)
                .isCombo(false).dineInOnly(false).build();
        reward = LoyaltyReward.builder().id(4L).company(company).itemMenu(item)
                .name("Café gratis").stampsCost(5).freeQuantity(1).active(true).build();
        account = LoyaltyAccount.builder().id(6L).company(company).customer(customer)
                .balance(12).lifetimeEarned(12).lifetimeRedeemed(0).active(true).build();
        lenient().when(cycleService.processAndCheck(company)).thenReturn(true);
    }

    private void enableCashPayments() {
        SystemConfiguration config = mock(SystemConfiguration.class);
        when(config.getPaymentMethods()).thenReturn(Map.of(PaymentMethodType.CASH, true));
        when(systemConfigurationService.getConfiguration()).thenReturn(config);
    }

    private Order standaloneOrder() {
        return Order.builder()
                .idOrder(99L)
                .orderNumber("ORD-99")
                .status(OrderStatus.PENDING)
                .orderType(OrderType.TAKEOUT)
                .total(BigDecimal.ZERO)
                .build();
    }

    @Test
    void createsAFreeOrderAndDeductsTheStamps() {
        enableCashPayments();
        when(customerRepository.findByIdCustomerAndCompany(2L, company)).thenReturn(Optional.of(customer));
        when(rewardRepository.findByIdAndCompany(4L, company)).thenReturn(Optional.of(reward));
        when(accountService.getOrCreate(company, customer)).thenReturn(account);
        when(accountService.lock(6L)).thenReturn(account);
        when(employeeService.findByUsername("cashier")).thenReturn(Optional.empty());

        Order created = standaloneOrder();
        when(adminOrderService.create(any(Order.class), anyList())).thenReturn(created);
        when(redemptionRepository.save(any(LoyaltyRedemption.class))).thenAnswer(invocation -> {
            LoyaltyRedemption redemption = invocation.getArgument(0);
            redemption.setId(7L);
            return redemption;
        });

        LoyaltyRedemptionResult result = service.redeem(company, 2L, 4L, 1, "cashier");

        assertEquals(7, account.getBalance());
        assertEquals(5, result.stampsSpent());
        assertEquals(99L, result.orderId());
        assertEquals("ORD-99", result.orderNumber());
        assertEquals(7, result.remainingBalance());
        assertTrue(created.isZeroLoyaltyRedemption());

        verify(adminOrderService).create(argThat(order ->
                        order.getCustomer() == customer
                                && order.getOrderType() == OrderType.TAKEOUT
                                && order.getCustomerName().equals("Ana")),
                argThat(details -> details.size() == 1
                        && details.get(0).getQuantity() == 1
                        && details.get(0).getUnitPrice().compareTo(BigDecimal.ZERO) == 0));
        verify(orderRepository).save(argThat(order ->
                order.getIdOrder().equals(99L)
                        && order.getLoyaltyRedemption() != null
                        && order.getLoyaltyRedemption().getOrderId().equals(99L)));
        verify(ledgerService).record(eq(company), eq(account), eq(created), any(),
                eq(LoyaltyStampEventType.REDEEM), eq(-5), eq(7), anyString(), eq("cashier"));
    }

    @Test
    void redeemsAComboBySendingItsChildItemsToTheKitchen() {
        enableCashPayments();
        ItemMenu combo = ItemMenu.builder().idItemMenu(20L).name("Combo pareja")
                .price(new BigDecimal("150.00")).active(true).internalOnly(false)
                .isCombo(true).dineInOnly(false).build();
        ItemMenu burger = ItemMenu.builder().idItemMenu(21L).name("Hamburguesa")
                .price(new BigDecimal("80.00")).active(true).internalOnly(false)
                .isCombo(false).requiresPreparation(true).build();
        ItemMenu soda = ItemMenu.builder().idItemMenu(22L).name("Refresco")
                .price(new BigDecimal("30.00")).active(true).internalOnly(false)
                .isCombo(false).requiresPreparation(false).build();
        ItemMenuComboItem burgerLink = ItemMenuComboItem.builder()
                .comboMenu(combo).childMenu(burger).quantity(1).displayOrder(0).build();
        ItemMenuComboItem sodaLink = ItemMenuComboItem.builder()
                .comboMenu(combo).childMenu(soda).quantity(2).displayOrder(1).build();
        LoyaltyReward comboReward = LoyaltyReward.builder().id(30L).company(company)
                .itemMenu(combo).name("Combo gratis").stampsCost(10).freeQuantity(1)
                .active(true).build();

        when(customerRepository.findByIdCustomerAndCompany(2L, company)).thenReturn(Optional.of(customer));
        when(rewardRepository.findByIdAndCompany(30L, company)).thenReturn(Optional.of(comboReward));
        when(comboItemRepository.findByComboMenuIdItemMenuOrderByDisplayOrderAsc(20L))
                .thenReturn(List.of(burgerLink, sodaLink));
        when(accountService.getOrCreate(company, customer)).thenReturn(account);
        when(accountService.lock(6L)).thenReturn(account);
        when(employeeService.findByUsername("cashier")).thenReturn(Optional.empty());
        when(adminOrderService.create(any(Order.class), anyList())).thenReturn(standaloneOrder());
        when(redemptionRepository.save(any(LoyaltyRedemption.class))).thenAnswer(invocation -> {
            LoyaltyRedemption redemption = invocation.getArgument(0);
            redemption.setId(7L);
            return redemption;
        });

        LoyaltyRedemptionResult result = service.redeem(company, 2L, 30L, 1, "cashier");

        assertEquals(10, result.stampsSpent());
        assertEquals(2, account.getBalance());
        verify(adminOrderService).create(any(Order.class), argThat(details -> {
            if (details.size() != 3) {
                return false;
            }
            OrderDetail parent = details.get(0);
            OrderDetail burgerLine = details.get(1);
            OrderDetail sodaLine = details.get(2);
            String group = parent.getComboGroupId();
            return group != null
                    && group.startsWith("loyalty_")
                    && parent.isComboParent()
                    && burgerLine.isComboChild()
                    && sodaLine.isComboChild()
                    && group.equals(burgerLine.getComboGroupId())
                    && group.equals(sodaLine.getComboGroupId())
                    && burgerLine.getQuantity() == 1
                    && sodaLine.getQuantity() == 2
                    && burgerLine.getItemMenu() == burger
                    && sodaLine.getItemMenu() == soda
                    && details.stream().allMatch(detail ->
                            detail.getUnitPrice().compareTo(BigDecimal.ZERO) == 0);
        }));
    }

    @Test
    void redeemsSeveralRewardsInOneOrderAndChargesTheSum() {
        enableCashPayments();
        ItemMenu juiceItem = ItemMenu.builder().idItemMenu(8L).name("Jugo")
                .price(new BigDecimal("40.00")).active(true).internalOnly(false)
                .isCombo(false).dineInOnly(false).build();
        LoyaltyReward juiceReward = LoyaltyReward.builder().id(9L).company(company)
                .itemMenu(juiceItem).name("Jugo gratis").stampsCost(3).freeQuantity(2)
                .active(true).build();

        when(customerRepository.findByIdCustomerAndCompany(2L, company)).thenReturn(Optional.of(customer));
        when(rewardRepository.findByIdAndCompany(4L, company)).thenReturn(Optional.of(reward));
        when(rewardRepository.findByIdAndCompany(9L, company)).thenReturn(Optional.of(juiceReward));
        when(accountService.getOrCreate(company, customer)).thenReturn(account);
        when(accountService.lock(6L)).thenReturn(account);
        when(employeeService.findByUsername("cashier")).thenReturn(Optional.empty());
        when(adminOrderService.create(any(Order.class), anyList())).thenReturn(standaloneOrder());

        int[] nextId = {7};
        when(redemptionRepository.save(any(LoyaltyRedemption.class))).thenAnswer(invocation -> {
            LoyaltyRedemption redemption = invocation.getArgument(0);
            redemption.setId((long) nextId[0]++);
            return redemption;
        });

        LoyaltyRedemptionResult result = service.redeemBasket(company, 2L,
                List.of(new LoyaltyRedeemLine(4L, 1), new LoyaltyRedeemLine(9L, 2)),
                null, "cashier");

        // 5 stamps for the coffee + 3 × 2 for the juices = 11
        assertEquals(11, result.stampsSpent());
        assertEquals(1, account.getBalance());
        assertEquals("Café gratis +1 más", result.rewardName());
        assertEquals(1, result.remainingBalance());

        verify(adminOrderService).create(any(Order.class), argThat(details ->
                details.size() == 2
                        && details.get(0).getQuantity() == 1
                        && details.get(1).getQuantity() == 4));
        verify(redemptionRepository, times(2)).save(any(LoyaltyRedemption.class));
        verify(ledgerService).record(eq(company), eq(account), any(Order.class), any(),
                eq(LoyaltyStampEventType.REDEEM), eq(-5), eq(1), anyString(), eq("cashier"));
        verify(ledgerService).record(eq(company), eq(account), any(Order.class), any(),
                eq(LoyaltyStampEventType.REDEEM), eq(-6), eq(1), anyString(), eq("cashier"));
    }

    @Test
    void attachesTheFreeLinesToTheOpenBillAndSkipsTheZeroOrder() {
        Order openOrder = Order.builder()
                .idOrder(80L)
                .orderNumber("ORD-80")
                .company(company)
                .customer(customer)
                .status(OrderStatus.PENDING)
                .orderType(OrderType.DINE_IN)
                .total(new BigDecimal("120.00"))
                .build();

        when(customerRepository.findByIdCustomerAndCompany(2L, company)).thenReturn(Optional.of(customer));
        when(rewardRepository.findByIdAndCompany(4L, company)).thenReturn(Optional.of(reward));
        when(adminOrderService.findById(80L)).thenReturn(Optional.of(openOrder));
        when(accountService.getOrCreate(company, customer)).thenReturn(account);
        when(accountService.lock(6L)).thenReturn(account);
        when(employeeService.findByUsername("waiter")).thenReturn(Optional.empty());
        when(adminOrderService.addItemsToExistingOrder(eq(80L), anyList(), eq("waiter")))
                .thenReturn(openOrder);
        when(redemptionRepository.save(any(LoyaltyRedemption.class))).thenAnswer(invocation -> {
            LoyaltyRedemption redemption = invocation.getArgument(0);
            redemption.setId(7L);
            return redemption;
        });

        LoyaltyRedemptionResult result = service.redeemBasket(company, 2L,
                List.of(new LoyaltyRedeemLine(4L, 1)), 80L, "waiter");

        assertEquals(80L, result.orderId());
        assertEquals(7, account.getBalance());
        assertTrue(openOrder.isLoyaltyRedemption());
        assertTrue(!openOrder.isZeroLoyaltyRedemption());
        verify(adminOrderService, never()).create(any(), anyList());
        verify(orderRepository).save(argThat(order ->
                order.getIdOrder().equals(80L)
                        && order.getLoyaltyRedemption() != null
                        && order.getLoyaltyRedemption().getOrderId().equals(80L)));
        verify(adminOrderService).addItemsToExistingOrder(eq(80L), argThat(details ->
                details.size() == 1
                        && details.get(0).getUnitPrice().compareTo(BigDecimal.ZERO) == 0
                        && details.get(0).getComments().contains("Café gratis")),
                eq("waiter"));
    }

    @Test
    void refusesToAttachTheRewardToAnotherCustomersBill() {
        Order openOrder = Order.builder()
                .idOrder(80L)
                .orderNumber("ORD-80")
                .company(company)
                .customer(Customer.builder().idCustomer(99L).fullName("Otro").build())
                .status(OrderStatus.PENDING)
                .orderType(OrderType.DINE_IN)
                .total(new BigDecimal("120.00"))
                .build();

        when(customerRepository.findByIdCustomerAndCompany(2L, company)).thenReturn(Optional.of(customer));
        when(rewardRepository.findByIdAndCompany(4L, company)).thenReturn(Optional.of(reward));
        when(adminOrderService.findById(80L)).thenReturn(Optional.of(openOrder));

        assertThrows(IllegalStateException.class, () -> service.redeemBasket(company, 2L,
                List.of(new LoyaltyRedeemLine(4L, 1)), 80L, "waiter"));

        verify(accountService, never()).save(any());
        verify(adminOrderService, never()).addItemsToExistingOrder(any(), anyList(), anyString());
    }

    @Test
    void pendingRewardOrdersGroupsLinesAndSkipsBillsWithABalance() {
        LoyaltyReward juice = LoyaltyReward.builder().id(9L).company(company)
                .name("Jugo gratis").stampsCost(3).freeQuantity(2).active(true).build();

        Order mixedBill = Order.builder()
                .idOrder(77L).orderNumber("ORD-77").company(company)
                .status(OrderStatus.PENDING).total(new BigDecimal("120.00"))
                .build();
        Order freeOrder = Order.builder()
                .idOrder(99L).orderNumber("ORD-99").company(company)
                .status(OrderStatus.READY).total(BigDecimal.ZERO)
                .build();

        LoyaltyRedemption onMixed = LoyaltyRedemption.builder()
                .id(1L).company(company).account(account).reward(reward)
                .stampsSpent(5).rewardQuantity(1).orderId(77L).quoteToken("a").build();
        LoyaltyRedemption coffee = LoyaltyRedemption.builder()
                .id(2L).company(company).account(account).reward(reward)
                .stampsSpent(5).rewardQuantity(1).orderId(99L).quoteToken("b").build();
        LoyaltyRedemption juiceLine = LoyaltyRedemption.builder()
                .id(3L).company(company).account(account).reward(juice)
                .stampsSpent(6).rewardQuantity(2).orderId(99L).quoteToken("c").build();

        when(redemptionRepository.findTop10ByCompanyAndStatusOrderByCreatedAtDesc(
                company, LoyaltyRedemptionStatus.APPLIED))
                .thenReturn(List.of(onMixed, coffee, juiceLine));
        when(adminOrderService.findById(77L)).thenReturn(Optional.of(mixedBill));
        when(adminOrderService.findById(99L)).thenReturn(Optional.of(freeOrder));

        List<LoyaltyRewardOrderView> result = service.pendingRewardOrders(company);

        assertEquals(1, result.size());
        assertEquals("ORD-99", result.get(0).orderNumber());
        assertEquals("Café gratis +1 más", result.get(0).rewardName());
        assertTrue(result.get(0).canBeSettled());
    }

    @Test
    void restoresEveryLineWhenTheOrderIsCancelled() {
        LoyaltyRedemption coffee = LoyaltyRedemption.builder()
                .id(11L).company(company).account(account).reward(reward)
                .stampsSpent(5).rewardQuantity(1).orderId(77L)
                .quoteToken("a").build();
        coffee.markApplied(77L, "cashier");
        LoyaltyRedemption juice = LoyaltyRedemption.builder()
                .id(12L).company(company).account(account).reward(reward)
                .stampsSpent(6).rewardQuantity(2).orderId(77L)
                .quoteToken("b").build();
        juice.markApplied(77L, "cashier");
        account.setBalance(1);
        account.setLifetimeRedeemed(11);

        when(redemptionRepository.findAllByOrderId(77L)).thenReturn(List.of(coffee, juice));
        when(accountService.lock(6L)).thenReturn(account);

        service.restoreForCancelledOrder(77L, "manager");

        assertEquals(12, account.getBalance());
        assertEquals(0, account.getLifetimeRedeemed());
        assertEquals(LoyaltyRedemptionStatus.CANCELLED, coffee.getStatus());
        assertEquals(LoyaltyRedemptionStatus.CANCELLED, juice.getStatus());
        verify(accountService).save(account);
        verify(ledgerService).record(eq(company), eq(account), isNull(), eq(coffee),
                eq(LoyaltyStampEventType.REVERSE), eq(5), eq(6), anyString(), eq("manager"));
        verify(ledgerService).record(eq(company), eq(account), isNull(), eq(juice),
                eq(LoyaltyStampEventType.REVERSE), eq(6), eq(12), anyString(), eq("manager"));
    }

    @Test
    void settlesAZeroTotalRewardOrderWithoutCollectingMoney() {
        Order rewardOrder = Order.builder()
                .idOrder(99L)
                .orderNumber("ORD-99")
                .company(company)
                .status(OrderStatus.DELIVERED)
                .orderType(OrderType.TAKEOUT)
                .total(BigDecimal.ZERO)
                .build();

        when(adminOrderService.findById(99L)).thenReturn(Optional.of(rewardOrder));
        when(redemptionRepository.existsByOrderId(99L)).thenReturn(true);
        when(employeeService.findByUsername("cashier")).thenReturn(Optional.empty());

        service.settleRewardOrder(company, 99L, "cashier");

        verify(adminOrderService).changeStatus(99L, OrderStatus.PAID, "cashier");
        verify(adminOrderService, never()).changeStatus(99L, OrderStatus.DELIVERED, "cashier");
    }

    @Test
    void marksAReadyRewardOrderAsDeliveredBeforeClosingIt() {
        Order rewardOrder = Order.builder()
                .idOrder(99L)
                .orderNumber("ORD-99")
                .company(company)
                .status(OrderStatus.READY)
                .orderType(OrderType.TAKEOUT)
                .total(BigDecimal.ZERO)
                .build();

        when(adminOrderService.findById(99L)).thenReturn(Optional.of(rewardOrder));
        when(redemptionRepository.existsByOrderId(99L)).thenReturn(true);
        when(employeeService.findByUsername("cashier")).thenReturn(Optional.empty());

        service.settleRewardOrder(company, 99L, "cashier");

        verify(adminOrderService).changeStatus(99L, OrderStatus.DELIVERED, "cashier");
        verify(adminOrderService).changeStatus(99L, OrderStatus.PAID, "cashier");
    }

    @Test
    void refusesToSettleAnOrderThatStillHasABalance() {
        Order paidOrder = Order.builder()
                .idOrder(99L)
                .orderNumber("ORD-99")
                .company(company)
                .status(OrderStatus.DELIVERED)
                .orderType(OrderType.TAKEOUT)
                .total(new BigDecimal("35.00"))
                .build();

        when(adminOrderService.findById(99L)).thenReturn(Optional.of(paidOrder));
        when(redemptionRepository.existsByOrderId(99L)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.settleRewardOrder(company, 99L, "cashier"));

        verify(adminOrderService, never()).changeStatus(any(), any(), anyString());
    }

    @Test
    void rejectsRedemptionWhenBalanceIsInsufficient() {
        when(customerRepository.findByIdCustomerAndCompany(2L, company)).thenReturn(Optional.of(customer));
        when(rewardRepository.findByIdAndCompany(4L, company)).thenReturn(Optional.of(reward));
        when(accountService.getOrCreate(company, customer)).thenReturn(account);
        when(accountService.lock(6L)).thenReturn(account);
        account.setBalance(2);

        assertThrows(IllegalStateException.class,
                () -> service.redeem(company, 2L, 4L, 1, "cashier"));

        verify(adminOrderService, never()).create(any(), anyList());
        verify(accountService, never()).save(any());
    }
}
