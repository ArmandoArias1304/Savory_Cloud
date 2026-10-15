package com.aatechsolutions.elgransazon.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.net.URI;

/**
 * Devuelve al usuario a la pantalla donde estaba después de guardar, activar o borrar.
 *
 * <p>Las listas del panel tienen paginación y filtros (platillos con paginación de servidor,
 * ingredientes con búsqueda y orden, pedidos y ventas con página), pero los controladores terminan
 * siempre en un {@code redirect:/<lista>} pelado: si estás en la página 5 filtrando por "tomate" y
 * guardas un cambio, aterrizas en la página 1 sin filtros.</p>
 *
 * <p>En lugar de tocar cada controlador, este interceptor recuerda en la sesión la última pantalla
 * de listado que visitó el usuario y, cuando un {@code redirect:} apunta a esa misma pantalla, le
 * vuelve a pegar su query string. Funciona igual para todas las listas y para todos los roles.</p>
 *
 * <p><strong>Seguridad:</strong> sólo se aceptan rutas locales (empiezan por {@code /}, nunca por
 * {@code //}, sin backslashes ni caracteres de control) y la redirección sólo se reescribe cuando el
 * destino tiene <em>exactamente la misma ruta</em> que la recordada. Así nunca puede desviar a otra
 * pantalla, a otro módulo ni a un sitio externo.</p>
 */
@Slf4j
public class ReturnToLastListInterceptor implements HandlerInterceptor {

    /** Última pantalla de listado visitada, con su query string (página y filtros). */
    public static final String LAST_LIST_ATTR = "volverALista";

    /** Pantalla desde la que se abrió el formulario actual (el "de dónde vengo"). */
    public static final String CAME_FROM_ATTR = "volverDe";

    /** Parámetro opcional con el que un formulario puede pedir explícitamente a dónde volver. */
    private static final String RETURN_TO_PARAM = "returnTo";

    private static final String REDIRECT_PREFIX = "redirect:";

    /** Un listado con más de esto en la URL no es un filtro razonable: se descarta. */
    private static final int MAX_URL_LENGTH = 2000;

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, @Nullable ModelAndView modelAndView) {
        if (modelAndView == null) {
            return;   // Respuestas @ResponseBody (APIs y AJAX): no hay pantalla que recordar
        }

        String viewName = modelAndView.getViewName();
        if (viewName == null) {
            return;
        }

        if (viewName.startsWith(REDIRECT_PREFIX)) {
            modelAndView.setViewName(rewriteRedirect(request, viewName));
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod())) {
            rememberVisit(request, viewName);
        }
    }

    /**
     * Reescribe {@code redirect:/lista} para que incluya la página y los filtros con los que el
     * usuario estaba trabajando, pero sólo si el destino es esa misma lista.
     */
    private String rewriteRedirect(HttpServletRequest request, String viewName) {
        String target = viewName.substring(REDIRECT_PREFIX.length());
        if (target.contains("?")) {
            return viewName;   // el controlador pidió una URL concreta: se respeta tal cual
        }

        String destination = firstMatch(target,
                request.getParameter(RETURN_TO_PARAM),      // lo que pida el formulario
                localReturnUrl(request),                    // acción lanzada desde la propia lista
                sessionAttribute(request, CAME_FROM_ATTR),  // formulario abierto desde la lista
                sessionAttribute(request, LAST_LIST_ATTR)); // última lista visitada

        if (destination == null || destination.equals(target)) {
            return viewName;
        }

        log.debug("Redirección '{}' devuelta a la pantalla de origen: {}", target, destination);
        return REDIRECT_PREFIX + destination;
    }

    /** Primer candidato que sea una ruta local y apunte a la misma pantalla que el destino. */
    private String firstMatch(String targetPath, String... candidates) {
        for (String candidate : candidates) {
            String url = toLocalUrlOfSameScreen(candidate, targetPath);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    /**
     * Normaliza un valor que puede venir como ruta ("/admin/x?a=1") o como URL completa
     * ("http://host/admin/x?a=1") y lo devuelve sólo si es seguro y apunta a la pantalla destino.
     */
    private String toLocalUrlOfSameScreen(String value, String targetPath) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String url = value.trim();
        if (url.regionMatches(true, 0, "http://", 0, 7) || url.regionMatches(true, 0, "https://", 0, 8)) {
            url = pathAndQueryOf(url);
        }

        url = sanitize(url);
        if (url == null || !targetPath.equals(pathOf(url))) {
            return null;
        }
        return url;
    }

    /** Guarda la lista que se está visitando, o el origen del que se llegó a un formulario. */
    private void rememberVisit(HttpServletRequest request, String viewName) {
        if (viewName.endsWith("/list")) {
            store(request, LAST_LIST_ATTR, pathAndQueryOf(request.getRequestURI(),
                    request.getQueryString()));
            return;
        }

        String referer = localReturnUrl(request);
        if (referer != null) {
            store(request, CAME_FROM_ATTR, referer);
        }
    }

    /**
     * Referer de la petición, sólo si viene del mismo host y no es la propia página.
     * Es lo que permite recordar la página en listados con paginación de cliente,
     * donde el número de página vive en la URL del navegador y no en una petición al servidor.
     *
     * <p>Es {@code static} porque el botón "Volver" de los formularios usa exactamente la misma
     * regla: así hay una sola definición de qué es una ruta de regreso segura.</p>
     */
    public static String localReturnUrl(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(referer.trim());
            if (uri.getHost() != null && !uri.getHost().equalsIgnoreCase(request.getServerName())) {
                return null;   // viene de otro sitio: no sirve para volver
            }
            String path = uri.getRawPath();
            if (path == null || path.isBlank() || path.equals(request.getRequestURI())) {
                return null;   // la misma pantalla no aporta nada
            }
            return pathAndQueryOf(path, uri.getRawQuery());
        } catch (IllegalArgumentException e) {
            log.trace("Referer no interpretable, se ignora: {}", referer);
            return null;
        }
    }

    /** Sólo guarda URLs con query string: sin filtros ni página no hay nada que restaurar. */
    private void store(HttpServletRequest request, String attribute, String url) {
        if (url == null || !url.contains("?")) {
            return;
        }

        HttpSession session = request.getSession();
        if (url.equals(session.getAttribute(attribute))) {
            return;
        }
        session.setAttribute(attribute, url);
    }

    private String sessionAttribute(HttpServletRequest request, String attribute) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(attribute);
        return value instanceof String url ? url : null;
    }

    /** Ruta local + query, o {@code null} si el valor no es una URL completa interpretable. */
    private static String pathAndQueryOf(String absoluteUrl) {
        try {
            URI uri = URI.create(absoluteUrl);
            return pathAndQueryOf(uri.getRawPath(), uri.getRawQuery());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String pathAndQueryOf(String path, String query) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return query == null || query.isBlank() ? path : path + "?" + query;
    }

    private String pathOf(String url) {
        int queryStart = url.indexOf('?');
        return queryStart < 0 ? url : url.substring(0, queryStart);
    }

    /** Acepta sólo rutas locales y sin caracteres peligrosos (cabeceras, esquemas, backslashes). */
    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }

        String url = value.trim();
        if (url.isEmpty() || url.length() > MAX_URL_LENGTH) {
            return null;
        }
        if (!url.startsWith("/") || url.startsWith("//")) {
            return null;
        }
        if (url.indexOf('\\') >= 0) {
            return null;
        }
        for (int i = 0; i < url.length(); i++) {
            if (url.charAt(i) < 0x20) {
                return null;   // caracteres de control: inyección de cabeceras
            }
        }
        return url;
    }
}
