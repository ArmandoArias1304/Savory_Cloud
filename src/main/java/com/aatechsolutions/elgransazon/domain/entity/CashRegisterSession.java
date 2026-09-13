package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A cashier's cash-register session (apertura / cierre de caja).
 *
 * One session per cashier per working period: the cashier opens the drawer with
 * an initial amount (fondo), records every manual payment / movement during the
 * day and closes it at the end of the day with the counted cash. Every session
 * belongs to a company (multi-tenant) and to the cashier who opened it.
 */
@Entity
@Table(name = "cash_register_sessions",
        indexes = {
                @Index(name = "idx_cash_register_company_cashier", columnList = "company_id, id_cashier"),
                @Index(name = "idx_cash_register_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"company", "cashier", "closedBy"})
public class CashRegisterSession implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ========== Company Relationship (Multi-Tenant) ==========

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** Cashier who opened (and owns) the drawer. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cashier", nullable = false)
    private Employee cashier;

    /** Employee who closed the drawer (usually the same cashier). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_closed_by", nullable = true)
    private Employee closedBy;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CashRegisterStatus status = CashRegisterStatus.OPEN;

    /** UTC timestamp when the drawer was opened. */
    @NotNull
    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    /** UTC timestamp when the drawer was closed (null while open). */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** Cash the drawer started with (fondo de caja). */
    @NotNull
    @Column(name = "initial_amount", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal initialAmount = BigDecimal.ZERO;

    /** Physical cash counted by the cashier when closing (null while open). */
    @Column(name = "counted_amount", precision = 10, scale = 2)
    private BigDecimal countedAmount;

    /** Notes captured when opening the drawer. */
    @Size(max = 500)
    @Column(name = "notes", length = 500)
    private String notes;

    /** Notes captured when closing the drawer. */
    @Size(max = 500)
    @Column(name = "closing_notes", length = 500)
    private String closingNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // ========== Lifecycle Callbacks ==========

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.openedAt == null) {
            this.openedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========== Business Methods ==========

    public boolean isOpen() {
        return status == CashRegisterStatus.OPEN;
    }
}
