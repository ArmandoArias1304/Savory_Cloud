package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A manual cash-register movement inside a {@link CashRegisterSession}.
 *
 * Examples: "Hielo" ($20) as an EXPENSE, an extra cash injection as INCOME or a
 * safe drop as WITHDRAWAL. Amounts are always stored positive; the type decides
 * whether the money leaves or enters the drawer.
 */
@Entity
@Table(name = "cash_register_movements",
        indexes = {
                @Index(name = "idx_cash_register_mov_session", columnList = "session_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"company", "session"})
public class CashRegisterMovement implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ========== Company Relationship (Multi-Tenant) ==========

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private CashRegisterSession session;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private CashRegisterMovementType type;

    /** What the money was for, e.g. "Hielo", "Gas", "Retiro a bóveda". */
    @NotBlank
    @Size(max = 150)
    @Column(name = "concept", nullable = false, length = 150)
    private String concept;

    /** Always a positive amount; the type decides the direction. */
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false, message = "El monto debe ser mayor a 0")
    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Size(max = 500)
    @Column(name = "notes", length = 500)
    private String notes;

    /** UTC timestamp when the movement happened. */
    @NotNull
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.occurredAt == null) {
            this.occurredAt = LocalDateTime.now();
        }
    }
}
