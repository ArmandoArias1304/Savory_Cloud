package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.aatechsolutions.elgransazon.infrastructure.util.CompanyLocalTime;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Promotion entity representing promotional offers for menu items
 * Supports three types: Buy X Pay Y, Percentage Discount, and Fixed Amount Discount
 */
@Entity
@Table(name = "promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idPromotion"})
@ToString(exclude = {"company", "items"})
public class Promotion implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_promotion")
    private Long idPromotion;

    // ========== Company Relationship (Multi-Tenant) ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotBlank(message = "El nombre de la promoción es requerido")
    @Size(min = 3, max = 200, message = "El nombre debe tener entre 3 y 200 caracteres")
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Size(max = 1000, message = "La descripción no puede exceder 1000 caracteres")
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Size(max = 500, message = "La URL de la imagen no puede exceder 500 caracteres")
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // ========== Promotion Type ==========

    @NotNull(message = "El tipo de promoción es requerido")
    @Enumerated(EnumType.STRING)
    @Column(name = "promotion_type", nullable = false, length = 30)
    private PromotionType promotionType;

    // ========== Discount Configuration ==========

    /**
     * For BUY_X_PAY_Y: The quantity to buy (X)
     * Example: Buy 2 (buyQuantity=2), Pay 1 (payQuantity=1)
     */
    @Min(value = 1, message = "La cantidad a comprar debe ser al menos 1")
    @Max(value = 999999, message = "La cantidad a comprar no puede exceder 999,999")
    @Column(name = "buy_quantity")
    private Integer buyQuantity;

    /**
     * For BUY_X_PAY_Y: The quantity to pay for (Y)
     * Example: Buy 2 (buyQuantity=2), Pay 1 (payQuantity=1)
     */
    @Min(value = 1, message = "La cantidad a pagar debe ser al menos 1")
    @Max(value = 999999, message = "La cantidad a pagar no puede exceder 999,999")
    @Column(name = "pay_quantity")
    private Integer payQuantity;

    /**
     * For PERCENTAGE_DISCOUNT: The discount percentage (0-100)
     * Example: 20.00 for 20% off
     */
    @DecimalMin(value = "0.0", message = "El porcentaje debe ser al menos 0")
    @DecimalMax(value = "100.0", message = "El porcentaje no puede exceder 100")
    @Digits(integer = 3, fraction = 2, message = "El porcentaje debe tener máximo 3 dígitos y 2 decimales")
    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    /**
     * For FIXED_AMOUNT_DISCOUNT: The fixed discount amount
     * Example: 5.00 for $5 off
     */
    @DecimalMin(value = "0.0", inclusive = false, message = "El monto debe ser mayor a 0")
    @DecimalMax(value = "999999.99", message = "El monto no puede exceder $999,999.99")
    @Digits(integer = 6, fraction = 2, message = "El monto debe tener máximo 6 dígitos y 2 decimales")
    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount;

    /**
     * For FIXED_AMOUNT_DISCOUNT: Minimum quantity required to apply the discount
     * The discount only applies when quantity is a multiple of this value
     * Example: minQuantityForFixedDiscount=2 means discount applies at 2, 4, 6... items
     * If null or 1, discount applies to every item
     */
    @Min(value = 1, message = "La cantidad mínima debe ser al menos 1")
    @Max(value = 999999, message = "La cantidad mínima no puede exceder 999,999")
    @Column(name = "min_quantity_for_fixed_discount")
    private Integer minQuantityForFixedDiscount;

    // ========== Validity Period ==========

    @NotNull(message = "La fecha de inicio es requerida")
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @NotNull(message = "La fecha de fin es requerida")
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * Days of week when promotion is valid
     * Stored as comma-separated values: MONDAY,FRIDAY,SATURDAY
     * Note: @NotBlank removed because controller sets this from daysOfWeek param
     * and validates manually via bindingResult.rejectValue()
     */
    @Column(name = "valid_days", nullable = false, length = 100)
    private String validDays;

    // ========== Status and Priority ==========

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    // ========== Relationships ==========

    /**
     * Many-to-Many relationship with ItemMenu
     * A promotion can apply to many items, and an item can have many promotions
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "promotion_items",
        joinColumns = @JoinColumn(name = "id_promotion"),
        inverseJoinColumns = @JoinColumn(name = "id_item_menu")
    )
    @Builder.Default
    private List<ItemMenu> items = new ArrayList<>();

    // ========== Timestamps ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.active == null) {
            this.active = true;
        }
        if (this.deleted == null) {
            this.deleted = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========== Business Methods ==========

    /**
     * Add an item to this promotion
     */
    public void addItem(ItemMenu item) {
        if (this.items == null) {
            this.items = new ArrayList<>();
        }
        this.items.add(item);
    }

    /**
     * Remove an item from this promotion
     */
    public void removeItem(ItemMenu item) {
        if (this.items != null) {
            this.items.remove(item);
        }
    }

    /**
     * Clear all items from this promotion
     */
    public void clearItems() {
        if (this.items != null) {
            this.items.clear();
        }
    }

    /**
     * Check if promotion is currently valid (date and active status)
     */
    public boolean isValidNow() {
        LocalDate today = CompanyLocalTime.today();
        return Boolean.TRUE.equals(active) 
            && !today.isBefore(startDate) 
            && !today.isAfter(endDate)
            && isValidForDay(today.getDayOfWeek());
    }

    /**
     * Check if promotion is valid for a specific day of week
     */
    public boolean isValidForDay(DayOfWeek dayOfWeek) {
        if (validDays == null || validDays.isEmpty()) {
            return false;
        }
        return validDays.contains(dayOfWeek.name());
    }

    /**
     * Get list of valid days as DayOfWeek enum
     */
    public Set<DayOfWeek> getValidDaysSet() {
        Set<DayOfWeek> days = new HashSet<>();
        if (validDays != null && !validDays.isEmpty()) {
            String[] dayArray = validDays.split(",");
            for (String day : dayArray) {
                try {
                    days.add(DayOfWeek.valueOf(day.trim()));
                } catch (IllegalArgumentException e) {
                    // Skip invalid day
                }
            }
        }
        return days;
    }

    /**
     * Set valid days from a set of DayOfWeek
     */
    public void setValidDaysFromSet(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            this.validDays = "";
            return;
        }
        this.validDays = String.join(",", 
            days.stream()
                .map(DayOfWeek::name)
                .sorted()
                .toArray(String[]::new)
        );
    }

    /**
     * Calculate discounted price for an item
     * @param originalPrice The original price of the item
     * @param quantity The quantity being purchased
     * @return The discounted price (total for all items)
     */
    public BigDecimal calculateDiscountedPrice(BigDecimal originalPrice, int quantity) {
        if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        switch (promotionType) {
            case BUY_X_PAY_Y:
                return calculateBuyXPayY(originalPrice, quantity);
            
            case PERCENTAGE_DISCOUNT:
                return calculatePercentageDiscount(originalPrice, quantity);
            
            case FIXED_AMOUNT_DISCOUNT:
                return calculateFixedDiscount(originalPrice, quantity);
            
            default:
                return originalPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    /**
     * Calculate price for Buy X Pay Y promotion
     * Example: 3x2 (Buy 3, Pay 2) means customer buys 3 items but only pays for 2
     */
    private BigDecimal calculateBuyXPayY(BigDecimal originalPrice, int quantity) {
        if (buyQuantity == null || payQuantity == null || buyQuantity <= 0 || payQuantity <= 0) {
            return originalPrice.multiply(BigDecimal.valueOf(quantity));
        }

        // Validate configuration: buyQuantity must be greater than payQuantity
        if (buyQuantity <= payQuantity) {
            // Invalid configuration, return full price
            return originalPrice.multiply(BigDecimal.valueOf(quantity));
        }

        // Calculate how many complete "sets" of the promotion
        // Example: 3x2 with 7 items = 2 complete sets (6 items) + 1 remaining
        int promotionSets = quantity / buyQuantity;
        int remainingItems = quantity % buyQuantity;

        // For each complete set, customer pays for only 'payQuantity' items
        // Example: 2 sets × 2 items to pay = 4 items at full price
        int itemsToPay = promotionSets * payQuantity;

        // Add remaining items (not enough for a full set) at full price
        // Example: 1 remaining item = 1 more at full price
        // Total to pay: 4 + 1 = 5 items at full price for 7 items purchased

        BigDecimal totalPrice = originalPrice
            .multiply(BigDecimal.valueOf(itemsToPay + remainingItems))
            .setScale(2, RoundingMode.HALF_UP);

        return totalPrice;
    }

    /**
     * Calculate price for percentage discount
     */
    private BigDecimal calculatePercentageDiscount(BigDecimal originalPrice, int quantity) {
        if (discountPercentage == null || discountPercentage.compareTo(BigDecimal.ZERO) <= 0) {
            return originalPrice.multiply(BigDecimal.valueOf(quantity));
        }

        // Price = originalPrice * (1 - percentage/100) * quantity
        BigDecimal multiplier = BigDecimal.ONE
            .subtract(discountPercentage.divide(BigDecimal.valueOf(100)));
        
        return originalPrice
            .multiply(multiplier)
            .multiply(BigDecimal.valueOf(quantity))
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate price for fixed amount discount
     * The discount only applies when quantity is a multiple of minQuantityForFixedDiscount
     * Example: price=160, discountAmount=10.5, minQuantity=2
     * - quantity=1: 1 × 160 = 160 (no discount)
     * - quantity=2: 2 × (160-10.5) = 299 (discount applied)
     * - quantity=3: 2 × (160-10.5) + 1 × 160 = 459 (discount on 2, full price on 1)
     * - quantity=4: 4 × (160-10.5) = 598 (discount applied twice)
     */
    private BigDecimal calculateFixedDiscount(BigDecimal originalPrice, int quantity) {
        if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return originalPrice.multiply(BigDecimal.valueOf(quantity));
        }

        // Validate that discount is not greater than price
        if (discountAmount.compareTo(originalPrice) > 0) {
            throw new IllegalArgumentException(
                String.format("El descuento fijo ($%.2f) no puede ser mayor que el precio del item ($%.2f)",
                    discountAmount, originalPrice)
            );
        }

        // Discounted price per item (cannot be negative)
        BigDecimal discountedPrice = originalPrice.subtract(discountAmount);
        if (discountedPrice.compareTo(BigDecimal.ZERO) < 0) {
            discountedPrice = BigDecimal.ZERO;
        }

        // If minQuantityForFixedDiscount is set and > 1, apply discount only to multiples
        int minQty = (minQuantityForFixedDiscount != null && minQuantityForFixedDiscount > 1) 
            ? minQuantityForFixedDiscount : 1;
        
        // Calculate how many "sets" qualify for the discount
        int discountedSets = quantity / minQty;
        int remainingItems = quantity % minQty;
        
        // Items that get the discount (multiples of minQty)
        int itemsWithDiscount = discountedSets * minQty;
        
        // Calculate total: discounted items + remaining items at full price
        BigDecimal discountedTotal = discountedPrice.multiply(BigDecimal.valueOf(itemsWithDiscount));
        BigDecimal fullPriceTotal = originalPrice.multiply(BigDecimal.valueOf(remainingItems));
        
        return discountedTotal.add(fullPriceTotal).setScale(2, RoundingMode.HALF_UP);
    }

    // ========== Public methods for unit price calculations (without quantity) ==========

    /**
     * Calculate discounted price per unit for BUY_X_PAY_Y promotion
     * @param originalPrice The original price per unit
     * @return The discounted price per unit
     */
    public BigDecimal calculateBuyXPayY(BigDecimal originalPrice) {
        if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (buyQuantity == null || payQuantity == null || buyQuantity <= 0 || payQuantity <= 0) {
            return originalPrice;
        }
        if (buyQuantity <= payQuantity) {
            return originalPrice;
        }

        // For unit price, calculate the effective price when buying buyQuantity items
        // Example: 3x2 means pay for 2 items when buying 3
        // Effective unit price = (originalPrice * payQuantity) / buyQuantity
        return originalPrice
            .multiply(BigDecimal.valueOf(payQuantity))
            .divide(BigDecimal.valueOf(buyQuantity), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate discounted price per unit for PERCENTAGE_DISCOUNT
     * @param originalPrice The original price per unit
     * @return The discounted price per unit
     */
    public BigDecimal calculatePercentageDiscount(BigDecimal originalPrice) {
        if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (discountPercentage == null || discountPercentage.compareTo(BigDecimal.ZERO) <= 0) {
            return originalPrice;
        }

        // Price = originalPrice * (1 - percentage/100)
        BigDecimal multiplier = BigDecimal.ONE
            .subtract(discountPercentage.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        
        return originalPrice
            .multiply(multiplier)
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate discounted price per unit for FIXED_AMOUNT_DISCOUNT
     * Note: This method returns the unit price assuming the discount applies.
     * The actual application depends on minQuantityForFixedDiscount at order time.
     * @param originalPrice The original price per unit
     * @return The discounted price per unit
     */
    public BigDecimal calculateFixedDiscount(BigDecimal originalPrice) {
        if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return originalPrice;
        }

        // Validate that discount is not greater than price
        if (discountAmount.compareTo(originalPrice) > 0) {
            throw new IllegalArgumentException(
                String.format("El descuento fijo ($%.2f) no puede ser mayor que el precio del item ($%.2f)",
                    discountAmount, originalPrice)
            );
        }

        // Discounted price per unit (cannot be negative)
        BigDecimal discountedPrice = originalPrice.subtract(discountAmount);
        if (discountedPrice.compareTo(BigDecimal.ZERO) < 0) {
            discountedPrice = BigDecimal.ZERO;
        }

        return discountedPrice.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Get the minimum quantity required for the fixed discount to apply
     * @return The minimum quantity, or 1 if not set
     */
    public int getEffectiveMinQuantityForFixedDiscount() {
        return (minQuantityForFixedDiscount != null && minQuantityForFixedDiscount > 0) 
            ? minQuantityForFixedDiscount : 1;
    }

    /**
     * Get display label for the promotion (for UI)
     * Examples: "2x1", "20% OFF", "-$5"
     */
    public String getDisplayLabel() {
        switch (promotionType) {
            case BUY_X_PAY_Y:
                return buyQuantity + "x" + payQuantity;
            
            case PERCENTAGE_DISCOUNT:
                return discountPercentage.stripTrailingZeros().toPlainString() + "% OFF";
            
            case FIXED_AMOUNT_DISCOUNT:
                String label = "-$" + discountAmount.stripTrailingZeros().toPlainString();
                if (minQuantityForFixedDiscount != null && minQuantityForFixedDiscount > 1) {
                    label += " (x" + minQuantityForFixedDiscount + ")";
                }
                return label;
            
            default:
                return "Promoción";
        }
    }

    /**
     * Validate promotion configuration based on type
     */
    public boolean isValidConfiguration() {
        switch (promotionType) {
            case BUY_X_PAY_Y:
                return buyQuantity != null && payQuantity != null 
                    && buyQuantity > 0 && payQuantity > 0 
                    && buyQuantity > payQuantity;
            
            case PERCENTAGE_DISCOUNT:
                return discountPercentage != null 
                    && discountPercentage.compareTo(BigDecimal.ZERO) > 0
                    && discountPercentage.compareTo(BigDecimal.valueOf(100)) <= 0;
            
            case FIXED_AMOUNT_DISCOUNT:
                return discountAmount != null 
                    && discountAmount.compareTo(BigDecimal.ZERO) > 0
                    && (minQuantityForFixedDiscount == null || minQuantityForFixedDiscount >= 1);
            
            default:
                return false;
        }
    }
}
