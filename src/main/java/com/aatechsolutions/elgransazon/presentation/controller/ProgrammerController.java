package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.BackupService;
import com.aatechsolutions.elgransazon.application.service.CompanyService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.GlobalSystemConfigService;
import com.aatechsolutions.elgransazon.application.service.ImageStorageService;
import com.aatechsolutions.elgransazon.application.service.LicenseService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.LicenseEvent;
import com.aatechsolutions.elgransazon.domain.entity.SystemError;
import com.aatechsolutions.elgransazon.domain.entity.SystemLicense;
import com.aatechsolutions.elgransazon.domain.repository.CustomerRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuRepository;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.domain.repository.SystemErrorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Controller for programmer/vendor dashboard
 * Access restricted to PROGRAMMER role only
 */
@Controller
@RequestMapping("/programmer")
@PreAuthorize("hasRole('PROGRAMMER')")
@RequiredArgsConstructor
@Slf4j
public class ProgrammerController {

    private final LicenseService licenseService;
    private final BackupService backupService;
    private final SystemErrorRepository errorRepository;
    private final EmployeeService employeeService;
    private final CompanyService companyService;
    private final ItemMenuRepository itemMenuRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final GlobalSystemConfigService globalSystemConfigService;
    private final ImageStorageService imageStorageService;

    /**
     * Programmer dashboard
     */
    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) Long companyId,
                           Model model, 
                           Authentication authentication) {
        log.info("Programmer {} accessed dashboard", authentication.getName());

        // Load ALL companies with their licenses
        List<Company> allCompanies = companyService.findAll();
        
        // Filter companies that have licenses
        List<Company> companiesWithLicense = allCompanies.stream()
                .filter(c -> c.getSystemLicense() != null)
                .toList();
        
        if (companiesWithLicense.isEmpty()) {
            model.addAttribute("noLicense", true);
            // Pass all companies so programmer can select which one to create license for
            List<Company> companiesWithoutLicense = allCompanies.stream()
                .filter(c -> c.getSystemLicense() == null)
                .toList();
            model.addAttribute("companies", companiesWithoutLicense);
            model.addAttribute("hasCompanies", !companiesWithoutLicense.isEmpty());
            return "programmer/dashboard";
        }

        // Pass all companies with licenses to the view
        model.addAttribute("companiesWithLicense", companiesWithLicense);
        
        // Select company: use companyId parameter if provided, otherwise default to first
        Company selectedCompany;
        if (companyId != null) {
            selectedCompany = companiesWithLicense.stream()
                    .filter(c -> c.getIdCompany().equals(companyId))
                    .findFirst()
                    .orElse(companiesWithLicense.get(0)); // Fallback to first if not found
            log.info("Selected company by ID: {} ({})", selectedCompany.getIdCompany(), selectedCompany.getName());
        } else {
            selectedCompany = companiesWithLicense.get(0);
            log.info("Selected default company: {} ({})", selectedCompany.getIdCompany(), selectedCompany.getName());
        }
        
        SystemLicense license = selectedCompany.getSystemLicense();

        // License information for the selected company
        model.addAttribute("license", license);
        model.addAttribute("selectedCompany", selectedCompany);
        model.addAttribute("daysLeft", license.daysUntilExpiration());
        
        // Backup configuration for password recovery
        try {
            backupService.getConfiguration().ifPresent(config -> 
                model.addAttribute("backupConfig", config));
        } catch (Exception e) {
            log.error("Error loading backup configuration: {}", e.getMessage());
        }
        model.addAttribute("daysActive", license.daysActive());
        model.addAttribute("isExpired", license.isExpired());
        model.addAttribute("needsWarning", license.daysUntilExpiration() <= 5);

        // System statistics (PROGRAMMER excluded from employee count via countAll())
        // NOTE: These statistics are for the selected company only
        long totalEmployees = employeeService.countAll();  // Already excludes PROGRAMMER
        long totalMenuItems = itemMenuRepository.count();
        long totalOrders = orderRepository.count();
        long totalCustomers = customerRepository.count();

        model.addAttribute("totalEmployees", totalEmployees);
        model.addAttribute("totalMenuItems", totalMenuItems);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("totalCustomers", totalCustomers);

        // Recent errors (last 7 days)
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<SystemError> recentErrors = errorRepository.findByOccurredAtBetweenOrderByOccurredAtDesc(
            sevenDaysAgo, LocalDateTime.now()
        );
        
        long unresolvedCount = recentErrors.stream().filter(e -> !e.getResolved()).count();
        long criticalCount = recentErrors.stream()
            .filter(e -> e.getSeverity() == SystemError.Severity.CRITICAL && !e.getResolved())
            .count();

        model.addAttribute("recentErrors", recentErrors);
        model.addAttribute("unresolvedErrorCount", unresolvedCount);
        model.addAttribute("criticalErrorCount", criticalCount);

        // License events for the selected license
        List<LicenseEvent> events = licenseService.getLicenseEventsById(license.getId());
        // Limit to 10 most recent
        events = events.subList(0, Math.min(10, events.size()));
        model.addAttribute("licenseEvents", events);

        // Financial summary for the selected license
        Double totalRevenue = licenseService.getTotalRevenueById(license.getId());
        List<LicenseEvent> renewalsWithAmount = licenseService.getRenewalEventsWithAmountById(license.getId());
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("renewalsWithAmount", renewalsWithAmount);

        return "programmer/dashboard";
    }

    /**
     * Renew license
     */
    @PostMapping("/renew")
    public String renewLicense(@RequestParam Long licenseId,
                              @RequestParam int months,
                              @RequestParam(required = false) Double amount,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            
            // Renew the specific license by ID
            licenseService.renewLicenseById(licenseId, months, amount, username);
            
            String action = months > 0 ? "renovada" : "ajustada";
            String monthsText = Math.abs(months) + " mes(es)";
            redirectAttributes.addFlashAttribute("successMessage", 
                "Licencia " + action + " exitosamente por " + monthsText);
            
            log.info("License {} renewed/adjusted for {} months by {}", licenseId, months, username);
        } catch (Exception e) {
            log.error("Error renewing license", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error al renovar la licencia: " + e.getMessage());
        }

        return "redirect:/programmer/dashboard";
    }

    /**
     * Suspend license
     */
    @PostMapping("/suspend")
    public String suspendLicense(@RequestParam Long licenseId,
                                @RequestParam(required = false) String reason,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            licenseService.suspendLicenseById(licenseId, username, reason);
            
            redirectAttributes.addFlashAttribute("warningMessage", 
                "Licencia suspendida exitosamente");
            
            log.info("License {} suspended by {}", licenseId, username);
        } catch (Exception e) {
            log.error("Error suspending license", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error al suspender la licencia: " + e.getMessage());
        }

        return "redirect:/programmer/dashboard";
    }

    /**
     * Reactivate license
     */
    @PostMapping("/reactivate")
    public String reactivateLicense(Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            licenseService.reactivateLicense(username);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Licencia reactivada exitosamente");
            
            log.info("License reactivated by {}", username);
        } catch (Exception e) {
            log.error("Error reactivating license", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error al reactivar la licencia: " + e.getMessage());
        }

        return "redirect:/programmer/dashboard";
    }

    /**
     * Change package type
     */
    @PostMapping("/change-package")
    public String changePackage(@RequestParam Long licenseId,
                               @RequestParam String packageType,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            SystemLicense.PackageType newPackage = SystemLicense.PackageType.valueOf(packageType);
            licenseService.changePackageTypeById(licenseId, newPackage, username);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Paquete cambiado exitosamente a " + newPackage.getDisplayName());
            
            log.info("Package of license {} changed to {} by {}", licenseId, newPackage, username);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid package change attempt: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error changing package", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error al cambiar el paquete: " + e.getMessage());
        }

        return "redirect:/programmer/dashboard";
    }

    /**
     * Update license notes
     */
    @PostMapping("/update-notes")
    public String updateNotes(@RequestParam String notes,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            licenseService.updateNotes(notes, username);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Notas actualizadas exitosamente");
            
            log.info("License notes updated by {}", username);
        } catch (Exception e) {
            log.error("Error updating notes", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error al actualizar las notas: " + e.getMessage());
        }

        return "redirect:/programmer/dashboard";
    }

    /**
     * Update license information
     */
    @PostMapping("/update-info")
    public String updateLicenseInfo(@RequestParam Long licenseId,
                                   @RequestParam String ownerName,
                                   @RequestParam String ownerEmail,
                                   @RequestParam String ownerPhone,
                                   @RequestParam(required = false) String ownerRfc,
                                   @RequestParam String restaurantName,
                                   @RequestParam(required = false) Integer maxUsers,
                                   @RequestParam int maxBranches,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            licenseService.updateLicenseInfoById(
                licenseId,
                ownerName,
                ownerEmail,
                ownerPhone,
                ownerRfc,
                restaurantName,
                maxUsers,
                maxBranches,
                username
            );
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Información de licencia actualizada exitosamente");
            
            log.info("License {} info updated by {}", licenseId, username);
        } catch (Exception e) {
            log.error("Error updating license info", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error al actualizar la información: " + e.getMessage());
        }

        return "redirect:/programmer/dashboard";
    }

    /**
     * View error details
     */
    @GetMapping("/errors/{id}")
    public String viewError(@PathVariable Long id, Model model) {
        SystemError error = errorRepository.findById(id).orElse(null);
        
        if (error == null) {
            return "redirect:/programmer/dashboard";
        }

        model.addAttribute("error", error);
        return "programmer/error-detail";
    }

    /**
     * Mark error as resolved
     */
    @PostMapping("/errors/{id}/resolve")
    public String resolveError(@PathVariable Long id,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            SystemError error = errorRepository.findById(id).orElse(null);
            
            if (error != null) {
                error.markAsResolved(authentication.getName());
                errorRepository.save(error);
                
                redirectAttributes.addFlashAttribute("successMessage", 
                    "Error marcado como resuelto");
            }
        } catch (Exception e) {
            log.error("Error resolving error", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error al marcar como resuelto: " + e.getMessage());
        }

        return "redirect:/programmer/dashboard";
    }

    /**
     * Create initial license for a company
     * MULTI-TENANT: Requires companyId to know which company gets the license
     */
    @PostMapping("/create-license")
    public String createLicense(@RequestParam Long companyId,
                               @RequestParam String licenseKey,
                               @RequestParam String packageType,
                               @RequestParam String billingCycle,
                               @RequestParam int months,
                               @RequestParam String ownerName,
                               @RequestParam String ownerEmail,
                               @RequestParam String ownerPhone,
                               @RequestParam String ownerRfc,
                               @RequestParam String restaurantName,
                               @RequestParam(required = false) Integer maxUsers,
                               @RequestParam(required = false, defaultValue = "1") int maxBranches,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            // Find the company
            Company company = companyService.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada con ID: " + companyId));
            
            // Check if company already has a license
            if (company.getSystemLicense() != null) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "La empresa ya tiene una licencia. Use renovar para extender la vigencia.");
                return "redirect:/programmer/dashboard";
            }

            String username = authentication.getName();
            licenseService.createInitialLicense(
                company,
                licenseKey,
                packageType,
                billingCycle,
                months,
                ownerName,
                ownerEmail,
                ownerPhone,
                ownerRfc,
                restaurantName,
                maxUsers != null ? maxUsers : 10,
                maxBranches,
                username
            );
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Licencia creada exitosamente para " + months + " mes(es)");
            
            log.info("Initial license created by {}", username);
        } catch (Exception e) {
            log.error("Error creating initial license", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error al crear la licencia: " + e.getMessage());
        }

        return "redirect:/programmer/dashboard";
    }

    /**
     * Update system branding (name and slogan)
     * Uses GlobalSystemConfigService for global system branding
     */
    @PostMapping("/update-system-branding")
    public String updateSystemBranding(@RequestParam String systemName,
                                       @RequestParam String systemSlogan,
                                       Authentication authentication,
                                       RedirectAttributes redirectAttributes) {
        try {
            globalSystemConfigService.updateBranding(systemName, systemSlogan);

            redirectAttributes.addFlashAttribute("successMessage",
                "Identidad del sistema actualizada exitosamente");

            log.info("System branding updated by {}", authentication.getName());
        } catch (Exception e) {
            log.error("Error updating system branding: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                "Error al actualizar la identidad del sistema: " + e.getMessage());
        }

        return "redirect:/programmer/dashboard";
    }

    /**
     * Upload system logo image
     * Uses GlobalSystemConfigService for global system logo
     * Uploads to Home/SavoryCloud/ folder in Cloudinary
     */
    @PostMapping("/upload-system-logo")
    public String uploadSystemLogo(@RequestParam("systemLogoFile") MultipartFile file,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        log.info("Processing system logo upload");

        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No se seleccionó ningún archivo");
            return "redirect:/programmer/dashboard";
        }

        try {
            GlobalSystemConfig config = globalSystemConfigService.getConfiguration();
            
            // Delete old logo if exists
            if (config.getSystemLogoUrl() != null && !config.getSystemLogoUrl().isEmpty()) {
                imageStorageService.deleteImage(config.getSystemLogoUrl());
            }

            // Upload to global system folder: Home/SavoryCloud/
            String imageUrl = imageStorageService.saveImage(file, "system-logo", "system-logo");
            globalSystemConfigService.updateLogoUrl(imageUrl);

            log.info("System logo uploaded successfully by {}", authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Logo del sistema actualizado exitosamente");
        } catch (Exception e) {
            log.error("Error uploading system logo: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al subir el logo: " + e.getMessage());
        }

        return "redirect:/programmer/dashboard";
    }
}
