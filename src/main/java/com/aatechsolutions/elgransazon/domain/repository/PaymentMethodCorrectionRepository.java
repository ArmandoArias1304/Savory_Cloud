package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentMethodCorrectionRepository extends JpaRepository<PaymentMethodCorrection, Long> {

    @Query("SELECT c FROM PaymentMethodCorrection c " +
            "WHERE c.order.idOrder = :orderId AND c.company = :company " +
            "ORDER BY c.changedAt DESC")
    List<PaymentMethodCorrection> findByOrderAndCompanyOrderByChangedAtDesc(
            @Param("orderId") Long orderId,
            @Param("company") Company company);
}
