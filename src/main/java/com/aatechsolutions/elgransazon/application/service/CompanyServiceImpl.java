package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.CompanyRepository;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.domain.repository.LicenseEventRepository;
import com.aatechsolutions.elgransazon.domain.repository.RoleRepository;
import com.aatechsolutions.elgransazon.domain.repository.SystemConfigurationRepository;
import com.aatechsolutions.elgransazon.domain.repository.SystemLicenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Implementation of CompanyService
 * Handles company creation with all related entities
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CompanyServiceImpl implements CompanyService {

    private final CompanyRepository companyRepository;
    private final SystemConfigurationRepository configRepository;
    private final SystemLicenseRepository licenseRepository;
    private final LicenseEventRepository licenseEventRepository;
    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.base-domain:localhost}")
    private String baseDomain;

    @Override
    @Transactional(readOnly = true)
    public Optional<Company> findById(Long id) {
        return companyRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Company> findBySlug(String slug) {
        return companyRepository.findBySlug(slug);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Company> findByCustomDomain(String customDomain) {
        return companyRepository.findByCustomDomain(customDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Company> findByHost(String host) {
        if (host == null || host.isEmpty()) {
            return Optional.empty();
        }

        log.debug("Finding company by host: {}", host);

        // Remove port if present
        String cleanHost = host.contains(":") ? host.split(":")[0] : host;

        // Handle localhost/127.0.0.1 as root domain (no company context)
        // In development, access companies via subdomains: elbuensazon.localhost:8080
        // Direct access to localhost:8080 will show landing page (no company)
        if ("localhost".equalsIgnoreCase(cleanHost) || "127.0.0.1".equals(cleanHost)) {
            log.debug("Root domain (localhost) detected - no company context (landing page)");
            return Optional.empty();
        }

        // Handle IP addresses (e.g., 192.168.1.76) - try to find by IP as slug
        if (isIpAddress(cleanHost)) {
            log.debug("IP address detected: {}, looking for company with slug matching IP", cleanHost);
            Optional<Company> byIp = companyRepository.findBySlugAndActiveTrue(cleanHost);
            if (byIp.isPresent()) {
                return byIp;
            }
            // Also check custom domain for IP
            return companyRepository.findByCustomDomainAndActiveTrue(cleanHost);
        }

        // First try to find by custom domain (exact match)
        // This handles cases like: www.pizzamax.com (restaurant's own domain)
        Optional<Company> byDomain = companyRepository.findByCustomDomainAndActiveTrue(cleanHost);
        if (byDomain.isPresent()) {
            log.debug("Found company by custom domain: {}", cleanHost);
            return byDomain;
        }

        // Check if this is the system's root domain (e.g., savorycloud.com)
        // The root domain is NOT a restaurant - it's the system landing page
        if (cleanHost.equalsIgnoreCase(baseDomain)) {
            log.debug("Root domain detected: {} - no company context (landing page)", cleanHost);
            return Optional.empty(); // No company for root domain - handled by landing page
        }

        // If not found, try to extract subdomain
        // Example: pizzamax.savorycloud.com -> extract "pizzamax"
        String[] parts = cleanHost.split("\\.");
        if (parts.length >= 2) {
            // Could be subdomain.domain.tld or domain.tld
            // We assume subdomain is the first part if host has more than 2 parts
            // or if it matches our base domain
            String potentialSlug = parts[0];
            
            // Check if this looks like our base domain pattern
            String remainingDomain = String.join(".", Arrays.copyOfRange(parts, 1, parts.length));
            if (remainingDomain.equalsIgnoreCase(baseDomain) || parts.length > 2) {
                log.debug("Trying slug: {} (from host: {})", potentialSlug, cleanHost);
                return companyRepository.findBySlugAndActiveTrue(potentialSlug);
            }
        }

        log.warn("No company found for host: {}", host);
        return Optional.empty();
    }

    /**
     * Check if a host string looks like an IP address
     */
    private boolean isIpAddress(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        // Simple check: IP addresses are digits and dots, with 4 parts
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            try {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a domain is protected (system's base domain or variations)
     * SECURITY: Prevents companies from registering the system domain as their customDomain
     */
    private boolean isProtectedDomain(String domain) {
        if (domain == null || domain.isEmpty()) {
            return false;
        }
        String cleanDomain = domain.toLowerCase().trim();
        
        // Check exact match with base domain
        if (cleanDomain.equalsIgnoreCase(baseDomain)) {
            return true;
        }
        
        // Check www. prefix variations of base domain
        if (cleanDomain.equalsIgnoreCase("www." + baseDomain)) {
            return true;
        }
        
        // Check if it ends with the base domain (could be a subdomain attempt)
        // e.g., prevent "admin.savorycloud.com" from being registered as customDomain
        if (cleanDomain.endsWith("." + baseDomain.toLowerCase())) {
            return true;
        }
        
        return false;
    }

    /**
     * SECURITY: Check if a slug is reserved and cannot be used by any company.
     * Prevents registration of slugs that could hijack system traffic or are commonly
     * used for system services.
     */
    private boolean isReservedSlug(String slug) {
        if (slug == null || slug.isEmpty()) {
            return false;
        }
        
        String cleanSlug = slug.toLowerCase().trim();
        
        // List of reserved slugs - system and common service names
        java.util.Set<String> reservedSlugs = java.util.Set.of(
            "localhost",
            "127",
            "www",
            "admin",
            "api",
            "app",
            "mail",
            "email",
            "ftp",
            "cdn",
            "static",
            "assets",
            "files",
            "images",
            "img",
            "media",
            "docs",
            "help",
            "support",
            "blog",
            "news",
            "status",
            "dashboard",
            "panel",
            "login",
            "auth",
            "oauth",
            "sso",
            "test",
            "dev",
            "staging",
            "prod",
            "production",
            "demo",
            "sandbox",
            "root",
            "system",
            "null",
            "undefined"
        );
        
        // Check against reserved list
        if (reservedSlugs.contains(cleanSlug)) {
            return true;
        }
        
        // SECURITY: Extract base domain name without TLD and check against slug
        // e.g., if baseDomain="savorycloud.com", prevent slug="savorycloud"
        String baseDomainName = baseDomain.toLowerCase();
        int lastDot = baseDomainName.lastIndexOf('.');
        if (lastDot > 0) {
            baseDomainName = baseDomainName.substring(0, lastDot);
        }
        if (cleanSlug.equals(baseDomainName)) {
            return true;
        }
        
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Company> findAll() {
        return companyRepository.findAllByOrderByNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Company> findAllActive() {
        return companyRepository.findByActiveTrue();
    }

    @Override
    public Company create(Company company) {
        log.info("Creating new company: {}", company.getSlug());

        // SECURITY: Prevent registering reserved system slugs
        if (isReservedSlug(company.getSlug())) {
            throw new IllegalArgumentException("El slug '" + company.getSlug() + "' está reservado para el sistema y no puede ser utilizado.");
        }

        // Validate slug uniqueness
        if (companyRepository.existsBySlug(company.getSlug())) {
            throw new IllegalArgumentException("El slug ya está en uso: " + company.getSlug());
        }

        // Validate custom domain uniqueness if provided
        if (company.getCustomDomain() != null && !company.getCustomDomain().isEmpty()) {
            // SECURITY: Prevent registering the system's base domain as customDomain
            if (isProtectedDomain(company.getCustomDomain())) {
                throw new IllegalArgumentException("No se puede usar el dominio del sistema como dominio personalizado: " + company.getCustomDomain());
            }
            if (companyRepository.existsByCustomDomain(company.getCustomDomain())) {
                throw new IllegalArgumentException("El dominio ya está en uso: " + company.getCustomDomain());
            }
        }

        // Save company first
        company.setActive(true);
        Company savedCompany = companyRepository.save(company);
        log.info("Company created with ID: {}", savedCompany.getIdCompany());

        // Create default SystemConfiguration
        createDefaultConfiguration(savedCompany);
        log.info("Default SystemConfiguration created for company: {}", savedCompany.getSlug());

        // Create default SystemLicense
        createDefaultLicense(savedCompany);
        log.info("Default SystemLicense created for company: {}", savedCompany.getSlug());

        // NOTE: BackupConfiguration is GLOBAL, not per-company
        // It's managed by the PROGRAMMER via /programmer/backup

        // Create default Admin employee
        Employee admin = createDefaultAdmin(savedCompany);
        log.info("Default Admin employee created for company: {} (username: {})", 
            savedCompany.getSlug(), admin.getUsername());

        return savedCompany;
    }

    @Override
    public Company create(String slug, String name, String customDomain,
                          String senderEmail, String senderName, String contactEmail,
                          String contactPhone, String address, String rfc, String timezone,
                          String adminUsername, String adminFirstName, String adminLastName, String adminPassword,
                          boolean freeTrial, String packageType, String billingCycle, int licenseMonths, Double licenseAmount,
                          String performedBy) {
        log.info("Creating new company with parameters: {}", slug);

        // Build company object with all fields
        Company company = Company.builder()
                .slug(slug)
                .name(name)
                .customDomain(customDomain != null && !customDomain.trim().isEmpty() ? customDomain : null)
                .senderEmail(senderEmail)
                .senderName(senderName != null && !senderName.trim().isEmpty() ? senderName : name)
                .contactEmail(contactEmail)
                .contactPhone(contactPhone)
                .address(address)
                .rfc(rfc)
                .timezone(timezone != null && !timezone.isBlank() ? timezone : "America/Mexico_City")
                .active(true)
                .build();

        // SECURITY: Prevent registering reserved system slugs
        if (isReservedSlug(company.getSlug())) {
            throw new IllegalArgumentException("El slug '" + company.getSlug() + "' está reservado para el sistema y no puede ser utilizado.");
        }

        // Validate slug uniqueness
        if (companyRepository.existsBySlug(company.getSlug())) {
            throw new IllegalArgumentException("El slug ya está en uso: " + company.getSlug());
        }

        // Validate custom domain uniqueness if provided
        if (company.getCustomDomain() != null && !company.getCustomDomain().isEmpty()) {
            // SECURITY: Prevent registering the system's base domain as customDomain
            if (isProtectedDomain(company.getCustomDomain())) {
                throw new IllegalArgumentException("No se puede usar el dominio del sistema como dominio personalizado: " + company.getCustomDomain());
            }
            if (companyRepository.existsByCustomDomain(company.getCustomDomain())) {
                throw new IllegalArgumentException("El dominio ya está en uso: " + company.getCustomDomain());
            }
        }

        // Save company first
        Company savedCompany = companyRepository.save(company);
        log.info("Company created with ID: {}", savedCompany.getIdCompany());

        // Create default SystemConfiguration
        createDefaultConfiguration(savedCompany);
        log.info("Default SystemConfiguration created for company: {}", savedCompany.getSlug());

        // Create configured SystemLicense with financial event
        createConfiguredLicense(savedCompany, freeTrial, packageType, billingCycle, licenseMonths, licenseAmount, performedBy);
        log.info("SystemLicense created for company: {} (freeTrial={}, package={}, months={})", savedCompany.getSlug(), freeTrial, packageType, licenseMonths);

        // NOTE: BackupConfiguration is GLOBAL, not per-company
        // It's managed by the PROGRAMMER via /programmer/backup

        // Create Admin employee with custom parameters
        Employee admin = createCustomAdmin(savedCompany, adminUsername, adminFirstName, adminLastName, adminPassword);
        log.info("Custom Admin employee created for company: {} (username: {})", 
            savedCompany.getSlug(), admin.getUsername());

        return savedCompany;
    }

    @Override
    public Company update(Long id, Company company) {
        log.info("Updating company with ID: {}", id);

        Company existing = companyRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));

        // Check slug uniqueness if changed
        if (!existing.getSlug().equals(company.getSlug())) {
            // SECURITY: Prevent changing to reserved system slugs
            if (isReservedSlug(company.getSlug())) {
                throw new IllegalArgumentException("El slug '" + company.getSlug() + "' está reservado para el sistema y no puede ser utilizado.");
            }
            if (companyRepository.existsBySlugAndIdNot(company.getSlug(), id)) {
                throw new IllegalArgumentException("El slug ya está en uso: " + company.getSlug());
            }
            existing.setSlug(company.getSlug());
        }

        // Check custom domain uniqueness if changed
        if (company.getCustomDomain() != null && !company.getCustomDomain().isEmpty()) {
            // SECURITY: Prevent registering the system's base domain as customDomain
            if (isProtectedDomain(company.getCustomDomain())) {
                throw new IllegalArgumentException("No se puede usar el dominio del sistema como dominio personalizado: " + company.getCustomDomain());
            }
            if (!company.getCustomDomain().equals(existing.getCustomDomain())) {
                if (companyRepository.existsByCustomDomainAndIdNot(company.getCustomDomain(), id)) {
                    throw new IllegalArgumentException("El dominio ya está en uso: " + company.getCustomDomain());
                }
            }
            existing.setCustomDomain(company.getCustomDomain());
        } else {
            existing.setCustomDomain(null);
        }

        existing.setName(company.getName());
        existing.setRfc(company.getRfc());
        existing.setSenderEmail(company.getSenderEmail());
        existing.setSenderName(company.getSenderName());

        return companyRepository.save(existing);
    }

    @Override
    public Company setActive(Long id, boolean active) {
        log.info("Setting company {} active status to: {}", id, active);

        Company company = companyRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));

        company.setActive(active);
        return companyRepository.save(company);
    }

    @Override
    public void delete(Long id) {
        log.warn("Deleting company with ID: {} - This will cascade delete all company data!", id);

        if (!companyRepository.existsById(id)) {
            throw new IllegalArgumentException("Empresa no encontrada");
        }

        companyRepository.deleteById(id);
        log.info("Company deleted: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlug(String slug) {
        return companyRepository.existsBySlug(slug);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCustomDomain(String customDomain) {
        return companyRepository.existsByCustomDomain(customDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return companyRepository.count();
    }

    // ========== Private Helper Methods ==========

    private SystemConfiguration createDefaultConfiguration(Company company) {
        // Initialize payment methods
        Map<PaymentMethodType, Boolean> paymentMethods = new HashMap<>();
        paymentMethods.put(PaymentMethodType.CASH, true);
        paymentMethods.put(PaymentMethodType.CREDIT_CARD, true);
        paymentMethods.put(PaymentMethodType.DEBIT_CARD, true);

        Map<PaymentMethodType, Boolean> deliveryPaymentMethods = new HashMap<>();
        deliveryPaymentMethods.put(PaymentMethodType.CASH, true);
        deliveryPaymentMethods.put(PaymentMethodType.CREDIT_CARD, false);
        deliveryPaymentMethods.put(PaymentMethodType.DEBIT_CARD, false);
        deliveryPaymentMethods.put(PaymentMethodType.TRANSFER, false);

        // Note: System branding (systemName, systemSlogan, systemLogoUrl) is now GLOBAL
        // and managed by GlobalSystemConfigService, not per-company
        SystemConfiguration config = SystemConfiguration.builder()
            .company(company)
            // Restaurant-specific fields (per-company)
            .restaurantName(company.getName())
            .slogan("Bienvenidos a " + company.getName())
            // NO systemName, systemSlogan, systemLogoUrl - these are now GLOBAL
            .address("Dirección pendiente de configurar")
            .phone("0000000000")
            .email(company.getContactEmail() != null && !company.getContactEmail().isBlank()
                    ? company.getContactEmail()
                    : "miempresa@ejemplo.com")
            .taxRate(BigDecimal.valueOf(16.00))
            .averageConsumptionTimeMinutes(120)
            .paymentMethods(paymentMethods)
            .deliveryPaymentMethods(deliveryPaymentMethods)
            .build();

        return configRepository.save(config);
    }

    private SystemLicense createConfiguredLicense(Company company, boolean freeTrial, String packageType,
                                                     String billingCycle, int months, Double amount, String performedBy) {
        String licenseKey = generateLicenseKey(company.getSlug());
        LocalDateTime purchaseDate = LocalDateTime.now();
        int effectiveMonths = months > 0 ? months : 1;
        LocalDateTime expirationDate = purchaseDate.plusMonths(effectiveMonths);

        SystemLicense.PackageType pkgType;
        try {
            pkgType = SystemLicense.PackageType.valueOf(packageType);
        } catch (Exception e) {
            pkgType = SystemLicense.PackageType.BASIC;
        }

        SystemLicense.BillingCycle cycle;
        try {
            cycle = SystemLicense.BillingCycle.valueOf(billingCycle);
        } catch (Exception e) {
            cycle = SystemLicense.BillingCycle.MONTHLY;
        }

        int maxUsers = 7;

        int maxBranches = switch (pkgType) {
            case BASIC -> 1;
            case WEB -> 3;
            case ECOMMERCE -> 10;
        };

        String notes = freeTrial
                ? String.format("Licencia de prueba gratis creada automáticamente. Válida por %d mes(es).", effectiveMonths)
                : String.format("Licencia %s creada. Válida por %d mes(es).", pkgType.name(), effectiveMonths);

        SystemLicense license = SystemLicense.builder()
            .company(company)
            .licenseKey(licenseKey)
            .packageType(pkgType)
            .billingCycle(cycle)
            .purchaseDate(purchaseDate)
            .expirationDate(expirationDate)
            .installationDate(LocalDate.now())
            .status(SystemLicense.LicenseStatus.ACTIVE)
            .ownerName(company.getName())
            .ownerEmail(company.getContactEmail() != null && !company.getContactEmail().isBlank()
                    ? company.getContactEmail()
                    : "miempresa@ejemplo.com")
            .restaurantName(company.getName())
            .maxUsers(maxUsers)
            .maxBranches(maxBranches)
            .version("1.0.0")
            .notes(notes)
            .lastCheckDate(LocalDate.now())
            .build();

        SystemLicense savedLicense = licenseRepository.save(license);

        // Create financial event
        double eventAmount = freeTrial ? 0.0 : (amount != null ? amount : 0.0);
        String description = freeTrial
                ? String.format("Licencia creada - Prueba gratis (%s, %d mes(es))", pkgType.name(), effectiveMonths)
                : String.format("Licencia creada - %s, %d mes(es), $%.2f", pkgType.name(), effectiveMonths, eventAmount);

        LicenseEvent event = LicenseEvent.builder()
            .licenseId(savedLicense.getId())
            .eventType(LicenseEvent.EventType.CREATED)
            .eventDate(LocalDateTime.now())
            .description(description)
            .performedBy(performedBy != null ? performedBy : "SYSTEM")
            .amount(eventAmount)
            .months(effectiveMonths)
            .build();
        licenseEventRepository.save(event);

        return savedLicense;
    }

    private SystemLicense createDefaultLicense(Company company) {
        return createConfiguredLicense(company, true, "BASIC", "MONTHLY", 1, 0.0, "SYSTEM");
    }

    private Employee createDefaultAdmin(Company company) {
        Role adminRole = roleRepository.findByNombreRol(Role.ADMIN)
            .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN not found"));

        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);

        // Generate unique username based on company slug
        String username = "admin_" + company.getSlug();

        Employee admin = Employee.builder()
            .company(company)
            .username(username)
            .nombre("Administrador")
            .apellido(company.getName())
            .edad(30)
            .contrasenia(passwordEncoder.encode("admin1234"))
            .telefono(null)
            .salario(0.0)
            .enabled(true)
            .roles(roles)
            .createdBy("SYSTEM")
            .updatedBy("SYSTEM")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        return employeeRepository.save(admin);
    }

    private Employee createCustomAdmin(Company company, String username, String firstName, String lastName, String password) {
        Role adminRole = roleRepository.findByNombreRol(Role.ADMIN)
            .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN not found"));

        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);

        Employee admin = Employee.builder()
            .company(company)
            .username(username)
            .nombre(firstName)
            .apellido(lastName)
            .edad(30)
            .contrasenia(passwordEncoder.encode(password))
            .telefono(null)
            .salario(0.0)
            .enabled(true)
            .roles(roles)
            .createdBy("SYSTEM")
            .updatedBy("SYSTEM")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        return employeeRepository.save(admin);
    }

    private String generateLicenseKey(String slug) {
        int year = LocalDate.now().getYear();
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return String.format("ELGS-%d-%s-%s", year, slug.toUpperCase(), random);
    }
}
