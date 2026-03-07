package com.aatechsolutions.elgransazon.infrastructure.init;

import com.aatechsolutions.elgransazon.domain.repository.SystemLicenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * License Initializer
 * MULTI-TENANT: Licenses are now created per-company via CompanyService.create()
 * This initializer is kept for backward compatibility but does NOT create licenses automatically
 * 
 * To set up the system:
 * 1. Run the application
 * 2. Login as PROGRAMMER
 * 3. Create a Company (which will automatically create its license)
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class LicenseInitializer implements CommandLineRunner {

    private final SystemLicenseRepository licenseRepository;

    @Override
    @Transactional(readOnly = true)
    public void run(String... args) {
        log.info("=== License Check ===");
        
        long licenseCount = licenseRepository.count();
        
        if (licenseCount > 0) {
            log.info("Found {} license(s) in the system.", licenseCount);
        } else {
            log.warn("No licenses found in the system.");
            log.info("MULTI-TENANT: Licenses are created per-company.");
            log.info("To create a license:");
            log.info("  1. Login as PROGRAMMER");
            log.info("  2. Go to Programmer Dashboard");
            log.info("  3. Create a Company (license is created automatically)");
            log.info("  OR use CompanyService.create() programmatically");
        }
    }
}
