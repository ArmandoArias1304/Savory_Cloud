package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Role;
import com.aatechsolutions.elgransazon.domain.entity.SystemLicense;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.domain.repository.CustomerRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for Employee management
 * Handles business logic for employee operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionRegistry sessionRegistry;
    private final LicenseService licenseService;

    /**
     * Find all employees
     * When company context exists, filters by company
     * When company is null (PROGRAMMER), returns all employees
     * 
     * @return List of employees (filtered by company if context exists)
     */
    @Transactional(readOnly = true)
    public List<Employee> findAll() {
        log.debug("Finding all employees");
        Company company = CompanyContext.getCurrentCompany();
        if (company != null) {
            log.debug("Filtering employees by company: {}", company.getIdCompany());
            return employeeRepository.findByCompany(company);
        }
        return employeeRepository.findAll();
    }

    /**
     * Find employee by ID
     * When company context exists, validates the employee belongs to the company
     * 
     * @param id Employee ID
     * @return Optional containing the employee if found and belongs to current company
     */
    @Transactional(readOnly = true)
    public Optional<Employee> findById(Long id) {
        log.debug("Finding employee by id: {}", id);
        Company company = CompanyContext.getCurrentCompany();
        if (company != null) {
            return employeeRepository.findByIdEmpleadoAndCompany(id, company);
        }
        return employeeRepository.findById(id);
    }

    /**
     * Find employee by username
     * MULTI-TENANT: Uses company context to find employee
     * 
     * @param username Employee's username
     * @return Optional containing the employee if found
     */
    @Transactional(readOnly = true)
    public Optional<Employee> findByUsername(String username) {
        log.debug("Finding employee by username: {}", username);
        Company company = CompanyContext.getCurrentCompany();
        if (company != null) {
            return employeeRepository.findByUsernameAndCompany(username, company);
        }
        // No company context - try to find PROGRAMMER (company=null)
        return employeeRepository.findByUsernameAndCompanyIsNull(username);
    }

    /**
     * Create a new employee
     * Encodes the password before saving
     * Sets admin as supervisor by default if no supervisor is provided
     * 
     * @param employee Employee to create
     * @param createdBy Username of the user creating this employee
     * @return Created employee
     * @throws IllegalArgumentException if employee with same username or phone already exists
     * @throws IllegalStateException if creating would exceed license user limit
     */
    @Transactional
    public Employee create(Employee employee, String createdBy) {
        log.info("Creating new employee: {} by {}", employee.getUsername(), createdBy);

        // Validate user limit before creating (only if employee will be enabled)
        if (employee.getEnabled() == null || employee.getEnabled()) {
            validateUserLimit();
        }

        // Get current company for multi-tenant validation
        Company company = CompanyContext.requireCurrentCompany();
        
        // Validate username uniqueness per company
        if (employeeRepository.existsByUsernameAndCompany(employee.getUsername(), company)) {
            log.error("Employee with username {} already exists in company {}", employee.getUsername(), company.getIdCompany());
            throw new IllegalArgumentException("El usuario '" + employee.getUsername() + "' ya existe");
        }
        
        // Check if username already exists in customers table for this company (cross-table validation)
        if (customerRepository.existsByUsernameIgnoreCaseAndCompany(employee.getUsername(), company)) {
            log.error("Username {} already exists in customers table for company {}", employee.getUsername(), company.getIdCompany());
            throw new IllegalArgumentException("El usuario '" + employee.getUsername() + "' ya existe");
        }

        // Check if phone number is already taken within company (if provided)
        if (employee.getTelefono() != null && !employee.getTelefono().isEmpty() &&
            employeeRepository.existsByTelefonoAndCompany(employee.getTelefono(), company)) {
            log.error("Employee with phone {} already exists in company {}", employee.getTelefono(), company.getIdCompany());
            throw new IllegalArgumentException("El teléfono '" + employee.getTelefono() + "' ya está registrado");
        }
        
        // Set company on new employee
        employee.setCompany(company);

        // Encode password before saving
        String encodedPassword = passwordEncoder.encode(employee.getContrasenia());
        employee.setContrasenia(encodedPassword);

        // Set audit fields
        employee.setCreatedBy(createdBy);
        employee.setUpdatedBy(createdBy);

        // Set admin as supervisor by default if no supervisor is provided
        // MULTI-TENANT: Find admin within the same company
        if (employee.getSupervisor() == null) {
            Optional<Employee> admin = employeeRepository.findByUsernameAndCompany("admin", company);
            admin.ifPresent(employee::setSupervisor);
            log.debug("Set admin as default supervisor for employee: {}", employee.getUsername());
        }

        Employee savedEmployee = employeeRepository.save(employee);
        log.info("Employee created successfully with id: {}", savedEmployee.getIdEmpleado());
        
        return savedEmployee;
    }

    /**
     * Update an existing employee
     * 
     * @param id Employee ID to update
     * @param employeeDetails Updated employee details
     * @param updatedBy Username of the user updating this employee
     * @return Updated employee
     * @throws IllegalArgumentException if employee not found
     */
    @Transactional
    public Employee update(Long id, Employee employeeDetails, String updatedBy) {
        log.info("Updating employee with id: {} by {}", id, updatedBy);

        // Get current company for multi-tenant validation
        Company company = CompanyContext.requireCurrentCompany();
        
        Employee employee = employeeRepository.findByIdEmpleadoAndCompany(id, company)
                .orElseThrow(() -> {
                    log.error("Employee not found with id: {} in company: {}", id, company.getIdCompany());
                    return new IllegalArgumentException("Empleado no encontrado con id: " + id);
                });

        // Get current user to check if it's a MANAGER
        // MULTI-TENANT: Find current user within the same company
        Employee currentUser = employeeRepository.findByUsernameAndCompany(updatedBy, company)
                .orElseThrow(() -> new IllegalStateException("Usuario actual no encontrado"));
        
        boolean isManager = currentUser.hasRole(com.aatechsolutions.elgransazon.domain.entity.Role.MANAGER);

        // Track if username is changing
        boolean usernameChanged = !employee.getUsername().equals(employeeDetails.getUsername());
        String oldUsername = employee.getUsername();
        
        // Check if username is being changed and if it's already taken within company
        if (usernameChanged && employeeRepository.existsByUsernameAndCompany(employeeDetails.getUsername(), company)) {
            log.error("Username {} already exists in company {}", employeeDetails.getUsername(), company.getIdCompany());
            throw new IllegalArgumentException("El usuario '" + employeeDetails.getUsername() + "' ya existe");
        }

        // Check if phone is being changed and if it's already taken within company (if provided)
        if (employeeDetails.getTelefono() != null && !employeeDetails.getTelefono().isEmpty() &&
            !employeeDetails.getTelefono().equals(employee.getTelefono()) &&
            employeeRepository.existsByTelefonoAndCompany(employeeDetails.getTelefono(), company)) {
            log.error("Phone {} already exists in company {}", employeeDetails.getTelefono(), company.getIdCompany());
            throw new IllegalArgumentException("El teléfono '" + employeeDetails.getTelefono() + "' ya está registrado");
        }

        // Update basic fields
        employee.setUsername(employeeDetails.getUsername());
        employee.setNombre(employeeDetails.getNombre());
        employee.setApellido(employeeDetails.getApellido());
        employee.setEdad(employeeDetails.getEdad());
        employee.setTelefono(employeeDetails.getTelefono());
        
        // MANAGER restrictions: cannot modify roles, salary, supervisor, or enabled status
        if (isManager) {
            log.info("MANAGER updating employee - preserving roles, salary, supervisor, and enabled status");
            // Keep original values for restricted fields
            // Roles, Salary, Supervisor, and Enabled status are NOT updated
        } else {
            // ADMIN can update everything except disabling ADMIN or PROGRAMMER users
            employee.setSalario(employeeDetails.getSalario());
            employee.setSupervisor(employeeDetails.getSupervisor());
            employee.setRoles(employeeDetails.getRoles());
            
            // Prevent disabling ADMIN or PROGRAMMER employees
            if (employee.hasRole(com.aatechsolutions.elgransazon.domain.entity.Role.ADMIN)) {
                log.warn("Attempted to change enabled status of ADMIN employee ID: {}. ADMINs must remain active.", id);
                employee.setEnabled(true); // Force ADMIN to always be enabled
            } else if (employee.hasRole(com.aatechsolutions.elgransazon.domain.entity.Role.PROGRAMMER)) {
                log.warn("Attempted to change enabled status of PROGRAMMER employee ID: {}. PROGRAMMERs must remain active.", id);
                employee.setEnabled(true); // Force PROGRAMMER to always be enabled
            } else {
                employee.setEnabled(employeeDetails.getEnabled());
            }
        }
        
        // Update audit field
        employee.setUpdatedBy(updatedBy);

        // Track if password is being changed
        boolean passwordChanged = false;
        
        // Only update password if it's provided and different
        if (employeeDetails.getContrasenia() != null && 
            !employeeDetails.getContrasenia().isEmpty() &&
            !employee.getContrasenia().equals(employeeDetails.getContrasenia())) {
            String encodedPassword = passwordEncoder.encode(employeeDetails.getContrasenia());
            employee.setContrasenia(encodedPassword);
            passwordChanged = true;
        }

        Employee updatedEmployee = employeeRepository.save(employee);
        log.info("Employee updated successfully: {}", updatedEmployee.getIdEmpleado());
        
        // Invalidate sessions if username was changed
        if (usernameChanged) {
            invalidateUserSessions(oldUsername);
            log.info("Invalidated sessions for user {} due to username change", oldUsername);
        }
        
        // Invalidate sessions if password was changed
        if (passwordChanged) {
            invalidateUserSessions(updatedEmployee.getUsername());
            log.info("Invalidated sessions for user {} due to password change", updatedEmployee.getUsername());
        }
        
        return updatedEmployee;
    }

    /**
     * Delete an employee
     * Validates employee belongs to current company
     * 
     * @param id Employee ID to delete
     * @throws IllegalArgumentException if employee not found
     */
    @Transactional
    public void delete(Long id) {
        log.info("Deleting employee with id: {}", id);
        
        Company company = CompanyContext.requireCurrentCompany();
        
        if (!employeeRepository.findByIdEmpleadoAndCompany(id, company).isPresent()) {
            log.error("Employee not found with id: {} in company: {}", id, company.getIdCompany());
            throw new IllegalArgumentException("Employee not found with id: " + id);
        }

        employeeRepository.deleteById(id);
        log.info("Employee deleted successfully: {}", id);
    }

    /**
     * Change employee password
     * Validates employee belongs to current company
     * 
     * @param id Employee ID
     * @param newPassword New password (plain text)
     * @throws IllegalArgumentException if employee not found
     */
    @Transactional
    public void changePassword(Long id, String newPassword) {
        log.info("Changing password for employee with id: {}", id);
        
        Company company = CompanyContext.requireCurrentCompany();

        Employee employee = employeeRepository.findByIdEmpleadoAndCompany(id, company)
                .orElseThrow(() -> {
                    log.error("Employee not found with id: {} in company: {}", id, company.getIdCompany());
                    return new IllegalArgumentException("Employee not found with id: " + id);
                });

        String encodedPassword = passwordEncoder.encode(newPassword);
        employee.setContrasenia(encodedPassword);
        
        employeeRepository.save(employee);
        log.info("Password changed successfully for employee: {}", id);
        
        // Invalidate sessions after password change
        invalidateUserSessions(employee.getUsername());
        log.info("Invalidated sessions for user {} due to password change", employee.getUsername());
    }

    /**
     * Enable or disable an employee
     * Validates employee belongs to current company
     * 
     * @param id Employee ID
     * @param enabled Enable status
     * @param updatedBy Username of the user updating this employee
     * @throws IllegalArgumentException if employee not found
     * @throws IllegalStateException if trying to activate would exceed license user limit
     */
    @Transactional
    public void setEnabled(Long id, boolean enabled, String updatedBy) {
        log.info("Setting enabled status to {} for employee with id: {} by {}", enabled, id, updatedBy);
        
        Company company = CompanyContext.requireCurrentCompany();

        Employee employee = employeeRepository.findByIdEmpleadoAndCompany(id, company)
                .orElseThrow(() -> {
                    log.error("Employee not found with id: {} in company: {}", id, company.getIdCompany());
                    return new IllegalArgumentException("Empleado no encontrado con id: " + id);
                });

        // ADMIN and PROGRAMMER employees cannot be deactivated
        if (!enabled) {
            if (employee.hasRole(Role.ADMIN)) {
                log.warn("Attempt to deactivate ADMIN employee with id: {}", id);
                throw new IllegalStateException("No se puede desactivar a un empleado con rol Administrador");
            }
            if (employee.hasRole(Role.PROGRAMMER)) {
                log.warn("Attempt to deactivate PROGRAMMER employee with id: {}", id);
                throw new IllegalStateException("No se puede desactivar a un usuario del sistema (Programador)");
            }
        }

        // If activating employee, check license user limit
        if (enabled && !employee.getEnabled()) {
            validateUserLimit();
        }

        employee.setEnabled(enabled);
        employee.setUpdatedBy(updatedBy);
        employeeRepository.save(employee);
        
        log.info("Employee enabled status updated: {}", id);
        
        // Invalidate sessions if employee was disabled
        if (!enabled) {
            invalidateUserSessions(employee.getUsername());
            log.info("Invalidated sessions for user {} due to account being disabled", employee.getUsername());
        }
    }

    /**
     * Update employee's last access time
     * Uses native query to avoid validation issues
     * 
     * @param username Employee's username
     */
    @Transactional
    public void updateLastAccess(String username) {
        log.debug("Updating last access for user: {}", username);
        
        // MULTI-TENANT: Find employee by username+company
        Company company = CompanyContext.getCurrentCompany();
        Optional<Employee> employeeOpt;
        if (company != null) {
            employeeOpt = employeeRepository.findByUsernameAndCompany(username, company);
        } else {
            employeeOpt = employeeRepository.findByUsernameAndCompanyIsNull(username);
        }
        if (employeeOpt.isPresent()) {
            // Use native query to update only the lastAccess field without triggering validation
            employeeRepository.updateLastAccessByUsername(username, java.time.LocalDateTime.now());
            log.debug("Last access updated for user: {}", username);
        }
    }

    /**
     * Find all enabled employees
     * When company context exists, filters by company
     * 
     * @return List of enabled employees (filtered by company if context exists)
     */
    @Transactional(readOnly = true)
    public List<Employee> findAllEnabled() {
        log.debug("Finding all enabled employees");
        Company company = CompanyContext.getCurrentCompany();
        if (company != null) {
            log.debug("Filtering enabled employees by company: {}", company.getIdCompany());
            return employeeRepository.findByEnabledTrueAndCompany(company);
        }
        return employeeRepository.findByEnabledTrue();
    }

    /**
     * Find employees by role
     * When company context exists, filters by company
     * 
     * @param roleName Role name (e.g., "ROLE_WAITER")
     * @return List of employees with that role (filtered by company if context exists)
     */
    @Transactional(readOnly = true)
    public List<Employee> findByRole(String roleName) {
        log.debug("Finding employees by role: {}", roleName);
        Company company = CompanyContext.getCurrentCompany();
        if (company != null) {
            log.debug("Filtering employees by role {} and company: {}", roleName, company.getIdCompany());
            return employeeRepository.findByCompany(company).stream()
                    .filter(e -> e.hasRole(roleName))
                    .toList();
        }
        return employeeRepository.findAll().stream()
                .filter(e -> e.hasRole(roleName))
                .toList();
    }

    /**
     * Count all employees (excluding PROGRAMMER)
     * PROGRAMMER is not counted as they are system users, not restaurant employees
     * When company context exists, counts only employees of that company
     * 
     * @return Total number of employees excluding PROGRAMMER (filtered by company if context exists)
     */
    @Transactional(readOnly = true)
    public long countAll() {
        Company company = CompanyContext.getCurrentCompany();
        if (company != null) {
            // Count by company - PROGRAMMER won't be in company anyway
            return employeeRepository.findByCompany(company).size();
        }
        return employeeRepository.countAllExcludingProgrammer();
    }

    /**
     * Count enabled employees (excluding PROGRAMMER)
     * PROGRAMMER is not counted towards license user limits
     * When company context exists, counts only employees of that company
     * 
     * @return Number of enabled employees excluding PROGRAMMER (filtered by company if context exists)
     */
    @Transactional(readOnly = true)
    public long countEnabled() {
        Company company = CompanyContext.getCurrentCompany();
        if (company != null) {
            return employeeRepository.countEnabledByCompanyExcludingProgrammer(company);
        }
        return employeeRepository.countEnabledExcludingProgrammer();
    }

    /**
     * Count all employees for a specific company (excluding PROGRAMMER)
     * 
     * @param company The company to count employees for
     * @return Total number of employees excluding PROGRAMMER
     */
    @Transactional(readOnly = true)
    public long countAllByCompany(Company company) {
        if (company == null) {
            return employeeRepository.countAllExcludingProgrammer();
        }
        return employeeRepository.findByCompany(company).size();
    }

    /**
     * Count enabled employees for a specific company (excluding PROGRAMMER)
     * 
     * @param company The company to count employees for
     * @return Number of enabled employees excluding PROGRAMMER
     */
    @Transactional(readOnly = true)
    public long countEnabledByCompany(Company company) {
        if (company == null) {
            return employeeRepository.countEnabledExcludingProgrammer();
        }
        return employeeRepository.countEnabledByCompanyExcludingProgrammer(company);
    }

    /**
     * Find all employees for a specific company
     * 
     * @param company The company to find employees for
     * @return List of employees for the company
     */
    @Transactional(readOnly = true)
    public List<Employee> findAllByCompany(Company company) {
        if (company == null) {
            return employeeRepository.findAll();
        }
        return employeeRepository.findByCompany(company);
    }

    /**
     * Find employees supervised by a specific employee
     * When company context exists, filters by company
     * 
     * @param supervisorId Supervisor's employee ID
     * @return List of employees supervised by this employee (filtered by company if context exists)
     */
    @Transactional(readOnly = true)
    public List<Employee> findBySupervisor(Long supervisorId) {
        log.debug("Finding employees supervised by: {}", supervisorId);
        Company company = CompanyContext.getCurrentCompany();
        if (company != null) {
            return employeeRepository.findBySupervisorIdEmpleadoAndCompany(supervisorId, company);
        }
        return employeeRepository.findBySupervisorIdEmpleado(supervisorId);
    }

    /**
     * Invalidate all active sessions for a user
     * Forces the user to re-authenticate
     * 
     * @param username Username of the user whose sessions should be invalidated
     */
    private void invalidateUserSessions(String username) {
        log.debug("Invalidating sessions for user: {}", username);
        
        try {
            List<Object> principals = sessionRegistry.getAllPrincipals();
            
            for (Object principal : principals) {
                if (principal instanceof UserDetails) {
                    UserDetails userDetails = (UserDetails) principal;
                    
                    if (userDetails.getUsername().equals(username)) {
                        List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
                        
                        for (SessionInformation session : sessions) {
                            session.expireNow();
                            log.info("Expired session {} for user {}", session.getSessionId(), username);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error invalidating sessions for user {}: {}", username, e.getMessage(), e);
        }
    }

    /**
     * Validate that activating a new employee doesn't exceed the license user limit
     * PROGRAMMER is not counted towards the license user limit
     * When company context exists, validates per company
     * 
     * @throws IllegalStateException if the user limit would be exceeded
     */
    private void validateUserLimit() {
        SystemLicense license = licenseService.getLicense();
        
        // If no license or no user limit, allow unlimited users
        if (license == null || !license.hasUserLimit()) {
            log.debug("No user limit enforced - license allows unlimited users");
            return;
        }
        
        Company company = CompanyContext.getCurrentCompany();
        long currentActiveUsers;
        
        if (company != null) {
            currentActiveUsers = employeeRepository.countEnabledByCompanyExcludingProgrammer(company);
        } else {
            currentActiveUsers = employeeRepository.countEnabledExcludingProgrammer();
        }
        
        Integer maxUsers = license.getMaxUsers();
        
        if (currentActiveUsers >= maxUsers) {
            String errorMsg = String.format(
                "No se puede activar el empleado. Has alcanzado el límite máximo de usuarios activos (%d/%d) permitido por tu licencia. " +
                "Para activar este empleado, primero desactiva otro empleado o contacta al proveedor para aumentar tu límite.",
                currentActiveUsers, maxUsers
            );
            log.warn("User limit exceeded: {}/{} active users (excluding PROGRAMMER)", currentActiveUsers, maxUsers);
            throw new IllegalStateException(errorMsg);
        }
        
        log.debug("User limit validation passed: {}/{} active users (excluding PROGRAMMER)", currentActiveUsers, maxUsers);
    }
}
