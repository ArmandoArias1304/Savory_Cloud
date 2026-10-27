package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.LoyaltyStampEventRepository;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoyaltyEarningServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private LoyaltyProgramService programService;
    @Mock private LoyaltyCycleService cycleService;
    @Mock private LoyaltyAccountService accountService;
    @Mock private LoyaltyLedgerService ledgerService;
    @Mock private LoyaltyStampEventRepository eventRepository;
    @Mock private FeatureAccessService featureAccessService;
    @Mock private DateTimeService dateTimeService;

    private LoyaltyEarningService service;
    private Company company;
    private Customer customer;

    @BeforeEach
    void setUp() {
        service = new LoyaltyEarningService(orderRepository, programService, cycleService, accountService,
                ledgerService, eventRepository, featureAccessService, dateTimeService);
        company = Company.builder().idCompany(1L).build();
        customer = Customer.builder().idCustomer(2L).build();
        lenient().when(cycleService.processAndCheck(company)).thenReturn(true);
    }

    private Order paidOrder(String orderNumber, BigDecimal total) {
        Order order = Order.builder()
                .company(company)
                .customer(customer)
                .status(OrderStatus.PAID)
                .orderType(OrderType.TAKEOUT)
                .orderNumber(orderNumber)
                .total(total)
                .build();
        order.addOrderDetail(OrderDetail.builder().quantity(1).build());
        return order;
    }

    private LoyaltyProgram program() {
        return LoyaltyProgram.builder()
                .company(company)
                .stampsRequired(10)
                .minimumPurchaseAmount(BigDecimal.ZERO)
                .minimumItems(1)
                .maxStampsPerSale(1)
                .active(true)
                .build();
    }

    @Test
    void awardsOneStampForAnEligiblePaidOrder() {
        Order order = paidOrder("ORD-1", new BigDecimal("100.00"));
        LoyaltyProgram program = program();
        LoyaltyAccount account = LoyaltyAccount.builder()
                .id(4L).company(company).customer(customer).balance(0).build();

        when(orderRepository.findByIdWithDetails(50L)).thenReturn(Optional.of(order));
        when(featureAccessService.isEnabled(company, FeatureKey.LOYALTY)).thenReturn(true);
        when(programService.findForCompany(company)).thenReturn(Optional.of(program));
        when(accountService.getOrCreate(company, customer)).thenReturn(account);
        when(accountService.lock(4L)).thenReturn(account);
        when(eventRepository.existsByOrder_IdOrderAndEventType(50L, LoyaltyStampEventType.EARN))
                .thenReturn(false);

        assertTrue(service.awardForPaidOrder(50L, "cashier"));

        assertTrue(account.getBalance() == 1);
        verify(accountService).save(account);
        verify(ledgerService).record(eq(company), eq(account), eq(order), isNull(),
                eq(LoyaltyStampEventType.EARN), eq(1), eq(1), anyString(), eq("cashier"));
    }

    @Test
    void neverAwardsTwiceForTheSameOrder() {
        Order order = paidOrder("ORD-1", new BigDecimal("100.00"));
        when(orderRepository.findByIdWithDetails(50L)).thenReturn(Optional.of(order));
        when(featureAccessService.isEnabled(company, FeatureKey.LOYALTY)).thenReturn(true);
        when(eventRepository.existsByOrder_IdOrderAndEventType(50L, LoyaltyStampEventType.EARN))
                .thenReturn(true);

        assertFalse(service.awardForPaidOrder(50L, "cashier"));
        verify(accountService, never()).getOrCreate(any(), any());
    }

    @Test
    void doesNotAwardForAZeroTotalRewardOrder() {
        Order order = paidOrder("ORD-1", BigDecimal.ZERO);
        when(orderRepository.findByIdWithDetails(50L)).thenReturn(Optional.of(order));
        when(featureAccessService.isEnabled(company, FeatureKey.LOYALTY)).thenReturn(true);

        assertFalse(service.awardForPaidOrder(50L, "cashier"));
        verify(accountService, never()).getOrCreate(any(), any());
    }
}
