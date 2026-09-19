package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.Payment;
import com.aatechsolutions.elgransazon.domain.entity.PaymentTender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link PaymentTender} rows (portions of a collection paid
 * with a given method). Persistence normally goes through the Order/Payment
 * cascade; this repository is for targeted lookups and tests.
 */
@Repository
public interface PaymentTenderRepository extends JpaRepository<PaymentTender, Long> {

    List<PaymentTender> findByOrderOrderByIdPaymentTenderAsc(Order order);

    List<PaymentTender> findByPaymentOrderByIdPaymentTenderAsc(Payment payment);
}
