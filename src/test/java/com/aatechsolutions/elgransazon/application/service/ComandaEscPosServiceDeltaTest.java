package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.PrinterType;
import com.aatechsolutions.elgransazon.domain.repository.OrderDetailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comanda printing rules for paper tickets:
 * a station ticket must carry ONLY the items that were added after the last printed
 * comanda, because paper has no live status (the previous ones are already in the station).
 */
class ComandaEscPosServiceDeltaTest {

    private static final Charset CP1252 = Charset.forName("Cp1252");
    private static final Long ORDER_ID = 7L;

    private OrderDetailRepository orderDetailRepository;
    private ComandaEscPosService service;

    @BeforeEach
    void setUp() {
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        DateTimeService dateTimeService = mock(DateTimeService.class);
        orderDetailRepository = mock(OrderDetailRepository.class);
        service = new ComandaEscPosService(systemConfigurationService, dateTimeService, orderDetailRepository);
    }

    private OrderDetail detail(Long id, String name, String preparationSnapshot, OrderStatus status) {
        OrderDetail d = OrderDetail.builder()
                .quantity(1)
                .unitPrice(new BigDecimal("100.00"))
                .subtotal(new BigDecimal("100.00"))
                .itemName(name)
                .itemStatus(status)
                .preparationTypeSnapshot(preparationSnapshot)
                .build();
        d.setIdOrderDetail(id);
        return d;
    }

    private Order orderWith(OrderDetail... details) {
        return Order.builder()
                .idOrder(ORDER_ID)
                .orderNumber("TEST-7")
                .orderDetails(new ArrayList<>(List.of(details)))
                .build();
    }

    @Test
    @DisplayName("La comanda de adición solo lleva el item nuevo, no lo ya impreso")
    void deltaComandaOnlyPrintsNewItems() throws Exception {
        OrderDetail boneles = detail(1L, "Boneles", "CHEF", OrderStatus.IN_PREPARATION);
        OrderDetail coca = detail(2L, "Coca", "BARISTA", OrderStatus.READY);
        OrderDetail aguachile = detail(3L, "Aguachile", "CHEF", OrderStatus.PENDING);

        // The first comanda already went out with boneles + coca
        boneles.markComandaPrintedFor(PrinterType.KITCHEN);
        coca.markComandaPrintedFor(PrinterType.BAR);

        Order order = orderWith(boneles, coca, aguachile);

        List<OrderDetail> pending = service.pendingItems(order, PrinterType.KITCHEN);

        assertThat(pending).containsExactly(aguachile);

        String ticket = new String(service.generateComanda(order, PrinterType.KITCHEN, pending, true), CP1252);

        assertThat(ticket).contains("NUEVO PEDIDO");
        assertThat(ticket).contains("Aguachile");
        assertThat(ticket).doesNotContain("Boneles");
        assertThat(ticket).doesNotContain("Coca");
        // En papel ya no se imprimen estatus: eso vive en la tablet/KDS
        assertThat(ticket).doesNotContain("PENDIENTE");
        assertThat(ticket).doesNotContain("EN PREPARACION");
        assertThat(ticket).doesNotContain("[ ");
    }

    @Test
    @DisplayName("Nombres de platillo y comentarios largos salen completos, con salto de línea")
    void longNamesAndCommentsAreNeverCut() throws Exception {
        OrderDetail chilaquiles = detail(1L, "Chilaquiles verdes con pollo", "CHEF", OrderStatus.PENDING);
        chilaquiles.setComments("sin cebolla y con salsa extra bien picante");

        Order order = orderWith(chilaquiles);

        String ticket = new String(service.generateComanda(order, PrinterType.KITCHEN,
                service.pendingItems(order, PrinterType.KITCHEN), true), CP1252);
        // Al rearmar los saltos de línea, el nombre y el comentario deben aparecer completos
        String flat = ticket.replaceAll("\\s+", " ");

        assertThat(flat).contains("Chilaquiles verdes con pollo");
        assertThat(flat).contains("sin cebolla y con salsa extra bien picante");
        // El recorte anterior dejaba "Chilaquiles verdes con."
        assertThat(ticket).doesNotContain("con.");
    }

    @Test
    @DisplayName("El comentario de un combo sale una sola vez, al final del grupo")
    void comboCommentIsPrintedOnceAtTheBottom() throws Exception {
        OrderDetail comboParent = detail(1L, "Combo Familiar", null, OrderStatus.READY);
        comboParent.setIsComboParentSnapshot(true);
        comboParent.setComboGroupId("combo_1");
        comboParent.setComments("sin cebolla");

        OrderDetail burger = detail(2L, "Hamburguesa", "CHEF", OrderStatus.PENDING);
        burger.setComboGroupId("combo_1");
        burger.setIsComboParentSnapshot(false);
        burger.setComments("sin cebolla");

        OrderDetail papas = detail(3L, "Papas a la francesa", "CHEF", OrderStatus.PENDING);
        papas.setComboGroupId("combo_1");
        papas.setIsComboParentSnapshot(false);
        papas.setComments("sin cebolla");

        // Un item suelto fuera del combo conserva su propio comentario
        OrderDetail aguachile = detail(4L, "Aguachile", "CHEF", OrderStatus.PENDING);
        aguachile.setComments("bien picante");

        Order order = orderWith(comboParent, burger, papas, aguachile);

        String ticket = new String(service.generateComanda(order, PrinterType.KITCHEN,
                service.pendingItems(order, PrinterType.KITCHEN), true), CP1252);

        assertThat(countOccurrences(ticket, "sin cebolla")).isEqualTo(1);
        assertThat(ticket.indexOf("sin cebolla"))
                .isGreaterThan(ticket.indexOf("Papas a la francesa"));
        assertThat(ticket).contains("bien picante");
        // El padre del combo no se imprime en la comanda, solo sus hijos
        assertThat(ticket).doesNotContain("Combo Familiar");
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    @Test
    @DisplayName("Los items por aceptar y los de otra estación no entran en la comanda de adición")
    void pendingItemsSkipsToAcceptAndOtherStations() {
        OrderDetail sopa = detail(1L, "Sopa", "CHEF", OrderStatus.PENDING);
        OrderDetail toAccept = detail(2L, "Tostada", "CHEF", OrderStatus.TO_ACCEPT);
        OrderDetail cerveza = detail(3L, "Cerveza", "BARISTA", OrderStatus.PENDING);

        Order order = orderWith(sopa, toAccept, cerveza);

        assertThat(service.pendingItems(order, PrinterType.KITCHEN)).containsExactly(sopa);
        assertThat(service.stationItems(order, PrinterType.BAR)).containsExactly(cerveza);
        assertThat(service.pendingItems(order, PrinterType.PARRILLERO)).isEmpty();
    }

    @Test
    @DisplayName("El ack marca como impreso solo lo que salió en la comanda")
    void markComandaPrintedMarksOnlyAcknowledgedItems() {
        OrderDetail boneles = detail(1L, "Boneles", "CHEF", OrderStatus.PENDING);
        OrderDetail aguachile = detail(3L, "Aguachile", "CHEF", OrderStatus.PENDING);

        Order order = orderWith(boneles, aguachile);
        boneles.setOrder(order);
        aguachile.setOrder(order);

        when(orderDetailRepository.findAllById(List.of(3L))).thenReturn(List.of(aguachile));

        int marked = service.markComandaPrinted(ORDER_ID, PrinterType.KITCHEN, List.of(3L), false);

        assertThat(marked).isEqualTo(1);
        assertThat(aguachile.isComandaPrintedFor(PrinterType.KITCHEN)).isTrue();
        assertThat(boneles.isComandaPrintedFor(PrinterType.KITCHEN)).isFalse();
        verify(orderDetailRepository).saveAll(List.of(aguachile));
    }

    @Test
    @DisplayName("La comanda completa marca todos los items de la estación")
    void markAllMarksEveryStationItem() {
        OrderDetail boneles = detail(1L, "Boneles", "CHEF", OrderStatus.PENDING);
        OrderDetail aguachile = detail(3L, "Aguachile", "CHEF", OrderStatus.PENDING);
        OrderDetail cerveza = detail(4L, "Cerveza", "BARISTA", OrderStatus.PENDING);

        Order order = orderWith(boneles, aguachile, cerveza);

        when(orderDetailRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(boneles, aguachile, cerveza));

        int marked = service.markComandaPrinted(ORDER_ID, PrinterType.KITCHEN, null, true);

        assertThat(marked).isEqualTo(2);
        assertThat(boneles.isComandaPrintedFor(PrinterType.KITCHEN)).isTrue();
        assertThat(aguachile.isComandaPrintedFor(PrinterType.KITCHEN)).isTrue();
        assertThat(cerveza.isComandaPrintedFor(PrinterType.KITCHEN)).isFalse();
    }
}
