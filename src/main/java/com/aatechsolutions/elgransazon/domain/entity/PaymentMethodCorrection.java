package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_method_corrections", indexes = {
        @Index(name = "idx_payment_method_correction_order", columnList = "id_order")
})
@Getter
@Setter
@NoArgsConstructor
public class PaymentMethodCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_payment_method_correction")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_order", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_payment")
    private Payment payment;

    @NotBlank
    @Size(max = 120)
    @Column(name = "scope_label", nullable = false, length = 120)
    private String scopeLabel;

    @NotBlank
    @Size(max = 500)
    @Column(name = "previous_methods", nullable = false, length = 500)
    private String previousMethods;

    @NotBlank
    @Size(max = 500)
    @Column(name = "new_methods", nullable = false, length = 500)
    private String newMethods;

    @NotBlank
    @Size(max = 500)
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @NotBlank
    @Size(max = 100)
    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @NotNull
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt = LocalDateTime.now();
}
