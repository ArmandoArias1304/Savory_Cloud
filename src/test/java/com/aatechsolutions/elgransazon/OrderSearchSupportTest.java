package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.util.OrderSearchSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El buscador de pedidos (admin, gerente, cajero y mesero) comparte estas reglas: qué término
 * encuentra qué pedido. Es la parte que no puede cambiar entre roles, así que se prueba aquí y no
 * plantilla por plantilla.
 *
 * <p>El detalle que más fácil se rompe: un término corto de dígitos ("7", "007") es el
 * CONSECUTIVO del día, no un texto que se busque dentro del folio. Si se buscara como subcadena,
 * "007" también encontraría {@code ORD-20261007-012} (la fecha contiene "007") y el resultado
 * saldría lleno de pedidos ajenos.</p>
 */
class OrderSearchSupportTest {

    @Test
    @DisplayName("El folio completo y sus fragmentos largos encuentran el pedido")
    void findsByOrderNumberFragment() {
        Order order = order(1L, "ORD-20260929-007");

        assertThat(OrderSearchSupport.matches(order, "ORD-20260929-007")).isTrue();
        assertThat(OrderSearchSupport.matches(order, "ord-20260929-007")).isTrue();
        assertThat(OrderSearchSupport.matches(order, "20260929-007")).isTrue();
        assertThat(OrderSearchSupport.matches(order, "ORD-20260929-008")).isFalse();
    }

    @Test
    @DisplayName("Un término de 1 a 3 dígitos es el consecutivo del día")
    void shortNumberIsTheDaySequence() {
        Order seven = order(1L, "ORD-20260929-007");
        Order twelve = order(2L, "ORD-20260929-012");

        assertThat(OrderSearchSupport.matches(seven, "7")).isTrue();
        assertThat(OrderSearchSupport.matches(seven, "007")).isTrue();
        assertThat(OrderSearchSupport.matches(twelve, "012")).isTrue();
        assertThat(OrderSearchSupport.matches(twelve, "007")).isFalse();
    }

    @Test
    @DisplayName("El consecutivo no se confunde con los dígitos de la fecha del folio")
    void theSequenceIsNotMatchedInsideTheDate() {
        // "007" aparece dentro de la fecha 20261007: un contains ingenuo devolvería este pedido
        // aunque su consecutivo sea otro.
        Order other = order(3L, "ORD-20261007-012");

        assertThat(OrderSearchSupport.matches(other, "007")).isFalse();
        // Un término largo de dígitos sí viaja como texto del folio: 20261007 es su fecha.
        assertThat(OrderSearchSupport.matches(other, "20261007")).isTrue();
    }

    @Test
    @DisplayName("El id interno del pedido también funciona como número")
    void findsByInternalId() {
        Order order = order(4821L, "ORD-20260929-007");

        assertThat(OrderSearchSupport.matches(order, "4821")).isTrue();
        assertThat(OrderSearchSupport.matches(order, "482")).isFalse();
    }

    @Test
    @DisplayName("El teléfono se compara solo por dígitos, con o sin formato")
    void findsByPhoneIgnoringFormat() {
        Order order = order(1L, "ORD-20260929-007");
        order.setCustomerPhone("5512345678");

        assertThat(OrderSearchSupport.matches(order, "5512345678")).isTrue();
        assertThat(OrderSearchSupport.matches(order, "55 1234 5678")).isTrue();
        assertThat(OrderSearchSupport.matches(order, "+52 55 1234 5678")).isTrue();
        assertThat(OrderSearchSupport.matches(order, "5512345679")).isFalse();
    }

    @Test
    @DisplayName("El nombre del cliente ignora mayúsculas y acentos")
    void findsByCustomerNameFoldingCaseAndAccents() {
        Order order = order(1L, "ORD-20260929-007");
        order.setCustomerName("José Ramírez");

        assertThat(OrderSearchSupport.matches(order, "jose")).isTrue();
        assertThat(OrderSearchSupport.matches(order, "RAMIREZ")).isTrue();
        assertThat(OrderSearchSupport.matches(order, "ramí")).isTrue();
        assertThat(OrderSearchSupport.matches(order, "Gutiérrez")).isFalse();
    }

    @Test
    @DisplayName("Sin término no se angosta nada")
    void blankTermMatchesEverything() {
        Order order = order(1L, "ORD-20260929-007");
        List<Order> orders = List.of(order);

        assertThat(OrderSearchSupport.matches(order, null)).isTrue();
        assertThat(OrderSearchSupport.matches(order, "   ")).isTrue();
        assertThat(OrderSearchSupport.filter(orders, "  ")).isSameAs(orders);
        assertThat(OrderSearchSupport.filter(orders, null)).isSameAs(orders);
        assertThat(OrderSearchSupport.trimToNull("  ")).isNull();
    }

    @Test
    @DisplayName("El filtro conserva el orden de la lista y solo deja las coincidencias")
    void filterKeepsTheListOrder() {
        Order first = order(1L, "ORD-20260929-001", "Ana López", "5511111111");
        Order second = order(2L, "ORD-20260929-002", "Beto Ruiz", "5522222222");
        Order third = order(3L, "ORD-20260929-003", "Ana Gómez", "5533333333");

        List<Order> result = OrderSearchSupport.filter(List.of(first, second, third), "ana");

        assertThat(result).containsExactly(first, third);
    }

    @Test
    @DisplayName("El folio que trae la fecha fija ese día; un teléfono nunca")
    void dateHintOnlyReadsTheFolioDate() {
        assertThat(OrderSearchSupport.dateHint("ORD-20260929-007")).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(OrderSearchSupport.dateHint("20260929-007")).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(OrderSearchSupport.dateHint("20260929")).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(OrderSearchSupport.dateHint("ord-20260929")).isEqualTo(LocalDate.of(2026, 9, 29));

        // Un teléfono de 10 dígitos NO es una fecha: si lo fuera, buscar por teléfono cambiaría
        // el filtro de fecha sin que el usuario lo pidiera.
        assertThat(OrderSearchSupport.dateHint("5512345678")).isNull();
        assertThat(OrderSearchSupport.dateHint("007")).isNull();
        assertThat(OrderSearchSupport.dateHint("Ana")).isNull();
        // 32 de septiembre no existe: se ignora en vez de romper la lista.
        assertThat(OrderSearchSupport.dateHint("20260932")).isNull();
    }

    private Order order(Long id, String orderNumber) {
        return order(id, orderNumber, "Cliente de prueba", "5599999999");
    }

    private Order order(Long id, String orderNumber, String customerName, String phone) {
        return Order.builder()
                .idOrder(id)
                .orderNumber(orderNumber)
                .orderType(OrderType.DINE_IN)
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethodType.CASH)
                .createdAt(LocalDateTime.of(2026, 9, 29, 14, 0))
                .total(new BigDecimal("150.00"))
                .customerName(customerName)
                .customerPhone(phone)
                .orderDetails(new ArrayList<>())
                .build();
    }
}
