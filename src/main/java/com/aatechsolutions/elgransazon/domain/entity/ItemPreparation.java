package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A batch of an internal item that was actually produced.
 *
 * <p>Internal items (buffet dishes, staff meals) are never sold, so they cannot travel through
 * an {@link Order}: there is no customer, table, payment or ticket. What the restaurant does
 * need is to know <b>how much was produced</b> so the recipe stock is discounted. Each row of
 * this entity is one such batch.</p>
 *
 * <p>Registering a batch deducts {@code quantity × recipe} from stock; cancelling it returns the
 * exact same amounts once. {@link #stockReturned} is the idempotency guard that makes a repeated
 * cancel harmless, mirroring {@code OrderDetail.stockDeducted}.</p>
 */
@Entity
@Table(name = "item_preparations", indexes = {
    @Index(name = "idx_item_preparations_company_date", columnList = "company_id, prepared_at"),
    @Index(name = "idx_item_preparations_item", columnList = "id_item_menu")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idPreparation"})
@ToString(exclude = {"company", "itemMenu", "preparedBy", "cancelledBy"})
public class ItemPreparation implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_preparation")
    private Long idPreparation;

    // ========== Company Relationship (Multi-Tenant) ==========

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // ========== What was produced ==========

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_item_menu", nullable = false)
    private ItemMenu itemMenu;

    /**
     * Portions produced. Stock is discounted for {@code quantity × recipe}, so two portions
     * consume twice the ingredients of one.
     */
    @NotNull(message = "La cantidad es requerida")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    // ========== Status ==========

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PreparationStatus status = PreparationStatus.PREPARED;

    // ========== Who and when ==========

    @Column(name = "prepared_at", nullable = false)
    @Builder.Default
    private LocalDateTime preparedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prepared_by")
    private Employee preparedBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private Employee cancelledBy;

    @Size(max = 300, message = "El motivo no puede exceder 300 caracteres")
    @Column(name = "cancel_reason", length = 300)
    private String cancelReason;

    /**
     * TRUE once the ingredients of this batch have been returned to stock. Prevents a second
     * cancellation (or a double click) from inflating inventory.
     */
    @Column(name = "stock_returned", nullable = false)
    @Builder.Default
    private Boolean stockReturned = false;

    // ========== Business Logic ==========

    /** Whether this batch is currently counted as produced. */
    public boolean isPrepared() {
        return status == PreparationStatus.PREPARED;
    }

    /** A batch can be cancelled once, and only while it still stands. */
    public boolean canBeCancelled() {
        return isPrepared() && !Boolean.TRUE.equals(stockReturned);
    }

    /**
     * Mark the batch cancelled. Only bookkeeping: returning the ingredients to stock is the
     * caller's job (it needs the repository/services).
     */
    public void cancel(Employee employee, String reason) {
        if (!canBeCancelled()) {
            throw new IllegalStateException("Esta preparación ya fue cancelada.");
        }
        this.status = PreparationStatus.CANCELLED;
        this.stockReturned = true;
        this.cancelledAt = LocalDateTime.now();
        this.cancelledBy = employee;
        String trimmed = reason != null ? reason.trim() : null;
        this.cancelReason = trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    /** Portions that still count as produced (0 when cancelled). */
    public int getActiveQuantity() {
        return isPrepared() ? (quantity != null ? quantity : 0) : 0;
    }

    /**
     * Estimated ingredient cost of this batch, using the recipe's current unit costs.
     * Returns zero when the recipe is not loaded or has no ingredient costs.
     */
    public BigDecimal getEstimatedCost() {
        if (itemMenu == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return itemMenu.calculateIngredientsCost().multiply(BigDecimal.valueOf(quantity));
    }

    public String getItemName() {
        return itemMenu != null ? itemMenu.getName() : "Platillo eliminado";
    }

    public String getPreparedByName() {
        return preparedBy != null ? preparedBy.getFullName() : "—";
    }
}
