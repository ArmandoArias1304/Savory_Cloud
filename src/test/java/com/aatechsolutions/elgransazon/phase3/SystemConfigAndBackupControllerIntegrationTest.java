package com.aatechsolutions.elgransazon.phase3;

import com.aatechsolutions.elgransazon.application.service.EmailService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Role;
import com.aatechsolutions.elgransazon.support.TestDataHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FASE 3 — Tests de integración para SystemConfigurationController y BackupController.
 *
 * Verifica:
 * - Solo ADMIN puede ver/modificar la configuración del sistema
 * - MANAGER no puede acceder a /admin/system-configuration
 * - Solo PROGRAMMER puede acceder a /programmer/backup
 * - ADMIN no puede acceder al área de PROGRAMMER
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("FASE 3 — SystemConfig & Backup Controller Integration Tests")
class SystemConfigAndBackupControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper testData;

    @MockBean private EmailService emailService;

    private static final String SLUG_C = "tp3-cfg-c1";

    private Company company;
    private Employee adminC;
    private Employee managerC;
    private Employee programmerEmp;

    @BeforeEach
    void setUp() {
        // Pre-cleanup: remove orphans from any previous failed run
        testData.cleanUpBySlug(SLUG_C);
        testData.cleanUpGlobalEmployee("tp3-prog-cfg");

        company  = testData.createActiveCompany(SLUG_C, "America/Mexico_City");
        adminC   = testData.createEmployee(company, "tp3-cadmin-c1", "pass", Role.ADMIN);
        managerC = testData.createEmployee(company, "tp3-cmgr-c1",   "pass", Role.MANAGER);

        // PROGRAMMER is global — company=null, accessed via localhost (no slug)
        programmerEmp = testData.createEmployee(null, "tp3-prog-cfg", "pass", Role.PROGRAMMER);
    }

    @AfterEach
    void cleanUp() {
        if (company != null) testData.deleteCompanyAndAllData(company.getIdCompany());
        if (programmerEmp != null) {
            testData.deleteEmployee(programmerEmp.getIdEmpleado());
        }
    }

    // ================================================================
    //  SystemConfigurationController — acceso
    // ================================================================

    @Test
    @DisplayName("ADMIN puede ver la configuración del sistema")
    void admin_canViewSystemConfig() throws Exception {
        mockMvc.perform(get("/admin/system-configuration")
                        .with(user("tp3-cadmin-c1").roles("ADMIN"))
                        .with(forCompany(SLUG_C)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("MANAGER no puede ver la configuración del sistema (ADMIN-only)")
    void manager_cannotViewSystemConfig() throws Exception {
        // SystemConfigurationController is @PreAuthorize("hasRole('ROLE_ADMIN')") at class level
        mockMvc.perform(get("/admin/system-configuration")
                        .with(user("tp3-cmgr-c1").roles("MANAGER"))
                        .with(forCompany(SLUG_C)))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    //  BackupController — acceso PROGRAMMER-only
    // ================================================================

    @Test
    @DisplayName("PROGRAMMER puede acceder a la gestión de backups")
    void programmer_canAccessBackupPage() throws Exception {
        mockMvc.perform(get("/programmer/backup")
                        .with(user("tp3-prog-cfg").roles("PROGRAMMER"))
                        .with(localhost()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN no puede acceder al área de backup del PROGRAMMER")
    void admin_cannotAccessBackupPage() throws Exception {
        // ADMIN tries to access PROGRAMMER endpoint — Spring Security redirects to access-denied (302)
        mockMvc.perform(get("/programmer/backup")
                        .with(user("tp3-cadmin-c1").roles("ADMIN"))
                        .with(forCompany(SLUG_C)))
                .andExpect(status().is3xxRedirection());
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private RequestPostProcessor forCompany(String slug) {
        return request -> {
            request.setServerName(slug + ".localhost");
            return request;
        };
    }

    private RequestPostProcessor localhost() {
        return request -> {
            request.setServerName("localhost");
            return request;
        };
    }
}
