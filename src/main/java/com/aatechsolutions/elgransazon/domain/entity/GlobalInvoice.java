package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Record of a global invoice (factura global / público en general) emitted by the ADMIN
 * from the facturación panel. Each record stores the Facturama CFDI of ONE global invoice
 * that amparó las operaciones de un periodo (un día o un mes completo) que no fueron
 * facturadas individualmente vía autofactura.
 *
 * Regla 2.7.1.21 de la RMF: receptor genérico XAXX010101000, IVA desglosado, un concepto
 * por operación con su folio, emitida a más tardar 24 horas después del cierre del periodo.
 */
@Entity
@Table(name = "global_invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"company"})
public class GlobalInvoice implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ========== Company Relationship (Multi-Tenant) ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // ========== Facturama CFDI Data ==========

    /**
     * Facturama CFDI ID of the global invoice (used to download PDF/XML).
     */
    @Column(name = "cfdi_id", length = 100)
    private String cfdiId;

    /**
     * SAT fiscal folio UUID of the global invoice.
     */
    @Column(name = "cfdi_uuid", length = 100)
    private String cfdiUuid;

    /**
     * Internal folio assigned to the global invoice (e.g. GLOBAL-20260906).
     */
    @Column(name = "folio", length = 60)
    private String folio;

    // ========== Period Covered ==========

    /**
     * "DAILY" (un día) or "MONTHLY" (un mes completo).
     */
    @Column(name = "period_type", length = 10)
    private String periodType;

    /**
     * First day (inclusive) of the covered period, company local timezone.
     */
    @Column(name = "period_from")
    private LocalDate periodFrom;

    /**
     * Last day (inclusive) of the covered period, company local timezone.
     */
    @Column(name = "period_to")
    private LocalDate periodTo;

    // ========== Totals ==========

    /**
     * Total con IVA amparado por la factura global (sum of the included tickets).
     */
    @Column(name = "total", precision = 10, scale = 2)
    private BigDecimal total;

    /**
     * Number of tickets/operations included (each becomes one CFDI concept).
     */
    @Column(name = "ticket_count")
    private Integer ticketCount;

    /**
     * SAT c_FormaPago of the payment form with the highest amount among the included
     * operations (required by Facturama for the global invoice header).
     */
    @Column(name = "payment_form", length = 5)
    private String paymentForm;

    // ========== Audit ==========

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}