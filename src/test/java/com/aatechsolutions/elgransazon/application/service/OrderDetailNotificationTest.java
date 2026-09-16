package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.presentation.dto.OrderNotificationDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The order detail views refresh the order badge and every item badge from a single
 * WebSocket snapshot. That snapshot must be company scoped and must carry the id and the
 * status of each item, otherwise the page would fall back to guessing from the order
 * status alone.
 */
class OrderDetailNotificationTest {

    private static final Long COMPANY_ID = 7L;

    private Order orderWithItems() {
        Company company = new Company();
        company.setIdCompany(COMPANY_ID);

        ItemMenu tacos = ItemMenu.builder().name("Tacos").requiresPreparation(true).build();
        OrderDetail detail = OrderDetail.builder()
                .idOrderDetail(501L)
                .itemMenu(tacos)
                .itemName("Tacos")
                .quantity(2)
                .itemStatus(OrderStatus.IN_PREPARATION)
                .build();

        return Order.builder()
                .idOrder(84L)
                .orderNumber("ORD-20260913-005")
                .company(company)
                .status(OrderStatus.IN_PREPARATION)
                .orderDetails(new ArrayList<>(List.of(detail)))
                .build();
    }

    @Test
    @DisplayName("El snapshot del detalle va al topic de la empresa y lleva el estado de cada item")
    void detailSnapshotCarriesItemStatuses() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        WebSocketNotificationService service = new WebSocketNotificationService(template);

        service.notifyOrderDetailUpdate(orderWithItems(), "Estados de items actualizados");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq("/topic/orders/detail/" + COMPANY_ID), captor.capture());
        OrderNotificationDTO payload = (OrderNotificationDTO) captor.getValue();

        assertThat(payload.getNotificationType()).isEqualTo("ORDER_DETAIL");
        assertThat(payload.getOrderId()).isEqualTo(84L);
        assertThat(payload.getStatus()).isEqualTo(OrderStatus.IN_PREPARATION);
        assertThat(payload.getItems()).hasSize(1);
        assertThat(payload.getItems().get(0).getIdOrderDetail()).isEqualTo(501L);
        assertThat(payload.getItems().get(0).getItemStatus()).isEqualTo(OrderStatus.IN_PREPARATION);
    }
}
