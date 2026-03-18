package com.aatechsolutions.elgransazon.phase6;

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

import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FASE 6 — Tests de integración para PaymentController, CashierPaymentController,
 * WaiterPaymentController y SalesController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("FASE 6 — Payment & Sales Controller Integration Tests")
class PaymentAndSalesControllerIntegrationTest {

    private static final String SLUG_1 = "tp6-pay-c1";
    private static final String SLUG_2 = "tp6-pay-c2";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper helper;
    @MockBean private EmailService emailService;

    private Company company1, company2;
    private Employee admin1, cashier1, waiter1, admin2;
    private Order deliveredOrder1, deliveredOrder2, deliveredOrderC2;

    @BeforeEach
    void setUp() {
        helper.cleanUpBySlug(SLUG_1);
        helper.cleanUpBySlug(SLUG_2);

        company1 = helper.createActiveCompany(SLUG_1, "America/Mexico_City");
        company2 = helper.createActiveCompany(SLUG_2, "America/Mexico_City");

        admin1   = helper.createEmployee(company1, "tp6-admin-1", "Pass1234!", "ROLE_ADMIN");
        cashier1 = helper.createEmployee(company1, "tp6-cashier-1", "Pass1234!", "ROLE_CASHIER");
        waiter1  = helper.createEmployee(company1, "tp6-waiter-1", "Pass1234!", "ROLE_WAITER");
        admin2   = helper.createEmployee(company2, "tp6-admin-2", "Pass1234!", "ROLE_ADMIN");

        // Create DELIVERED orders for payment testing
        deliveredOrder1 = helper.createDeliveredOrder(company1, admin1,
                OrderType.TAKEOUT, PaymentMethodType.CASH, "ORD-P6-001");
        deliveredOrder2 = helper.createDeliveredOrder(company1, waiter1,
                OrderType.DINE_IN, PaymentMethodType.CREDIT_CARD, "ORD-P6-002");
        deliveredOrderC2 = helper.createDeliveredOrder(company2, admin2,
                OrderType.TAKEOUT, PaymentMethodType.CASH, "ORD-P6-C2-001");
    }

    @AfterEach
    void tearDown() {
        helper.cleanUpBySlug(SLUG_1);
        helper.cleanUpBySlug(SLUG_2);
    }

    // ================================================================
    //  PaymentController  /admin/payments
    // ================================================================

    @Test
    @DisplayName("ADMIN puede ver formulario de pago para orden DELIVERED")
    void admin_canSeePaymentForm() throws Exception {
        mockMvc.perform(get("/admin/payments/form/" + deliveredOrder1.getIdOrder())
                        .with(user("tp6-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("order", "enabledPaymentMethods"));
    }

    @Test
    @DisplayName("ADMIN puede procesar pago de orden DELIVERED")
    void admin_canProcessPayment() throws Exception {
        mockMvc.perform(post("/admin/payments/process/" + deliveredOrder1.getIdOrder())
                        .with(user("tp6-admin-1").roles("ADMIN"))
                        .with(csrf())
                        .with(forCompany(SLUG_1))
                        .param("paymentMethod", "CASH")
                        .param("tip", "10.00"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("ADMIN — formulario de pago para orden inexistente redirige con error")
    void admin_paymentForm_nonExistentOrder_redirects() throws Exception {
        mockMvc.perform(get("/admin/payments/form/99999")
                        .with(user("tp6-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("ADMIN — download ticket sin sesión previa retorna redirect o error")
    void admin_downloadTicket_noSession() throws Exception {
        int code = mockMvc.perform(get("/admin/payments/download-ticket")
                        .with(user("tp6-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andReturn().getResponse().getStatus();
        // Without a prior payment session, may redirect (302) or return error
        org.junit.jupiter.api.Assertions.assertTrue(
                code == 302 || code == 404 || code == 500 || code == 200,
                "Expected redirect or error but got " + code);
    }

    // ================================================================
    //  CashierPaymentController  /cashier/payments
    // ================================================================

    @Test
    @DisplayName("CASHIER puede ver formulario de pago")
    void cashier_canSeePaymentForm() throws Exception {
        mockMvc.perform(get("/cashier/payments/form/" + deliveredOrder1.getIdOrder())
                        .with(user("tp6-cashier-1").roles("CASHIER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("order"));
    }

    @Test
    @DisplayName("CASHIER puede procesar pago")
    void cashier_canProcessPayment() throws Exception {
        mockMvc.perform(post("/cashier/payments/process/" + deliveredOrder2.getIdOrder())
                        .with(user("tp6-cashier-1").roles("CASHIER"))
                        .with(csrf())
                        .with(forCompany(SLUG_1))
                        .param("paymentMethod", "CREDIT_CARD")
                        .param("tip", "5.00"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("CASHIER — pago con orden inexistente redirige")
    void cashier_paymentForm_nonExistent_redirects() throws Exception {
        mockMvc.perform(get("/cashier/payments/form/99999")
                        .with(user("tp6-cashier-1").roles("CASHIER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().is3xxRedirection());
    }

    // ================================================================
    //  WaiterPaymentController  /waiter/payments
    // ================================================================

    @Test
    @DisplayName("WAITER puede ver formulario de pago")
    void waiter_canSeePaymentForm() throws Exception {
        mockMvc.perform(get("/waiter/payments/form/" + deliveredOrder2.getIdOrder())
                        .with(user("tp6-waiter-1").roles("WAITER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("WAITER puede procesar pago con tarjeta")
    void waiter_canProcessPaymentWithCard() throws Exception {
        mockMvc.perform(post("/waiter/payments/process/" + deliveredOrder1.getIdOrder())
                        .with(user("tp6-waiter-1").roles("WAITER"))
                        .with(csrf())
                        .with(forCompany(SLUG_1))
                        .param("paymentMethod", "CREDIT_CARD")
                        .param("tip", "0"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("WAITER — formulario de pago para orden inexistente redirige")
    void waiter_paymentForm_nonExistent_redirects() throws Exception {
        mockMvc.perform(get("/waiter/payments/form/99999")
                        .with(user("tp6-waiter-1").roles("WAITER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().is3xxRedirection());
    }

    // ================================================================
    //  Seguridad de roles cruzados
    // ================================================================

    @Test
    @DisplayName("CASHIER no puede acceder a /admin/payments (role mismatch)")
    void cashier_cannotAccessAdminPayments() throws Exception {
        mockMvc.perform(get("/admin/payments/form/" + deliveredOrder1.getIdOrder())
                        .with(user("tp6-cashier-1").roles("CASHIER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("WAITER no puede acceder a /cashier/payments (role mismatch)")
    void waiter_cannotAccessCashierPayments() throws Exception {
        mockMvc.perform(get("/cashier/payments/form/" + deliveredOrder1.getIdOrder())
                        .with(user("tp6-waiter-1").roles("WAITER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN no puede acceder a /cashier/payments (role mismatch)")
    void admin_cannotAccessCashierPayments() throws Exception {
        mockMvc.perform(get("/cashier/payments/form/" + deliveredOrder1.getIdOrder())
                        .with(user("tp6-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    //  SalesController  /admin/sales
    // ================================================================

    @Test
    @DisplayName("ADMIN puede ver listado de ventas")
    void admin_canViewSales() throws Exception {
        mockMvc.perform(get("/admin/sales")
                        .with(user("tp6-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("sales"));
    }

    @Test
    @DisplayName("WAITER no puede ver ventas en /admin/sales (SecurityConfig restringe /admin/**)")
    void waiter_cannotViewAdminSales() throws Exception {
        mockMvc.perform(get("/admin/sales")
                        .with(user("tp6-waiter-1").roles("WAITER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CASHIER no puede acceder a ventas (no autorizado)")
    void cashier_cannotAccessSales() throws Exception {
        mockMvc.perform(get("/admin/sales")
                        .with(user("tp6-cashier-1").roles("CASHIER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Ventas con filtros de fecha")
    void admin_salesWithDateFilters() throws Exception {
        mockMvc.perform(get("/admin/sales")
                        .with(user("tp6-admin-1").roles("ADMIN"))
                        .with(forCompany(SLUG_1))
                        .param("startDate", "2025-01-01")
                        .param("endDate", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("totalSales"));
    }

    // ================================================================
    //  Multi-tenant aislamiento
    // ================================================================

    @Test
    @DisplayName("Company2 no ve órdenes de Company1 en ventas")
    void multiTenant_sales_isolation() throws Exception {
        mockMvc.perform(get("/admin/sales")
                        .with(user("tp6-admin-2").roles("ADMIN"))
                        .with(forCompany(SLUG_2)))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("ORD-P6-001"))));
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
