package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import com.aatechsolutions.elgransazon.infrastructure.util.CompanyLocalTime;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * System License entity
 * Represents the license information for a company (Multi-Tenant)
 * Each company has exactly one SystemLicense (OneToOne relationship)
 */
@Entity
@Table(name = "system_license")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"company"})
public class SystemLicense implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== Company Relationship (Multi-Tenant) ==========
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Column(name = "license_key", nullable = false, unique = true)
    private String licenseKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", nullable = false)
    private PackageType packageType;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false)
    private BillingCycle billingCycle;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Column(name = "installation_date", nullable = false)
    private LocalDate installationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private LicenseStatus status = LicenseStatus.ACTIVE;

    // Client information
    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "owner_email")
    private String ownerEmail;

    @Column(name = "owner_phone", length = 20)
    private String ownerPhone;

    @Column(name = "owner_rfc", length = 50)
    private String ownerRfc;

    @Column(name = "restaurant_name")
    private String restaurantName;

    // Limits
    @Column(name = "max_users")
    @Builder.Default
    private Integer maxUsers = 5;

    @Column(name = "max_branches")
    @Builder.Default
    private Integer maxBranches = 1;

    // Technical information
    @Column(name = "version", length = 50)
    @Builder.Default
    private String version = "1.0.0";

    @Column(name = "last_check_date")
    private LocalDate lastCheckDate;

    @Column(name = "last_notification_sent")
    private LocalDate lastNotificationSent;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDate createdAt;

    @Column(name = "updated_at")
    private LocalDate updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDate.now();
        updatedAt = LocalDate.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDate.now();
    }

    /**
     * Check if the license is expired
     * Returns true if expiration date has passed OR is today
     */
    public boolean isExpired() {
        return !expirationDate.isAfter(CompanyLocalTime.today());
    }

    /**
     * Get days until expiration
     * Returns negative number if already expired
     */
    public long daysUntilExpiration() {
        return ChronoUnit.DAYS.between(CompanyLocalTime.today(), expirationDate);
    }

    /**
     * Get days since installation
     */
    public long daysActive() {
        return ChronoUnit.DAYS.between(installationDate, CompanyLocalTime.today());
    }

    /**
     * Check if license needs notification
     */
    public boolean needsNotification() {
        long daysLeft = daysUntilExpiration();
        LocalDate today = CompanyLocalTime.today();

        // Don't notify if already notified today
        if (lastNotificationSent != null && lastNotificationSent.equals(today)) {
            return false;
        }

        // Monthly: notify 5 days before
        if (billingCycle == BillingCycle.MONTHLY && daysLeft <= 5 && daysLeft >= 0) {
            return true;
        }

        // Annual: notify 30 days before
        if (billingCycle == BillingCycle.ANNUAL && daysLeft <= 30 && daysLeft >= 0) {
            return true;
        }

        // Expired (day 0 or after)
        return daysLeft <= 0;
    }

    /**
     * Check if it's a monthly license
     */
    public boolean isMonthly() {
        return billingCycle == BillingCycle.MONTHLY;
    }

    /**
     * Check if it's an annual license
     */
    public boolean isAnnual() {
        return billingCycle == BillingCycle.ANNUAL;
    }

    /**
     * Get package name in Spanish
     */
    public String getPackageDisplayName() {
        return packageType.getDisplayName();
    }

    /**
     * Get billing cycle name in Spanish
     */
    public String getBillingCycleDisplayName() {
        return billingCycle.getDisplayName();
    }

    /**
     * Get status name in Spanish
     */
    public String getStatusDisplayName() {
        return status.getDisplayName();
    }

    /**
     * Check if there's a user limit
     */
    public boolean hasUserLimit() {
        return maxUsers != null;
    }

    /**
     * Get max users display text
     */
    public String getMaxUsersDisplay() {
        return maxUsers != null ? maxUsers.toString() : "Sin límite";
    }

    /**
     * Package type enum
     */
    public enum PackageType {
        BASIC("Básico - Gestión Interna"),
        WEB("Web - Gestión + Presencia Web"),
        ECOMMERCE("E-Commerce Total");

        private final String displayName;

        PackageType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Billing cycle enum
     */
    public enum BillingCycle {
        MONTHLY("Mensual"),
        ANNUAL("Anual");

        private final String displayName;

        BillingCycle(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * License status enum
     */
    public enum LicenseStatus {
        ACTIVE("Activa"),
        EXPIRED("Expirada"),
        TRIAL("Prueba"),
        SUSPENDED("Suspendida");

        private final String displayName;

        LicenseStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
