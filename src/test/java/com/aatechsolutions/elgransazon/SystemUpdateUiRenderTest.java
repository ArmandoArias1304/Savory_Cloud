package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.SystemUpdate;
import com.aatechsolutions.elgransazon.domain.entity.SystemUpdateRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.support.RequestContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.context.webmvc.SpringWebMvcThymeleafRequestContext;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.spring6.naming.SpringContextVariableNames;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The version chip and the update modal are shared by every role, so they are
 * easy to break without any page compiling wrong. These tests render the real
 * fragment (catching Thymeleaf errors) and pin where each role gets its button.
 */
@SpringBootTest
class SystemUpdateUiRenderTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private WebContext context(SystemUpdate latest, SystemUpdate pending) {
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webApplicationContext);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setContextPath("");

        SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "mesero", "n/a", List.of(new SimpleGrantedAuthority("ROLE_WAITER"))));
        SecurityContextHolder.setContext(securityContext);
        request.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaServletWebApplication application =
                JakartaServletWebApplication.buildApplication(servletContext);
        IWebExchange exchange = application.buildExchange(request, response);
        WebContext context = new WebContext(exchange);
        context.setVariable(
                ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                new ThymeleafEvaluationContext(webApplicationContext, new DefaultConversionService()));
        context.setVariable(SpringContextVariableNames.THYMELEAF_REQUEST_CONTEXT,
                new SpringWebMvcThymeleafRequestContext(
                        new RequestContext(request, response), request));

        context.setVariable("globalSystemConfig", GlobalSystemConfig.builder()
                .systemName("Savory Cloud")
                .systemLogoUrl("https://imagedelivery.net/account/logo/public")
                .build());
        context.setVariable("latestSystemUpdate", latest);
        context.setVariable("pendingSystemUpdate", pending);
        return context;
    }

    private SystemUpdate update() {
        return SystemUpdate.builder()
                .id(7L)
                .version("1.4.2")
                .title("Mejoras para meseros")
                .changes("Nuevo filtro de pedidos por mesa\nCorrección del total de la cuenta")
                .targetRoles(Set.of(SystemUpdateRole.WAITER))
                .published(true)
                .publishedAt(LocalDateTime.of(2026, 10, 19, 10, 30))
                .build();
    }

    private String render(SystemUpdate latest, SystemUpdate pending) {
        return templateEngine.process("fragments/system-update", context(latest, pending));
    }

    @Test
    void modalShowsTheSystemLogoTheVersionAndTheChanges() {
        String html = render(update(), null);

        assertTrue(html.contains("id=\"systemUpdateModal\""), "the modal must render");
        assertTrue(html.contains("1.4.2"), "the version must be visible");
        assertTrue(html.contains("Mejoras para meseros"), "the title must be visible");
        assertTrue(html.contains("Nuevo filtro de pedidos por mesa"), "one change per line");
        assertTrue(html.contains("Corrección del total de la cuenta"), "all changes render");
        assertTrue(html.contains("w=192,h=192,fit=contain"),
                "the configured system logo is used with the large variant");
        assertTrue(html.contains("Gracias por utilizar"), "the friendly footer must be there");
        assertTrue(html.contains("Seguimos mejorando cada día para tu restaurante."));
    }

    @Test
    void modalScrollsInsideItsRoundedFrameInsteadOfOnTheOuterEdge() {
        String html = render(update(), null);
        int dialogBox = html.indexOf("rounded-3xl shadow-2xl");
        assertTrue(dialogBox >= 0, "the modal frame must keep its rounded corners");
        String frameTag = html.substring(html.lastIndexOf("<div", dialogBox),
                html.indexOf(">", dialogBox) + 1);
        assertTrue(frameTag.contains("overflow-hidden"),
                "the rounded frame must clip what is inside it");
        assertTrue(frameTag.contains("flex-col"),
                "the frame lays out its scroll area vertically");
        assertFalse(frameTag.contains("overflow-y-auto"),
                "the frame itself must not scroll, or the bar breaks the rounded corners");

        int scroller = html.indexOf("system-update-scroll", dialogBox);
        assertTrue(scroller > dialogBox,
                "the scroll area lives inside the rounded frame");
        String scrollerTag = html.substring(html.lastIndexOf("<div", scroller),
                html.indexOf(">", scroller) + 1);
        assertTrue(scrollerTag.contains("overflow-y-auto"),
                "an inner area carries the scrolling");
        assertTrue(scrollerTag.contains("min-h-0"),
                "the inner area must be allowed to shrink so it scrolls");
        assertTrue(html.contains(".system-update-scroll::-webkit-scrollbar-thumb"),
                "the thumb is styled rounded and inset from the edge");
    }

    @Test
    void modalOpensAutomaticallyOnlyWhenTheUpdateIsPendingForTheEmployee() {
        String pending = render(update(), update());
        assertTrue(pending.contains("const autoOpen = true;"),
                "a targeted employee gets the modal on entry");
        assertTrue(pending.contains("seenUrl = \"\\/system-updates\\/7\\/seen\""),
                "closing it records that this employee already saw it");

        String alreadySeen = render(update(), null);
        assertTrue(alreadySeen.contains("const autoOpen = false;"),
                "the button still opens the modal, but it does not pop up again");
    }

    @Test
    void noPublishedUpdateMeansNoModalAndNoChip() {
        String html = render(null, null);

        assertFalse(html.contains("id=\"systemUpdateModal\""), "there is no modal to show");
        assertFalse(html.contains("openSystemUpdateModal()"), "there is no version button either");
        assertFalse(html.contains("Gracias por utilizar"), "the footer belongs to the modal");
        assertTrue(html.contains("Aún no hay actualizaciones publicadas."),
                "the admin card explains the empty state");
    }

    @Test
    void everyRoleGetsTheVersionButtonWhereItsNameAppears() throws IOException {
        assertTrue(read("fragments/theme.html").contains("fragments/system-update :: systemUpdateModal"),
                "the modal is included once globally");
        assertTrue(read("admin/system-configuration/form.html")
                        .contains("fragments/system-update :: adminVersionCard"),
                "the admin button replaces the thank-you box in Apariencia");
        assertFalse(read("admin/system-configuration/form.html").contains("Gracias por usar"),
                "the old thank-you box is gone from the admin card");

        String sidebar = read("fragments/sidebar.html");
        assertTrue(sidebar.contains("sec:authorize=\"hasRole('MANAGER')\""),
                "only the manager gets the sidebar chip, because admin already has the card");
        assertTrue(sidebar.contains("versionChip('sidebar')"));
        int chip = sidebar.indexOf("versionChip('sidebar')");
        int roleLabel = sidebar.indexOf("Gerente</span>");
        int logout = sidebar.indexOf("id=\"logoutBtn\"");
        assertTrue(roleLabel >= 0 && chip > roleLabel,
                "the chip must sit below the role label, not between name and role");
        assertTrue(logout > chip,
                "the chip must sit right above the logout button");

        for (String dashboard : List.of(
                "waiter/dashboard.html",
                "cashier/dashboard.html",
                "chef/dashboard.html",
                "delivery/dashboard.html")) {
            assertTrue(read(dashboard).contains("versionChip('primary')"),
                    dashboard + " must show the version next to the employee name");
        }

        assertTrue(read("programmer/dashboard.html").contains("@{/programmer/updates}"),
                "the programmer needs a way to reach the update manager");
    }

    private String read(String template) throws IOException {
        Path path = TEMPLATES.resolve(template);
        assertTrue(Files.exists(path), "template exists: " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
