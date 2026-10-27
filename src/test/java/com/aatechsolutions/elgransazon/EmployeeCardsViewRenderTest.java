package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.Role;
import com.aatechsolutions.elgransazon.domain.entity.Shift;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La lista de empleados se puede ver como tabla o como tarjetas con forma de gafete, y las dos
 * vistas son la misma lista: la fila y la tarjeta publican los mismos atributos {@code data-*}
 * para que el filtro sea uno solo, la tarjeta abre la ficha que ya existía y ofrece las mismas
 * acciones (editar y activar/desactivar).
 *
 * <p>La tarjeta vive en {@code admin/employees/employee-card.html}, así que se procesa sola con
 * Thymeleaf —sin base de datos ni barra lateral— y las pruebas comprueban de verdad lo que se
 * pinta, incluido que las acciones de administración solo aparezcan con
 * {@code canManageEmployees}. La plantilla de la lista se revisa como texto, igual que
 * {@code AdminWaiterOrdersCardsViewTest}.</p>
 */
class EmployeeCardsViewRenderTest {

    private static final Path LIST_TEMPLATE =
            Path.of("src/main/resources/templates/admin/employees/list.html");

    private static final Path CARD_FRAGMENT =
            Path.of("src/main/resources/templates/admin/employees/employee-card.html");

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

    /** Un contexto web real para que los enlaces {@code @{...}} del gafete se resuelvan. */
    private WebContext context(Employee employee, boolean canManageEmployees) {
        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setContextPath("");
        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        IWebExchange exchange = application.buildExchange(request, response);

        WebContext ctx = new WebContext(exchange);
        ctx.setVariable("globalSystemConfig",
                GlobalSystemConfig.builder().systemName("El Gran Sazón").systemLogoUrl(null).build());
        ctx.setVariable("employee", employee);
        ctx.setVariable("canManageEmployees", canManageEmployees);
        return ctx;
    }

    private Employee employee(boolean enabled, String role) {
        Employee supervisor = Employee.builder()
                .idEmpleado(1L)
                .nombre("Marta")
                .apellido("Rivas")
                .username("marta")
                .enabled(true)
                .build();

        return Employee.builder()
                .idEmpleado(7L)
                .nombre("Ana")
                .apellido("Lopez")
                .username("ana")
                .telefono("5512345678")
                .edad(28)
                .salario(1200.5)
                .enabled(enabled)
                .supervisor(supervisor)
                .roles(Set.of(Role.builder().idRol(2L).nombreRol(role).build()))
                .shifts(Set.of(Shift.builder().id(3L).name("Matutino").build()))
                .build();
    }

    private String renderCard(Employee employee, boolean canManageEmployees) {
        return engine.process("admin/employees/employee-card", context(employee, canManageEmployees));
    }

    /**
     * La lista completa se puede procesar sin Spring si la barra lateral y el tema se sustituyen
     * por los stubs de {@code src/test/resources/templates-stubs}; así se comprueba el bucle real
     * de tarjetas de la plantilla.
     */
    private TemplateEngine listEngine() {
        ClassLoaderTemplateResolver stubs = new ClassLoaderTemplateResolver();
        stubs.setPrefix("templates-stubs/");
        stubs.setSuffix(".html");
        stubs.setTemplateMode(TemplateMode.HTML);
        stubs.setCharacterEncoding("UTF-8");
        stubs.setCheckExistence(true);
        stubs.setCacheable(false);
        stubs.setOrder(1);

        ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setTemplateMode(TemplateMode.HTML);
        templates.setCharacterEncoding("UTF-8");
        templates.setCacheable(false);
        templates.setOrder(2);

        TemplateEngine listEngine = new TemplateEngine();
        listEngine.setTemplateResolver(stubs);
        listEngine.addTemplateResolver(templates);
        listEngine.setDialect(new SpringStandardDialect());
        return listEngine;
    }

    private WebContext listContext() {
        WebContext ctx = context(employee(true, Role.WAITER), true);

        Employee second = Employee.builder()
                .idEmpleado(8L)
                .nombre("Carlos")
                .apellido("Mendez")
                .username("carlos")
                .enabled(false)
                .roles(Set.of(Role.builder().idRol(3L).nombreRol(Role.CASHIER).build()))
                .build();

        ctx.setVariable("employees", List.of(employee(true, Role.WAITER), second));
        ctx.setVariable("allShifts", List.of(Shift.builder().id(3L).name("Matutino").build()));
        ctx.setVariable("allRoles", List.of(Role.builder().idRol(2L).nombreRol(Role.WAITER).build()));
        ctx.setVariable("totalCount", 2L);
        ctx.setVariable("enabledCount", 1L);
        ctx.setVariable("disabledCount", 1L);
        ctx.setVariable("rolesCount", 1L);
        ctx.setVariable("maxUsers", 10);
        ctx.setVariable("canCreateMoreUsers", true);
        ctx.setVariable("hasUserLimit", true);
        return ctx;
    }

    /** El SVG completo (apertura y cierre) que dibuja el avatar de la tarjeta. */
    private static String iconSvg(String html) {
        int start = html.indexOf("<svg");
        int end = html.indexOf("</svg>", start);
        assertTrue(start >= 0 && end > start, "the card must draw the role icon as an inline svg");
        return html.substring(start, end + "</svg>".length());
    }

    private static int count(String text, String needle) {
        int total = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            total++;
            index += needle.length();
        }
        return total;
    }

    @Test
    @DisplayName("El gafete muestra los datos del empleado y abre su ficha al pulsarlo")
    void cardShowsEmployeeDataAndOpensTheDetailsModal() {
        String html = renderCard(employee(true, Role.WAITER), true);

        assertNotNull(html);
        assertTrue(html.contains("class=\"employee-item employee-card"),
                "the card must be part of the list that the filters walk through");
        assertTrue(html.contains("showEmployeeDetails(7)"),
                "clicking the card must open the existing details modal");
        assertTrue(html.contains("data-name=\"ana lopez\""),
                "the card must publish the name used by the filters");
        assertTrue(html.contains("data-role=\"ROLE_WAITER\""), "the card must publish the role");
        assertTrue(html.contains("data-enabled=\"true\""), "the card must publish the status");
        assertTrue(html.contains("data-employee-id=\"7\""), "the card must publish its id");

        assertTrue(html.contains("badge-avatar") && html.contains("rounded-full"),
                "the avatar must be a circle");
        assertFalse(html.contains("badge-hexagon") || html.contains("clip-path"),
                "the avatar must not be a hexagon any more");
        assertFalse(html.contains(">AL<"),
                "the avatar must show the role icon instead of the initials");
        assertTrue(iconSvg(html).contains("a5.8 5.8 0 0 1 11.6 0"),
                "a waiter card must draw the service bell");
        assertFalse(html.contains("badge-barcode"),
                "the card must not keep the decorative barcode: it says nothing about the employee");
        assertTrue(html.contains("Gafete de personal"), "the card header must label the badge");
        assertTrue(html.contains("Ana Lopez"), "the card must show the full name");
        assertTrue(html.contains("@ana"), "the card must show the username");
        assertTrue(html.contains("Mesero"), "the card must show the role");
        assertTrue(html.contains("$1,200.50"), "the card must show the salary");
        assertTrue(html.contains("1 turno"), "the card must show the assigned shifts");
        assertTrue(html.contains("5512345678"), "the card must show the phone number");
        assertTrue(html.contains("Marta Rivas"), "the card must show the supervisor");
        assertTrue(html.contains("Activo"), "the card must show the current status");
    }

    @Test
    @DisplayName("La tarjeta ofrece editar y desactivar sin abrir el modal al pulsarlos")
    void cardActionsEditAndDeactivate() {
        String html = renderCard(employee(true, Role.WAITER), true);

        assertTrue(html.contains("/admin/employees/7/edit"), "the card must offer the edit action");
        assertTrue(html.contains("onclick=\"event.stopPropagation()\""),
                "editing from the card must not open the details modal");
        assertTrue(html.contains("onclick=\"onEmployeeCardStatusClick(event, this)\""),
                "the card must offer the activate/deactivate action");
        assertTrue(html.contains("data-target-enabled=\"false\""),
                "an active employee must be deactivated from the card");
        assertTrue(html.contains(">Desactivar"), "the button must name the action it performs");
    }

    @Test
    @DisplayName("Cada rol dibuja su propio icono en el círculo del gafete")
    void everyRoleDrawsItsOwnIcon() {
        List<String> roles = List.of(Role.ADMIN, Role.MANAGER, Role.WAITER, Role.CHEF, Role.BARISTA,
                Role.PARRILLERO, Role.CASHIER, Role.DELIVERY, Role.PROGRAMMER);

        Map<String, String> icons = new LinkedHashMap<>();
        for (String role : roles) {
            String html = renderCard(employee(true, role), true);
            assertEquals(1, count(html, "<svg"),
                    "the card must draw exactly one role icon for " + role);
            icons.put(role, iconSvg(html));
        }

        assertEquals(roles.size(), new HashSet<>(icons.values()).size(),
                "two roles must never share the same icon: " + icons.keySet());
    }

    @Test
    @DisplayName("Un cliente o un empleado sin rol usan la silueta de persona")
    void clientAndUnknownRolesFallBackToThePersonIcon() {
        Employee withoutRole = Employee.builder()
                .idEmpleado(9L)
                .nombre("Luis")
                .apellido("Soto")
                .username("luis")
                .enabled(true)
                .build();

        String client = renderCard(employee(true, Role.CLIENT), true);
        String unknown = renderCard(withoutRole, true);

        assertEquals(iconSvg(client), iconSvg(unknown),
                "a role without its own drawing must fall back to the person icon");
        assertTrue(iconSvg(unknown).contains("cy=\"8.4\""),
                "the fallback must be the person silhouette");
    }

    @Test
    @DisplayName("Sin teléfono ni supervisor la tarjeta lo dice en vez de dejar huecos")
    void cardExplainsMissingContactData() {
        Employee withoutData = Employee.builder()
                .idEmpleado(9L)
                .nombre("Luis")
                .apellido("Soto")
                .username("luis")
                .enabled(true)
                .roles(Set.of(Role.builder().idRol(2L).nombreRol(Role.WAITER).build()))
                .build();

        String html = renderCard(withoutData, true);

        assertTrue(html.contains("Sin teléfono"), "a missing phone must be explained");
        assertTrue(html.contains("Sin supervisor"), "a missing supervisor must be explained");
    }

    @Test
    @DisplayName("Una tarjeta inactiva ofrece activar y el ADMIN no se puede desactivar")
    void inactiveAndProtectedEmployees() {
        String inactive = renderCard(employee(false, Role.WAITER), true);
        assertTrue(inactive.contains("Inactivo"), "the card must show the inactive status");
        assertTrue(inactive.contains("data-target-enabled=\"true\""),
                "an inactive employee must be activated from the card");
        assertTrue(inactive.contains(">Activar"), "the button must offer to activate");

        String admin = renderCard(employee(true, Role.ADMIN), true);
        assertTrue(admin.contains("Protegido"), "the administrator card must explain the missing action");
        assertFalse(admin.contains("onEmployeeCardStatusClick"),
                "the administrator must not be deactivatable from the card");
    }

    @Test
    @DisplayName("Solo quien administra ve la acción de desactivar en la tarjeta")
    void deactivateActionIsAdminOnly() {
        String html = renderCard(employee(true, Role.WAITER), false);

        assertTrue(html.contains("/admin/employees/7/edit"),
                "a manager can still see the card and its edit action");
        assertFalse(html.contains("onEmployeeCardStatusClick"),
                "a manager must not see the activate/deactivate action");
        assertFalse(html.contains("Protegido"),
                "a manager must not see the administrator hint either");
    }

    @Test
    @DisplayName("La lista pinta una tarjeta por empleado y conserva la tabla")
    void listRendersOneCardPerEmployee() {
        String html = listEngine().process("admin/employees/list", listContext());

        assertNotNull(html);
        assertTrue(html.contains("id=\"cardsView\""), "the cards view must be in the page");
        assertTrue(html.contains("id=\"tableView\""), "the table view must be in the page");
        assertTrue(html.contains("Ana Lopez") && html.contains("Carlos Mendez"),
                "both employees must appear in the cards view");
        assertTrue(html.contains("showEmployeeDetails(7)") && html.contains("showEmployeeDetails(8)"),
                "each card must open its own employee");
        assertTrue(count(html, "class=\"employee-item employee-card") == 2,
                "there must be one card per employee, not a single card");
        assertTrue(count(html, "class=\"employee-item employee-row") == 2,
                "the table must keep one row per employee");
        assertTrue(html.contains("Gafete de personal"), "the card fragment must be included");
        assertEquals(2, count(html, "class=\"badge-avatar mx-auto"),
                "each card must draw its own circular avatar");
    }

    @Test
    @DisplayName("La lista usa la tarjeta, ofrece ambas vistas y comparte el filtro")
    void listWiresTheCardsViewAndTheSharedFilters() throws IOException {
        String template = Files.readString(LIST_TEMPLATE, StandardCharsets.UTF_8);
        String fragment = Files.readString(CARD_FRAGMENT, StandardCharsets.UTF_8);

        assertTrue(template.contains("id=\"tableView\""), "the table view container must exist");
        assertTrue(template.contains("id=\"cardsView\""), "the cards view container must exist");
        assertTrue(template.contains(
                        "th:replace=\"~{admin/employees/employee-card :: employeeCard(${employee}, ${canManageEmployees})}\""),
                "the list must render one card per employee with the shared fragment");
        assertTrue(template.contains("<th:block th:each=\"employee : ${employees}\">"),
                "the loop must wrap the include: th:each and th:replace on the same element renders a single card");
        assertTrue(fragment.contains("th:fragment=\"employeeCard(employee, canManageEmployees)\""),
                "the card fragment must accept the employee and the permission");

        assertTrue(template.contains("switchEmployeeView('table')") && template.contains("switchEmployeeView('cards')"),
                "the user must be able to switch between table and cards");
        assertTrue(template.contains("localStorage.getItem(\"employeesView\") || \"cards\""),
                "the cards view must be the default and the last choice must be remembered");
        assertTrue(template.contains("localStorage.setItem(\"employeesView\""),
                "switching views must be remembered");

        assertTrue(template.contains("document.querySelectorAll(\".employee-item\")"),
                "one filter must cover the table and the cards");
        assertTrue(template.contains("class=\"employee-item employee-row"),
                "the table row must publish the same data as the card");
        assertTrue(template.contains("id=\"noResultsCards\""),
                "the cards view needs its own no-results notice");
        assertTrue(template.contains("id=\"employeeModal\""),
                "the details modal the card opens must still be present");

        assertTrue(template.contains("function onEmployeeCardStatusClick(event, button)"),
                "the card action must have its own handler");
        assertTrue(template.contains("function requestEmployeeStatusChange(employeeId, employeeName, enabled)"),
                "the row switch and the card button must share the same confirmation");
        assertTrue(template.contains("requestEmployeeStatusChange(employeeId, employeeName, enabled)"),
                "the row switch must use the shared confirmation");

        assertTrue(fragment.contains(
                        "th:if=\"${canManageEmployees and !employee.hasRole('ROLE_ADMIN') and !employee.hasRole('ROLE_PROGRAMMER')}\""),
                "ADMIN and PROGRAMMER employees must not be deactivatable from the card");
    }
}
