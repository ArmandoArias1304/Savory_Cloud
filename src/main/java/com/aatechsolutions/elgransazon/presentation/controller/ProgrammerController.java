package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.BackupService;
import com.aatechsolutions.elgransazon.application.service.CompanyService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.GlobalSystemConfigService;
import com.aatechsolutions.elgransazon.application.service.ImageStorageService;
import com.aatechsolutions.elgransazon.application.service.LandingImageService;
import com.aatechsolutions.elgransazon.application.service.LicenseService;
import com.aatechsolutions.elgransazon.domain.repository.CustomerRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuRepository;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.LandingImage;
import com.aatechsolutions.elgransazon.domain.entity.LicenseEvent;
import com.aatechsolutions.elgransazon.domain.entity.SystemLicense;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final CompanyService companyService;
    private final GlobalSystemConfigService globalSystemConfigService;
    private final ImageStorageService imageStorageService;
    private final LandingImageService landingImageService;
    private final EmployeeService employeeService;
    private final ItemMenuRepository itemMenuRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;

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

        // System statistics for the selected company
        long totalEmployees = employeeService.countEnabledByCompany(selectedCompany);
        long totalMenuItems = itemMenuRepository.countByCompany(selectedCompany);
        long totalOrders = orderRepository.countByCompany(selectedCompany);
        long totalCustomers = customerRepository.countByCompany(selectedCompany);
        model.addAttribute("totalEmployees", totalEmployees);
        model.addAttribute("totalMenuItems", totalMenuItems);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("totalCustomers", totalCustomers);

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

        Long companyId = getCompanyIdFromLicense(licenseId);
        return "redirect:/programmer/dashboard" + (companyId != null ? "?companyId=" + companyId : "");
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

        Long companyId = getCompanyIdFromLicense(licenseId);
        return "redirect:/programmer/dashboard" + (companyId != null ? "?companyId=" + companyId : "");
    }

    /**
     * Reactivate license
     */
    @PostMapping("/reactivate")
    public String reactivateLicense(@RequestParam Long licenseId,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            licenseService.reactivateLicenseById(licenseId, username);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Licencia reactivada exitosamente");
            
            log.info("License {} reactivated by {}", licenseId, username);
        } catch (Exception e) {
            log.error("Error reactivating license", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error al reactivar la licencia: " + e.getMessage());
        }

        Long companyId = getCompanyIdFromLicense(licenseId);
        return "redirect:/programmer/dashboard" + (companyId != null ? "?companyId=" + companyId : "");
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

        Long companyId = getCompanyIdFromLicense(licenseId);
        return "redirect:/programmer/dashboard" + (companyId != null ? "?companyId=" + companyId : "");
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

        Long companyId = getCompanyIdFromLicense(licenseId);
        return "redirect:/programmer/dashboard" + (companyId != null ? "?companyId=" + companyId : "");
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
     * Upload (or set) the global system logo.
     *
     * Accepts two modes (Direct Creator Upload is preferred and bypasses this server):
     *  1. Direct Upload mode (preferred): the JS uploaded the file straight to Cloudflare
     *     and only sends the resulting {@code imageUrl} string to this endpoint.
     *  2. Server-side mode (fallback): a legacy {@code systemLogoFile} multipart is sent.
     */
    @PostMapping("/upload-system-logo")
    public String uploadSystemLogo(@RequestParam(value = "systemLogoFile", required = false) MultipartFile file,
                                    @RequestParam(value = "imageUrl", required = false) String imageUrl,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        log.info("Processing system logo upload (directUpload={})", imageUrl != null && !imageUrl.isBlank());

        boolean hasFile = file != null && !file.isEmpty();
        boolean hasUrl = imageUrl != null && !imageUrl.isBlank();
        if (!hasFile && !hasUrl) {
            redirectAttributes.addFlashAttribute("errorMessage", "No se seleccionó ningún archivo");
            return "redirect:/programmer/dashboard";
        }

        try {
            GlobalSystemConfig config = globalSystemConfigService.getConfiguration();

            // Delete old logo if exists (fire-and-forget)
            if (config.getSystemLogoUrl() != null && !config.getSystemLogoUrl().isEmpty()) {
                imageStorageService.deleteImage(config.getSystemLogoUrl());
            }

            String finalUrl;
            if (hasUrl) {
                finalUrl = imageUrl.trim();
            } else {
                finalUrl = imageStorageService.saveImage(file, "system-logo", "system-logo");
            }
            globalSystemConfigService.updateLogoUrl(finalUrl);

            log.info("System logo uploaded successfully by {}", authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Logo del sistema actualizado exitosamente");
        } catch (Exception e) {
            log.error("Error uploading system logo: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al subir el logo: " + e.getMessage());
        }

        return "redirect:/programmer/dashboard";
    }

    /**
     * Get all landing images for a company (AJAX)
     */
    @GetMapping("/api/landing-images")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getLandingImages(@RequestParam Long companyId) {
        Company company = companyService.findById(companyId).orElse(null);
        if (company == null) {
            return ResponseEntity.badRequest().build();
        }

        List<LandingImage> images = landingImageService.findByCompany(company);
        List<Map<String, Object>> result = images.stream().map(img -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", img.getId());
            map.put("section", img.getSection().name());
            map.put("position", img.getPosition());
            map.put("imageUrl", img.getImageUrl());
            return map;
        }).toList();

        return ResponseEntity.ok(result);
    }

    /**
     * Mint a Direct Upload token for a landing image. The programmer is choosing the
     * company explicitly, so we set the tenant context manually before delegating
     * to {@link ImageStorageService#prepareDirectUpload(String, String)}.
     */
    @PostMapping("/api/landing-images/upload-token")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> landingUploadToken(
            @RequestParam Long companyId,
            @RequestParam String section,
            @RequestParam int position) {
        Map<String, Object> body = new HashMap<>();
        try {
            Company company = companyService.findById(companyId).orElse(null);
            if (company == null) {
                body.put("success", false);
                body.put("message", "Empresa no encontrada");
                return ResponseEntity.badRequest().body(body);
            }
            LandingImage.Section sec = LandingImage.Section.valueOf(section);
            if (position < 1 || position > sec.getMaxPositions()) {
                body.put("success", false);
                body.put("message", "Posición inválida");
                return ResponseEntity.badRequest().body(body);
            }

            com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext.setCurrentCompany(company);
            try {
                var token = imageStorageService.prepareDirectUpload(
                        "landing", section.toLowerCase() + "-" + position);
                body.put("success", true);
                body.put("uploadUrl", token.uploadUrl());
                body.put("imageId", token.imageId());
                body.put("finalUrl", token.finalUrl());
                return ResponseEntity.ok(body);
            } finally {
                com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext.clear();
            }
        } catch (IllegalArgumentException e) {
            body.put("success", false);
            body.put("message", "Sección inválida: " + section);
            return ResponseEntity.badRequest().body(body);
        } catch (Exception e) {
            log.error("Error issuing landing upload token: {}", e.getMessage());
            body.put("success", false);
            body.put("message", "No se pudo generar el token: " + e.getMessage());
            return ResponseEntity.status(500).body(body);
        }
    }

    /**
     * Upload a landing image for a specific company/section/position (AJAX)
     */
    @PostMapping("/api/landing-images/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadLandingImage(
            @RequestParam Long companyId,
            @RequestParam String section,
            @RequestParam int position,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "imageUrl", required = false) String imageUrl,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();
        try {
            Company company = companyService.findById(companyId).orElse(null);
            if (company == null) {
                response.put("success", false);
                response.put("message", "Empresa no encontrada");
                return ResponseEntity.badRequest().body(response);
            }

            LandingImage.Section sec = LandingImage.Section.valueOf(section);
            if (position < 1 || position > sec.getMaxPositions()) {
                response.put("success", false);
                response.put("message", "Posición inválida para la sección " + sec.getDisplayName());
                return ResponseEntity.badRequest().body(response);
            }

            // Two upload paths supported:
            //   (a) Direct Upload — browser already pushed the file to Cloudflare; we receive only the URL.
            //   (b) Server-side fallback — multipart file flowing through this server (legacy).
            LandingImage saved;
            if (imageUrl != null && !imageUrl.isBlank()) {
                saved = landingImageService.saveImageUrl(company, sec, position, imageUrl.trim());
            } else if (file != null && !file.isEmpty()) {
                // Tenant context for the per-company subfolder lookup happens inside the service.
                com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext.setCurrentCompany(company);
                try {
                    saved = landingImageService.uploadImage(company, sec, position, file);
                } finally {
                    com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext.clear();
                }
            } else {
                response.put("success", false);
                response.put("message", "No se recibió ni archivo ni URL de imagen");
                return ResponseEntity.badRequest().body(response);
            }

            response.put("success", true);
            response.put("imageUrl", saved.getImageUrl());
            response.put("message", "Imagen subida exitosamente");

            log.info("Landing image saved by {} for company {} section {} position {}",
                    authentication.getName(), company.getSlug(), section, position);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", "Sección inválida: " + section);
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error uploading landing image: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error al subir la imagen: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Delete a landing image (AJAX)
     */
    @DeleteMapping("/api/landing-images")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteLandingImage(
            @RequestParam Long companyId,
            @RequestParam String section,
            @RequestParam int position,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();
        try {
            Company company = companyService.findById(companyId).orElse(null);
            if (company == null) {
                response.put("success", false);
                response.put("message", "Empresa no encontrada");
                return ResponseEntity.badRequest().body(response);
            }

            LandingImage.Section sec = LandingImage.Section.valueOf(section);
            landingImageService.deleteImage(company, sec, position);

            response.put("success", true);
            response.put("message", "Imagen eliminada exitosamente");

            log.info("Landing image deleted by {} for company {} section {} position {}",
                    authentication.getName(), company.getSlug(), section, position);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error deleting landing image: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error al eliminar la imagen: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * AJAX endpoint: count CFDIs by company and date range.
     * Dates are received as local dates (America/Mexico_City) and converted to UTC for the DB query.
     */
    @GetMapping("/api/cfdi-count")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> countCfdis(
            @RequestParam Long companyId,
            @RequestParam String from,
            @RequestParam String to) {

        try {
            Company company = companyService.findAll().stream()
                    .filter(c -> c.getIdCompany().equals(companyId))
                    .findFirst()
                    .orElse(null);
            if (company == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Empresa no encontrada"));
            }

            ZoneId zone = ZoneId.of("America/Mexico_City");
            LocalDate fromDate = LocalDate.parse(from);
            LocalDate toDate = LocalDate.parse(to);

            if (fromDate.isAfter(toDate)) {
                return ResponseEntity.badRequest().body(Map.of("error", "La fecha 'desde' debe ser menor o igual a 'hasta'"));
            }

            // Convert local dates to UTC range
            LocalDateTime startUtc = fromDate.atStartOfDay(zone).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
            LocalDateTime endUtc = toDate.plusDays(1).atStartOfDay(zone).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();

            long count = orderRepository.countCfdisByCompanyAndDateRange(company, startUtc, endUtc);

            Map<String, Object> result = new HashMap<>();
            result.put("count", count);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error counting CFDIs: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Helper to get company ID from a license ID for redirect purposes
     */
    private Long getCompanyIdFromLicense(Long licenseId) {
        if (licenseId == null) return null;
        return licenseService.getLicenseById(licenseId)
            .map(license -> license.getCompany().getIdCompany())
            .orElse(null);
    }
}
