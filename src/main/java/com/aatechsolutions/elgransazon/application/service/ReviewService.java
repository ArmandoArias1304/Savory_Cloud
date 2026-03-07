package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Review;
import com.aatechsolutions.elgransazon.domain.entity.ReviewStatus;
import com.aatechsolutions.elgransazon.domain.repository.ReviewRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing customer reviews
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;

    /**
     * Create or update a customer review
     * If customer already has a review, update it. Otherwise, create new one.
     */
    @Transactional
    public Review createOrUpdateReview(Customer customer, Integer rating, String comment) {
        log.info("Creating/updating review for customer: {}", customer.getUsername());
        
        Company company = CompanyContext.requireCurrentCompany();
        Optional<Review> existingReview = reviewRepository.findByCustomerAndCompany(customer, company);
        
        if (existingReview.isPresent()) {
            // Update existing review
            Review review = existingReview.get();
            review.setRating(rating);
            review.setComment(comment);
            review.setStatus(ReviewStatus.PENDING); // Reset to pending for re-approval
            review.setApprovedBy(null);
            review.setApprovedAt(null);
            
            log.info("Updated review for customer: {}", customer.getUsername());
            return reviewRepository.save(review);
        } else {
            // Create new review
            Review newReview = Review.builder()
                    .customer(customer)
                    .company(company)
                    .rating(rating)
                    .comment(comment)
                    .status(ReviewStatus.PENDING)
                    .build();
            
            log.info("Created new review for customer: {}", customer.getUsername());
            return reviewRepository.save(newReview);
        }
    }

    /**
     * Get review by customer
     */
    @Transactional(readOnly = true)
    public Optional<Review> getReviewByCustomer(Customer customer) {
        Company company = CompanyContext.requireCurrentCompany();
        return reviewRepository.findByCustomerAndCompany(customer, company);
    }

    /**
     * Get all reviews with details (for admin)
     */
    @Transactional(readOnly = true)
    public List<Review> getAllReviews() {
        Company company = CompanyContext.requireCurrentCompany();
        return reviewRepository.findAllWithDetailsByCompany(company);
    }

    /**
     * Get reviews by status
     */
    @Transactional(readOnly = true)
    public List<Review> getReviewsByStatus(ReviewStatus status) {
        Company company = CompanyContext.requireCurrentCompany();
        return reviewRepository.findByStatusWithDetailsByCompany(status, company);
    }

    /**
     * Get all pending reviews
     */
    @Transactional(readOnly = true)
    public List<Review> getPendingReviews() {
        Company company = CompanyContext.requireCurrentCompany();
        return reviewRepository.findByStatusWithDetailsByCompany(ReviewStatus.PENDING, company);
    }

    /**
     * Get all approved reviews (for landing page)
     */
    @Transactional(readOnly = true)
    public List<Review> getApprovedReviews() {
        Company company = CompanyContext.requireCurrentCompany();
        return reviewRepository.findApprovedReviewsByCompany(company);
    }

    /**
     * Get count of pending reviews (for badge notification)
     */
    @Transactional(readOnly = true)
    public long getPendingReviewsCount() {
        Company company = CompanyContext.requireCurrentCompany();
        return reviewRepository.countByStatusAndCompany(ReviewStatus.PENDING, company);
    }

    /**
     * Approve a review
     */
    @Transactional
    public Review approveReview(Long reviewId, Employee approver) {
        log.info("Approving review ID: {} by employee: {}", reviewId, approver.getUsername());
        
        Company company = CompanyContext.requireCurrentCompany();
        Review review = reviewRepository.findByIdReviewAndCompany(reviewId, company)
                .orElseThrow(() -> new IllegalArgumentException("Review not found with ID: " + reviewId));
        
        review.approve(approver);
        
        return reviewRepository.save(review);
    }

    /**
     * Reject a review
     */
    @Transactional
    public Review rejectReview(Long reviewId, Employee rejector) {
        log.info("Rejecting review ID: {} by employee: {}", reviewId, rejector.getUsername());
        
        Company company = CompanyContext.requireCurrentCompany();
        Review review = reviewRepository.findByIdReviewAndCompany(reviewId, company)
                .orElseThrow(() -> new IllegalArgumentException("Review not found with ID: " + reviewId));
        
        review.reject(rejector);
        
        return reviewRepository.save(review);
    }

    /**
     * Get review by ID
     */
    @Transactional(readOnly = true)
    public Optional<Review> getReviewById(Long id) {
        Company company = CompanyContext.requireCurrentCompany();
        return reviewRepository.findByIdReviewAndCompany(id, company);
    }

    /**
     * Delete a review
     */
    @Transactional
    public void deleteReview(Long reviewId) {
        log.info("Deleting review ID: {}", reviewId);
        Company company = CompanyContext.requireCurrentCompany();
        Review review = reviewRepository.findByIdReviewAndCompany(reviewId, company)
                .orElseThrow(() -> new IllegalArgumentException("Review not found with ID: " + reviewId));
        reviewRepository.delete(review);
    }

    /**
     * Check if customer already has a review
     */
    @Transactional(readOnly = true)
    public boolean customerHasReview(Customer customer) {
        Company company = CompanyContext.requireCurrentCompany();
        return reviewRepository.existsByCustomerAndCompany(customer, company);
    }
}
