package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
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
 * El aviso de "pedido listo para recoger" solo debe hacer saltar la alerta en la pantalla de quien
 * va a recoger ESE pedido.
 *
 * <p>El aviso viaja por un topic compartido —lo reciben a la vez la lista de admin/gerente, la del
 * mesero y la del cajero—, así que cada pantalla necesita saber dos cosas para poder decidir si el
 * aviso es suyo: quién levantó el pedido y quién lo atiende AHORA. Eso es lo que fija esta prueba,
 * porque sin esos dos campos la pantalla no tendría con qué comparar y el aviso o no saldría nunca
 * o le saldría a todo el mundo.</p>
 */
class OrderReadyNotificationOwnerTest {

    private static final Long COMPANY_ID = 7L;

    /** El topic que reciben las tres listas: se manda siempre, cambie lo que cambie. */
    private static final String LIST_TOPIC = "/topic/admin/kitchen/" + COMPANY_ID;

    @Test
    @DisplayName("El aviso lleva quién levantó el pedido y quién lo atiende ahora")
    void statusChangeCarriesTheOwnerAndTheCurrentWaiter() {
        Order order = orderReady();
        order.setCreatedBy("meseroA");
        order.setEmployee(waiter("meseroB"));

        OrderNotificationDTO payload = statusChangeOf(order);

        // El que lo levantó: para admin/gerente y cajero, que solo deben recibir avisos de los
        // pedidos que ellos mismos capturaron.
        assertThat(payload.getCreatedBy()).isEqualTo("meseroA");
        // El que lo atiende ahora: para el mesero, que tras un traspaso ya no es el creador.
        assertThat(payload.getOperatedBy()).isEqualTo("meseroB");
        // Y el tipo ya rotulado, para que el aviso de un pedido sin mesa pueda nombrarlo por su
        // tipo sin que la pantalla repita la traducción.
        assertThat(payload.getOrderTypeLabel()).isEqualTo("Para comer aquí");
        assertThat(payload.getStatus()).isEqualTo(OrderStatus.READY);
        assertThat(payload.getNotificationType()).isEqualTo("STATUS_CHANGE");
    }

    @Test
    @DisplayName("Sin mesero asignado, el responsable es el que levantó el pedido")
    void withoutAnAssignedWaiterTheOwnerIsTheResponsible() {
        Order order = orderReady();
        order.setCreatedBy("meseroA");
        order.setEmployee(null);

        OrderNotificationDTO payload = statusChangeOf(order);

        // Espeja Order.isOperatedBy: sin asignado, manda createdBy, así que la pantalla del
        // mesero lo resuelve sola con lo que le llega.
        assertThat(payload.getOperatedBy()).isNull();
        assertThat(payload.getCreatedBy()).isEqualTo("meseroA");
    }

    @Test
    @DisplayName("El aviso del pedido listo llega a la pantalla de las listas")
    void theReadyNoticeReachesTheListScreens() {
        Order order = orderReady();
        order.setCreatedBy("cajera");
        order.setEmployee(null);

        OrderNotificationDTO payload = statusChangeOf(order);

        // Si no llegara a este topic, las listas no se enterarían de nada y la alerta no sonaría.
        assertThat(payload).isNotNull();
        assertThat(payload.getOrderId()).isEqualTo(90L);
        assertThat(payload.getOrderNumber()).isEqualTo("ORD-20261015-090");
        assertThat(payload.getTableNumber()).isEqualTo(7);
    }

    /** Dispara el aviso y devuelve lo que viajó a la pantalla de las listas. */
    private OrderNotificationDTO statusChangeOf(Order order) {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        WebSocketNotificationService service = new WebSocketNotificationService(template);

        service.notifyOrderStatusChange(order, "Pedido listo para recoger", "chef");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq(LIST_TOPIC), captor.capture());
        return (OrderNotificationDTO) captor.getValue();
    }

    /** Pedido ya LISTO, en la mesa 7, con un ítem de preparación. */
    private Order orderReady() {
        Company company = new Company();
        company.setIdCompany(COMPANY_ID);

        ItemMenu tacos = ItemMenu.builder().name("Tacos").requiresPreparation(true).build();
        OrderDetail detail = OrderDetail.builder()
                .idOrderDetail(501L)
                .itemMenu(tacos)
                .itemName("Tacos")
                .quantity(2)
                .itemStatus(OrderStatus.READY)
                .build();

        com.aatechsolutions.elgransazon.domain.entity.RestaurantTable table =
                new com.aatechsolutions.elgransazon.domain.entity.RestaurantTable();
        table.setId(3L);
        table.setTableNumber(7);

        return Order.builder()
                .idOrder(90L)
                .orderNumber("ORD-20261015-090")
                .company(company)
                .orderType(OrderType.DINE_IN)
                .status(OrderStatus.READY)
                .table(table)
                .orderDetails(new ArrayList<>(List.of(detail)))
                .build();
    }

    private Employee waiter(String username) {
        Employee employee = new Employee();
        employee.setIdEmpleado(2L);
        employee.setUsername(username);
        employee.setNombre("Juan");
        employee.setApellido("Perez");
        employee.setEnabled(true);
        return employee;
    }
}
