package com.aatechsolutions.elgransazon.phase7;

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
 * FASE 7 — Tests de integración para Dashboard & Role Controllers:
 * AdminController, CashierController, ChefController, WaiterController, DeliveryController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("FASE 7 — Dashboard & Role Controller Integration Tests")
class DashboardAndRoleControllerIntegrationTest {

    private static final String SLUG_1 = "tp7-dash-c1";
    private static final String SLUG_2 = "tp7-dash-c2";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper helper;
    @MockBean private EmailService emailService;

    private Company company1, company2;
    private Employee admin1, cashier1, waiter1, chef1, delivery1, admin2;

    @BeforeEach
    void setUp() {
        helper.cleanUpBySlug(SLUG_1);
        helper.cleanUpBySlug(SLUG_2);

        company1 = helper.createActiveCompany(SLUG_1, "America/Mexico_City");
        company2 = helper.createActiveCompany(SLUG_2, "America/Mexico_City");

        admin1    = helper.createEmployee(company1, "tp7-admin-1", "Pass1234!", "ROLE_ADMIN");
        cashier1  = helper.createEmployee(company1, "tp7-cashier-1", "Pass1234!", "ROLE_CASHIER");
        waiter1   = helper.createEmployee(company1, "tp7-waiter-1", "Pass1234!", "ROLE_WAITER");
        chef1     = helper.createEmployee(company1, "tp7-chef-1", "Pass1234!", "ROLE_CHEF");
        delivery1 = helper.createEmployee(company1, "tp7-delivery-1", "Pass1234!", "ROLE_DELIVERY");
        admin2    = helper.createEmployee(company2, "tp7-admin-2", "Pass1234!", "ROLE_ADMIN");
    }

    @AfterEach
    void tearDown() {
        helper.cleanUpBySlug(SLUG_1);
        helper.cleanUpBySlug(SLUG_2);
    }

    // ================================================================
    //  AdminController  /admin/dashboard
    // ================================================================

    @Test
    @DisplayName("ADMIN puede ver dashboard")
    void admin_canViewDashboard() throws Exception {
        mockMvc.perform(get("/admin/dashboard")
                        .with(user("tp7-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"));
    }

    @Test
    @DisplayName("ADMIN — endpoint popular-items retorna JSON")
    void admin_popularItems_returnsJson() throws Exception {
        mockMvc.perform(get("/admin/dashboard/popular-items")
                        .with(user("tp7-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    @DisplayName("ADMIN — endpoint stats retorna JSON")
    void admin_stats_returnsJson() throws Exception {
        mockMvc.perform(get("/admin/dashboard/stats")
                        .with(user("tp7-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    @DisplayName("CASHIER no puede acceder a /admin/dashboard")
    void cashier_cannotAccessAdminDashboard() throws Exception {
        mockMvc.perform(get("/admin/dashboard")
                        .with(user("tp7-cashier-1").roles("CASHIER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    //  CashierController  /cashier
    // ================================================================

    @Test
    @DisplayName("CASHIER puede ver su dashboard")
    void cashier_canViewDashboard() throws Exception {
        mockMvc.perform(get("/cashier/dashboard")
                        .with(user("tp7-cashier-1").roles("CASHIER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN no puede acceder al dashboard de CASHIER")
    void admin_cannotAccessCashierDashboard() throws Exception {
        mockMvc.perform(get("/cashier/dashboard")
                        .with(user("tp7-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    //  ChefController  /chef
    // ================================================================

    @Test
    @DisplayName("CHEF puede ver su dashboard")
    void chef_canViewDashboard() throws Exception {
        mockMvc.perform(get("/chef/dashboard")
                        .with(user("tp7-chef-1").roles("CHEF"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CHEF puede ver órdenes pendientes")
    void chef_canViewPendingOrders() throws Exception {
        mockMvc.perform(get("/chef/orders/pending")
                        .with(user("tp7-chef-1").roles("CHEF"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CHEF puede ver mis órdenes")
    void chef_canViewMyOrders() throws Exception {
        mockMvc.perform(get("/chef/orders/my-orders")
                        .with(user("tp7-chef-1").roles("CHEF"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CHEF puede ver su perfil")
    void chef_canViewProfile() throws Exception {
        mockMvc.perform(get("/chef/profile")
                        .with(user("tp7-chef-1").roles("CHEF"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("WAITER no puede acceder a /chef/dashboard")
    void waiter_cannotAccessChefDashboard() throws Exception {
        mockMvc.perform(get("/chef/dashboard")
                        .with(user("tp7-waiter-1").roles("WAITER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    //  WaiterController  /waiter
    // ================================================================

    @Test
    @DisplayName("WAITER puede ver su dashboard")
    void waiter_canViewDashboard() throws Exception {
        mockMvc.perform(get("/waiter/dashboard")
                        .with(user("tp7-waiter-1").roles("WAITER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("WAITER puede ver su perfil")
    void waiter_canViewProfile() throws Exception {
        mockMvc.perform(get("/waiter/profile")
                        .with(user("tp7-waiter-1").roles("WAITER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("WAITER puede ver menú")
    void waiter_canViewMenu() throws Exception {
        mockMvc.perform(get("/waiter/menu/view")
                        .with(user("tp7-waiter-1").roles("WAITER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("WAITER puede ver ranking")
    void waiter_canViewRanking() throws Exception {
        mockMvc.perform(get("/waiter/ranking/view")
                        .with(user("tp7-waiter-1").roles("WAITER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CHEF no puede acceder a /waiter/dashboard")
    void chef_cannotAccessWaiterDashboard() throws Exception {
        mockMvc.perform(get("/waiter/dashboard")
                        .with(user("tp7-chef-1").roles("CHEF"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    //  DeliveryController  /delivery
    // ================================================================

    @Test
    @DisplayName("DELIVERY puede ver su dashboard")
    void delivery_canViewDashboard() throws Exception {
        mockMvc.perform(get("/delivery/dashboard")
                        .with(user("tp7-delivery-1").roles("DELIVERY"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELIVERY puede ver órdenes pendientes")
    void delivery_canViewPendingOrders() throws Exception {
        mockMvc.perform(get("/delivery/orders/pending")
                        .with(user("tp7-delivery-1").roles("DELIVERY"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELIVERY puede ver perfil")
    void delivery_canViewProfile() throws Exception {
        mockMvc.perform(get("/delivery/profile")
                        .with(user("tp7-delivery-1").roles("DELIVERY"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN no puede acceder a /delivery/dashboard")
    void admin_cannotAccessDeliveryDashboard() throws Exception {
        mockMvc.perform(get("/delivery/dashboard")
                        .with(user("tp7-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    //  Multi-tenant isolation
    // ================================================================

    @Test
    @DisplayName("Company2 ADMIN ve su propio dashboard (aislamiento)")
    void multiTenant_adminDashboard_isolation() throws Exception {
        mockMvc.perform(get("/admin/dashboard")
                        .with(user("tp7-admin-2").roles("ADMIN"))
                        .with(forCompany(SLUG_2)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"));
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
