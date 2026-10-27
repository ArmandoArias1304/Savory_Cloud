package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.LoyaltyStampEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single writer for the loyalty ledger.
 *
 * <p>All stamp movements go through this service so the balance and the
 * immutable event are always created together. Callers must hold the account
 * lock before calling it.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyLedgerService {

    private final LoyaltyStampEventRepository eventRepository;

    @Transactional
    public LoyaltyStampEvent record(Company company,
                                    LoyaltyAccount account,
                                    Order order,
                                    LoyaltyRedemption redemption,
                                    LoyaltyStampEventType type,
                                    int stamps,
                                    int balanceAfter,
                                    String reason,
                                    String username) {
        LoyaltyStampEvent event = LoyaltyStampEvent.builder()
                .company(company)
                .account(account)
                .order(order)
                .redemption(redemption)
                .eventType(type)
                .stamps(stamps)
                .balanceAfter(balanceAfter)
                .reason(reason)
                .createdBy(username)
                .build();

        LoyaltyStampEvent saved = eventRepository.save(event);
        log.debug("Loyalty ledger {} {} stamps for account {} (balance {})",
                type, stamps, account.getId(), balanceAfter);
        return saved;
    }
}
