package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.ComandaEscPosService;
import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.application.service.PrintClaimService;
import com.aatechsolutions.elgransazon.application.service.PrinterService;
import com.aatechsolutions.elgransazon.application.service.TicketEscPosService;
import com.aatechsolutions.elgransazon.application.service.WebSocketNotificationService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.Payment;
import com.aatechsolutions.elgransazon.domain.repository.PaymentRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Split-bill accounts are printed one ticket per person: the endpoint must serve the account
 * of the requested order, never another company's account, and the claim must let only one
 * PC print it.
 */
class PrinterControllerAccountTicketTest {

    private static final long ORDER_ID = 84L;
    private static final long PAYMENT_ID = 501L;
    private static final long COMPANY_ID = 2L;

    private PaymentRepository paymentRepository;
    private TicketEscPosService ticketEscPosService;
    private PrintClaimService printClaimService;
    private OrderService adminOrderService;
    private WebSocketNotificationService wsNotificationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        ticketEscPosService = mock(TicketEscPosService.class);
        printClaimService = new PrintClaimService();
        adminOrderService = mock(OrderService.class);
        wsNotificationService = mock(WebSocketNotificationService.class);
        PrinterController controller = new PrinterController(
                mock(PrinterService.class),
                mock(ComandaEscPosService.class),
                ticketEscPosService,
                adminOrderService,
                printClaimService,
                paymentRepository,
                wsNotificationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        CompanyContext.setCurrentCompany(company(COMPANY_ID));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private Company company(long id) {
        Company company = new Company();
        company.setIdCompany(id);
        return company;
    }

    private Payment paymentOf(long orderId, long companyId) {
        Order order = Order.builder().idOrder(orderId).orderNumber("ORD-1").build();
        Payment payment = new Payment();
        payment.setIdPayment(PAYMENT_ID);
        payment.setPaymentFolio("F-501");
        payment.setOrder(order);
        payment.setCompany(company(companyId));
        return payment;
    }

    @Test
    @DisplayName("Descarga el ticket de la cuenta del pedido")
    void downloadsAccountTicket() throws Exception {
        when(paymentRepository.findByIdWithDetails(PAYMENT_ID))
                .thenReturn(Optional.of(paymentOf(ORDER_ID, COMPANY_ID)));
        when(ticketEscPosService.generateTicket(org.mockito.ArgumentMatchers.any(Payment.class)))
                .thenReturn(new byte[]{0x1B, 0x40, 0x0A});

        byte[] body = mockMvc.perform(get("/api/print/ticket/" + ORDER_ID + "/payment/" + PAYMENT_ID))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).containsExactly(0x1B, 0x40, 0x0A);
    }

    @Test
    @DisplayName("Una cuenta de otro pedido no se imprime")
    void accountFromAnotherOrderIsNotFound() throws Exception {
        when(paymentRepository.findByIdWithDetails(PAYMENT_ID))
                .thenReturn(Optional.of(paymentOf(999L, COMPANY_ID)));

        mockMvc.perform(get("/api/print/ticket/" + ORDER_ID + "/payment/" + PAYMENT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Una cuenta de otra empresa no se imprime (multi-tenant)")
    void accountFromAnotherCompanyIsNotFound() throws Exception {
        when(paymentRepository.findByIdWithDetails(PAYMENT_ID))
                .thenReturn(Optional.of(paymentOf(ORDER_ID, 99L)));

        mockMvc.perform(get("/api/print/ticket/" + ORDER_ID + "/payment/" + PAYMENT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("El claim de una cuenta lo gana una sola PC")
    void accountClaimIsExactlyOnce() throws Exception {
        mockMvc.perform(post("/api/print/ticket/payment/" + PAYMENT_ID + "/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim").value(true));

        mockMvc.perform(post("/api/print/ticket/payment/" + PAYMENT_ID + "/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim").value(false));

        // A different account keeps its own claim
        mockMvc.perform(post("/api/print/ticket/payment/502/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim").value(true));
    }

    @Test
    @DisplayName("El claim del pedido completo no afecta al de las cuentas")
    void orderAndAccountClaimsAreIndependent() throws Exception {
        mockMvc.perform(post("/api/print/ticket/" + ORDER_ID + "/claim"))
                .andExpect(jsonPath("$.claim").value(true));

        mockMvc.perform(post("/api/print/ticket/payment/" + PAYMENT_ID + "/claim"))
                .andExpect(jsonPath("$.claim").value(true));
    }

    @Test
    @DisplayName("Si la PC que cobró no puede imprimir, suelta el ticket y el agente lo toma")
    void releasedOrderClaimCanBeTakenByAnAgent() throws Exception {
        mockMvc.perform(post("/api/print/ticket/" + ORDER_ID + "/claim"))
                .andExpect(jsonPath("$.claim").value(true));

        // La impresión local falló: el ticket vuelve a quedar libre
        mockMvc.perform(post("/api/print/ticket/" + ORDER_ID + "/release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.released").value(true));

        mockMvc.perform(post("/api/print/ticket/" + ORDER_ID + "/claim"))
                .andExpect(jsonPath("$.claim").value(true));
    }

    @Test
    @DisplayName("Si no se imprime una cuenta, se suelta para que la tome el agente")
    void releasedAccountClaimCanBeTakenByAnAgent() throws Exception {
        mockMvc.perform(post("/api/print/ticket/payment/" + PAYMENT_ID + "/claim"))
                .andExpect(jsonPath("$.claim").value(true));

        mockMvc.perform(post("/api/print/ticket/payment/" + PAYMENT_ID + "/release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.released").value(true));

        mockMvc.perform(post("/api/print/ticket/payment/" + PAYMENT_ID + "/claim"))
                .andExpect(jsonPath("$.claim").value(true));
    }

    @Test
    @DisplayName("Al soltar una cuenta la PC que falló, el ticket pasa a los agentes")
    void releasedAccountIsHandedToTheAgents() throws Exception {
        Payment payment = paymentOf(ORDER_ID, COMPANY_ID);
        when(paymentRepository.findByIdWithDetails(PAYMENT_ID)).thenReturn(Optional.of(payment));

        mockMvc.perform(post("/api/print/ticket/payment/" + PAYMENT_ID + "/release"))
                .andExpect(status().isOk());

        verify(wsNotificationService).notifyPrintTicketToAgents(
                eq(payment.getOrder()), eq(java.util.List.of(PAYMENT_ID)), anyString());
    }

    @Test
    @DisplayName("Al soltar el ticket del pedido, los agentes lo reciben de inmediato")
    void releasedOrderTicketIsHandedToTheAgents() throws Exception {
        Order order = Order.builder().idOrder(ORDER_ID).orderNumber("ORD-1").build();
        when(adminOrderService.findByIdWithDetails(ORDER_ID)).thenReturn(Optional.of(order));

        mockMvc.perform(post("/api/print/ticket/" + ORDER_ID + "/release"))
                .andExpect(status().isOk());

        verify(wsNotificationService).notifyPrintTicketToAgents(
                eq(order), eq(java.util.List.of()), anyString());
    }
}
