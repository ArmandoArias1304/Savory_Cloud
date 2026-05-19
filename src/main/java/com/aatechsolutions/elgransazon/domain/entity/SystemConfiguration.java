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

    // ========== Default Delivery Cost ==========
    // Default amount charged for DELIVERY orders. Includes IVA.
    // Staff can override per order in admin/waiter/cashier; clients always use this default.
    @NotNull(message = "El costo de envío por defecto es obligatorio")
    @DecimalMin(value = "0.0", message = "El costo de envío no puede ser negativo")
    @DecimalMax(value = "999999.99", message = "El costo de envío no puede ser mayor a $999,999.99")
    @Digits(integer = 6, fraction = 2, message = "El costo de envío solo permite hasta 2 decimales")
    @Column(name = "default_delivery_cost", nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal defaultDeliveryCost = BigDecimal.ZERO;

    // ========== Restaurant Geolocation (for delivery range validation) ==========
    // Nullable: when both coords + maxDistance are configured, delivery is restricted
    // to addresses within the configured straight-line radius (meters) from these coords.
    @DecimalMin(value = "-90.0", message = "Latitud inválida")
    @DecimalMax(value = "90.0", message = "Latitud inválida")
    @Column(name = "restaurant_latitude")
    private Double restaurantLatitude;

    @DecimalMin(value = "-180.0", message = "Longitud inválida")
    @DecimalMax(value = "180.0", message = "Longitud inválida")
    @Column(name = "restaurant_longitude")
    private Double restaurantLongitude;

    // Max delivery distance in METERS (straight-line). Null or <=0 disables the check.
    @Min(value = 1, message = "La distancia máxima debe ser al menos 1 metro")
    @Max(value = 1_000_000, message = "La distancia máxima no puede exceder 1,000,000 metros")
    @Column(name = "delivery_max_distance_meters")
    private Integer deliveryMaxDistanceMeters;

    @NotNull(message = "El tiempo promedio de consumo es obligatorio")
    @Min(value = 30, message = "El tiempo promedio de consumo debe ser al menos 30 minutos")
    @Max(value = 480, message = "El tiempo promedio de consumo no puede exceder los 480 minutos (8 horas)")
    @Column(name = "average_consumption_time_minutes", nullable = false)
    @Builder.Default
    private Integer averageConsumptionTimeMinutes = 120; // Default: 2 hours

    // ========== Customer Order Acceptance ==========
    // When TRUE, orders created or items added by customers (ROLE_CLIENT) start in TO_ACCEPT
    // status and ingredient stock is NOT deducted until an admin/manager/cashier accepts them.
    // When FALSE (default), the legacy behavior applies: items start in PENDING and stock is
    // deducted immediately on order creation. Backward compatible.
    @Column(name = "require_customer_order_acceptance", nullable = false,
            columnDefinition = "boolean not null default false")
    @Builder.Default
    private Boolean requireCustomerOrderAcceptance = false;

    // ========== Staff Order Status Permission ==========
    // Parent flag. When TRUE, staff (waiter/cashier/admin/manager) can advance the status of
    // items that require chef/barista preparation (PENDING -> IN_PREPARATION -> READY) with
    // a single click on the orders list, instead of relying on chef/barista accepting them.
    // When FALSE (default), only chef/barista can manage those item statuses. Backward compatible.
    @Column(name = "enable_order_status_permission", nullable = false,
            columnDefinition = "boolean not null default false")
    @Builder.Default
    private Boolean enableOrderStatusPermission = false;

    // Child flag (only evaluated when enableOrderStatusPermission = TRUE).
    // When TRUE, staff can advance items that require chef preparation (requiresPreparation=true).
    // When both child flags are TRUE, staff manages chef + barista items together with one click.
    @Column(name = "staff_can_manage_chef_items", nullable = false,
            columnDefinition = "boolean not null default false")
    @Builder.Default
    private Boolean staffCanManageChefItems = false;

    // Child flag (only evaluated when enableOrderStatusPermission = TRUE).
    // When TRUE, staff can advance items that require barista preparation (requiresBaristaPreparation=true).
    @Column(name = "staff_can_manage_barista_items", nullable = false,
            columnDefinition = "boolean not null default false")
    @Builder.Default
    private Boolean staffCanManageBaristaItems = false;

    // Child flag (only evaluated when enableOrderStatusPermission = TRUE).
    // When TRUE, staff can advance items that require parrillero preparation (requiresParrilleroPreparation=true).
    @Column(name = "staff_can_manage_parrillero_items", nullable = false,
            columnDefinition = "boolean not null default false")
    @Builder.Default
    private Boolean staffCanManageParrilleroItems = false;

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
    @OrderBy("name ASC")
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

    // ========== Delivery Range Helpers (Haversine) ==========

    /**
     * True only when the admin has configured BOTH the restaurant coords AND a positive max distance.
     * If false, no delivery distance check should be performed.
     */
    public boolean hasDeliveryRangeRestriction() {
        return restaurantLatitude != null
                && restaurantLongitude != null
                && deliveryMaxDistanceMeters != null
                && deliveryMaxDistanceMeters > 0;
    }

    /**
     * Straight-line (great-circle) distance in METERS between the configured restaurant
     * coordinates and the given point, using the Haversine formula.
     * Returns null if the restaurant coords are not configured.
     */
    public Double distanceToInMeters(Double targetLatitude, Double targetLongitude) {
        if (restaurantLatitude == null || restaurantLongitude == null
                || targetLatitude == null || targetLongitude == null) {
            return null;
        }
        final double earthRadiusMeters = 6_371_000d;
        double lat1Rad = Math.toRadians(restaurantLatitude);
        double lat2Rad = Math.toRadians(targetLatitude);
        double dLat = Math.toRadians(targetLatitude - restaurantLatitude);
        double dLng = Math.toRadians(targetLongitude - restaurantLongitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusMeters * c;
    }

    /**
     * Returns true when the given coords are within the configured delivery range,
     * OR when no restriction has been configured (open policy).
     */
    public boolean isWithinDeliveryRange(Double targetLatitude, Double targetLongitude) {
        if (!hasDeliveryRangeRestriction()) {
            return true;
        }
        Double distance = distanceToInMeters(targetLatitude, targetLongitude);
        if (distance == null) {
            // Restriction is configured but target coords missing → reject (be safe).
            return false;
        }
        return distance <= deliveryMaxDistanceMeters;
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
