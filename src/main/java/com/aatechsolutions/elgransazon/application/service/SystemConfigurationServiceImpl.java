package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.BusinessHours;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.DayOfWeek;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.repository.SystemConfigurationRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of SystemConfigurationService
 * MULTI-TENANT: Each company has its own SystemConfiguration (OneToOne relationship)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SystemConfigurationServiceImpl implements SystemConfigurationService {

    private final SystemConfigurationRepository configurationRepository;

    // ========== IN-MEMORY CACHE PER COMPANY (reduces DB queries from GlobalControllerAdvice) ==========
    private final Map<Long, SystemConfiguration> cachedConfigByCompany = new ConcurrentHashMap<>();
    private final Map<Long, Long> configCacheTimestamps = new ConcurrentHashMap<>();
    private static final long CONFIG_CACHE_TTL_MS = 30_000; // 30 seconds

    /**
     * Invalidate the configuration cache. Call this after any configuration modification.
     */
    public void invalidateConfigCache() {
        Company company = CompanyContext.getCurrentCompany();
        if (company != null) {
            cachedConfigByCompany.remove(company.getIdCompany());
            configCacheTimestamps.remove(company.getIdCompany());
        } else {
            // Clear all caches if no company context
            cachedConfigByCompany.clear();
            configCacheTimestamps.clear();
        }
    }

    @Override
    public SystemConfiguration getConfiguration() {
        Company company = CompanyContext.getCurrentCompany();
        
        // If no company context, return null - templates should use globalSystemConfig instead
        if (company == null) {
            log.debug("No company context, returning null (use globalSystemConfig for branding)");
            return null;
        }
        
        Long companyId = company.getIdCompany();
        long now = System.currentTimeMillis();
        
        // Check cache
        Long cacheTimestamp = configCacheTimestamps.get(companyId);
        if (cacheTimestamp != null && (now - cacheTimestamp) < CONFIG_CACHE_TTL_MS) {
            SystemConfiguration cached = cachedConfigByCompany.get(companyId);
            if (cached != null) {
                return cached;
            }
        }
        
        log.debug("Fetching system configuration for company: {}", companyId);
        Optional<SystemConfiguration> config = configurationRepository.findByCompany(company);
        
        if (config.isPresent()) {
            cachedConfigByCompany.put(companyId, config.get());
            configCacheTimestamps.put(companyId, now);
            return config.get();
        }
        
        // No configuration found for this company - should not happen if CompanyService.create() worked correctly
        log.warn("No configuration found for company: {}. This should not happen.", companyId);
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SystemConfiguration> getConfigurationById(Long id) {
        log.debug("Fetching configuration by ID: {}", id);
        return configurationRepository.findById(id);
    }

    @Override
    public SystemConfiguration updateConfiguration(SystemConfiguration configuration) {
        log.info("Updating system configuration");
        log.debug("Input averageConsumptionTimeMinutes: {}", configuration.getAverageConsumptionTimeMinutes());
        
        // MULTI-TENANT: Require company context for updates
        Company company = CompanyContext.requireCurrentCompany();
        SystemConfiguration existingConfig = configurationRepository.findByCompany(company)
                .orElseThrow(() -> new IllegalStateException("System configuration not found for company: " + company.getSlug()));
        
        log.debug("Existing averageConsumptionTimeMinutes before update: {}", existingConfig.getAverageConsumptionTimeMinutes());
        
        // Update fields
        existingConfig.setRestaurantName(configuration.getRestaurantName());
        existingConfig.setSlogan(configuration.getSlogan());
        
        // Preserve restaurant logo URL if not provided (managed by separate upload endpoints)
        if (configuration.getRestaurantLogoUrl() != null && !configuration.getRestaurantLogoUrl().isEmpty()) {
            existingConfig.setRestaurantLogoUrl(configuration.getRestaurantLogoUrl());
        }
        // NOTE: System branding (systemName, systemSlogan, systemLogoUrl) is now GLOBAL
        // and managed by GlobalSystemConfigService, not per-company
        existingConfig.setAddress(configuration.getAddress());
        existingConfig.setPhone(configuration.getPhone());
        existingConfig.setEmail(configuration.getEmail());
        existingConfig.setTaxRate(configuration.getTaxRate());
        existingConfig.setAverageConsumptionTimeMinutes(configuration.getAverageConsumptionTimeMinutes());
        
        log.debug("Existing averageConsumptionTimeMinutes after update: {}", existingConfig.getAverageConsumptionTimeMinutes());
        
        if (configuration.getPaymentMethods() != null) {
            existingConfig.setPaymentMethods(configuration.getPaymentMethods());
        }
        
        // Update delivery payment methods if provided
        if (configuration.getDeliveryPaymentMethods() != null) {
            existingConfig.setDeliveryPaymentMethods(configuration.getDeliveryPaymentMethods());
        }
        
        SystemConfiguration saved = configurationRepository.save(existingConfig);
        invalidateConfigCache();
        log.info("System configuration updated successfully");
        return saved;
    }

    @Override
    public SystemConfiguration createInitialConfiguration(SystemConfiguration configuration) {
        log.info("Creating initial system configuration");
        
        if (configurationExists()) {
            throw new IllegalStateException("System configuration already exists. Use update instead.");
        }
        
        // Initialize payment methods if not set
        if (configuration.getPaymentMethods() == null || configuration.getPaymentMethods().isEmpty()) {
            Map<PaymentMethodType, Boolean> paymentMethods = new HashMap<>();
            paymentMethods.put(PaymentMethodType.CASH, true);
            paymentMethods.put(PaymentMethodType.CREDIT_CARD, true);
            paymentMethods.put(PaymentMethodType.DEBIT_CARD, true);
            configuration.setPaymentMethods(paymentMethods);
        }
        
        // Initialize delivery payment methods if not set
        if (configuration.getDeliveryPaymentMethods() == null || configuration.getDeliveryPaymentMethods().isEmpty()) {
            Map<PaymentMethodType, Boolean> deliveryPaymentMethods = new HashMap<>();
            deliveryPaymentMethods.put(PaymentMethodType.CASH, true); // Cash enabled by default for delivery
            deliveryPaymentMethods.put(PaymentMethodType.CREDIT_CARD, false);
            deliveryPaymentMethods.put(PaymentMethodType.DEBIT_CARD, false);
            deliveryPaymentMethods.put(PaymentMethodType.TRANSFER, false);
            configuration.setDeliveryPaymentMethods(deliveryPaymentMethods);
        }
        
        SystemConfiguration saved = configurationRepository.save(configuration);
        log.info("Initial system configuration created successfully");
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean configurationExists() {
        return configurationRepository.existsConfiguration();
    }

    @Override
    public SystemConfiguration updatePaymentMethods(Map<PaymentMethodType, Boolean> paymentMethods) {
        log.info("Updating payment methods");
        
        if (paymentMethods == null || paymentMethods.isEmpty()) {
            throw new IllegalArgumentException("Payment methods cannot be empty");
        }
        
        SystemConfiguration config = configurationRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("System configuration not found"));
        config.setPaymentMethods(paymentMethods);
        
        SystemConfiguration saved = configurationRepository.save(config);
        invalidateConfigCache();
        log.info("Payment methods updated successfully");
        return saved;
    }

    @Override
    public SystemConfiguration updateTaxRate(BigDecimal taxRate) {
        log.info("Updating tax rate to: {}", taxRate);
        
        if (taxRate == null || taxRate.compareTo(BigDecimal.ZERO) < 0 || taxRate.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Tax rate must be between 0 and 100");
        }
        
        SystemConfiguration config = configurationRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("System configuration not found"));
        config.setTaxRate(taxRate);
        
        SystemConfiguration saved = configurationRepository.save(config);
        invalidateConfigCache();
        log.info("Tax rate updated successfully");
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isWorkDay(DayOfWeek day) {
        return configurationRepository.findFirstByOrderByIdAsc()
                .map(config -> config.isWorkDay(day))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPaymentMethodEnabled(PaymentMethodType type) {
        return configurationRepository.findFirstByOrderByIdAsc()
                .map(config -> config.isPaymentMethodEnabled(type))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public String getRestaurantName() {
        return configurationRepository.findFirstByOrderByIdAsc()
                .map(SystemConfiguration::getRestaurantName)
                .orElse("Restaurante");
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTaxRate() {
        return configurationRepository.findFirstByOrderByIdAsc()
                .map(SystemConfiguration::getTaxRate)
                .orElse(new BigDecimal("16.00"));
    }

    /**
     * Create a default configuration if none exists
     */
    private SystemConfiguration createDefaultConfiguration() {
        log.info("Creating default system configuration");
        
        // Define default work days (Monday to Saturday)
        Set<DayOfWeek> defaultWorkDays = new HashSet<>(Arrays.asList(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
        ));
        
        Map<PaymentMethodType, Boolean> defaultPaymentMethods = new HashMap<>();
        defaultPaymentMethods.put(PaymentMethodType.CASH, true);
        defaultPaymentMethods.put(PaymentMethodType.CREDIT_CARD, true);
        defaultPaymentMethods.put(PaymentMethodType.DEBIT_CARD, true);
        
        SystemConfiguration defaultConfig = SystemConfiguration.builder()
                .restaurantName("Mi Restaurante")
                .slogan("El mejor sabor de la ciudad")
                // NOTE: System branding (systemName, systemSlogan, systemLogoUrl) is now GLOBAL
                // and managed by GlobalSystemConfig entity, not per-company
                .address("Dirección no configurada")
                .phone("0000-0000")
                .email("contacto@restaurant.com")
                .taxRate(new BigDecimal("16.00"))
                .averageConsumptionTimeMinutes(120) // 2 hours default
                .paymentMethods(defaultPaymentMethods)
                .build();
        
        // Save configuration first to get the ID
        SystemConfiguration saved = configurationRepository.save(defaultConfig);
        log.info("Default system configuration created with ID: {}", saved.getId());
        
        // Create default business hours for all days
        log.info("Creating default business hours for all days");
        for (DayOfWeek day : DayOfWeek.values()) {
            boolean isWorkDay = defaultWorkDays.contains(day);
            BusinessHours hours = BusinessHours.builder()
                    .dayOfWeek(day)
                    .openTime(java.time.LocalTime.of(8, 0))   // 8:00 AM
                    .closeTime(java.time.LocalTime.of(22, 0))  // 10:00 PM
                    .isClosed(!isWorkDay) // Closed if not a work day (Sunday closed)
                    .systemConfiguration(saved)
                    .build();
            saved.addBusinessHours(hours);
            log.debug("Created business hours for {}: {} - {} (closed: {})", 
                    day.getDisplayName(), hours.getOpenTime(), hours.getCloseTime(), hours.getIsClosed());
        }
        
        // Save again with business hours
        saved = configurationRepository.save(saved);
        log.info("Default business hours created successfully for all 7 days");
        
        return saved;
    }
}
