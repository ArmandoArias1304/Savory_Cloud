package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Order entity representing customer orders in the restaurant
 */
@Entity
@Table(name = "orders", uniqueConstraints = {
    @UniqueConstraint(name = "uk_order_number_company", columnNames = {"order_number", "company_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idOrder"})
@ToString(exclude = {"company", "table", "employee", "preparedBy", "paidBy", "orderDetails"})
public class Order implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_order")
    private Long idOrder;

    // ========== Company Relationship (Multi-Tenant) ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotBlank(message = "El número de orden es requerido")
    @Size(max = 50, message = "El número de orden no puede exceder 50 caracteres")
    @Column(name = "order_number", nullable = false, length = 50)
    private String orderNumber;

    // ========== Order Type ==========

    @NotNull(message = "El tipo de orden es requerido")
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    // ========== Order Status ==========

    @NotNull(message = "El estado de la orden es requerido")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    // ========== Customer Information ==========
    // NOTE: These fields are optional for DINE_IN orders
    // Validation is handled in the service layer based on order type

    @Size(max = 100, message = "El nombre del cliente no puede exceder 100 caracteres")
    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "El teléfono debe tener exactamente 10 dígitos")
    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Size(max = 500, message = "La dirección no puede exceder 500 caracteres")
    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

    @Size(max = 500, message = "Las referencias no pueden exceder 500 caracteres")
    @Column(name = "delivery_references", length = 500)
    private String deliveryReferences;

    // GPS Coordinates for delivery location
    @Column(name = "delivery_latitude")
    private Double deliveryLatitude;

    @Column(name = "delivery_longitude")
    private Double deliveryLongitude;

    // ========== Relationships ==========

    // NOTE: Table is optional - only required for DINE_IN orders
    // For TAKEOUT and DELIVERY orders, table can be null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_table", nullable = true)
    private RestaurantTable table;

    // Employee who created/took the order (typically a waiter)
    // NOTE: This is nullable to support customer-created orders
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_employee", nullable = true)
    private Employee employee;

    // Customer who created the order (for online orders)
    // NOTE: This is nullable to support employee-created orders
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_customer", nullable = true)
    private Customer customer;

    // Employee who prepared the order (chef)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_prepared_by", nullable = true)
    private Employee preparedBy;

    // Employee who prepared beverages/coffee (barista)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_prepared_by_barista", nullable = true)
    private Employee preparedByBarista;

    // Employee who prepared grilled items (parrillero)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_prepared_by_parrillero", nullable = true)
    private Employee preparedByParrillero;

    // Employee who collected payment (cashier or waiter, depending on payment method)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_paid_by", nullable = true)
    private Employee paidBy;

    // Employee who delivered the order (delivery person - only for DELIVERY orders)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_delivered_by", nullable = true)
    private Employee deliveredBy;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderDetail> orderDetails = new ArrayList<>();

    /**
     * Per-person accounts when the bill was split (empty/null for regular orders).
     * Populated by the split-payment flow; each Payment carries its own amounts,
     * tip, method, autofactura key and CFDI data.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Payment> payments = new ArrayList<>();

    /**
     * True when this order was paid through split accounts (1..N Payment rows).
     */
    public boolean isSplitOrder() {
        return payments != null && !payments.isEmpty();
    }

    /**
     * Next sequential "Persona N" number for the split editor: one past the
     * highest person number already charged on this order. Person labels are a
     * snapshot per payment, so numbering must continue where it left off
     * instead of restarting at 1 on every collection.
     */
    public int nextPersonNumber() {
        int max = 0;
        if (payments != null) {
            for (Payment p : payments) {
                if (p.getPersonLabel() != null) {
                    String label = p.getPersonLabel().trim();
                    if (label.startsWith("Persona ")) {
                        try {
                            max = Math.max(max, Integer.parseInt(label.substring(8).trim()));
                        } catch (NumberFormatException ignored) {
                            // Non-numeric label does not advance the counter.
                        }
                    }
                }
            }
        }
        return max + 1;
    }

    // ========== Payment Method ==========

    @NotNull(message = "El método de pago es requerido")
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethodType paymentMethod;

    // ========== Calculations ==========

    @NotNull(message = "El subtotal es requerido")
    @DecimalMin(value = "0.0", message = "El subtotal no puede ser negativo")
    @Column(name = "subtotal", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @NotNull(message = "La tasa de impuesto es requerida")
    @DecimalMin(value = "0.0", message = "La tasa de impuesto no puede ser negativa")
    @DecimalMax(value = "100.0", message = "La tasa de impuesto no puede exceder 100%")
    @Column(name = "tax_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal taxRate;

    @NotNull(message = "El monto del impuesto es requerido")
    @DecimalMin(value = "0.0", message = "El monto del impuesto no puede ser negativo")
    @Column(name = "tax_amount", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @NotNull(message = "El total es requerido")
    @DecimalMin(value = "0.0", inclusive = true, message = "El total no puede ser negativo")
    @Column(name = "total", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    // ========== Tip (Propina) ==========

    @DecimalMin(value = "0.0", message = "La propina no puede ser negativa")
    @DecimalMax(value = "999999.99", message = "La propina no puede ser mayor a $999,999.99")
    @Digits(integer = 6, fraction = 2, message = "La propina solo permite hasta 2 decimales")
    @Column(name = "tip", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal tip = BigDecimal.ZERO;

    // ========== Delivery Cost (only meaningful for DELIVERY orders) ==========
    // Includes IVA. For non-DELIVERY orders, this value is forced to 0 by the service layer.

    @NotNull(message = "El costo de envío es requerido")
    @DecimalMin(value = "0.0", message = "El costo de envío no puede ser negativo")
    @DecimalMax(value = "999999.99", message = "El costo de envío no puede ser mayor a $999,999.99")
    @Digits(integer = 6, fraction = 2, message = "El costo de envío solo permite hasta 2 decimales")
    @Column(name = "delivery_cost", precision = 8, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal deliveryCost = BigDecimal.ZERO;

    // ========== Order Discount (descuento aplicado al total de la orden por admin/cashier al cobrar) ==========
    // Includes IVA. Independent and additive to per-item promotion discounts.
    // Validated by service layer: 0 <= orderDiscount <= (itemsTotal + deliveryCost).

    @NotNull(message = "El descuento sobre el total es requerido")
    @DecimalMin(value = "0.0", message = "El descuento sobre el total no puede ser negativo")
    @DecimalMax(value = "999999.99", message = "El descuento sobre el total no puede ser mayor a $999,999.99")
    @Digits(integer = 6, fraction = 2, message = "El descuento sobre el total solo permite hasta 2 decimales")
    @Column(name = "order_discount", precision = 8, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal orderDiscount = BigDecimal.ZERO;

    /**
     * Porcentaje de descuento capturado por admin/cajero (0–100, 2 decimales).
     * Cuando está presente, {@link #orderDiscount} sigue siendo el monto
     * resuelto (total × %) para no alterar totales, IVA, reportes, tickets ni
     * Facturama.
     */
    @DecimalMin(value = "0.0", message = "El porcentaje de descuento no puede ser negativo")
    @DecimalMax(value = "100.0", message = "El porcentaje de descuento no puede exceder 100%")
    @Digits(integer = 3, fraction = 2, message = "El porcentaje de descuento solo permite hasta 2 decimales")
    @Column(name = "order_discount_percent", precision = 5, scale = 2)
    private BigDecimal orderDiscountPercent;

    /**
     * true cuando el descuento quedó fijado (flujo "persona que se va"):
     * todo cobro posterior de la orden debe usar exactamente el mismo
     * porcentaje. El mesero nunca lo fija (solo admin/cajero).
     */
    @NotNull(message = "El bloqueo del descuento es requerido")
    @Column(name = "order_discount_locked", nullable = false,
            columnDefinition = "boolean not null default false")
    @Builder.Default
    private boolean orderDiscountLocked = false;

    // ========== Audit Fields ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @NotBlank(message = "El usuario creador es requerido")
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "prepared_at")
    private LocalDateTime preparedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /**
     * Timestamp (UTC) when the order status transitioned to PAID.
     *
     * Set exactly once by {@link com.aatechsolutions.elgransazon.application.service.OrderServiceImpl#changeStatus}
     * when {@code newStatus == PAID}. Never overwritten afterwards (autofactura saves do not touch this field),
     * so it is the authoritative source of truth for revenue/sales/CFDI date filtering.
     */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // ========== Reservation Association ==========
    // This field stores the reservation ID associated with this order
    // When an order is created for a reserved table, this field links to the reservation
    // The reservation status will be set to COMPLETED when order is created
    @Column(name = "reservation_id")
    private Long reservationId;

    // ========== Facturama Autofactura (Facturación Electrónica) ==========
    // autofacturaKey is set at payment time (UUID); client visits /autofactura/{key} to self-invoice.
    // facturamaCfdiId and facturamaCfdiUuid are set when the client completes the autofactura form.

    /**
     * Facturama CFDI ID (set after client generates their invoice via autofactura page).
     */
    @Column(name = "facturama_cfdi_id", length = 100)
    private String facturamaCfdiId;

    /**
     * SAT fiscal folio UUID (set after CFDI creation).
     */
    @Column(name = "facturama_cfdi_uuid", length = 100)
    private String facturamaCfdiUuid;

    /**
     * Unique autofactura key (UUID) generated at payment time.
     * Used in the self-invoice URL: /autofactura/{key}
     */
    @Column(name = "autofactura_key", length = 50)
    private String autofacturaKey;

    /**
     * Timestamp (UTC) when the CFDI was created via Facturama.
     */
    @Column(name = "facturama_cfdi_created_at")
    private LocalDateTime facturamaCfdiCreatedAt;

    // ========== Factura Global (Público en General) ==========
    // When the ADMIN emits the daily/monthly global invoice, every paid ticket without
    // an individual CFDI gets these fields filled. Once set, the autofactura page blocks
    // individual invoicing (SAT forbids invoicing the same operation twice).

    /**
     * Facturama CFDI ID of the global invoice (público en general) that included this order.
     */
    @Column(name = "factura_global_cfdi_id", length = 100)
    private String facturaGlobalCfdiId;

    /**
     * SAT fiscal folio UUID of the global invoice that included this order.
     */
    @Column(name = "factura_global_cfdi_uuid", length = 100)
    private String facturaGlobalCfdiUuid;

    /**
     * Timestamp (UTC) when the global invoice CFDI was created via Facturama.
     */
    @Column(name = "factura_global_cfdi_created_at")
    private LocalDateTime facturaGlobalCfdiCreatedAt;

    /**
     * Full self-invoice URL for this order (e.g. https://slug.domain.com/autofactura/{key}).
     */
    @Column(name = "self_invoice_url", length = 300)
    private String selfInvoiceUrl;

    // ========== Lifecycle Callbacks ==========

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========== Business Methods ==========

    /**
     * Add order detail to this order
     */
    public void addOrderDetail(OrderDetail orderDetail) {
        this.orderDetails.add(orderDetail);
        orderDetail.setOrder(this);
    }

    /**
     * Remove order detail from this order
     */
    public void removeOrderDetail(OrderDetail orderDetail) {
        this.orderDetails.remove(orderDetail);
        orderDetail.setOrder(null);
    }

    /**
     * Clear all order details
     */
    public void clearOrderDetails() {
        this.orderDetails.clear();
    }

    /**
     * Recalculate all order amounts.
     * 
     * IMPORTANT: OrderDetail.subtotal already includes IVA (prices are stored with tax included).
     * Therefore:
     * - total = sum of OrderDetail.subtotal + sum of OrderDetail.complementsTotal (final price with IVA)
     * - subtotal = total / (1 + taxRate/100) (price without IVA, for display purposes)
     * - taxAmount = total - subtotal (IVA amount, for display purposes)
     */
    public void recalculateAmounts() {
        // Step 1: Total is the sum of all order details + their complements (already includes IVA)
        BigDecimal itemsTotal = orderDetails.stream()
                .map(OrderDetail::getTotalWithComplements)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // Add delivery cost (only meaningful for DELIVERY orders; service layer forces 0 for others).
        // deliveryCost already includes IVA.
        BigDecimal effectiveDeliveryCost = (this.deliveryCost != null && this.orderType == OrderType.DELIVERY)
                ? this.deliveryCost
                : BigDecimal.ZERO;

        // Subtract order-level discount (gross, includes IVA). Clamp to [0, itemsTotal+envío]
        // so a malformed value can never push total below 0.
        BigDecimal grossBeforeDiscount = itemsTotal.add(effectiveDeliveryCost);
        BigDecimal effectiveOrderDiscount;
        if (this.orderDiscountLocked
                && this.orderDiscountPercent != null
                && this.orderDiscountPercent.compareTo(BigDecimal.ZERO) > 0) {
            // Descuento fijado (persona que se va): el monto SIEMPRE se re-deriva
            // del % sobre el total bruto vigente, por lo que los ítems agregados
            // después del bloqueo también quedan descontados.
            effectiveOrderDiscount = grossBeforeDiscount
                    .multiply(this.orderDiscountPercent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            effectiveOrderDiscount = (this.orderDiscount != null)
                    ? this.orderDiscount
                    : BigDecimal.ZERO;
        }
        if (effectiveOrderDiscount.compareTo(BigDecimal.ZERO) < 0) {
            effectiveOrderDiscount = BigDecimal.ZERO;
        }
        if (effectiveOrderDiscount.compareTo(grossBeforeDiscount) > 0) {
            effectiveOrderDiscount = grossBeforeDiscount;
        }
        // Keep the resolved amount in sync with the applied discount.
        this.orderDiscount = effectiveOrderDiscount;

        this.total = grossBeforeDiscount.subtract(effectiveOrderDiscount).setScale(2, RoundingMode.HALF_UP);
        
        // Step 2: Calculate subtotal (price without IVA) from total
        // subtotal = total / (1 + taxRate/100)
        if (this.taxRate != null && this.taxRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal taxMultiplier = BigDecimal.ONE.add(
                    this.taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
            );
            this.subtotal = this.total.divide(taxMultiplier, 2, RoundingMode.HALF_UP);
            // Step 3: Tax amount is the difference
            this.taxAmount = this.total.subtract(this.subtotal);
        } else {
            // No tax rate, subtotal equals total
            this.subtotal = this.total;
            this.taxAmount = BigDecimal.ZERO;
        }
    }
    
    /**
     * @deprecated Use recalculateAmounts() instead. 
     * This method is kept for backward compatibility but now delegates to recalculateAmounts().
     */
    @Deprecated
    public void calculateSubtotal() {
        // Legacy method - now part of recalculateAmounts()
        // Does nothing on its own, call recalculateAmounts() instead
    }

    /**
     * @deprecated Use recalculateAmounts() instead.
     * This method is kept for backward compatibility but now delegates to recalculateAmounts().
     */
    @Deprecated
    public void calculateTaxAmount() {
        // Legacy method - now part of recalculateAmounts()
        // Does nothing on its own, call recalculateAmounts() instead
    }

    /**
     * @deprecated Use recalculateAmounts() instead.
     * This method is kept for backward compatibility but now delegates to recalculateAmounts().
     */
    @Deprecated
    public void calculateTotal() {
        // Legacy method - now part of recalculateAmounts()
        // Does nothing on its own, call recalculateAmounts() instead
    }

    /**
     * Generate order number based on date and sequence
     * Format: ORD-YYYYMMDD-XXX
     */
    public static String generateOrderNumber(int sequence) {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("ORD-%s-%03d", dateStr, sequence);
    }

    /**
     * Check if order can be cancelled
     */
    public boolean canBeCancelled() {
        return this.status.canBeCancelled();
    }

    /**
     * Check if order should return stock when cancelled
     */
    public boolean shouldReturnStockOnCancel() {
        return this.status.shouldReturnStockOnCancel();
    }

    /**
     * Cancel the order
     */
    public void cancel() {
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    /**
     * Tax multiplier (1 + taxRate/100). Returns BigDecimal.ONE when no tax applies.
     * Used internally to keep full precision in derived calculations.
     */
    private BigDecimal getTaxMultiplier() {
        if (this.taxRate == null || this.taxRate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return BigDecimal.ONE.add(
                this.taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
        );
    }

    /**
     * Sum of (unitPrice × quantity + complementsTotal) for all details, BEFORE discount.
     * Includes IVA (prices stored with tax). Full precision (no rounding).
     */
    private BigDecimal getOriginalItemsTotalWithTaxRaw() {
        if (orderDetails == null || orderDetails.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return orderDetails.stream()
                .map(d -> {
                    BigDecimal itemTotal = d.getUnitPrice().multiply(BigDecimal.valueOf(d.getQuantity()));
                    BigDecimal compTotal = d.getComplementsTotal();
                    return itemTotal.add(compTotal);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Current items total WITH tax AFTER discount (sum of OrderDetail.getTotalWithComplements()).
     * Full precision (no rounding).
     */
    private BigDecimal getCurrentItemsTotalWithTaxRaw() {
        if (orderDetails == null || orderDetails.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return orderDetails.stream()
                .map(OrderDetail::getTotalWithComplements)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Original items subtotal WITHOUT IVA (before discount). Full precision.
     */
    private BigDecimal getOriginalSubtotalWithoutTaxRaw() {
        BigDecimal totalWithTax = getOriginalItemsTotalWithTaxRaw();
        BigDecimal mult = getTaxMultiplier();
        if (mult.compareTo(BigDecimal.ONE) == 0) {
            return totalWithTax;
        }
        return totalWithTax.divide(mult, 10, RoundingMode.HALF_UP);
    }

    /**
     * Current items subtotal WITHOUT IVA (after discount, excludes delivery). Full precision.
     */
    private BigDecimal getSubtotalWithoutDeliveryRaw() {
        BigDecimal totalWithTax = getCurrentItemsTotalWithTaxRaw();
        BigDecimal mult = getTaxMultiplier();
        if (mult.compareTo(BigDecimal.ONE) == 0) {
            return totalWithTax;
        }
        return totalWithTax.divide(mult, 10, RoundingMode.HALF_UP);
    }

    /**
     * Delivery cost WITHOUT IVA. Full precision.
     */
    private BigDecimal getDeliveryCostWithoutTaxRaw() {
        if (this.deliveryCost == null
                || this.orderType != OrderType.DELIVERY
                || this.deliveryCost.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal mult = getTaxMultiplier();
        if (mult.compareTo(BigDecimal.ONE) == 0) {
            return this.deliveryCost;
        }
        return this.deliveryCost.divide(mult, 10, RoundingMode.HALF_UP);
    }

    /**
     * Get the original subtotal before any promotion discount, without IVA.
     * This is: sum of (unitPrice × quantity + complementsTotal) for all details / (1 + taxRate/100)
     * complementsTotal already handles sauce multiplication via OrderDetail.getComplementsTotal()
     */
    public BigDecimal getOriginalSubtotalWithoutTax() {
        return getOriginalSubtotalWithoutTaxRaw().setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Get the delivery cost portion that corresponds to the price WITHOUT IVA.
     * deliveryCost stored in DB already includes IVA.
     */
    public BigDecimal getDeliveryCostWithoutTax() {
        return getDeliveryCostWithoutTaxRaw().setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Get the IVA portion of the delivery cost.
     * = deliveryCost - deliveryCostWithoutTax (computed in full precision, rounded at display).
     */
    public BigDecimal getDeliveryCostTaxAmount() {
        if (this.deliveryCost == null
                || this.orderType != OrderType.DELIVERY
                || this.deliveryCost.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return this.deliveryCost.subtract(getDeliveryCostWithoutTaxRaw())
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Get the items-only subtotal without IVA (excludes delivery cost).
     * For display: lets the user see the items charge separated from envío.
     */
    public BigDecimal getSubtotalWithoutDelivery() {
        return getSubtotalWithoutDeliveryRaw().setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Get the items-only tax amount (excludes delivery's IVA portion).
     * Computed as currentItemsTotalWithTax - currentItemsSubtotalWithoutTax (full precision).
     */
    public BigDecimal getTaxAmountWithoutDelivery() {
        BigDecimal itemsWithTax = getCurrentItemsTotalWithTaxRaw();
        BigDecimal itemsWithoutTax = getSubtotalWithoutDeliveryRaw();
        return itemsWithTax.subtract(itemsWithoutTax).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Get the promotion discount amount without IVA.
     * Discount = originalSubtotalWithoutTax - subtotalWithoutDelivery (full precision).
     */
    public BigDecimal getDiscountWithoutTax() {
        BigDecimal original = getOriginalSubtotalWithoutTaxRaw();
        BigDecimal current = getSubtotalWithoutDeliveryRaw();
        BigDecimal discount = original.subtract(current).setScale(2, RoundingMode.HALF_UP);
        return discount.compareTo(BigDecimal.ZERO) > 0 ? discount : BigDecimal.ZERO;
    }

    /**
     * Check if this order has any promotion discount applied.
     */
    public boolean hasDiscount() {
        return getDiscountWithoutTax().compareTo(new BigDecimal("0.01")) > 0;
    }

    /**
     * Get formatted original subtotal without tax (before discount), items only.
     */
    public String getFormattedOriginalSubtotalWithoutTax() {
        return String.format("$%.2f", getOriginalSubtotalWithoutTax());
    }

    /**
     * Original subtotal WITHOUT IVA, INCLUDING delivery cost (before discount).
     * Used in views/tickets where the subtotal row should reflect the same scope as the CFDI
     * (i.e. items + envío sin IVA, antes de descuento).
     */
    public BigDecimal getOriginalSubtotalWithoutTaxIncludingDelivery() {
        return getOriginalSubtotalWithoutTaxRaw()
                .add(getDeliveryCostWithoutTaxRaw())
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Get formatted original subtotal without tax INCLUDING delivery (before discount).
     */
    public String getFormattedOriginalSubtotalWithoutTaxIncludingDelivery() {
        return String.format("$%.2f", getOriginalSubtotalWithoutTaxIncludingDelivery());
    }

    /**
     * Get formatted discount without tax
     */
    public String getFormattedDiscountWithoutTax() {
        return String.format("-$%.2f", getDiscountWithoutTax());
    }

    /**
     * Get the promotion discount amount WITH IVA included (total savings as seen by customer).
     * = originalItemsTotalWithTax - currentItemsTotalWithTax (full precision, rounded at the end).
     */
    public BigDecimal getDiscountWithTax() {
        BigDecimal original = getOriginalItemsTotalWithTaxRaw();
        BigDecimal current = getCurrentItemsTotalWithTaxRaw();
        BigDecimal discount = original.subtract(current).setScale(2, RoundingMode.HALF_UP);
        return discount.compareTo(BigDecimal.ZERO) > 0 ? discount : BigDecimal.ZERO;
    }

    /**
     * Get formatted promotion discount WITH IVA (positive amount, no minus sign).
     * Used in informative notes like "Incluye descuento por promoción de $X.XX".
     */
    public String getFormattedDiscountWithTax() {
        return String.format("$%.2f", getDiscountWithTax());
    }

    /**
     * Get formatted subtotal
     */
    public String getFormattedSubtotal() {
        if (subtotal == null) {
            return "$0.00";
        }
        return String.format("$%.2f", subtotal);
    }

    /**
     * Get formatted items-only subtotal (excludes delivery cost without IVA).
     */
    public String getFormattedSubtotalWithoutDelivery() {
        return String.format("$%.2f", getSubtotalWithoutDelivery());
    }

    /**
     * Get formatted items-only tax amount (excludes delivery's IVA portion).
     */
    public String getFormattedTaxAmountWithoutDelivery() {
        return String.format("$%.2f", getTaxAmountWithoutDelivery());
    }

    /**
     * Get formatted delivery cost without IVA.
     */
    public String getFormattedDeliveryCostWithoutTax() {
        return String.format("$%.2f", getDeliveryCostWithoutTax());
    }

    // ========== Order Discount Helpers (descuento sobre el total, incluye IVA) ==========

    /**
     * @return true if this order has an order-level discount applied (> 0).
     */
    public boolean hasOrderDiscount() {
        return orderDiscount != null && orderDiscount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Order discount amount WITHOUT IVA (raw, unrounded).
     * orderDiscount is stored gross (includes IVA), so the net amount is
     * orderDiscount / (1 + taxRate/100).
     */
    public BigDecimal getOrderDiscountWithoutTaxRaw() {
        if (orderDiscount == null || orderDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (taxRate == null || taxRate.compareTo(BigDecimal.ZERO) <= 0) {
            return orderDiscount;
        }
        BigDecimal taxMultiplier = BigDecimal.ONE.add(
                taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
        );
        return orderDiscount.divide(taxMultiplier, 10, RoundingMode.HALF_UP);
    }

    /**
     * Order discount amount WITHOUT IVA, rounded to 2 decimals.
     */
    public BigDecimal getOrderDiscountWithoutTax() {
        return getOrderDiscountWithoutTaxRaw().setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * IVA portion of the order discount (rounded to 2 decimals).
     */
    public BigDecimal getOrderDiscountTaxAmount() {
        if (orderDiscount == null || orderDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return orderDiscount.subtract(getOrderDiscountWithoutTax()).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Formatted order discount WITH IVA, e.g. "$12.34". Always positive.
     */
    public String getFormattedOrderDiscount() {
        BigDecimal value = orderDiscount != null ? orderDiscount : BigDecimal.ZERO;
        return String.format("$%.2f", value);
    }

    /**
     * Formatted order discount WITHOUT IVA, e.g. "$10.64". Always positive.
     */
    public String getFormattedOrderDiscountWithoutTax() {
        return String.format("$%.2f", getOrderDiscountWithoutTax());
    }

    // ========== Order Discount Percent (porcentaje capturado) ==========

    /**
     * @return true when this order was discounted with a captured percentage (> 0).
     */
    public boolean hasOrderDiscountPercent() {
        return orderDiscountPercent != null && orderDiscountPercent.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * @return true when the percentage discount is locked (departing-guest flow),
     *         so every later collection must reuse the same percentage.
     */
    public boolean hasLockedOrderDiscount() {
        return orderDiscountLocked && hasOrderDiscountPercent();
    }

    /**
     * Formatted percentage without trailing zeros, e.g. "10" or "12.5".
     */
    public String getFormattedOrderDiscountPercent() {
        if (orderDiscountPercent == null) {
            return "0";
        }
        return orderDiscountPercent.stripTrailingZeros().toPlainString();
    }

    /**
     * Gross order base used to resolve a percentage discount: items with item-level
     * promotions already applied plus the delivery cost (IVA included).
     */
    public BigDecimal getGrossBeforeOrderDiscount() {
        BigDecimal items = getCurrentItemsTotalWithTaxRaw();
        BigDecimal delivery = (this.deliveryCost != null && this.orderType == OrderType.DELIVERY)
                ? this.deliveryCost
                : BigDecimal.ZERO;
        return items.add(delivery).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Multiplier of the global percentage discount: (1 − %/100).
     * Returns {@code 1} when the order has no captured percentage.
     */
    public BigDecimal getOrderDiscountFactor() {
        if (!hasOrderDiscountPercent()) {
            return BigDecimal.ONE;
        }
        return BigDecimal.ONE.subtract(
                orderDiscountPercent.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
    }

    /**
     * Resolves a captured percentage (0–100) to a discount amount.
     *
     * The base is what is still owed when the order already has partial
     * (departing-guest) charges, otherwise the full gross order; the result is
     * capped at that base so the discount can never exceed what remains to be
     * charged. Shared by the controllers to keep the amount stored on the order
     * consistent with the captured percentage.
     */
    public BigDecimal resolveDiscountAmountForPercent(BigDecimal percent) {
        if (percent == null || percent.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal base = hasPartialCollections()
                ? getRemainingTotal()
                : getGrossBeforeOrderDiscount();
        if (base == null || base.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal resolved = base.multiply(percent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return resolved.min(base).max(BigDecimal.ZERO);
    }

    /**
     * Subtotal sin IVA "antes" del descuento de orden, para mostrar en tickets/vistas.
     * = order.subtotal + orderDiscountSinIVA.
     * Cuando no hay orderDiscount, equivale a order.subtotal.
     */
    public BigDecimal getDisplaySubtotal() {
        BigDecimal sub = subtotal != null ? subtotal : BigDecimal.ZERO;
        return sub.add(getOrderDiscountWithoutTax()).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Formatted display subtotal (sin IVA, antes del descuento de orden).
     */
    public String getFormattedDisplaySubtotal() {
        return String.format("$%.2f", getDisplaySubtotal());
    }

    /**
     * Get formatted tax amount
     */
    public String getFormattedTaxAmount() {
        if (taxAmount == null) {
            return "$0.00";
        }
        return String.format("$%.2f", taxAmount);
    }

    /**
     * Get formatted total
     */
    public String getFormattedTotal() {
        if (total == null) {
            return "$0.00";
        }
        return String.format("$%.2f", total);
    }

    /**
     * Get formatted tip
     */
    public String getFormattedTip() {
        if (tip == null) {
            return "$0.00";
        }
        return String.format("$%.2f", tip);
    }

    /**
     * Get total with tip
     */
    public BigDecimal getTotalWithTip() {
        BigDecimal baseTotal = total != null ? total : BigDecimal.ZERO;
        BigDecimal tipAmount = tip != null ? tip : BigDecimal.ZERO;
        return baseTotal.add(tipAmount);
    }

    /**
     * Get formatted total with tip
     */
    public String getFormattedTotalWithTip() {
        return String.format("$%.2f", getTotalWithTip());
    }

    /**
     * Get formatted created date
     */
    public String getFormattedCreatedAt() {
        if (createdAt == null) {
            return "";
        }
        return createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    /**
     * Compute the autofactura deadline (last day of the month in which the order was paid),
     * expressed in the company's local timezone.
     *
     * Anchor: {@code paidAt} if present, otherwise {@code createdAt} (legacy fallback).
     * Both are stored as UTC LocalDateTime, so they are converted to the given zone before
     * extracting the calendar month.
     *
     * Returns {@code null} only when the order has neither timestamp (should never happen
     * in practice).
     */
    public java.time.LocalDate getInvoiceDeadline(java.time.ZoneId zone) {
        java.time.LocalDateTime anchor = paidAt != null ? paidAt : createdAt;
        if (anchor == null || zone == null) {
            return null;
        }
        java.time.LocalDate localDate = anchor.atZone(java.time.ZoneOffset.UTC)
                .withZoneSameInstant(zone)
                .toLocalDate();
        return java.time.YearMonth.from(localDate).atEndOfMonth();
    }

    /**
     * Check whether the invoicing window for this order has expired.
     * Strict cutoff at end-of-day on the last day of the payment month, in the company timezone.
     */
    public boolean isAutofacturaExpired(java.time.ZoneId zone) {
        java.time.LocalDate deadline = getInvoiceDeadline(zone);
        if (deadline == null) {
            return false;
        }
        return java.time.LocalDate.now(zone).isAfter(deadline);
    }

    /**
     * Get delivery person name
     */
    public String getDeliveryPersonName() {
        return deliveredBy != null ? deliveredBy.getFullName() : "Sin asignar";
    }

    /**
     * Calculate order status based on individual item statuses
     * 
     * NEW LOGIC: Order status is the MINIMUM (lowest) status of all items
     * Respecting status hierarchy: PENDING < IN_PREPARATION < READY < DELIVERED
     * 
     * Examples:
     * - 3 items IN_PREPARATION + 1 item PENDING → Order stays PENDING
     * - All items IN_PREPARATION → Order is IN_PREPARATION
     * - All items READY → Order is READY
     * - Some items READY + some IN_PREPARATION → Order stays IN_PREPARATION
     * 
     * This ensures order doesn't advance until ALL items reach the same level
     */
    public OrderStatus calculateStatusFromItems() {
        if (orderDetails == null || orderDetails.isEmpty()) {
            return OrderStatus.PENDING;
        }

        // Count items in each status
        boolean hasToAccept = orderDetails.stream()
            .anyMatch(detail -> detail.getItemStatus() == OrderStatus.TO_ACCEPT);

        boolean hasPending = orderDetails.stream()
            .anyMatch(detail -> detail.getItemStatus() == OrderStatus.PENDING);
        
        boolean hasInPreparation = orderDetails.stream()
            .anyMatch(detail -> detail.getItemStatus() == OrderStatus.IN_PREPARATION);
        
        boolean hasReady = orderDetails.stream()
            .anyMatch(detail -> detail.getItemStatus() == OrderStatus.READY);
        
        long deliveredCount = orderDetails.stream()
            .filter(detail -> detail.getItemStatus() == OrderStatus.DELIVERED)
            .count();
        
        int totalItems = orderDetails.size();

        // Order status follows the MINIMUM (lowest) item status
        // Hierarchy: TO_ACCEPT < PENDING < IN_PREPARATION < READY < DELIVERED

        // If ANY item is still TO_ACCEPT (waiting for restaurant acceptance),
        // the entire order is TO_ACCEPT
        if (hasToAccept) {
            return OrderStatus.TO_ACCEPT;
        }

        // If ANY item is still PENDING, entire order is PENDING
        if (hasPending) {
            return OrderStatus.PENDING;
        }
        
        // If no PENDING items, but ANY item is IN_PREPARATION, order is IN_PREPARATION
        if (hasInPreparation) {
            return OrderStatus.IN_PREPARATION;
        }
        
        // If no PENDING or IN_PREPARATION items, but ANY item is READY, order is READY
        if (hasReady) {
            return OrderStatus.READY;
        }
        
        // All items delivered
        if (deliveredCount == totalItems) {
            return OrderStatus.DELIVERED;
        }

        // Default fallback
        return OrderStatus.PENDING;
    }

    /**
     * Update order status based on item statuses
     */
    public void updateStatusFromItems() {
        this.status = calculateStatusFromItems();
    }

    /**
     * Whether the order can be charged: status DELIVERED and every chargeable
     * line already ENTREGADO (per-item status DELIVERED).
     *
     * An order that received new items later (items still PENDING /
     * IN_PREPARATION / READY / TO_ACCEPT) is not charged until those items are
     * delivered, because only items with itemStatus ENTREGADO are collected.
     * Cancelled lines are not charged and never block the payment. Lines whose
     * per-item status is null are treated as delivered for legacy orders
     * created before the per-item status flow existed.
     */
    public boolean isReadyToCharge() {
        if (status != OrderStatus.DELIVERED) {
            return false;
        }
        if (orderDetails == null || orderDetails.isEmpty()) {
            return false;
        }
        return orderDetails.stream()
                .filter(d -> d.getItemStatus() != null
                        && d.getItemStatus() != OrderStatus.CANCELLED)
                .allMatch(d -> d.getItemStatus() == OrderStatus.DELIVERED);
    }

    /**
     * Whether some non-cancelled line has not been delivered yet
     * (order still open: extra items cooking or ready).
     */
    public boolean hasUndeliveredItems() {
        if (orderDetails == null) {
            return false;
        }
        return orderDetails.stream()
                .filter(d -> d.getItemStatus() != null
                        && d.getItemStatus() != OrderStatus.CANCELLED)
                .anyMatch(d -> d.getItemStatus() != OrderStatus.DELIVERED);
    }

    /**
     * True when at least one line has already been charged in a partial
     * (departing-guest) collection while the order stayed open.
     */
    public boolean hasPartialCollections() {
        if (orderDetails == null) {
            return false;
        }
        return orderDetails.stream().anyMatch(OrderDetail::hasPaidUnits);
    }

    /**
     * Amount already collected in partial (departing-guest) charges: every paid
     * unit at its effective price plus its prorated complement share (same
     * rounding as the departure collections).
     */
    public BigDecimal getCollectedAmount() {
        // Real collections win: every Payment row already carries the discount
        // that applied at the time it was charged, so summing their totals is
        // exact even when the discount was set midway through the bill.
        if (payments != null && !payments.isEmpty()) {
            return payments.stream()
                    .map(p -> p.getTotal() != null ? p.getTotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        // Fallback (no Payment rows yet): reconstruct from the charged units.
        if (orderDetails == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal collected = BigDecimal.ZERO;
        for (OrderDetail d : orderDetails) {
            if (d.isComboChild() || d.getItemStatus() == OrderStatus.CANCELLED) {
                continue;
            }
            BigDecimal paidQty = d.getPaidQuantityOrZero();
            if (paidQty.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            BigDecimal eff = (d.getPromotionAppliedPrice() != null)
                    ? d.getPromotionAppliedPrice()
                    : d.getUnitPrice();
            collected = collected.add(eff.multiply(paidQty).setScale(2, RoundingMode.HALF_UP));
            BigDecimal effComp = getEffectiveComplementsTotal(d);
            if (effComp.compareTo(BigDecimal.ZERO) > 0
                    && d.getQuantity() != null && d.getQuantity() > 0) {
                collected = collected.add(effComp.multiply(paidQty)
                        .divide(BigDecimal.valueOf(d.getQuantity()), 2, RoundingMode.HALF_UP));
            }
        }
        return collected;
    }

    /**
     * Complement total that must be charged for a line in a per-person payment.
     *
     * Combos are stored as a priced parent line plus $0-priced child lines, and
     * the PAID complements live on the children (e.g. "Arrachera" inside the
     * combo's Pizza Atrevida). Per-person charges only assign the parent line,
     * so for a combo parent the effective complement total is its own
     * complements PLUS the complements of its children in the same combo group
     * (non-cancelled and already delivered, so only served items are charged).
     * For every other line it is just the line's own complements.
     */
    public BigDecimal getEffectiveComplementsTotal(OrderDetail d) {
        if (d == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal own = d.getComplementsTotal() != null ? d.getComplementsTotal() : BigDecimal.ZERO;
        if (!d.isComboParent() || d.getComboGroupId() == null || orderDetails == null) {
            return own;
        }
        String group = d.getComboGroupId();
        BigDecimal children = orderDetails.stream()
                .filter(c -> c.isComboChild())
                .filter(c -> group.equals(c.getComboGroupId()))
                .filter(c -> c.getItemStatus() != OrderStatus.CANCELLED)
                .filter(c -> c.getItemStatus() == null || c.getItemStatus() == OrderStatus.DELIVERED)
                .map(c -> c.getComplementsTotal() != null ? c.getComplementsTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return own.add(children);
    }

    /**
     * Split-bill item descriptors for the payment screens (split-bill.js): every
     * non-combo-child, non-cancelled line with the units still owed, the
     * effective unit price and the EFFECTIVE complement total (combo parents
     * include the complements of their $0-priced children, so per-person
     * charges and their previews collect them too).
     */
    public List<Map<String, Object>> getSplitBillItems() {
        List<Map<String, Object>> out = new ArrayList<>();
        if (orderDetails == null) {
            return out;
        }
        for (OrderDetail d : orderDetails) {
            if (d.isComboChild()) {
                continue;
            }
            if (d.getItemStatus() != null && d.getItemStatus() == OrderStatus.CANCELLED) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getIdOrderDetail());
            m.put("name", d.getDisplayName());
            m.put("qty", d.getRemainingQuantity());
            m.put("lineQty", BigDecimal.valueOf(d.getQuantity()));
            m.put("price", d.getPromotionAppliedPrice() != null
                    ? d.getPromotionAppliedPrice()
                    : d.getUnitPrice());
            m.put("comps", getEffectiveComplementsTotal(d));
            m.put("delivered", d.getItemStatus() == null
                    || d.getItemStatus() == OrderStatus.DELIVERED);
            out.add(m);
        }
        return out;
    }

    /**
     * Total still owed after partial (departing-guest) charges.
     */
    public BigDecimal getRemainingTotal() {
        BigDecimal base = total != null ? total : BigDecimal.ZERO;
        return base.subtract(getCollectedAmount()).max(BigDecimal.ZERO);
    }

    /**
     * Get formatted collected amount (partial charges).
     */
    public String getFormattedCollectedAmount() {
        return String.format("$%.2f", getCollectedAmount());
    }

    /**
     * Get formatted remaining total.
     */
    public String getFormattedRemainingTotal() {
        return String.format("$%.2f", getRemainingTotal());
    }

    /**
     * There is at least one ENTREGADO line with units not charged yet — the
     * waiter/cashier can charge a departing guest those units right now even
     * though the rest of the order may still be pending.
     */
    public boolean hasChargeableDeliveredItems() {
        if (orderDetails == null) {
            return false;
        }
        return orderDetails.stream()
                // Per-item status null (legacy orders) counts as delivered, same as isReadyToCharge()
                .filter(d -> d.getItemStatus() == null || d.getItemStatus() == OrderStatus.DELIVERED)
                .anyMatch(OrderDetail::hasRemainingQuantity);
    }

    /**
     * Whether the "cobrar a la persona que se va" action is available:
     * the order is open (not PAID/CANCELLED, not a delivery) and there is at
     * least one delivered line that has not been fully charged.
     */
    public boolean canCollectDeparture() {
        return status != OrderStatus.PAID
                && status != OrderStatus.CANCELLED
                && orderType != OrderType.DELIVERY
                && hasChargeableDeliveredItems();
    }

    /**
     * Get pending items count
     */
    public long getPendingItemsCount() {
        if (orderDetails == null) return 0;
        return orderDetails.stream()
                .filter(detail -> detail.getItemStatus() == OrderStatus.PENDING)
                .count();
    }

    /**
     * Get count of items waiting for restaurant acceptance (TO_ACCEPT)
     */
    public long getToAcceptItemsCount() {
        if (orderDetails == null) return 0;
        return orderDetails.stream()
                .filter(detail -> detail.getItemStatus() == OrderStatus.TO_ACCEPT)
                .count();
    }

    /**
     * Check if order has any items waiting for acceptance
     */
    public boolean hasItemsToAccept() {
        return getToAcceptItemsCount() > 0;
    }

    /**
     * Get items waiting for acceptance
     */
    public List<OrderDetail> getToAcceptItems() {
        if (orderDetails == null) return new ArrayList<>();
        return orderDetails.stream()
                .filter(detail -> detail.getItemStatus() == OrderStatus.TO_ACCEPT)
                .toList();
    }

    /**
     * Get new items count
     */
    public long getNewItemsCount() {
        if (orderDetails == null) return 0;
        return orderDetails.stream()
                .filter(OrderDetail::isNew)
                .count();
    }

    /**
     * Check if order has pending items
     */
    public boolean hasPendingItems() {
        return getPendingItemsCount() > 0;
    }

    /**
     * Check if order has new items
     */
    public boolean hasNewItems() {
        return getNewItemsCount() > 0;
    }

    /**
     * Get pending items
     */
    public List<OrderDetail> getPendingItems() {
        if (orderDetails == null) return new ArrayList<>();
        return orderDetails.stream()
                .filter(detail -> detail.getItemStatus() == OrderStatus.PENDING)
                .toList();
    }

    /**
     * Get items in preparation
     */
    public List<OrderDetail> getItemsInPreparation() {
        if (orderDetails == null) return new ArrayList<>();
        return orderDetails.stream()
                .filter(detail -> detail.getItemStatus() == OrderStatus.IN_PREPARATION)
                .toList();
    }

    /**
     * Get ready items
     */
    public List<OrderDetail> getReadyItems() {
        if (orderDetails == null) return new ArrayList<>();
        return orderDetails.stream()
                .filter(detail -> detail.getItemStatus() == OrderStatus.READY)
                .toList();
    }

    /**
     * Check if order can accept new items
     */
    public boolean canAcceptNewItems() {
        // DINE_IN orders can accept new items until PAID
        // Customers are physically at the restaurant and can keep ordering
        if (this.orderType == OrderType.DINE_IN) {
            return this.status == OrderStatus.TO_ACCEPT ||
                   this.status == OrderStatus.PENDING ||
                   this.status == OrderStatus.IN_PREPARATION ||
                   this.status == OrderStatus.READY || 
                   this.status == OrderStatus.DELIVERED;
        }
        
        // TAKEOUT orders (staff perspective): can accept new items even after DELIVERED.
        // The customer may return to the counter and request additional items before paying.
        // Customer-side restriction (cannot add once DELIVERED) lives in canCustomerAcceptNewItems().
        if (this.orderType == OrderType.TAKEOUT) {
            return this.status == OrderStatus.TO_ACCEPT ||
                   this.status == OrderStatus.PENDING ||
                   this.status == OrderStatus.IN_PREPARATION ||
                   this.status == OrderStatus.READY ||
                   this.status == OrderStatus.DELIVERED;
        }
        
        // DELIVERY orders can accept new items only until READY
        // Once ON_THE_WAY, the delivery person is already on route
        // and it's not practical to add more items
        if (this.orderType == OrderType.DELIVERY) {
            return this.status == OrderStatus.TO_ACCEPT ||
                   this.status == OrderStatus.PENDING ||
                   this.status == OrderStatus.IN_PREPARATION ||
                   this.status == OrderStatus.READY;
        }
        
        return false;
    }

    /**
     * Customer-side variant of {@link #canAcceptNewItems()}.
     * Stricter than the staff method: for TAKEOUT, customers cannot add more items
     * once the order has been DELIVERED (they already picked it up). All other rules
     * are the same as {@link #canAcceptNewItems()}.
     */
    public boolean canCustomerAcceptNewItems() {
        if (this.orderType == OrderType.TAKEOUT) {
            return this.status == OrderStatus.TO_ACCEPT ||
                   this.status == OrderStatus.PENDING ||
                   this.status == OrderStatus.IN_PREPARATION ||
                   this.status == OrderStatus.READY;
        }
        return canAcceptNewItems();
    }

    /**
     * Check if items can be deleted from this order
     * Similar to canAcceptNewItems but for deletion
     * 
     * For DELIVERY orders: Cannot delete items once ON_THE_WAY or superior
     * For other orders: Cannot delete items once CANCELLED or PAID
     */
    public boolean canDeleteItems() {
        // Cannot delete from CANCELLED or PAID orders
        if (this.status == OrderStatus.CANCELLED || this.status == OrderStatus.PAID) {
            return false;
        }
        
        // DELIVERY orders: cannot delete once ON_THE_WAY or superior
        if (this.orderType == OrderType.DELIVERY) {
            return this.status == OrderStatus.TO_ACCEPT ||
                   this.status == OrderStatus.PENDING ||
                   this.status == OrderStatus.IN_PREPARATION ||
                   this.status == OrderStatus.READY;
        }
        
        // DINE_IN and TAKEOUT can delete until PAID (already checked above)
        return true;
    }
}
