package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Role;
import com.aatechsolutions.elgransazon.domain.repository.CustomerRepository;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.infrastructure.security.CustomCustomerDetails;
import com.aatechsolutions.elgransazon.infrastructure.security.CustomEmployeeDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Custom UserDetailsService implementation for Spring Security
 * Loads user details from Employee or Customer entities
 * 
 * Authentication logic:
 * 1. First tries to find an Employee by username
 * 2. For employees (except PROGRAMMER), validates they belong to the current company
 * 3. If not found, tries to find a Customer by email/username AND company
 * 4. If neither found, throws UsernameNotFoundException
 * 
 * MULTI-TENANT: 
 * - PROGRAMMER can log in from any domain (global user, company=null)
 * - Other employees must belong to the company from CompanyContext
 * - Customers must be registered with the specific company (per-company registration)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("=== Starting authentication for user: {} ===", username);
        
        try {
            // MULTI-TENANT: First try to find Employee by username+company if context exists
            Company currentCompany = CompanyContext.getCurrentCompany();
            Employee employee = null;
            
            if (currentCompany != null) {
                // Try company-specific search first (for regular employees)
                employee = employeeRepository.findByUsernameAndCompany(username, currentCompany).orElse(null);
                log.debug("Company context exists ({}), searched by username+company: found={}", 
                    currentCompany.getIdCompany(), employee != null);
            }
            
            // If not found in company context, try to find PROGRAMMER (global user with company=null)
            if (employee == null) {
                employee = employeeRepository.findByUsernameAndCompanyIsNull(username).orElse(null);
                log.debug("Searched for PROGRAMMER (company=null): found={}", employee != null);
            }
            
            if (employee != null) {
                log.info("Employee found: {} (ID: {})", employee.getFullName(), employee.getIdEmpleado());
                log.info("Employee enabled: {}", employee.getEnabled());
                
                // MULTI-TENANT VALIDATION: Check if employee belongs to current company
                if (!isEmployeeAllowedForCurrentCompany(employee)) {
                    log.warn("Employee {} does not belong to the current company context", username);
                    throw new UsernameNotFoundException("Usuario no encontrado: " + username);
                }
                
                // Try to access roles
                try {
                    log.info("Attempting to load employee roles...");
                    int rolesCount = employee.getRoles() != null ? employee.getRoles().size() : 0;
                    log.info("Roles loaded successfully. Count: {}", rolesCount);
                    
                    if (rolesCount > 0) {
                        employee.getRoles().forEach(role -> 
                            log.info("  - Role: {} (ID: {})", role.getNombreRol(), role.getIdRol())
                        );
                    } else {
                        log.warn("WARNING: Employee {} has NO ROLES assigned!", username);
                    }
                } catch (Exception e) {
                    log.error("ERROR loading employee roles: {}", e.getMessage(), e);
                    throw e;
                }

                // MULTI-TENANT: Use CustomEmployeeDetails which includes companyId in equals/hashCode
                // This ensures employees with same username from different companies are treated as different users
                log.info("Building CustomEmployeeDetails for employee...");
                Long companyId = employee.getCompany() != null ? employee.getCompany().getIdCompany() : null;
                UserDetails userDetails = new CustomEmployeeDetails(
                        employee.getUsername(),
                        employee.getContrasenia(),
                        employee.getEnabled(),
                        getEmployeeAuthorities(employee),
                        companyId,
                        employee.getIdEmpleado()
                );
                
                log.info("=== Authentication successful for employee: {} (companyId: {}) ===", username, companyId);
                return userDetails;
            }
            
            // If not an employee, try to find a Customer by email or username for current company
            log.info("Employee not found, trying customer...");
            Company currentCompanyForCustomer = CompanyContext.getCurrentCompany();
            Customer customer = null;
            
            if (currentCompanyForCustomer != null) {
                // MULTI-TENANT: Search customer by username/email AND company
                customer = customerRepository.findByUsernameOrEmailAndCompany(username, currentCompanyForCustomer).orElse(null);
                log.debug("Company context exists ({}), searched customer by username/email+company: found={}", 
                    currentCompanyForCustomer.getIdCompany(), customer != null);
            } else {
                log.warn("No company context available for customer authentication");
            }
            
            if (customer != null) {
                log.info("Customer found: {} (ID: {}) for company: {}", 
                    customer.getFullName(), customer.getIdCustomer(), currentCompanyForCustomer.getIdCompany());
                log.info("Customer active: {}", customer.getActive());
                
                // MULTI-TENANT: Use CustomCustomerDetails which includes companyId in equals/hashCode
                // This ensures customers with same email from different companies are treated as different users
                log.info("Building CustomCustomerDetails for customer...");
                UserDetails userDetails = new CustomCustomerDetails(
                        customer.getEmail(),
                        customer.getPassword(),
                        customer.getActive(),
                        getCustomerAuthorities(),
                        currentCompanyForCustomer.getIdCompany(),
                        customer.getIdCustomer()
                );
                
                log.info("=== Authentication successful for customer: {} (companyId: {}) ===", 
                    username, currentCompanyForCustomer.getIdCompany());
                return userDetails;
            }
            
            // Neither employee nor customer found
            log.error("User not found (neither employee nor customer): {}", username);
            throw new UsernameNotFoundException("Usuario o cliente no encontrado: " + username);
            
        } catch (UsernameNotFoundException e) {
            log.error("User not found: {}", username);
            throw e;
        } catch (Exception e) {
            log.error("CRITICAL ERROR during authentication for user {}: {}", username, e.getMessage(), e);
            log.error("Exception type: {}", e.getClass().getName());
            if (e.getCause() != null) {
                log.error("Caused by: {}", e.getCause().getMessage(), e.getCause());
            }
            throw new UsernameNotFoundException("Error loading user: " + username, e);
        }
    }

    /**
     * Check if an employee is allowed to log in to the current company context
     * MULTI-TENANT RULES:
     * - PROGRAMMER role: Can ONLY log in when there's NO company context (global access)
     * - Other employees: Must belong to the company from CompanyContext
     * - If no company context: Only PROGRAMMER can log in
     */
    private boolean isEmployeeAllowedForCurrentCompany(Employee employee) {
        // Check if employee is a PROGRAMMER (global role)
        boolean isProgrammer = employee.getRoles().stream()
                .anyMatch(role -> Role.PROGRAMMER.equals(role.getNombreRol()));
        
        // Get current company context
        Company currentCompany = CompanyContext.getCurrentCompany();
        
        if (isProgrammer) {
            // PROGRAMMER can ONLY log in when there's NO company context
            if (currentCompany != null) {
                log.warn("PROGRAMMER {} attempted to login with company context ({}), denied", 
                    employee.getUsername(), currentCompany.getSlug());
                return false;
            }
            log.debug("PROGRAMMER {} allowed (no company context)", employee.getUsername());
            return true;
        }
        
        if (currentCompany == null) {
            // No company context - only PROGRAMMER can log in (already handled above)
            log.debug("No company context and employee is not PROGRAMMER, denying access");
            return false;
        }
        
        // Check if employee belongs to the current company
        Company employeeCompany = employee.getCompany();
        if (employeeCompany == null) {
            log.debug("Employee {} has no company assigned, denying access", employee.getUsername());
            return false;
        }
        
        boolean belongsToCompany = employeeCompany.getIdCompany().equals(currentCompany.getIdCompany());
        log.debug("Employee {} belongs to company {}, current company is {}: allowed={}",
                employee.getUsername(), employeeCompany.getIdCompany(), currentCompany.getIdCompany(), belongsToCompany);
        
        return belongsToCompany;
    }

    /**
     * Returns the authorities/roles for an employee from their assigned roles
     */
    private Collection<? extends GrantedAuthority> getEmployeeAuthorities(Employee employee) {
        if (employee.getRoles().isEmpty()) {
            log.warn("Employee {} has no roles assigned, granting default EMPLOYEE role", employee.getUsername());
            return Collections.singletonList(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        }

        return employee.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getNombreRol()))
                .toList();
    }
    
    /**
     * Returns the authorities/roles for a customer (always ROLE_CLIENT)
     */
    private Collection<? extends GrantedAuthority> getCustomerAuthorities() {
        return List.of(new SimpleGrantedAuthority(Role.CLIENT));
    }
}
