package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.BusinessHoursService;
import com.aatechsolutions.elgransazon.application.service.DashboardService;
import com.aatechsolutions.elgransazon.presentation.dto.DashboardStatsDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * Controller for Administrator and Manager role views
 * Handles all admin-related pages and operations
 * Accessible by ADMIN and MANAGER roles
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final DashboardService dashboardService;
    private final BusinessHoursService businessHoursService;

    /**
     * Display admin dashboard with real-time statistics
     *
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @param request HTTP request to access license warning attributes
     * @return admin dashboard view
     */
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model, HttpServletRequest request) {
        String username = authentication.getName();
        log.info("Admin {} accessed dashboard", username);
        
        try {
            // Get dashboard statistics
            DashboardStatsDTO stats = dashboardService.getDashboardStats();
            
            model.addAttribute("username", username);
            model.addAttribute("role", "Administrator");
            model.addAttribute("stats", stats);
            
            // Check if restaurant is currently open
            boolean isRestaurantOpen = businessHoursService.isOpenNow();
            model.addAttribute("isRestaurantOpen", isRestaurantOpen);
            log.debug("Restaurant is currently: {}", isRestaurantOpen ? "open" : "closed");
            
            // Add license warning attributes if present (set by LicenseInterceptor)
            if (request.getAttribute("showLicenseWarning") != null) {
                model.addAttribute("showLicenseWarning", request.getAttribute("showLicenseWarning"));
                model.addAttribute("daysLeft", request.getAttribute("daysLeft"));
                model.addAttribute("expirationDate", request.getAttribute("expirationDate"));
                model.addAttribute("billingCycle", request.getAttribute("billingCycle"));
                log.info("License warning added to dashboard for admin {}: {} days remaining", 
                         username, request.getAttribute("daysLeft"));
            }
            
            log.debug("Dashboard stats loaded successfully for user: {}", username);
        } catch (Exception e) {
            log.error("Error loading dashboard stats for user: " + username, e);
            model.addAttribute("errorMessage", "Error al cargar las estadísticas del dashboard");
        }
        
        return "admin/dashboard";
    }
    
    /**
     * Get popular items filtered by period (today, week, month)
     * REST endpoint for AJAX calls
     */
    @GetMapping("/dashboard/popular-items")
    @ResponseBody
    public List<DashboardStatsDTO.PopularItemDTO> getPopularItems(
            @RequestParam(defaultValue = "today") String period) {
        log.debug("Fetching popular items for period: {}", period);
        return dashboardService.getPopularItemsByPeriod(period);
    }
    
    /**
     * Get dashboard statistics - REST endpoint for real-time updates
     */
    @GetMapping("/dashboard/stats")
    @ResponseBody
    public DashboardStatsDTO getDashboardStats() {
        log.debug("Fetching dashboard stats via REST");
        return dashboardService.getDashboardStats();
    }
}
