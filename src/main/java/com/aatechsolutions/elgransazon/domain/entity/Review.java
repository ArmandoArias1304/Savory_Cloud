package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Review entity representing customer reviews for the restaurant
 * Each customer can only have one review per company
 */
@Entity
@Table(name = "reviews", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"customer_id", "company_id"}, name = "uk_review_customer_company")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idReview"})
public class Review implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long idReview;

    // ========== Company Relationship (Multi-Tenant) ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    @NotNull(message = "El cliente es requerido")
    private Customer customer;

    @NotNull(message = "La calificación es requerida")
    @Min(value = 1, message = "La calificación mínima es 1")
    @Max(value = 5, message = "La calificación máxima es 5")
    @Column(name = "rating", nullable = false)
    private Integer rating;

    @NotBlank(message = "El comentario es requerido")
    @Size(min = 10, max = 500, message = "El comentario debe tener entre 10 y 500 caracteres")
    @Column(name = "comment", nullable = false, length = 500)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PENDING;

    @Column(name = "is_accepted", nullable = false)
    @Builder.Default
    private Boolean isAccepted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Employee approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ========== Lifecycle Callbacks ==========

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = ReviewStatus.PENDING;
        }
        if (this.isAccepted == null) {
            this.isAccepted = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========== Business Methods ==========

    /**
     * Approve this review
     */
    public void approve(Employee employee) {
        this.status = ReviewStatus.APPROVED;
        this.isAccepted = true;
        this.approvedBy = employee;
        this.approvedAt = LocalDateTime.now();
    }

    /**
     * Reject this review
     */
    public void reject(Employee employee) {
        this.status = ReviewStatus.REJECTED;
        this.isAccepted = false;
        this.approvedBy = employee;
        this.approvedAt = LocalDateTime.now();
    }

    /**
     * Check if review is approved
     */
    public boolean isApproved() {
        return ReviewStatus.APPROVED.equals(this.status);
    }

    /**
     * Check if review is pending
     */
    public boolean isPending() {
        return ReviewStatus.PENDING.equals(this.status);
    }
}
