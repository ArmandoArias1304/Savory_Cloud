package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.CustomerService;
import com.aatechsolutions.elgransazon.application.service.EmailVerificationService;
import com.aatechsolutions.elgransazon.application.service.LicenseService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for Customer authentication and registration
 * Handles login, register, and logout for customers
 * MULTI-TENANT: Customers are registered per company
 */
@Controller
@RequestMapping("/client")
@RequiredArgsConstructor
@Slf4j
public class ClientAuthController {

    private final CustomerService customerService;
    private final SystemConfigurationService systemConfigurationService;
    private final EmailVerificationService emailVerificationService;
    private final LicenseService licenseService;

    /**
     * Show customer login form
     * Redirects to employee login if no company context or license doesn't allow customer module
     */
    @GetMapping("/login")
    public String showLoginForm(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            Model model) {
        
        log.debug("Displaying customer login form");
        
        // MULTI-TENANT: Client login requires company context
        if (CompanyContext.getCurrentCompany() == null) {
            log.warn("No company context for client login. Redirecting to employee login.");
            return "redirect:/login";
        }
        
        // Check if license allows customer module access (ECOMMERCE only)
        if (!licenseService.hasCustomerModuleAccess()) {
            log.warn("License doesn't allow customer module access. Redirecting to employee login.");
            return "redirect:/login?restricted=true";
        }
        
        // Add system configuration for branding
        model.addAttribute("config", systemConfigurationService.getConfiguration());
        
        if (error != null) {
            if ("employeeAttempt".equals(error)) {
                log.warn("Employee attempted to login via client login page");
                model.addAttribute("errorType", "employeeAttempt");
                model.addAttribute("error", "Este acceso es solo para clientes");
            } else {
                model.addAttribute("error", "Usuario o contraseña incorrectos");
            }
        }
        
        if (logout != null) {
            model.addAttribute("message", "Has cerrado sesión exitosamente");
        }
        
        return "auth/loginClient";
    }

    /**
     * Show customer registration form
     * Redirects to employee login if no company context or license doesn't allow customer module
     */
    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        log.debug("Displaying customer registration form");
        
        // MULTI-TENANT: Client registration requires company context
        if (CompanyContext.getCurrentCompany() == null) {
            log.warn("No company context for client registration. Redirecting to employee login.");
            return "redirect:/login";
        }
        
        // Check if license allows customer module access (ECOMMERCE only)
        if (!licenseService.hasCustomerModuleAccess()) {
            log.warn("License doesn't allow customer module access. Redirecting to employee login.");
            return "redirect:/login?restricted=true";
        }
        
        model.addAttribute("customer", new Customer());
        model.addAttribute("config", systemConfigurationService.getConfiguration());
        return "auth/registerClient";
    }

    /**
     * Process customer registration
     * MULTI-TENANT: Registers customer for current company
     */
    @PostMapping("/register")
    public String registerCustomer(
            @Valid @ModelAttribute("customer") Customer customer,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        // MULTI-TENANT: Get current company from context
        Company currentCompany = CompanyContext.getCurrentCompany();
        if (currentCompany == null) {
            log.error("No company context available for customer registration");
            return "redirect:/login";
        }
        
        log.info("Processing customer registration: {} for company: {}", 
            customer.getEmail(), currentCompany.getIdCompany());
        
        // Validate form
        if (bindingResult.hasErrors()) {
            log.warn("Validation errors in customer registration: {}", bindingResult.getAllErrors());
            model.addAttribute("config", systemConfigurationService.getConfiguration());
            return "auth/registerClient";
        }
        
        try {
            // MULTI-TENANT: Check if username already exists in THIS company's customers
            if (customerService.existsByUsernameAndCompany(customer.getUsername(), currentCompany)) {
                bindingResult.rejectValue("username", "error.customer", "El nombre de usuario ya está en uso");
                model.addAttribute("config", systemConfigurationService.getConfiguration());
                return "auth/registerClient";
            }
            
            // MULTI-TENANT: Check if username exists in employees for THIS company
            if (customerService.usernameExistsInEmployees(customer.getUsername(), currentCompany)) {
                bindingResult.rejectValue("username", "error.customer", "El nombre de usuario ya está en uso");
                model.addAttribute("config", systemConfigurationService.getConfiguration());
                return "auth/registerClient";
            }
            
            // MULTI-TENANT: Check if email already exists in THIS company
            if (customerService.existsByEmailAndCompany(customer.getEmail(), currentCompany)) {
                bindingResult.rejectValue("email", "error.customer", "El correo electrónico ya está registrado");
                model.addAttribute("config", systemConfigurationService.getConfiguration());
                return "auth/registerClient";
            }
            
            // MULTI-TENANT: Check if phone already exists in THIS company
            if (customerService.existsByPhoneAndCompany(customer.getPhone(), currentCompany)) {
                bindingResult.rejectValue("phone", "error.customer", "El teléfono ya está registrado");
                model.addAttribute("config", systemConfigurationService.getConfiguration());
                return "auth/registerClient";
            }
            
            // MULTI-TENANT: Create customer for current company
            Customer newCustomer = customerService.create(customer, currentCompany);
            
            log.info("Customer registered successfully: {} for company: {}", 
                customer.getEmail(), currentCompany.getIdCompany());
            
            // Send verification email
            try {
                emailVerificationService.createOrReuseToken(newCustomer);
                log.info("Verification email sent to: {}", newCustomer.getEmail());
                
                redirectAttributes.addFlashAttribute("successMessage", 
                    "¡Registro exitoso! Te hemos enviado un correo de verificación a " + newCustomer.getEmail() + 
                    ". Por favor verifica tu correo antes de iniciar sesión.");
            } catch (Exception e) {
                log.error("Error sending verification email", e);
                redirectAttributes.addFlashAttribute("successMessage", 
                    "Registro exitoso. Sin embargo, hubo un problema al enviar el correo de verificación. " +
                    "Por favor contacta a soporte.");
            }
            
            return "redirect:/client/login";
            
        } catch (Exception e) {
            log.error("Error registering customer", e);
            model.addAttribute("config", systemConfigurationService.getConfiguration());
            model.addAttribute("errorMessage", "Error al registrar el cliente: " + e.getMessage());
            return "auth/registerClient";
        }
    }
}
