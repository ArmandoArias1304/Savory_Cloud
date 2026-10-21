package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenuComboItem;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.util.PreparationTimeFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * El tiempo de preparación es opcional por platillo y la comanda lo estima sumando
 * los tiempos de sus platillos. Las reglas que fija esta prueba son las que el
 * restaurante explicó:
 *
 * <ul>
 *   <li>La cantidad no multiplica: dos hamburguesas del mismo platillo se preparan
 *       en paralelo y el mismo platillo cuenta una sola vez.</li>
 *   <li>Los platillos sin tiempo capturado no inventan minutos: si ninguno tiene
 *       tiempo, la comanda no muestra estimado ({@code null}).</li>
 *   <li>Los platillos cancelados dejan de contar.</li>
 *   <li>Un combo cuenta una sola vez: si la orden trae las líneas de sus hijos, se
 *       suman esas y se ignora la línea del combo padre.</li>
 * </ul>
 */
class PreparationTimeEstimateTest {

    private static ItemMenu item(Long id, Integer minutes) {
        ItemMenu item = ItemMenu.builder()
                .name("Platillo " + id)
                .preparationTimeMinutes(minutes)
                .build();
        item.setIdItemMenu(id);
        return item;
    }

    private static OrderDetail detail(ItemMenu item, int quantity, OrderStatus status) {
        OrderDetail detail = OrderDetail.builder()
                .itemMenu(item)
                .quantity(quantity)
                .itemStatus(status)
                .build();
        return detail;
    }

    private static Order order(OrderDetail... details) {
        Order order = Order.builder().orderNumber("ORD-1").build();
        order.setOrderDetails(new ArrayList<>(List.of(details)));
        return order;
    }

    @Test
    @DisplayName("Suma el tiempo de cada platillo, sin multiplicar por la cantidad")
    void sumsEachDishOnceRegardlessOfQuantity() {
        ItemMenu burger = item(1L, 15);
        Order order = order(
                detail(burger, 1, OrderStatus.PENDING),
                detail(burger, 3, OrderStatus.PENDING));

        assertEquals(15, order.getEstimatedPreparationMinutes());
    }

    @Test
    @DisplayName("Suma platillos distintos de la misma comanda")
    void addsDifferentDishes() {
        Order order = order(
                detail(item(1L, 10), 1, OrderStatus.PENDING),
                detail(item(2L, 5), 2, OrderStatus.PENDING));

        assertEquals(15, order.getEstimatedPreparationMinutes());
        assertEquals("≈ 15 min", order.getEstimatedPreparationLabel());
    }

    @Test
    @DisplayName("Sin tiempos capturados la comanda no muestra estimado")
    void returnsNullWhenNoDishHasTime() {
        Order order = order(
                detail(item(1L, null), 1, OrderStatus.PENDING),
                detail(item(2L, null), 1, OrderStatus.PENDING));

        assertNull(order.getEstimatedPreparationMinutes());
        assertNull(order.getEstimatedPreparationLabel());
        assertNull(PreparationTimeFormatter.formatApprox(null));
    }

    @Test
    @DisplayName("Los platillos cancelados no cuentan para el estimado")
    void ignoresCancelledLines() {
        Order order = order(
                detail(item(1L, 20), 1, OrderStatus.CANCELLED),
                detail(item(2L, 8), 1, OrderStatus.PENDING));

        assertEquals(8, order.getEstimatedPreparationMinutes());
    }

    @Test
    @DisplayName("Un combo con líneas de hijos se cuenta por sus hijos, no dos veces")
    void countsComboThroughItsChildLinesOnlyOnce() {
        ItemMenu combo = ItemMenu.builder()
                .name("Combo")
                .isCombo(true)
                .preparationTimeMinutes(90)
                .build();
        combo.setIdItemMenu(10L);

        OrderDetail parent = detail(combo, 1, OrderStatus.PENDING);
        parent.setComboGroupId("COMBO-10-1");
        parent.setIsComboParentSnapshot(true);

        OrderDetail childA = detail(item(11L, 12), 1, OrderStatus.PENDING);
        childA.setComboGroupId("COMBO-10-1");
        childA.setIsComboParentSnapshot(false);

        OrderDetail childB = detail(item(12L, 6), 1, OrderStatus.PENDING);
        childB.setComboGroupId("COMBO-10-1");
        childB.setIsComboParentSnapshot(false);

        Order order = order(parent, childA, childB);

        assertEquals(18, order.getEstimatedPreparationMinutes());
    }

    @Test
    @DisplayName("Un combo sin líneas de hijos usa la suma de sus hijos configurados")
    void comboWithoutChildLinesUsesItsChildrenTimes() {
        ItemMenu drink = item(21L, 4);
        ItemMenu dessert = item(22L, 6);

        ItemMenu combo = ItemMenu.builder()
                .name("Combo desayuno")
                .isCombo(true)
                .build();
        combo.setIdItemMenu(20L);
        combo.setComboItems(new ArrayList<>(List.of(
                comboChild(combo, drink, 1),
                comboChild(combo, dessert, 2))));

        Order order = order(detail(combo, 1, OrderStatus.PENDING));

        assertEquals(16, order.getEstimatedPreparationMinutes());
    }

    @Test
    @DisplayName("El texto del tiempo es legible en minutos y horas")
    void formatsMinutesAndHours() {
        assertEquals("45 min", PreparationTimeFormatter.format(45));
        assertEquals("1 h", PreparationTimeFormatter.format(60));
        assertEquals("1 h 20 min", PreparationTimeFormatter.format(80));
        assertEquals("≈ 1 h 20 min", PreparationTimeFormatter.formatApprox(80));
        assertNull(PreparationTimeFormatter.format(0));
        assertNull(PreparationTimeFormatter.format(-3));
    }

    private static ItemMenuComboItem comboChild(ItemMenu combo, ItemMenu child, int quantity) {
        ItemMenuComboItem comboItem = ItemMenuComboItem.builder()
                .comboMenu(combo)
                .childMenu(child)
                .quantity(quantity)
                .build();
        return comboItem;
    }
}
