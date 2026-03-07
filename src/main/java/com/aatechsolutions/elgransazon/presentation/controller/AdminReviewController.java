package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.ReviewService;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Review;
import com.aatechsolutions.elgransazon.domain.entity.ReviewStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing customer reviews (Admin/Manager only)
 */
@Controller
@RequestMapping("/admin/reviews")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class AdminReviewController {

    private final ReviewService reviewService;
    private final EmployeeService employeeService;

    /**
     * Display all reviews with filtering options
     */
    @GetMapping
    public String listReviews(
            @RequestParam(required = false) String status,
            Model model) {
        
        log.debug("Admin accessing reviews list with status filter: {}", status);
        
        try {
            List<Review> reviews;
            
            if (status != null && !status.isEmpty() && !status.equals("ALL")) {
                ReviewStatus reviewStatus = ReviewStatus.valueOf(status);
                reviews = reviewService.getReviewsByStatus(reviewStatus);
            } else {
                reviews = reviewService.getAllReviews();
            }
            
            // Get counts for badges
            Long pendingCount = reviewService.getPendingReviewsCount();
            Long approvedCount = (long) reviewService.getReviewsByStatus(ReviewStatus.APPROVED).size();
            Long rejectedCount = (long) reviewService.getReviewsByStatus(ReviewStatus.REJECTED).size();
            
            model.addAttribute("reviews", reviews);
            model.addAttribute("currentFilter", status != null ? status : "ALL");
            model.addAttribute("pendingCount", pendingCount != null ? pendingCount : 0L);
            model.addAttribute("approvedCount", approvedCount != null ? approvedCount : 0L);
            model.addAttribute("rejectedCount", rejectedCount != null ? rejectedCount : 0L);
            
            return "admin/reviews/list";
            
        } catch (Exception e) {
            log.error("Error loading reviews list", e);
            model.addAttribute("reviews", List.of());
            model.addAttribute("currentFilter", "ALL");
            model.addAttribute("pendingCount", 0L);
            model.addAttribute("approvedCount", 0L);
            model.addAttribute("rejectedCount", 0L);
            model.addAttribute("errorMessage", "Error al cargar las reseñas: " + e.getMessage());
            return "admin/reviews/list";
        }
    }

    /**
     * Approve a review (AJAX endpoint)
     */
    @PostMapping("/{id}/approve")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> approveReview(
            @PathVariable Long id,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            Employee employee = employeeService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));
            
            Review review = reviewService.approveReview(id, employee);
            
            log.info("Review {} approved by {}", id, employee.getUsername());
            
            response.put("success", true);
            response.put("message", "Reseña aprobada exitosamente");
            response.put("status", review.getStatus().name());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error approving review {}", id, e);
            response.put("success", false);
            response.put("message", "Error al aprobar reseña: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Reject a review (AJAX endpoint)
     */
    @PostMapping("/{id}/reject")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> rejectReview(
            @PathVariable Long id,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            Employee employee = employeeService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));
            
            Review review = reviewService.rejectReview(id, employee);
            
            log.info("Review {} rejected by {}", id, employee.getUsername());
            
            response.put("success", true);
            response.put("message", "Reseña rechazada exitosamente");
            response.put("status", review.getStatus().name());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error rejecting review {}", id, e);
            response.put("success", false);
            response.put("message", "Error al rechazar reseña: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Delete a review (AJAX endpoint)
     */
    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteReview(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            reviewService.deleteReview(id);
            
            log.info("Review {} deleted", id);
            
            response.put("success", true);
            response.put("message", "Reseña eliminada exitosamente");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error deleting review {}", id, e);
            response.put("success", false);
            response.put("message", "Error al eliminar reseña: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
