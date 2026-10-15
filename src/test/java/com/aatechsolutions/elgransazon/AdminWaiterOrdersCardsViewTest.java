package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.infrastructure.thymeleaf.TimezoneDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los pedidos de admin/gerente y de mesero se pueden ver como tabla o como tarjetas, con la misma
 * pieza compartida ({@code js/orders-view.js}) que usa el interruptor de la vista de cajero.
 *
 * <p>Cada plantilla tiene una sola tabla y no puede haber dos que se desincronicen: las tarjetas
 * se construyen desde las filas. Eso crea un contrato silencioso entre el servidor y el JavaScript
 * —la fila publica sus datos en atributos {@code data-order-*} y la tarjeta los lee por nombre—,
 * y es justo lo que aquí se fija: el último test recorre el JavaScript, saca los atributos que
 * lee y comprueba que la plantilla los publica. Renombrar uno de los dos lados falla, en vez de
 * dejar una tarjeta en blanco sin que nada avise.</p>
 *
 * <p>El listado completo no se puede renderizar sin Spring (la barra lateral usa
 * {@code sec:authorize} y necesita contexto de seguridad), así que los enganches se comprueban
 * sobre la plantilla real, igual que {@code CashierOrdersCardsViewTest}.</p>
 */
class AdminWaiterOrdersCardsViewTest {

    private static final Path ADMIN_LIST =
            Path.of("src/main/resources/templates/admin/orders/list.html");

    private static final Path WAITER_LIST =
            Path.of("src/main/resources/templates/waiter/orders/list.html");

    private static final Path SHARED_JS =
            Path.of("src/main/resources/static/js/orders-view.js");

    private static final Path MODAL_FRAGMENT =
            Path.of("src/main/resources/templates/fragments/order-detail-modal.html");

    /**
     * Datos que la tarjeta necesita y que las DOS páginas publican: sin ellos la tarjeta sale
     * incompleta en una de las dos vistas.
     */
    private static final List<String> ATRIBUTOS_COMUNES = List.of(
            "id", "number", "when", "table", "type", "type-label", "customer",
            "payment", "total", "href");

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

        Company company = new Company();
        company.setIdCompany(1L);
        company.setName("Restaurante de prueba");
        company.setTimezone("America/Mexico_City");
        CompanyContext.setCurrentCompany(company);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("el interruptor se renderiza de verdad con su ámbito")
    void elInterruptorOfreceLasDosVistasYSeIdentificaPorSuAmbito() {
        String html = renderToggle("main");

        assertTrue(html.contains("orders-view-btn"), "los botones que engancha el script");
        assertTrue(html.contains("data-orders-view=\"table\""), "botón de tabla");
        assertTrue(html.contains("data-orders-view=\"grid\""), "botón de tarjetas");
        assertTrue(html.contains("data-orders-view-scope=\"main\""), "ámbito de la lista");
        assertTrue(html.contains("aria-pressed=\"true\""), "la vista de tabla viene activa");
        assertTrue(html.contains("aria-pressed=\"false\""), "y la de tarjetas no");
        assertTrue(html.contains("table_rows") && html.contains("grid_view"), "los dos iconos");
    }

    @Test
    @DisplayName("las dos listas tienen su tabla, sus tarjetas y su interruptor")
    void lasDosListasTienenTablaTarjetasEInterruptor() throws IOException {
        for (Path lista : List.of(ADMIN_LIST, WAITER_LIST)) {
            String page = read(lista);

            assertTrue(page.contains("orders-view-toggle :: toggle('main')"),
                    "el interruptor en " + lista);
            for (String id : new String[]{
                    "ordersTableView", "ordersGridView", "ordersGridContainer", "ordersGridEmpty"}) {
                assertTrue(page.contains("id=\"" + id + "\""), "falta " + id + " en " + lista);
            }
            assertTrue(page.contains("<script th:src=\"@{/js/orders-view.js}\">"),
                    "la pieza compartida se carga en " + lista);
        }
    }

    @Test
    @DisplayName("cada rol recuerda su elección por separado")
    void laVistaSeRecuerdaConSuPropiaLlave() throws IOException {
        String admin = read(ADMIN_LIST);
        String waiter = read(WAITER_LIST);

        assertTrue(admin.contains("viewKey: \"adminOrdersView\""), "llave del admin/gerente");
        assertTrue(waiter.contains("viewKey: \"waiterOrdersView\""), "llave del mesero");
        assertTrue(admin.contains("OrdersView.init({") && waiter.contains("OrdersView.init({"),
                "ambas arrancan la pieza compartida");
    }

    @Test
    @DisplayName("la página publica todos los datos que la tarjeta lee")
    void laPaginaPublicaLosDatosQueLaTarjetaLee() throws IOException {
        Set<String> leidos = atributosQueLeeLaTarjeta();

        assertTrue(leidos.containsAll(ATRIBUTOS_COMUNES),
                "el JavaScript lee más atributos de los que este test declara: " + leidos);

        for (Path lista : List.of(ADMIN_LIST, WAITER_LIST)) {
            String page = read(lista);
            for (String atributo : ATRIBUTOS_COMUNES) {
                assertTrue(page.contains("th:data-order-" + atributo + "=\""),
                        "falta th:data-order-" + atributo + " en " + lista
                                + ": la tarjeta lo lee y saldría en blanco");
            }
        }
    }

    @Test
    @DisplayName("lo propio de cada rol también viaja a la tarjeta")
    void cadaRolPublicaLoQueSuTablaTiene() throws IOException {
        // El admin/gerente ve quién creó el pedido; el mesero, la propina que dejaron.
        assertTrue(read(ADMIN_LIST).contains("th:data-order-created-by=\""),
                "el admin publica \"Creado por\" para su tarjeta");
        assertTrue(read(WAITER_LIST).contains("th:data-order-tip=\""),
                "el mesero publica la propina para su tarjeta");
    }

    @Test
    @DisplayName("la tarjeta copia el estado, el pago y las acciones de la fila")
    void laTarjetaCopiaEstadoPagoYAcciones() throws IOException {
        String js = read(SHARED_JS);

        List<String> copiados = List.of("status-badge", "payment-pill", "order-actions-cell");
        for (String clase : copiados) {
            assertTrue(js.contains("\"." + clase + "\""), "el JS copia " + clase);
        }

        for (Path lista : List.of(ADMIN_LIST, WAITER_LIST)) {
            String page = read(lista);
            for (String clase : copiados) {
                assertTrue(page.contains(clase),
                        "la fila de " + lista + " no lleva " + clase
                                + ": la tarjeta no tendría de dónde copiarlo");
            }
        }

        // Los colores de cada estado siguen siendo los del servidor: la tarjeta copia el badge
        // en vez de repetir aquí la tabla de colores, que se desincronizaría.
        assertTrue(js.contains("badgeSource.cloneNode(true)"), "el badge se copia, no se rehace");
        assertTrue(read(ADMIN_LIST).contains("status-badge"),
                "el badge real de la fila (con sus colores por estado)");
    }

    @Test
    @DisplayName("los botones copiados se vuelven a enganchar: si no, salen muertos")
    void losBotonesClonadosSeVuelvenAEnganchar() throws IOException {
        String js = read(SHARED_JS);
        assertTrue(js.contains("settings.bindActions(card)"),
                "la pieza compartida avisa a la página para que enganche los botones nuevos");

        for (Path lista : List.of(ADMIN_LIST, WAITER_LIST)) {
            String page = read(lista);

            assertTrue(page.contains("function bindOrderActionButtons(root)"),
                    "el enganche reutilizable en " + lista);
            assertTrue(page.contains("scope.querySelectorAll(") || page.contains("scope\n"),
                    "el enganche trabaja sobre el ámbito que recibe, no siempre sobre document");
            assertTrue(page.contains("bindActions: bindOrderActionButtons"),
                    "la página se lo pasa a la vista de tarjetas en " + lista);
            assertTrue(page.contains("bindOrderActionButtons(document);"),
                    "y sigue enganchando la tabla al cargar en " + lista);
        }
    }

    @Test
    @DisplayName("la tarjeta abre el modal de detalle, con los botones suyos y no los del cajero")
    void laTarjetaAbreElModalDeDetalleDelRol() throws IOException {
        String js = read(SHARED_JS);
        String modal = read(MODAL_FRAGMENT);

        // El cuerpo lo sirve el servidor con el rol en la ruta: al del mesero solo le responde
        // con sus pedidos.
        assertTrue(js.contains(
                "\"/\" + settings.detailPanel.role + \"/orders/\" + orderId + \"/detail-panel\""),
                "la pieza compartida pide el panel de SU rol");
        assertTrue(js.contains("openDetailPanel(orderId)"), "el clic de la tarjeta abre el modal");

        // Los botones del modal son los de la fila pulsada. El juego del cajero NO vale aquí: el
        // mesero no puede abrir /cashier/payments y el admin vería botones distintos a los de su
        // fila.
        assertTrue(js.contains("rowActionsBox(orderId)"), "el modal recibe los botones de la fila");
        assertTrue(js.contains("cloneActions(row)"), "copiados de la fila real de esa pantalla");
        assertFalse(js.contains("/cashier/payments"), "sin los botones de cobro del cajero");

        assertTrue(read(ADMIN_LIST).contains("detailPanel: { role: \"admin\" }"),
                "la lista de admin/gerente declara su rol");
        assertTrue(read(WAITER_LIST).contains("detailPanel: { role: \"waiter\" }"),
                "la del mesero, el suyo");

        for (Path lista : List.of(ADMIN_LIST, WAITER_LIST)) {
            String page = read(lista);
            assertTrue(page.contains("~{fragments/order-detail-modal :: modal}"),
                    "el modal compartido (el mismo del cajero) en " + lista);
        }

        assertTrue(modal.contains("id=\"orderDetailModal\""), "el modal");
        assertTrue(modal.contains("id=\"orderDetailModalBody\""), "el cuerpo que se rellena");
        assertTrue(modal.contains("id=\"orderDetailFullLink\""),
                "el enlace al pedido completo, para no perder la vista de siempre");

        // Cerrar: el fondo, la X y Escape. Y los botones de la tarjeta no abren el modal, para
        // que cancelar un pedido no abra además el detalle.
        assertTrue(modal.contains("data-order-detail-close"), "el fondo y la X");
        assertTrue(js.contains("\"[data-order-detail-close]\""), "y su enganche");
        assertTrue(js.contains("event.key === \"Escape\""), "además de Escape");
        assertTrue(js.contains("event.target.closest(\"a, button\")"),
                "los botones y enlaces de la tarjeta no abren el modal");
    }

    @Test
    @DisplayName("el aviso de la cocina por WebSocket también rehace las tarjetas y el modal")
    void elRefrescoEnVivoActualizaTarjetasYModal() throws IOException {
        for (Path lista : List.of(ADMIN_LIST, WAITER_LIST)) {
            String page = read(lista);

            String handler = liveUpdateHandler(page);

            // Cuando la cocina, la barra o la parrilla marcan un pedido como listo, el servidor
            // recalcula el estado y avisa por WebSocket. La tabla ya se parcheaba en sitio, pero
            // en vista de tarjetas esa tabla está oculta: sin rehacer las tarjetas, lo que el
            // empleado tiene delante seguiría mostrando el estado viejo.
            assertTrue(handler.contains("OrdersView.render()"),
                    "las tarjetas se rehacen con el estado nuevo en " + lista);
            assertTrue(handler.contains("OrdersView.refreshDetailPanel(orderId)"),
                    "y el modal abierto se vuelve a pedir al servidor en " + lista);

            // La fila se sigue parcheando: el aviso no puede depender de que haya tarjetas.
            assertTrue(handler.contains("status-badge"),
                    "el badge de la fila se actualiza en sitio en " + lista);
        }
    }

    /**
     * El cuerpo de {@code handleOrderListUpdate}, que es quien recibe el aviso del servidor.
     * Se corta en la siguiente función del mismo nivel para no leer código de más.
     */
    private String liveUpdateHandler(String page) {
        int start = page.indexOf("function handleOrderListUpdate(notification)");
        assertTrue(start > 0, "el manejador del aviso en vivo");

        int end = page.indexOf("\n      function ", start);
        return end > start ? page.substring(start, end) : page.substring(start);
    }

    /** Los atributos data-order-* que el JavaScript de las tarjetas lee de cada fila. */
    private Set<String> atributosQueLeeLaTarjeta() throws IOException {
        Matcher matcher = Pattern.compile("attr\\(row, \"([a-z-]+)\"\\)")
                .matcher(read(SHARED_JS));

        Set<String> nombres = new TreeSet<>();
        while (matcher.find()) {
            nombres.add(matcher.group(1));
        }
        return nombres;
    }

    /**
     * Renderiza el fragmento real. El parámetro del fragmento se resuelve desde el contexto
     * (no se escribe en el nombre de la plantilla, que Thymeleaf tomaría por un archivo).
     */
    private String renderToggle(String scope) {
        Context context = new Context();
        context.setVariable("scope", scope);
        return engine.process("fragments/orders-view-toggle", context);
    }

    private String read(Path path) throws IOException {
        assertTrue(Files.exists(path), "existe " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
