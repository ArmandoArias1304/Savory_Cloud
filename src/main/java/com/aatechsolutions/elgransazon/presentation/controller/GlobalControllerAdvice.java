package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.entity.SystemUpdate;
import com.aatechsolutions.elgransazon.domain.entity.SystemUpdateRole;
import com.aatechsolutions.elgransazon.application.service.GlobalSystemConfigService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.application.service.SystemUpdateService;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.infrastructure.security.CustomEmployeeDetails;
import com.aatechsolutions.elgransazon.infrastructure.web.ReturnToLastListInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Global Controller Advice para añadir atributos comunes a todas las vistas.
 * 
 * Provides two configuration objects to all templates:
 * 1. globalSystemConfig - GLOBAL branding (system name, slogan, logo) - same for all companies
 * 2. systemConfig - Per-company configuration (restaurant name, address, phone, etc.)
 * 
 * MULTI-TENANT: 
 * - globalSystemConfig is shared across all tenants (SavoryCloud branding)
 * - systemConfig is specific to each company/tenant
 */
@ControllerAdvice
@Slf4j
public class GlobalControllerAdvice {

    @Autowired
    private SystemConfigurationService systemConfigurationService;

    @Autowired
    private GlobalSystemConfigService globalSystemConfigService;

    @Autowired
    private SystemUpdateService systemUpdateService;

    @Autowired
    private EmployeeRepository employeeRepository;

    /**
     * Trim leading/trailing whitespace from all String fields across every controller.
     * Empty strings (after trim) are converted to null.
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    /**
     * Añade la configuración GLOBAL del sistema a todas las vistas.
     * Esta configuración es la misma para todas las empresas (branding del sistema).
     * 
     * Uso en templates: ${globalSystemConfig.systemName}, ${globalSystemConfig.systemLogoUrl}
     * 
     * @return GlobalSystemConfig (never null - defaults provided)
     */
    @ModelAttribute("globalSystemConfig")
    public GlobalSystemConfig addGlobalSystemConfiguration() {
        GlobalSystemConfig config = globalSystemConfigService.getConfiguration();
        if (config != null) {
            return config;
        }
        
        // Return a transient default if not initialized (should not happen normally)
        return GlobalSystemConfig.builder()
            .id(1L)
            .systemName("SavoryCloud")
            .systemSlogan("Sistema de Gestión Restaurantera")
            .build();
    }

    /**
     * Latest published system update. Every version button reads it; it is only
     * added for authenticated users because the modal never shows on public pages.
     */
    @ModelAttribute("latestSystemUpdate")
    public SystemUpdate addLatestSystemUpdate(Authentication authentication) {
        if (!isRealAuthenticatedUser(authentication)) {
            return null;
        }
        try {
            return systemUpdateService.findLatestPublished().orElse(null);
        } catch (Exception e) {
            log.warn("Could not load latest system update: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Update that must pop up automatically for the current employee. It is null
     * when the employee already saw it, when the latest update targets other roles
     * or when there is no published update.
     */
    @ModelAttribute("pendingSystemUpdate")
    public SystemUpdate addPendingSystemUpdate(Authentication authentication) {
        if (!isRealAuthenticatedUser(authentication)) {
            return null;
        }
        Set<SystemUpdateRole> roles = authentication.getAuthorities().stream()
                .map(authority -> SystemUpdateRole.fromAuthority(authority.getAuthority()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (roles.isEmpty()) {
            return null;
        }

        Long employeeId = currentEmployeeId(authentication);
        if (employeeId == null) {
            return null;
        }
        try {
            return systemUpdateService.findPendingFor(employeeId, roles).orElse(null);
        } catch (Exception e) {
            log.warn("Could not resolve pending system update: {}", e.getMessage());
            return null;
        }
    }

    private boolean isRealAuthenticatedUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private Long currentEmployeeId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof CustomEmployeeDetails details) {
            return details.getEmployeeId();
        }
        Company company = CompanyContext.getCurrentCompany();
        Optional<Employee> employee = company != null
                ? employeeRepository.findByUsernameAndCompany(authentication.getName(), company)
                : employeeRepository.findByUsername(authentication.getName());
        return employee.map(Employee::getIdEmpleado).orElse(null);
    }

    /**
     * Añade la configuración PER-COMPANY a todas las vistas.
     * Esta configuración es específica para cada empresa/tenant.
     * 
     * Uso en templates: ${systemConfig.restaurantName}, ${systemConfig.phone}, etc.
     * 
     * MULTI-TENANT: Returns the company's config or null when no company context.
     * When null, templates should use globalSystemConfig for branding or show nothing.
     */
    @ModelAttribute("systemConfig")
    public SystemConfiguration addSystemConfiguration() {
        // Returns null when no company context - templates should handle null gracefully
        return systemConfigurationService.getConfiguration();
    }

    /**
     * Pantalla a la que debe volver un botón "Volver": la lista desde la que el usuario abrió este
     * formulario, con su búsqueda y su página. Es {@code null} cuando el navegador no manda un
     * origen utilizable, y entonces la plantilla usa su destino habitual.
     */
    @ModelAttribute("volverA")
    public String addReturnTo(HttpServletRequest request) {
        return ReturnToLastListInterceptor.localReturnUrl(request);
    }
}
