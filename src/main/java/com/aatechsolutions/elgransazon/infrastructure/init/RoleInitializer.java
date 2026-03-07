package com.aatechsolutions.elgransazon.infrastructure.init;

import com.aatechsolutions.elgransazon.domain.entity.Role;
import com.aatechsolutions.elgransazon.domain.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Role Initializer
 * Creates default system roles if they don't exist
 * Runs at application startup
 */
@Component
@Order(2) // Runs after LicenseInitializer (Order 1)
@RequiredArgsConstructor
@Slf4j
public class RoleInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;

    // Define all system roles
    private static final List<String> SYSTEM_ROLES = Arrays.asList(
            Role.ADMIN,
            Role.MANAGER,
            Role.WAITER,
            Role.CHEF,
            Role.BARISTA,
            Role.CASHIER,
            Role.DELIVERY,
            Role.CLIENT,
            Role.PROGRAMMER
    );

    @Override
    @Transactional
    public void run(String... args) {
        log.info("🔐 Initializing system roles...");

        int createdCount = 0;
        int existingCount = 0;

        for (String roleName : SYSTEM_ROLES) {
            if (!roleRepository.existsByNombreRol(roleName)) {
                Role role = new Role(roleName);
                roleRepository.save(role);
                log.info("✅ Created role: {}", roleName);
                createdCount++;
            } else {
                log.debug("⏭️  Role already exists: {}", roleName);
                existingCount++;
            }
        }

        log.info("🎭 Role initialization completed. Created: {}, Already existed: {}, Total: {}", 
                createdCount, existingCount, SYSTEM_ROLES.size());
    }
}
