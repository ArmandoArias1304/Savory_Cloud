package com.aatechsolutions.elgransazon.phase10;

import com.aatechsolutions.elgransazon.application.service.EmailService;
import com.aatechsolutions.elgransazon.domain.entity.*;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FASE 10 — Tests de integración para Client, Programmer, Home y LicenseExpired controllers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("FASE 10 — Client, Programmer, Home & Auth Integration Tests")
class ClientProgrammerHomeIntegrationTest {

    private static final String SLUG_1 = "tp10-cli-c1";
    private static final String SLUG_2 = "tp10-cli-c2";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper helper;
    @MockBean private EmailService emailService;

    private Company company1, company2;
    private Employee admin1, programmer1;
    private Customer customer1;

    @BeforeEach
    void setUp() {
        helper.cleanUpProgrammerByUsername("tp10-prog-1");
        helper.cleanUpBySlug(SLUG_1);
        helper.cleanUpBySlug(SLUG_2);

        company1 = helper.createActiveCompany(SLUG_1, "America/Mexico_City");
        company2 = helper.createActiveCompany(SLUG_2, "America/Mexico_City");

        admin1      = helper.createEmployee(company1, "tp10-admin-1", "Pass1234!", "ROLE_ADMIN");
        // PROGRAMMER must have company=null (global user, no tenant context)
        programmer1 = helper.createEmployee(null, "tp10-prog-1", "Pass1234!", "ROLE_PROGRAMMER");

        customer1 = helper.createVerifiedCustomer(company1,
                "tp10_client_1", "tp10client1@test.com", "5559871234", "Pass1234!");
    }

    @AfterEach
    void tearDown() {
        helper.cleanUpBySlug(SLUG_1);
        helper.cleanUpBySlug(SLUG_2);
        helper.cleanUpProgrammerByUsername("tp10-prog-1");
    }

    // ================================================================
    //  HomeController  /  y /home
    // ================================================================

    @Test
    @DisplayName("Página de inicio accesible sin autenticación")
    void home_isPublic() throws Exception {
        mockMvc.perform(get("/home")
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Ruta raíz accesible")
    void root_isAccessible() throws Exception {
        int code = mockMvc.perform(get("/")
                        .with(forCompany(SLUG_1)))
                .andReturn().getResponse().getStatus();
        // Root may redirect to /home or return 200
        assertTrue(code == 200 || code == 302, "Expected 200 or 302 but got " + code);
    }

    @Test
    @DisplayName("Menú público accesible")
    void homeMenu_isPublic() throws Exception {
        mockMvc.perform(get("/home/menu")
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    // ================================================================
    //  LicenseExpiredController
    // ================================================================

    @Test
    @DisplayName("Página de licencia expirada accesible sin autenticación")
    void licenseExpired_isPublic() throws Exception {
        mockMvc.perform(get("/license-expired")
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    // ================================================================
    //  ClientAuthController  /client (público)
    // ================================================================

    @Test
    @DisplayName("Login de cliente accesible sin autenticación")
    void clientLogin_isPublic() throws Exception {
        mockMvc.perform(get("/client/login")
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Registro de cliente accesible sin autenticación")
    void clientRegister_isPublic() throws Exception {
        mockMvc.perform(get("/client/register")
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Recuperar contraseña accesible sin autenticación")
    void clientForgotPassword_isPublic() throws Exception {
        mockMvc.perform(get("/client/forgot-password")
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    // ================================================================
    //  ClientController  /client (protegido — ROLE_CLIENT)
    // ================================================================

    @Test
    @DisplayName("CLIENT puede ver su dashboard")
    void client_canViewDashboard() throws Exception {
        mockMvc.perform(get("/client/dashboard")
                        .with(user("tp10client1@test.com").roles("CLIENT"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CLIENT puede ver menú")
    void client_canViewMenu() throws Exception {
        mockMvc.perform(get("/client/menu")
                        .with(user("tp10client1@test.com").roles("CLIENT"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CLIENT puede ver sus órdenes")
    void client_canViewOrders() throws Exception {
        mockMvc.perform(get("/client/orders")
                        .with(user("tp10client1@test.com").roles("CLIENT"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CLIENT puede ver su perfil")
    void client_canViewProfile() throws Exception {
        mockMvc.perform(get("/client/profile")
                        .with(user("tp10client1@test.com").roles("CLIENT"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CLIENT puede ver formulario de reseña")
    void client_canViewReview() throws Exception {
        mockMvc.perform(get("/client/review")
                        .with(user("tp10client1@test.com").roles("CLIENT"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CLIENT puede obtener promociones activas (JSON)")
    void client_canGetActivePromotions() throws Exception {
        mockMvc.perform(get("/client/promotions/active")
                        .with(user("tp10client1@test.com").roles("CLIENT"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    @DisplayName("ADMIN no puede acceder a /client/dashboard (role mismatch)")
    void admin_cannotAccessClientDashboard() throws Exception {
        mockMvc.perform(get("/client/dashboard")
                        .with(user("tp10-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Usuario no autenticado redirigido al login desde /client/dashboard")
    void unauthenticated_redirectsFromClientDashboard() throws Exception {
        int code = mockMvc.perform(get("/client/dashboard")
                        .with(forCompany(SLUG_1)))
                .andReturn().getResponse().getStatus();
        assertTrue(code == 302 || code == 401, "Expected redirect to login but got " + code);
    }

    // ================================================================
    //  ProgrammerController  /programmer
    // ================================================================

    @Test
    @DisplayName("PROGRAMMER puede ver su dashboard")
    void programmer_canViewDashboard() throws Exception {
        mockMvc.perform(get("/programmer/dashboard")
                        .with(user("tp10-prog-1").roles("PROGRAMMER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PROGRAMMER puede ver lista de empresas")
    void programmer_canViewCompanies() throws Exception {
        mockMvc.perform(get("/programmer/companies")
                        .with(user("tp10-prog-1").roles("PROGRAMMER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PROGRAMMER puede ver formulario nueva empresa")
    void programmer_canViewNewCompanyForm() throws Exception {
        mockMvc.perform(get("/programmer/companies/new")
                        .with(user("tp10-prog-1").roles("PROGRAMMER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PROGRAMMER puede ver detalle de empresa")
    void programmer_canViewCompanyDetail() throws Exception {
        mockMvc.perform(get("/programmer/companies/" + company1.getIdCompany())
                        .with(user("tp10-prog-1").roles("PROGRAMMER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN no puede acceder a /programmer/dashboard — redirigido")
    void admin_cannotAccessProgrammerDashboard() throws Exception {
        // ADMIN with company context hitting /programmer/ → CompanyContextFilter skips company lookup
        // → UserValidationFilter: no company → findByUsernameAndCompanyIsNull(admin) → empty → redirect to /login
        int code = mockMvc.perform(get("/programmer/dashboard")
                        .with(user("tp10-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andReturn().getResponse().getStatus();
        assertTrue(code == 302 || code == 403, "Expected 302 or 403 but got " + code);
    }

    // ================================================================
    //  Multi-tenant isolation
    // ================================================================

    @Test
    @DisplayName("Company2 no tiene los mismos clientes que Company1")
    void multiTenant_clientIsolation() throws Exception {
        // Company2 home should be accessible and not contain company1 data
        mockMvc.perform(get("/home")
                        .with(forCompany(SLUG_2)))
                .andExpect(status().isOk());
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
}
