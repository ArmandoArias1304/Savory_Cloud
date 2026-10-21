package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.presentation.dto.DashboardStatsDTO;
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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El encabezado del dashboard de admin/gerente saluda por nombre y enseña la fecha
 * más un reloj en vivo. Ambos roles caen en esta misma plantilla, así que el
 * saludo no puede quedar genérico y el reloj necesita su propio guion.
 */
@SpringBootTest
class AdminDashboardHeaderRenderTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private DashboardStatsDTO stats() {
        return DashboardStatsDTO.builder()
                .todaySales(BigDecimal.ZERO)
                .salesChangePercentage(0.0)
                .salesIncreased(true)
                .todayOrders(0L)
                .ordersChangePercentage(0.0)
                .ordersIncreased(true)
                .todayCustomers(0L)
                .customersChangePercentage(0.0)
                .customersIncreased(true)
                .totalHistoricalRevenue(BigDecimal.ZERO)
                .popularItems(List.of())
                .activeEmployees(0)
                .totalEmployees(0)
                .capacityPercentage(0.0)
                .employeeInitials(List.of())
                .inventoryAlerts(List.of())
                .hourlySales(List.of())
                .tableStatus(DashboardStatsDTO.TableStatusDTO.builder()
                        .totalTables(0).available(0).occupied(0).outOfService(0).build())
                .pendingOrders(DashboardStatsDTO.PendingOrdersDTO.builder()
                        .pending(0L).inPreparation(0L).ready(0L).onTheWay(0L).build())
                .todayReservations(List.of())
                .build();
    }

    /**
     * El sidebar adjunto usa {@code #authentication} y {@code sec:authorize},
     * así que el contexto necesita el SecurityContext real y el ApplicationContext
     * en el ServletContext (mismo arquetipo que SidebarCategoriesRenderTest).
     * ROLE_WAITER deja fuera los enlaces ADMIN/MANAGER del sidebar: sus
     * {@code th:if} resuelven {@code @licenseService} y necesitan el pipeline
     * real de request. El saludo no depende del rol, solo del nombre.
     */
    private AbstractContext webContext() {
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webApplicationContext);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setContextPath("");
        SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "maria.g", "n/a", List.of(new SimpleGrantedAuthority("ROLE_WAITER"))));
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

    private String render(String displayName) {
        AbstractContext context = webContext();

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("globalSystemConfig", GlobalSystemConfig.builder().systemName("SavoryCloud").build());
        variables.put("systemConfig", new SystemConfiguration());
        variables.put("config", new SystemConfiguration());
        variables.put("stats", stats());
        variables.put("username", "maria.g");
        variables.put("displayName", displayName);
        variables.put("role", "Administrator");
        variables.put("isRestaurantOpen", true);
        variables.put("currentPage", 1);
        variables.put("totalPages", 1);
        variables.forEach(context::setVariable);
        String html = templateEngine.process("admin/dashboard", context);
        SecurityContextHolder.clearContext();
        return html;
    }

    @Test
    void greetingShowsTheLoggedInUsersName() {
        String html = render("María González");

        assertTrue(html.contains("Bienvenido de vuelta,"),
                "el saludo conserva su texto base");
        assertTrue(html.contains(">María González</span"),
                "el nombre de quien inició sesión va dentro del saludo");
        assertTrue(html.contains(", aquí está tu resumen del día"),
                "la frase se cierra después del nombre");
        assertFalse(html.contains("Bienvenido de vuelta, aquí está tu resumen del día"),
                "el saludo ya no puede quedar sin el nombre");
    }

    @Test
    void greetingFallsBackToTheUsernameWhenNoNameIsResolved() {
        String html = render(null);

        assertTrue(html.contains(">maria.g</span"),
                "sin nombre completo se usa el usuario del inicio de sesión");
    }

    @Test
    void headerShowsTheDateAndALiveClock() {
        String html = render("María González");

        assertTrue(html.contains("id=\"dashboardDate\""), "la fecha tiene su contenedor");
        assertTrue(html.contains("id=\"dashboardClock\""), "el reloj tiene su contenedor");
        assertTrue(html.contains("tabular-nums"),
                "las cifras del reloj no deben saltar al cambiar");
        assertTrue(html.contains("calendar_today"), "la fecha lleva su icono");

        assertTrue(html.contains("const dateEl = document.getElementById(\"dashboardDate\")"),
                "el guion toma el nodo de la fecha");
        assertTrue(html.contains("const clockEl = document.getElementById(\"dashboardClock\")"),
                "el guion toma el nodo del reloj");
        assertTrue(html.contains("setInterval(tick, 1000)"),
                "el reloj se actualiza cada segundo");
        assertTrue(html.contains("pad(now.getHours())"), "el reloj muestra la hora");
        assertTrue(html.contains("pad(now.getMinutes())"), "el reloj muestra los minutos");
        assertTrue(html.contains("pad(now.getSeconds())"), "el reloj muestra los segundos");
        assertTrue(html.contains("\"diciembre\"") && html.contains("\"miércoles\""),
                "la fecha se escribe en español");
    }
}
