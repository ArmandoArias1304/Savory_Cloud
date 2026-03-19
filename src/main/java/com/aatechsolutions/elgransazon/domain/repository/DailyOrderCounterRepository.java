package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.DailyOrderCounter;
import com.aatechsolutions.elgransazon.domain.entity.DailyOrderCounterKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DailyOrderCounterRepository extends JpaRepository<DailyOrderCounter, DailyOrderCounterKey> {

    /**
     * Find counter by date (from embedded key) and company with pessimistic lock
     * Uses derived query method naming convention
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DailyOrderCounter> findByIdDateAndCompany(LocalDate date, Company company);

    /**
     * Step 1: Initialise the counter row for today if it does not exist.
     *
     * Uses INSERT IGNORE to avoid the S→X lock-upgrade deadlock that
     * INSERT … ON DUPLICATE KEY UPDATE causes when many threads race to
     * insert the first row of the day.
     *
     * When the counter table was manually wiped, this initialises
     * last_sequence to MAX(existing sequences for today) so the next
     * INCREMENT call produces a value that does not collide with any
     * previously created order.
     *
     * When the row already exists, INSERT IGNORE is a no-op.
     */
    @Modifying(clearAutomatically = true)
    @Query(value =
        "INSERT IGNORE INTO daily_order_counters (counter_date, company_id, last_sequence) " +
        "SELECT :date, :companyId, " +
        "COALESCE(MAX(CAST(SUBSTRING_INDEX(order_number, '-', -1) AS UNSIGNED)), 0) " +
        "FROM orders WHERE company_id = :companyId AND order_number LIKE :prefix",
        nativeQuery = true)
    void initCounterIfAbsent(@Param("date") LocalDate date,
                             @Param("companyId") Long companyId,
                             @Param("prefix") String prefix);

    /**
     * Read-only MAX sequence from existing orders for a given company-day prefix.
     * No write locks — used to pre-compute the initial counter value before
     * calling {@link #insertIgnoreWithValue}, eliminating the cross-table lock
     * contention of {@link #initCounterIfAbsent}.
     */
    @Query(value =
        "SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(order_number, '-', -1) AS UNSIGNED)), 0) " +
        "FROM orders WHERE company_id = :companyId AND order_number LIKE :prefix",
        nativeQuery = true)
    Long findMaxSequenceFromOrders(@Param("companyId") Long companyId,
                                   @Param("prefix") String prefix);

    /**
     * Simple INSERT IGNORE with a pre-computed value (no cross-table SELECT).
     * Used together with {@link #findMaxSequenceFromOrders} to initialise the
     * counter without holding both read locks on {@code orders} and write locks
     * on {@code daily_order_counters} at the same time.
     */
    @Modifying(clearAutomatically = true)
    @Query(value =
        "INSERT IGNORE INTO daily_order_counters (counter_date, company_id, last_sequence) " +
        "VALUES (:date, :companyId, :initialValue)",
        nativeQuery = true)
    void insertIgnoreWithValue(@Param("date") LocalDate date,
                               @Param("companyId") Long companyId,
                               @Param("initialValue") Long initialValue);

    /** Whether a counter row already exists for this date + company. */
    @Query(value =
        "SELECT COUNT(*) FROM daily_order_counters " +
        "WHERE counter_date = :date AND company_id = :companyId",
        nativeQuery = true)
    int existsCounter(@Param("date") LocalDate date, @Param("companyId") Long companyId);

    /**
     * Step 2: Atomically increment the counter and capture the new value
     * via LAST_INSERT_ID(expr) so the caller can retrieve it with
     * getLastInsertId().
     *
     * The row-level UPDATE lock serialises concurrent increments without
     * causing the cross-table deadlocks typical of INSERT … SELECT patterns.
     */
    @Modifying(clearAutomatically = true)
    @Query(value =
        "UPDATE daily_order_counters " +
        "SET last_sequence = LAST_INSERT_ID(last_sequence + 1) " +
        "WHERE counter_date = :date AND company_id = :companyId",
        nativeQuery = true)
    int incrementCounter(@Param("date") LocalDate date, @Param("companyId") Long companyId);

    /**
     * Returns the sequence number written by THIS connection's last upsertCounter call.
     * MySQL's LAST_INSERT_ID() is session-scoped, so concurrent transactions each
     * see only their own assigned value — no race with getLastSequence().
     */
    @Query(value = "SELECT LAST_INSERT_ID()", nativeQuery = true)
    Long getLastInsertId();
}
