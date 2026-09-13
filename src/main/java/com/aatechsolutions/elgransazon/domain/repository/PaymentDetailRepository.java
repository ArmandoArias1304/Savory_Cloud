package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Payment;
import com.aatechsolutions.elgransazon.domain.entity.PaymentDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for PaymentDetail (item lines assigned to a Payment).
 */
@Repository
public interface PaymentDetailRepository extends JpaRepository<PaymentDetail, Long> {

    List<PaymentDetail> findByPayment(Payment payment);
}