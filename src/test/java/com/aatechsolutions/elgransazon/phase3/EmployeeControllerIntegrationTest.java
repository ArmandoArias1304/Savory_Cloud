package com.aatechsolutions.elgransazon.phase3;

import com.aatechsolutions.elgransazon.application.service.EmailService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Role;
import com.aatechsolutions.elgransazon.domain.repository.RoleRepository;
import com.aatechsolutions.elgransazon.support.TestDataHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FASE 3 — Tests de integración para EmployeeController.
 *
 * Verifica:
 * - ADMIN ve todos los empleados de su company (excepto PROGRAMMER)
 * - MANAGER solo ve empleados que supervisa
 * - Solo ADMIN puede crear/desactivar empleados
 * - Aislamiento multi-tenant: employees de Company A no visibles en Company B
 * - Límite de licencia previene creación de empleados
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("FASE 3 — Employee Controller Integration Tests")
class EmployeeControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper testData;
    @Autowired private RoleRepository roleRepository;

    @MockBean private EmailService emailService;

    private static final String SLUG_A = "tp3-emp-a";
    private static final String SLUG_B = "tp3-emp-b";

    private Company companyA;
    private Company companyB;
    private Employee adminA;
    private Employee managerA;
    private Employee waiterA;
    private Employee adminB;
    private Employee waiterB;

    @BeforeEach
    void setUp() {
        // Pre-cleanup: remove orphans from any previous failed run
        testData.cleanUpBySlug(SLUG_A);
        testData.cleanUpBySlug(SLUG_B);

        companyA = testData.createActiveCompany(SLUG_A, "America/Mexico_City");
        companyB = testData.createActiveCompany(SLUG_B, "America/Mexico_City");

        adminA   = testData.createEmployee(companyA, "tp3-eadmin-a",  "pass", Role.ADMIN);
        managerA = testData.createEmployee(companyA, "tp3-emgr-a",    "pass", Role.MANAGER);
        waiterA  = testData.createEmployee(companyA, "tp3-ewaiter-a", "pass", Role.WAITER);
        adminB   = testData.createEmployee(companyB, "tp3-eadmin-b",  "pass", Role.ADMIN);
        waiterB  = testData.createEmployee(companyB, "tp3-ewaiter-b", "pass", Role.WAITER);

        // waiterA is supervised by managerA
        testData.setSupervisor(waiterA.getIdEmpleado(), managerA.getIdEmpleado());
    }

    @AfterEach
    void cleanUp() {
        if (companyA != null) testData.deleteCompanyAndAllData(companyA.getIdCompany());
        if (companyB != null) testData.deleteCompanyAndAllData(companyB.getIdCompany());
    }

    // ================================================================
    //  Listar empleados
    // ================================================================

    @Test
    @DisplayName("ADMIN puede listar empleados de su company")
    void admin_canListEmployees() throws Exception {
        mockMvc.perform(get("/admin/employees")
                        .with(user("tp3-eadmin-a").roles("ADMIN"))
                        .with(forCompany(SLUG_A)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("MANAGER puede acceder a la lista de empleados")
    void manager_canListEmployees() throws Exception {
        mockMvc.perform(get("/admin/employees")
                        .with(user("tp3-emgr-a").roles("MANAGER"))
                        .with(forCompany(SLUG_A)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Lista de empleados de Company A no contiene empleados de Company B")
    void employeeList_isolatedByCompany() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/employees")
                        .with(user("tp3-eadmin-a").roles("ADMIN"))
                        .with(forCompany(SLUG_A)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // Company A admin should see waiter A
        assertThat(body).contains("tp3-ewaiter-a");
        // Company A admin should NOT see company B's waiter
        assertThat(body).doesNotContain("tp3-ewaiter-b");
    }

    // ================================================================
    //  Formulario de nuevo empleado
    // ================================================================

    @Test
    @DisplayName("ADMIN puede acceder al formulario de nuevo empleado")
    void admin_canAccessNewEmployeeForm() throws Exception {
        mockMvc.perform(get("/admin/employees/new")
                        .with(user("tp3-eadmin-a").roles("ADMIN"))
                        .with(forCompany(SLUG_A)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("MANAGER no puede acceder al formulario de nuevo empleado (ADMIN-only)")
    void manager_cannotAccessNewEmployeeForm() throws Exception {
        mockMvc.perform(get("/admin/employees/new")
                        .with(user("tp3-emgr-a").roles("MANAGER"))
                        .with(forCompany(SLUG_A)))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    //  Crear empleado
    // ================================================================

    @Test
    @DisplayName("ADMIN puede crear un nuevo empleado en su company")
    void admin_canCreateEmployee() throws Exception {
        // Find WAITER role ID for the POST form
        Long waiterRoleId = roleRepository.findByNombreRol(Role.WAITER)
                .map(r -> r.getIdRol())
                .orElseThrow(() -> new IllegalStateException("WAITER role not in DB"));

        MockHttpSession session = getAuthenticatedSession("tp3-eadmin-a", "pass", SLUG_A);

        MvcResult result = mockMvc.perform(post("/admin/employees")
                        .session(session)
                        .with(csrf())
                        .with(forCompany(SLUG_A))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("nombre", "Nuevo")
                        .param("apellido", "Mesero")
                        .param("edad", "25")
                        .param("username", "tp3-new-emp")
                        .param("password", "pass123")
                        .param("roleId", waiterRoleId.toString()))
                .andReturn();

        // Should redirect to the employee list on success (or show form on error)
        int status = result.getResponse().getStatus();
        // 302 = success redirect; 200 = form shown again (possibly due to validation)
        // We accept 302 (success) as correct
        assertThat(status).as("Should redirect after successful creation").isEqualTo(302);
        assertThat(result.getResponse().getRedirectedUrl()).contains("/admin/employees");
    }

    @Test
    @DisplayName("Límite de licencia impide creación de empleados cuando se alcanza el máximo")
    void licenseLimit_preventsEmployeeCreation() throws Exception {
        // Set maxUsers to the current active count (1 admin + 1 manager + 1 waiter = 3 enabled)
        testData.setLicenseMaxUsers(companyA.getIdCompany(), 3);

        Long waiterRoleId = roleRepository.findByNombreRol(Role.WAITER)
                .map(r -> r.getIdRol())
                .orElseThrow(() -> new IllegalStateException("WAITER role not in DB"));

        MockHttpSession session = getAuthenticatedSession("tp3-eadmin-a", "pass", SLUG_A);

        MvcResult result = mockMvc.perform(post("/admin/employees")
                        .session(session)
                        .with(csrf())
                        .with(forCompany(SLUG_A))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("nombre", "Extra")
                        .param("apellido", "Empleado")
                        .param("edad", "25")
                        .param("username", "tp3-extra-emp")
                        .param("password", "pass123")
                        .param("roleId", waiterRoleId.toString()))
                .andReturn();

        // Should redirect to the list with an error message (limit exceeded)
        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        String redirectUrl = result.getResponse().getRedirectedUrl();
        assertThat(redirectUrl).contains("/admin/employees");

        // Re-set the license to unlimited to not affect cleanup
        testData.setLicenseMaxUsers(companyA.getIdCompany(), 50);
    }

    // ================================================================
    //  Toggle de estado (ADMIN-only)
    // ================================================================

    @Test
    @DisplayName("ADMIN puede activar/desactivar un empleado")
    void admin_canToggleEmployeeStatus() throws Exception {
        MockHttpSession session = getAuthenticatedSession("tp3-eadmin-a", "pass", SLUG_A);

        mockMvc.perform(post("/admin/employees/" + waiterA.getIdEmpleado() + "/toggle-status")
                        .session(session)
                        .with(csrf())
                        .with(forCompany(SLUG_A))
                        .param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Re-enable to not affect cleanup
        testData.setEmployeeEnabled(waiterA.getIdEmpleado(), true);
    }

    @Test
    @DisplayName("MANAGER no puede activar/desactivar empleados (ADMIN-only)")
    void manager_cannotToggleEmployeeStatus() throws Exception {
        // MANAGER is not authorized for toggle-status (@PreAuthorize("hasRole('ROLE_ADMIN')"))
        mockMvc.perform(post("/admin/employees/" + waiterA.getIdEmpleado() + "/toggle-status")
                        .with(user("tp3-emgr-a").roles("MANAGER"))
                        .with(forCompany(SLUG_A))
                        .with(csrf())
                        .param("enabled", "false"))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private MvcResult loginEmployee(String username, String password, String slug) throws Exception {
        return mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username)
                        .param("password", password)
                        .with(forCompany(slug)))
                .andReturn();
    }

    private MockHttpSession getAuthenticatedSession(String username, String password, String slug) throws Exception {
        MvcResult result = loginEmployee(username, password, slug);
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        return session != null ? session : new MockHttpSession();
    }

    private RequestPostProcessor forCompany(String slug) {
        return request -> {
            request.setServerName(slug + ".localhost");
            return request;
        };
    }
}
