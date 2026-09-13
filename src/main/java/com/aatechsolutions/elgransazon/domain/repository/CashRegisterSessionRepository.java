package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.CashRegisterSession;
import com.aatechsolutions.elgransazon.domain.entity.CashRegisterStatus;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashRegisterSessionRepository extends JpaRepository<CashRegisterSession, Long> {

    /** Open drawer of a cashier (at most one at a time). */
    Optional<CashRegisterSession> findFirstByCompanyAndCashierAndStatusOrderByOpenedAtDesc(
            Company company, Employee cashier, CashRegisterStatus status);

    /** Sessions of a cashier opened within a UTC window (day filter). */
    List<CashRegisterSession> findByCompanyAndCashierAndOpenedAtBetweenOrderByOpenedAtDesc(
            Company company, Employee cashier, LocalDateTime start, LocalDateTime end);

    /** Latest session of a cashier (any status). */
    Optional<CashRegisterSession> findFirstByCompanyAndCashierOrderByOpenedAtDesc(
            Company company, Employee cashier);

    /** Session by id scoped to the current company. */
    Optional<CashRegisterSession> findByIdAndCompany(Long id, Company company);
}
