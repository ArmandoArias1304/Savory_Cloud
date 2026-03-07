package com.aatechsolutions.elgransazon.infrastructure.init;

import com.aatechsolutions.elgransazon.application.service.GlobalSystemConfigService;
import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Global System Config Initializer
 * Creates the singleton GlobalSystemConfig record if it doesn't exist.
 * 
 * Runs early (Order 0) to ensure global config is available before
 * other initializers and services that might need it.
 * 
 * MULTI-TENANT: This is a GLOBAL configuration, not per-company.
 */
@Component
@Order(0) // Run first, before other initializers
@RequiredArgsConstructor
@Slf4j
public class GlobalSystemConfigInitializer implements CommandLineRunner {

    private final GlobalSystemConfigService globalSystemConfigService;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("=== Global System Configuration Check ===");
        
        if (globalSystemConfigService.configurationExists()) {
            GlobalSystemConfig config = globalSystemConfigService.getConfiguration();
            log.info("✓ Global system config exists: {} - {}", 
                config.getSystemName(), config.getSystemSlogan());
        } else {
            log.info("→ Creating default global system configuration...");
            GlobalSystemConfig config = globalSystemConfigService.initializeDefault();
            log.info("✓ Global system config created: {} - {}", 
                config.getSystemName(), config.getSystemSlogan());
        }
    }
}
