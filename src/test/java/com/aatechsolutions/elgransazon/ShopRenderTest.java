package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.ShopProduct;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Processes the real shop templates with the application's Thymeleaf engine.
 *
 * <p>This catches syntax errors in the new pages and verifies that the
 * WhatsApp call to action only appears when a number is configured.</p>
 */
@SpringBootTest
class ShopRenderTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private AbstractContext webContext(String role) {
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webApplicationContext);

        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setContextPath("");

        SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "n/a", List.of(new SimpleGrantedAuthority(role))));
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

    private void baseVariables(AbstractContext ctx) {
        ctx.setVariable("globalSystemConfig",
                GlobalSystemConfig.builder().systemName("Test").build());
        ctx.setVariable("systemConfig", SystemConfiguration.builder()
                .restaurantName("El Gran Sazón")
                .build());
    }

    private ShopProduct product() {
        return ShopProduct.builder()
                .idProduct(1L)
                .name("Tarjeta QR para mesa")
                .description("Tarjeta impresa con código QR para el menú.")
                .price(new BigDecimal("250.00"))
                .category("Tarjetas")
                .imageUrl("https://images.example.com/qr.png")
                .active(true)
                .featured(true)
                .build();
    }

    @Test
    @DisplayName("el catálogo del restaurante muestra el producto y el botón de WhatsApp")
    void catalogueRendersWhatsAppCallToAction() {
        // ROLE_WAITER keeps the sidebar's ECOMMERCE link (a @licenseService bean call)
        // out of this manual render; the shop page itself has no role-specific markup.
        AbstractContext ctx = webContext("ROLE_WAITER");
        baseVariables(ctx);
        ShopProduct product = product();
        Map<Long, String> links = new LinkedHashMap<>();
        links.put(product.getIdProduct(), "https://wa.me/525512345678?text=Hola%20soy%20de%20El%20Gran%20Saz%C3%B3n");

        ctx.setVariable("products", List.of(product));
        ctx.setVariable("whatsappLinks", links);
        ctx.setVariable("whatsappConfigured", true);
        ctx.setVariable("restaurantName", "El Gran Sazón");
        ctx.setVariable("categories", List.of("Tarjetas"));

        String html = templateEngine.process("admin/shop/list", ctx);

        assertNotNull(html);
        assertTrue(html.contains("Tarjeta QR para mesa"));
        assertTrue(html.contains("$ 250.00"));
        assertTrue(html.contains("Pedir por WhatsApp"));
        assertTrue(html.contains("https://wa.me/525512345678"));
        assertTrue(html.contains("El Gran Sazón"));
        assertFalse(html.contains("La tienda todavía no tiene un WhatsApp"));
    }

    @Test
    @DisplayName("sin WhatsApp configurado el catálogo lo avisa y desactiva el botón")
    void catalogueWithoutWhatsappShowsTheNotice() {
        AbstractContext ctx = webContext("ROLE_WAITER");
        baseVariables(ctx);
        ShopProduct product = product();
        Map<Long, String> links = new LinkedHashMap<>();
        links.put(product.getIdProduct(), null);

        ctx.setVariable("products", List.of(product));
        ctx.setVariable("whatsappLinks", links);
        ctx.setVariable("whatsappConfigured", false);
        ctx.setVariable("restaurantName", "El Gran Sazón");
        ctx.setVariable("categories", List.of());

        String html = templateEngine.process("admin/shop/list", ctx);

        assertTrue(html.contains("La tienda todavía no tiene un WhatsApp"));
        assertTrue(html.contains("WhatsApp no disponible"));
        assertFalse(html.contains("Pedir por WhatsApp"));
    }

    @Test
    @DisplayName("el panel del programador lista productos y configura el WhatsApp")
    void programmerListRenders() {
        AbstractContext ctx = webContext("ROLE_PROGRAMMER");
        baseVariables(ctx);
        ctx.setVariable("products", List.of(product()));
        ctx.setVariable("categories", List.of("Tarjetas"));
        ctx.setVariable("whatsappNumber", "525512345678");
        ctx.setVariable("totalCount", 1L);
        ctx.setVariable("activeCount", 1L);

        String html = templateEngine.process("programmer/shop/list", ctx);

        assertTrue(html.contains("Tienda"));
        assertTrue(html.contains("Tarjeta QR para mesa"));
        assertTrue(html.contains("WhatsApp de ventas"));
        assertTrue(html.contains("Nuevo producto"));
        assertTrue(html.contains("/programmer/shop/1/edit"));
    }

}
