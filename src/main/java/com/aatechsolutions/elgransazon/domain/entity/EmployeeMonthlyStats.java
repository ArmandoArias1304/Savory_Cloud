package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing monthly statistics for employees
 * Used to track "Employee of the Month" metrics:
 * - Waiters: total sales (sum of order totals without tip)
 * - Chefs: total orders completed
 */
@Entity
@Table(name = "employee_monthly_stats",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_employee_month_year",
           columnNames = {"employee_id", "month", "year"}
       ),
       indexes = {
           @Index(name = "idx_employee_monthly_stats_month_year", columnList = "month, year"),
           @Index(name = "idx_employee_monthly_stats_employee", columnList = "employee_id"),
           @Index(name = "idx_employee_monthly_stats_total_sales", columnList = "total_sales DESC"),
           @Index(name = "idx_employee_monthly_stats_total_orders", columnList = "total_orders DESC")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeMonthlyStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_stat")
    private Long idStat;

    /**
     * Employee reference
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /**
     * Month (1-12)
     */
    @Column(name = "month", nullable = false)
    private Integer month;

    /**
     * Year (e.g., 2025)
     */
    @Column(name = "year", nullable = false)
    private Integer year;

    /**
     * Total sales for waiters (sum of order totals without tip)
     */
    @Column(name = "total_sales", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalSales = BigDecimal.ZERO;

    /**
     * Total orders completed for chefs
     */
    @Column(name = "total_orders", nullable = false)
    @Builder.Default
    private Integer totalOrders = 0;

    /**
     * Creation timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last update timestamp
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * JPA lifecycle callback - set timestamps on creation
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * JPA lifecycle callback - update timestamp on modification
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Add sales amount (for waiters)
     */
    public void addSales(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            this.totalSales = this.totalSales.add(amount);
        }
    }

    /**
     * Increment orders count (for chefs)
     */
    public void incrementOrders() {
        this.totalOrders++;
    }

    /**
     * Reset statistics (used when new month starts)
     */
    public void reset() {
        this.totalSales = BigDecimal.ZERO;
        this.totalOrders = 0;
    }
}
