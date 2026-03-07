package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;

/**
 * Service interface for GlobalSystemConfig business logic.
 * Manages the GLOBAL system branding configuration (singleton).
 * 
 * This configuration is NOT per-company, it's shared across all tenants.
 * Only PROGRAMMER role should be able to modify these settings.
 */
public interface GlobalSystemConfigService {

    /**
     * Get the global system configuration.
     * Returns the singleton configuration or creates a default one if not exists.
     * @return GlobalSystemConfig (never null)
     */
    GlobalSystemConfig getConfiguration();

    /**
     * Update the global system configuration.
     * @param config the updated configuration
     * @return the saved configuration
     */
    GlobalSystemConfig updateConfiguration(GlobalSystemConfig config);

    /**
     * Update only the system branding fields (name, slogan).
     * @param systemName new system name
     * @param systemSlogan new system slogan
     * @return the updated configuration
     */
    GlobalSystemConfig updateBranding(String systemName, String systemSlogan);

    /**
     * Update the system logo URL.
     * @param logoUrl new logo URL (Cloudinary)
     * @return the updated configuration
     */
    GlobalSystemConfig updateLogoUrl(String logoUrl);

    /**
     * Check if the global configuration exists.
     * @return true if exists
     */
    boolean configurationExists();

    /**
     * Initialize the default global configuration.
     * Called by GlobalSystemConfigInitializer at startup.
     * @return the created configuration
     */
    GlobalSystemConfig initializeDefault();
}
