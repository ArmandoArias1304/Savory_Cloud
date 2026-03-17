package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.repository.DailyOrderCounterRepository;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Manages the daily order counter without cross-table locking deadlocks.
 *
 * <h3>Strategy:</h3>
 * <ol>
 *   <li>{@link #ensureCounterExists}: First checks if the row exists (fast,
 *       no write locks). If it does, returns immediately. If not, reads
 *       {@code MAX(sequence)} from {@code orders} in a read-only query (no
 *       INSERT pressure), then does a plain {@code INSERT IGNORE … VALUES}
 *       with the pre-computed value. Because the INSERT does not contain a
 *       cross-table SELECT, it holds only a brief X lock on the new row and
 *       does not conflict with concurrent reads on {@code orders}.</li>
 *   <li>{@link #incrementAndGet}: Issues an atomic {@code UPDATE} that both
 *       increments and captures the new sequence via
 *       {@code LAST_INSERT_ID(expr)}. The row-level X lock serialises
 *       concurrent increments cleanly.</li>
 * </ol>
 *
 * Both methods run in their own {@code REQUIRES_NEW} transaction so that they
 * commit immediately and their locks are released before the outer
 * {@code create()} transaction continues.
 */
@Service
public class DailyOrderCounterService {

    private final DailyOrderCounterRepository counterRepository;

    public DailyOrderCounterService(DailyOrderCounterRepository counterRepository) {
        this.counterRepository = counterRepository;
    }

    /**
     * Ensures the counter row exists (if missing, seeds it from MAX order sequence),
     * then atomically increments it — all in a single REQUIRES_NEW transaction.
     *
     * <p>Using one transaction instead of two reduces connection pool pressure:
     * each caller needs only its outer connection + this sub-transaction connection
     * (2 instead of 3 previously).</p>
     *
     * <p>The INSERT uses a pre-computed value (no cross-table SELECT inside the INSERT)
     * to avoid InnoDB gap-lock/record-lock deadlocks under high concurrency.</p>
     *
     * @throws CannotAcquireLockException if a deadlock occurs (caller should retry)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long ensureAndIncrement(LocalDate date, Long companyId, String prefix) {
        // Fast path: row exists — skip initialisation entirely
        if (counterRepository.existsCounter(date, companyId) == 0) {
            // Slow path: read MAX from orders in a read-only query (no INSERT pressure),
            // then do a simple INSERT IGNORE VALUES — no cross-table SELECT, no gap locks.
            Long maxSeq = counterRepository.findMaxSequenceFromOrders(companyId, prefix);
            long initialValue = maxSeq != null ? maxSeq : 0L;
            counterRepository.insertIgnoreWithValue(date, companyId, initialValue);
        }
        counterRepository.incrementCounter(date, companyId);
        Long seq = counterRepository.getLastInsertId();
        return seq != null ? seq : 0L;
    }

    /**
     * @deprecated Use {@link #ensureAndIncrement} instead (fewer connections).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureCounterExists(LocalDate date, Long companyId, String prefix) {
        if (counterRepository.existsCounter(date, companyId) > 0) {
            return;
        }
        Long maxSeq = counterRepository.findMaxSequenceFromOrders(companyId, prefix);
        long initialValue = maxSeq != null ? maxSeq : 0L;
        counterRepository.insertIgnoreWithValue(date, companyId, initialValue);
    }

    /**
     * @deprecated Use {@link #ensureAndIncrement} instead (fewer connections).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long incrementAndGet(LocalDate date, Long companyId) {
        counterRepository.incrementCounter(date, companyId);
        Long seq = counterRepository.getLastInsertId();
        return seq != null ? seq : 0L;
    }
}
