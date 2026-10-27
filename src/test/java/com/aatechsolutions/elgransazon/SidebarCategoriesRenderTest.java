package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.context.WebApplicationContext;
import org.thymeleaf.context.AbstractContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renderiza el sidebar real para fijar su organización por categorías: los
 * grupos son secciones estáticas siempre visibles (sin acordeón), los títulos
 * usan el verde de la marca en claro y en oscuro, y ningún enlace
 * existente se pierde.
 */
@SpringBootTest
class SidebarCategoriesRenderTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private WebApplicationContext webApplicationContext;

    /**
     * ROLE_WAITER deja fuera los enlaces ADMIN/MANAGER: sus {@code th:if}
     * resuelven {@code @licenseService} y necesitan el pipeline real de request.
     */
    private AbstractContext webContext() {
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webApplicationContext);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setContextPath("");
        SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "ana", "n/a", List.of(new SimpleGrantedAuthority("ROLE_WAITER"))));
        SecurityContextHolder.setContext(securityContext);
        request.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaServletWebApplication application =
                JakartaServletWebApplication.buildApplication(servletContext);
        IWebExchange exchange = application.buildExchange(request, response);
        return new WebContext(exchange);
    }

    private String render(String activeMenu) {
        AbstractContext ctx = webContext();
        ctx.setVariable("activeMenu", activeMenu);
        ctx.setVariable("globalSystemConfig",
                GlobalSystemConfig.builder().systemName("Test").systemLogoUrl(null).build());
        String html = templateEngine.process("fragments/sidebar", ctx);
        SecurityContextHolder.clearContext();
        return html;
    }

    private static int count(String haystack, String needle) {
        int total = 0;
        int from = 0;
        while ((from = haystack.indexOf(needle, from)) != -1) {
            total++;
            from += needle.length();
        }
        return total;
    }

    /** Sección de la categoría cuyo rótulo se indica. */
    private static String groupOf(String html, String label) {
        for (String part : html.split("<div class=\"nav-group\">")) {
            if (part.contains("<span>" + label + "</span>")) {
                return part;
            }
        }
        throw new AssertionError("No se encontró la categoría " + label);
    }

    @Test
    void categoriesAreAlwaysVisibleStaticSections() {
        String html = render("dashboard");

        assertFalse(html.contains("<details"), "las categorías ya no deben ser desplegables");
        assertFalse(html.contains("nav-group-chevron"), "ya no hay chevrons de expansión");
        assertEquals(6, count(html, "<div class=\"nav-group\">"), "deben existir seis categorías");
        assertEquals(6, count(html, "class=\"nav-group-title"),
                "cada categoría necesita su título");

        for (String label : List.of("Operación", "Catálogo", "Inventario", "Usuarios", "Análisis", "Sistema")) {
            String group = groupOf(html, label);
            assertTrue(group.contains("nav-group-title"), label + " debe tener su título de sección");
            assertTrue(group.contains("class=\"nav-item"), label + " debe listar sus módulos");
        }
    }

    @Test
    void categoryTitlesAreBrandGreenInLightAndDark() {
        String html = render("dashboard");

        assertEquals(6, count(html, "nav-group-title flex items-center gap-2 px-3 sm:px-4 pt-4 pb-1 "
                + "text-[11px] font-semibold uppercase tracking-wider text-primary"),
                "cada título debe ser verde de marca en claro y en oscuro");
        assertFalse(html.contains("uppercase tracking-wider text-gray-900"),
                "los títulos ya no deben ser negros en modo claro");
        assertFalse(html.contains("uppercase tracking-wider text-gray-500"));
        assertFalse(html.contains("uppercase tracking-wider text-gray-400"));
    }

    @Test
    void activePageStillMarksItsMenuItem() {
        String kitchen = render("kitchen");
        assertTrue(groupOf(kitchen, "Operación").contains("active bg-gradient-to-r"),
                "Cocina debe seguir marcado como activo");
        assertFalse(groupOf(kitchen, "Catálogo").contains("active bg-gradient-to-r"),
                "las demás categorías no deben tener enlaces activos");

        assertTrue(groupOf(render("inventory"), "Inventario").contains("active bg-gradient-to-r"));
        assertTrue(groupOf(render("reports"), "Análisis").contains("active bg-gradient-to-r"));
    }

    @Test
    void sidebarCanBeCollapsedOnDesktopAndRemembersIt() {
        String html = render("dashboard");

        assertTrue(html.contains("id=\"sidebarCollapse\""),
                "el sidebar necesita su botón para ocultarse");
        assertTrue(html.contains("id=\"sidebarExpand\""),
                "debe existir un control para volver a mostrarlo");
        assertTrue(html.contains("aria-controls=\"sidebar\""),
                "ambos controles deben apuntar al sidebar");
        assertTrue(html.contains("html.sidebar-collapsed #sidebar"),
                "la regla del sidebar minimizado debe estar presente");
        assertTrue(html.contains("width: 4.75rem"),
                "minimizado debe quedar como riel de iconos, no desaparecer");
        assertTrue(html.contains("elgransazon-sidebar-collapsed"),
                "la preferencia debe persistir entre páginas");
        assertTrue(html.contains("id=\"sidebarScroll\""),
                "el menú debe tener un contenedor de desplazamiento identificable");
        assertTrue(html.contains("elgransazon-sidebar-scroll-top"),
                "la posición del menú debe conservarse al navegar entre páginas");
        assertTrue(html.contains("sessionStorage.setItem")
                        && html.contains("scrollContainer.scrollTop = Number(storedScrollTop)"),
                "el desplazamiento se guarda al navegar y se restaura al cargar");
        assertTrue(html.contains("class=\"nav-label\">Cocina")
                        && html.contains("class=\"nav-label\">Cerrar Sesión"),
                "los rótulos de los módulos y el de cerrar sesión deben poder ocultarse");
        assertTrue(html.contains("title=\"Cocina\"") && html.contains("aria-label=\"Cocina\""),
                "cada icono debe conservar nombre accesible y tooltip");
        assertTrue(html.contains("sidebar-monogram"),
                "sin logo, el riel debe mostrar un monograma");
    }

    @Test
    void everyMenuIconInheritsTheItemTextColor() {
        String html = render("dashboard");

        assertFalse(html.contains("? 'text-primary' : 'text-gray-400"),
                "ningún icono debe apagar su color con gris propio");
        assertFalse(html.contains("? 'text-primary' : 'text-gray-500"),
                "ningún icono debe apagar su color con gris propio");
        assertTrue(html.contains(">print</span"),
                "el icono de impresoras debe seguir en el sidebar");
    }

    @Test
    void systemNameIsNeverTruncatedInTheHeader() {
        String html = render("dashboard");
        int brand = html.indexOf("sidebar-brand");
        assertTrue(brand >= 0, "el bloque del nombre debe existir");
        int h1Start = html.indexOf("<h1", brand);
        int h1End = html.indexOf("</h1>", h1Start);
        assertTrue(h1Start >= 0 && h1End > h1Start, "el nombre del sistema va en un h1");
        String h1 = html.substring(h1Start, h1End);

        assertFalse(h1.contains("truncate"),
                "el nombre del sistema no debe recortarse con puntos suspensivos");
        assertTrue(h1.contains("break-words"),
                "el nombre debe poder envolver para mostrarse completo");
        assertTrue(h1.contains(">Test"),
                "el nombre completo debe salir entero, sin recorte");
    }

    @Test
    void everyMenuLinkKeepsWorkingInsideItsCategory() {
        String html = render("reviews");

        for (String href : List.of(
                "/admin/dashboard",
                "/admin/orders/select-table",
                "/admin/kitchen",
                "/admin/orders",
                "/admin/tables",
                "/admin/reservations",
                "/admin/menu-items",
                "/admin/complements",
                "/admin/promotions",
                "/admin/inventory",
                "/admin/suppliers",
                "/admin/employees",
                "/admin/reviews",
                "/admin/sales",
                "/admin/reports",
                "/admin/printers")) {
            assertTrue(html.contains(href), "el enlace " + href + " no debe desaparecer");
        }

        assertTrue(groupOf(html, "Operación").contains("/admin/kitchen"));
        assertTrue(groupOf(html, "Operación").contains("/admin/orders"));
        assertTrue(groupOf(html, "Catálogo").contains("/admin/menu-items"));
        assertTrue(groupOf(html, "Inventario").contains("/admin/inventory"));
        assertTrue(groupOf(html, "Usuarios").contains("/admin/employees"));
        assertTrue(groupOf(html, "Análisis").contains("/admin/reports"));
        assertTrue(groupOf(html, "Sistema").contains("/admin/printers"));

        // Los accesos ADMIN/MANAGER siguen ocultos para el resto de roles.
        assertFalse(html.contains("/admin/facturacion"));
        assertFalse(html.contains("/admin/system-configuration"));
        assertFalse(html.contains("/admin/cash-register"));
        assertFalse(html.contains("/admin/shop"));
    }
}
