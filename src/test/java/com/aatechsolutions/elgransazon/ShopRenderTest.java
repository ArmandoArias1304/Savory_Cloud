package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.ShopBillingType;
import com.aatechsolutions.elgransazon.domain.entity.ShopPricingMode;
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
    @DisplayName("el catálogo tacha el precio normal y resalta el de promoción")
    void catalogueRendersPromotionalPrice() {
        AbstractContext ctx = webContext("ROLE_WAITER");
        baseVariables(ctx);
        ShopProduct product = product();
        product.setDiscountPrice(new BigDecimal("199.00"));
        Map<Long, String> links = new LinkedHashMap<>();
        links.put(product.getIdProduct(), "https://wa.me/525512345678?text=Hola");

        ctx.setVariable("products", List.of(product));
        ctx.setVariable("whatsappLinks", links);
        ctx.setVariable("whatsappConfigured", true);
        ctx.setVariable("restaurantName", "El Gran Sazón");
        ctx.setVariable("categories", List.of("Tarjetas"));

        String html = templateEngine.process("admin/shop/list", ctx);

        assertTrue(html.contains("$ 250.00"), "se conserva el precio original");
        assertTrue(html.contains("$ 199.00"), "se muestra el precio con descuento");
        assertTrue(html.contains("line-through"), "el precio original va tachado");
        assertTrue(html.contains("-20%"), "se anuncia el porcentaje de descuento");
        assertTrue(html.contains("Ahorras $ 51.00"));
        assertTrue(html.contains("/admin/shop/1"), "la tarjeta abre el detalle del producto");
        assertTrue(html.contains("Ver detalle"));
    }

    @Test
    @DisplayName("un producto mensual se anuncia por mes y con su distintivo de cobro")
    void catalogueMarksMonthlyProducts() {
        AbstractContext ctx = webContext("ROLE_WAITER");
        baseVariables(ctx);
        ShopProduct product = product();
        product.setPrice(new BigDecimal("150.00"));
        product.setBillingType(ShopBillingType.MONTHLY);
        Map<Long, String> links = new LinkedHashMap<>();
        links.put(product.getIdProduct(), "https://wa.me/525512345678?text=Hola");

        ctx.setVariable("products", List.of(product));
        ctx.setVariable("whatsappLinks", links);
        ctx.setVariable("whatsappConfigured", true);
        ctx.setVariable("restaurantName", "El Gran Sazón");
        ctx.setVariable("categories", List.of("Tarjetas"));

        String html = templateEngine.process("admin/shop/list", ctx);

        assertTrue(html.contains("$ 150.00 / mes"), "el precio mensual se anuncia por mes");
        assertTrue(html.contains("Mensual"), "la tarjeta distingue el cobro mensual");
        assertTrue(html.contains("Se suma a la mensualidad del restaurante."),
                "la tarjeta explica que se suma a la mensualidad");
        assertFalse(html.contains("$ 150.00 </p>"), "nunca se muestra como si fuera un pago único");
    }

    @Test
    @DisplayName("un rango se anuncia con mínimo y máximo, sin inventar un precio")
    void catalogueShowsThePriceRange() {
        AbstractContext ctx = webContext("ROLE_WAITER");
        baseVariables(ctx);
        ShopProduct product = product();
        product.setPricingMode(ShopPricingMode.RANGE);
        product.setPrice(new BigDecimal("200.00"));
        product.setMaxPrice(new BigDecimal("500.00"));
        product.setPriceNote("Depende de la distancia");
        Map<Long, String> links = new LinkedHashMap<>();
        links.put(product.getIdProduct(), "https://wa.me/525512345678?text=Hola");

        ctx.setVariable("products", List.of(product));
        ctx.setVariable("whatsappLinks", links);
        ctx.setVariable("whatsappConfigured", true);
        ctx.setVariable("restaurantName", "El Gran Sazón");
        ctx.setVariable("categories", List.of("Tarjetas"));

        String html = templateEngine.process("admin/shop/list", ctx);

        assertTrue(html.contains("$ 200.00 – $ 500.00"), "se publica el rango completo");
        assertTrue(html.contains("Depende de la distancia"), "la nota explica de qué depende");
        assertFalse(html.contains("$ 200.00 </p>"), "no se anuncia solo el mínimo");
    }

    @Test
    @DisplayName("un producto a evaluar se muestra sin monto")
    void catalogueShowsQuoteProductsWithoutAmount() {
        AbstractContext ctx = webContext("ROLE_WAITER");
        baseVariables(ctx);
        ShopProduct product = product();
        product.setPricingMode(ShopPricingMode.QUOTE);
        product.setPrice(BigDecimal.ZERO);
        product.setPriceNote("Se cotiza según el número de empleados");
        Map<Long, String> links = new LinkedHashMap<>();
        links.put(product.getIdProduct(), "https://wa.me/525512345678?text=Hola");

        ctx.setVariable("products", List.of(product));
        ctx.setVariable("whatsappLinks", links);
        ctx.setVariable("whatsappConfigured", true);
        ctx.setVariable("restaurantName", "El Gran Sazón");
        ctx.setVariable("categories", List.of());

        String html = templateEngine.process("admin/shop/list", ctx);

        assertTrue(html.contains("Precio a evaluar"));
        assertTrue(html.contains("Se cotiza según el número de empleados"));
        assertFalse(html.contains("$ 0.00"), "un precio a evaluar nunca muestra cero");
    }

    @Test
    @DisplayName("el detalle de un producto a evaluar explica la cotización")
    void productDetailExplainsTheQuote() {
        AbstractContext ctx = webContext("ROLE_WAITER");
        baseVariables(ctx);
        ShopProduct product = product();
        product.setPricingMode(ShopPricingMode.QUOTE);
        product.setPrice(BigDecimal.ZERO);
        product.setPriceNote("Depende de la distancia y del número de empleados");

        ctx.setVariable("product", product);
        ctx.setVariable("whatsappLink", "https://wa.me/525512345678?text=Hola");
        ctx.setVariable("whatsappConfigured", true);
        ctx.setVariable("restaurantName", "El Gran Sazón");

        String html = templateEngine.process("admin/shop/detail", ctx);

        assertTrue(html.contains("Precio a evaluar"));
        assertTrue(html.contains("Depende de la distancia y del número de empleados"));
        assertTrue(html.contains("Se cobra una sola vez, al pedirlo."),
                "la forma de cobro sigue visible");
        assertFalse(html.contains("$ 0.00"));
    }

    @Test
    @DisplayName("el programador ve qué productos se cotizan y cuáles tienen rango")
    void programmerListShowsRangeAndQuotePrices() {
        AbstractContext ctx = webContext("ROLE_PROGRAMMER");
        baseVariables(ctx);
        ShopProduct range = product();
        range.setPricingMode(ShopPricingMode.RANGE);
        range.setPrice(new BigDecimal("200.00"));
        range.setMaxPrice(new BigDecimal("500.00"));
        ShopProduct quote = product();
        quote.setIdProduct(2L);
        quote.setName("Capacitación a domicilio");
        quote.setPricingMode(ShopPricingMode.QUOTE);
        quote.setPrice(BigDecimal.ZERO);

        ctx.setVariable("products", List.of(range, quote));
        ctx.setVariable("categories", List.of("Tarjetas"));
        ctx.setVariable("whatsappNumber", "525512345678");
        ctx.setVariable("totalCount", 2L);
        ctx.setVariable("activeCount", 2L);

        String html = templateEngine.process("programmer/shop/list", ctx);

        assertTrue(html.contains("$ 200.00 – $ 500.00"));
        assertTrue(html.contains("Precio a evaluar"));
        assertFalse(html.contains("$ 0.00"));
    }

    @Test
    @DisplayName("el detalle de un producto mensual aclara el cobro")
    void productDetailExplainsTheMonthlyCharge() {
        AbstractContext ctx = webContext("ROLE_WAITER");
        baseVariables(ctx);
        ShopProduct product = product();
        product.setBillingType(ShopBillingType.MONTHLY);

        ctx.setVariable("product", product);
        ctx.setVariable("whatsappLink", "https://wa.me/525512345678?text=Hola");
        ctx.setVariable("whatsappConfigured", true);
        ctx.setVariable("restaurantName", "El Gran Sazón");

        String html = templateEngine.process("admin/shop/detail", ctx);

        assertTrue(html.contains("$ 250.00 / mes"));
        assertTrue(html.contains("Cobro mensual"));
        assertTrue(html.contains("Se suma a la mensualidad del restaurante."));
        assertFalse(html.contains("Se cobra una sola vez, al pedirlo."));
    }

    @Test
    @DisplayName("el programador distingue sus productos mensuales de los de pago único")
    void programmerListShowsTheBillingType() {
        AbstractContext ctx = webContext("ROLE_PROGRAMMER");
        baseVariables(ctx);
        ShopProduct monthly = product();
        monthly.setBillingType(ShopBillingType.MONTHLY);
        ShopProduct oneTime = product();
        oneTime.setIdProduct(2L);
        oneTime.setName("Tarjeta QR impresa");

        ctx.setVariable("products", List.of(monthly, oneTime));
        ctx.setVariable("categories", List.of("Tarjetas"));
        ctx.setVariable("whatsappNumber", "525512345678");
        ctx.setVariable("totalCount", 2L);
        ctx.setVariable("activeCount", 2L);

        String html = templateEngine.process("programmer/shop/list", ctx);

        assertTrue(html.contains("$ 250.00 / mes"), "el precio del mensual se marca por mes");
        assertTrue(html.contains("Mensual"));
        assertTrue(html.contains("Pago único"));
        assertTrue(html.contains("Se cobra una sola vez, al pedirlo."));
    }

    @Test
    @DisplayName("el detalle muestra foto grande, descripción completa y promoción")
    void productDetailRendersEverything() {
        AbstractContext ctx = webContext("ROLE_WAITER");
        baseVariables(ctx);
        ShopProduct product = product();
        product.setDescription("Tarjeta impresa con código QR.\nIncluye base de acrílico y envío.");
        product.setDiscountPrice(new BigDecimal("199.00"));

        ctx.setVariable("product", product);
        ctx.setVariable("whatsappLink", "https://wa.me/525512345678?text=Hola");
        ctx.setVariable("whatsappConfigured", true);
        ctx.setVariable("restaurantName", "El Gran Sazón");

        String html = templateEngine.process("admin/shop/detail", ctx);

        assertTrue(html.contains("https://images.example.com/qr.png"));
        assertTrue(html.contains("Tarjeta QR para mesa"));
        assertTrue(html.contains("Incluye base de acrílico y envío."));
        assertTrue(html.contains("$ 250.00"));
        assertTrue(html.contains("$ 199.00"));
        assertTrue(html.contains("line-through"));
        assertTrue(html.contains("-20% de descuento"));
        assertTrue(html.contains("Ahorras $ 51.00"));
        assertTrue(html.contains("Pedir por WhatsApp"));
        assertTrue(html.contains("Volver a la tienda"));
    }

    @Test
    @DisplayName("sin descuento el detalle no inventa una promoción")
    void productDetailWithoutDiscount() {
        AbstractContext ctx = webContext("ROLE_WAITER");
        baseVariables(ctx);

        ctx.setVariable("product", product());
        ctx.setVariable("whatsappLink", null);
        ctx.setVariable("whatsappConfigured", false);
        ctx.setVariable("restaurantName", "El Gran Sazón");

        String html = templateEngine.process("admin/shop/detail", ctx);

        assertTrue(html.contains("$ 250.00"));
        assertFalse(html.contains("de descuento"));
        assertFalse(html.contains("Ahorras"));
        assertTrue(html.contains("Pago único"));
        assertTrue(html.contains("Se cobra una sola vez, al pedirlo."));
        assertTrue(html.contains("WhatsApp no disponible"));
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
