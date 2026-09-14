package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.domain.entity.TableStatus;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for the tables list: processing the real template catches Thymeleaf
 * expression errors in the new "delete table" action without a server, and the
 * delete button must carry the table id and number used by the confirmation dialog.
 */
@SpringBootTest
class TablesListRenderTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private WebApplicationContext webApplicationContext;

    /** A real web context so @{...} links inside the theme/sidebar fragments resolve. */
    private AbstractContext webContext() {
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webApplicationContext);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setContextPath("");
        SecurityContextImpl securityContext = new SecurityContextImpl();
        // NOTE: a non ADMIN/MANAGER role is used on purpose, same as
        // OrdersListDeliveryButtonRenderTest. The sidebar hides its ADMIN-only
        // links (which resolve @licenseService and need the real request
        // pipeline) before their th:if is evaluated, so the rest of the page can
        // be rendered standalone. The table rows are not role dependent.
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "ana", "n/a", List.of(new SimpleGrantedAuthority("ROLE_WAITER"))));
        SecurityContextHolder.setContext(securityContext);
        request.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        IWebExchange exchange = application.buildExchange(request, response);
        return new WebContext(exchange);
    }

    private void baseModel(AbstractContext ctx) {
        ctx.setVariable("globalSystemConfig",
                GlobalSystemConfig.builder().systemName("Test").systemLogoUrl(null).build());
        ctx.setVariable("tables", List.of(RestaurantTable.builder()
                .id(1L)
                .tableNumber(5)
                .capacity(4)
                .location("Terraza")
                .status(TableStatus.AVAILABLE)
                .build()));
        ctx.setVariable("locations", List.of("Terraza"));
        ctx.setVariable("allStatuses", TableStatus.values());
        ctx.setVariable("totalCount", 1L);
        ctx.setVariable("availableCount", 1L);
        ctx.setVariable("occupiedCount", 0L);
        ctx.setVariable("outOfServiceCount", 0L);
        ctx.setVariable("username", "ana");
    }

    @Test
    void deleteActionIsWiredWithTableIdAndNumber() {
        AbstractContext ctx = webContext();
        baseModel(ctx);

        String html = templateEngine.process("admin/tables/list", ctx);

        assertNotNull(html);
        assertTrue(html.contains("confirmDeleteTable(1, 5)"),
                "the delete button must call confirmDeleteTable with the table id and number");
        assertTrue(html.contains("title=\"Eliminar mesa\""),
                "the delete action must be available in the actions column");
        assertTrue(html.contains("/admin/tables/1/edit"),
                "the existing edit action must keep working");
        assertTrue(html.contains("onGridDeleteClick(event, 1, 5)"),
                "the grid card must offer the same delete action");
        assertTrue(
                html.contains("function onGridDeleteClick") && html.contains("event.stopPropagation()"),
                "the grid delete button must stop the click from opening the details modal");
    }

    @Test
    void deleteDialogAsksForConfirmationAndOffersInactivation() {
        AbstractContext ctx = webContext();
        baseModel(ctx);

        String html = templateEngine.process("admin/tables/list", ctx);

        assertTrue(html.contains("function confirmDeleteTable"),
                "deleting must always go through a confirmation dialog");
        assertTrue(html.contains("cancelButtonText: \"Cancelar\""),
                "the confirmation dialog must be cancellable");
        assertTrue(html.contains("function offerInactivateTable") && html.contains("Inactivar mesa"),
                "when the backend rejects the delete the user must be offered to inactivate");
        assertTrue(html.contains("/change-status") && html.contains("status=OUT_OF_SERVICE"),
                "inactivating must reuse the table status endpoint with OUT_OF_SERVICE");
    }
}
