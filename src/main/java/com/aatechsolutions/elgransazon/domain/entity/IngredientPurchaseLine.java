package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "ingredient_purchase_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngredientPurchaseLine implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false)
    private IngredientPurchase purchase;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "product_code", length = 50)
    private String productCode;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Column(name = "purchased_quantity", nullable = false, precision = 12, scale = 4)
    private BigDecimal purchasedQuantity;

    @Column(name = "purchase_unit", length = 30)
    private String purchaseUnit;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "line_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "discount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "vat_amount", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal vatAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_history_id", unique = true)
    private IngredientStockHistory stockHistory;

    @Column(name = "conversion_factor", nullable = false, precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal conversionFactor = BigDecimal.ONE;

    @Column(name = "stock_quantity", precision = 12, scale = 3)
    private BigDecimal stockQuantity;

    @Column(name = "cost_per_stock_unit", precision = 10, scale = 2)
    private BigDecimal costPerStockUnit;
}