package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * One use of a reward by a customer.
 *
 * <p>Stamps are deducted when the redemption is created ({@code PENDING}) so
 * two terminals cannot spend the same balance. If the order is abandoned or
 * cancelled before payment, {@link #status} becomes {@code CANCELLED} and the
 * stamps are returned through a reversal event.</p>
 *
 * <p>One order can carry several rows: staff can redeem more than one reward
 * in the same basket, and every row keeps its own reward, quantity and stamp
 * cost so the audit trail stays readable. The stamps are deducted once for the
 * whole basket.</p>
 */
@Entity
@Table(name = "loyalty_redemptions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_loyalty_redemption_token", columnNames = {"quote_token"})
        },
        indexes = {
                @Index(name = "idx_loyalty_redemption_company", columnList = "company_id"),
                @Index(name = "idx_loyalty_redemption_account", columnList = "account_id"),
                @Index(name = "idx_loyalty_redemption_status", columnList = "status"),
                @Index(name = "idx_loyalty_redemption_order", columnList = "order_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"company", "account", "reward"})
public class LoyaltyRedemption implements Serializable {

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
    @JoinColumn(name = "reward_id", nullable = false)
    private LoyaltyReward reward;

    @Min(value = 1, message = "El canje debe usar al menos un sello")
    @Column(name = "stamps_spent", nullable = false)
    private Integer stampsSpent;

    /** How many units of the reward were redeemed in this order. */
    @Min(value = 1, message = "La cantidad de recompensas debe ser al menos 1")
    @Column(name = "reward_quantity", nullable = false)
    @Builder.Default
    private Integer rewardQuantity = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LoyaltyRedemptionStatus status = LoyaltyRedemptionStatus.PENDING;

    /**
     * Plain order id, not a JPA relationship, to keep the FK on the order side
     * and avoid a circular mapping. An order can carry one row per redeemed
     * reward (multi-item basket), so this column is indexed but not unique.
     */
    @Column(name = "order_id")
    private Long orderId;

    @Size(max = 64)
    @Column(name = "quote_token", nullable = false, unique = true, length = 64)
    private String quoteToken;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Size(max = 100)
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Size(max = 100)
    @Column(name = "applied_by", length = 100)
    private String appliedBy;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Size(max = 255)
    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (expiresAt == null) {
            expiresAt = LocalDateTime.now().plusMinutes(15);
        }
        if (status == null) {
            status = LoyaltyRedemptionStatus.PENDING;
        }
        if (rewardQuantity == null || rewardQuantity < 1) {
            rewardQuantity = 1;
        }
    }

    public int safeRewardQuantity() {
        return rewardQuantity != null && rewardQuantity > 0 ? rewardQuantity : 1;
    }

    public boolean isPending() {
        return status == LoyaltyRedemptionStatus.PENDING;
    }

    public boolean isApplied() {
        return status == LoyaltyRedemptionStatus.APPLIED;
    }

    public boolean isCancelled() {
        return status == LoyaltyRedemptionStatus.CANCELLED;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean canBeApplied() {
        return isPending() && !isExpired();
    }

    public void markApplied(Long orderId, String username) {
        this.orderId = orderId;
        this.status = LoyaltyRedemptionStatus.APPLIED;
        this.appliedBy = username;
        this.appliedAt = LocalDateTime.now();
    }

    public void cancel(String reason) {
        this.status = LoyaltyRedemptionStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancelReason = reason;
    }
}
