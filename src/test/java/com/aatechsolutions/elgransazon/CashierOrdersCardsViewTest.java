package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.infrastructure.thymeleaf.TimezoneDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La lista de pedidos del cajero se puede ver como tabla o como tarjetas, y la tarjeta abre un
 * modal con el detalle del pedido.
 *
 * <p>Aquí se cubren las dos mitades de ese contrato: el interruptor se RENDERIZA de verdad (el
 * fragmento real, con el dialecto de la aplicación) y la página cumple el contrato de la vista
 * de tarjetas. El listado completo no se puede renderizar sin Spring (incluye la barra lateral,
 * que usa {@code sec:authorize} y necesita el contexto de seguridad), así que sus enganches se
 * comprueban sobre la plantilla.</p>
 */
class CashierOrdersCardsViewTest {

    private static final Path LIST_TEMPLATE =
            Path.of("src/main/resources/templates/cashier/orders/list.html");

    private static final Path TOGGLE_TEMPLATE =
            Path.of("src/main/resources/templates/fragments/orders-view-toggle.html");

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
    void elInterruptorOfreceLasDosVistasYSeIdentificaPorSuAmbito() {
        String html = renderToggle("my");

        assertTrue(html.contains("orders-view-btn"), "los botones que engancha el script");
        assertTrue(html.contains("data-orders-view=\"table\""), "botón de tabla");
        assertTrue(html.contains("data-orders-view=\"grid\""), "botón de tarjetas");
        assertTrue(html.contains("data-orders-view-scope=\"my\""), "ámbito de la lista");
        assertTrue(html.contains("aria-pressed=\"true\""), "la vista de tabla viene activa");
        assertTrue(html.contains("aria-pressed=\"false\""), "y la de tarjetas no");
        assertTrue(html.contains("table_rows") && html.contains("grid_view"), "los dos iconos");
    }

    @Test
    void lasDosListasTienenSuInterruptorSuTablaYSusTarjetas() {
        String page = readTemplate(LIST_TEMPLATE);

        assertTrue(page.contains("orders-view-toggle :: toggle('my')"),
                "el interruptor de \"Mis Pedidos\"");
        assertTrue(page.contains("orders-view-toggle :: toggle('global')"),
                "el interruptor de la lista global");

        for (String id : new String[]{
                "myOrdersTableView", "myOrdersGridView", "myOrdersGridContainer", "myOrdersGridEmpty",
                "globalOrdersTableView", "globalOrdersGridView", "globalOrdersGridContainer",
                "globalOrdersGridEmpty"}) {
            assertTrue(page.contains("id=\"" + id + "\""), "falta " + id);
        }
    }

    @Test
    void laVistaDeTarjetasSeRecuerdaYSeAplicaAlCargar() {
        String page = readTemplate(LIST_TEMPLATE);

        assertTrue(page.contains("const ORDERS_VIEW_KEY = \"cashierOrdersView\""),
                "la preferencia tiene su propia llave");
        assertTrue(page.contains("localStorage.setItem("), "se guarda la elección");
        assertTrue(page.contains("localStorage.getItem(ORDERS_VIEW_KEY)"), "y se recupera");
        assertTrue(page.contains("function switchOrdersView(view)"), "cambio de vista");
        assertTrue(page.contains("function applyOrdersView()"), "aplicación de la vista");
        assertTrue(page.contains("applyOrdersView();"), "se aplica al terminar de cargar");
    }

    @Test
    void lasTarjetasSeConstruyenDesdeLasFilasDeLaTabla() {
        String page = readTemplate(LIST_TEMPLATE);

        // Una sola fuente de verdad: la tarjeta copia las celdas de su fila y los MISMOS botones
        // del fragmento de acciones, en vez de repetir la información y las reglas en el HTML.
        assertTrue(page.contains("function renderOrderCards()"), "construcción de las tarjetas");
        assertTrue(page.contains("table.querySelectorAll(\"tr[data-order-id]\")"),
                "cada tarjeta sale de su fila");
        assertTrue(page.contains("row.querySelectorAll(\"td\")"), "los datos salen de las celdas");
        assertTrue(page.contains(".order-actions-cell"), "y las acciones, del fragmento de la fila");
        assertTrue(page.contains("bindOrderActionButtons(card)"),
                "los botones copiados se vuelven a enganchar");
    }

    @Test
    void laCabeceraDeLaTarjetaDestacaLaMesaYElNumeroDePedido() {
        String page = readTemplate(LIST_TEMPLATE);

        // La mesa va en su propia caja, como en el panel de detalle, y el número de la mesa se
        // saca de la celda de la tabla (que lo pinta como "Mesa #3").
        assertTrue(page.contains("const tableNumber = cellText(2).replace(/[^0-9]/g, \"\")"),
                "la mesa se lee de la celda de la tabla");
        assertTrue(page.contains("h-14 w-14"), "en una caja de su tamaño");
        assertTrue(page.contains("text-xl font-extrabold leading-none tabular-nums"),
                "con el número de mesa en grande");
        // Los pedidos sin mesa (domicilio, para recoger) no muestran un "N/A": la caja pinta el
        // TIPO con su icono y su color, leídos de la fila por su atributo.
        assertTrue(page.contains("th:data-order-type=\"${order.orderType.name()}\""),
                "la fila lleva el tipo de pedido para que la tarjeta lo pueda pintar");
        assertTrue(page.contains("ORDER_TYPE_BOX[row.getAttribute(\"data-order-type\")]"),
                "la caja del encabezado se decide por el tipo de pedido");
        assertTrue(page.contains("icon: \"local_shipping\""), "domicilio: el icono del reparto");
        assertTrue(page.contains("icon: \"shopping_bag\""), "para recoger: el de la bolsa");
        assertTrue(page.contains("bg-orange-100 text-orange-600"), "domicilio en naranja");
        assertTrue(page.contains("bg-purple-100 text-purple-600"), "para recoger en morado");
        assertTrue(page.contains("caption: \"Domicilio\"") && page.contains("caption: \"Recoger\""),
                "cada caja se rotula con su tipo");
        assertTrue(page.contains("font-mono text-lg sm:text-xl font-extrabold"),
                "el número de pedido es el título de la tarjeta y va en grande");

        // El número ocupa el ancho que antes compartía con el badge de estado: en dos columnas
        // (tablet) los tres elementos no caben en una fila sin partir el número.
        assertTrue(page.contains("when.appendChild(badge)"),
                "el estado baja a la línea de la fecha, debajo del número");
        assertTrue(page.contains("badge.classList.remove(\"px-3\", \"py-1\", \"text-xs\")"),
                "el badge copiado se ajusta al tamaño de esa línea");
    }

    @Test
    void laTarjetaAbreElModalYElModalTraeElDetalleDelServidor() {
        String page = readTemplate(LIST_TEMPLATE);

        assertTrue(page.contains("id=\"orderDetailModal\""), "el modal");
        assertTrue(page.contains("id=\"orderDetailModalBody\""), "el cuerpo que se rellena");
        assertTrue(page.contains("id=\"orderDetailFullLink\""), "el enlace al pedido completo");
        assertTrue(page.contains("function openOrderDetailPanel(orderId)"), "abrir el detalle");
        assertTrue(page.contains("function closeOrderDetailPanel()"), "cerrarlo");
        assertTrue(page.contains("/cashier/orders/${orderId}/detail-panel"),
                "el endpoint que sirve el panel (fragments/order-detail-panel)");
        assertTrue(page.contains("event.target.closest(\"a, button\")"),
                "los botones de la tarjeta no abren el modal");
        assertTrue(page.contains("data-order-detail-close"), "cerrar tocando el fondo o la X");
        assertTrue(page.contains("event.key === \"Escape\""), "y con Escape");
    }

    @Test
    void elRefrescoEnVivoActualizaTambienTarjetasYModal() {
        String page = readTemplate(LIST_TEMPLATE);

        // refreshOrderActions re-renderiza la columna de acciones de la fila cuando llega un
        // STATUS_CHANGE (cocina, reparto u otro cajero): las tarjetas y el panel abierto tienen
        // que quedar con los botones nuevos, no con los de antes.
        assertTrue(page.contains("function refreshOrderActions(orderId)"), "refresco de la fila");
        assertTrue(page.contains("function refreshOpenOrderDetailPanel(orderId)"),
                "refresco del panel abierto");
        assertTrue(page.contains("function updateRowStatusBadge(orderId, status)"),
                "el semáforo de estado de la fila");

        String refresco = page.substring(page.indexOf("function refreshOrderActions(orderId)"),
                page.indexOf("function formatCurrency"));
        assertTrue(refresco.contains("renderOrderCards();"), "la fila re-renderizada rehace las tarjetas");
        assertTrue(refresco.contains("refreshOpenOrderDetailPanel(orderId);"),
                "y refresca el modal si es el pedido abierto");

        String semaforo = page.substring(page.indexOf("function updateRowStatusBadge(orderId, status)"),
                page.indexOf("document.addEventListener(\"DOMContentLoaded\", function () {\n        bindOrderActionButtons(document);"));
        assertTrue(semaforo.contains("renderOrderCards();"),
                "un cambio de estado también rehace las tarjetas");
    }

    @Test
    void lasAccionesDeLaTarjetaSonMasGrandesQueEnLaTabla() {
        String page = readTemplate(LIST_TEMPLATE);

        // La tarjeta es táctil: sus botones (los mismos del fragmento de acciones) se agrandan
        // y se separan un poco, sin llegar al tamaño del panel de detalle.
        assertTrue(page.contains("order-card-actions border-t"),
                "el pie de la tarjeta lleva su clase al construirla");
        assertTrue(page.contains(".order-card-actions > div > a"),
                "se ajusta el tamaño de los botones copiados de la fila");
        assertTrue(page.contains("padding: 0.625rem"), "más área de toque que el p-2 de la tabla");
        assertTrue(page.contains("font-size: 1.0625rem"), "y el icono un punto más grande");
        assertTrue(page.contains(".order-card-actions > div") && page.contains("gap: 0.5rem"),
                "con más separación entre botones");
        // Y vuelven a crecer en pantallas anchas: en una tablet el cajero toca con el dedo, no
        // con el ratón, así que el mínimo cómodo (unos 44 px) se mantiene en cualquier ancho.
        assertTrue(page.contains("padding: 0.75rem"),
                "en pantallas anchas el botón vuelve a crecer");
        assertTrue(page.contains("gap: 0.75rem"), "y se separa otro poco más");
    }

    @Test
    void elInterruptorNoDuplicaLaInformacionEnElHtml() {
        String page = readTemplate(LIST_TEMPLATE);

        // Si las tarjetas se escribieran también en el HTML habría dos juegos de la misma
        // información (y dos sitios donde mantener las reglas de los botones).
        assertFalse(page.contains("fa-receipt"), "los botones no se repiten en el HTML de la página");
        assertFalse(page.contains("fa-dollar-sign"), "tampoco los de cobro");
        assertTrue(readTemplate(TOGGLE_TEMPLATE).contains("th:fragment=\"toggle(scope)\""),
                "el interruptor es un fragmento reutilizable");
    }

    // ================================================================
    // Utilidades
    // ================================================================

    private String renderToggle(String scope) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("scope", scope);

        JakartaServletWebApplication application = JakartaServletWebApplication
                .buildApplication(new MockServletContext());
        WebContext context = new WebContext(
                application.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse()));
        context.setVariables(variables);
        return engine.process("fragments/orders-view-toggle", context);
    }

    /** La plantilla con los saltos de línea normalizados: el checkout en Windows la trae CRLF
     *  y las comprobaciones de aquí no dependen del fin de línea. */
    private static String readTemplate(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + path.toAbsolutePath(), e);
        }
    }
}
