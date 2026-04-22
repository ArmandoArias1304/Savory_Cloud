package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.CompanyService;
import com.aatechsolutions.elgransazon.application.service.FacturamaService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.FacturamaConfig;
import com.aatechsolutions.elgransazon.domain.repository.CompanyRepository;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.presentation.dto.CompanyCreateDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

/**
 * Controller for company (multi-tenant) management
 * Access restricted to PROGRAMMER role only
 */
@Controller
@RequestMapping("/programmer/companies")
@PreAuthorize("hasRole('PROGRAMMER')")
@RequiredArgsConstructor
@Slf4j
public class CompanyController {

    private final CompanyService companyService;
    private final CompanyRepository companyRepository;
    private final FacturamaService facturamaService;
    private final OrderRepository orderRepository;

    /**
     * List all companies with pagination
     */
    @GetMapping
    public String listCompanies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            Model model,
            Authentication authentication) {
        
        log.info("Programmer {} accessing companies list", authentication.getName());
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        Page<Company> companies;
        if (search != null && !search.trim().isEmpty()) {
            companies = companyRepository.findByNameContainingIgnoreCaseOrSlugContainingIgnoreCase(
                    search.trim(), search.trim(), pageable);
            model.addAttribute("search", search);
        } else {
            companies = companyRepository.findAll(pageable);
        }
        
        model.addAttribute("companies", companies);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", companies.getTotalPages());
        model.addAttribute("totalElements", companies.getTotalElements());
        
        // Statistics
        model.addAttribute("activeCount", companyRepository.countByActiveTrue());
        model.addAttribute("inactiveCount", companyRepository.countByActiveFalse());
        
        return "programmer/companies/list";
    }

    /**
     * Show company creation form
     */
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("company", new CompanyCreateDTO());
        return "programmer/companies/form";
    }

    /**
     * Create new company
     */
    @PostMapping
    public String createCompany(
            @Valid @ModelAttribute("company") CompanyCreateDTO dto,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model,
            Authentication authentication) {
        
        log.info("Programmer {} creating new company: {}", authentication.getName(), dto.getSlug());
        
        // Validate slug uniqueness
        if (companyRepository.existsBySlug(dto.getSlug())) {
            bindingResult.rejectValue("slug", "duplicate", "Este slug ya está en uso");
        }
        
        // Validate custom domain uniqueness if provided
        if (dto.getCustomDomain() != null && !dto.getCustomDomain().trim().isEmpty()) {
            if (companyRepository.existsByCustomDomain(dto.getCustomDomain())) {
                bindingResult.rejectValue("customDomain", "duplicate", "Este dominio ya está en uso");
            }
        }
        
        if (bindingResult.hasErrors()) {
            return "programmer/companies/form";
        }
        
        try {
            Company company = companyService.create(
                    dto.getSlug(),
                    dto.getName(),
                    dto.getCustomDomain(),
                    dto.getSenderEmail(),
                    dto.getSenderName(),
                    dto.getContactEmail(),
                    dto.getContactPhone(),
                    dto.getAddress(),
                    dto.getRfc(),
                    dto.getTimezone(),
                    dto.getAdminUsername(),
                    dto.getAdminFirstName(),
                    dto.getAdminLastName(),
                    dto.getAdminPassword(),
                    dto.isFreeTrial(),
                    dto.getPackageType(),
                    dto.getBillingCycle(),
                    dto.getLicenseMonths(),
                    dto.getLicenseAmount(),
                    dto.getTaxRate(),
                    authentication.getName()
            );
            
            redirectAttributes.addFlashAttribute("success", 
                    "Empresa '" + company.getName() + "' creada exitosamente. " +
                    "URL: " + dto.getSlug() + ".tudominio.com");
            
            log.info("Company {} created successfully by {}", company.getSlug(), authentication.getName());
            
        } catch (jakarta.validation.ConstraintViolationException e) {
            log.error("Validation error creating company: {}", e.getMessage());
            String messages = e.getConstraintViolations().stream()
                    .map(cv -> cv.getMessage())
                    .collect(java.util.stream.Collectors.joining("; "));
            model.addAttribute("error", messages);
            return "programmer/companies/form";
        } catch (IllegalArgumentException e) {
            log.error("Error creating company: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            return "programmer/companies/form";
        } catch (Exception e) {
            log.error("Error creating company: {}", e.getMessage(), e);
            // Try to extract constraint violation messages from nested cause
            Throwable cause = e.getCause();
            while (cause != null) {
                if (cause instanceof jakarta.validation.ConstraintViolationException cve) {
                    String messages = cve.getConstraintViolations().stream()
                            .map(cv -> cv.getMessage())
                            .collect(java.util.stream.Collectors.joining("; "));
                    model.addAttribute("error", messages);
                    return "programmer/companies/form";
                }
                cause = cause.getCause();
            }
            model.addAttribute("error", "Error al crear la empresa: " + e.getMessage());
            return "programmer/companies/form";
        }
        
        return "redirect:/programmer/companies";
    }

    /**
     * View company details
     */
    @GetMapping("/{id}")
    public String viewCompany(@PathVariable Long id, Model model) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
        
        model.addAttribute("company", company);
        
        // Facturama config for this company
        FacturamaConfig facturamaConfig = facturamaService.getConfigForCompany(company).orElse(null);
        model.addAttribute("facturamaConfig", facturamaConfig);
        model.addAttribute("facturamaLiveMode", facturamaService.isLiveMode());
        model.addAttribute("totalCfdis", orderRepository.countByCompanyAndFacturamaCfdiCreatedAtIsNotNull(company));
        
        return "programmer/companies/view";
    }

    /**
     * Initialize Facturama configuration for a company.
     */
    @PostMapping("/{id}/facturama/init")
    public String initFacturama(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {

        try {
            Company company = companyRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));

            facturamaService.initConfig(company);

            redirectAttributes.addFlashAttribute("success",
                    "Configuración de Facturama inicializada. Ahora el admin puede subir los certificados CSD.");

            log.info("Facturama config initialized for company {} by {}", company.getSlug(), authentication.getName());

        } catch (Exception e) {
            log.error("Error initializing Facturama config: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error",
                    "Error al inicializar configuración: " + e.getMessage());
        }

        return "redirect:/programmer/companies/" + id;
    }

    /**
     * Toggle Facturama integration enabled/disabled for a company.
     * Validates that CSD and legal data are configured before enabling.
     */
    @PostMapping("/{id}/facturama/toggle")
    public String toggleFacturama(
            @PathVariable Long id,
            @RequestParam boolean enabled,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {

        try {
            Company company = companyRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));

            FacturamaConfig config = facturamaService.getConfigForCompany(company)
                    .orElseThrow(() -> new IllegalStateException("No se ha inicializado la configuración de Facturama"));

            if (enabled) {
                if (!config.getCsdUploaded() || !config.getLegalDataConfigured()) {
                    redirectAttributes.addFlashAttribute("error",
                            "No se puede activar: el admin debe completar los certificados CSD y datos fiscales primero.");
                    return "redirect:/programmer/companies/" + id;
                }
                facturamaService.enableIntegration(config);
                redirectAttributes.addFlashAttribute("success",
                        "Facturación electrónica activada. Los tickets incluirán el enlace de autofactura.");
            } else {
                facturamaService.disableIntegration(config);
                redirectAttributes.addFlashAttribute("success",
                        "Facturación electrónica desactivada.");
            }

            log.info("Facturama toggled to {} for company {} by {}", enabled, company.getSlug(), authentication.getName());

        } catch (Exception e) {
            log.error("Error toggling Facturama for company {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/programmer/companies/" + id;
    }

    /**
     * Show edit form
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
        
        model.addAttribute("company", company);
        model.addAttribute("isEdit", true);
        return "programmer/companies/edit";
    }

    /**
     * Update company
     */
    @PostMapping("/{id}")
    public String updateCompany(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam String slug,
            @RequestParam(required = false) String customDomain,
            @RequestParam(required = false) String senderEmail,
            @RequestParam(required = false) String senderName,
            @RequestParam(required = false) String contactEmail,
            @RequestParam(required = false) String contactPhone,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String rfc,
            @RequestParam(defaultValue = "America/Mexico_City") String timezone,
            @RequestParam(required = false) BigDecimal taxRate,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {
        
        log.info("Programmer {} updating company {}", authentication.getName(), id);
        
        try {
            Company company = companyRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
            
            company.setName(name);
            
            // Validate slug uniqueness if changed
            if (!slug.equals(company.getSlug())) {
                if (companyRepository.existsBySlug(slug)) {
                    redirectAttributes.addFlashAttribute("error", "El slug ya está en uso");
                    return "redirect:/programmer/companies/" + id + "/edit";
                }
                company.setSlug(slug);
            }
            
            // Validate custom domain uniqueness if changed
            if (customDomain != null && !customDomain.trim().isEmpty()) {
                if (!customDomain.equals(company.getCustomDomain()) && 
                    companyRepository.existsByCustomDomain(customDomain)) {
                    redirectAttributes.addFlashAttribute("error", "El dominio personalizado ya está en uso");
                    return "redirect:/programmer/companies/" + id + "/edit";
                }
                company.setCustomDomain(customDomain.trim());
            } else {
                company.setCustomDomain(null);
            }
            
            // Update all company fields
            company.setSenderEmail(senderEmail);
            company.setSenderName(senderName);
            company.setContactEmail(contactEmail);
            company.setContactPhone(contactPhone);
            company.setAddress(address);
            company.setRfc(rfc);
            if (timezone != null && !timezone.isBlank()) {
                company.setTimezone(timezone);
            }
            
            // Update taxRate on the company's SystemConfiguration
            if (taxRate != null && company.getSystemConfiguration() != null) {
                company.getSystemConfiguration().setTaxRate(taxRate);
            }
            
            companyRepository.save(company);
            
            redirectAttributes.addFlashAttribute("success", "Empresa actualizada exitosamente");
            
        } catch (Exception e) {
            log.error("Error updating company: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error al actualizar: " + e.getMessage());
        }
        
        return "redirect:/programmer/companies/" + id;
    }

    /**
     * Toggle company active status
     */
    @PostMapping("/{id}/toggle-active")
    public String toggleActive(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {
        
        try {
            Company company = companyRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
            
            company.setActive(!company.getActive());
            companyRepository.save(company);
            
            String status = company.getActive() ? "activada" : "desactivada";
            redirectAttributes.addFlashAttribute("success", "Empresa " + status + " exitosamente");
            
            log.info("Company {} {} by {}", company.getSlug(), status, authentication.getName());
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        
        return "redirect:/programmer/companies";
    }

    /**
     * Delete company (soft delete by deactivating)
     */
    @PostMapping("/{id}/delete")
    public String deleteCompany(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {
        
        try {
            Company company = companyRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
            
            // Soft delete - just deactivate
            company.setActive(false);
            companyRepository.save(company);
            
            redirectAttributes.addFlashAttribute("success", 
                    "Empresa '" + company.getName() + "' desactivada exitosamente");
            
            log.info("Company {} deleted (deactivated) by {}", company.getSlug(), authentication.getName());
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        
        return "redirect:/programmer/companies";
    }
}
