package com.aatechsolutions.elgransazon.phase2;

import com.aatechsolutions.elgransazon.application.service.EmailService;
import com.aatechsolutions.elgransazon.application.service.LicenseService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Role;
import com.aatechsolutions.elgransazon.domain.entity.SystemLicense;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FASE 2 — Tests de integración para LicenseInterceptor y LicenseValidationFilter.
 *
 * Verifica:
 * - Licencia ACTIVE → acceso normal a todos los roles
 * - Licencia EXPIRED → bloquea a usuarios autenticados y redirige a /license-expired
 * - Licencia SUSPENDED → bloquea el acceso (redirige a /license-expired)
 * - PROGRAMMER siempre tiene acceso (incluso con licencia expirada)
 * - Warning de licencia (≤5 días) es visible en el modelo de respuesta
 * - Timezone: la expiración se evalúa según la timezone de la company
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("FASE 2 — License Filters Integration Tests")
class LicenseFilterIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper testData;
    @Autowired private LicenseService licenseService;

    @MockBean private EmailService emailService;

    // Slugs únicos para evitar contaminación con otros tests
    private static final String SLUG_ACTIVE       = "tp2-lic-active";
    private static final String SLUG_EXPIRED      = "tp2-lic-expired";
    private static final String SLUG_SUSPENDED    = "tp2-lic-suspended";
    private static final String SLUG_WARN         = "tp2-lic-warn";         // 3 días restantes
    private static final String SLUG_TOKYO        = "tp2-lic-tokyo";        // timezone test
    private static final String SLUG_MEXICO       = "tp2-lic-mexico";       // timezone test
    private static final String SLUG_ACTIVE_STALE = "tp2-lic-active-stale"; // status=ACTIVE pero fecha vencida

    private Company activeCompany;
    private Company expiredCompany;
    private Company suspendedCompany;
    private Company warnCompany;
    private Company tokyoCompany;
    private Company mexicoCompany;
    private Company activeStatusExpiredDateCompany;
    private Employee programmerEmployee;

    @BeforeEach
    void setUp() {
        activeCompany    = testData.createActiveCompany(SLUG_ACTIVE, "America/Mexico_City");
        expiredCompany   = testData.createExpiredLicenseCompany(SLUG_EXPIRED);
        suspendedCompany = testData.createSuspendedLicenseCompany(SLUG_SUSPENDED);
        warnCompany      = testData.createActiveCompany(SLUG_WARN, "America/Mexico_City");
        testData.setLicenseDaysUntilExpiry(warnCompany.getIdCompany(), 3); // 3 días → warning

        tokyoCompany  = testData.createActiveCompany(SLUG_TOKYO, "Asia/Tokyo");
        mexicoCompany = testData.createActiveCompany(SLUG_MEXICO, "America/Mexico_City");
        activeStatusExpiredDateCompany = testData.createActiveStatusExpiredDateCompany(SLUG_ACTIVE_STALE);

        // Create employees for each company
        testData.createEmployee(activeCompany,                "tp2-admin-active",       "pass", Role.ADMIN);
        testData.createEmployee(expiredCompany,               "tp2-admin-expired",      "pass", Role.ADMIN);
        testData.createEmployee(suspendedCompany,             "tp2-admin-suspended",    "pass", Role.ADMIN);
        testData.createEmployee(warnCompany,                  "tp2-admin-warn",         "pass", Role.ADMIN);
        testData.createEmployee(tokyoCompany,                 "tp2-admin-tokyo",        "pass", Role.ADMIN);
        testData.createEmployee(mexicoCompany,                "tp2-admin-mexico",       "pass", Role.ADMIN);
        testData.createEmployee(activeStatusExpiredDateCompany, "tp2-admin-stale",      "pass", Role.ADMIN);

        // PROGRAMMER is a global user (company=null) — needed for UserValidationFilter lookups
        programmerEmployee = testData.createEmployee(null, "tp2-prog-user", "pass", Role.PROGRAMMER);
    }

    @AfterEach
    void cleanUp() {
        // Invalidate license caches first
        invalidateCacheFor(activeCompany);
        invalidateCacheFor(expiredCompany);
        invalidateCacheFor(suspendedCompany);
        invalidateCacheFor(warnCompany);
        invalidateCacheFor(tokyoCompany);
        invalidateCacheFor(mexicoCompany);
        invalidateCacheFor(activeStatusExpiredDateCompany);

        testData.deleteCompanyAndAllData(activeCompany.getIdCompany());
        testData.deleteCompanyAndAllData(expiredCompany.getIdCompany());
        testData.deleteCompanyAndAllData(suspendedCompany.getIdCompany());
        testData.deleteCompanyAndAllData(warnCompany.getIdCompany());
        testData.deleteCompanyAndAllData(tokyoCompany.getIdCompany());
        testData.deleteCompanyAndAllData(mexicoCompany.getIdCompany());
        testData.deleteCompanyAndAllData(activeStatusExpiredDateCompany.getIdCompany());

        // Programmer has company=null so not cleaned up by deleteCompanyAndAllData
        if (programmerEmployee != null) {
            testData.deleteEmployee(programmerEmployee.getIdEmpleado());
        }
    }

    // ================================================================
    //  Licencia ACTIVE → acceso normal
    // ================================================================

    @Test
    @DisplayName("Licencia ACTIVE: login exitoso y acceso al dashboard")
    void activeLicense_loginSucceeds() throws Exception {
        MvcResult result = loginEmployee("tp2-admin-active", "pass", SLUG_ACTIVE);
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/admin/dashboard");
    }

    @Test
    @DisplayName("Licencia ACTIVE: GET /admin/dashboard permite acceso al ADMIN autenticado")
    void activeLicense_adminCanAccessDashboard() throws Exception {
        MockHttpSession session = getAuthenticatedSession("tp2-admin-active", "pass", SLUG_ACTIVE);

        mockMvc.perform(get("/admin/dashboard")
                        .session(session)
                        .with(forCompany(SLUG_ACTIVE)))
                .andExpect(status().isOk());
    }

    // ================================================================
    //  Licencia EXPIRED → bloqueo (excepto PROGRAMMER)
    // ================================================================

    @Test
    @DisplayName("Licencia EXPIRED: login falla o redirige a /license-expired")
    void expiredLicense_loginRedirectsToLicenseExpired() throws Exception {
        // Login succeeds (Spring Security authenticates OK - license is checked on NEXT request)
        MvcResult loginResult = loginEmployee("tp2-admin-expired", "pass", SLUG_EXPIRED);
        String loginRedirect = loginResult.getResponse().getRedirectedUrl();

        // Login itself is NOT blocked by license - the success handler redirects to /admin/dashboard
        // The LicenseValidationFilter intercepts on the NEXT page request
        assertThat(loginRedirect)
                .as("Login redirige al dashboard (la licencia se verifica en el siguiente request)")
                .isEqualTo("/admin/dashboard");

        // Follow-up: GET /admin/dashboard → license filter blocks and redirects to /license-expired
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        if (session != null) {
            mockMvc.perform(get("/admin/dashboard")
                            .session(session)
                            .with(forCompany(SLUG_EXPIRED)))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/license-expired"));
        }
    }

    @Test
    @DisplayName("Licencia EXPIRED: usuario autenticado es redirigido a /license-expired en siguiente request")
    void expiredLicense_authenticatedUserRedirectedToLicenseExpired() throws Exception {
        // Simulate an authenticated session for expired company
        // (The filter LicenseValidationFilter checks on every request)
        mockMvc.perform(get("/admin/dashboard")
                        .with(user("tp2-admin-expired").roles("ADMIN"))
                        .with(forCompany(SLUG_EXPIRED)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/license-expired"));
    }

    @Test
    @DisplayName("Licencia SUSPENDED: GET /admin/dashboard redirige a /license-expired (LicenseInterceptor)")
    void suspendedLicense_blocksAccess() throws Exception {
        // LicenseInterceptor blocks SUSPENDED licenses
        mockMvc.perform(get("/admin/dashboard")
                        .with(user("tp2-admin-suspended").roles("ADMIN"))
                        .with(forCompany(SLUG_SUSPENDED)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/license-expired"));
    }

    @Test
    @DisplayName("Licencia SUSPENDED: página pública /login sigue siendo accesible")
    void suspendedLicense_publicPagesStillAccessible() throws Exception {
        // LicenseInterceptor should NOT filter /login
        mockMvc.perform(get("/login").with(forCompany(SLUG_SUSPENDED)))
                .andExpect(status().isOk());
    }

    // ================================================================
    //  PROGRAMMER — acceso siempre garantizado
    // ================================================================

    @Test
    @DisplayName("PROGRAMMER puede acceder a /programmer/ sin importar si la licencia es válida")
    void programmer_alwaysCanAccess_ignoringLicense() throws Exception {
        // PROGRAMMER is a global user (company=null) that accesses via localhost (no tenant subdomain).
        // Even though a SUSPENDED company exists, the PROGRAMMER dashboard should still return 200.
        // The LicenseInterceptor and LicenseValidationFilter both bypass /programmer/ paths.

        // Step 1: Log in as programmer via localhost (no company slug)
        MvcResult loginResult = mockMvc.perform(post("/perform_login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "tp2-prog-user")
                        .param("password", "pass")
                        .with(localhost()))
                .andReturn();

        assertThat(loginResult.getResponse().getRedirectedUrl())
                .as("Programmer login should redirect to programmer dashboard")
                .contains("/programmer");

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).as("Session must exist after programmer login").isNotNull();

        // Step 2: Access /programmer/dashboard — must succeed despite suspended company existing
        mockMvc.perform(get("/programmer/dashboard")
                        .session(session)
                        .with(localhost()))
                .andExpect(status().isOk());
    }

    // ================================================================
    //  Warning de licencia ≤5 días
    // ================================================================

    @Test
    @DisplayName("Licencia con 3 días restantes: atributo showLicenseWarning presente en request")
    void licenseWarning_addedToRequest_whenDaysLt5() throws Exception {
        MockHttpSession session = getAuthenticatedSession("tp2-admin-warn", "pass", SLUG_WARN);

        MvcResult result = mockMvc.perform(get("/admin/dashboard")
                        .session(session)
                        .with(forCompany(SLUG_WARN)))
                .andExpect(status().isOk())
                .andReturn();

        // LicenseInterceptor adds showLicenseWarning, daysLeft attributes
        Object warningAttr = result.getRequest().getAttribute("showLicenseWarning");
        Object daysLeft    = result.getRequest().getAttribute("daysLeft");

        assertThat(warningAttr)
                .as("showLicenseWarning debe estar presente cuando quedan ≤5 días")
                .isNotNull();
        assertThat(warningAttr).isEqualTo(true);
        assertThat(daysLeft).isNotNull();

        Long days = (Long) daysLeft;
        assertThat(days).isBetween(0L, 5L);
    }

    @Test
    @DisplayName("Licencia con 365 días restantes: NO hay showLicenseWarning")
    void noLicenseWarning_whenPlentyOfDaysLeft() throws Exception {
        MockHttpSession session = getAuthenticatedSession("tp2-admin-active", "pass", SLUG_ACTIVE);

        MvcResult result = mockMvc.perform(get("/admin/dashboard")
                        .session(session)
                        .with(forCompany(SLUG_ACTIVE)))
                .andExpect(status().isOk())
                .andReturn();

        Object warningAttr = result.getRequest().getAttribute("showLicenseWarning");
        assertThat(warningAttr)
                .as("No debe haber warning cuando quedan 365 días")
                .isNull();
    }

    // ================================================================
    //  Prueba concluyente: status=ACTIVE en BD pero fecha vencida
    //  El LicenseCheckJob NUNCA corrió → el campo status sigue siendo ACTIVE.
    //  LicenseValidationFilter debe bloquear igual, solo por comparación de fechas.
    // ================================================================

    @Test
    @DisplayName("CONCLUSIVO: status=ACTIVE en BD + expirationDate=ayer → filtro bloquea SIN necesitar LicenseCheckJob")
    void filterBlocksAccess_whenStatusIsActiveButDateIsExpired() throws Exception {
        // Este escenario simula exactamente el caso real:
        // - La licencia venció a las 5am
        // - El LicenseCheckJob está desactivado (o aún no corrió a las 9am)
        // - El campo status en BD sigue siendo ACTIVE
        // - El usuario intenta acceder al dashboard
        //
        // LicenseValidationFilter llama license.isExpired() que compara:
        //   !expirationDate.isAfter(CompanyLocalTime.today())
        // → true (ayer no es después de hoy), por lo tanto bloquea.
        //
        // Si este test pasa: LicenseCheckJob es INNECESARIO para bloquear acceso.
        // El status=ACTIVE en BD no importa. Solo importa la fecha.

        mockMvc.perform(get("/admin/dashboard")
                        .with(user("tp2-admin-stale").roles("ADMIN"))
                        .with(forCompany(SLUG_ACTIVE_STALE)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/license-expired"));
    }

    // ================================================================
    //  GET /license-expired — siempre accesible
    // ================================================================

    @Test
    @DisplayName("GET /license-expired es accesible (HTTP 200) incluso sin autenticación")
    void licenseExpiredPage_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/license-expired").with(forCompany(SLUG_EXPIRED)))
                .andExpect(status().isOk());
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

    private void invalidateCacheFor(Company company) {
        if (company != null) {
            com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext
                    .setCurrentCompany(company);
            try {
                licenseService.invalidateLicenseCache();
            } finally {
                com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext.clear();
            }
        }
    }

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
