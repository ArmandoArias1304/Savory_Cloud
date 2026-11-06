package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ingredient_purchases", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ingredient_purchase_company_uuid", columnNames = { "company_id", "cfdi_uuid" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngredientPurchase implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "cfdi_uuid", nullable = false, length = 50)
    private String uuid;

    @Column(name = "issuer_rfc", nullable = false, length = 20)
    private String issuerRfc;

    @Column(name = "issuer_name", length = 250)
    private String issuerName;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "invoice_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private IngredientPurchaseStatus status = IngredientPurchaseStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private Employee uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private Employee cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "include_vat")
    private Boolean includeVat;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    @Builder.Default
    private List<IngredientPurchaseLine> lines = new ArrayList<>();

    public void addLine(IngredientPurchaseLine line) {
        lines.add(line);
        line.setPurchase(this);
    }

    public boolean canBeCancelled() {
        return status == IngredientPurchaseStatus.CONFIRMED
                && !lines.isEmpty()
                && lines.stream().allMatch(line -> line.getIngredient() != null && line.getStockHistory() != null);
    }
}