package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Category;
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

import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Categories view: the rows can be dragged (or moved with the arrows) to set the order the
 * printed menu follows, and the table shows that order.
 */
@SpringBootTest
class CategoriesReorderRenderTest {

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
                "gerente", "n/a", List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
        SecurityContextHolder.setContext(securityContext);
        request.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        IWebExchange exchange = application.buildExchange(request, response);
        WebContext context = new WebContext(exchange);
        context.setVariable(
                ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                new ThymeleafEvaluationContext(webApplicationContext, new DefaultConversionService()));
        return context;
    }

    private String render(List<Category> categories) {
        AbstractContext ctx = webContext();
        ctx.setVariable("globalSystemConfig",
                GlobalSystemConfig.builder().systemName("Test").systemLogoUrl(null).build());
        ctx.setVariable("activeMenu", "categories");
        ctx.setVariable("username", "gerente");
        ctx.setVariable("companyZone", ZoneId.of("America/Mexico_City"));
        ctx.setVariable("categories", categories);
        ctx.setVariable("activeCount", (long) categories.size());

        String html = templateEngine.process("admin/categories/list", ctx);
        assertNotNull(html);
        return html;
    }

    @Test
    void everyRowCanBeDraggedIntoTheMenuOrder() {
        String html = render(List.of(category(3L, "Entradas", 1), category(5L, "Postres", 2)));

        assertTrue(html.contains(">Orden<") || html.contains(">\n                      Orden"),
                "falta el encabezado de la columna de orden");
        assertTrue(html.contains("draggable=\"true\""), "las filas no se pueden arrastrar");
        assertTrue(html.contains("data-category-id=\"3\"") && html.contains("data-category-id=\"5\""),
                "las filas no llevan el id de la categoría");
        assertTrue(html.contains("/admin/categories/reorder"), "no se conectó el endpoint del orden");
    }

    @Test
    void showsThePositionAndTheArrowButtons() {
        String html = render(List.of(category(3L, "Entradas", 1), category(5L, "Postres", 2)));

        assertTrue(html.contains("category-position"), "no se muestra la posición en el menú");
        assertTrue(html.contains("data-move=\"up\""), "falta el botón de subir");
        assertTrue(html.contains("data-move=\"down\""), "falta el botón de bajar");
        assertTrue(html.contains("arrow_upward") && html.contains("arrow_downward"),
                "los botones de mover no usan sus flechas");
    }

    @Test
    void theEmptyStateSpansTheNewColumn() {
        String html = render(List.of());

        assertTrue(html.contains("colspan=\"7\""),
                "el estado vacío no cubre la columna nueva, la tabla quedaría desalineada");
    }

    private Category category(Long id, String name, Integer displayOrder) {
        return Category.builder()
                .idCategory(id)
                .name(name)
                .description(name + " de la casa")
                .icon("restaurant")
                .active(true)
                .displayOrder(displayOrder)
                .build();
    }
}
