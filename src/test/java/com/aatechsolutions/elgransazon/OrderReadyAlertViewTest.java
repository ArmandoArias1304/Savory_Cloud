package com.aatechsolutions.elgransazon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cuando la cocina, la barra o la parrilla marcan un pedido como listo, a quien lo va a recoger le
 * salta un aviso que suena y parpadea hasta que le dan "Entendido". Ese aviso lo pone una pieza
 * compartida ({@code js/order-ready-alert.js}) y las tres listas de pedidos tienen que estar
 * enganchadas a ella.
 *
 * <p>Aquí se fija el contrato de las páginas, que es donde se rompería en silencio: sin el
 * script cargado la alerta no existe, sin el usuario de la sesión nadie puede decidir si el aviso
 * es suyo, y sin el enganche en el manejador del WebSocket el aviso llega y se ignora. También se
 * comprueba que el sonido sea el que ya existe en el proyecto: un nombre de archivo inventado
 * dejaría la alerta muda sin que nada avisara.</p>
 *
 * <p>Las listas no se pueden renderizar sin Spring (la barra lateral usa {@code sec:authorize}),
 * así que el contrato se comprueba sobre la plantilla, igual que las demás pruebas de estas
 * pantallas.</p>
 */
class OrderReadyAlertViewTest {

    private static final Path SHARED_JS =
            Path.of("src/main/resources/static/js/order-ready-alert.js");

    private static final Path SOUND =
            Path.of("src/main/resources/static/sounds/notification.mp3");

    /** Las tres listas que pueden levantar un pedido, con el rol que cada una declara. */
    private static final List<String[]> SCREENS = List.of(
            new String[]{"src/main/resources/templates/admin/orders/list.html", "admin"},
            new String[]{"src/main/resources/templates/waiter/orders/list.html", "waiter"},
            new String[]{"src/main/resources/templates/cashier/orders/list.html", "cashier"});

    @Test
    @DisplayName("Las tres listas cargan la alerta y le dicen quién está en la pantalla")
    void everyOrderListLoadsTheAlertWithItsUser() throws IOException {
        for (String[] screen : SCREENS) {
            Path list = Path.of(screen[0]);
            String role = screen[1];
            String page = read(list);

            assertTrue(page.contains("<script th:src=\"@{/js/order-ready-alert.js}\">"),
                    "la pieza de la alerta se carga en " + list);
            assertTrue(page.contains("OrderReadyAlert.init({"), "y se arranca en " + list);
            assertTrue(page.contains("role: \"" + role + "\""),
                    "declarando su rol en " + list + ": la regla de \"es mío\" depende de él");
            assertTrue(page.contains("username: /*[[${currentUsername}]]*/"),
                    "y el usuario de la sesión en " + list
                            + ": sin él no se puede saber si el pedido es suyo");
        }
    }

    @Test
    @DisplayName("El aviso se engancha en el manejador que ya recibe el WebSocket")
    void theAlertIsHookedIntoTheLiveUpdateHandler() throws IOException {
        for (String[] screen : SCREENS) {
            Path list = Path.of(screen[0]);

            String handler = liveUpdateHandler(read(list));

            assertTrue(handler.contains("OrderReadyAlert.handle(notification)"),
                    "el manejador avisa a la alerta en " + list);
        }
    }

    @Test
    @DisplayName("La alerta del centro sustituye al aviso pequeño de la esquina")
    void theCenteredAlertReplacesTheCornerToast() throws IOException {
        // El mesero y el cajero ya anunciaban los cambios de estado con un aviso pequeño en la
        // esquina. Cuando el aviso es el de "pedido listo", esa noticia NO se da dos veces: sale la
        // alerta del centro, que hay que reconocer, y se calla la de la esquina.
        for (String list : List.of(
                "src/main/resources/templates/waiter/orders/list.html",
                "src/main/resources/templates/cashier/orders/list.html")) {
            String handler = liveUpdateHandler(read(Path.of(list)));

            assertTrue(handler.contains("if (!readyAlertShown)"),
                    "el aviso pequeño se salta si ya salió la alerta en " + list);
            assertTrue(handler.contains("const readyAlertShown = window.OrderReadyAlert"),
                    "la alerta se consulta sin romper la página si aún no cargó, en " + list);
            assertTrue(handler.contains("showStatusToast("),
                    "y sigue saliendo para los demás cambios de estado en " + list);
        }

        // El admin no tiene aviso pequeño: ahí la alerta es la única noticia del pedido listo.
        assertFalse(
                liveUpdateHandler(read(Path.of("src/main/resources/templates/admin/orders/list.html")))
                        .contains("showStatusToast("),
                "el admin no usa el aviso de la esquina");
    }

    @Test
    @DisplayName("El aviso dice de qué pedido se trata: su mesa o su tipo")
    void theAlertNamesTheTableOrTheOrderType() throws IOException {
        String js = read(SHARED_JS);

        // Con mesa, por la mesa; sin mesa (reparto, para recoger) por su tipo, que es lo único que
        // lo identifica en el salón.
        assertTrue(js.contains("\"Mesa \" + notification.tableNumber"), "la mesa cuando la hay");
        assertTrue(js.contains("notification.orderTypeLabel"),
                "y si no hay mesa, el tipo rotulado por el servidor (no una traducción inventada aquí)");

        // La frase final se adapta a lo que hay que hacer: no es lo mismo un plato de salón que un
        // domicilio, que se va con el repartidor.
        assertTrue(js.contains("case \"TAKEOUT\"") && js.contains("case \"DELIVERY\""),
                "cada tipo cierra con lo que corresponde");
        assertTrue(js.contains("Ya está listo para pasar a recoger."),
                "y el texto que se pidió para el pedido de salón");
    }

    @Test
    @DisplayName("La alerta suena con el sonido que ya existe y con un solo botón")
    void theAlertUsesTheExistingSoundAndOneButton() throws IOException {
        String js = read(SHARED_JS);

        assertTrue(Files.exists(SOUND), "el sonido debe existir en " + SOUND);
        assertTrue(js.contains("\"/sounds/notification.mp3\""),
                "la alerta usa el sonido de la carpeta sounds/ (no sound/)");
        assertTrue(js.contains("confirmButtonText: \"Entendido\""), "y se cierra con \"Entendido\"");

        // El aviso que importa es el del CENTRO de la pantalla, no un aviso pequeño de la esquina:
        // es lo que pide el flujo del salón, porque hay que reconocerlo antes de salir a recoger.
        assertFalse(js.contains("toast: true"), "la alerta no es un aviso de esquina");
        assertTrue(js.contains("swal.fire({"),
                "y se pinta como el modal de SweetAlert, que sale centrado");
        assertTrue(js.contains("animation: order-ready-pulse"), "y parpadea");
    }

    @Test
    @DisplayName("El latido se aparta al cerrarse: si no, la alerta se queda pegada y bloquea la pantalla")
    void thePulseStepsAsideWhenTheAlertCloses() throws IOException {
        String js = read(SHARED_JS);

        // SweetAlert se lleva la alerta con SU propia animación (la clase swal2-hide) sobre el mismo
        // elemento que late. Dos reglas de "animation" sobre un mismo elemento no se suman: gana una,
        // y era la nuestra, así que la de cierre nunca corría. El resultado se vio en pantalla: al
        // dar "Entendido" la alerta no se iba, el botón quedaba apagado y su capa se comía todos los
        // clics. El latido tiene que dejar de aplicar en cuanto la clase de cierre está puesta.
        assertTrue(
                js.contains(".swal2-popup.order-ready-popup:not(.swal2-hide) { animation: order-ready-pulse"),
                "el latido está condicionado a que la alerta NO esté cerrándose");
        assertFalse(js.contains(".order-ready-popup { animation:"),
                "y no queda ningún latido sin esa condición, que es lo que dejaba la pantalla bloqueada");

        // La excepción de "menos movimiento" tiene que seguir ganando al latido, así que repite la
        // condición (misma especificidad, y va después): sin ella no apagaría nada.
        assertTrue(js.contains(".swal2-popup.order-ready-popup:not(.swal2-hide), .order-ready-title { animation: none; }"),
                "y quien pidió menos movimiento sigue sin ver nada latiendo");

        // Red de seguridad: si otra versión de SweetAlert se llevara la alerta de otra forma, su
        // capa se retira igual y la pantalla no vuelve a quedar muerta.
        assertTrue(js.contains("closing.then(") && js.contains("setTimeout(removeStuckAlert, 700)"),
                "y la capa de una alerta ya cerrada se retira igualmente");
        assertTrue(js.contains(".swal2-popup.order-ready-popup.swal2-hide"),
                "retirando solo la capa de esta alerta, nunca la de otra alerta de la página");
    }

    @Test
    @DisplayName("La regla de \"es mío\" es la del que lo atiende para el mesero y la del creador para el resto")
    void theOwnershipRuleMatchesEachRole() throws IOException {
        String js = read(SHARED_JS);

        // El mesero: el pedido que atiende AHORA (Order.isOperatedBy), para que un traspaso mueva
        // el aviso a quien lo va a recoger.
        assertTrue(js.contains("notification.operatedBy") && js.contains("notification.createdBy"),
                "la regla mira al responsable actual y al creador");
        assertTrue(js.contains("if (settings.role === \"waiter\")"),
                "el mesero usa la regla del responsable actual");

        // Admin/gerente y cajero: solo lo que ellos levantaron. El cajero tiene delante el tablero
        // global, y esos pedidos no son suyos.
        assertTrue(js.contains("return sameUser(notification.createdBy, settings.username);"),
                "los demás roles se quedan con lo que ellos levantaron");

        // Candados del aviso: solo al quedar listo, no en las fotos de ítems, y una sola vez
        // por pedido (hasta que el pedido deje de estar listo, que sería otra ronda).
        assertTrue(js.contains("notification.notificationType !== STATUS_CHANGE"),
                "no reacciona a avisos que no son un cambio de estado");
        assertTrue(js.contains("delete alerted[orderId]"),
                "si el pedido deja de estar listo, la próxima vez vuelve a avisar");
        assertTrue(js.contains("alerted[orderId] = true;"),
                "y cada pedido se avisa una sola vez");
        assertFalse(js.contains("location.reload"), "la alerta no puede recargar la pantalla");
    }

    /** El cuerpo del manejador del WebSocket, que es quien recibe el aviso del servidor. */
    private String liveUpdateHandler(String page) {
        int start = page.indexOf("function handleOrderListUpdate(notification)");
        assertTrue(start > 0, "el manejador del aviso en vivo");

        int end = page.indexOf("\n      function ", start);
        return end > start ? page.substring(start, end) : page.substring(start);
    }

    private String read(Path path) throws IOException {
        assertTrue(Files.exists(path), "existe " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
