package com.aatechsolutions.elgransazon.infrastructure.scheduler;

import com.aatechsolutions.elgransazon.application.service.LicenseService;
import com.aatechsolutions.elgransazon.domain.entity.SystemLicense;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job to check license status daily
 * Runs every day at 9:00 AM
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LicenseCheckJob {

    private final LicenseService licenseService;

    /**
     * Check license status daily at 9:00 AM
     * Cron expression: second minute hour day month weekday
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void checkLicenseStatus() {
        log.info("Starting daily license check...");

        try {
            SystemLicense license = licenseService.getLicense();

            if (license == null) {
                log.warn("No license found in the system!");
                return;
            }

            // Update last check date
            licenseService.updateLastCheck();

            // Check if expired
            if (license.isExpired() && license.getStatus() == SystemLicense.LicenseStatus.ACTIVE) {
                log.warn("License has expired! Marking as expired.");
                licenseService.markAsExpired();
                
                // TODO: Send expiration notification email
                log.info("Expiration notification should be sent to: {}", license.getOwnerEmail());
            }

            // Check if needs notification
            if (license.needsNotification()) {
                long daysLeft = license.daysUntilExpiration();
                log.info("License needs notification. Days left: {}", daysLeft);

                licenseService.updateLastNotification();
                
                // TODO: Send warning notification email
                log.info("Warning notification should be sent to: {}", license.getOwnerEmail());
            }

            log.info("License check completed. Status: {}, Days left: {}", 
                license.getStatus(), license.daysUntilExpiration());

        } catch (Exception e) {
            log.error("Error during license check", e);
        }
    }

    /**
     * Manual trigger for testing (can be removed in production)
     * This method can be called from a controller for testing
     */
    public void manualCheck() {
        log.info("Manual license check triggered");
        checkLicenseStatus();
    }
}
