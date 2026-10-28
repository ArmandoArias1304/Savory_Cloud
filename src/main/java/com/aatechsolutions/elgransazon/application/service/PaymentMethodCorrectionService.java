package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.domain.repository.PaymentMethodCorrectionRepository;
import com.aatechsolutions.elgransazon.domain.repository.PaymentRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.util.PaymentTenderSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class PaymentMethodCorrectionService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMethodCorrectionRepository correctionRepository;

    public PaymentMethodCorrectionService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PaymentMethodCorrectionRepository correctionRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.correctionRepository = correctionRepository;
    }

    @Transactional
    public PaymentMethodCorrection correct(
            Long orderId,
            Long paymentId,
            PaymentMethodType newMethod,
            String reason,
            String changedBy) {
        if (newMethod == null) {
            throw new IllegalArgumentException("Selecciona el método de pago correcto");
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.length() < 5 || normalizedReason.length() > 500) {
            throw new IllegalArgumentException("El motivo debe tener entre 5 y 500 caracteres");
        }
        if (changedBy == null || changedBy.isBlank() || changedBy.length() > 100) {
            throw new IllegalArgumentException("No se pudo identificar al usuario que realiza la corrección");
        }

        Company company = CompanyContext.requireCurrentCompany();
        Order order = orderRepository.findByIdOrderAndCompany(orderId, company)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

        if (paymentId == null) {
            return correctWholeOrder(order, newMethod, normalizedReason, changedBy);
        }
        return correctAccount(order, paymentId, newMethod, normalizedReason, changedBy, company);
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodCorrection> findHistory(Long orderId) {
        return correctionRepository.findByOrderAndCompanyOrderByChangedAtDesc(
                orderId, CompanyContext.requireCurrentCompany());
    }

    private PaymentMethodCorrection correctWholeOrder(
            Order order, PaymentMethodType newMethod, String reason, String changedBy) {
        if (order.isSplitOrder()) {
            throw new IllegalStateException("Selecciona la cuenta que deseas corregir");
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new IllegalStateException("Solo se puede corregir el método de un pedido pagado");
        }
        if (order.isInvoiced()) {
            throw new IllegalStateException("No se puede corregir el pago porque el pedido ya tiene CFDI");
        }

        String previousMethods = order.getPaymentMethodsDisplayWithAmounts();
        if (!order.hasMixedPaymentMethods() && order.getPaymentMethod() == newMethod) {
            throw new IllegalArgumentException("El pedido ya tiene registrado ese método de pago");
        }

        if (order.getPaymentTenders() != null) {
            order.getPaymentTenders().forEach(tender -> tender.setPaymentMethod(newMethod));
        }
        order.setPaymentMethod(newMethod);
        order.setUpdatedBy(changedBy);

        return saveCorrection(
                order,
                null,
                "Pedido completo",
                previousMethods,
                newMethod,
                order.getTotal(),
                reason,
                changedBy);
    }

    private PaymentMethodCorrection correctAccount(
            Order order,
            Long paymentId,
            PaymentMethodType newMethod,
            String reason,
            String changedBy,
            Company company) {
        Payment payment = paymentRepository.findByIdPaymentAndCompany(paymentId, company)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta de pago no encontrada"));
        if (payment.getOrder() == null || !Objects.equals(payment.getOrder().getIdOrder(), order.getIdOrder())) {
            throw new IllegalArgumentException("La cuenta de pago no pertenece a este pedido");
        }
        if (payment.getPaidAt() == null && order.getStatus() != OrderStatus.PAID) {
            throw new IllegalStateException("Solo se puede corregir una cuenta que ya fue pagada");
        }
        if (order.isInvoiced() || payment.isInvoiced()) {
            throw new IllegalStateException("No se puede corregir el pago porque ya tiene CFDI");
        }

        String previousMethods = payment.getPaymentMethodsDisplayWithAmounts();
        if (!payment.hasMixedPaymentMethods() && payment.getPaymentMethod() == newMethod) {
            throw new IllegalArgumentException("La cuenta ya tiene registrado ese método de pago");
        }

        if (payment.getPaymentTenders() != null) {
            payment.getPaymentTenders().forEach(tender -> tender.setPaymentMethod(newMethod));
        }
        payment.setPaymentMethod(newMethod);
        payment.setUpdatedBy(changedBy);

        String scopeLabel = payment.getPersonLabel() != null && !payment.getPersonLabel().isBlank()
                ? payment.getPersonLabel()
                : "Cuenta " + payment.getAccountNumber();
        return saveCorrection(
                order,
                payment,
                scopeLabel,
                previousMethods,
                newMethod,
                payment.getTotal(),
                reason,
                changedBy);
    }

    private PaymentMethodCorrection saveCorrection(
            Order order,
            Payment payment,
            String scopeLabel,
            String previousMethods,
            PaymentMethodType newMethod,
            BigDecimal amount,
            String reason,
            String changedBy) {
        PaymentMethodCorrection correction = new PaymentMethodCorrection();
        correction.setCompany(order.getCompany());
        correction.setOrder(order);
        correction.setPayment(payment);
        correction.setScopeLabel(scopeLabel);
        correction.setPreviousMethods(previousMethods);
        correction.setNewMethods(PaymentTenderSupport.displayWithAmounts(
                List.of(new PaymentTenderSupport.TenderLine(newMethod, amount))));
        correction.setReason(reason);
        correction.setChangedBy(changedBy);
        order.getPaymentMethodCorrections().add(correction);
        return correctionRepository.save(correction);
    }
}
