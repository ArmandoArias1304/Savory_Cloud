package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * A per-person account/payment of a split bill.
 *
 * One Order may have 1..N Payment rows. Each Payment represents a single
 * person's account: its own items (via {@link PaymentDetail}), its own
 * subtotal/IVA/total, its own tip, payment method, collector, autofactura
 * key/QR and CFDI fields.
 *
 * The parent Order keeps status DELIVERED until ALL its payments are done;
 * only then it transitions to PAID (table freed + employee stats updated once).
 *
 * Folio format: {@code ORD-YYYYMMDD-NNN-XX} where the suffix {@code -XX} links
 * the account to its parent order number (multitenant + company timezone date
 * come from the parent order's generated number).
 */
@Entity
@Table(name = "payments", uniqueConstraints = {
    @UniqueConstraint(name = "uk_payment_folio_company", columnNames = {"payment_folio", "company_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idPayment"})
@ToString(exclude = {"company", "order", "paidBy", "paymentDetails"})
public class Payment implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_payment")
    private Long idPayment;

    // ========== Company Relationship (Multi-Tenant) ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // ========== Parent Order ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_order", nullable = false)
    private Order order;

    /**
     * 1-based account index within the parent order (1..N).
     */
    @NotNull
    @Column(name = "account_number", nullable = false)
    private Integer accountNumber;

    /**
     * Folio of this account: parent order number + "-XX" (e.g. ORD-20260906-001-02).
     */
    @NotBlank
    @Size(max = 60)
    @Column(name = "payment_folio", nullable = false, length = 60)
    private String paymentFolio;

    /**
     * How the account totals were derived (EQUAL or ITEMS).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "split_mode", length = 20)
    private SplitMode splitMode;

    /**
     * Total number of accounts in the split (snapshot).
     */
    @Column(name = "split_count")
    private Integer splitCount;

    /**
     * Optional label for the person, e.g. "Persona 1" (snapshot from the form).
     */
    @Size(max = 100)
    @Column(name = "person_label", length = 100)
    private String personLabel;

    // ========== Calculations (equivalent to Order's fields) ==========

    /**
     * Subtotal sin IVA, después de descuento de orden prorrateado (2 decimals).
     */
    @NotNull
    @Column(name = "subtotal", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @NotNull
    @Column(name = "tax_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal taxRate;

    @NotNull
    @Column(name = "tax_amount", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /**
     * Share of the order-level discount (incluye IVA) absorbed by this account.
     */
    @NotNull
    @Column(name = "order_discount", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal orderDiscount = BigDecimal.ZERO;

    /**
     * Snapshot of the order-level discount percentage applied to this account
     * (null when the discount was a fixed amount). Lets each account ticket show
     * its share of the global percentage discount.
     */
    @Column(name = "order_discount_percent", precision = 5, scale = 2)
    private BigDecimal orderDiscountPercent;

    /**
     * Share of the delivery cost (incluye IVA) absorbed by this account (0 for non-DELIVERY).
     */
    @NotNull
    @Column(name = "delivery_cost", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal deliveryCost = BigDecimal.ZERO;

    /**
     * Total a pagar por esta cuenta (incluye IVA, después de descuento, sin propina).
     */
    @NotNull
    @Column(name = "total", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    // ========== Tip / Payment Method ==========

    @DecimalMin(value = "0.0", message = "La propina no puede ser negativa")
    @Column(name = "tip", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal tip = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethodType paymentMethod;

    // Employee who collected this account's payment
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_paid_by", nullable = true)
    private Employee paidBy;

    /**
     * Timestamp (UTC) when this account was paid.
     * Authoritative per-account payment timestamp (autofactura deadline anchor).
     */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // ========== Facturama Autofactura (per account) ==========

    @Column(name = "facturama_cfdi_id", length = 100)
    private String facturamaCfdiId;

    @Column(name = "facturama_cfdi_uuid", length = 100)
    private String facturamaCfdiUuid;

    /**
     * Unique autofactura key (UUID) generated at payment time for THIS account.
     * Used in the self-invoice URL: /autofactura/{key}
     */
    @Column(name = "autofactura_key", length = 50)
    private String autofacturaKey;

    @Column(name = "facturama_cfdi_created_at")
    private LocalDateTime facturamaCfdiCreatedAt;

    // ========== Factura Global (Público en General) ==========
    // When the ADMIN emits the daily/monthly global invoice, every paid account without
    // an individual CFDI gets these fields filled. Once set, the autofactura page blocks
    // individual invoicing (SAT forbids invoicing the same operation twice).

    /**
     * Facturama CFDI ID of the global invoice (público en general) that included this account.
     */
    @Column(name = "factura_global_cfdi_id", length = 100)
    private String facturaGlobalCfdiId;

    /**
     * SAT fiscal folio UUID of the global invoice that included this account.
     */
    @Column(name = "factura_global_cfdi_uuid", length = 100)
    private String facturaGlobalCfdiUuid;

    /**
     * Timestamp (UTC) when the global invoice CFDI was created via Facturama.
     */
    @Column(name = "factura_global_cfdi_created_at")
    private LocalDateTime facturaGlobalCfdiCreatedAt;

    /**
     * Full self-invoice URL for this account (e.g. https://slug.domain.com/autofactura/{key}).
     */
    @Column(name = "self_invoice_url", length = 300)
    private String selfInvoiceUrl;

    // ========== Audit Fields ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @NotBlank
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // ========== Relationships ==========

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private java.util.List<PaymentDetail> paymentDetails = new java.util.ArrayList<>();

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

    public void addPaymentDetail(PaymentDetail paymentDetail) {
        this.paymentDetails.add(paymentDetail);
        paymentDetail.setPayment(this);
    }

    /**
     * Total with tip (what the person actually pays).
     */
    public BigDecimal getTotalWithTip() {
        BigDecimal baseTotal = total != null ? total : BigDecimal.ZERO;
        BigDecimal tipAmount = tip != null ? tip : BigDecimal.ZERO;
        return baseTotal.add(tipAmount);
    }

    public boolean hasOrderDiscount() {
        return orderDiscount != null && orderDiscount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasOrderDiscountPercent() {
        return orderDiscountPercent != null && orderDiscountPercent.compareTo(BigDecimal.ZERO) > 0;
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
     * Order discount share sin IVA (rounded).
     */
    public BigDecimal getOrderDiscountWithoutTax() {
        if (orderDiscount == null || orderDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (taxRate == null || taxRate.compareTo(BigDecimal.ZERO) <= 0) {
            return orderDiscount;
        }
        BigDecimal taxMultiplier = BigDecimal.ONE.add(
                taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
        );
        return orderDiscount.divide(taxMultiplier, 2, RoundingMode.HALF_UP);
    }

    /**
     * Subtotal sin IVA "antes" del descuento de orden, para mostrar en tickets/vistas
     * (equivale a subtotal + descuento sin IVA).
     */
    public BigDecimal getDisplaySubtotal() {
        BigDecimal sub = subtotal != null ? subtotal : BigDecimal.ZERO;
        return sub.add(getOrderDiscountWithoutTax()).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Autofactura deadline: last day of the month in which the account was paid,
     * expressed in the company's local timezone. Anchored on {@code paidAt}
     * (stored UTC), falling back to {@code createdAt}.
     */
    public java.time.LocalDate getInvoiceDeadline(java.time.ZoneId zone) {
        LocalDateTime anchor = paidAt != null ? paidAt : createdAt;
        if (anchor == null || zone == null) {
            return null;
        }
        java.time.LocalDate localDate = anchor.atZone(java.time.ZoneOffset.UTC)
                .withZoneSameInstant(zone)
                .toLocalDate();
        return YearMonth.from(localDate).atEndOfMonth();
    }

    /**
     * Check whether the invoicing window for this account has expired
     * (strict cutoff at end-of-day on the last day of the payment month, company timezone).
     */
    public boolean isAutofacturaExpired(java.time.ZoneId zone) {
        java.time.LocalDate deadline = getInvoiceDeadline(zone);
        if (deadline == null) {
            return false;
        }
        return java.time.LocalDate.now(zone).isAfter(deadline);
    }
}