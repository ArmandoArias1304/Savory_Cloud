package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Configuration of the loyalty card for one restaurant.
 *
 * <p>There is exactly one program per company. The program is created lazily
 * with sensible defaults the first time an administrator opens the loyalty
 * screen, so existing companies do not need a data migration.</p>
 */
@Entity
@Table(name = "loyalty_programs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_loyalty_program_company", columnNames = {"company_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"company"})
public class LoyaltyProgram implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    /** Number of stamps needed to complete the card. */
    @Min(value = 1, message = "La tarjeta debe requerir al menos un sello")
    @Column(name = "stamps_required", nullable = false)
    @Builder.Default
    private Integer stampsRequired = 10;

    /** Minimum eligible purchase amount. Zero disables the check. */
    @DecimalMin(value = "0.00", message = "El monto mínimo no puede ser negativo")
    @Column(name = "minimum_purchase_amount", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal minimumPurchaseAmount = BigDecimal.ZERO;

    /** Minimum number of items in the sale. Zero disables the check. */
    @Min(value = 0, message = "Los artículos mínimos no pueden ser negativos")
    @Column(name = "minimum_items", nullable = false)
    @Builder.Default
    private Integer minimumItems = 1;

    /**
     * Maximum stamps a single paid order can generate. The first version
     * awards one stamp per eligible order, but the field is stored so the
     * rule can evolve without a schema change.
     */
    @Min(value = 1, message = "Debe permitirse al menos un sello por venta")
    @Column(name = "max_stamps_per_sale", nullable = false)
    @Builder.Default
    private Integer maxStampsPerSale = 1;

    /** Optional daily cap per customer. Null or zero means no cap. */
    @Min(value = 0, message = "El límite diario no puede ser negativo")
    @Column(name = "max_stamps_per_day")
    private Integer maxStampsPerDay;

    /** Days before an earned stamp expires. Zero means it never expires. */
    @Min(value = 0, message = "La vigencia no puede ser negativa")
    @Column(name = "stamp_expiry_days", nullable = false)
    @Builder.Default
    private Integer stampExpiryDays = 0;

    /** Last day customers may redeem the current global stamp balance. */
    @Column(name = "stamp_cycle_deadline")
    private LocalDate stampCycleDeadline;

    /** Deadline already processed, preventing a repeated reset for that cycle. */
    @Column(name = "stamp_cycle_reset_deadline")
    private LocalDate stampCycleResetDeadline;

    @Size(max = 120, message = "El título no puede exceder 120 caracteres")
    @Column(name = "card_title", length = 120)
    private String cardTitle;

    @Size(max = 255, message = "El mensaje no puede exceder 255 caracteres")
    @Column(name = "card_message", length = 255)
    private String cardMessage;

    @Size(max = 255, message = "La recompensa no puede exceder 255 caracteres")
    @Column(name = "reward_text", length = 255)
    private String rewardText;

    @Size(max = 500, message = "El logo no puede exceder 500 caracteres")
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    /**
     * Colour of the card front (brand side), as {@code #RRGGBB}. Null uses the
     * palette the card shipped with.
     */
    @Size(max = 7, message = "El color debe tener el formato #RRGGBB")
    @Column(name = "card_front_color", length = 7)
    private String cardFrontColor;

    /**
     * Colour of the card back (coupons side), as {@code #RRGGBB}. Null uses the
     * palette the card shipped with.
     */
    @Size(max = 7, message = "El color debe tener el formato #RRGGBB")
    @Column(name = "card_back_color", length = 7)
    private String cardBackColor;

    @Column(name = "terms", columnDefinition = "TEXT")
    private String terms;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Size(max = 100, message = "El usuario no puede exceder 100 caracteres")
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        applyDefaults();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        applyDefaults();
    }

    private void applyDefaults() {
        if (stampsRequired == null || stampsRequired < 1) {
            stampsRequired = 10;
        }
        if (minimumPurchaseAmount == null) {
            minimumPurchaseAmount = BigDecimal.ZERO;
        }
        if (minimumItems == null || minimumItems < 0) {
            minimumItems = 0;
        }
        if (maxStampsPerSale == null || maxStampsPerSale < 1) {
            maxStampsPerSale = 1;
        }
        if (stampExpiryDays == null || stampExpiryDays < 0) {
            stampExpiryDays = 0;
        }
        if (active == null) {
            active = true;
        }
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    public String getEffectiveCardTitle() {
        return cardTitle != null && !cardTitle.isBlank() ? cardTitle : "Tarjeta de fidelidad";
    }

    public String getEffectiveCardMessage() {
        return cardMessage != null && !cardMessage.isBlank()
                ? cardMessage
                : "Junta sellos en cada visita y canjéalos por recompensas.";
    }

    public String getEffectiveRewardText() {
        return rewardText != null && !rewardText.isBlank()
                ? rewardText
                : "Completa tu tarjeta y disfruta una recompensa.";
    }

    // ========== Colores de la tarjeta del cliente ==========

    /** Palette the card uses when the restaurant has not chosen colours. */
    public static final String DEFAULT_CARD_FRONT_COLOR = "#F2833F";
    public static final String DEFAULT_CARD_BACK_COLOR = "#12A7B5";

    public String getEffectiveCardFrontColor() {
        String value = normalizeHex(cardFrontColor);
        return value != null ? value : DEFAULT_CARD_FRONT_COLOR;
    }

    public String getEffectiveCardBackColor() {
        String value = normalizeHex(cardBackColor);
        return value != null ? value : DEFAULT_CARD_BACK_COLOR;
    }

    /** Slightly darker tone that closes the front gradient. */
    public String getCardFrontDarkColor() {
        return shade(getEffectiveCardFrontColor(), 0.80);
    }

    /** Deep tone of the front, used for text over white inside the card. */
    public String getCardFrontDeepColor() {
        return shade(getEffectiveCardFrontColor(), 0.60);
    }

    /** Slightly darker tone that closes the back gradient. */
    public String getCardBackDarkColor() {
        return shade(getEffectiveCardBackColor(), 0.80);
    }

    /** Deep tone of the back, used for the logo initial inside the stamps. */
    public String getCardBackDeepColor() {
        return shade(getEffectiveCardBackColor(), 0.60);
    }

    /**
     * Normalizes a {@code #RRGGBB} colour to upper case, or returns null when the
     * value is missing or is not a colour in that format.
     */
    public static String normalizeHex(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim().toUpperCase(java.util.Locale.ROOT);
        return candidate.matches("#[0-9A-F]{6}") ? candidate : null;
    }

    private static String shade(String hex, double factor) {
        int red = Integer.parseInt(hex.substring(1, 3), 16);
        int green = Integer.parseInt(hex.substring(3, 5), 16);
        int blue = Integer.parseInt(hex.substring(5, 7), 16);
        int scaledRed = (int) Math.round(red * factor);
        int scaledGreen = (int) Math.round(green * factor);
        int scaledBlue = (int) Math.round(blue * factor);
        return String.format("#%02X%02X%02X", scaledRed, scaledGreen, scaledBlue);
    }

    public boolean hasMinimumPurchase() {
        return minimumPurchaseAmount != null && minimumPurchaseAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasMinimumItems() {
        return minimumItems != null && minimumItems > 0;
    }

    public boolean hasExpiry() {
        return stampExpiryDays != null && stampExpiryDays > 0;
    }
}
