package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.PrinterType;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.repository.OrderDetailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The comanda is read standing up, a few steps away from the pass: every tier of the ticket
 * was raised one size step, keeping the same order on paper. The paper is 32 Font A columns
 * wide, so a line may only grow in WIDTH while its text still fits; growth in height is free.
 *
 * Hierarchy under test (biggest first):
 * COMANDA (2x3) &gt; station / NUEVO PEDIDO / PEDIDO &amp; number (2x2) &gt; items, complements and
 * comments (1x2) &gt; restaurant name (1x1).
 */
class ComandaTextSizeTest {

    /** GS ! n: bits 7-4 = width multiplier - 1, bits 3-0 = height multiplier - 1. */
    private static final int SIZE_NORMAL = 0x00; // 1 x 1
    private static final int SIZE_STRONG = 0x01; // 1 x 2
    private static final int SIZE_HEADER = 0x11; // 2 x 2
    private static final int SIZE_TITLE = 0x12;  // 2 x 3

    private static final Charset CP1252 = Charset.forName("Cp1252");

    private ComandaEscPosService service;

    @BeforeEach
    void setUp() {
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(systemConfigurationService.getConfiguration()).thenReturn(
                SystemConfiguration.builder().restaurantName("El Gran Sazón").build());
        DateTimeService dateTimeService = mock(DateTimeService.class);
        when(dateTimeService.formatToCompanyTime(any(), anyString())).thenReturn("12/09/2026 13:40");
        service = new ComandaEscPosService(systemConfigurationService, dateTimeService,
                mock(OrderDetailRepository.class));
    }

    @Test
    @DisplayName("Cada nivel de la comanda sale un paso más grande y en el mismo orden")
    void everyTierIsBiggerAndKeepsItsPlace() throws Exception {
        List<PrintedLine> lines = printedLines(
                service.generateComanda(kitchenOrder("ORD-20260912-014"), PrinterType.KITCHEN,
                        kitchenItems(), true));

        assertThat(textAt(lines, SIZE_TITLE)).containsExactly("COMANDA");
        assertThat(textAt(lines, SIZE_HEADER)).containsExactly("COCINA", "NUEVO PEDIDO", "PEDIDO",
                "ORD-20260912-014");
        assertThat(textAt(lines, SIZE_STRONG)).anyMatch(l -> l.contains("2x Chilaquiles verdes con"))
                .anyMatch(l -> l.contains("Tocino extra"))
                .anyMatch(l -> l.contains("sin cebolla"));
        // El nombre del restaurante sigue siendo lo más pequeño del ticket: a tamaño normal
        // solo quedan él y las líneas separadoras
        assertThat(textAt(lines, SIZE_NORMAL)).contains("El Gran Sazón");
        assertThat(textAt(lines, SIZE_NORMAL).stream()
                .filter(l -> !l.isBlank() && !l.startsWith("---"))).containsExactly("El Gran Sazón");

        // El ticket sigue leyéndose de mayor a menor: título, encabezado y luego el detalle
        assertThat(firstIndexOf(lines, SIZE_TITLE)).isLessThan(firstIndexOf(lines, SIZE_HEADER));
        assertThat(lastIndexOf(lines, SIZE_HEADER)).isLessThan(firstIndexOf(lines, SIZE_STRONG));
    }

    @Test
    @DisplayName("El número de pedido largo baja a doble alto para que la impresora no lo parta")
    void longOrderNumberFallsBackToDoubleHeight() throws Exception {
        String longNumber = "ORD-20260912-014-EXTRA";
        Order order = Order.builder()
                .idOrder(7L).orderNumber(longNumber).orderType(OrderType.DINE_IN)
                .table(RestaurantTable.builder().tableNumber(7).capacity(4).build())
                .orderDetails(new ArrayList<>(kitchenItems()))
                .build();

        List<PrintedLine> lines = printedLines(service.generateComanda(order, PrinterType.KITCHEN,
                kitchenItems(), false));

        // El número sale completo, en una sola línea, a doble alto (no cabe a doble ancho)
        assertThat(lines).anySatisfy(line -> {
            assertThat(line.text()).isEqualTo(longNumber);
            assertThat(line.size()).isEqualTo(SIZE_STRONG);
        });
        // La etiqueta sigue a doble ancho: el número es el que se adapta
        assertThat(lines).anySatisfy(line -> {
            assertThat(line.text()).isEqualTo("PEDIDO");
            assertThat(line.size()).isEqualTo(SIZE_HEADER);
        });
    }

    @Test
    @DisplayName("Sin mesa el bloque de datos no imprime mesa, y sigue a doble alto")
    void orderInfoIsDoubleHeight() throws Exception {
        Order order = Order.builder()
                .idOrder(7L).orderNumber("ORD-20260912-014").orderType(OrderType.TAKEOUT)
                .employee(Employee.builder().nombre("Luis").apellido("Mesero").build())
                .orderDetails(new ArrayList<>(kitchenItems()))
                .build();

        List<PrintedLine> lines = printedLines(service.generateComanda(order, PrinterType.KITCHEN,
                kitchenItems(), false));

        assertThat(textAt(lines, SIZE_STRONG)).anyMatch(l -> l.startsWith("Tipo:"))
                .contains("Mesero:  Luis Mesero");
        assertThat(textAt(lines, SIZE_STRONG)).noneMatch(l -> l.startsWith("Mesa:"));
    }

    // ========== Helpers ==========

    /** A printed line together with the GS ! size that was active when the printer got it. */
    private record PrintedLine(int size, String text) {
    }

    private List<String> textAt(List<PrintedLine> lines, int size) {
        return lines.stream().filter(l -> l.size() == size).map(PrintedLine::text).toList();
    }

    private int firstIndexOf(List<PrintedLine> lines, int size) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).size() == size) return i;
        }
        return -1;
    }

    private int lastIndexOf(List<PrintedLine> lines, int size) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).size() == size) return i;
        }
        return -1;
    }

    /**
     * Walks the raw ESC/POS bytes tracking the active character size, so each printed line can
     * be asserted together with the size it was printed at.
     */
    private List<PrintedLine> printedLines(byte[] bytes) {
        List<PrintedLine> lines = new ArrayList<>();
        ByteArrayOutputStream current = new ByteArrayOutputStream();
        int size = SIZE_NORMAL;

        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            if (b == 0x1B) { // ESC — command of 2 bytes (ESC @) or 3 bytes (ESC a/E/M/t + arg)
                i += (i + 1 < bytes.length && (bytes[i + 1] & 0xFF) == 0x40) ? 1 : 2;
                continue;
            }
            if (b == 0x1D) { // GS — here always GS ! n (size) or GS V m (cut)
                if (i + 1 < bytes.length && (bytes[i + 1] & 0xFF) == 0x21) {
                    size = bytes[i + 2] & 0xFF;
                }
                i += 2;
                continue;
            }
            if (b == 0x0A) { // LF: the line is complete
                lines.add(new PrintedLine(size, current.toString(CP1252)));
                current.reset();
                continue;
            }
            current.write(b);
        }
        return lines;
    }

    private List<OrderDetail> kitchenItems() {
        OrderDetail chilaquiles = OrderDetail.builder()
                .quantity(2).unitPrice(new BigDecimal("100.00")).subtotal(new BigDecimal("200.00"))
                .itemName("Chilaquiles verdes con pollo")
                .preparationTypeSnapshot("CHEF").itemStatus(OrderStatus.PENDING)
                .build();
        chilaquiles.setIdOrderDetail(1L);
        chilaquiles.setComments("sin cebolla y con salsa extra bien picante");
        chilaquiles.setSelectedComplements(new ArrayList<>(List.of(
                com.aatechsolutions.elgransazon.domain.entity.OrderDetailComplement.builder()
                        .complementName("Tocino extra").quantity(1).build())));

        OrderDetail aguachile = OrderDetail.builder()
                .quantity(1).unitPrice(new BigDecimal("100.00")).subtotal(new BigDecimal("100.00"))
                .itemName("Aguachile")
                .preparationTypeSnapshot("CHEF").itemStatus(OrderStatus.PENDING)
                .build();
        aguachile.setIdOrderDetail(2L);

        return Arrays.asList(chilaquiles, aguachile);
    }

    private Order kitchenOrder(String orderNumber) {
        return Order.builder()
                .idOrder(7L)
                .orderNumber(orderNumber)
                .orderType(OrderType.DINE_IN)
                .table(RestaurantTable.builder().tableNumber(7).capacity(4).build())
                .employee(Employee.builder().nombre("Luis").apellido("Mesero").build())
                .orderDetails(new ArrayList<>(kitchenItems()))
                .build();
    }
}
