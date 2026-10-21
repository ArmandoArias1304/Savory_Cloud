package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.infrastructure.thymeleaf.TimezoneDialect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El reloj flotante del cliente y los chips de tiempo viven en un fragmento
 * compartido. Aquí se renderiza con el motor real y pedidos de verdad para fijar
 * lo que el navegador recibe: el número de pedido, el instante de creación (para
 * calcular cuánto lleva esperando), el estimado y la leyenda de que es aproximado.
 */
class PreparationTimeClockRenderTest {

    private static final Pattern CREATED = Pattern.compile("data-created=\"(\\d+)\"");

    private TemplateEngine engine;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setDialect(new SpringStandardDialect());
        engine.addDialect(new TimezoneDialect());
    }

    @Test
    @DisplayName("El reloj muestra la orden, el instante de creación, el estimado y la advertencia")
    void rendersTheFloatingClockWithElapsedData() {
        Order order = Order.builder()
                .orderNumber("ORD-77")
                .status(OrderStatus.IN_PREPARATION)
                .createdAt(LocalDateTime.now().minusMinutes(12))
                .build();
        ItemMenu soup = ItemMenu.builder().name("Sopa").preparationTimeMinutes(10).build();
        soup.setIdItemMenu(1L);
        ItemMenu steak = ItemMenu.builder().name("Corte").preparationTimeMinutes(15).build();
        steak.setIdItemMenu(2L);
        order.setOrderDetails(new ArrayList<>(List.of(
                OrderDetail.builder().itemMenu(soup).quantity(1).itemStatus(OrderStatus.IN_PREPARATION).build(),
                OrderDetail.builder().itemMenu(steak).quantity(2).itemStatus(OrderStatus.PENDING).build())));

        Context context = new Context();
        context.setVariable("trackedOrders", List.of(order));

        String html = engine.process("fragments/prep-time", java.util.Set.of("clientClock"), context);
        String text = html.replaceAll("\\s+", " ");

        assertTrue(html.contains("prepClockButton"), "dibuja el botón flotante del reloj");
        assertTrue(html.contains("data-order-number=\"ORD-77\""), "identifica el pedido");
        assertTrue(html.contains("≈ 25 min"), "muestra la suma de los platillos");
        assertTrue(text.contains("Es un aproximado"), "advierte que el tiempo puede variar");
        assertTrue(text.contains("puede durar más o durar menos"), "explica la variación");

        Matcher created = CREATED.matcher(html);
        assertTrue(created.find(), "incluye el instante de creación para calcular el tiempo transcurrido");
        assertTrue(Long.parseLong(created.group(1)) > 0L, "el instante es un epoch válido");
    }

    @Test
    @DisplayName("Sin pedidos activos no se dibuja el reloj")
    void hidesTheClockWithoutTrackedOrders() {
        Context context = new Context();
        context.setVariable("trackedOrders", List.of());

        String html = engine.process("fragments/prep-time", java.util.Set.of("clientClock"), context);

        assertFalse(html.contains("prepClockButton"));
    }

    @Test
    @DisplayName("El chip dibuja el tiempo aproximado y se omite cuando no hay dato")
    void rendersTheChipOnlyWhenThereIsALabel() {
        Context withLabel = new Context();
        withLabel.setVariable("label", "≈ 20 min");
        String chip = engine.process("fragments/prep-time", java.util.Set.of("chip"), withLabel);
        assertTrue(chip.contains("≈ 20 min"));

        Context withoutLabel = new Context();
        withoutLabel.setVariable("label", null);
        String empty = engine.process("fragments/prep-time", java.util.Set.of("chip"), withoutLabel);
        assertFalse(empty.contains("≈"), "sin etiqueta no inventa un tiempo");
    }
}
