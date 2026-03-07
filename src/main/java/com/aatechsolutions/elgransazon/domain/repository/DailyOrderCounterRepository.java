package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.DailyOrderCounter;
import com.aatechsolutions.elgransazon.domain.entity.DailyOrderCounterKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
}
