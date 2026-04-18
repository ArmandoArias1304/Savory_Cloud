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
import java.util.List;

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
        this.total = orderDetails.stream()
                .map(OrderDetail::getTotalWithComplements)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        
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
     * Get the original subtotal before any promotion discount, without IVA.
     * This is: sum of (unitPrice × quantity + complementsTotal) for all details / (1 + taxRate/100)
     * complementsTotal already handles sauce multiplication via OrderDetail.getComplementsTotal()
     */
    public BigDecimal getOriginalSubtotalWithoutTax() {
        if (orderDetails == null || orderDetails.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalWithTax = orderDetails.stream()
                .map(d -> {
                    BigDecimal itemTotal = d.getUnitPrice().multiply(BigDecimal.valueOf(d.getQuantity()));
                    BigDecimal compTotal = d.getComplementsTotal();
                    return itemTotal.add(compTotal);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (this.taxRate != null && this.taxRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal taxMultiplier = BigDecimal.ONE.add(
                    this.taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
            );
            return totalWithTax.divide(taxMultiplier, 2, RoundingMode.HALF_UP);
        }
        return totalWithTax.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Get the promotion discount amount without IVA.
     * Discount = originalSubtotalWithoutTax - subtotal (order.subtotal is already without IVA)
     */
    public BigDecimal getDiscountWithoutTax() {
        BigDecimal original = getOriginalSubtotalWithoutTax();
        BigDecimal current = this.subtotal != null ? this.subtotal : BigDecimal.ZERO;
        BigDecimal discount = original.subtract(current);
        return discount.compareTo(BigDecimal.ZERO) > 0 ? discount : BigDecimal.ZERO;
    }

    /**
     * Check if this order has any promotion discount applied.
     */
    public boolean hasDiscount() {
        return getDiscountWithoutTax().compareTo(new BigDecimal("0.01")) > 0;
    }

    /**
     * Get formatted original subtotal without tax (before discount)
     */
    public String getFormattedOriginalSubtotalWithoutTax() {
        return String.format("$%.2f", getOriginalSubtotalWithoutTax());
    }

    /**
     * Get formatted discount without tax
     */
    public String getFormattedDiscountWithoutTax() {
        return String.format("-$%.2f", getDiscountWithoutTax());
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
        // Hierarchy: PENDING < IN_PREPARATION < READY < DELIVERED
        
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
     * Get pending items count
     */
    public long getPendingItemsCount() {
        if (orderDetails == null) return 0;
        return orderDetails.stream()
                .filter(detail -> detail.getItemStatus() == OrderStatus.PENDING)
                .count();
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
            return this.status == OrderStatus.PENDING ||
                   this.status == OrderStatus.IN_PREPARATION ||
                   this.status == OrderStatus.READY || 
                   this.status == OrderStatus.DELIVERED;
        }
        
        // TAKEOUT orders can accept new items only until READY
        // Once DELIVERED (customer picked it up), they can't add more items
        if (this.orderType == OrderType.TAKEOUT) {
            return this.status == OrderStatus.PENDING ||
                   this.status == OrderStatus.IN_PREPARATION ||
                   this.status == OrderStatus.READY;
        }
        
        // DELIVERY orders can accept new items only until READY
        // Once ON_THE_WAY, the delivery person is already on route
        // and it's not practical to add more items
        if (this.orderType == OrderType.DELIVERY) {
            return this.status == OrderStatus.PENDING ||
                   this.status == OrderStatus.IN_PREPARATION ||
                   this.status == OrderStatus.READY;
        }
        
        return false;
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
            return this.status == OrderStatus.PENDING ||
                   this.status == OrderStatus.IN_PREPARATION ||
                   this.status == OrderStatus.READY;
        }
        
        // DINE_IN and TAKEOUT can delete until PAID (already checked above)
        return true;
    }
}
