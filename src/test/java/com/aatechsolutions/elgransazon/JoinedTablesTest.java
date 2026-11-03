package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El modelo de mesas unidas es ADITIVO: la mesa principal sigue viviendo en
 * {@code Order.table} y las mesas extra solo se guardan en {@code joinedTableRows}.
 * Estas pruebas fijan ese contrato, que es lo que permite que tickets, reportes y
 * consultas existentes sigan funcionando.
 */
class JoinedTablesTest {

    private RestaurantTable table(long id, int number) {
        return RestaurantTable.builder()
                .id(id)
                .tableNumber(number)
                .capacity(6)
                .build();
    }

    @Test
    @DisplayName("Un pedido de una sola mesa conserva la etiqueta de siempre")
    void singleTableKeepsLegacyLabel() {
        Order order = new Order();
        order.setTable(table(4L, 4));

        assertThat(order.hasJoinedTables()).isFalse();
        assertThat(order.getGroupTables()).hasSize(1);
        assertThat(order.getTableNumberLabel()).isEqualTo("4");
        assertThat(order.getTableDisplayName()).isEqualTo("Mesa #4");
    }

    @Test
    @DisplayName("Un pedido con mesas unidas muestra los números ordenados: 4+7")
    void joinedTablesShowAllNumbersSorted() {
        Order order = new Order();
        order.setTable(table(7L, 7));
        order.addJoinedTable(table(4L, 4));

        assertThat(order.hasJoinedTables()).isTrue();
        assertThat(order.getTableNumberLabel()).isEqualTo("4+7");
        assertThat(order.getTableDisplayName()).isEqualTo("Mesa 4+7");
        assertThat(order.getGroupTables())
                .extracting(RestaurantTable::getTableNumber)
                .containsExactly(7, 4); // principal primero; la etiqueta se ordena aparte
    }

    @Test
    @DisplayName("No se duplica la mesa principal ni una mesa ya unida")
    void doesNotDuplicateTables() {
        RestaurantTable primary = table(4L, 4);
        Order order = new Order();
        order.setTable(primary);

        order.addJoinedTable(primary);
        order.addJoinedTable(table(7L, 7));
        order.addJoinedTable(table(7L, 7));

        assertThat(order.getGroupTables()).hasSize(2);
        assertThat(order.getJoinedTables()).hasSize(1);
        assertThat(order.getTableNumberLabel()).isEqualTo("4+7");
    }

    @Test
    @DisplayName("Un pedido sin mesa no revienta las etiquetas")
    void noTableReturnsEmptyLabels() {
        Order order = new Order();

        assertThat(order.getGroupTables()).isEmpty();
        assertThat(order.getTableNumberLabel()).isEmpty();
        assertThat(order.getTableDisplayName()).isEmpty();
    }
}
