package com.aatechsolutions.elgransazon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El modal de mesa ocupada es la explicación que recibe cualquier rol con vista de
 * crear pedido cuando pulsa una mesa que ya está en uso. Debe existir una sola vez,
 * ser el mismo en mesero, cajero y admin/gerente, y llevar el enlace al pedido solo
 * cuando el servidor lo permite.
 */
class OccupiedTableModalRenderTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Path JS = Path.of("src/main/resources/static/js/table-occupancy-modal.js");

    private static final List<String> ROLE_VIEWS = List.of(
            "waiter/orders/order-table-selection.html",
            "cashier/orders/order-table-selection.html",
            "admin/orders/order-table-selection.html");

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
    }

    @Test
    @DisplayName("El modal compartido renderiza la información de la ocupación y sus acciones")
    void rendersTheOccupancyInformationAndActions() {
        String html = renderModal();

        assertTrue(html.contains("id=\"occupiedTableModal\""), "el contenedor del modal");
        assertTrue(html.contains("Atendida por"), "quién tiene la mesa");
        assertTrue(html.contains("id=\"occupiedTableWaiterName\""), "el nombre del mesero");
        assertTrue(html.contains("id=\"occupiedTableOrderNumber\""), "el número de pedido");
        assertTrue(html.contains("id=\"occupiedTableOpenedAt\""), "la hora de apertura");
        assertTrue(html.contains("id=\"occupiedTableElapsed\""), "el tiempo ocupada");
        assertTrue(html.contains("id=\"occupiedTableItems\""), "los artículos");
        assertTrue(html.contains("id=\"occupiedTableStatus\""), "el estado del pedido");
        assertTrue(html.contains("id=\"occupiedTableTotal\""), "el total");
        assertTrue(html.contains("id=\"occupiedTableCustomer\""), "el cliente");
        assertFalse(html.contains("occupiedTableViewOrder"), "ya no aparece el botón Ver pedido");
        assertTrue(html.contains("data-occupied-close"), "los cierres por fondo, X y botón");
    }

    @Test
    @DisplayName("Las tres vistas de crear pedido incluyen el mismo modal y el mapa de ocupación")
    void allCreateOrderViewsIncludeTheSharedModal() throws IOException {
        for (String view : ROLE_VIEWS) {
            String html = Files.readString(TEMPLATES.resolve(view));

            assertTrue(html.contains("fragments/occupied-table-modal :: modal"),
                    view + ": incluye el modal compartido");
            assertTrue(html.contains("window.__occupiedTables"),
                    view + ": recibe el mapa de mesas ocupadas del controlador");
            assertTrue(html.contains("table-occupancy-modal.js"),
                    view + ": carga el JavaScript compartido");
            assertTrue(html.contains("TableOccupancyModal.show"),
                    view + ": abre el modal al pulsar una mesa ocupada");
            assertTrue(html.contains("showTableNotAvailable("),
                    view + ": conserva el aviso para los demás estados");
            assertFalse(html.contains("data-occupied-by"),
                    view + ": ya no depende del dato suelto del intento anterior");
        }
    }

    @Test
    @DisplayName("El JavaScript pinta el resumen, oculta el enlace sin permiso y cierra con Escape")
    void theSharedScriptPaintsAndClosesTheModal() throws IOException {
        String js = Files.readString(JS);

        assertTrue(js.contains("window.__occupiedTables"), "lee el mapa que dejó el servidor");
        assertTrue(js.contains("formatElapsed"), "calcula el tiempo transcurrido");
        assertFalse(js.contains("occupiedTableViewOrder"), "ya no gestiona el botón Ver pedido");
        assertFalse(js.contains("info.orderUrl"), "ya no enlaza el pedido desde este modal");
        assertTrue(js.contains("event.key === \"Escape\""), "cierra con Escape");
        assertTrue(js.contains("button[data-occupied-close]"), "enfoca el botón de cierre");
    }

    private String renderModal() {
        JakartaServletWebApplication application = JakartaServletWebApplication
                .buildApplication(new MockServletContext());
        WebContext context = new WebContext(
                application.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse()));
        context.setVariables(new LinkedHashMap<>(Map.of()));
        return engine.process("fragments/occupied-table-modal", Set.of("modal"), context);
    }
}
