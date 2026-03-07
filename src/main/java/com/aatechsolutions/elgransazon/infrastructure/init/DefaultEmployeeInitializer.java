package com.aatechsolutions.elgransazon.infrastructure.init;

import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Role;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.domain.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Default Employee Initializer
 * Creates ONLY the global PROGRAMMER employee if none exists.
 * MULTI-TENANT: ADMIN is created per-company by CompanyService.create()
 * Runs after RoleInitializer (Order 2) to ensure roles are available.
 */
@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class DefaultEmployeeInitializer implements CommandLineRunner {

    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("🔍 Checking if default PROGRAMMER needs to be created...");

        boolean programmerCreated = createDefaultProgrammer();

        if (!programmerCreated) {
            log.info("👤 Default PROGRAMMER already exists. Skipping initialization.");
        } else {
            log.info("👤 Default PROGRAMMER initialization completed.");
        }
    }

    /**
     * Creates the default PROGRAMMER employee if no employee with ROLE_PROGRAMMER exists.
     * PROGRAMMER is global (no company_id) and manages all companies.
     */
    private boolean createDefaultProgrammer() {
        // Check if any employee with PROGRAMMER role already exists
        boolean programmerExists = employeeRepository.findAll().stream()
                .anyMatch(e -> e.getRoles().stream()
                        .anyMatch(r -> r.getNombreRol().equals(Role.PROGRAMMER)));

        if (programmerExists) {
            log.debug("⏭️  Programmer employee already exists. Skipping.");
            return false;
        }

        Role programmerRole = roleRepository.findByNombreRol(Role.PROGRAMMER)
                .orElseThrow(() -> new IllegalStateException("ROLE_PROGRAMMER not found. Ensure RoleInitializer runs first."));

        Set<Role> roles = new HashSet<>();
        roles.add(programmerRole);

        // PROGRAMMER is global - no company_id and no supervisor
        Employee programmer = Employee.builder()
                .company(null) // PROGRAMMER is global, not tied to any company
                .username("programador")
                .nombre("Programador")
                .apellido("Sistema")
                .edad(25)
                .contrasenia(passwordEncoder.encode("programador1234"))
                .telefono(null)
                .salario(0.0)
                .enabled(true)
                .roles(roles)
                .supervisor(null) // PROGRAMMER has no supervisor
                .createdBy("SYSTEM")
                .updatedBy("SYSTEM")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        employeeRepository.save(programmer);
        log.info("✅ Default PROGRAMMER employee created (username: programador)");
        return true;
    }
}
