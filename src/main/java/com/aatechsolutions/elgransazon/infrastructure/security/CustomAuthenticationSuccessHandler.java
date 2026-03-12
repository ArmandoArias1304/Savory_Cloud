package com.aatechsolutions.elgransazon.infrastructure.security;

import com.aatechsolutions.elgransazon.application.service.CustomerService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.EmailVerificationService;
import com.aatechsolutions.elgransazon.application.service.LicenseService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.domain.entity.Role;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;

/**
 * Custom authentication success handler
 * Redirects users to role-specific pages after successful login
 * and updates last access timestamp
 */
@Component
@Slf4j
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final EmployeeService employeeService;
    private final CustomerService customerService;
    private final EmailVerificationService emailVerificationService;
    private final LicenseService licenseService;
    private final EmployeeRepository employeeRepository;
    
    public CustomAuthenticationSuccessHandler(@Lazy EmployeeService employeeService,
                                             @Lazy CustomerService customerService,
                                             @Lazy EmailVerificationService emailVerificationService,
                                             @Lazy LicenseService licenseService,
                                             EmployeeRepository employeeRepository) {
        this.employeeService = employeeService;
        this.customerService = customerService;
        this.emailVerificationService = emailVerificationService;
        this.licenseService = licenseService;
        this.employeeRepository = employeeRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        
        String username = authentication.getName();
        log.info("User {} logged in successfully", username);
        
        // Check if it's a customer (has ROLE_CLIENT) or employee
        boolean isCustomer = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals(Role.CLIENT));
        
        // Get the login URL to validate if user is using correct login page
        String referer = request.getHeader("Referer");
        
        log.debug("Referer: {}", referer);
        
        // Validate: Customer trying to login via employee login
        if (isCustomer && (referer != null && !referer.contains("/client/login"))) {
            log.warn("Customer {} attempted to login via employee login page", username);
            request.getSession().invalidate();
            response.sendRedirect("/login?error=clientAttempt");
            return;
        }
        
        // Validate: Employee trying to login via client login
        if (!isCustomer && (referer != null && referer.contains("/client/login"))) {
            log.warn("Employee {} attempted to login via client login page", username);
            request.getSession().invalidate();
            response.sendRedirect("/client/login?error=employeeAttempt");
            return;
        }
        
        // Validate: Customer email verification (async to not block login redirect)
        if (isCustomer) {
            try {
                // MULTI-TENANT: Get customer by username/email AND company
                Company currentCompany = CompanyContext.getCurrentCompany();
                Customer customer = null;
                
                if (currentCompany != null) {
                    customer = customerService.findByUsernameOrEmailAndCompany(username, currentCompany)
                            .orElseThrow(() -> new RuntimeException("Customer not found for company"));
                } else {
                    // Fallback - should not happen for customers
                    log.error("No company context for customer login: {}", username);
                    request.getSession().invalidate();
                    response.sendRedirect("/client/login?error=noCompanyContext");
                    return;
                }
                
                // Check if email is verified
                if (!Boolean.TRUE.equals(customer.getEmailVerified())) {
                    log.warn("Customer {} attempted to login without verifying email", username);
                    
                    boolean emailSent = false;
                    try {
                        // Try to send verification email (respects the token logic)
                        emailSent = emailVerificationService.createOrReuseToken(customer);
                    } catch (Exception e) {
                        log.error("Error sending verification email to unverified customer {}", username, e);
                        // Fallo al enviar el correo, pero debemos bloquear el acceso de todas formas
                    }
                    
                    request.getSession().invalidate();
                    
                    if (emailSent) {
                        response.sendRedirect("/client/login?error=emailNotVerified&emailSent=true");
                    } else {
                        response.sendRedirect("/client/login?error=emailNotVerified&emailSent=false");
                    }
                    return;
                }
                
                // Update last access asynchronously (don't wait for it)
                updateLastAccessAsync(username, true, currentCompany);
                
            } catch (Exception e) {
                log.error("Error checking email verification for customer {}", username, e);
                // Continue with normal flow if check fails
            }
        } else {
            // Update last access asynchronously for employees
            // Capture company context here (on the request thread) before launching async thread
            updateLastAccessAsync(username, false, CompanyContext.getCurrentCompany());
            
            // Check user limit enforcement for non-customer logins
            boolean isProgrammer = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals(Role.PROGRAMMER));
            boolean isAdminRole = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals(Role.ADMIN));
            
            if (!isProgrammer) {
                // MULTI-TENANT: Count employees only for the current company
                Company company = CompanyContext.getCurrentCompany();
                long currentActiveUsers = company != null 
                    ? employeeRepository.countEnabledByCompanyExcludingProgrammer(company)
                    : employeeRepository.countEnabledExcludingProgrammer();
                boolean limitExceeded = licenseService.isUserLimitExceeded(currentActiveUsers);
                
                if (limitExceeded) {
                    if (isAdminRole) {
                        // ADMIN can enter but must go to employees page
                        Integer maxUsers = licenseService.getMaxUsers();
                        request.getSession().setAttribute("userLimitExceeded", true);
                        request.getSession().setAttribute("userLimitExceededInfo",
                            String.format("El límite de usuarios se ha reducido a %d, pero actualmente hay %d empleados activos. " +
                                "Debes desactivar %d empleado(s) para continuar usando el sistema normalmente.",
                                maxUsers, currentActiveUsers, currentActiveUsers - maxUsers));
                        request.getSession().setAttribute("userLimitMax", maxUsers);
                        request.getSession().setAttribute("userLimitCurrent", currentActiveUsers);
                        request.getSession().setAttribute("userLimitExcess", (int)(currentActiveUsers - maxUsers));
                        log.warn("User limit exceeded. Redirecting ADMIN {} to /admin/employees", username);
                        response.sendRedirect("/admin/employees");
                        return;
                    } else {
                        // Non-admin, non-programmer employees: block login entirely
                        log.warn("User limit exceeded. Blocking login for employee: {}", username);
                        request.getSession().invalidate();
                        response.sendRedirect("/login?error=userLimitExceeded");
                        return;
                    }
                }
            }
        }
        
        String targetUrl = determineTargetUrl(authentication);
        log.debug("Redirecting user {} to {}", username, targetUrl);
        
        response.sendRedirect(targetUrl);
    }
    
    /**
     * Update last access timestamp asynchronously to avoid blocking login
     * @param username The user's email/username
     * @param isCustomer Whether this is a customer (true) or employee (false)
     * @param company The company context for customers (null for employees)
     */
    private void updateLastAccessAsync(String username, boolean isCustomer, Company company) {
        // Run in a separate thread to not block the response
        new Thread(() -> {
            try {
                if (isCustomer) {
                    // MULTI-TENANT: Update with company context
                    if (company != null) {
                        customerService.updateLastAccess(username, company);
                    } else {
                        log.warn("No company context for customer last access update: {}", username);
                    }
                    log.debug("Updated last access for customer {}", username);
                } else {
                    employeeService.updateLastAccess(username, company);
                    log.debug("Updated last access for employee {}", username);
                }
            } catch (Exception e) {
                log.error("Error updating last access for user {}", username, e);
            }
        }).start();
    }

    /**
     * Determine the target URL based on user's roles
     * Priority: CLIENT > ADMIN > MANAGER > CHEF > BARISTA > WAITER > CASHIER > DELIVERY > default
     */
    private String determineTargetUrl(Authentication authentication) {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        
        for (GrantedAuthority authority : authorities) {
            String role = authority.getAuthority();
            log.debug("Checking role: {}", role);
            
            if (Role.CLIENT.equals(role)) {
                return "/client/dashboard";
            } else if (Role.PROGRAMMER.equals(role)) {
                return "/programmer/dashboard";
            } else if (Role.ADMIN.equals(role)) {
                return "/admin/dashboard";
            } else if (Role.MANAGER.equals(role)) {
                return "/admin/dashboard";
            } else if (Role.CHEF.equals(role)) {
                return "/chef/dashboard";
            } else if (Role.BARISTA.equals(role)) {
                return "/chef/dashboard";
            } else if (Role.WAITER.equals(role)) {
                return "/waiter/dashboard";
            } else if (Role.CASHIER.equals(role)) {
                return "/cashier/dashboard";
            } else if (Role.DELIVERY.equals(role)) {
                return "/delivery/dashboard";
            }
        }
        
        // Default redirect
        log.warn("No specific role found for user {}, redirecting to default home", authentication.getName());
        return "/home";
    }
}
