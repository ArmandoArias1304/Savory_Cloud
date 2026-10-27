package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Catalog reward the restaurant offers for a number of stamps.
 *
 * <p>Each reward points at a sellable menu item and a free quantity, e.g.
 * "Café americano gratis por 5 sellos" is {@code itemMenu = Café americano}
 * and {@code freeQuantity = 1}. When the cashier redeems it, the system creates
 * an order with that item at 100% discount; the sticker price of the item is
 * never trusted from the browser, it always comes from the menu.</p>
 *
 * <p>Rewards are per company and are never deleted by the cashier; the admin
 * activates or deactivates them so historical redemptions keep their name.</p>
 */
@Entity
@Table(name = "loyalty_rewards",
        indexes = {
                @Index(name = "idx_loyalty_reward_company", columnList = "company_id"),
                @Index(name = "idx_loyalty_reward_active", columnList = "active")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"company", "itemMenu"})
public class LoyaltyReward implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** Menu item delivered by the reward. Must belong to the same company. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_menu_id", nullable = false)
    private ItemMenu itemMenu;

    @Size(max = 120, message = "El nombre no puede exceder 120 caracteres")
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    @Column(name = "description", length = 500)
    private String description;

    /** Stamps charged for one unit of this reward. */
    @Min(value = 1, message = "La recompensa debe costar al menos un sello")
    @Column(name = "stamps_cost", nullable = false)
    private Integer stampsCost;

    /** Units of {@link #itemMenu} delivered per redemption. */
    @Min(value = 1, message = "La recompensa debe incluir al menos un artículo")
    @Column(name = "free_quantity", nullable = false)
    @Builder.Default
    private Integer freeQuantity = 1;

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

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (stampsCost == null || stampsCost < 1) {
            stampsCost = 1;
        }
        if (freeQuantity == null || freeQuantity < 1) {
            freeQuantity = 1;
        }
        if (active == null) {
            active = true;
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (freeQuantity == null || freeQuantity < 1) {
            freeQuantity = 1;
        }
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    public int safeStampsCost() {
        return stampsCost != null ? stampsCost : 0;
    }

    public int safeFreeQuantity() {
        return freeQuantity != null && freeQuantity > 0 ? freeQuantity : 1;
    }

    public String getEffectiveDescription() {
        return description != null && !description.isBlank()
                ? description
                : "Recompensa canjeable con " + safeStampsCost() + " sellos";
    }

    /** e.g. {@code "1 × Café americano"}; used by staff and customer screens. */
    public String getDisplayValue() {
        if (itemMenu == null) {
            return name;
        }
        return safeFreeQuantity() + " × " + itemMenu.getName();
    }
}
