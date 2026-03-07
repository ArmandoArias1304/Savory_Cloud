package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;

/**
 * GlobalSystemConfig entity representing GLOBAL system branding settings.
 * This is a SINGLETON entity (only one record with id=1) that stores
 * system-wide branding information shared across all companies.
 * 
 * MULTI-TENANT: This configuration is NOT per-company, it's global for the entire platform.
 * 
 * Fields:
 * - systemName: Name of the system/platform (e.g., "SavoryCloud")
 * - systemSlogan: Slogan/tagline (e.g., "Sistema de Gestión Restaurantera")
 * - systemLogoUrl: URL to the system logo stored in Cloudinary
 */
@Entity
@Table(name = "global_system_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString
public class GlobalSystemConfig implements Serializable {

    /**
     * Fixed ID = 1 for singleton pattern.
     * This entity should only have ONE record.
     */
    @Id
    @Column(name = "id")
    @Builder.Default
    private Long id = 1L;

    /**
     * Name of the system/platform displayed in UI.
     * Example: "SavoryCloud", "El Gran Sazón"
     */
    @Size(min = 2, max = 100, message = "System name must be between 2 and 100 characters")
    @Column(name = "system_name", length = 100, nullable = false)
    @Builder.Default
    private String systemName = "SavoryCloud";

    /**
     * Slogan/tagline displayed under the system name.
     * Example: "Sistema de Gestión Restaurantera"
     */
    @Size(max = 255, message = "System slogan cannot exceed 255 characters")
    @Column(name = "system_slogan", length = 255)
    @Builder.Default
    private String systemSlogan = "Sistema de Gestión Restaurantera";

    /**
     * URL to the system logo image stored in Cloudinary.
     * Used for favicon, sidebar logo, login pages, etc.
     */
    @Size(max = 500, message = "System logo URL cannot exceed 500 characters")
    @Column(name = "system_logo_url", length = 500)
    private String systemLogoUrl;

    /**
     * Ensure the ID is always 1 (singleton pattern)
     */
    @PrePersist
    @PreUpdate
    private void ensureSingletonId() {
        this.id = 1L;
    }
}
