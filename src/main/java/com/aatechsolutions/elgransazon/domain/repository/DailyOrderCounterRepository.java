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
     * Uses MySQL's ON DUPLICATE KEY UPDATE to avoid the gap-lock deadlock that
     * occurs when two concurrent transactions both do SELECT-FOR-UPDATE on a
     * non-existent row and then both try to INSERT.
     */
    @Modifying(clearAutomatically = true)
    @Query(value =
        "INSERT INTO daily_order_counters (counter_date, company_id, last_sequence) " +
        "VALUES (:date, :companyId, 1) " +
        "ON DUPLICATE KEY UPDATE last_sequence = last_sequence + 1",
        nativeQuery = true)
    void upsertCounter(@Param("date") LocalDate date, @Param("companyId") Long companyId);

    /**
     * Read back the sequence value that was just written by upsertCounter.
     * Safe within the same REQUIRES_NEW transaction because the exclusive row lock
     * acquired by the UPSERT prevents any other transaction from modifying the row
     * until this transaction commits.
     */
    @Query(value =
        "SELECT last_sequence FROM daily_order_counters " +
        "WHERE counter_date = :date AND company_id = :companyId",
        nativeQuery = true)
    Integer getLastSequence(@Param("date") LocalDate date, @Param("companyId") Long companyId);
}
