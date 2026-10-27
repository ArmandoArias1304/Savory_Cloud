package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Append-only movement of the loyalty ledger.
 *
 * <p>Never update a row: to correct a mistake write a {@link
 * LoyaltyStampEventType#ADJUST} or {@link LoyaltyStampEventType#REVERSE}
 * event. This is the audit trail that backs the customer's displayed balance.</p>
 */
@Entity
@Table(name = "loyalty_stamp_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_loyalty_event_order_type",
                        columnNames = {"order_id", "event_type"}),
                @UniqueConstraint(name = "uk_loyalty_event_redemption_type",
                        columnNames = {"redemption_id", "event_type"})
        },
        indexes = {
                @Index(name = "idx_loyalty_event_company", columnList = "company_id"),
                @Index(name = "idx_loyalty_event_account", columnList = "account_id"),
                @Index(name = "idx_loyalty_event_created", columnList = "created_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"company", "account", "order", "redemption"})
public class LoyaltyStampEvent implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private LoyaltyAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redemption_id")
    private LoyaltyRedemption redemption;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private LoyaltyStampEventType eventType;

    /** Signed movement: +1 for earned, -5 for redeemed. */
    @Column(name = "stamps", nullable = false)
    private Integer stamps;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Size(max = 255, message = "El motivo no puede exceder 255 caracteres")
    @Column(name = "reason", length = 255)
    private String reason;

    @Size(max = 100, message = "El usuario no puede exceder 100 caracteres")
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (stamps == null) {
            stamps = 0;
        }
        if (balanceAfter == null) {
            balanceAfter = 0;
        }
    }

    public boolean isPositive() {
        return stamps != null && stamps > 0;
    }
}
