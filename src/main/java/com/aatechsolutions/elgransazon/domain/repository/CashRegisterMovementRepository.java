package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.CashRegisterMovement;
import com.aatechsolutions.elgransazon.domain.entity.CashRegisterSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CashRegisterMovementRepository extends JpaRepository<CashRegisterMovement, Long> {

    List<CashRegisterMovement> findBySessionOrderByOccurredAtAsc(CashRegisterSession session);

    long countBySession(CashRegisterSession session);
}
