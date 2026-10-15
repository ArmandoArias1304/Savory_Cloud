package com.aatechsolutions.elgransazon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El botón "transferir a otro mesero" de la lista del mesero no hacía nada: al sacar el enganche
 * de los botones a una función para que también sirviera a las tarjetas, la línea del traspaso se
 * quedó fuera de esa función y empezó a usar {@code scope}, que ahí no existe. Al dispararse el
 * {@code DOMContentLoaded} la referencia se caía con {@code ReferenceError} y el botón nacía sin
 * listener: ni en la tabla, ni en las tarjetas, ni en el detalle.
 *
 * <p>Aquí se fija la regla que lo evita y que vale para cualquier botón que se añada después:
 * <b>los botones de la fila se enganchan dentro de {@code bindOrderActionButtons}</b>, que es la
 * misma función que la lista le pasa a la vista de tarjetas para reenganchar las copias, y
 * <b>{@code scope} no se usa fuera de la función que lo declara</b>.</p>
 */
class WaiterTransferButtonWiringTest {

    private static final Path LIST =
            Path.of("src/main/resources/templates/waiter/orders/list.html");

    private static final String BIND_FUNCTION = "function bindOrderActionButtons(root) {";

    @Test
    @DisplayName("El traspaso se engancha dentro de la función que también atiende las copias")
    void theTransferButtonIsWiredWhereTheClonesAreWired() throws IOException {
        String page = read();
        String body = bindFunctionBody(page);

        assertTrue(body.contains(".querySelectorAll(\".btn-transfer-order\")"),
                "el traspaso se engancha en bindOrderActionButtons, no en un enganche suelto");
        assertTrue(page.contains("bindActions: bindOrderActionButtons"),
                "y esa es la función con la que la lista reengancha las copias de tarjeta y detalle");
    }

    @Test
    @DisplayName("Ningún uso de scope vive fuera de la función que lo declara")
    void noScopeLeaksOutsideItsFunction() throws IOException {
        String page = read();
        String body = bindFunctionBody(page);

        int inPage = count(page, "\\bscope\\b");
        int inFunction = count(body, "\\bscope\\b");

        assertEquals(inFunction, inPage,
                "todo uso de scope tiene que estar dentro de bindOrderActionButtons: fuera se cae "
                        + "con ReferenceError y deja el enganche a medias");
        assertTrue(inFunction > 0, "la comprobación tiene que estar mirando algo (scope existe)");
    }

    @Test
    @DisplayName("Todos los botones de la fila quedan enganchados, también en las copias")
    void everyRowButtonIsWired() throws IOException {
        String page = read();
        String body = bindFunctionBody(page);
        Set<String> buttons = buttonClasses(page);

        assertTrue(buttons.contains("btn-transfer-order"),
                "la lista del mesero tiene el botón de traspaso");

        for (String button : buttons) {
            assertTrue(body.contains(".querySelectorAll(\"." + button + "\")"),
                    button + ": se engancha en bindOrderActionButtons, o en la fila de la tarjeta "
                            + "y en el detalle no responde");
        }
    }

    @Test
    @DisplayName("Al entrar, las solicitudes de traspaso sin responder se siguen mostrando")
    void pendingTransferRequestsAreStillShown() throws IOException {
        assertTrue(read().contains("showPendingTransferAlerts(pendingTransferAlerts)"),
                "la lista avisa de las transferencias que le esperan al mesero");
    }

    /** El cuerpo de la función, contando llaves para no depender del formato. */
    private static String bindFunctionBody(String page) {
        int start = page.indexOf(BIND_FUNCTION);
        assertTrue(start > 0, "existe " + BIND_FUNCTION);

        int open = page.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < page.length(); i++) {
            char c = page.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return page.substring(open, i + 1);
                }
            }
        }
        throw new AssertionError("la función " + BIND_FUNCTION + " no cierra");
    }

    /** Las clases de botón que usa la página ("btn-algo"), sin repetir. */
    private static Set<String> buttonClasses(String page) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("\\bbtn-[a-z-]+\\b").matcher(page);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }

    private static int count(String text, String regex) {
        int total = 0;
        Matcher matcher = Pattern.compile(regex).matcher(text);
        while (matcher.find()) {
            total++;
        }
        return total;
    }

    private static String read() throws IOException {
        assertTrue(Files.exists(LIST), "existe " + LIST);
        return Files.readString(LIST, StandardCharsets.UTF_8);
    }
}
