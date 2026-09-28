package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.entity.DayOfWeek;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;

import java.util.Optional;
import java.util.Map;

/**
 * Service interface for SystemConfiguration business logic
 */
public interface SystemConfigurationService {

    /**
     * Get the system configuration (Singleton)
     * Creates a default one if it doesn't exist
     */
    SystemConfiguration getConfiguration();

    /**
     * Get configuration by ID
     */
    Optional<SystemConfiguration> getConfigurationById(Long id);

    /**
     * Update the system configuration
     * @param configuration the updated configuration data
     * @return the updated configuration
     */
    SystemConfiguration updateConfiguration(SystemConfiguration configuration);

    /**
     * Create initial configuration (only if none exists)
     * @param configuration the configuration to create
     * @return the created configuration
     * @throws IllegalStateException if configuration already exists
     */
    SystemConfiguration createInitialConfiguration(SystemConfiguration configuration);

    /**
     * Check if configuration exists
     */
    boolean configurationExists();

    /**
     * Update payment methods status
     */
    SystemConfiguration updatePaymentMethods(Map<PaymentMethodType, Boolean> paymentMethods);

    /**
     * Update tax rate
     */
    SystemConfiguration updateTaxRate(java.math.BigDecimal taxRate);

    /**
     * Check if restaurant is open on a specific day
     */
    boolean isWorkDay(DayOfWeek day);

    /**
     * Check if a payment method is enabled
     */
    boolean isPaymentMethodEnabled(PaymentMethodType type);

    /**
     * Get restaurant name
     */
    String getRestaurantName();

    /**
     * Get tax rate
     */
    java.math.BigDecimal getTaxRate();

    /**
     * Persist the printed-menu ("carta") style of the current company.
     *
     * <p>Only the carta columns are touched: the general configuration form
     * ({@link #updateConfiguration(SystemConfiguration)}) deliberately ignores them, so the
     * two entry points never overwrite each other. Invalidates the configuration cache so
     * the next preview or print already uses the new style.</p>
     *
     * @param style style chosen in the menu view (null falls back to the built-in design)
     * @return the saved configuration
     */
    SystemConfiguration updateMenuStyle(com.aatechsolutions.elgransazon.application.dto.MenuStyle style);
}
