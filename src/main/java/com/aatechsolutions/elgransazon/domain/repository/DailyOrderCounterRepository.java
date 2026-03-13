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
     * Atomically insert or increment the daily counter for a company.
     *
     * On INSERT (first order of the day): initialises last_sequence to
     *   MAX(existing order sequences for today) + 1, preventing duplicates
     *   if the counter table was manually wiped while orders still exist.
     * On UPDATE (duplicate key): increments last_sequence by 1.
     *
     * In both cases MySQL's LAST_INSERT_ID() is set per-connection to the value
     * written, so getLastInsertId() returns the sequence owned by THIS specific
     * transaction — not a value another concurrent transaction may have already
     * incremented.
     *
     * @param prefix  LIKE pattern matching today's order numbers, e.g. 'ORD-20260313-%'
     */
    @Modifying(clearAutomatically = true)
    @Query(value =
        "INSERT INTO daily_order_counters (counter_date, company_id, last_sequence) " +
        "SELECT :date, :companyId, LAST_INSERT_ID(COALESCE(MAX(CAST(SUBSTRING_INDEX(order_number, '-', -1) AS UNSIGNED)), 0) + 1) " +
        "FROM orders WHERE company_id = :companyId AND order_number LIKE :prefix " +
        "ON DUPLICATE KEY UPDATE last_sequence = LAST_INSERT_ID(last_sequence + 1)",
        nativeQuery = true)
    void upsertCounter(@Param("date") LocalDate date, @Param("companyId") Long companyId, @Param("prefix") String prefix);

    /**
     * Returns the sequence number written by THIS connection's last upsertCounter call.
     * MySQL's LAST_INSERT_ID() is session-scoped, so concurrent transactions each
     * see only their own assigned value — no race with getLastSequence().
     */
    @Query(value = "SELECT LAST_INSERT_ID()", nativeQuery = true)
    Long getLastInsertId();
}
