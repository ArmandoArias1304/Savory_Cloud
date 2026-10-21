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

    @NotNull(message = "El precio es requerido")
    @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El precio debe tener máximo 8 dígitos y 2 decimales")
    @Column(name = "price", precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

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
        return discountPrice != null
                && price != null
                && discountPrice.compareTo(BigDecimal.ZERO) > 0
                && discountPrice.compareTo(price) < 0;
    }

    /** Price the customer actually pays. */
    public BigDecimal getFinalPrice() {
        return isOnSale() ? discountPrice : price;
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
        if (this.sortOrder == null) {
            this.sortOrder = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
