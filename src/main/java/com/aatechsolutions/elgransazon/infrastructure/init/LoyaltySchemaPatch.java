package com.aatechsolutions.elgransazon.infrastructure.init;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drops uk_loyalty_redemption_order if it still exists.
 *
 * <p>Hibernate {@code ddl-auto=update} creates missing indexes but never removes
 * an old unique one. That index allowed only one redeemed reward per order; the
 * staff screen now redeems several rewards in the same basket, so one order can
 * carry one row per reward. The index on {@code order_id} stays, it is just no
 * longer unique.</p>
 */
@Component
@Order(21)
@Slf4j
public class LoyaltySchemaPatch implements CommandLineRunner {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        try {
            Number count = (Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM information_schema.statistics "
                            + "WHERE table_schema = DATABASE() "
                            + "AND table_name = 'loyalty_redemptions' "
                            + "AND index_name = 'uk_loyalty_redemption_order'")
                    .getSingleResult();
            if (count != null && count.longValue() > 0) {
                entityManager.createNativeQuery(
                        "ALTER TABLE loyalty_redemptions DROP INDEX uk_loyalty_redemption_order")
                        .executeUpdate();
                log.info("Dropped unique index uk_loyalty_redemption_order "
                        + "(one order can now redeem several rewards)");
            }
        } catch (Exception e) {
            log.debug("Could not drop uk_loyalty_redemption_order "
                    + "(table or index may not exist yet): {}", e.getMessage());
        }
    }
}
