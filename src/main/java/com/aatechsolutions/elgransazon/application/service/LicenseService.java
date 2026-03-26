package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.LicenseEvent;
import com.aatechsolutions.elgransazon.domain.entity.SystemLicense;
import com.aatechsolutions.elgransazon.domain.entity.SystemLicense.LicenseStatus;
import com.aatechsolutions.elgransazon.domain.repository.LicenseEventRepository;
import com.aatechsolutions.elgransazon.domain.repository.SystemLicenseRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing system licenses
 * MULTI-TENANT: Each company has its own license (OneToOne relationship)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LicenseService {

    private final SystemLicenseRepository licenseRepository;
    private final LicenseEventRepository eventRepository;

    // ========== IN-MEMORY CACHE PER COMPANY (reduces DB queries from filters) ==========
    private final Map<Long, SystemLicense> cachedLicenseByCompany = new ConcurrentHashMap<>();
    private final Map<Long, Long> licenseCacheTimestamps = new ConcurrentHashMap<>();
    private static final long LICENSE_CACHE_TTL_MS = 30_000; // 30 seconds

    /**
     * Get the system license for the current company
     * Returns null if no license exists.
     * Uses in-memory cache with 30s TTL to avoid repeated DB queries from security filters.
     * MULTI-TENANT: Uses CompanyContext to get the correct license
     */
    public SystemLicense getLicense() {
        Company company = CompanyContext.getCurrentCompany();
        
        // If no company context, fall back to first license (for backward compatibility / PROGRAMMER)
        if (company == null) {
            log.debug("No company context, fetching first available license");
            return licenseRepository.findFirstByOrderByIdAsc().orElse(null);
        }
        
        Long companyId = company.getIdCompany();
        long now = System.currentTimeMillis();
        
        // Check cache
        Long cacheTimestamp = licenseCacheTimestamps.get(companyId);
        if (cacheTimestamp != null && (now - cacheTimestamp) < LICENSE_CACHE_TTL_MS) {
            SystemLicense cached = cachedLicenseByCompany.get(companyId);
            if (cached != null) {
                return cached;
            }
        }
        
        // Fetch from database
        Optional<SystemLicense> license = licenseRepository.findByCompany(company);
        if (license.isPresent()) {
            cachedLicenseByCompany.put(companyId, license.get());
            licenseCacheTimestamps.put(companyId, now);
            return license.get();
        }
        
        log.warn("No license found for company: {}. This should not happen.", companyId);
        return null;
    }

    /**
     * Invalidate the license cache. Call this after any license modification.
     */
    public void invalidateLicenseCache() {
        Company company = CompanyContext.getCurrentCompany();
        if (company != null) {
            cachedLicenseByCompany.remove(company.getIdCompany());
            licenseCacheTimestamps.remove(company.getIdCompany());
        } else {
            // Clear all caches if no company context
            cachedLicenseByCompany.clear();
            licenseCacheTimestamps.clear();
        }
    }

    /**
     * Get or create the system license
     * MULTI-TENANT: Returns license for current company
     * If no license exists, it will return null
     */
    public SystemLicense getOrCreateLicense() {
        SystemLicense license = getLicense();
        
        if (license == null) {
            log.warn("No license found in system. License should be created by CompanyService.create().");
        }
        
        return license;
    }

    /**
     * Get license by ID
     */
    public Optional<SystemLicense> getLicenseById(Long id) {
        return licenseRepository.findById(id);
    }

    /**
     * Check if the license is valid (active and not expired)
     */
    public boolean isLicenseValid() {
        SystemLicense license = getLicense();
        if (license == null) {
            log.warn("No license found in the system");
            return false;
        }

        boolean isValid = !license.isExpired() && license.getStatus() == LicenseStatus.ACTIVE;
        
        if (!isValid) {
            log.warn("License is not valid. Status: {}, Expired: {}", 
                license.getStatus(), license.isExpired());
        }

        return isValid;
    }

    /**
     * Get comprehensive license information
     */
    public Map<String, Object> getLicenseInfo() {
        SystemLicense license = getLicense();
        if (license == null) {
            log.warn("No license found");
            return null;
        }

        Map<String, Object> info = new HashMap<>();
        info.put("license", license);
        info.put("daysLeft", license.daysUntilExpiration());
        info.put("daysActive", license.daysActive());
        info.put("isExpired", license.isExpired());
        info.put("needsWarning", license.daysUntilExpiration() <= 5);
        info.put("isCritical", license.daysUntilExpiration() <= 3);

        return info;
    }

    /**
     * Renew the license for specified months (can be negative to subtract time)
     */
    @Transactional
    public void renewLicense(int months, Double amount, String performedBy) {
        SystemLicense license = getLicense();
        if (license == null) {
            throw new RuntimeException("No license found to renew");
        }

        // Determine base date: if expired and adding time, start from NOW (fair to customer);
        // otherwise extend from current expiration (preserves pre-paid time)
        boolean startingFresh = months > 0 && license.isExpired();
        LocalDateTime base = startingFresh ? LocalDateTime.now() : license.getExpirationDate();
        LocalDateTime newExpiration = base.plusMonths(months);

        // Preserve original purchase day to prevent day erosion (31→28→28...)
        // Only when extending an active license (not when starting fresh after expiry)
        if (months > 0 && !startingFresh) {
            int originalDay = license.getPurchaseDate().getDayOfMonth();
            int maxDay = YearMonth.from(newExpiration).lengthOfMonth();
            newExpiration = newExpiration.withDayOfMonth(Math.min(originalDay, maxDay));
        }

        license.setExpirationDate(newExpiration);
        license.setStatus(LicenseStatus.ACTIVE);
        licenseRepository.save(license);
        invalidateLicenseCache();

        // Create event with amount and months
        String action = months > 0 ? "renovada" : "ajustada (tiempo restado)";
        String formattedExp = formatExpirationForCompany(newExpiration, license);
        String description = "Licencia " + action + " por " + Math.abs(months) + " mes(es). Nueva fecha de vencimiento: " + formattedExp;
        if (amount != null && amount > 0) {
            description += ". Monto: $" + String.format("%.2f", amount) + " MXN";
        }
        
        createLicenseEvent(
            license.getId(),
            LicenseEvent.EventType.RENEWED,
            description,
            performedBy,
            amount,
            months
        );

        log.info("License renewed/adjusted for {} months by {}. New expiration: {}. Fresh start: {}", months, performedBy, newExpiration, startingFresh);
    }

    /**
     * Suspend the license
     */
    @Transactional
    public void suspendLicense(String performedBy, String reason) {
        SystemLicense license = getLicense();
        if (license == null) {
            throw new RuntimeException("No license found to suspend");
        }

        license.setStatus(LicenseStatus.SUSPENDED);
        licenseRepository.save(license);
        invalidateLicenseCache();

        // Create event
        createLicenseEvent(
            license.getId(),
            LicenseEvent.EventType.SUSPENDED,
            "Licencia suspendida. Razón: " + (reason != null ? reason : "No especificada"),
            performedBy
        );

        log.info("License suspended by {}. Reason: {}", performedBy, reason);
    }

    /**
     * Reactivate a suspended license
     */
    @Transactional
    public void reactivateLicense(String performedBy) {
        SystemLicense license = getLicense();
        if (license == null) {
            throw new RuntimeException("No license found to reactivate");
        }

        license.setStatus(LicenseStatus.ACTIVE);
        licenseRepository.save(license);
        invalidateLicenseCache();

        // Create event
        createLicenseEvent(
            license.getId(),
            LicenseEvent.EventType.REACTIVATED,
            "Licencia reactivada",
            performedBy
        );

        log.info("License reactivated by {}", performedBy);
    }

    /**
     * Update license notes
     */
    @Transactional
    public void updateNotes(String notes, String performedBy) {
        SystemLicense license = getLicense();
        if (license == null) {
            throw new RuntimeException("No license found to update");
        }

        license.setNotes(notes);
        licenseRepository.save(license);

        log.info("License notes updated by {}", performedBy);
    }

    /**
     * Update license information (owner, restaurant, limits)
     */
    @Transactional
    public void updateLicenseInfo(String ownerName,
                                  String ownerEmail,
                                  String ownerPhone,
                                  String ownerRfc,
                                  String restaurantName,
                                  Integer maxUsers,
                                  Integer maxBranches,
                                  String performedBy) {
        SystemLicense license = getLicense();
        if (license == null) {
            throw new RuntimeException("No se encontró licencia para actualizar");
        }

        // Update owner information
        if (ownerName != null && !ownerName.trim().isEmpty()) {
            license.setOwnerName(ownerName);
        }
        if (ownerEmail != null && !ownerEmail.trim().isEmpty()) {
            license.setOwnerEmail(ownerEmail);
        }
        if (ownerPhone != null && !ownerPhone.trim().isEmpty()) {
            license.setOwnerPhone(ownerPhone);
        }
        if (ownerRfc != null) {
            license.setOwnerRfc(ownerRfc);
        }
        if (restaurantName != null && !restaurantName.trim().isEmpty()) {
            license.setRestaurantName(restaurantName);
        }

        // Update limits
        // maxUsers can be null (unlimited) or a positive number
        license.setMaxUsers(maxUsers);
        
        if (maxBranches != null && maxBranches > 0) {
            license.setMaxBranches(maxBranches);
        }

        licenseRepository.save(license);
        invalidateLicenseCache();

        // Create event
        createLicenseEvent(
            license.getId(),
            LicenseEvent.EventType.UPDATED,
            "Información de licencia actualizada",
            performedBy
        );

        log.info("License information updated by {}", performedBy);
    }

    /**
     * Change license package type
     * @param newPackageType The new package type
     * @param performedBy Username who performs the change
     * @throws IllegalArgumentException if trying to change to same package
     */
    @Transactional
    public void changePackageType(SystemLicense.PackageType newPackageType, String performedBy) {
        SystemLicense license = getLicense();
        if (license == null) {
            throw new IllegalStateException("No existe una licencia en el sistema");
        }

        SystemLicense.PackageType currentPackage = license.getPackageType();
        
        // Prevent changing to same package
        if (currentPackage == newPackageType) {
            throw new IllegalArgumentException("El paquete ya es " + newPackageType.getDisplayName());
        }

        String oldPackage = currentPackage.getDisplayName();
        license.setPackageType(newPackageType);
        licenseRepository.save(license);
        invalidateLicenseCache();

        // Create event
        createLicenseEvent(
            license.getId(),
            LicenseEvent.EventType.UPDATED,
            String.format("Paquete cambiado de %s a %s", oldPackage, newPackageType.getDisplayName()),
            performedBy
        );

        log.info("Package changed from {} to {} by {}", oldPackage, newPackageType, performedBy);
    }

    /**
     * Mark license as expired
     */
    @Transactional
    public void markAsExpired() {
        SystemLicense license = getLicense();
        if (license == null) {
            log.warn("No license found to mark as expired");
            return;
        }

        if (license.getStatus() != LicenseStatus.EXPIRED) {
            license.setStatus(LicenseStatus.EXPIRED);
            licenseRepository.save(license);
            invalidateLicenseCache();

            // Create event
            createLicenseEvent(
                license.getId(),
                LicenseEvent.EventType.EXPIRED,
                "Licencia expirada automáticamente",
                "SYSTEM"
            );

            log.warn("License marked as expired");
        }
    }

    /**
     * Update last check date
     */
    @Transactional
    public void updateLastCheck() {
        SystemLicense license = getLicense();
        if (license != null) {
            license.setLastCheckDate(LocalDate.now());
            licenseRepository.save(license);
        }
    }

    /**
     * Update last notification sent date
     */
    @Transactional
    public void updateLastNotification() {
        SystemLicense license = getLicense();
        if (license != null) {
            license.setLastNotificationSent(LocalDate.now());
            licenseRepository.save(license);

            // Create event
            createLicenseEvent(
                license.getId(),
                LicenseEvent.EventType.NOTIFICATION_SENT,
                "Notificación de vencimiento enviada. Días restantes: " + license.daysUntilExpiration(),
                "SYSTEM"
            );
        }
    }

    /**
     * Format a UTC expiration date to the company's local timezone for event descriptions.
     */
    private String formatExpirationForCompany(LocalDateTime utcExpiration, SystemLicense license) {
        ZoneId zone = ZoneId.of("America/Mexico_City"); // default
        try {
            Company company = license.getCompany();
            if (company != null && company.getTimezone() != null && !company.getTimezone().isBlank()) {
                zone = ZoneId.of(company.getTimezone());
            }
        } catch (Exception ignored) {}
        LocalDateTime local = utcExpiration.atZone(ZoneId.of("UTC")).withZoneSameInstant(zone).toLocalDateTime();
        return local.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    /**
     * Create a license event
     */
    @Transactional
    public void createLicenseEvent(Long licenseId, LicenseEvent.EventType eventType, 
                                   String description, String performedBy) {
        createLicenseEvent(licenseId, eventType, description, performedBy, null, null);
    }

    /**
     * Create a license event with amount and months
     */
    @Transactional
    public void createLicenseEvent(Long licenseId, LicenseEvent.EventType eventType, 
                                   String description, String performedBy, Double amount, Integer months) {
        LicenseEvent event = LicenseEvent.builder()
            .licenseId(licenseId)
            .eventType(eventType)
            .eventDate(LocalDateTime.now())
            .description(description)
            .performedBy(performedBy)
            .amount(amount)
            .months(months)
            .build();

        eventRepository.save(event);
        log.debug("License event created: {} - {}", eventType, description);
    }

    /**
     * Get recent license events
     */
    public List<LicenseEvent> getRecentEvents(int limit) {
        if (limit <= 0) {
            return eventRepository.findTop10ByOrderByEventDateDesc();
        }
        
        List<LicenseEvent> events = eventRepository.findTop10ByOrderByEventDateDesc();
        return events.subList(0, Math.min(limit, events.size()));
    }

    /**
     * Get all events for the current license
     */
    public List<LicenseEvent> getLicenseEvents() {
        SystemLicense license = getLicense();
        if (license == null) {
            return List.of();
        }
        return eventRepository.findByLicenseIdOrderByEventDateDesc(license.getId());
    }

    /**
     * Get total revenue from renewals
     */
    public Double getTotalRevenue() {
        SystemLicense license = getLicense();
        if (license == null) {
            return 0.0;
        }
        
        List<LicenseEvent> renewalEvents = eventRepository.findByLicenseIdOrderByEventDateDesc(license.getId())
            .stream()
            .filter(e -> (e.getEventType() == LicenseEvent.EventType.RENEWED || e.getEventType() == LicenseEvent.EventType.CREATED) && e.getAmount() != null)
            .toList();
        
        return renewalEvents.stream()
            .mapToDouble(LicenseEvent::getAmount)
            .sum();
    }

    /**
     * Get renewal events with amount
     */
    public List<LicenseEvent> getRenewalEventsWithAmount() {
        SystemLicense license = getLicense();
        if (license == null) {
            return List.of();
        }
        
        return eventRepository.findByLicenseIdOrderByEventDateDesc(license.getId())
            .stream()
            .filter(e -> (e.getEventType() == LicenseEvent.EventType.RENEWED || e.getEventType() == LicenseEvent.EventType.CREATED) && e.getAmount() != null)
            .toList();
    }

    /**
     * Enforce one license per company
     * Throws exception if trying to create when one already exists for the company
     * MULTI-TENANT: Each company can have exactly one license
     */
    private void enforceSingletonPerCompany(Company company) {
        if (company == null) {
            throw new IllegalStateException("Company is required to create a license.");
        }
        if (licenseRepository.existsByCompany(company)) {
            throw new IllegalStateException("License already exists for company: " + company.getName() + ". Only one license is allowed per company.");
        }
    }

    /**
     * Create a new license (initial setup)
     * MULTI-TENANT: Enforces one license per company
     */
    @Transactional
    public SystemLicense createLicense(SystemLicense license, String performedBy) {
        // Enforce one license per company
        Company company = license.getCompany();
        enforceSingletonPerCompany(company);

        SystemLicense saved = licenseRepository.save(license);

        // Create creation event
        createLicenseEvent(
            saved.getId(),
            LicenseEvent.EventType.CREATED,
            "Licencia creada - Paquete: " + saved.getPackageType() + 
            ", Ciclo: " + saved.getBillingCycle(),
            performedBy
        );

        log.info("New license created: {}", saved.getLicenseKey());
        return saved;
    }

    /**
     * Generate a unique license key
     */
    public String generateLicenseKey(String restaurantName) {
        String prefix = "ELGS";
        String year = String.valueOf(LocalDate.now().getYear());
        String restaurant = restaurantName.replaceAll("[^A-Za-z]", "")
            .toUpperCase()
            .substring(0, Math.min(4, restaurantName.length()));
        String random = String.valueOf(System.currentTimeMillis()).substring(7);

        return String.format("%s-%s-%s-%s", prefix, year, restaurant, random);
    }

    /**
     * Create initial license with all parameters
     * MULTI-TENANT: Creates license for the specified company
     */
    @Transactional
    public SystemLicense createInitialLicense(Company company,
                                             String licenseKey,
                                             String packageType,
                                             String billingCycle,
                                             int months,
                                             String ownerName,
                                             String ownerEmail,
                                             String ownerPhone,
                                             String ownerRfc,
                                             String restaurantName,
                                             int maxUsers,
                                             int maxBranches,
                                             String performedBy) {
        // Enforce one license per company
        enforceSingletonPerCompany(company);

        LocalDateTime purchaseDate = LocalDateTime.now();
        LocalDateTime expirationDate = purchaseDate.plusMonths(months);

        SystemLicense license = SystemLicense.builder()
            .company(company)
            .licenseKey(licenseKey)
            .packageType(SystemLicense.PackageType.valueOf(packageType))
            .billingCycle(SystemLicense.BillingCycle.valueOf(billingCycle))
            .purchaseDate(purchaseDate)
            .expirationDate(expirationDate)
            .installationDate(LocalDate.now())
            .status(LicenseStatus.ACTIVE)
            .ownerName(ownerName)
            .ownerEmail(ownerEmail)
            .ownerPhone(ownerPhone)
            .ownerRfc(ownerRfc)
            .restaurantName(restaurantName)
            .maxUsers(maxUsers)
            .maxBranches(maxBranches)
            .version("1.0.0")
            .lastCheckDate(LocalDate.now())
            .build();

        SystemLicense saved = licenseRepository.save(license);

        // Create creation event
        createLicenseEvent(
            saved.getId(),
            LicenseEvent.EventType.CREATED,
            String.format("Licencia inicial creada - Paquete: %s, Ciclo: %s, Vigencia: %d meses", 
                saved.getPackageDisplayName(), saved.getBillingCycleDisplayName(), months),
            performedBy
        );

        log.info("Initial license created: {} for {} months by {}", 
            saved.getLicenseKey(), months, performedBy);
        return saved;
    }

    /**
     * Check if license needs notification
     */
    public boolean needsNotification() {
        SystemLicense license = getLicense();
        return license != null && license.needsNotification();
    }

    /**
     * Get days until expiration
     */
    public long getDaysUntilExpiration() {
        SystemLicense license = getLicense();
        return license != null ? license.daysUntilExpiration() : -1;
    }

    /**
     * Check if license has landing page access (WEB or ECOMMERCE)
     */
    public boolean hasLandingPageAccess() {
        SystemLicense license = getLicense();
        if (license == null) {
            return false; // Sin licencia, no tiene acceso
        }
        return license.getPackageType() == SystemLicense.PackageType.WEB ||
               license.getPackageType() == SystemLicense.PackageType.ECOMMERCE;
    }

    /**
     * Check if license has customer/client module access (ECOMMERCE only)
     */
    public boolean hasCustomerModuleAccess() {
        SystemLicense license = getLicense();
        if (license == null) {
            return false; // Sin licencia, no tiene acceso
        }
        return license.getPackageType() == SystemLicense.PackageType.ECOMMERCE;
    }

    /**
     * Check if more users can be created (based on license limit)
     * PROGRAMMER is not counted towards this limit
     * 
     * @param currentUserCount Current number of active employees (excluding PROGRAMMER)
     * @return true if more users can be created, false if limit reached
     */
    public boolean canCreateMoreUsers(long currentUserCount) {
        SystemLicense license = getLicense();
        if (license == null) {
            return false; // No license, no users allowed
        }
        
        // If maxUsers is null, unlimited users allowed
        if (license.getMaxUsers() == null) {
            return true;
        }
        
        log.debug("Checking user limit: {}/{} active users (excluding PROGRAMMER)", 
                  currentUserCount, license.getMaxUsers());
        
        // Check if current count is below limit
        return currentUserCount < license.getMaxUsers();
    }

    /**
     * Get the maximum number of users allowed
     * @return max users or null if unlimited
     */
    public Integer getMaxUsers() {
        SystemLicense license = getLicense();
        if (license == null) {
            return 0;
        }
        return license.getMaxUsers();
    }

    /**
     * Get package type
     */
    public SystemLicense.PackageType getPackageType() {
        SystemLicense license = getLicense();
        return license != null ? license.getPackageType() : null;
    }

    /**
     * Check if the current number of active employees exceeds the license user limit.
     * This happens when a PROGRAMMER reduces the maxUsers after employees have been created.
     * 
     * @param currentActiveUsers Number of currently active (enabled) employees excluding PROGRAMMER
     * @return true if the limit is exceeded (more active users than allowed)
     */
    public boolean isUserLimitExceeded(long currentActiveUsers) {
        SystemLicense license = getLicense();
        if (license == null) {
            return false;
        }
        // If no limit set (null), it's never exceeded
        if (license.getMaxUsers() == null) {
            return false;
        }
        return currentActiveUsers > license.getMaxUsers();
    }

    /**
     * Get the number of excess users over the limit.
     * 
     * @param currentActiveUsers Number of currently active (enabled) employees excluding PROGRAMMER
     * @return Number of users that need to be deactivated, or 0 if within limit
     */
    public int getExcessUserCount(long currentActiveUsers) {
        SystemLicense license = getLicense();
        if (license == null || license.getMaxUsers() == null) {
            return 0;
        }
        int excess = (int) (currentActiveUsers - license.getMaxUsers());
        return Math.max(0, excess);
    }

    // ========== METHODS FOR PROGRAMMER MULTI-TENANT MANAGEMENT ==========
    // These methods accept licenseId directly, for use by PROGRAMMER when managing multiple companies

    /**
     * Renew license by ID (for PROGRAMMER managing multiple companies)
     */
    @Transactional
    public void renewLicenseById(Long licenseId, int months, Double amount, String performedBy) {
        SystemLicense license = licenseRepository.findById(licenseId)
            .orElseThrow(() -> new RuntimeException("License not found: " + licenseId));

        // Determine base date: if expired and adding time, start from NOW (fair to customer);
        // otherwise extend from current expiration (preserves pre-paid time)
        boolean startingFresh = months > 0 && license.isExpired();
        LocalDateTime base = startingFresh ? LocalDateTime.now() : license.getExpirationDate();
        LocalDateTime newExpiration = base.plusMonths(months);

        // Preserve original purchase day to prevent day erosion (31→28→28...)
        // Only when extending an active license (not when starting fresh after expiry)
        if (months > 0 && !startingFresh) {
            int originalDay = license.getPurchaseDate().getDayOfMonth();
            int maxDay = YearMonth.from(newExpiration).lengthOfMonth();
            newExpiration = newExpiration.withDayOfMonth(Math.min(originalDay, maxDay));
        }

        license.setExpirationDate(newExpiration);
        license.setStatus(LicenseStatus.ACTIVE);
        licenseRepository.save(license);
        invalidateLicenseCacheForCompany(license.getCompany().getIdCompany());

        String action = months > 0 ? "renovada" : "ajustada (tiempo restado)";
        String formattedExp = formatExpirationForCompany(newExpiration, license);
        String description = "Licencia " + action + " por " + Math.abs(months) + " mes(es). Nueva fecha de vencimiento: " + formattedExp;
        /*if (startingFresh) {
            description += " (renovada desde hoy por expiración previa)";
        }*/
        if (amount != null && amount > 0) {
            description += ". Monto: $" + String.format("%.2f", amount) + " MXN";
        }
        
        createLicenseEvent(licenseId, LicenseEvent.EventType.RENEWED, description, performedBy, amount, months);
        log.info("License {} renewed/adjusted for {} months by {}. Fresh start: {}", licenseId, months, performedBy, startingFresh);
    }

    /**
     * Suspend license by ID (for PROGRAMMER managing multiple companies)
     */
    @Transactional
    public void suspendLicenseById(Long licenseId, String performedBy, String reason) {
        SystemLicense license = licenseRepository.findById(licenseId)
            .orElseThrow(() -> new RuntimeException("License not found: " + licenseId));

        license.setStatus(LicenseStatus.SUSPENDED);
        licenseRepository.save(license);
        invalidateLicenseCacheForCompany(license.getCompany().getIdCompany());

        String description = "Licencia suspendida";
        if (reason != null && !reason.trim().isEmpty()) {
            description += ". Razón: " + reason;
        }
        
        createLicenseEvent(licenseId, LicenseEvent.EventType.SUSPENDED, description, performedBy, null, null);
        log.info("License {} suspended by {}", licenseId, performedBy);
    }

    /**
     * Reactivate license by ID (for PROGRAMMER managing multiple companies)
     */
    @Transactional
    public void reactivateLicenseById(Long licenseId, String performedBy) {
        SystemLicense license = licenseRepository.findById(licenseId)
            .orElseThrow(() -> new RuntimeException("License not found: " + licenseId));

        license.setStatus(LicenseStatus.ACTIVE);
        licenseRepository.save(license);
        invalidateLicenseCacheForCompany(license.getCompany().getIdCompany());

        createLicenseEvent(licenseId, LicenseEvent.EventType.REACTIVATED, "Licencia reactivada", performedBy, null, null);
        log.info("License {} reactivated by {}", licenseId, performedBy);
    }

    /**
     * Change package type by ID (for PROGRAMMER managing multiple companies)
     */
    @Transactional
    public void changePackageTypeById(Long licenseId, SystemLicense.PackageType newPackageType, String performedBy) {
        SystemLicense license = licenseRepository.findById(licenseId)
            .orElseThrow(() -> new RuntimeException("License not found: " + licenseId));

        SystemLicense.PackageType oldPackage = license.getPackageType();
        if (oldPackage == newPackageType) {
            throw new IllegalArgumentException("El paquete seleccionado ya está activo");
        }

        license.setPackageType(newPackageType);
        licenseRepository.save(license);
        invalidateLicenseCacheForCompany(license.getCompany().getIdCompany());

        String description = "Paquete cambiado de " + oldPackage.getDisplayName() + " a " + newPackageType.getDisplayName();
        createLicenseEvent(licenseId, LicenseEvent.EventType.UPDATED, description, performedBy, null, null);
        log.info("License {} package changed from {} to {} by {}", licenseId, oldPackage, newPackageType, performedBy);
    }

    /**
     * Update license information by ID (for PROGRAMMER managing multiple companies)
     */
    @Transactional
    public void updateLicenseInfoById(Long licenseId, String ownerName, String ownerEmail, String ownerPhone,
                                      String ownerRfc, String restaurantName, Integer maxUsers, int maxBranches,
                                      String performedBy) {
        SystemLicense license = licenseRepository.findById(licenseId)
            .orElseThrow(() -> new RuntimeException("License not found: " + licenseId));

        license.setOwnerName(ownerName);
        license.setOwnerEmail(ownerEmail);
        license.setOwnerPhone(ownerPhone);
        license.setOwnerRfc(ownerRfc);
        license.setRestaurantName(restaurantName);
        license.setMaxUsers(maxUsers);
        license.setMaxBranches(maxBranches);
        licenseRepository.save(license);
        invalidateLicenseCacheForCompany(license.getCompany().getIdCompany());

        String description = "Información de licencia actualizada: " + restaurantName;
        createLicenseEvent(licenseId, LicenseEvent.EventType.UPDATED, description, performedBy, null, null);
        log.info("License {} info updated by {}", licenseId, performedBy);
    }

    /**
     * Get all events for a specific license by ID (for PROGRAMMER managing multiple companies)
     */
    public List<LicenseEvent> getLicenseEventsById(Long licenseId) {
        return eventRepository.findByLicenseIdOrderByEventDateDesc(licenseId);
    }

    /**
     * Get total revenue from renewals for a specific license (for PROGRAMMER managing multiple companies)
     */
    public Double getTotalRevenueById(Long licenseId) {
        List<LicenseEvent> renewalEvents = eventRepository.findByLicenseIdOrderByEventDateDesc(licenseId)
                .stream()
                .filter(e -> (e.getEventType() == LicenseEvent.EventType.RENEWED || e.getEventType() == LicenseEvent.EventType.CREATED) && e.getAmount() != null)
                .toList();
        
        return renewalEvents.stream()
                .mapToDouble(LicenseEvent::getAmount)
                .sum();
    }

    /**
     * Get renewal events with amount for a specific license (for PROGRAMMER managing multiple companies)
     */
    public List<LicenseEvent> getRenewalEventsWithAmountById(Long licenseId) {
        return eventRepository.findByLicenseIdOrderByEventDateDesc(licenseId)
                .stream()
                .filter(e -> (e.getEventType() == LicenseEvent.EventType.RENEWED || e.getEventType() == LicenseEvent.EventType.CREATED) && e.getAmount() != null)
                .toList();
    }

    /**
     * Invalidate license cache for a specific company
     */
    private void invalidateLicenseCacheForCompany(Long companyId) {
        cachedLicenseByCompany.remove(companyId);
        licenseCacheTimestamps.remove(companyId);
        log.debug("License cache invalidated for company {}", companyId);
    }
}

