package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
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
import org.thymeleaf.context.AbstractContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los clientes registrados se pueden ver como tabla o como tarjetas, con la misma forma que las de
 * ventas.
 *
 * <p>Las tarjetas no se pintan en el servidor: las construye {@code js/customers-view.js} desde las
 * filas de la tabla, que siguen siendo la única fuente de verdad. Eso crea un contrato silencioso
 * —la fila publica sus datos en atributos {@code data-customer-*} y la tarjeta los lee por
 * nombre—, así que aquí se renderiza la plantilla real y se comprueba lo que publica, además de
 * recorrer el JavaScript para verificar que cada atributo que lee existe en la plantilla.</p>
 *
 * <p>La pantalla filtra en el navegador (oculta filas), así que también se comprueba que las
 * tarjetas se rehagan después de filtrar.</p>
 */
@SpringBootTest
class CustomersCardsViewTest {

    private static final Path TEMPLATE =
            Path.of("src/main/resources/templates/admin/customers/list.html");

    private static final Path SCRIPT =
            Path.of("src/main/resources/static/js/customers-view.js");

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private AbstractContext webContext() {
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webApplicationContext);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setContextPath("");
        SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(securityContext);
        request.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaServletWebApplication application =
                JakartaServletWebApplication.buildApplication(servletContext);
        IWebExchange exchange = application.buildExchange(request, response);
        WebContext context = new WebContext(exchange);
        context.setVariable(
                ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                new ThymeleafEvaluationContext(webApplicationContext, new DefaultConversionService()));
        return context;
    }

    private Customer customer(long id, String fullName, String username, String email,
                              String phone, boolean active, boolean verified,
                              LocalDateTime createdAt, LocalDateTime lastAccess) {
        return Customer.builder()
                .idCustomer(id)
                .fullName(fullName)
                .username(username)
                .email(email)
                .phone(phone)
                .password("x")
                .active(active)
                .emailVerified(verified)
                .createdAt(createdAt)
                .lastAccess(lastAccess)
                .build();
    }

    private List<Customer> customers() {
        return List.of(
                customer(7L, "Ana López", "ana.lopez", "ana@example.com", "5512345678",
                        true, true,
                        LocalDateTime.of(2026, 10, 23, 15, 0),
                        LocalDateTime.of(2026, 10, 23, 18, 30)),
                customer(8L, "Carlos Méndez", "carlos.m", "carlos@example.com", "5598765432",
                        false, false,
                        LocalDateTime.of(2026, 9, 1, 10, 0),
                        null));
    }

    private String render(List<Customer> customers) {
        AbstractContext ctx = webContext();
        ctx.setVariable("globalSystemConfig",
                GlobalSystemConfig.builder().systemName("Test").systemLogoUrl(null).build());
        ctx.setVariable("customers", customers);
        ctx.setVariable("currentFilter", "ALL");
        ctx.setVariable("searchTerm", "");
        ctx.setVariable("totalCount", (long) customers.size());
        ctx.setVariable("activeCount", customers.stream().filter(Customer::isActive).count());
        ctx.setVariable("inactiveCount", customers.stream().filter(c -> !c.isActive()).count());
        ctx.setVariable("verifiedCount",
                customers.stream().filter(c -> Boolean.TRUE.equals(c.getEmailVerified())).count());
        ctx.setVariable("unverifiedCount",
                customers.stream().filter(c -> !Boolean.TRUE.equals(c.getEmailVerified())).count());
        ctx.setVariable("companyZone", ZoneId.of("America/Mexico_City"));
        ctx.setVariable("activeMenu", "customers");
        ctx.setVariable("username", "admin");
        return templateEngine.process("admin/customers/list", ctx);
    }

    @Test
    void pageOffersTheCardsViewOfTheSameList() {
        String html = render(customers());

        assertNotNull(html);
        assertTrue(html.contains("data-orders-view=\"table\""), "the toggle must offer the table");
        assertTrue(html.contains("data-orders-view=\"grid\""), "the toggle must offer the cards");
        assertTrue(html.contains("data-orders-view-scope=\"customers\""),
                "the toggle must say which list it switches");
        assertTrue(html.contains("id=\"customersTableView\""), "the table is the table view");
        assertTrue(html.contains("id=\"customersCardsView\""),
                "the cards need their own container");
        assertTrue(html.contains("id=\"customersCards\""), "the cards need a grid to be built into");
        assertTrue(html.contains("id=\"customersCardsEmpty\""),
                "the cards view needs its own no-results notice");
        assertTrue(html.contains("/js/customers-view.js"),
                "the page must load the script that builds the cards");
        assertTrue(html.contains("CustomersView.init("),
                "the script must be started with the view it remembers");
    }

    @Test
    void eachRowPublishesTheCustomerCardData() {
        String html = render(customers());

        assertTrue(html.contains("data-customer-id=\"7\""), "the row must identify the customer");
        assertTrue(html.contains("data-customer-name=\"Ana López\""),
                "the card shows the name as written, not lowercased");
        assertTrue(html.contains("data-customer-username=\"ana.lopez\""),
                "the card shows the username");
        assertTrue(html.contains("data-customer-email=\"ana@example.com\""),
                "the card shows the email");
        assertTrue(html.contains("data-customer-phone=\"5512345678\""),
                "the card shows the phone");
        assertTrue(html.contains("data-customer-status=\"active\""), "the card knows the status");
        // Las fechas se guardan en UTC y se muestran en la zona del establecimiento (UTC-6).
        assertTrue(html.contains("data-customer-since=\"23/10/2026 09:00\""),
                "the card shows the registration date in the company timezone");
        assertTrue(html.contains("data-customer-last-access=\"23/10/2026 12:30\""),
                "the card shows the last access in the company timezone");
        assertTrue(html.contains("data-customer-last-access=\"Sin acceso\""),
                "a customer who never signed in says so, instead of a blank space");
        assertTrue(html.contains("data-customer-status=\"inactive\""),
                "an inactive customer must be published as inactive");
    }

    @Test
    void theCardCopiesTheBadgesAndTheRowActions() {
        String html = render(customers());

        assertTrue(html.contains("customer-status-badge"),
                "the card copies the server-side status badge");
        assertTrue(html.contains("customer-verified-badge"),
                "the card copies the verification badge");
        assertTrue(html.contains("customer-actions-cell"),
                "the card copies the actions of the row");
        assertTrue(html.contains("viewCustomer(7)"),
                "the row keeps its detail action, which the card copies");
        assertTrue(html.contains("toggleCustomerStatus(7, false)"),
                "the administrator keeps the deactivate action");
        assertFalse(html.contains("CUSTOMERS CARDS (Mobile/Tablet)"),
                "the old mobile-only cards must be gone: there is only one card implementation");
    }

    @Test
    void filtersRebuildTheCards() throws IOException {
        String template = Files.readString(TEMPLATE, StandardCharsets.UTF_8);

        assertTrue(template.contains("if (window.CustomersView) CustomersView.render();"),
                "the browser filters hide rows, so the cards must be rebuilt after filtering");
        assertTrue(template.contains("const rows = document.querySelectorAll(\".customer-row\");"),
                "one filter must keep walking the same rows");
        assertEquals(1, count(template, "class=\"customer-row "),
                "only the table rows carry the shared class: the cards are built from them");
    }

    /**
     * El contrato entre la plantilla y el JavaScript: cada atributo que el script lee de la fila
     * tiene que estar publicado, y las clases que copia también.
     */
    @Test
    void everyAttributeTheScriptReadsIsPublishedByTheTemplate() throws IOException {
        String template = Files.readString(TEMPLATE, StandardCharsets.UTF_8);
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        Matcher matcher = Pattern.compile("attr\\(row, \"([a-z-]+)\"\\)").matcher(script);
        Set<String> read = new LinkedHashSet<>();
        while (matcher.find()) {
            read.add(matcher.group(1));
        }

        assertFalse(read.isEmpty(), "the script must read the rows through data-customer-* attributes");
        for (String attribute : read) {
            assertTrue(template.contains("th:data-customer-" + attribute + "="),
                    "the template must publish data-customer-" + attribute
                            + " (the card reads it and would come out empty)");
        }

        assertTrue(script.contains(".customer-actions-cell")
                        && template.contains("customer-actions-cell"),
                "the actions cell the card copies must be marked in the row");
        assertTrue(script.contains(".customer-status-badge")
                        && template.contains("customer-status-badge"),
                "the status badge the card copies must be marked in the row");
        assertTrue(script.contains(".customer-verified-badge")
                        && template.contains("customer-verified-badge"),
                "the verification badge the card copies must be marked in the row");
        assertTrue(script.contains("customersCardsView") && script.contains("customersCardsEmpty"),
                "the script must know the containers the page renders");
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
}
