package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.LicenseService;
import com.aatechsolutions.elgransazon.application.service.ShiftService;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Role;
import com.aatechsolutions.elgransazon.domain.entity.Shift;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.domain.repository.RoleRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for Employee management
 * ADMIN has full access, MANAGER has restricted access (cannot create, deactivate, or edit sensitive fields)
 */
@Controller
@RequestMapping("/admin/employees")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class EmployeeController {

    private final EmployeeService employeeService;
    private final ShiftService shiftService;
    private final RoleRepository roleRepository;
    private final EmployeeRepository employeeRepository;
    private final LicenseService licenseService;
    private final DateTimeService dateTimeService;

    /**
     * Show list of all employees with filters
     * MANAGER cannot see ADMIN employees
     * PROGRAMMER is automatically excluded from counts (handled by countAll() and countEnabled())
     */
    @GetMapping
    public String listEmployees(Authentication authentication, Model model) {
        log.debug("Displaying employees list");
        
        Employee currentUser = employeeService.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Usuario actual no encontrado"));
        
        List<Employee> employees;
        
        // Filter employees for MANAGER role - only show supervised employees
        if (currentUser.hasRole(Role.MANAGER)) {
            employees = employeeService.findBySupervisor(currentUser.getIdEmpleado());
            // Double check to ensure no ADMINs or PROGRAMMERs are included
            employees = employees.stream()
                    .filter(e -> !e.hasRole(Role.ADMIN) && !e.hasRole(Role.PROGRAMMER))
                    .collect(Collectors.toList());
            log.debug("Filtered employees for MANAGER (supervisor check)");
        } else {
            // ADMIN sees all employees except PROGRAMMER
            employees = employeeService.findAll().stream()
                    .filter(e -> !e.hasRole(Role.PROGRAMMER))
                    .collect(Collectors.toList());
        }
        
        List<Shift> allShifts = shiftService.getAllShifts();
        List<Role> allRoles = roleRepository.findAll().stream()
                .filter(r -> !r.getNombreRol().equals(Role.ADMIN) && !r.getNombreRol().equals(Role.PROGRAMMER))
                .collect(Collectors.toList());
        
        // These counts EXCLUDE PROGRAMMER (handled by countAll() and countEnabled())
        long totalCount = employeeService.countAll();        // Already excludes PROGRAMMER
        long enabledCount = employeeService.countEnabled();  // Already excludes PROGRAMMER
        long disabledCount = totalCount - enabledCount;
        long rolesCount = allRoles.size();
        
        model.addAttribute("employees", employees);
        model.addAttribute("allShifts", allShifts);
        model.addAttribute("allRoles", allRoles);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("enabledCount", enabledCount);
        model.addAttribute("disabledCount", disabledCount);
        model.addAttribute("rolesCount", rolesCount);
        
        // License limit information (PROGRAMMER not counted towards limit)
        Integer maxUsers = licenseService.getMaxUsers();
        boolean canCreateMore = licenseService.canCreateMoreUsers(enabledCount);
        model.addAttribute("maxUsers", maxUsers);
        model.addAttribute("canCreateMoreUsers", canCreateMore);
        model.addAttribute("hasUserLimit", maxUsers != null);
        
        return "admin/employees/list";
    }

    /**
     * Show form to create a new employee
     * Only ADMIN can create employees
     */
    @GetMapping("/new")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public String newEmployeeForm(Model model, RedirectAttributes redirectAttributes) {
        log.debug("Displaying new employee form");
        
        // Check if user limit has been reached before showing form
        long currentActiveUsers = employeeService.countEnabled();
        Integer maxUsers = licenseService.getMaxUsers();
        boolean canCreateMore = licenseService.canCreateMoreUsers(currentActiveUsers);
        
        if (!canCreateMore) {
            String limitMessage = maxUsers != null 
                ? "Has alcanzado el límite de " + maxUsers + " usuarios activos. No puedes crear más empleados hasta que desactives alguno o actualices tu licencia."
                : "No se puede crear más usuarios en este momento.";
            redirectAttributes.addFlashAttribute("errorMessage", limitMessage);
            return "redirect:/admin/employees";
        }
        
        Employee employee = new Employee();
        employee.setEnabled(true);
        
        List<Role> availableRoles = roleRepository.findAll().stream()
                .filter(r -> !r.getNombreRol().equals(Role.ADMIN))
                .collect(Collectors.toList());
        
        List<Shift> availableShifts = shiftService.getAllActiveShifts();
        List<Employee> supervisors = employeeService.findAll().stream()
                .filter(e -> e.hasRole(Role.ADMIN) || e.hasRole(Role.MANAGER))
                .collect(Collectors.toList());
        
        model.addAttribute("employee", employee);
        model.addAttribute("availableRoles", availableRoles);
        model.addAttribute("shifts", availableShifts);
        model.addAttribute("supervisors", supervisors);
        model.addAttribute("isEdit", false);
        model.addAttribute("isAdminEmployee", false); // New employees are never ADMIN
        model.addAttribute("formAction", "/admin/employees");
        
        // Add license limit information to form
        model.addAttribute("maxUsers", maxUsers);
        model.addAttribute("currentActiveUsers", currentActiveUsers);
        model.addAttribute("hasUserLimit", maxUsers != null);
        model.addAttribute("canCreateMore", canCreateMore);
        
        return "admin/employees/form";
    }

    /**
     * Show form to edit an existing employee
     */
    @GetMapping("/{id}/edit")
    public String editEmployeeForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes, Authentication authentication) {
        log.debug("Displaying edit form for employee ID: {}", id);
        
        return employeeService.findById(id)
                .map(employee -> {
                    // Check permissions: Manager can only edit their own supervised employees
                    Employee currentUser = employeeService.findByUsername(authentication.getName())
                            .orElseThrow(() -> new IllegalStateException("Usuario actual no encontrado"));
                            
                    if (currentUser.hasRole(Role.MANAGER)) {
                        boolean isMyEmployee = employee.getSupervisor() != null && 
                                               employee.getSupervisor().getIdEmpleado().equals(currentUser.getIdEmpleado());
                        
                        // Also allow editing self? Usually lists don't include self for managers in this context or self-edit is profile.
                        // Assuming Manager manages OTHER employees. 
                        // If the employee found is NOT supervised by current user, deny.
                        if (!isMyEmployee) {
                             log.warn("Manager {} tried to edit employee {} without supervision rights", currentUser.getUsername(), id);
                             redirectAttributes.addFlashAttribute("errorMessage", "No tiene permiso para editar este empleado.");
                             return "redirect:/admin/employees";
                        }
                    }

                    // Check if employee has ADMIN role
                    boolean isAdminEmployee = employee.hasRole(Role.ADMIN);
                    
                    List<Role> availableRoles = roleRepository.findAll().stream()
                            .filter(r -> !r.getNombreRol().equals(Role.ADMIN))
                            .collect(Collectors.toList());
                    
                    List<Shift> availableShifts = shiftService.getAllActiveShifts();
                    List<Employee> supervisors = employeeService.findAll().stream()
                            .filter(e -> e.hasRole(Role.ADMIN) || e.hasRole(Role.MANAGER))
                            .filter(e -> !e.getIdEmpleado().equals(id)) // Can't be own supervisor
                            .collect(Collectors.toList());
                    
                    model.addAttribute("employee", employee);
                    model.addAttribute("availableRoles", availableRoles);
                    model.addAttribute("shifts", availableShifts);
                    model.addAttribute("supervisors", supervisors);
                    model.addAttribute("isEdit", true);
                    model.addAttribute("isAdminEmployee", isAdminEmployee);
                    model.addAttribute("formAction", "/admin/employees/" + id);
                    
                    return "admin/employees/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Empleado no encontrado");
                    return "redirect:/admin/employees";
                });
    }

    /**
     * Create a new employee
     * Only ADMIN can create employees
     */
    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public String createEmployee(
            @Valid @ModelAttribute("employee") Employee employee,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "roleId", required = false) Long roleId,
            @RequestParam(value = "shiftId", required = false) Long shiftId,
            @RequestParam(value = "supervisorId", required = false) Long supervisorId,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.info("Creating new employee: {}", employee.getUsername());
        
        // Check if user limit has been reached
        long currentActiveUsers = employeeService.countEnabled();
        if (!licenseService.canCreateMoreUsers(currentActiveUsers)) {
            Integer maxUsers = licenseService.getMaxUsers();
            String limitMessage = maxUsers != null 
                ? "Has alcanzado el límite de " + maxUsers + " usuarios activos. Actualiza tu licencia para crear más empleados."
                : "No se puede crear más usuarios en este momento.";
            redirectAttributes.addFlashAttribute("errorMessage", limitMessage);
            return "redirect:/admin/employees";
        }
        
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, employee, false);
            return "admin/employees/form";
        }
        
        try {
            // Set password
            if (password != null && !password.isEmpty()) {
                employee.setContrasenia(password);
            } else {
                model.addAttribute("errorMessage", "La contraseña es requerida");
                prepareFormModel(model, employee, false);
                return "admin/employees/form";
            }
            
            // Set role
            if (roleId != null) {
                Optional<Role> role = roleRepository.findById(roleId);
                if (role.isPresent()) {
                    Set<Role> roles = new HashSet<>();
                    roles.add(role.get());
                    employee.setRoles(roles);
                }
            }
            
            // Set supervisor if provided
            if (supervisorId != null) {
                employeeService.findById(supervisorId).ifPresent(employee::setSupervisor);
            }
            
            // Create employee (supervisor will be set to admin by default if not provided)
            String currentUsername = authentication.getName();
            Employee created = employeeService.create(employee, currentUsername);
            
            // Assign shift if provided
            if (shiftId != null) {
                Optional<Shift> shift = shiftService.getShiftById(shiftId);
                if (shift.isPresent()) {
                    List<Long> employeeIds = Arrays.asList(created.getIdEmpleado());
                    Long actionById = employeeService.findByUsername(currentUsername)
                            .map(Employee::getIdEmpleado)
                            .orElse(null);
                    shiftService.assignEmployeesToShift(shiftId, employeeIds, actionById);
                    log.info("Shift assigned to new employee: {}", created.getUsername());
                }
            }
            
            log.info("Employee created successfully with ID: {}", created.getIdEmpleado());
            redirectAttributes.addFlashAttribute("successMessage", 
                    "Empleado '" + created.getFullName() + "' creado exitosamente");
            return "redirect:/admin/employees";
            
        } catch (IllegalStateException e) {
            // Handle user limit exceeded error
            log.error("User limit exceeded when creating employee: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            prepareFormModel(model, employee, false);
            return "admin/employees/form";
            
        } catch (IllegalArgumentException e) {
            log.error("Validation error creating employee: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            prepareFormModel(model, employee, false);
            return "admin/employees/form";
            
        } catch (Exception e) {
            log.error("Error creating employee", e);
            model.addAttribute("errorMessage", "Error al crear el empleado: " + e.getMessage());
            prepareFormModel(model, employee, false);
            return "admin/employees/form";
        }
    }

    /**
     * Update an existing employee (without password)
     */
    @PostMapping("/{id}")
    public String updateEmployee(
            @PathVariable Long id,
            @Valid @ModelAttribute("employee") Employee employee,
            @RequestParam(value = "roleId", required = false) Long roleId,
            @RequestParam(value = "supervisorId", required = false) Long supervisorId,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.info("Updating employee with ID: {}", id);
        
        // Validation: Manager can only update their own supervised employees
        Employee currentUser = employeeService.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Usuario actual no encontrado"));
                
        if (currentUser.hasRole(Role.MANAGER)) {
             Employee existingEmployee = employeeService.findById(id).orElse(null);
             if (existingEmployee != null) {
                 boolean isMyEmployee = existingEmployee.getSupervisor() != null && 
                                        existingEmployee.getSupervisor().getIdEmpleado().equals(currentUser.getIdEmpleado());
                 
                 if (!isMyEmployee) {
                     log.warn("Manager {} tried to update employee {} without supervision rights", currentUser.getUsername(), id);
                     redirectAttributes.addFlashAttribute("errorMessage", "No tiene permiso para modificar este empleado.");
                     return "redirect:/admin/employees";
                 }
             }
        }
        
        // Check for validation errors, ignoring password field for updates (as it's handled separately)
        boolean hasRelevantErrors = bindingResult.getAllErrors().stream()
                .anyMatch(error -> {
                    if (error instanceof org.springframework.validation.FieldError) {
                        return !((org.springframework.validation.FieldError) error).getField().equals("contrasenia");
                    }
                    return true;
                });

        if (hasRelevantErrors) {
            prepareFormModel(model, employee, true);
            return "admin/employees/form";
        }
        
        try {
            // Get existing employee to check if is ADMIN
            Employee existingEmployee = employeeService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado"));
            
            // Prevent role change for ADMIN employees
            if (existingEmployee.hasRole(Role.ADMIN)) {
                log.warn("Attempted to change role of ADMIN employee ID: {}", id);
                // Preserve ADMIN role
                employee.setRoles(existingEmployee.getRoles());
                // Admin must be their own supervisor or null. 
                // Requirement: "El admin solo tendrá como supervisor el mismo"
                // We set supervisor to self (using existing entity which has the ID)
                employee.setSupervisor(existingEmployee);
            } else {
                // Set role (only for non-ADMIN employees)
                if (roleId != null) {
                    Optional<Role> role = roleRepository.findById(roleId);
                    if (role.isPresent()) {
                        Set<Role> roles = new HashSet<>();
                        roles.add(role.get());
                        employee.setRoles(roles);
                        
                        // Check if promoted to ADMIN (unlikely given UI but possible)
                        if (role.get().getNombreRol().equals(Role.ADMIN)) {
                            employee.setSupervisor(existingEmployee);
                        } else {
                             // Regular employee supervisor assignment
                            if (supervisorId != null) {
                                employeeService.findById(supervisorId).ifPresent(employee::setSupervisor);
                            }
                        }
                    }
                } else {
                     // No role change, preserve supervisor assignment logic
                     if (supervisorId != null) {
                        employeeService.findById(supervisorId).ifPresent(employee::setSupervisor);
                     }
                }
            }
            
            // Preserve existing password (no password change in this endpoint)
            employee.setContrasenia(existingEmployee.getContrasenia());
            
            // Supervisor assignment is now handled above based on Role logic
            // (Removed previous simple assignment)
            
            String currentUsername = authentication.getName();
            Employee updated = employeeService.update(id, employee, currentUsername);
            
            log.info("Employee updated successfully");
            redirectAttributes.addFlashAttribute("successMessage", 
                    "Empleado '" + updated.getFullName() + "' actualizado exitosamente");
            return "redirect:/admin/employees";
            
        } catch (IllegalArgumentException e) {
            log.error("Validation error updating employee: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            prepareFormModel(model, employee, true);
            return "admin/employees/form";
            
        } catch (Exception e) {
            log.error("Error updating employee", e);
            model.addAttribute("errorMessage", "Error al actualizar el empleado: " + e.getMessage());
            prepareFormModel(model, employee, true);
            return "admin/employees/form";
        }
    }

    /**
     * Change employee password (separate endpoint to prevent session logout)
     */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/{id}/change-password")
    public String changeEmployeePassword(
            @PathVariable Long id,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        log.info("Changing password for employee ID: {}", id);
        
        try {
            // Validate passwords match
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                        "Las contraseñas no coinciden");
                return "redirect:/admin/employees/" + id + "/edit";
            }
            
            // Validate minimum length
            if (newPassword.length() < 6) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                        "La contraseña debe tener al menos 6 caracteres");
                return "redirect:/admin/employees/" + id + "/edit";
            }
            
            // Get employee
            Employee employee = employeeService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado"));
            
            // Update password using dedicated method that hashes it
            employeeService.changePassword(id, newPassword);
            
            log.info("Password changed successfully for employee: {}", employee.getFullName());
            redirectAttributes.addFlashAttribute("successMessage", 
                    "Contraseña actualizada exitosamente para " + employee.getFullName());
            redirectAttributes.addFlashAttribute("showPasswordSuccessAlert", true);
            return "redirect:/admin/employees/" + id + "/edit";
            
        } catch (IllegalArgumentException e) {
            log.error("Error changing password: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/employees/" + id + "/edit";
            
        } catch (Exception e) {
            log.error("Error changing employee password", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Error al cambiar la contraseña: " + e.getMessage());
            return "redirect:/admin/employees/" + id + "/edit";
        }
    }

    /**
     * Toggle employee enabled status
     * Only ADMIN can activate/deactivate employees
     */
    @PostMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @ResponseBody
    public Map<String, Object> toggleEmployeeStatus(
            @PathVariable Long id,
            @RequestParam boolean enabled,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String currentUsername = authentication.getName();
            employeeService.setEnabled(id, enabled, currentUsername);
            
            response.put("success", true);
            response.put("message", enabled ? "Empleado activado" : "Empleado desactivado");
            response.put("enabled", enabled);
            
        } catch (IllegalStateException e) {
            // Handle user limit exceeded error
            log.error("User limit exceeded when toggling employee status", e);
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Error toggling employee status", e);
            response.put("success", false);
            response.put("message", "Error al cambiar el estado del empleado");
        }
        
        return response;
    }

    /**
     * Get employee details for modal display
     */
    @GetMapping("/{id}/details")
    @ResponseBody
    public Map<String, Object> getEmployeeDetails(@PathVariable Long id, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Employee employee = employeeService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado"));
            
            // Check permissions: Manager can only view details of supervised employees
            Employee currentUser = employeeService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Usuario actual no encontrado"));
            
            if (currentUser.hasRole(Role.MANAGER)) {
                 // Check if it's the manager themselves (unlikely but possible) or a supervised employee
                 boolean isMyEmployee = employee.getSupervisor() != null && 
                                        employee.getSupervisor().getIdEmpleado().equals(currentUser.getIdEmpleado());
                 
                 if (!isMyEmployee) { // If manager wants to see their own details, we might allow it, but requirement implies supervised only.
                                      // Actually usually self-detail is fine. Let's assume supervised check is what's important for "others".
                                      // If id == currentUser.getId(), allow.
                     if (!employee.getIdEmpleado().equals(currentUser.getIdEmpleado())) {
                         throw new org.springframework.security.access.AccessDeniedException("No tiene permiso para ver detalles de este empleado");
                     }
                 }
            }
            
            Map<String, Object> employeeData = new HashMap<>();
            employeeData.put("id", employee.getIdEmpleado());
            employeeData.put("fullName", employee.getFullName());
            employeeData.put("username", employee.getUsername());
            employeeData.put("edad", employee.getEdad());
            employeeData.put("telefono", employee.getTelefono());
            employeeData.put("salario", employee.getFormattedSalary());
            employeeData.put("roleName", employee.getRoleDisplayName());
            employeeData.put("supervisorName", employee.getSupervisorName());
            employeeData.put("shiftNames", employee.getShiftNames());
            employeeData.put("lastAccess", employee.getLastAccess() != null ? dateTimeService.formatToCompanyTime(employee.getLastAccess(), "dd/MM/yyyy HH:mm") : "Nunca");
            employeeData.put("enabled", employee.getEnabled());
            employeeData.put("enabledText", employee.getEnabled() ? "Activo" : "Inactivo");
            
            response.put("success", true);
            response.put("employee", employeeData);
            
        } catch (Exception e) {
            log.error("Error getting employee details", e);
            response.put("success", false);
            response.put("message", "Error al obtener los detalles del empleado");
        }
        
        return response;
    }

    /**
     * Check if phone number is already registered
     */
    @GetMapping("/check-phone")
    @ResponseBody
    public Map<String, Object> checkPhoneAvailability(
            @RequestParam String telefono,
            @RequestParam(required = false) Long employeeId) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // If phone is empty, it's valid
            if (telefono == null || telefono.trim().isEmpty()) {
                response.put("available", true);
                return response;
            }
            
            // Check if phone exists
            Optional<Employee> existingEmployee = employeeRepository.findByTelefono(telefono);
            
            if (existingEmployee.isPresent()) {
                // If it's the same employee being edited, it's valid
                if (employeeId != null && existingEmployee.get().getIdEmpleado().equals(employeeId)) {
                    response.put("available", true);
                } else {
                    response.put("available", false);
                    response.put("message", "El teléfono ya está registrado");
                }
            } else {
                response.put("available", true);
            }
            
        } catch (Exception e) {
            log.error("Error checking phone availability", e);
            response.put("available", false);
            response.put("message", "Error al verificar el teléfono");
        }
        
        return response;
    }

    /**
     * Delete an employee
     */
    @PostMapping("/{id}/delete")
    public String deleteEmployee(@PathVariable Long id, RedirectAttributes redirectAttributes, Authentication authentication) {
        log.info("Deleting employee with ID: {}", id);
        
        // Validation: Manager can only delete their own supervised employees (if they have delete permission)
        Employee currentUser = employeeService.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Usuario actual no encontrado"));
                
        if (currentUser.hasRole(Role.MANAGER)) {
             Employee existingEmployee = employeeService.findById(id).orElse(null);
             if (existingEmployee != null) {
                 boolean isMyEmployee = existingEmployee.getSupervisor() != null && 
                                        existingEmployee.getSupervisor().getIdEmpleado().equals(currentUser.getIdEmpleado());
                 
                 if (!isMyEmployee) {
                     log.warn("Manager {} tried to delete employee {} without supervision rights", currentUser.getUsername(), id);
                     redirectAttributes.addFlashAttribute("errorMessage", "No tiene permiso para eliminar este empleado.");
                     return "redirect:/admin/employees";
                 }
             }
        }
        
        try {
            Optional<Employee> employeeOpt = employeeService.findById(id);
            if (employeeOpt.isPresent()) {
                Employee employee = employeeOpt.get();
                
                // Check if employee has admin role
                if (employee.hasRole(Role.ADMIN)) {
                    redirectAttributes.addFlashAttribute("errorMessage", 
                            "No se puede eliminar un administrador. Los administradores no pueden ser eliminados por seguridad del sistema.");
                    return "redirect:/admin/employees";
                }
                
                employeeService.delete(id);
                redirectAttributes.addFlashAttribute("successMessage", 
                        "Empleado eliminado exitosamente");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Empleado no encontrado");
            }
            
        } catch (Exception e) {
            log.error("Error deleting employee", e);
            // Check if it's a constraint violation (employee has associated records)
            String errorMessage = e.getMessage();
            if (errorMessage != null && (errorMessage.contains("constraint") || 
                errorMessage.contains("foreign key") || 
                errorMessage.contains("referencia"))) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                        "No se puede eliminar este empleado debido a que ya cuenta con registros asociados (órdenes, turnos, supervisiones, etc.). Puedes deshabilitarlo en su lugar.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", 
                        "Error al eliminar el empleado: " + e.getMessage());
            }
        }
        
        return "redirect:/admin/employees";
    }

    /**
     * Helper method to prepare form model attributes
     */
    private void prepareFormModel(Model model, Employee employee, boolean isEdit) {
        List<Role> availableRoles = roleRepository.findAll().stream()
                .filter(r -> !r.getNombreRol().equals(Role.ADMIN))
                .collect(Collectors.toList());
        
        List<Shift> availableShifts = shiftService.getAllActiveShifts();
        List<Employee> supervisors = employeeService.findAll().stream()
                .filter(e -> e.hasRole(Role.ADMIN) || e.hasRole(Role.MANAGER))
                .collect(Collectors.toList());
        
        // Check if employee is ADMIN (for edit mode)
        boolean isAdminEmployee = isEdit && employee.getIdEmpleado() != null && employee.hasRole(Role.ADMIN);
        
        model.addAttribute("availableRoles", availableRoles);
        model.addAttribute("shifts", availableShifts);
        model.addAttribute("supervisors", supervisors);
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("isAdminEmployee", isAdminEmployee);
        
        String formAction = isEdit && employee.getIdEmpleado() != null 
            ? "/admin/employees/" + employee.getIdEmpleado() 
            : "/admin/employees";
        model.addAttribute("formAction", formAction);
        
        // Add license limit information (for new employee form)
        if (!isEdit) {
            long currentActiveUsers = employeeService.countEnabled();
            Integer maxUsers = licenseService.getMaxUsers();
            boolean canCreateMore = licenseService.canCreateMoreUsers(currentActiveUsers);
            
            model.addAttribute("maxUsers", maxUsers);
            model.addAttribute("currentActiveUsers", currentActiveUsers);
            model.addAttribute("hasUserLimit", maxUsers != null);
            model.addAttribute("canCreateMore", canCreateMore);
        }
    }

    /**
     * API endpoint to check user limit status.
     * Used by the frontend to determine if the limit issue has been resolved
     * (after the ADMIN deactivates enough employees).
     */
    @GetMapping("/user-limit-status")
    @ResponseBody
    public Map<String, Object> getUserLimitStatus(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        long currentActiveUsers = employeeService.countEnabled();
        Integer maxUsers = licenseService.getMaxUsers();
        boolean limitExceeded = licenseService.isUserLimitExceeded(currentActiveUsers);
        int excess = licenseService.getExcessUserCount(currentActiveUsers);
        
        response.put("limitExceeded", limitExceeded);
        response.put("currentActiveUsers", currentActiveUsers);
        response.put("maxUsers", maxUsers);
        response.put("excessCount", excess);
        
        // If limit is no longer exceeded, clear session attributes
        if (!limitExceeded) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.removeAttribute("userLimitExceeded");
                session.removeAttribute("userLimitExceededInfo");
                session.removeAttribute("userLimitMax");
                session.removeAttribute("userLimitCurrent");
                session.removeAttribute("userLimitExcess");
            }
        }
        
        return response;
    }
}
