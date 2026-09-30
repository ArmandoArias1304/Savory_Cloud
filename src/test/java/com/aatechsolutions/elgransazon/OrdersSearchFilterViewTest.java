package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.infrastructure.thymeleaf.TimezoneDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El buscador tiene que existir y estar cableado en las TRES listas de pedidos (admin/gerente,
 * mesero y cajero) con el mismo contrato: la caja lleva el término al servidor, se limpia sola, y
 * cuando no hay resultados ofrece ver todos los días.
 *
 * <p>Las listas completas no se pueden renderizar sin Spring (incluyen la barra lateral con
 * {@code sec:authorize}), así que sus enganches se comprueban sobre la plantilla, igual que hace
 * {@code CashierOrdersCardsViewTest} con el interruptor tabla/tarjetas.</p>
 */
class OrdersSearchFilterViewTest {

    private static final List<Path> ORDER_LISTS = List.of(
            Path.of("src/main/resources/templates/admin/orders/list.html"),
            Path.of("src/main/resources/templates/waiter/orders/list.html"),
            Path.of("src/main/resources/templates/cashier/orders/list.html"));

    private static final Path CASHIER_LIST = Path.of("src/main/resources/templates/cashier/orders/list.html");

    /** Marcas que delimitan el aviso de resultados dentro de cada lista. */
    private static final String BANNER_START = "<!-- Resultado de la búsqueda";
    private static final String BANNER_END = "<!-- ORDERS TABLE -->";

    @Test
    @DisplayName("Las tres listas tienen la caja de búsqueda con su valor y su Enter")
    void everyOrderListHasTheSearchBox() throws IOException {
        for (Path list : ORDER_LISTS) {
            String template = read(list);

            assertThat(template)
                    .as("caja de búsqueda en %s", list)
                    .contains("id=\"filterSearch\"")
                    .contains("th:value=\"${searchTerm}\"")
                    .contains("onkeydown=\"if (event.key === 'Enter') applyFilters()\"");
        }
    }

    @Test
    @DisplayName("El término viaja al servidor en la petición de filtros")
    void theTermIsSentToTheServer() throws IOException {
        // Admin y mesero arman la URL a mano; el cajero usa URLSearchParams sobre la actual.
        assertThat(read(ORDER_LISTS.get(0))).contains("url += `search=${encodeURIComponent(search)}&`");
        assertThat(read(ORDER_LISTS.get(1))).contains("url += `search=${encodeURIComponent(search)}&`");
        assertThat(read(CASHIER_LIST)).contains("params.set(\"search\", search)");
    }

    @Test
    @DisplayName("Se puede limpiar solo la búsqueda sin perder los demás filtros")
    void theSearchCanBeClearedOnItsOwn() throws IOException {
        for (Path list : ORDER_LISTS) {
            String template = read(list);

            assertThat(template)
                    .as("limpiar búsqueda en %s", list)
                    .contains("function clearSearchFilter()")
                    .contains("params.delete(\"search\")");
        }
    }

    @Test
    @DisplayName("Cuando no hay resultados se ofrece buscar en todos los días")
    void emptyResultsOfferTheWiderDateSearch() throws IOException {
        for (Path list : ORDER_LISTS) {
            String template = read(list);

            assertThat(template)
                    .as("aviso de resultados en %s", list)
                    .contains("th:if=\"${searchTerm != null}\"")
                    .contains("${searchResultCount == 1 ? 'resultado' : 'resultados'}")
                    .contains("th:if=\"${searchResultCount == 0 && selectedDateValue != null}\"")
                    .contains("function clearDateFilter()")
                    .contains("Buscar en todos los días");
        }
    }

    @Test
    @DisplayName("El cajero limpia la búsqueda en sus dos tableros")
    void theCashierClearsSearchOnBothBoards() throws IOException {
        String template = read(CASHIER_LIST);

        assertThat(template)
                .contains("params.delete(\"page\")")
                .contains("params.delete(\"globalPage\")");
    }

    @Test
    @DisplayName("El aviso se renderiza con el término, el conteo y la fecha del filtro")
    void theResultsBannerRenders() throws IOException {
        for (Path list : ORDER_LISTS) {
            String html = render(bannerOf(list), bannerModel("007", 0, LocalDate.of(2026, 9, 29)));

            assertThat(html)
                    .as("aviso de resultados en %s", list)
                    .contains("«007»")
                    .contains("0")
                    .contains("resultados")
                    .contains("29/09/2026")
                    .contains("Buscar en todos los días");
        }
    }

    @Test
    @DisplayName("Con coincidencias el aviso cuenta los resultados y no ofrece ampliar la fecha")
    void theBannerOnlyOffersTheWiderSearchWhenThereAreNoResults() throws IOException {
        String html = render(bannerOf(CASHIER_LIST), bannerModel("Ana", 3, null));

        assertThat(html)
                .contains("«Ana»")
                .contains("3")
                .contains("resultados")
                .doesNotContain("Buscar en todos los días");
    }

    @Test
    @DisplayName("Sin término no se pinta el aviso (ni se rompe la plantilla)")
    void theBannerDisappearsWithoutATerm() throws IOException {
        String html = render(bannerOf(CASHIER_LIST), bannerModel(null, 0, null));

        assertThat(html).doesNotContain("resultados").doesNotContain("manage_search");
    }

    /** El aviso tal como está en la plantilla real: se recorta entre sus dos marcas. */
    private String bannerOf(Path list) throws IOException {
        String template = read(list);
        int start = template.indexOf(BANNER_START);
        int end = template.indexOf(BANNER_END, start);
        assertThat(start).as("aviso presente en %s", list).isNotNegative();
        assertThat(end).as("fin del aviso en %s", list).isGreaterThan(start);
        return template.substring(start, end);
    }

    private Map<String, Object> bannerModel(String term, int results, LocalDate day) {
        // HashMap y no Map.of: el caso "sin búsqueda" necesita valores nulos.
        Map<String, Object> model = new HashMap<>();
        model.put("searchTerm", term);
        model.put("searchResultCount", results);
        model.put("selectedDateValue", day);
        return model;
    }

    /** Renderiza el trozo real de la plantilla con el dialecto de la aplicación. */
    private String render(String markup, Map<String, Object> model) {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setDialect(new SpringStandardDialect());
        engine.addDialect(new TimezoneDialect());

        return engine.process(markup, new Context(java.util.Locale.forLanguageTag("es-MX"), model));
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path)).as("existe %s", path).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
