package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * FacturamaConfig entity storing Facturama integration settings per company.
 * Each company can optionally enable electronic invoicing (facturación electrónica)
 * through Facturama's API Multiemisor + our custom Autofactura page.
 *
 * SECURITY:
 * - CSD files (.cer, .key) and passwords are NEVER stored in this system.
 *   They are forwarded directly to Facturama API and discarded.
 * - Facturama credentials (user/password) are stored as environment variables.
 * - Only company-specific fiscal data is stored here.
 */
@Entity
@Table(name = "facturama_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"company"})
public class FacturamaConfig implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ========== Company Relationship (Multi-Tenant) ==========
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    // ========== Facturama Issuer Data (per company/RFC) ==========

    /**
     * RFC of the issuer (extracted from CSD certificate).
     * Used in the Issuer node of every CFDI created for this company.
     */
    @Size(max = 13, message = "El RFC no puede exceder 13 caracteres")
    @Column(name = "rfc", length = 13)
    private String rfc;

    /**
     * Legal name (razón social) as registered with the SAT.
     */
    @Size(max = 300, message = "La razón social no puede exceder 300 caracteres")
    @Column(name = "legal_name", length = 300)
    private String legalName;

    /**
     * SAT fiscal regime code (régimen fiscal), e.g. "601", "612".
     */
    @Size(max = 5, message = "El régimen fiscal no puede exceder 5 caracteres")
    @Column(name = "fiscal_regime", length = 5)
    private String fiscalRegime;

    /**
     * Fiscal zip code (código postal de expedición) from which invoices are issued.
     */
    @Size(max = 5, message = "El código postal no puede exceder 5 caracteres")
    @Column(name = "expedition_place", length = 5)
    private String expeditionPlace;

    // ========== Configuration ==========

    /**
     * Whether the Facturama integration is enabled for this company.
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    /**
     * Whether to use the live environment (true) or sandbox (false).
     */
    @Column(name = "live_mode", nullable = false)
    @Builder.Default
    private Boolean liveMode = false;

    /**
     * Whether CSD certificates have been uploaded to Facturama for this RFC.
     */
    @Column(name = "csd_uploaded", nullable = false)
    @Builder.Default
    private Boolean csdUploaded = false;

    /**
     * Whether the legal/fiscal data has been configured.
     */
    @Column(name = "legal_data_configured", nullable = false)
    @Builder.Default
    private Boolean legalDataConfigured = false;

    // ========== Timestamps ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========== Business Methods ==========

    /**
     * Check if the integration is fully configured and ready to create autofactura links.
     */
    public boolean isReady() {
        return enabled
                && csdUploaded
                && legalDataConfigured
                && rfc != null && !rfc.isBlank()
                && legalName != null && !legalName.isBlank()
                && fiscalRegime != null && !fiscalRegime.isBlank()
                && expeditionPlace != null && !expeditionPlace.isBlank();
    }
}
