package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.domain.entity.Review;
import com.aatechsolutions.elgransazon.domain.entity.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Review entity
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * Find review by customer
     */
    Optional<Review> findByCustomer(Customer customer);

    /**
     * Find all reviews by status
     */
    List<Review> findByStatusOrderByCreatedAtDesc(ReviewStatus status);

    /**
     * Find all reviews ordered by creation date
     */
    List<Review> findAllByOrderByCreatedAtDesc();

    /**
     * Count reviews by status
     */
    long countByStatus(ReviewStatus status);

    /**
     * Check if customer already has a review
     */
    boolean existsByCustomer(Customer customer);

    /**
     * Get approved reviews with customer info (for landing page)
     */
    @Query("SELECT r FROM Review r JOIN FETCH r.customer WHERE r.status = 'APPROVED' ORDER BY r.createdAt DESC")
    List<Review> findApprovedReviewsWithCustomer();

    /**
     * Get all reviews with customer and employee info (for admin)
     */
    @Query("SELECT r FROM Review r LEFT JOIN FETCH r.customer LEFT JOIN FETCH r.approvedBy ORDER BY r.createdAt DESC")
    List<Review> findAllWithDetails();

    /**
     * Get reviews by status with details
     */
    @Query("SELECT r FROM Review r LEFT JOIN FETCH r.customer LEFT JOIN FETCH r.approvedBy WHERE r.status = :status ORDER BY r.createdAt DESC")
    List<Review> findByStatusWithDetails(ReviewStatus status);

    // ========== Multi-Tenant Methods (by Company) ==========

    /**
     * Find all reviews by company
     */
    List<Review> findByCompany(Company company);

    /**
     * Find review by ID and company (for security validation)
     */
    Optional<Review> findByIdReviewAndCompany(Long idReview, Company company);

    /**
     * Find review by customer and company
     */
    Optional<Review> findByCustomerAndCompany(Customer customer, Company company);

    /**
     * Check if customer has review for company
     */
    boolean existsByCustomerAndCompany(Customer customer, Company company);

    /**
     * Find approved reviews by company
     */
    @Query("SELECT r FROM Review r JOIN FETCH r.customer WHERE r.company = :company AND r.status = 'APPROVED' ORDER BY r.createdAt DESC")
    List<Review> findApprovedReviewsByCompany(@Param("company") Company company);

    /**
     * Count reviews by status and company
     */
    long countByStatusAndCompany(ReviewStatus status, Company company);

    /**
     * Get all reviews with details for a company (for admin)
     */
    @Query("SELECT r FROM Review r LEFT JOIN FETCH r.customer LEFT JOIN FETCH r.approvedBy WHERE r.company = :company ORDER BY r.createdAt DESC")
    List<Review> findAllWithDetailsByCompany(@Param("company") Company company);

    /**
     * Get reviews by status with details for a company
     */
    @Query("SELECT r FROM Review r LEFT JOIN FETCH r.customer LEFT JOIN FETCH r.approvedBy WHERE r.company = :company AND r.status = :status ORDER BY r.createdAt DESC")
    List<Review> findByStatusWithDetailsByCompany(@Param("status") ReviewStatus status, @Param("company") Company company);

    // ========== Migration Helper Methods ==========

    /**
     * Find all reviews without company (for data migration)
     */
    List<Review> findByCompanyIsNull();
}
