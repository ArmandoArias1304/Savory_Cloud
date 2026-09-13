package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Item line assigned to a Payment (per-person account).
 *
 * Quantity is a BigDecimal: whole units in per-person (ITEMS) assignments
 * (an order line of qty 4 becomes e.g. 2 units for one person, 1 for another),
 * fractional only when parts-equal (EQUAL) proration divides a line between
 * accounts. The effective unit price (with any promotion already applied) is
 * snapshotted at payment time, together with the item name and a textual
 * summary of the complements assigned to this account (their amount is folded
 * into {@link #total}).
 */
@Entity
@Table(name = "payment_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"payment", "orderDetail"})
public class PaymentDetail implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_payment_detail")
    private Long idPaymentDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_payment", nullable = false)
    private Payment payment;

    /**
     * Original OrderDetail this line was split from (kept for traceability).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_order_detail", nullable = false)
    private OrderDetail orderDetail;

    @Size(max = 200)
    @Column(name = "item_name", length = 200)
    private String itemName;

    /**
     * Fractional quantity assigned to this account (0.5, 1, 2.25...).
     */
    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @Column(name = "quantity", precision = 10, scale = 4, nullable = false)
    private BigDecimal quantity;

    /**
     * Effective unit price WITH IVA, promotion already applied (snapshot).
     */
    @NotNull
    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    /**
     * Line subtotal = unitPrice × quantity (rounded to 2 decimals).
     */
    @NotNull
    @Column(name = "subtotal", precision = 10, scale = 2, nullable = false)
    private BigDecimal subtotal;

    /**
     * Share of the complements of the original line (rounded to 2 decimals).
     */
    @NotNull
    @Column(name = "complement_subtotal", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal complementSubtotal = BigDecimal.ZERO;

    /**
     * Line total for this account = subtotal + complementSubtotal.
     */
    @NotNull
    @Column(name = "total", precision = 10, scale = 2, nullable = false)
    private BigDecimal total;

    /**
     * Textual summary of the complements included in this line, e.g. "Extra queso x2, Aderezo x1".
     * Used on tickets and CFDI descriptions.
     */
    @Size(max = 500)
    @Column(name = "complement_details", length = 500)
    private String complementDetails;

    /**
     * Snapshot of the customer comments of the original line.
     */
    @Size(max = 500)
    @Column(name = "comments", length = 500)
    private String comments;

    /**
     * Snapshot of combo metadata of the original line so tickets can render
     * "[COMBO]" prefixes even after the parent order changes.
     */
    @Column(name = "combo_group_id", length = 50)
    private String comboGroupId;

    @Column(name = "is_combo_parent_snapshot")
    private Boolean isComboParentSnapshot;

    /**
     * True when this line came from a combo parent row.
     */
    public boolean isComboParent() {
        return comboGroupId != null && !comboGroupId.isEmpty()
                && Boolean.TRUE.equals(isComboParentSnapshot);
    }

    /**
     * Whether this line is a courtesy (cortesía): a $0.00 item given away for free
     * (e.g. the courtesy coffee). Its recipe stock is still deducted.
     */
    public boolean isCourtesy() {
        return unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Quantity for display without trailing zeros: 2, 1.5, 0.25...
     */
    public String getQuantityDisplay() {
        if (quantity == null) {
            return "0";
        }
        return quantity.stripTrailingZeros().toPlainString();
    }
}