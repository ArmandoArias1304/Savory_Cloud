package com.aatechsolutions.elgransazon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de las pantallas del flujo "Unir mesas": la selección múltiple, el paso
 * de `tableIds` por cliente/menú y la etiqueta del grupo. Los controladores y el
 * servicio se prueban por separado; aquí se fija el cableado de las vistas.
 */
class JoinedTablesUiTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final List<String> ROLES = List.of("waiter", "cashier", "admin");

    private String read(String path) throws IOException {
        return Files.readString(TEMPLATES.resolve(path));
    }

    @Test
    @DisplayName("Las tres pantallas de selección cargan la selección múltiple")
    void allTableSelectionViewsLoadTheUnionScript() throws IOException {
        for (String role : ROLES) {
            String html = read(role + "/orders/order-table-selection.html");
            assertThat(html)
                    .as(role + " debe cargar union-tables.js")
                    .contains("/js/union-tables.js")
                    .as(role + " debe exponer si la mesa tiene reservación vigente")
                    .contains("data-reserved-now");
        }
        String script = Files.readString(
                Path.of("src/main/resources/static/js/union-tables.js"));
        assertThat(script).contains("tableIds=");
        assertThat(script).contains("unionTablesBar");
        assertThat(script)
                .as("las tarjetas deben seleccionarse al hacer clic, sin checkbox")
                .contains("card.addEventListener")
                .contains("union-table-selected")
                .doesNotContain("union-table-toggle");
        assertThat(script)
                .as("la vista de tabla debe usar checkbox en sus filas")
                .contains("tr[data-table-id][data-can-use=\"true\"]")
                .contains("union-table-checkbox");
        assertThat(script).contains("startButton.textContent = \"Unir mesas\"");
        assertThat(script)
                .as("unir mesas no debe consumir una reservación vigente en silencio")
                .contains("data-reserved-now");
    }

    @Test
    @DisplayName("El formulario de cliente conserva las mesas elegidas")
    void customerInfoFormsCarryTheSelectedTables() throws IOException {
        for (String role : ROLES) {
            String html = read(role + "/orders/order-customer-info.html");
            assertThat(html)
                    .as(role + " debe enviar tableIds al menú")
                    .contains("name=\"tableIds\"")
                    .contains("selectedTableIds");
        }
    }

    @Test
    @DisplayName("El menú envía la lista de mesas al crear el pedido")
    void menuViewsSendTheTableListToTheServer() throws IOException {
        for (String role : ROLES) {
            String html = read(role + "/orders/order-menu.html");
            assertThat(html)
                    .as(role + " debe mandar joinedTableIds en el JSON")
                    .contains("tableIds: /*[[${joinedTableIds}]]*/");
        }
    }

    @Test
    @DisplayName("Las vistas de pedido muestran la etiqueta del grupo")
    void orderViewsShowTheGroupLabel() throws IOException {
        for (String role : ROLES) {
            String html = read(role + "/orders/view.html");
            assertThat(html)
                    .as(role + " debe usar la etiqueta que arma el pedido")
                    .contains("order.tableDisplayName");
        }
    }

    @Test
    @DisplayName("Listados, menú y cobros conservan la etiqueta 4+7")
    void listsMenusAndPaymentsKeepTheGroupLabel() throws IOException {
        for (String role : List.of("waiter", "admin")) {
            assertThat(read(role + "/orders/list.html"))
                    .as(role + " debe pasar la etiqueta al refresco AJAX de tarjetas")
                    .contains("th:data-order-table=\"${order.table != null ? order.tableNumberLabel : ''}\"");
        }

        for (String role : ROLES) {
            assertThat(read(role + "/orders/order-menu.html"))
                    .as(role + " debe mostrar el grupo en el badge de mesa")
                    .contains("th:text=\"${tableLabel != null and !#strings.isEmpty(tableLabel) ? tableLabel : selectedTable.tableNumber}\"");
        }

        for (String role : List.of("waiter", "cashier", "admin", "delivery")) {
            String html = read(role + "/payments/form.html");
            assertThat(html)
                    .as(role + " no debe anteponer 'Mesa #' a una etiqueta ya compuesta")
                    .doesNotContain(">Mesa #<span")
                    .contains("th:text=\"${order.tableNumberLabel}\"");
        }
    }
}
