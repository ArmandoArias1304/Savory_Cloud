package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.LicenseService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for authentication-related views
 * Handles login and logout pages
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final SystemConfigurationService systemConfigurationService;
    private final LicenseService licenseService;

    /**
     * Display login page
     * 
     * @param error indicates if there was a login error
     * @param logout indicates if user just logged out
     * @param model Spring MVC model
     * @return login view name
     */
    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        @RequestParam(value = "restricted", required = false) String restricted,
                        Model model) {
        
        if (error != null) {
            if ("clientAttempt".equals(error)) {
                log.warn("Client attempted to login via employee login page");
                model.addAttribute("errorType", "clientAttempt");
                model.addAttribute("error", "Este acceso es solo para empleados");
            } else if ("userLimitExceeded".equals(error)) {
                log.warn("Employee login blocked due to user limit exceeded");
                model.addAttribute("error", "El límite de usuarios activos ha sido reducido. Contacta al administrador para que ajuste los empleados activos.");
            } else {
                log.warn("Login attempt failed");
                model.addAttribute("error", "Nombre de usuario o contraseña incorrectos");
            }
        }
        
        if (logout != null) {
            log.info("User logged out");
            model.addAttribute("message", "Has cerrado sesión correctamente");
        }
        
        if (restricted != null) {
            log.info("Access to customer module restricted by license");
            model.addAttribute("warning", "El módulo de clientes no está disponible con tu licencia actual. Contacta al administrador para actualizar.");
        }
        
        // Get system configuration for logo and restaurant name
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        model.addAttribute("config", config);
        
        // MULTI-TENANT: Check if there's company context
        boolean hasCompanyContext = CompanyContext.getCurrentCompany() != null;
        model.addAttribute("hasCompanyContext", hasCompanyContext);
        
        // Check if license has customer module access (ECOMMERCE only)
        // Only relevant when there's company context
        boolean hasCustomerModule = hasCompanyContext && licenseService.hasCustomerModuleAccess();
        model.addAttribute("hasCustomerModule", hasCustomerModule);
        
        return "auth/login";
    }

    /**
     * Display help page (FAQ)
     * 
     * @return help view name
     */
    @GetMapping("/help")
    public String help() {
        log.info("User accessed help page");
        return "auth/help";
    }

    /**
     * Display support page (Contact)
     * 
     * @return support view name
     */
    @GetMapping("/support")
    public String support() {
        log.info("User accessed support page");
        return "auth/support";
    }

    /**
     * Display help page for clients (FAQ)
     * 
     * @return help client view name
     */
    @GetMapping("/helpClient")
    public String helpClient() {
        log.info("Client accessed help page");
        return "auth/helpClient";
    }
}
