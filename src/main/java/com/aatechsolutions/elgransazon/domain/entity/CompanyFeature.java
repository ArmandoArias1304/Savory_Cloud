package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Add-on contracted by a restaurant, independent from its main license.
 *
 * <p>This is what lets the programmer sell loyalty monthly to one company and
 * remove it later without changing the company's package or deleting any
 * customer data.</p>
 */
@Entity
@Table(name = "company_features",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_company_feature_key",
                        columnNames = {"company_id", "feature_key"})
        },
        indexes = {
                @Index(name = "idx_company_feature_company", columnList = "company_id"),
                @Index(name = "idx_company_feature_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"company"})
public class CompanyFeature implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_key", nullable = false, length = 40)
    private FeatureKey featureKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CompanyFeatureStatus status = CompanyFeatureStatus.DISABLED;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
    @Column(name = "price", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Size(max = 3)
    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "MXN";

    @Size(max = 20)
    @Column(name = "billing_cycle", length = 20)
    @Builder.Default
    private String billingCycle = "MONTHLY";

    @Size(max = 255)
    @Column(name = "notes", length = 255)
    private String notes;

    @Size(max = 100)
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Size(max = 100)
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

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
        if (status == null) {
            status = CompanyFeatureStatus.DISABLED;
        }
        if (price == null) {
            price = BigDecimal.ZERO;
        }
        if (currency == null || currency.isBlank()) {
            currency = "MXN";
        }
        if (billingCycle == null || billingCycle.isBlank()) {
            billingCycle = "MONTHLY";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        if (status != CompanyFeatureStatus.ENABLED && status != CompanyFeatureStatus.TRIAL) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (startsAt != null && startsAt.isAfter(now)) {
            return false;
        }
        return endsAt == null || endsAt.isAfter(now);
    }

    public boolean isExpired() {
        return status == CompanyFeatureStatus.EXPIRED
                || (endsAt != null && !endsAt.isAfter(LocalDateTime.now()));
    }

    public long getDaysRemaining() {
        if (endsAt == null) {
            return -1;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), endsAt.toLocalDate());
    }

    public String getStatusDisplayName() {
        return status != null ? status.getDisplayName() : "Desconocido";
    }

    public String getBillingCycleDisplayName() {
        return "MONTHLY".equalsIgnoreCase(billingCycle) ? "Mensual" : "Único";
    }
}
