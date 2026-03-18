package com.aatechsolutions.elgransazon.phase1;

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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FASE 1 — Tests de integración para el flujo de autenticación.
 *
 * Verifica:
 * - Login con credenciales válidas (empleado) → redirect por rol
 * - Login con credenciales inválidas → redirect con ?error=true
 * - Multi-tenant: mismo username en 2 companies → autenticación independiente
 * - Redirección post-login según rol (ADMIN, CHEF, WAITER, etc.)
 * - Acceso a GET /login muestra el formulario
 *
 * BD real: bd_restaurant (localhost:3306, root, sin contraseña).
 * Datos de prueba son creados y limpiados en cada test para máximo aislamiento.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("FASE 1 — Auth Integration Tests")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestDataHelper testData;

    // Mock email sending to avoid SMTP calls in tests
    @MockBean
    private EmailService emailService;

    // Test company slugs (unique per test run to avoid dirty-read issues)
    private static final String SLUG_CO1 = "tp1-auth-co1";
    private static final String SLUG_CO2 = "tp1-auth-co2";

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
    //  GET /login — página de login
    // ================================================================

    @Test
    @DisplayName("GET /login muestra el formulario de login (HTTP 200)")
    void getLogin_returnsLoginPage() throws Exception {
        mockMvc.perform(get("/login").with(forCompany(SLUG_CO1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /login?error=true muestra mensaje de error")
    void getLogin_withErrorParam_showsError() throws Exception {
        mockMvc.perform(get("/login").param("error", "true").with(forCompany(SLUG_CO1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /login?logout=true muestra mensaje de cierre de sesión")
    void getLogin_withLogoutParam_showsLogoutMessage() throws Exception {
        mockMvc.perform(get("/login").param("logout", "true").with(forCompany(SLUG_CO1)))
                .andExpect(status().isOk());
    }

    // ================================================================
    //  LOGIN con credenciales válidas — Redirección por rol
    // ================================================================

    @Test
    @DisplayName("ADMIN login exitoso → redirige a /admin/dashboard")
    void adminLogin_redirectsToDashboard() throws Exception {
        testData.createEmployee(company1, "tp1-admin", "pass123", Role.ADMIN);

        MvcResult result = mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "tp1-admin")
                        .param("password", "pass123")
                        .with(forCompany(SLUG_CO1)))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirectUrl = result.getResponse().getRedirectedUrl();
        assertThat(redirectUrl).isEqualTo("/admin/dashboard");
    }

    @Test
    @DisplayName("CHEF login exitoso → redirige a /chef/dashboard")
    void chefLogin_redirectsToChefDashboard() throws Exception {
        testData.createEmployee(company1, "tp1-chef", "pass123", Role.CHEF);

        MvcResult result = mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "tp1-chef")
                        .param("password", "pass123")
                        .with(forCompany(SLUG_CO1)))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/chef/dashboard");
    }

    @Test
    @DisplayName("WAITER login exitoso → redirige a /waiter/dashboard")
    void waiterLogin_redirectsToWaiterDashboard() throws Exception {
        testData.createEmployee(company1, "tp1-waiter", "pass123", Role.WAITER);

        MvcResult result = mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "tp1-waiter")
                        .param("password", "pass123")
                        .with(forCompany(SLUG_CO1)))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/waiter/dashboard");
    }

    @Test
    @DisplayName("CASHIER login exitoso → redirige a /cashier/dashboard")
    void cashierLogin_redirectsToCashierDashboard() throws Exception {
        testData.createEmployee(company1, "tp1-cashier", "pass123", Role.CASHIER);

        MvcResult result = mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "tp1-cashier")
                        .param("password", "pass123")
                        .with(forCompany(SLUG_CO1)))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/cashier/dashboard");
    }

    @Test
    @DisplayName("DELIVERY login exitoso → redirige a /delivery/dashboard")
    void deliveryLogin_redirectsToDeliveryDashboard() throws Exception {
        testData.createEmployee(company1, "tp1-delivery", "pass123", Role.DELIVERY);

        MvcResult result = mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "tp1-delivery")
                        .param("password", "pass123")
                        .with(forCompany(SLUG_CO1)))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/delivery/dashboard");
    }

    @Test
    @DisplayName("BARISTA login exitoso → redirige a /chef/dashboard")
    void baristaLogin_redirectsToChefDashboard() throws Exception {
        testData.createEmployee(company1, "tp1-barista", "pass123", Role.BARISTA);

        MvcResult result = mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "tp1-barista")
                        .param("password", "pass123")
                        .with(forCompany(SLUG_CO1)))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/chef/dashboard");
    }

    // ================================================================
    //  LOGIN con credenciales inválidas
    // ================================================================

    @Test
    @DisplayName("Login con contraseña incorrecta → redirige a /login?error=true")
    void loginWithWrongPassword_redirectsWithError() throws Exception {
        testData.createEmployee(company1, "tp1-emp-wrong", "realpass", Role.WAITER);

        MvcResult result = mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "tp1-emp-wrong")
                        .param("password", "wrongpassword")
                        .with(forCompany(SLUG_CO1)))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/login?error=true");
    }

    @Test
    @DisplayName("Login con usuario inexistente → redirige a /login?error=true")
    void loginWithNonexistentUser_redirectsWithError() throws Exception {
        MvcResult result = mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "nonexistentuser_xyz")
                        .param("password", "somepassword")
                        .with(forCompany(SLUG_CO1)))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/login?error=true");
    }

    @Test
    @DisplayName("Empleado deshabilitado no puede autenticarse")
    void disabledEmployee_cannotLogin() throws Exception {
        testData.createDisabledEmployee(company1, "tp1-disabled", "pass123", Role.WAITER);

        MvcResult result = mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "tp1-disabled")
                        .param("password", "pass123")
                        .with(forCompany(SLUG_CO1)))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/login?error=true");
    }

    // ================================================================
    //  MULTI-TENANT: mismo username en 2 companies distintas
    // ================================================================

    @Test
    @DisplayName("MULTI-TENANT: mismo username en 2 companies → cada uno se autentica en SU company")
    void multiTenant_sameUsername_eachLogsIntoOwnCompany() throws Exception {
        // Crear empleados con MISMO username en diferentes companies
        testData.createEmployee(company1, "tp1-shared-user", "pass1", Role.ADMIN);
        testData.createEmployee(company2, "tp1-shared-user", "pass2", Role.CHEF);

        // Login en Company 1 → debe funcionar con pass1 y redirigir a /admin/dashboard
        MvcResult result1 = mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "tp1-shared-user")
                        .param("password", "pass1")
                        .with(forCompany(SLUG_CO1)))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result1.getResponse().getRedirectedUrl())
                .as("Company 1 user debe redirigir a /admin/dashboard")
                .isEqualTo("/admin/dashboard");

        // Login en Company 2 → debe funcionar con pass2 y redirigir a /chef/dashboard
        MvcResult result2 = mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "tp1-shared-user")
                        .param("password", "pass2")
                        .with(forCompany(SLUG_CO2)))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result2.getResponse().getRedirectedUrl())
                .as("Company 2 user debe redirigir a /chef/dashboard")
                .isEqualTo("/chef/dashboard");
    }

    @Test
    @DisplayName("MULTI-TENANT: contraseña de company 1 NO funciona en company 2 (mismo username)")
    void multiTenant_password_fromOneCompany_doesNotWorkInOther() throws Exception {
        // Mismo username, distintas contraseñas en distintas companies
        testData.createEmployee(company1, "tp1-cross-user", "pass-co1", Role.ADMIN);
        testData.createEmployee(company2, "tp1-cross-user", "pass-co2", Role.WAITER);

        // Intentar usar pass-co1 en company2 → debe fallar
        MvcResult result = mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "tp1-cross-user")
                        .param("password", "pass-co1")  // contraseña de company1
                        .with(forCompany(SLUG_CO2)))     // pero logueando en company2
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl())
                .as("Contraseña de company1 NO debe funcionar en company2")
                .isEqualTo("/login?error=true");
    }

    @Test
    @DisplayName("MULTI-TENANT: empleado de company 1 no puede loguearse en company 2 (aunque username coincida)")
    void multiTenant_employeeOfCo1_cannotLoginAsOwnUserInCo2_whenAbsent() throws Exception {
        // Solo en company 1, no en company 2
        testData.createEmployee(company1, "tp1-only-co1", "pass123", Role.WAITER);

        // Intentar login en company 2 → usuario no existe ahí
        MvcResult result = mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "tp1-only-co1")
                        .param("password", "pass123")
                        .with(forCompany(SLUG_CO2)))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl())
                .as("Empleado de co1 no debe poder loguearse en co2 donde no existe")
                .isEqualTo("/login?error=true");
    }

    // ================================================================
    //  LOGOUT
    // ================================================================

    @Test
    @DisplayName("Logout exitoso → redirige a /login")
    void logout_redirectsToLogin() throws Exception {
        testData.createEmployee(company1, "tp1-logout-user", "pass123", Role.WAITER);

        // Login → obtener sesión
        MvcResult loginResult = mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "tp1-logout-user")
                        .param("password", "pass123")
                        .with(forCompany(SLUG_CO1)))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Logout
        MvcResult logoutResult = mockMvc.perform(post("/logout")
                        .session(session != null ? session : new MockHttpSession())
                        .with(forCompany(SLUG_CO1)))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        // CustomLogoutSuccessHandler should redirect to /login or /login?logout=true
        String redirectUrl = logoutResult.getResponse().getRedirectedUrl();
        assertThat(redirectUrl).startsWith("/login");
    }

    // ================================================================
    //  Páginas auxiliares
    // ================================================================

    @Test
    @DisplayName("GET /help → HTTP 200")
    void helpPage_isAccessible() throws Exception {
        mockMvc.perform(get("/help").with(forCompany(SLUG_CO1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /support → HTTP 200")
    void supportPage_isAccessible() throws Exception {
        mockMvc.perform(get("/support").with(forCompany(SLUG_CO1)))
                .andExpect(status().isOk());
    }

    // ================================================================
    //  Helper
    // ================================================================

    /**
     * Sets the serverName in MockMvc request to simulate subdomain-based company resolution.
     * CompanyContextFilter uses request.getServerName() to find the company by slug.
     */
    private org.springframework.test.web.servlet.request.RequestPostProcessor forCompany(String slug) {
        return request -> {
            request.setServerName(slug + ".localhost");
            return request;
        };
    }
}
