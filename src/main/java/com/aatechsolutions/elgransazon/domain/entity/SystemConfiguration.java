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

    // Child flag (only evaluated when enableOrderStatusPermission = TRUE).
    // When TRUE, ONLY admin/manager/cashier can advance the status of DELIVERY orders
    // (READY -> ON_THE_WAY "En camino" -> DELIVERED "Entregado") with a single click,
    // exactly like the repartidor does. The waiter is intentionally excluded from this
    // permission, and the repartidor keeps their own ability regardless of this flag.
    @Column(name = "staff_can_manage_delivery_orders", nullable = false,
            columnDefinition = "boolean not null default false")
    @Builder.Default
    private Boolean staffCanManageDeliveryOrders = false;

    // ========== Waiter/Delivery Collection Permission ==========
    // When TRUE (default), waiters and delivery staff CAN collect payments
    // (waiters: credit/debit cards only; delivery: card/transfer from delivery
    // payment methods — never cash).
    // When FALSE, waiters and delivery staff can only advance orders up to DELIVERED
    // status — the charge button is hidden and payment endpoints are blocked;
    // only cashier/admin/manager can collect. Backward compatible (default TRUE).
    @Column(name = "waiter_delivery_can_collect", nullable = false,
            columnDefinition = "boolean not null default true")
    @Builder.Default
    private Boolean waiterDeliveryCanCollect = true;

    // Ticket logo intensity (10-100%). Controls how dark/visible the logo prints on thermal tickets.
    // Higher value = darker print (more pixels become black dots). 50% ≈ original threshold.
    // Useful for logos with light colors that don't print well on thermal printers.
    @NotNull(message = "La intensidad del logo del ticket es obligatoria")
    @Min(value = 10, message = "La intensidad mínima es 10%")
    @Max(value = 100, message = "La intensidad máxima es 100%")
    @Column(name = "ticket_logo_opacity", nullable = false, columnDefinition = "integer not null default 50")
    @Builder.Default
    private Integer ticketLogoOpacity = 50; // Default: 50% (original threshold ~128)

    /**
     * Legends printed at the bottom of the ticket, right below the totals
     * (first line bold, second line small). Blank/null falls back to the text the
     * ticket has always printed, so rows created before these columns existed —
     * and any ticket printed without configuring them — keep working unchanged.
     * Both the thermal ticket (TicketEscPosService) and the PDF ticket
     * (TicketPdfService) read them, so what the preview shows is what prints.
     */
    public static final String DEFAULT_TICKET_FOOTER_LINE_1 = "\u00A1Gracias por su preferencia!";
    public static final String DEFAULT_TICKET_FOOTER_LINE_2 = "Esperamos volver a atenderle pronto";

    @Size(max = 40, message = "La primera leyenda del ticket no puede exceder los 40 caracteres")
    @Column(name = "ticket_footer_line1", length = 40)
    private String ticketFooterLine1;

    @Size(max = 40, message = "La segunda leyenda del ticket no puede exceder los 40 caracteres")
    @Column(name = "ticket_footer_line2", length = 40)
    private String ticketFooterLine2;

    /**
     * First ticket legend. Never blank: falls back to the built-in text.
     */
    public String getTicketFooterLine1() {
        return (ticketFooterLine1 == null || ticketFooterLine1.isBlank())
                ? DEFAULT_TICKET_FOOTER_LINE_1
                : ticketFooterLine1.trim();
    }

    /**
     * Second ticket legend. Never blank: falls back to the built-in text.
     */
    public String getTicketFooterLine2() {
        return (ticketFooterLine2 == null || ticketFooterLine2.isBlank())
                ? DEFAULT_TICKET_FOOTER_LINE_2
                : ticketFooterLine2.trim();
    }

    // ======================================================================
    // Printed menu ("carta") style
    // ======================================================================
    // Style used by MenuPdfService to print the menu card the restaurant hands to
    // its guests: typography, size, colors, paper and what to include. Every column
    // is nullable and every getter resolves null/blank to the built-in default, so
    // rows created before these columns existed print the same design as always and
    // no migration is needed. Managed from the menu view (Personalizar carta).

    public static final String DEFAULT_MENU_FONT_FAMILY = "MODERNA";
    public static final int DEFAULT_MENU_FONT_SIZE = 11;
    public static final String DEFAULT_MENU_PRIMARY_COLOR = "#1F2937";
    public static final String DEFAULT_MENU_ACCENT_COLOR = "#6B7280";
    public static final String DEFAULT_MENU_PAGE_COLOR = "#ffffff";
    public static final String DEFAULT_MENU_PAPER_SIZE = "LETTER";
    public static final int DEFAULT_MENU_COLUMNS = 1;

    /** Typography of the carta: MODERNA, CLASICA, REDONDA, ELEGANTE or ESTANDAR. */
    @Size(max = 20, message = "La tipografía de la carta no es válida")
    @Column(name = "menu_font_family", length = 20)
    private String menuFontFamily;

    /** Base body size in points (8-16); headings scale relative to it. */
    @Min(value = 8, message = "El tamaño de letra mínimo es 8")
    @Max(value = 16, message = "El tamaño de letra máximo es 16")
    @Column(name = "menu_font_size")
    private Integer menuFontSize;

    /** Hex color (#RRGGBB) for titles, category headers and the header rule. */
    @Size(max = 9, message = "El color principal de la carta no es válido")
    @Column(name = "menu_primary_color", length = 9)
    private String menuPrimaryColor;

    /** Hex color (#RRGGBB) for prices, descriptions and separators. */
    @Size(max = 9, message = "El color de acento de la carta no es válido")
    @Column(name = "menu_accent_color", length = 9)
    private String menuAccentColor;

    /**
     * Background color painted on the whole sheet (the "paper" of the carta), #RRGGBB.
     * White means plain paper and nothing is painted behind the text.
     */
    @Size(max = 9, message = "El color de la hoja de la carta no es válido")
    @Column(name = "menu_page_color", length = 9)
    private String menuPageColor;

    /** Sheet size of the carta: LETTER, A4, HALF_LETTER or A5. */
    @Size(max = 20, message = "El formato de hoja de la carta no es válido")
    @Column(name = "menu_paper_size", length = 20)
    private String menuPaperSize;

    /** Columns of the carta body: 1 or 2. */
    @Min(value = 1, message = "La carta debe tener al menos una columna")
    @Max(value = 2, message = "La carta admite como máximo dos columnas")
    @Column(name = "menu_columns")
    private Integer menuColumns;

    /** Print the short description under each dish name. */
    @Column(name = "menu_show_descriptions")
    private Boolean menuShowDescriptions;

    /** Print the dish photo (downloaded from its Cloudflare image URL) next to the name. */
    @Column(name = "menu_show_images")
    private Boolean menuShowImages;

    /** Print prices. Turn it off for a table carta with no prices. */
    @Column(name = "menu_show_prices")
    private Boolean menuShowPrices;

    /** Include dishes flagged as "agotado" (available = false). */
    @Column(name = "menu_include_unavailable")
    private Boolean menuIncludeUnavailable;

    /** Print a QR code (in the header) that opens the digital menu. */
    @Column(name = "menu_show_qr")
    private Boolean menuShowQr;

    /** Carta typography. Never blank: falls back to the built-in one. */
    public String getMenuFontFamily() {
        return (menuFontFamily == null || menuFontFamily.isBlank())
                ? DEFAULT_MENU_FONT_FAMILY
                : menuFontFamily.trim().toUpperCase(Locale.ROOT);
    }

    /** Carta base font size, clamped to the range the generator supports. */
    public int getMenuFontSize() {
        if (menuFontSize == null) {
            return DEFAULT_MENU_FONT_SIZE;
        }
        return Math.max(8, Math.min(16, menuFontSize));
    }

    /** Carta primary color. Never blank: falls back to the built-in one. */
    public String getMenuPrimaryColor() {
        return (menuPrimaryColor == null || menuPrimaryColor.isBlank())
                ? DEFAULT_MENU_PRIMARY_COLOR
                : menuPrimaryColor.trim();
    }

    /** Carta accent color. Never blank: falls back to the built-in one. */
    public String getMenuAccentColor() {
        return (menuAccentColor == null || menuAccentColor.isBlank())
                ? DEFAULT_MENU_ACCENT_COLOR
                : menuAccentColor.trim();
    }

    /** Background color of the carta sheet. Never blank: falls back to plain white. */
    public String getMenuPageColor() {
        return (menuPageColor == null || menuPageColor.isBlank())
                ? DEFAULT_MENU_PAGE_COLOR
                : menuPageColor.trim();
    }

    /** Carta paper size. Never blank: falls back to the built-in one. */
    public String getMenuPaperSize() {
        return (menuPaperSize == null || menuPaperSize.isBlank())
                ? DEFAULT_MENU_PAPER_SIZE
                : menuPaperSize.trim().toUpperCase(Locale.ROOT);
    }

    /** Carta column count, clamped to 1-2. */
    public int getMenuColumns() {
        if (menuColumns == null) {
            return DEFAULT_MENU_COLUMNS;
        }
        return Math.max(1, Math.min(2, menuColumns));
    }

    /**
     * Whether descriptions print. Declared as getXxx (not isXxx) on purpose: Lombok
     * only skips generating a getter when the name matches, so a matching getter
     * keeps a single, always-defaulted accessor for Thymeleaf and Jackson.
     */
    public boolean getMenuShowDescriptions() {
        return menuShowDescriptions == null || menuShowDescriptions;
    }

    public boolean getMenuShowImages() {
        return menuShowImages != null && menuShowImages;
    }

    public boolean getMenuShowPrices() {
        return menuShowPrices == null || menuShowPrices;
    }

    public boolean getMenuIncludeUnavailable() {
        return menuIncludeUnavailable != null && menuIncludeUnavailable;
    }

    public boolean getMenuShowQr() {
        return menuShowQr != null && menuShowQr;
    }

    // Declared payment methods when creating an order (customer and staff).
    // Same list for dine-in, takeout and delivery. Rider collection uses
    // deliveryPaymentMethods instead (never cash).
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "system_payment_methods", joinColumns = @JoinColumn(name = "system_configuration_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "enabled")
    @Builder.Default
    private Map<PaymentMethodType, Boolean> paymentMethods = new HashMap<>();

    // What the delivery person may collect at the door (never cash: cash goes to caja).
    // Distinct from restaurant paymentMethods, which the customer sees when ordering.
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
        if (this.deliveryPaymentMethods != null) {
            this.deliveryPaymentMethods.put(PaymentMethodType.CASH, false);
        }
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
        if (this.deliveryPaymentMethods == null || this.deliveryPaymentMethods.isEmpty()) {
            this.deliveryPaymentMethods = new HashMap<>();
            this.deliveryPaymentMethods.put(PaymentMethodType.CASH, false);
            this.deliveryPaymentMethods.put(PaymentMethodType.CREDIT_CARD, false);
            this.deliveryPaymentMethods.put(PaymentMethodType.DEBIT_CARD, false);
            this.deliveryPaymentMethods.put(PaymentMethodType.TRANSFER, false);
        } else {
            this.deliveryPaymentMethods.put(PaymentMethodType.CASH, false);
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

    /**
     * Whether the delivery person may collect with {@code type}. Cash is always
     * false: physical cash is handed to caja (admin/manager/cashier).
     */
    public boolean isDeliveryPaymentMethodEnabled(PaymentMethodType type) {
        if (type == null || type == PaymentMethodType.CASH) {
            return false;
        }
        return deliveryPaymentMethods != null
                && Boolean.TRUE.equals(deliveryPaymentMethods.getOrDefault(type, false));
    }

    public boolean hasAnyDeliveryPaymentMethodEnabled() {
        if (deliveryPaymentMethods == null || deliveryPaymentMethods.isEmpty()) {
            return false;
        }
        return deliveryPaymentMethods.entrySet().stream()
                .anyMatch(e -> e.getKey() != PaymentMethodType.CASH && Boolean.TRUE.equals(e.getValue()));
    }

    /**
     * Declared method when creating an order (customer or staff). Always the
     * restaurant methods, for dine-in, takeout and delivery. Delivery-person
     * collection uses {@link #isDeliveryPaymentMethodEnabled}.
     */
    public boolean isPaymentMethodEnabledForOrderType(PaymentMethodType type, OrderType orderType) {
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
