package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.SystemUpdateService;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.infrastructure.security.CustomEmployeeDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Marks the current employee's update announcement as seen.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class SystemUpdateController {

    private final SystemUpdateService systemUpdateService;
    private final EmployeeRepository employeeRepository;

    @PostMapping("/system-updates/{id}/seen")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public ResponseEntity<Void> markSeen(@PathVariable Long id, Authentication authentication) {
        Long employeeId = currentEmployeeId(authentication);
        if (employeeId != null) {
            systemUpdateService.markSeen(id, employeeId);
        }
        return ResponseEntity.noContent().build();
    }

    private Long currentEmployeeId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof CustomEmployeeDetails details) {
            return details.getEmployeeId();
        }
        return employeeRepository.findByUsername(authentication.getName())
                .map(Employee::getIdEmpleado)
                .orElse(null);
    }
}
