package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Company entity representing a restaurant/business in the multi-tenant system.
 * Each company has its own configuration, license, employees, menu, orders, etc.
 */
@Entity
@Table(name = "companies", indexes = {
    @Index(name = "idx_company_slug", columnList = "slug"),
    @Index(name = "idx_company_domain", columnList = "custom_domain")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idCompany"})
@ToString(exclude = {"systemConfiguration", "systemLicense", "facturamaConfig"})
public class Company implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_company")
    private Long idCompany;

    /**
     * Unique slug for URL identification (subdomain)
     * Example: "pizzamax" for pizzamax.misistema.com
     * Also allows IP addresses for local network testing (e.g., 192.168.1.76)
     */
    @NotBlank(message = "El slug es requerido")
    @Size(min = 3, max = 50, message = "El slug debe tener entre 3 y 50 caracteres")
    @Pattern(regexp = "^[a-z0-9.-]+$", message = "El slug solo puede contener letras minúsculas, números, guiones y puntos")
    @Column(name = "slug", nullable = false, unique = true, length = 50)
    private String slug;

    /**
     * Custom domain (optional)
     * Example: "www.pizzamax.com"
     */
    @Size(max = 200, message = "El dominio no puede exceder 200 caracteres")
    @Column(name = "custom_domain", unique = true, length = 200)
    private String customDomain;

    /**
     * Business/Commercial name
     */
    @NotBlank(message = "El nombre de la empresa es requerido")
    @Size(min = 2, max = 150, message = "El nombre debe tener entre 2 y 150 caracteres")
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /**
     * Tax ID (RFC in Mexico)
     */
    @Size(max = 20, message = "El RFC no puede exceder 20 caracteres")
    @Column(name = "rfc", length = 20)
    private String rfc;

    /**
     * Email sender registered in SendGrid for this company.
     * Optional — falls back to the platform default (mail.from.email) when not set.
     */
    @Email(message = "Formato de email inválido")
    @Size(max = 200, message = "El email no puede exceder 200 caracteres")
    @Column(name = "sender_email", length = 200)
    private String senderEmail;

    /**
     * Name that appears in emails
     */
    @Size(max = 100, message = "El nombre del remitente no puede exceder 100 caracteres")
    @Column(name = "sender_name", length = 100)
    private String senderName;

    /**
     * Contact email for the company (different from senderEmail)
     */
    @Email(message = "Formato de email inválido")
    @Size(max = 200, message = "El email no puede exceder 200 caracteres")
    @Column(name = "contact_email", length = 200)
    private String contactEmail;

    /**
     * Contact phone number
     */
    @Size(max = 20, message = "El teléfono no puede exceder 20 caracteres")
    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    /**
     * Company physical address
     */
    @Size(max = 500, message = "La dirección no puede exceder 500 caracteres")
    @Column(name = "address", length = 500)
    private String address;

    /**
     * Company status
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Timezone for this company (IANA timezone ID).
     * Dates stored in DB are UTC; this zone is used for display and business-hours logic.
     * Defaults to America/Mexico_City (CST, UTC-6).
     */
    @Column(name = "timezone", nullable = false, length = 50)
    @Builder.Default
    private String timezone = "America/Mexico_City";

    // ========== Timestamps ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ========== Relationships (One-to-One, owned by child entities) ==========

    @OneToOne(mappedBy = "company", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private SystemConfiguration systemConfiguration;

    @OneToOne(mappedBy = "company", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private SystemLicense systemLicense;

    @OneToOne(mappedBy = "company", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private FacturamaConfig facturamaConfig;

    // NOTE: BackupConfiguration is now GLOBAL (not per-company)
    // See BackupService.getOrCreateGlobalConfiguration()

    // ========== Lifecycle Callbacks ==========

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========== Business Methods ==========

    /**
     * Check if company is active
     */
    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    /**
     * Get display name (for emails and UI)
     */
    public String getDisplayName() {
        return senderName != null && !senderName.isEmpty() ? senderName : name;
    }

    /**
     * Check if company has a custom domain
     */
    public boolean hasCustomDomain() {
        return customDomain != null && !customDomain.isEmpty();
    }
}
