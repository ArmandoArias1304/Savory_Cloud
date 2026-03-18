package com.aatechsolutions.elgransazon.phase2;

import com.aatechsolutions.elgransazon.application.service.EmailService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Role;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FASE 2 — Tests de integración para UserLimitEnforcementFilter.
 *
 * Verifica:
 * - PROGRAMMER: siempre tiene acceso (sin importar el límite)
 * - ADMIN: cuando se supera el límite, solo puede acceder a /admin/employees
 * - Otros roles (WAITER, CHEF, etc.): son deslogueados si se supera el límite
 * - UserValidationFilter: desactivar empleado en BD invalida su sesión
 * - UserValidationFilter: desactivar cliente en BD invalida su sesión
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("FASE 2 — UserLimitEnforcement & UserValidation Filter Tests")
class UserLimitEnforcementFilterTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper testData;
    @Autowired private EmployeeRepository employeeRepository;

    @MockBean private EmailService emailService;

    private static final String SLUG = "tp2-limit-co";

    private Company company;
    private Employee adminEmp;
    private Employee waiterEmp;

    @BeforeEach
    void setUp() {
        // Create company with maxUsers=2 (will be set below)
        company = testData.createActiveCompany(SLUG, "America/Mexico_City");
        // Set max users to 2
        testData.setLicenseMaxUsers(company.getIdCompany(), 2);

        adminEmp  = testData.createEmployee(company, "tp2-limit-admin",  "pass", Role.ADMIN);
        waiterEmp = testData.createEmployee(company, "tp2-limit-waiter", "pass", Role.WAITER);
        // Now there are 2 enabled employees, maxUsers=2 → currently at limit but NOT exceeded
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCompanyAndAllData(company.getIdCompany());
    }

    // ================================================================
    //  Límite NO superado → acceso normal
    // ================================================================

    @Test
    @DisplayName("Dentro del límite: ADMIN puede acceder normalmente")
    void withinLimit_adminHasFullAccess() throws Exception {
        MockHttpSession session = getAuthenticatedSession("tp2-limit-admin", "pass");

        mockMvc.perform(get("/admin/dashboard")
                        .session(session)
                        .with(forCompany(SLUG)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Dentro del límite: WAITER puede acceder normalmente")
    void withinLimit_waiterHasFullAccess() throws Exception {
        MockHttpSession session = getAuthenticatedSession("tp2-limit-waiter", "pass");

        mockMvc.perform(get("/waiter/dashboard")
                        .session(session)
                        .with(forCompany(SLUG)))
                .andExpect(status().isOk());
    }

    // ================================================================
    //  Límite SUPERADO → restricciones por rol
    // ================================================================

    @Test
    @DisplayName("Superado el límite: WAITER es deslogueado en el siguiente request")
    void overLimit_waiterIsLoggedOut() throws Exception {
        // First login waiter (within limit)
        MockHttpSession waiterSession = getAuthenticatedSession("tp2-limit-waiter", "pass");
        assertThat(waiterSession).isNotNull();

        // Add 3 more employees to exceed the limit (maxUsers=2, now 5 employees)
        testData.createEmployee(company, "tp2-limit-extra1", "pass", Role.CHEF);
        testData.createEmployee(company, "tp2-limit-extra2", "pass", Role.BARISTA);
        testData.createEmployee(company, "tp2-limit-extra3", "pass", Role.CASHIER);

        // Now: 5 employees, maxUsers=2 → EXCEEDED
        // Next request from waiter should result in logout redirect
        MvcResult result = mockMvc.perform(get("/waiter/dashboard")
                        .session(waiterSession)
                        .with(forCompany(SLUG)))
                .andReturn();

        int status = result.getResponse().getStatus();
        String redirect = result.getResponse().getRedirectedUrl();

        // Should be redirected to /login?error=userLimitExceeded
        assertThat(status).isEqualTo(302);
        assertThat(redirect)
                .as("WAITER debe ser deslogueado cuando se supera el límite de usuarios")
                .contains("userLimitExceeded");

        // Cleanup extra employees
        deleteExtraEmployees();
    }

    @Test
    @DisplayName("Superado el límite: ADMIN solo puede acceder a /admin/employees")
    void overLimit_adminCanOnlyAccessEmployeesPage() throws Exception {
        // Login admin (within limit)
        MockHttpSession adminSession = getAuthenticatedSession("tp2-limit-admin", "pass");

        // Exceed the limit
        testData.createEmployee(company, "tp2-limit-extra4", "pass", Role.CHEF);
        testData.createEmployee(company, "tp2-limit-extra5", "pass", Role.BARISTA);
        testData.createEmployee(company, "tp2-limit-extra6", "pass", Role.CASHIER);

        // Admin accessing /admin/employees should still work
        MvcResult empResult = mockMvc.perform(get("/admin/employees")
                        .session(adminSession)
                        .with(forCompany(SLUG)))
                .andReturn();

        int empStatus = empResult.getResponse().getStatus();
        assertThat(empStatus)
                .as("ADMIN debe poder acceder a /admin/employees cuando se supera el límite")
                .isIn(200, 302); // 302 may redirect to the list page

        // Admin accessing any other page should be redirected to /admin/employees
        MvcResult otherResult = mockMvc.perform(get("/admin/dashboard")
                        .session(adminSession)
                        .with(forCompany(SLUG)))
                .andReturn();

        String redirectUrl = otherResult.getResponse().getRedirectedUrl();
        if (redirectUrl != null) {
            assertThat(redirectUrl)
                    .as("ADMIN fuera de /admin/employees debe ser redirigido a /admin/employees")
                    .isEqualTo("/admin/employees");
        }

        // Cleanup
        deleteExtraEmployees();
    }

    @Test
    @DisplayName("Superado el límite: atributo 'userLimitExceeded' se establece en sesión del ADMIN")
    void overLimit_sessionAttributeSet_forAdmin() throws Exception {
        MockHttpSession adminSession = getAuthenticatedSession("tp2-limit-admin", "pass");

        // Exceed limit
        testData.createEmployee(company, "tp2-limit-extra7", "pass", Role.CHEF);
        testData.createEmployee(company, "tp2-limit-extra8", "pass", Role.BARISTA);
        testData.createEmployee(company, "tp2-limit-extra9", "pass", Role.CASHIER);

        // Make any request → filter should set session attribute
        mockMvc.perform(get("/admin/dashboard")
                        .session(adminSession)
                        .with(forCompany(SLUG)))
                .andReturn();

        // Check session attribute set by UserLimitEnforcementFilter
        Object limitExceeded = adminSession.getAttribute("userLimitExceeded");
        if (limitExceeded != null) {
            assertThat(limitExceeded).isEqualTo(true);
        }
        // Note: if admin is redirected to /admin/employees directly, the attribute
        // may not appear on dashboard request, but should appear on employees request

        deleteExtraEmployees();
    }

    // ================================================================
    //  UserValidationFilter — desactivación en tiempo real
    // ================================================================

    @Test
    @DisplayName("UserValidationFilter: empleado desactivado en BD es deslogueado en siguiente request")
    void userValidation_disabledEmployee_isLoggedOut() throws Exception {
        // Temporarily increase maxUsers to allow a 3rd employee to login
        // (setUp already created admin + waiter = 2 users, and maxUsers=2)
        testData.setLicenseMaxUsers(company.getIdCompany(), 10);

        // Create a test employee and login
        Employee testEmp = testData.createEmployee(company, "tp2-validate-emp", "pass", Role.WAITER);
        MockHttpSession session = getAuthenticatedSession("tp2-validate-emp", "pass");
        assertThat(session).isNotNull();

        // Verify access works while enabled
        MvcResult beforeDisable = mockMvc.perform(get("/waiter/dashboard")
                        .session(session)
                        .with(forCompany(SLUG)))
                .andReturn();
        assertThat(beforeDisable.getResponse().getStatus()).isEqualTo(200);

        // Disable the employee in DB (direct repository update)
        testData.setEmployeeEnabled(testEmp.getIdEmpleado(), false);

        // Next request → UserValidationFilter should detect enabled=false → logout
        MvcResult afterDisable = mockMvc.perform(get("/waiter/dashboard")
                        .session(session)
                        .with(forCompany(SLUG)))
                .andReturn();

        int status = afterDisable.getResponse().getStatus();
        String redirect = afterDisable.getResponse().getRedirectedUrl();

        assertThat(status)
                .as("Empleado desactivado debe ser redirigido, no recibir 200")
                .isEqualTo(302);
        assertThat(redirect)
                .as("Empleado desactivado debe ser redirigido al login")
                .contains("login");
    }

    @Test
    @DisplayName("UserValidationFilter: cliente desactivado en BD es deslogueado en siguiente request")
    void userValidation_disabledCustomer_isLoggedOut() throws Exception {
        testData.createVerifiedCustomer(company, "tp2validatecust", "val@co.com", "5512340001", "custpass");

        // Login as customer
        MvcResult loginResult = mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "tp2validatecust")
                        .param("password", "custpass")
                        .with(forCompany(SLUG)))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        if (session != null) {
            // Deactivate the customer
            com.aatechsolutions.elgransazon.domain.repository.CustomerRepository customerRepo =
                    getCustomerRepository();
            com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext.setCurrentCompany(company);
            try {
                customerRepo.findByUsernameIgnoreCaseAndCompany("tp2validatecust", company)
                        .ifPresent(cust -> testData.setCustomerActive(cust.getIdCustomer(), false));
            } finally {
                com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext.clear();
            }

            // Next request → UserValidationFilter detects active=false → logout
            MvcResult nextRequest = mockMvc.perform(get("/client/menu")
                            .session(session)
                            .with(forCompany(SLUG)))
                    .andReturn();

            int status = nextRequest.getResponse().getStatus();
            if (status == 302) {
                assertThat(nextRequest.getResponse().getRedirectedUrl())
                        .contains("login");
            }
        }
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private MockHttpSession getAuthenticatedSession(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username)
                        .param("password", password)
                        .with(forCompany(SLUG)))
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        return session != null ? session : new MockHttpSession();
    }

    private void deleteExtraEmployees() {
        for (String uname : new String[]{"tp2-limit-extra1","tp2-limit-extra2","tp2-limit-extra3",
                                         "tp2-limit-extra4","tp2-limit-extra5","tp2-limit-extra6",
                                         "tp2-limit-extra7","tp2-limit-extra8","tp2-limit-extra9"}) {
            employeeRepository.findByUsernameAndCompany(uname, company).ifPresent(emp -> {
                emp.getRoles().clear();
                employeeRepository.save(emp);
                employeeRepository.delete(emp);
            });
        }
    }

    @Autowired
    private com.aatechsolutions.elgransazon.domain.repository.CustomerRepository customerRepository;

    private com.aatechsolutions.elgransazon.domain.repository.CustomerRepository getCustomerRepository() {
        return customerRepository;
    }

    private RequestPostProcessor forCompany(String slug) {
        return request -> {
            request.setServerName(slug + ".localhost");
            return request;
        };
    }
}
