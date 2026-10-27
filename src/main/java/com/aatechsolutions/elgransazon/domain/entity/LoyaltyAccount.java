package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Stamp balance of one customer at one restaurant.
 *
 * <p>The authoritative history is {@link LoyaltyStampEvent}; this row keeps a
 * cached balance for fast screen rendering. Every mutation is performed while
 * holding a pessimistic lock or the JPA version, and the service also verifies
 * that the balance never goes negative.</p>
 */
@Entity
@Table(name = "loyalty_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_loyalty_account_company_customer",
                        columnNames = {"company_id", "customer_id"})
        },
        indexes = {
                @Index(name = "idx_loyalty_account_company", columnList = "company_id"),
                @Index(name = "idx_loyalty_account_customer", columnList = "customer_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"company", "customer"})
public class LoyaltyAccount implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "balance", nullable = false)
    @Builder.Default
    private Integer balance = 0;

    @Column(name = "lifetime_earned", nullable = false)
    @Builder.Default
    private Integer lifetimeEarned = 0;

    @Column(name = "lifetime_redeemed", nullable = false)
    @Builder.Default
    private Integer lifetimeRedeemed = 0;

    @Column(name = "last_earned_at")
    private LocalDateTime lastEarnedAt;

    @Column(name = "last_redeemed_at")
    private LocalDateTime lastRedeemedAt;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (balance == null || balance < 0) {
            balance = 0;
        }
        if (lifetimeEarned == null) {
            lifetimeEarned = 0;
        }
        if (lifetimeRedeemed == null) {
            lifetimeRedeemed = 0;
        }
        if (active == null) {
            active = true;
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (balance == null || balance < 0) {
            balance = 0;
        }
    }

    public void earn(int stamps) {
        if (stamps <= 0) {
            return;
        }
        this.balance = safeBalance() + stamps;
        this.lifetimeEarned = safeLifetimeEarned() + stamps;
        this.lastEarnedAt = LocalDateTime.now();
    }

    public void redeem(int stamps) {
        if (stamps <= 0) {
            throw new IllegalArgumentException("Los sellos a canjear deben ser mayores a cero");
        }
        if (safeBalance() < stamps) {
            throw new IllegalStateException("El cliente no tiene sellos suficientes");
        }
        this.balance = safeBalance() - stamps;
        this.lifetimeRedeemed = safeLifetimeRedeemed() + stamps;
        this.lastRedeemedAt = LocalDateTime.now();
    }

    public void restore(int stamps) {
        if (stamps <= 0) {
            return;
        }
        this.balance = safeBalance() + stamps;
        this.lifetimeRedeemed = Math.max(0, safeLifetimeRedeemed() - stamps);
    }

    public int safeBalance() {
        return balance != null ? balance : 0;
    }

    public int safeLifetimeEarned() {
        return lifetimeEarned != null ? lifetimeEarned : 0;
    }

    public int safeLifetimeRedeemed() {
        return lifetimeRedeemed != null ? lifetimeRedeemed : 0;
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }
}
