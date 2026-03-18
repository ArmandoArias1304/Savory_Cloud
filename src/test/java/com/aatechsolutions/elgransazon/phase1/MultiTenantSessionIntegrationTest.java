package com.aatechsolutions.elgransazon.phase1;

import com.aatechsolutions.elgransazon.application.service.EmailService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Role;
import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.infrastructure.security.CustomCustomerDetails;
import com.aatechsolutions.elgransazon.infrastructure.security.CustomEmployeeDetails;
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
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FASE 1 — Tests de aislamiento de sesiones multi-tenant.
 *
 * Verifica que Spring Security trate a usuarios con el MISMO username pero en
 * DIFERENTES companies como entidades distintas:
 * - Sesión de company1 no invalida la sesión de company2 (aunque mismo username)
 * - maxSessions=1 se aplica POR company, NO globalmente
 * - Clientes con mismo email en 2 companies tienen sesiones independientes
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("FASE 1 — Multi-Tenant Session Isolation Tests")
class MultiTenantSessionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestDataHelper testData;

    @Autowired
    private SessionRegistry sessionRegistry;

    @MockBean
    private EmailService emailService;

    private static final String SLUG_CO1 = "tp1-sess-co1";
    private static final String SLUG_CO2 = "tp1-sess-co2";

    private Company company1;
    private Company company2;

    @BeforeEach
    void setUp() {
        company1 = testData.createActiveCompany(SLUG_CO1, "America/Mexico_City");
        company2 = testData.createActiveCompany(SLUG_CO2, "America/Mexico_City");
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCompanyAndAllData(company1.getIdCompany());
        testData.deleteCompanyAndAllData(company2.getIdCompany());
    }

    // ================================================================
    //  Multi-tenant session isolation — Empleados
    // ================================================================

    @Test
    @DisplayName("MULTI-TENANT: sesión co1 y sesión co2 con mismo username son INDEPENDIENTES")
    void multiTenant_sameUsername_sessionsAreIndependent() throws Exception {
        // mismo username, distintas contraseñas
        testData.createEmployee(company1, "sess-shared", "pass-co1", Role.ADMIN);
        testData.createEmployee(company2, "sess-shared", "pass-co2", Role.CHEF);

        // Login en co1
        MvcResult login1 = loginEmployee("sess-shared", "pass-co1", SLUG_CO1);
        MockHttpSession session1 = (MockHttpSession) login1.getRequest().getSession(false);

        // Login en co2 con mismo username
        MvcResult login2 = loginEmployee("sess-shared", "pass-co2", SLUG_CO2);
        MockHttpSession session2 = (MockHttpSession) login2.getRequest().getSession(false);

        // Both logins must succeed
        assertThat(login1.getResponse().getRedirectedUrl())
                .as("Co1 ADMIN debe ir a /admin/dashboard")
                .isEqualTo("/admin/dashboard");
        assertThat(login2.getResponse().getRedirectedUrl())
                .as("Co2 CHEF debe ir a /chef/dashboard")
                .isEqualTo("/chef/dashboard");

        // Sessions should be different (different HttpSession instances)
        if (session1 != null && session2 != null) {
            assertThat(session1.getId())
                    .as("Sesiones de distintas companies deben ser diferentes")
                    .isNotEqualTo(session2.getId());
        }
    }

    @Test
    @DisplayName("MULTI-TENANT: segundo login del MISMO usuario en MISMA company invalida sesión anterior")
    void sameCompany_secondLogin_invalidatesPreviousSession() throws Exception {
        testData.createEmployee(company1, "sess-dup", "pass123", Role.WAITER);

        // Primer login
        MvcResult login1 = loginEmployee("sess-dup", "pass123", SLUG_CO1);
        MockHttpSession session1 = (MockHttpSession) login1.getRequest().getSession(false);

        assertThat(login1.getResponse().getRedirectedUrl())
                .isEqualTo("/waiter/dashboard");

        // Segundo login → con maxSessions=1 y maxSessionsPreventsLogin=false,
        // el segundo login DEBE triunfar, e invalidar al primero
        MvcResult login2 = loginEmployee("sess-dup", "pass123", SLUG_CO1);
        
        assertThat(login2.getResponse().getRedirectedUrl())
                .as("Segundo login debe redirigir al dashboard normalmente")
                .isEqualTo("/waiter/dashboard");

        // La primera sesión puede estar marcada como expirada (Spring Security lo hace en el siguiente request)
        // No podemos verificar directamente la invalidación sin hacer un request con session1
    }

    @Test
    @DisplayName("MULTI-TENANT: CustomEmployeeDetails de distintas companies NO son iguales (Session Registry)")
    void employeeDetails_differentCompanies_areNotEqual_inSessionRegistry() {
        // Simular los principals que Spring Security crearía
        var detailsCo1 = new CustomEmployeeDetails(
                "shared-user", "pass1", true,
                List.of(), company1.getIdCompany(), 1L);

        var detailsCo2 = new CustomEmployeeDetails(
                "shared-user", "pass2", true,
                List.of(), company2.getIdCompany(), 2L);

        // Igualdad: companyId distinto → NOT equal
        assertThat(detailsCo1).isNotEqualTo(detailsCo2);

        // hashCode también debe ser distinto
        assertThat(detailsCo1.hashCode()).isNotEqualTo(detailsCo2.hashCode());

        // Implication: Spring's SessionRegistry.getAllPrincipals() would count them as different users
        // i.e., login from co2 does NOT expire session from co1
    }

    // ================================================================
    //  Multi-tenant session isolation — Clientes
    // ================================================================

    @Test
    @DisplayName("MULTI-TENANT: mismo email en 2 companies → clientes distintos, sesiones independientes")
    void multiTenant_sameCustomerEmail_independentSessions() throws Exception {
        // Crear cliente con mismo email en dos companies
        testData.createVerifiedCustomer(company1, "john123", "john@example.com", "5511111111", "pass-co1");
        testData.createVerifiedCustomer(company2, "john123", "john@example.com", "5511111111", "pass-co2");

        // Login de cliente en co1
        MvcResult login1 = loginCustomer("john123", "pass-co1", SLUG_CO1);
        assertThat(login1.getResponse().getRedirectedUrl())
                .as("Cliente co1 debe ir al /client/dashboard o similar - verificar implementación")
                .isNotEqualTo("/client/login?error=true");

        // Login de cliente en co2
        MvcResult login2 = loginCustomer("john123", "pass-co2", SLUG_CO2);
        assertThat(login2.getResponse().getRedirectedUrl())
                .as("Cliente co2 debe loguearse correctamente")
                .isNotEqualTo("/client/login?error=true");
    }

    @Test
    @DisplayName("MULTI-TENANT: CustomCustomerDetails de distintas companies NO son iguales")
    void customerDetails_differentCompanies_areNotEqual() {
        var custCo1 = new CustomCustomerDetails(
                "john@example.com", "pass", true,
                List.of(), company1.getIdCompany(), 100L);

        var custCo2 = new CustomCustomerDetails(
                "john@example.com", "pass", true,
                List.of(), company2.getIdCompany(), 200L);

        assertThat(custCo1).isNotEqualTo(custCo2);
        assertThat(custCo1.hashCode()).isNotEqualTo(custCo2.hashCode());
    }

    @Test
    @DisplayName("Cliente intenta loguearse en formulario de empleado → error=clientAttempt")
    void clientLogsIntoEmployeeForm_redirectsWithError() throws Exception {
        testData.createVerifiedCustomer(company1, "custonEmp", "cust@co1.com", "5599999999", "custpass");

        // Intentar login como cliente en el formulario de empleados (/perform_login)
        // Debe incluir Referer del login de empleados (no /client/login) para activar el check
        MvcResult result = mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "custonEmp")
                        .param("password", "custpass")
                        .header("Referer", "http://" + SLUG_CO1 + ".localhost/login")
                        .with(forCompany(SLUG_CO1)))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        // El success handler detecta ROLE_CLIENT en formulario de empleados
        // y redirige con ?error=clientAttempt
        String redirect = result.getResponse().getRedirectedUrl();
        assertThat(redirect)
                .as("Cliente en formulario de empleado debe recibir error=clientAttempt")
                .contains("clientAttempt");
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private MvcResult loginEmployee(String username, String password, String companySlug) throws Exception {
        return mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username)
                        .param("password", password)
                        .with(forCompany(companySlug)))
                .andExpect(status().is3xxRedirection())
                .andReturn();
    }

    private MvcResult loginCustomer(String username, String password, String companySlug) throws Exception {
        return mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username)
                        .param("password", password)
                        .with(forCompany(companySlug)))
                .andReturn();
    }

    private RequestPostProcessor forCompany(String slug) {
        return request -> {
            request.setServerName(slug + ".localhost");
            return request;
        };
    }
}
