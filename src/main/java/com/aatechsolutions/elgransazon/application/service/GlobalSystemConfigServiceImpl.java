package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.repository.GlobalSystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of GlobalSystemConfigService.
 * Manages the GLOBAL system branding configuration (singleton, id=1).
 * 
 * This service does NOT depend on CompanyContext since the configuration
 * is global across all tenants.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GlobalSystemConfigServiceImpl implements GlobalSystemConfigService {

    private final GlobalSystemConfigRepository repository;

    // In-memory cache for performance (reduces DB queries)
    private GlobalSystemConfig cachedConfig;
    private long cacheTimestamp = 0;
    private static final long CACHE_TTL_MS = 60_000; // 1 minute

    @Override
    @Transactional(readOnly = true)
    public GlobalSystemConfig getConfiguration() {
        long now = System.currentTimeMillis();
        
        // Check cache
        if (cachedConfig != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
            return cachedConfig;
        }
        
        GlobalSystemConfig config = repository.getConfiguration();
        
        if (config == null) {
            log.warn("Global system configuration not found. Creating default...");
            config = initializeDefault();
        }
        
        // Update cache
        cachedConfig = config;
        cacheTimestamp = now;
        
        return config;
    }

    @Override
    public GlobalSystemConfig updateConfiguration(GlobalSystemConfig config) {
        log.info("Updating global system configuration");
        
        // Ensure ID is always 1 (singleton)
        config.setId(1L);
        
        GlobalSystemConfig saved = repository.save(config);
        
        // Invalidate cache
        invalidateCache();
        
        return saved;
    }

    @Override
    public GlobalSystemConfig updateBranding(String systemName, String systemSlogan) {
        log.info("Updating global system branding: name={}, slogan={}", systemName, systemSlogan);
        
        GlobalSystemConfig config = getConfiguration();
        
        if (systemName != null && !systemName.isBlank()) {
            config.setSystemName(systemName);
        }
        if (systemSlogan != null) {
            config.setSystemSlogan(systemSlogan);
        }
        
        GlobalSystemConfig saved = repository.save(config);
        invalidateCache();
        
        return saved;
    }

    @Override
    public GlobalSystemConfig updateLogoUrl(String logoUrl) {
        log.info("Updating global system logo URL");
        
        GlobalSystemConfig config = getConfiguration();
        config.setSystemLogoUrl(logoUrl);
        
        GlobalSystemConfig saved = repository.save(config);
        invalidateCache();
        
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean configurationExists() {
        return repository.configurationExists();
    }

    @Override
    public GlobalSystemConfig initializeDefault() {
        if (repository.configurationExists()) {
            log.info("Global system configuration already exists");
            return repository.getConfiguration();
        }
        
        log.info("Creating default global system configuration");
        
        GlobalSystemConfig config = GlobalSystemConfig.builder()
            .id(1L)
            .systemName("SavoryCloud")
            .systemSlogan("Sistema de Gestión Restaurantera")
            .build();
        
        GlobalSystemConfig saved = repository.save(config);
        invalidateCache();
        
        return saved;
    }

    /**
     * Invalidate the in-memory cache.
     * Call this after any modification.
     */
    private void invalidateCache() {
        cachedConfig = null;
        cacheTimestamp = 0;
    }
}
