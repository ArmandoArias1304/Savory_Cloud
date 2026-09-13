package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.presentation.dto.PrintTicketNotificationDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Ticket print events must carry the accounts of a split bill so the agent prints one ticket
 * per person, and must stay a whole-order event for a normal charge.
 */
class WebSocketNotificationServicePrintTest {

    private static final Long COMPANY_ID = 2L;

    private Order paidOrder() {
        Company company = new Company();
        company.setIdCompany(COMPANY_ID);
        Order order = Order.builder()
                .idOrder(84L)
                .orderNumber("ORD-20260913-005")
                .company(company)
                .build();
        return order;
    }

    private PrintTicketNotificationDTO sent() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        WebSocketNotificationService service = new WebSocketNotificationService(template);

        service.notifyPrintTicket(paidOrder(), "mesero");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq("/topic/print/ticket/" + COMPANY_ID), captor.capture());
        return (PrintTicketNotificationDTO) captor.getValue();
    }

    @Test
    @DisplayName("Cobro normal: el evento no trae cuentas (ticket del pedido completo)")
    void normalChargeSendsWholeOrderTicket() {
        PrintTicketNotificationDTO payload = sent();

        assertThat(payload.getOrderId()).isEqualTo(84L);
        assertThat(payload.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(payload.getPaymentIds()).isEmpty();
    }

    @Test
    @DisplayName("Cuentas divididas: el evento lleva cada cuenta para imprimir una por persona")
    void splitChargeSendsOneTicketPerAccount() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        WebSocketNotificationService service = new WebSocketNotificationService(template);
        List<Long> paymentIds = new ArrayList<>(List.of(11L, 12L));

        service.notifyPrintTicketAccounts(paidOrder(), paymentIds, "mesero");
        // The caller keeps mutating its own list: the payload must not follow it
        paymentIds.add(13L);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq("/topic/print/ticket/" + COMPANY_ID), captor.capture());
        PrintTicketNotificationDTO payload = (PrintTicketNotificationDTO) captor.getValue();

        assertThat(payload.getPaymentIds()).containsExactly(11L, 12L);
        assertThat(payload.getOrderNumber()).isEqualTo("ORD-20260913-005");
    }

    @Test
    @DisplayName("Sin cuentas no se manda ningún evento de cuentas")
    void emptyAccountsSendNothing() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        WebSocketNotificationService service = new WebSocketNotificationService(template);

        service.notifyPrintTicketAccounts(paidOrder(), List.of(), "mesero");

        verify(template, never()).convertAndSend(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    @DisplayName("El ticket va a los agentes recién pasado el margen para imprimirlo local")
    void ticketBroadcastWaitsForTheLocalPrintWindow() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        // 5000 ms of local-first window: the charging PC prints before the agents are told
        WebSocketNotificationService service =
                new WebSocketNotificationService(template, scheduler, 5000L);

        service.notifyPrintTicket(paidOrder(), "cajero");

        // Inside the window nothing was broadcast to the agents yet
        verify(template, never()).convertAndSend(anyString(), any(Object.class));

        ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(job.capture(), any(Instant.class));

        // The local PC could not print: the agents take the ticket when the window closes
        job.getValue().run();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq("/topic/print/ticket/" + COMPANY_ID), captor.capture());
        PrintTicketNotificationDTO payload = (PrintTicketNotificationDTO) captor.getValue();
        assertThat(payload.getPaymentIds()).isEmpty();
    }

    @Test
    @DisplayName("El ticket del pedido completo nunca sale cuando hay cuentas")
    void driverAccountIdsAreUsedAsIs() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        WebSocketNotificationService service = new WebSocketNotificationService(template);

        service.notifyPrintTicketAccounts(paidOrder(), List.of(77L), "cajero");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq("/topic/print/ticket/" + COMPANY_ID), captor.capture());
        PrintTicketNotificationDTO payload = (PrintTicketNotificationDTO) captor.getValue();

        assertThat(payload.getPaymentIds()).containsExactly(77L);
    }
}
