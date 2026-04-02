package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * SystemConfiguration entity representing system settings per company
 * Each company has exactly one SystemConfiguration (OneToOne relationship)
 */
@Entity
@Table(name = "system_configuration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"company", "businessHours", "socialNetworks"})
public class SystemConfiguration implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ========== Company Relationship (Multi-Tenant) ==========
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @NotBlank(message = "El nombre del restaurante es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre del restaurante debe tener entre 2 y 100 caracteres")
    @Column(name = "restaurant_name", nullable = false, length = 100)
    private String restaurantName;

    @Size(max = 255, message = "El eslogan no puede exceder los 255 caracteres")
    @Column(name = "slogan", length = 255)
    private String slogan;

    // Restaurant logo URL (uploaded image, stored in DB)
    @Size(max = 500, message = "La URL del logo del restaurante no puede exceder los 500 caracteres")
    @Column(name = "restaurant_logo_url", length = 500)
    private String restaurantLogoUrl;

    // NOTE: System branding fields (systemName, systemSlogan, systemLogoUrl) have been moved to
    // GlobalSystemConfig entity for GLOBAL (not per-company) system branding.
    // See GlobalSystemConfig for system-wide branding.

    @Size(max = 13, message = "El RFC no puede exceder los 13 caracteres")
    @Pattern(regexp = "^$|^[A-Z&Ñ]{3,4}[0-9]{6}[A-Z0-9]{3}$", message = "El formato del RFC es inválido")
    @Column(name = "rfc", length = 13)
    private String rfc;

    @NotBlank(message = "La dirección es obligatoria")
    @Size(max = 500, message = "La dirección no puede exceder los 500 caracteres")
    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(regexp = "^[0-9]{10}$", message = "El teléfono debe tener exactamente 10 dígitos")
    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico es inválido")
    @Size(max = 100, message = "El correo electrónico no puede exceder los 100 caracteres")
    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @NotNull(message = "La tasa de impuestos es obligatoria")
    @DecimalMin(value = "0.0", message = "La tasa de impuestos debe ser al menos 0")
    @DecimalMax(value = "100.0", message = "La tasa de impuestos no puede exceder 100")
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @NotNull(message = "El tiempo promedio de consumo es obligatorio")
    @Min(value = 30, message = "El tiempo promedio de consumo debe ser al menos 30 minutos")
    @Max(value = 480, message = "El tiempo promedio de consumo no puede exceder los 480 minutos (8 horas)")
    @Column(name = "average_consumption_time_minutes", nullable = false)
    @Builder.Default
    private Integer averageConsumptionTimeMinutes = 120; // Default: 2 hours

    // Ticket logo intensity (10-100%). Controls how dark/visible the logo prints on thermal tickets.
    // Higher value = darker print (more pixels become black dots). 50% ≈ original threshold.
    // Useful for logos with light colors that don't print well on thermal printers.
    @NotNull(message = "La intensidad del logo del ticket es obligatoria")
    @Min(value = 10, message = "La intensidad mínima es 10%")
    @Max(value = 100, message = "La intensidad máxima es 100%")
    @Column(name = "ticket_logo_opacity", nullable = false, columnDefinition = "integer not null default 50")
    @Builder.Default
    private Integer ticketLogoOpacity = 50; // Default: 50% (original threshold ~128)

    // Payment methods with enable/disable status (for restaurant/in-house orders)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "system_payment_methods", joinColumns = @JoinColumn(name = "system_configuration_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "enabled")
    @Builder.Default
    private Map<PaymentMethodType, Boolean> paymentMethods = new HashMap<>();

    // Delivery payment methods with enable/disable status (separate from restaurant)
    // This allows disabling payment methods ONLY for delivery without affecting restaurant payments
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "system_delivery_payment_methods", joinColumns = @JoinColumn(name = "system_configuration_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "payment_method_type")
    @Column(name = "enabled")
    @Builder.Default
    private Map<PaymentMethodType, Boolean> deliveryPaymentMethods = new HashMap<>();

    @OneToMany(mappedBy = "systemConfiguration", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("dayOfWeek ASC")
    @Builder.Default
    private List<BusinessHours> businessHours = new ArrayList<>();

    @OneToMany(mappedBy = "systemConfiguration", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<SocialNetwork> socialNetworks = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        // Initialize payment methods if not set
        if (this.paymentMethods == null || this.paymentMethods.isEmpty()) {
            this.paymentMethods = new HashMap<>();
            this.paymentMethods.put(PaymentMethodType.CASH, true);
            this.paymentMethods.put(PaymentMethodType.CREDIT_CARD, true);
            this.paymentMethods.put(PaymentMethodType.DEBIT_CARD, true);
            this.paymentMethods.put(PaymentMethodType.TRANSFER, false); // Disabled by default
        }
        // Initialize delivery payment methods if not set
        // By default, only CASH is enabled for delivery
        if (this.deliveryPaymentMethods == null || this.deliveryPaymentMethods.isEmpty()) {
            this.deliveryPaymentMethods = new HashMap<>();
            this.deliveryPaymentMethods.put(PaymentMethodType.CASH, true); // Cash enabled by default for delivery
            this.deliveryPaymentMethods.put(PaymentMethodType.CREDIT_CARD, false); // Disabled by default
            this.deliveryPaymentMethods.put(PaymentMethodType.DEBIT_CARD, false); // Disabled by default
            this.deliveryPaymentMethods.put(PaymentMethodType.TRANSFER, false); // Disabled by default
        }
    }

    // Helper methods for managing business hours
    public void addBusinessHours(BusinessHours hours) {
        businessHours.add(hours);
        hours.setSystemConfiguration(this);
    }

    public void removeBusinessHours(BusinessHours hours) {
        businessHours.remove(hours);
        hours.setSystemConfiguration(null);
    }

    public void clearBusinessHours() {
        businessHours.forEach(hours -> hours.setSystemConfiguration(null));
        businessHours.clear();
    }

    // Helper methods for managing social networks
    public void addSocialNetwork(SocialNetwork network) {
        socialNetworks.add(network);
        network.setSystemConfiguration(this);
    }

    public void removeSocialNetwork(SocialNetwork network) {
        socialNetworks.remove(network);
        network.setSystemConfiguration(null);
    }

    public void clearSocialNetworks() {
        socialNetworks.forEach(network -> network.setSystemConfiguration(null));
        socialNetworks.clear();
    }

    // Helper method to check if a day is a work day
    // A day is a work day if it has business hours and is NOT closed
    public boolean isWorkDay(DayOfWeek day) {
        return businessHours.stream()
                .anyMatch(hours -> hours.getDayOfWeek().equals(day) && !hours.getIsClosed());
    }

    // Helper method to check if a payment method is enabled
    public boolean isPaymentMethodEnabled(PaymentMethodType type) {
        return paymentMethods.getOrDefault(type, false);
    }

    // Helper method to check if a delivery payment method is enabled
    public boolean isDeliveryPaymentMethodEnabled(PaymentMethodType type) {
        return deliveryPaymentMethods.getOrDefault(type, false);
    }

    /**
     * Check if a payment method is enabled based on order type
     * For DELIVERY orders, uses deliveryPaymentMethods
     * For other orders (DINE_IN, TAKEOUT), uses paymentMethods
     */
    public boolean isPaymentMethodEnabledForOrderType(PaymentMethodType type, OrderType orderType) {
        if (orderType == OrderType.DELIVERY) {
            return isDeliveryPaymentMethodEnabled(type);
        }
        return isPaymentMethodEnabled(type);
    }

    // Helper method to get active social networks
    public List<SocialNetwork> getActiveSocialNetworks() {
        return socialNetworks.stream()
                .filter(SocialNetwork::getActive)
                .toList();
    }

    // Helper method to get work days sorted
    // Returns all days that are NOT closed, sorted by ordinal
    public List<DayOfWeek> getSortedWorkDays() {
        return businessHours.stream()
                .filter(hours -> !hours.getIsClosed())
                .map(BusinessHours::getDayOfWeek)
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList();
    }

    // Get business hours for a specific day
    public Optional<BusinessHours> getBusinessHoursForDay(DayOfWeek day) {
        return businessHours.stream()
                .filter(hours -> hours.getDayOfWeek().equals(day))
                .findFirst();
    }

    /**
     * Get formatted average consumption time (e.g., "2 horas" or "90 minutos")
     */
    public String getAverageConsumptionTimeDisplay() {
        if (averageConsumptionTimeMinutes == null) {
            return "N/A";
        }
        
        int hours = averageConsumptionTimeMinutes / 60;
        int minutes = averageConsumptionTimeMinutes % 60;
        
        if (hours > 0 && minutes == 0) {
            return hours == 1 ? "1 hora" : hours + " horas";
        } else if (hours > 0) {
            return hours + "h " + minutes + "min";
        } else {
            return minutes + " minutos";
        }
    }
}
