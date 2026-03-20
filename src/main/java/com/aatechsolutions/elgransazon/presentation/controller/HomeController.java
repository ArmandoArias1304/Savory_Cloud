package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.BusinessHoursService;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.EmployeeMonthlyStatsService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.application.service.LandingImageService;
import com.aatechsolutions.elgransazon.application.service.LicenseService;
import com.aatechsolutions.elgransazon.application.service.PromotionService;
import com.aatechsolutions.elgransazon.application.service.ReviewService;
import com.aatechsolutions.elgransazon.application.service.SocialNetworkService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.BusinessHours;
import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.DayOfWeek;
import com.aatechsolutions.elgransazon.domain.entity.EmployeeMonthlyStats;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.Promotion;
import com.aatechsolutions.elgransazon.domain.entity.PromotionType;
import com.aatechsolutions.elgransazon.domain.entity.Review;
import com.aatechsolutions.elgransazon.domain.entity.SocialNetwork;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for home/dashboard views
 * MULTI-TENANT: Uses CompanyContext to show data for the current company
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class HomeController {

    private final BusinessHoursService businessHoursService;
    private final SocialNetworkService socialNetworkService;
    private final PromotionService promotionService;
    private final ReviewService reviewService;
    private final EmployeeMonthlyStatsService monthlyStatsService;
    private final LicenseService licenseService;
    private final CategoryService categoryService;
    private final ItemMenuService itemMenuService;
    private final SystemConfigurationService systemConfigurationService;
    private final LandingImageService landingImageService;
    private final ResourceLoader resourceLoader;
    private final DateTimeService dateTimeService;

    /**
     * Display home/landing page with system configuration data
     * MULTI-TENANT: Shows data for the current company from CompanyContext
     * Redirects to /login if license doesn't include landing page access
     * 
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @return landing view name or redirect to login
     */
    @GetMapping({"/", "/home"})
    public String home(Authentication authentication, Model model) {
        // MULTI-TENANT: Get current company from context
        Company currentCompany = CompanyContext.getCurrentCompany();
        
        if (currentCompany == null) {
            log.info("No company context found - showing system landing page");
            // Show generic system landing page when no company context
            return "home/system-landing";
        }
        
        log.info("Serving landing page for company: {} ({})", 
            currentCompany.getName(), currentCompany.getSlug());
        model.addAttribute("company", currentCompany);
        
        // Check if license has landing page access (WEB or ECOMMERCE)
        if (!licenseService.hasLandingPageAccess()) {
            log.info("License doesn't have landing page access (BASIC package). Redirecting to /login");
            return "redirect:/login";
        }

        if (authentication != null) {
            String username = authentication.getName();
            log.info("User {} accessed home page", username);
            model.addAttribute("username", username);
        }
        
        // Note: systemConfig is already provided by GlobalControllerAdvice
        // Load additional data
        List<BusinessHours> businessHours = businessHoursService.getAllBusinessHours();
        List<SocialNetwork> activeSocialNetworks = socialNetworkService.getAllActiveSocialNetworks();
        
        // Sort business hours by week order (Monday to Sunday)
        businessHours.sort((h1, h2) -> h1.getDayOfWeek().compareTo(h2.getDayOfWeek()));
        
        // Check if restaurant is currently open
        LocalDateTime now = dateTimeService.nowLocal();
        java.time.DayOfWeek javaDayOfWeek = now.getDayOfWeek();
        DayOfWeek currentDay = DayOfWeek.valueOf(javaDayOfWeek.name());
        LocalTime currentTime = now.toLocalTime();
        
        boolean isOpen = false;
        BusinessHours todayHours = null;
        
        for (BusinessHours hours : businessHours) {
            if (hours.getDayOfWeek() == currentDay) {
                todayHours = hours;
                if (!hours.getIsClosed()) {
                    isOpen = hours.isOpenAt(currentTime);
                }
                break;
            }
        }
        
        model.addAttribute("businessHours", businessHours);
        model.addAttribute("socialNetworks", activeSocialNetworks);
        model.addAttribute("isOpen", isOpen);
        model.addAttribute("todayHours", todayHours);
        model.addAttribute("currentDay", currentDay);
        
        // Check if license has customer module access (ECOMMERCE only)
        boolean hasCustomerModule = licenseService.hasCustomerModuleAccess();
        model.addAttribute("hasCustomerModule", hasCustomerModule);
        
        // Load active promotions (one of each type)
        Promotion promoCombo = promotionService.findActiveByType(PromotionType.BUY_X_PAY_Y)
            .stream().findFirst().orElse(null);
        
        Promotion promoPercent = promotionService.findActiveByType(PromotionType.PERCENTAGE_DISCOUNT)
            .stream().findFirst().orElse(null);
        
        Promotion promoFixed = promotionService.findActiveByType(PromotionType.FIXED_AMOUNT_DISCOUNT)
            .stream().findFirst().orElse(null);
        
        model.addAttribute("promoCombo", promoCombo);
        model.addAttribute("promoPercent", promoPercent);
        model.addAttribute("promoFixed", promoFixed);
        
        // Load approved reviews for testimonials section
        List<Review> approvedReviews = reviewService.getApprovedReviews();
        model.addAttribute("approvedReviews", approvedReviews);
        
        // Load employees of the month
        try {
            EmployeeMonthlyStats waiterOfMonth = monthlyStatsService.getWaiterOfCurrentMonth().orElse(null);
            EmployeeMonthlyStats chefOfMonth = monthlyStatsService.getChefOfCurrentMonth().orElse(null);
            EmployeeMonthlyStats baristaOfMonth = monthlyStatsService.getBaristaOfCurrentMonth().orElse(null);
            EmployeeMonthlyStats cashierOfMonth = monthlyStatsService.getCashierOfCurrentMonth().orElse(null);
            
            model.addAttribute("waiterOfMonth", waiterOfMonth);
            model.addAttribute("chefOfMonth", chefOfMonth);
            model.addAttribute("baristaOfMonth", baristaOfMonth);
            model.addAttribute("cashierOfMonth", cashierOfMonth);
            
            log.debug("Employees of the month loaded: waiter={}, chef={}, barista={}, cashier={}", 
                     waiterOfMonth != null ? waiterOfMonth.getEmployee().getFullName() : "none",
                     chefOfMonth != null ? chefOfMonth.getEmployee().getFullName() : "none",
                     baristaOfMonth != null ? baristaOfMonth.getEmployee().getFullName() : "none",
                     cashierOfMonth != null ? cashierOfMonth.getEmployee().getFullName() : "none");
        } catch (Exception e) {
            log.error("Error loading employees of the month: {}", e.getMessage(), e);
            model.addAttribute("waiterOfMonth", null);
            model.addAttribute("chefOfMonth", null);
            model.addAttribute("baristaOfMonth", null);
            model.addAttribute("cashierOfMonth", null);
        }
        
        // Load landing page images for this company
        Map<String, String> landingImages = landingImageService.getImageMapForCompany(currentCompany);
        model.addAttribute("landingImages", landingImages);
        
        // MULTI-TENANT: Build company-specific landing template name
        // Example: company name "Pizza Max" → template "home/landingPizzaMax"
        String companyTemplateName = buildCompanyTemplateName(currentCompany.getName());
        
        // Check if template exists, fallback to system landing if not
        if (!templateExists(companyTemplateName)) {
            log.warn("Template {} not found for company {}. Falling back to system-landing.",
                companyTemplateName, currentCompany.getName());
            return "home/system-landing";
        }
        
        log.info("Using company-specific landing template: {}", companyTemplateName);
        return companyTemplateName;
    }
    
    /**
     * Builds the company-specific landing template name from company name.
     * Removes spaces and formats as "home/landing{CompanyName}"
     * 
     * @param companyName The company name (e.g., "Pizza Max")
     * @return Template path (e.g., "home/landingPizzaMax")
     */
    private String buildCompanyTemplateName(String companyName) {
        // Remove spaces and special characters, keep letters and numbers
        String cleanName = companyName.replaceAll("[^a-zA-Z0-9]", "");
        return "home/landing" + cleanName;
    }
    
    /**
     * Checks if a Thymeleaf template exists in the classpath.
     * 
     * @param templateName Template name (e.g., "home/landingPizzaMax")
     * @return true if template exists, false otherwise
     */
    private boolean templateExists(String templateName) {
        String templatePath = "classpath:/templates/" + templateName + ".html";
        try {
            return resourceLoader.getResource(templatePath).exists();
        } catch (Exception e) {
            log.debug("Error checking template existence: {}", templatePath, e);
            return false;
        }
    }

    /**
     * Display public menu view (no authentication required)
     * 
     * @param model Spring MVC model
     * @return menu view name
     */
    @GetMapping("/home/menu")
    public String viewMenu(Model model) {
        log.info("Accessing public menu view");
        
        // Check if license has landing page access (WEB or ECOMMERCE)
        if (!licenseService.hasLandingPageAccess()) {
            log.info("License doesn't have landing page access (BASIC package). Redirecting to /login");
            return "redirect:/login";
        }
        
        try {
            // Update item availability
            itemMenuService.updateAllItemsAvailability();
            
            // Get available items
            List<ItemMenu> availableItems = itemMenuService.findAvailableItems();
            
            // Group items by category
            Map<Long, List<ItemMenu>> itemsByCategory = availableItems.stream()
                    .collect(Collectors.groupingBy(item -> item.getCategory().getIdCategory()));
            
            // Get active categories - ONLY those with active items
            List<Category> categories = categoryService.getAllActiveCategories().stream()
                    .filter(category -> itemsByCategory.containsKey(category.getIdCategory()))
                    .collect(Collectors.toList());
            
            // Get system configuration
            SystemConfiguration config = systemConfigurationService.getConfiguration();
            
            model.addAttribute("config", config);
            model.addAttribute("categories", categories);
            model.addAttribute("itemsByCategory", itemsByCategory);
            
            return "home/menu/view";
        } catch (Exception e) {
            log.error("Error loading public menu: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Error al cargar el menú");
            return "redirect:/home";
        }
    }
}
