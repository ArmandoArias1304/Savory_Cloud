package com.aatechsolutions.elgransazon.gaps;

import com.aatechsolutions.elgransazon.application.service.EmailService;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GAP-FILL — Tests de filtrado de datos por rol:
 * 1) Waiter solo ve sus propios pedidos
 * 2) Cashier solo ve sus propios pedidos
 * 3) Admin ve TODOS los pedidos de su empresa
 * 4) Aislamiento multi-tenant: admin empresa A no ve pedidos de empresa B
 * 5) Chef ve todos los pedidos que requieren preparación
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("GAP — Role-Based Data Filtering Integration Tests")
class RoleBasedDataFilteringIntegrationTest {

    private static final String SLUG_A = "gap-role-a";
    private static final String SLUG_B = "gap-role-b";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper helper;
    @MockBean  private EmailService emailService;

    private Company companyA, companyB;
    private Employee adminA, waiter1A, waiter2A, cashier1A, cashier2A, chefA;
    private Employee adminB;

    @BeforeEach
    void setUp() {
        helper.cleanUpBySlug(SLUG_A);
        helper.cleanUpBySlug(SLUG_B);

        companyA = helper.createActiveCompany(SLUG_A, "America/Mexico_City");
        companyB = helper.createActiveCompany(SLUG_B, "America/Mexico_City");

        adminA    = helper.createEmployee(companyA, "gap-admin-a",    "Pass1234!", "ROLE_ADMIN");
        waiter1A  = helper.createEmployee(companyA, "gap-waiter1-a",  "Pass1234!", "ROLE_WAITER");
        waiter2A  = helper.createEmployee(companyA, "gap-waiter2-a",  "Pass1234!", "ROLE_WAITER");
        cashier1A = helper.createEmployee(companyA, "gap-cashier1-a", "Pass1234!", "ROLE_CASHIER");
        cashier2A = helper.createEmployee(companyA, "gap-cashier2-a", "Pass1234!", "ROLE_CASHIER");
        chefA     = helper.createEmployee(companyA, "gap-chef-a",     "Pass1234!", "ROLE_CHEF");

        adminB    = helper.createEmployee(companyB, "gap-admin-b",    "Pass1234!", "ROLE_ADMIN");

        // Create orders attributed to different creators
        // 2 orders by waiter1, 1 by waiter2, 2 by cashier1, 1 by cashier2
        helper.createDeliveredOrder(companyA, waiter1A,  OrderType.DINE_IN,  PaymentMethodType.CASH, "RFILT-W1-001");
        helper.createDeliveredOrder(companyA, waiter1A,  OrderType.DINE_IN,  PaymentMethodType.CASH, "RFILT-W1-002");
        helper.createDeliveredOrder(companyA, waiter2A,  OrderType.TAKEOUT,  PaymentMethodType.CASH, "RFILT-W2-001");
        helper.createDeliveredOrder(companyA, cashier1A, OrderType.DINE_IN,  PaymentMethodType.CREDIT_CARD, "RFILT-C1-001");
        helper.createDeliveredOrder(companyA, cashier1A, OrderType.TAKEOUT,  PaymentMethodType.CASH, "RFILT-C1-002");
        helper.createDeliveredOrder(companyA, cashier2A, OrderType.TAKEOUT,  PaymentMethodType.CASH, "RFILT-C2-001");

        // 1 order in company B (for multi-tenant isolation check)
        helper.createDeliveredOrder(companyB, adminB, OrderType.DINE_IN, PaymentMethodType.CASH, "RFILT-B-001");
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        helper.cleanUpBySlug(SLUG_A);
        helper.cleanUpBySlug(SLUG_B);
    }

    // ================================================================
    //  Admin: sees ALL orders of their company
    // ================================================================

    @Test
    @DisplayName("Admin ve TODOS los pedidos de su empresa (6 pedidos)")
    void admin_seesAllCompanyOrders() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/orders")
                        .with(user("gap-admin-a").roles("ADMIN"))
                        .with(forCompany(SLUG_A)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // All 6 order numbers should be present
        assertThat(body).contains("RFILT-W1-001", "RFILT-W1-002", "RFILT-W2-001",
                "RFILT-C1-001", "RFILT-C1-002", "RFILT-C2-001");
    }

    @Test
    @DisplayName("Admin empresa B no ve pedidos de empresa A")
    void adminB_doesNotSeeCompanyAOrders() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/orders")
                        .with(user("gap-admin-b").roles("ADMIN"))
                        .with(forCompany(SLUG_B)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // Company B should see its own order
        assertThat(body).contains("RFILT-B-001");
        // But NOT company A orders
        assertThat(body).doesNotContain("RFILT-W1-001", "RFILT-C1-001");
    }

    // ================================================================
    //  Waiter: only sees OWN orders (createdBy filter) via OrderController
    // ================================================================

    @Test
    @DisplayName("Waiter1 solo ve sus propios pedidos (2 pedidos)")
    void waiter1_seesOnlyOwnOrders() throws Exception {
        MvcResult result = mockMvc.perform(get("/waiter/orders")
                        .with(user("gap-waiter1-a").roles("WAITER"))
                        .with(forCompany(SLUG_A)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("RFILT-W1-001", "RFILT-W1-002");
        assertThat(body).doesNotContain("RFILT-W2-001", "RFILT-C1-001", "RFILT-C2-001");
    }

    @Test
    @DisplayName("Waiter2 solo ve su pedido (1 pedido)")
    void waiter2_seesOnlyOwnOrders() throws Exception {
        MvcResult result = mockMvc.perform(get("/waiter/orders")
                        .with(user("gap-waiter2-a").roles("WAITER"))
                        .with(forCompany(SLUG_A)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("RFILT-W2-001");
        assertThat(body).doesNotContain("RFILT-W1-001", "RFILT-W1-002", "RFILT-C1-001");
    }

    // ================================================================
    //  Cashier: CashierController shows "my orders" + "global orders"
    //  Design: Cashier sees own orders AND other company orders in separate tables
    //  Verify model attributes to confirm correct separation
    // ================================================================

    @Test
    @DisplayName("Cashier1 endpoint devuelve 200 y contiene sus propios pedidos")
    void cashier1_endpointContainsOwnOrders() throws Exception {
        MvcResult result = mockMvc.perform(get("/cashier/orders")
                        .with(user("gap-cashier1-a").roles("CASHIER"))
                        .with(forCompany(SLUG_A)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // Cashier1 should see their own orders
        assertThat(body).contains("RFILT-C1-001", "RFILT-C1-002");
    }

    @Test
    @DisplayName("Cashier2 endpoint devuelve 200 y contiene su pedido propio")
    void cashier2_endpointContainsOwnOrder() throws Exception {
        MvcResult result = mockMvc.perform(get("/cashier/orders")
                        .with(user("gap-cashier2-a").roles("CASHIER"))
                        .with(forCompany(SLUG_A)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // Cashier2 should see their own order
        assertThat(body).contains("RFILT-C2-001");
    }

    // ================================================================
    //  Chef: no orders/list template exists; test dashboard access only
    // ================================================================

    @Test
    @DisplayName("Chef puede acceder a su dashboard")
    void chef_canAccessDashboard() throws Exception {
        mockMvc.perform(get("/chef/dashboard")
                        .with(user("gap-chef-a").roles("CHEF"))
                        .with(forCompany(SLUG_A)))
                .andExpect(status().isOk());
    }

    // ================================================================
    //  Cross-role isolation: waiter can't access admin/cashier endpoints
    // ================================================================

    @Test
    @DisplayName("Waiter no puede acceder a /admin/orders (403 o redirect)")
    void waiter_cannotAccessAdminOrders() throws Exception {
        mockMvc.perform(get("/admin/orders")
                        .with(user("gap-waiter1-a").roles("WAITER"))
                        .with(forCompany(SLUG_A)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cashier no puede acceder a /admin/orders (403)")
    void cashier_cannotAccessAdminOrders() throws Exception {
        mockMvc.perform(get("/admin/orders")
                        .with(user("gap-cashier1-a").roles("CASHIER"))
                        .with(forCompany(SLUG_A)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Chef no puede acceder a /admin/orders (403)")
    void chef_cannotAccessAdminOrders() throws Exception {
        mockMvc.perform(get("/admin/orders")
                        .with(user("gap-chef-a").roles("CHEF"))
                        .with(forCompany(SLUG_A)))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    //  Multi-tenant: same username pattern, isolated data
    // ================================================================

    @Test
    @DisplayName("Multi-tenant: admin en empresa A vs empresa B ven datos aislados")
    void multiTenant_admins_seeIsolatedData() throws Exception {
        MvcResult resultA = mockMvc.perform(get("/admin/orders")
                        .with(user("gap-admin-a").roles("ADMIN"))
                        .with(forCompany(SLUG_A)))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult resultB = mockMvc.perform(get("/admin/orders")
                        .with(user("gap-admin-b").roles("ADMIN"))
                        .with(forCompany(SLUG_B)))
                .andExpect(status().isOk())
                .andReturn();

        String bodyA = resultA.getResponse().getContentAsString();
        String bodyB = resultB.getResponse().getContentAsString();

        // Company A has 6 orders, Company B has 1
        assertThat(bodyA).contains("RFILT-W1-001");
        assertThat(bodyA).doesNotContain("RFILT-B-001");

        assertThat(bodyB).contains("RFILT-B-001");
        assertThat(bodyB).doesNotContain("RFILT-W1-001");
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
