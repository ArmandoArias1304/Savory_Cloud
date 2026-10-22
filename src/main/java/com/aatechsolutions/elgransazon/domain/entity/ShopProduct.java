package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Product of the platform shop: physical or digital goods the programmer sells
 * to restaurants (QR cards, printed menus, table stands, etc.).
 *
 * <p>GLOBAL entity: the catalogue is shared by every company, it is not part of
 * a restaurant's inventory. Restaurant assets are modelled by
 * {@link InventoryObject} instead.</p>
 */
@Entity
@Table(name = "shop_products", indexes = {
    @Index(name = "idx_shop_product_active", columnList = "active"),
    @Index(name = "idx_shop_product_category", columnList = "category")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idProduct"})
@ToString
public class ShopProduct implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_product")
    private Long idProduct;

    @NotBlank(message = "El nombre del producto es requerido")
    @Size(max = 120, message = "El nombre no puede exceder 120 caracteres")
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Size(max = 2000, message = "La descripción no puede exceder 2000 caracteres")
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * How the price is published. With a range it is the minimum; a quote has
     * no published amount and stores 0 so the NOT NULL column stays valid.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_mode", length = 20)
    @Builder.Default
    private ShopPricingMode pricingMode = ShopPricingMode.FIXED;

    // Required for the fixed and range modes; the controller reports which one.
    @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El precio debe tener máximo 8 dígitos y 2 decimales")
    @Column(name = "price", precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    /** Upper bound of the range mode; null for fixed and quote prices. */
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio máximo debe ser mayor a 0")
    @Digits(integer = 8, fraction = 2, message = "El precio máximo debe tener máximo 8 dígitos y 2 decimales")
    @Column(name = "max_price", precision = 10, scale = 2)
    private BigDecimal maxPrice;

    /** What the price depends on: distance, headcount, scope... */
    @Size(max = 300, message = "La nota de precio no puede exceder 300 caracteres")
    @Column(name = "price_note", length = 300)
    private String priceNote;

    @Size(max = 3, message = "El código de moneda debe tener 3 caracteres")
    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "MXN";

    @Size(max = 60, message = "La categoría no puede exceder 60 caracteres")
    @Column(name = "category", length = 60)
    private String category;

    @Size(max = 500, message = "La URL de la imagen no puede exceder 500 caracteres")
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * Promotional price shown instead of {@link #price} while the offer lasts.
     * {@code null} means the product is sold at its regular price.
     */
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio con descuento debe ser mayor a 0")
    @Digits(integer = 8, fraction = 2, message = "El precio con descuento debe tener máximo 8 dígitos y 2 decimales")
    @Column(name = "discount_price", precision = 10, scale = 2)
    private BigDecimal discountPrice;

    /**
     * How the restaurant is charged: once, or every month on top of its
     * subscription. Existing rows (created before this field existed) are
     * treated as one-time payments, which is what every product used to be.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type", length = 20)
    @Builder.Default
    private ShopBillingType billingType = ShopBillingType.ONE_TIME;

    @Column(name = "featured", nullable = false)
    @Builder.Default
    private Boolean featured = false;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * True when the product is currently offered below its regular price.
     * A discount equal to or above the price is ignored so the store never
     * shows an offer that is not a real saving.
     */
    public boolean isOnSale() {
        return isFixedPrice()
                && discountPrice != null
                && price != null
                && discountPrice.compareTo(BigDecimal.ZERO) > 0
                && discountPrice.compareTo(price) < 0;
    }

    /** Pricing mode, defaulting to a fixed price when it was never set. */
    public ShopPricingMode getPricingModeOrDefault() {
        return pricingMode == null ? ShopPricingMode.FIXED : pricingMode;
    }

    /** Price published as one number (the only mode that can have a promotion). */
    public boolean isFixedPrice() {
        return getPricingModeOrDefault() == ShopPricingMode.FIXED;
    }

    /** Price published as a minimum and a maximum. */
    public boolean isPriceRange() {
        return getPricingModeOrDefault() == ShopPricingMode.RANGE;
    }

    /** No published price: the restaurant asks for a quote. */
    public boolean isQuote() {
        return getPricingModeOrDefault() == ShopPricingMode.QUOTE;
    }

    /** Human label of the pricing mode, e.g. {@code Precio a evaluar}. */
    public String getPricingLabel() {
        return getPricingModeOrDefault().getLabel();
    }

    /** Price the customer actually pays. */
    public BigDecimal getFinalPrice() {
        return isOnSale() ? discountPrice : price;
    }

    /** Billing type, defaulting to a one-time payment when it was never set. */
    public ShopBillingType getBillingTypeOrDefault() {
        return billingType == null ? ShopBillingType.ONE_TIME : billingType;
    }

    /** True when the price is charged every month. */
    public boolean isMonthly() {
        return getBillingTypeOrDefault() == ShopBillingType.MONTHLY;
    }

    /** Human label of the billing type, e.g. {@code Mensual}. */
    public String getBillingLabel() {
        return getBillingTypeOrDefault().getLabel();
    }

    /** Explanation shown next to the price, e.g. {@code Se suma a la mensualidad}. */
    public String getBillingDescription() {
        return getBillingTypeOrDefault().getDescription();
    }

    /** Suffix appended to the price: {@code  / mes} only for monthly products. */
    public String getBillingPeriodSuffix() {
        return isMonthly() ? " / mes" : "";
    }

    /** Whole percentage saved, e.g. {@code 20} for 499 → 399.20. */
    public int getDiscountPercent() {
        if (!isOnSale()) {
            return 0;
        }
        return price.subtract(discountPrice)
                .multiply(BigDecimal.valueOf(100))
                .divide(price, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    /** Amount saved when the promotion is active; zero otherwise. */
    public BigDecimal getSavingsAmount() {
        if (!isOnSale()) {
            return BigDecimal.ZERO;
        }
        return price.subtract(discountPrice);
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.currency == null || this.currency.isBlank()) {
            this.currency = "MXN";
        }
        if (this.active == null) {
            this.active = true;
        }
        if (this.featured == null) {
            this.featured = false;
        }
        if (this.billingType == null) {
            this.billingType = ShopBillingType.ONE_TIME;
        }
        if (this.pricingMode == null) {
            this.pricingMode = ShopPricingMode.FIXED;
        }
        if (this.sortOrder == null) {
            this.sortOrder = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
