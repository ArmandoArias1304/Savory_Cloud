package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.application.service.GlobalSystemConfigService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Global Controller Advice para añadir atributos comunes a todas las vistas.
 * 
 * Provides two configuration objects to all templates:
 * 1. globalSystemConfig - GLOBAL branding (system name, slogan, logo) - same for all companies
 * 2. systemConfig - Per-company configuration (restaurant name, address, phone, etc.)
 * 
 * MULTI-TENANT: 
 * - globalSystemConfig is shared across all tenants (SavoryCloud branding)
 * - systemConfig is specific to each company/tenant
 */
@ControllerAdvice
public class GlobalControllerAdvice {

    @Autowired
    private SystemConfigurationService systemConfigurationService;

    @Autowired
    private GlobalSystemConfigService globalSystemConfigService;

    /**
     * Añade la configuración GLOBAL del sistema a todas las vistas.
     * Esta configuración es la misma para todas las empresas (branding del sistema).
     * 
     * Uso en templates: ${globalSystemConfig.systemName}, ${globalSystemConfig.systemLogoUrl}
     * 
     * @return GlobalSystemConfig (never null - defaults provided)
     */
    @ModelAttribute("globalSystemConfig")
    public GlobalSystemConfig addGlobalSystemConfiguration() {
        GlobalSystemConfig config = globalSystemConfigService.getConfiguration();
        if (config != null) {
            return config;
        }
        
        // Return a transient default if not initialized (should not happen normally)
        return GlobalSystemConfig.builder()
            .id(1L)
            .systemName("SavoryCloud")
            .systemSlogan("Sistema de Gestión Restaurantera")
            .build();
    }

    /**
     * Añade la configuración PER-COMPANY a todas las vistas.
     * Esta configuración es específica para cada empresa/tenant.
     * 
     * Uso en templates: ${systemConfig.restaurantName}, ${systemConfig.phone}, etc.
     * 
     * MULTI-TENANT: Returns the company's config or null when no company context.
     * When null, templates should use globalSystemConfig for branding or show nothing.
     */
    @ModelAttribute("systemConfig")
    public SystemConfiguration addSystemConfiguration() {
        // Returns null when no company context - templates should handle null gracefully
        return systemConfigurationService.getConfiguration();
    }
}
