package com.aatechsolutions.elgransazon.application.event;

import com.aatechsolutions.elgransazon.application.service.LoyaltyEarningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Awards stamps once the paid order is committed. Keeping this outside the
 * payment transaction means a loyalty failure can never block a payment or
 * leave the order half-updated.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoyaltyEarningListener {

    private final LoyaltyEarningService earningService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderPaid(OrderPaidEvent event) {
        try {
            earningService.awardForPaidOrder(event.orderId(), event.paidBy());
        } catch (Exception e) {
            log.error("Loyalty: could not award stamps for paid order {}: {}",
                    event.orderId(), e.getMessage(), e);
        }
    }
}
