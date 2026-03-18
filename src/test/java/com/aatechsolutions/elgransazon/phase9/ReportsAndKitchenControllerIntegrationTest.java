package com.aatechsolutions.elgransazon.phase9;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FASE 9 — Tests de integración para ReportsController y AdminKitchenController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("FASE 9 — Reports & Kitchen Controller Integration Tests")
class ReportsAndKitchenControllerIntegrationTest {

    private static final String SLUG_1 = "tp9-rep-c1";
    private static final String SLUG_2 = "tp9-rep-c2";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper helper;
    @MockBean private EmailService emailService;

    private Company company1, company2;
    private Employee admin1, waiter1, cashier1, admin2;

    @BeforeEach
    void setUp() {
        helper.cleanUpBySlug(SLUG_1);
        helper.cleanUpBySlug(SLUG_2);

        company1 = helper.createActiveCompany(SLUG_1, "America/Mexico_City");
        company2 = helper.createActiveCompany(SLUG_2, "America/Mexico_City");

        admin1   = helper.createEmployee(company1, "tp9-admin-1", "Pass1234!", "ROLE_ADMIN");
        waiter1  = helper.createEmployee(company1, "tp9-waiter-1", "Pass1234!", "ROLE_WAITER");
        cashier1 = helper.createEmployee(company1, "tp9-cashier-1", "Pass1234!", "ROLE_CASHIER");
        admin2   = helper.createEmployee(company2, "tp9-admin-2", "Pass1234!", "ROLE_ADMIN");
    }

    @AfterEach
    void tearDown() {
        helper.cleanUpBySlug(SLUG_1);
        helper.cleanUpBySlug(SLUG_2);
    }

    // ================================================================
    //  ReportsController  /admin/reports
    // ================================================================

    @Test
    @DisplayName("ADMIN puede ver reportes")
    void admin_canViewReports() throws Exception {
        mockMvc.perform(get("/admin/reports")
                        .with(user("tp9-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/reports/list"));
    }

    @Test
    @DisplayName("ADMIN puede ver reportes con filtros de fecha")
    void admin_reportsWithDateFilters() throws Exception {
        mockMvc.perform(get("/admin/reports")
                        .with(user("tp9-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1))
                        .param("startDate", "2025-01-01")
                        .param("endDate", "2026-12-31"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN puede descargar PDF ejecutivo")
    void admin_canDownloadExecutivePdf() throws Exception {
        mockMvc.perform(get("/admin/reports/download/executive")
                        .with(user("tp9-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    @DisplayName("ADMIN puede descargar PDF de productos")
    void admin_canDownloadProductsPdf() throws Exception {
        mockMvc.perform(get("/admin/reports/download/products")
                        .with(user("tp9-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    @DisplayName("ADMIN puede descargar PDF de empleados")
    void admin_canDownloadEmployeesPdf() throws Exception {
        mockMvc.perform(get("/admin/reports/download/employees")
                        .with(user("tp9-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    @DisplayName("ADMIN puede descargar PDF de clientes")
    void admin_canDownloadClientsPdf() throws Exception {
        mockMvc.perform(get("/admin/reports/download/clients")
                        .with(user("tp9-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    @DisplayName("ADMIN puede ver reporte ingresos/gastos")
    void admin_canViewIncomeExpenses() throws Exception {
        mockMvc.perform(get("/admin/reports/income-expenses")
                        .with(user("tp9-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/reports/income-expenses"));
    }

    @Test
    @DisplayName("ADMIN — ventas de complementos (JSON)")
    void admin_complementSalesJson() throws Exception {
        mockMvc.perform(get("/admin/reports/income-expenses/income/complements")
                        .with(user("tp9-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    @DisplayName("CASHIER no puede acceder a /admin/reports (SecurityConfig /admin/**)")
    void cashier_cannotAccessReports() throws Exception {
        mockMvc.perform(get("/admin/reports")
                        .with(user("tp9-cashier-1").roles("CASHIER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    //  AdminKitchenController  /admin/kitchen
    // ================================================================

    @Test
    @DisplayName("ADMIN puede ver cocina")
    void admin_canViewKitchen() throws Exception {
        mockMvc.perform(get("/admin/kitchen")
                        .with(user("tp9-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/kitchen/index"));
    }

    @Test
    @DisplayName("ADMIN puede ver todas las órdenes de cocina")
    void admin_canViewAllKitchenOrders() throws Exception {
        mockMvc.perform(get("/admin/kitchen/all-orders")
                        .with(user("tp9-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/kitchen/all-orders"));
    }

    @Test
    @DisplayName("ADMIN — órdenes de cocina con filtros")
    void admin_kitchenOrdersWithFilters() throws Exception {
        mockMvc.perform(get("/admin/kitchen/all-orders")
                        .with(user("tp9-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1))
                        .param("status", "PENDING")
                        .param("page", "0"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("WAITER no puede acceder a /admin/kitchen (SecurityConfig /admin/**)")
    void waiter_cannotAccessKitchen() throws Exception {
        mockMvc.perform(get("/admin/kitchen")
                        .with(user("tp9-waiter-1").roles("WAITER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CASHIER no puede acceder a /admin/kitchen")
    void cashier_cannotAccessKitchen() throws Exception {
        mockMvc.perform(get("/admin/kitchen")
                        .with(user("tp9-cashier-1").roles("CASHIER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    //  Multi-tenant isolation
    // ================================================================

    @Test
    @DisplayName("Company2 ADMIN ve su propia cocina (aislamiento)")
    void multiTenant_kitchen_isolation() throws Exception {
        mockMvc.perform(get("/admin/kitchen")
                        .with(user("tp9-admin-2").roles("ADMIN"))
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
