package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * One portion of an order or split-account paid with a single payment method.
 *
 * A regular (non-split) order stores its tenders with {@code payment == null}.
 * A split/departing-guest account stores its tenders on the {@link Payment}
 * row ({@code payment != null}); those rows still keep {@code order} for
 * tenant queries and reporting.
 *
 * The sum of tenders of a collection must equal the collected total
 * (without tip). Legacy paid rows without tenders are treated as a single
 * tender of {@code paymentMethod} + {@code total}.
 */
@Entity
@Table(name = "payment_tenders", indexes = {
        @Index(name = "idx_tender_order", columnList = "id_order"),
        @Index(name = "idx_tender_payment", columnList = "id_payment"),
        @Index(name = "idx_tender_method", columnList = "payment_method")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"company", "order", "payment"})
public class PaymentTender implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_payment_tender")
    private Long idPaymentTender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_order", nullable = false)
    private Order order;

    /**
     * Set when this tender belongs to a split/departing-guest account.
     * Null for a whole-order (non-split) collection.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_payment")
    private Payment payment;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethodType paymentMethod;

    /**
     * Portion of the collected total (without tip) paid with this method.
     */
    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;
}
