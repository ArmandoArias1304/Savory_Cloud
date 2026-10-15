package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las cinco pantallas que enseñan la carta —el menú público del local y los menús
 * visuales de mesero, cajero, cocina y reparto— muestran un recuadro por platillo y
 * sus tamaños dentro del diálogo. Antes ninguna sabía de los tamaños: se veía el
 * item padre y no había forma de llegar a sus variantes.
 *
 * <p>Se renderizan las plantillas reales con el motor de verdad, para que lo que se
 * fije sea lo que el navegador recibe, y se comprueba que cada una incluya la pieza
 * compartida donde vive el diálogo. Las cinco tienen que decir lo mismo: si una se
 * queda atrás, aparece aquí.</p>
 */
@SpringBootTest
class MenuVariantsViewTest {

    /** Las cinco pantallas de carta que comparten el reparto de tamaños. */
    private static final List<String> MENUS = List.of(
            "home/menu/view",
            "waiter/menu/view",
            "cashier/menu/view",
            "chef/menu/view",
            "delivery/menu/view");

    private static final Path DIALOGO =
            Path.of("src/main/resources/static/js/menu-item-dialog.js");

    private static final Pattern MAPPING = Pattern.compile(
            "const sizeItemsMapping = ([\\s\\S]*?);");

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "home/menu/view",
        "waiter/menu/view",
        "cashier/menu/view",
        "chef/menu/view",
        "delivery/menu/view",
    })
    @DisplayName("el grid muestra un recuadro por platillo: los tamaños no son tarjetas aparte")
    void theGridShowsOneCardPerDish(String menu) {
        String html = render(menu);

        assertEquals(
                2,
                occurrences(html, "class=\"menu-item-card flex flex-col"),
                "una tarjeta para la pizza y otra para el refresco, ninguna para sus tamaños");
        assertTrue(html.contains("data-item-id=\"1\""), "la pizza se muestra");
        assertTrue(html.contains("data-item-id=\"9\""), "el refresco se muestra");
        assertFalse(
                html.contains("data-item-id=\"2\"") || html.contains("data-item-id=\"3\""),
                "los tamaños no se dibujan como recuadros sueltos");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "home/menu/view",
        "waiter/menu/view",
        "cashier/menu/view",
        "chef/menu/view",
        "delivery/menu/view",
    })
    @DisplayName("la tarjeta avisa de que el platillo tiene varios tamaños")
    void theCardAnnouncesItsSizes(String menu) {
        String html = render(menu);

        assertTrue(html.contains("data-has-sizes=\"true\""), "la pizza los tiene");
        assertTrue(html.contains("data-has-sizes=\"false\""), "el refresco no");
        assertTrue(html.contains("Tamaños"), "la tarjeta lo dice en vez de un 'Ver' a secas");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "home/menu/view",
        "waiter/menu/view",
        "cashier/menu/view",
        "chef/menu/view",
        "delivery/menu/view",
    })
    @DisplayName("el servidor le manda al diálogo los tamaños del item, ordenados por precio")
    void theDialogReceivesTheSizes(String menu) {
        String html = render(menu);

        Matcher matcher = MAPPING.matcher(html);
        assertTrue(matcher.find(), "la plantilla debe declarar el mapa de tamaños");
        String mapping = matcher.group(1);

        assertTrue(mapping.contains("Mediana"), "el tamaño mediano viaja al JavaScript");
        assertTrue(mapping.contains("Grande"), "y el grande también");
        assertTrue(
                mapping.indexOf("Mediana") < mapping.indexOf("Grande"),
                "los tamaños van de la presentación más barata a la más cara");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "home/menu/view",
        "waiter/menu/view",
        "cashier/menu/view",
        "chef/menu/view",
        "delivery/menu/view",
    })
    @DisplayName("la plantilla usa la pieza compartida y su búsqueda mira los tamaños")
    void theTemplateUsesTheSharedDialog(String menu) {
        String html = render(menu);

        assertTrue(
                html.contains("/js/menu-item-dialog.js"),
                menu + " debe incluir el diálogo compartido");
        assertTrue(
                html.contains("menuItemSearchText(card)"),
                menu + " busca por nombre y por tamaños, no sólo por nombre");
        assertFalse(
                html.contains("function openItemModal"),
                menu + " no debe volver a tener su propia copia del diálogo");
    }

    @Test
    @DisplayName("el diálogo compartido trae el selector de tamaños: flechas y atajos directos")
    void theSharedDialogBringsTheVariantNavigator() throws Exception {
        String js = Files.readString(DIALOGO);

        assertTrue(js.contains("window.openItemModal"), "las tarjetas lo llaman por su nombre");
        assertTrue(js.contains("data-variant-step=\"-1\""), "hay flecha para el anterior");
        assertTrue(js.contains("data-variant-step=\"1\""), "y para el siguiente");
        assertTrue(js.contains("data-variant-index"), "y un atajo por tamaño");
        assertTrue(
                js.contains("onclick=\"changePresentation(") && js.contains("onclick=\"showPresentation("),
                "los botones responden desde su primer clic, sin esperar a un listener");
        assertTrue(js.contains("menuItemSearchText"), "expone la búsqueda por tamaño");
    }

    // ---------- datos de prueba ----------

    /** Una categoría con un platillo de dos tamaños y otro suelto. */
    private Map<Long, List<ItemMenu>> menu() {
        Category pizzas = Category.builder().idCategory(1L).name("Pizzas").icon("local_pizza").build();
        Category drinks = Category.builder().idCategory(2L).name("Bebidas").icon("local_drink").build();

        ItemMenu pizza = item(1L, "Pizza", "149.00", pizzas);
        ItemMenu medium = item(3L, "Pizza mediana", "199.00", pizzas);
        medium.setSizeName("Mediana");
        medium.setParentItem(pizza);
        ItemMenu large = item(2L, "Pizza grande", "249.00", pizzas);
        large.setSizeName("Grande");
        large.setParentItem(pizza);
        ItemMenu soda = item(9L, "Refresco", "35.00", drinks);

        Map<Long, List<ItemMenu>> byCategory = new LinkedHashMap<>();
        byCategory.put(pizzas.getIdCategory(), new ArrayList<>(List.of(pizza)));
        byCategory.put(drinks.getIdCategory(), new ArrayList<>(List.of(soda)));
        return byCategory;
    }

    private ItemMenu item(Long id, String name, String price, Category category) {
        return ItemMenu.builder()
                .idItemMenu(id)
                .name(name)
                .description("Descripción de " + name)
                .price(new BigDecimal(price))
                .available(true)
                .category(category)
                .build();
    }

    /** El mapa de tamaños tal como lo arma el controlador: padre → hijos por precio. */
    private Map<Long, List<Map<String, Object>>> sizes() {
        Map<Long, List<Map<String, Object>>> sizes = new LinkedHashMap<>();
        sizes.put(1L, new ArrayList<>(List.of(
                size(3L, "Pizza mediana", "Mediana", "199.00"),
                size(2L, "Pizza grande", "Grande", "249.00"))));
        return sizes;
    }

    private Map<String, Object> size(Long id, String name, String sizeName, String price) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", id);
        dto.put("name", name);
        dto.put("sizeName", sizeName);
        dto.put("price", new BigDecimal(price));
        dto.put("available", true);
        dto.put("imageUrl", "");
        dto.put("description", "Descripción de " + name);
        return dto;
    }

    private String render(String menu) {
        Map<Long, List<ItemMenu>> itemsByCategory = menu();
        AbstractContext ctx = webContext();
        SystemConfiguration config = SystemConfiguration.builder()
                .restaurantName("El Gran Sazon")
                .taxRate(new BigDecimal("16.00"))
                .build();

        List<Category> categories = itemsByCategory.values().stream()
                .flatMap(List::stream)
                .map(ItemMenu::getCategory)
                .distinct()
                .collect(Collectors.toList());

        ctx.setVariable("globalSystemConfig", GlobalSystemConfig.builder().systemName("Test").build());
        ctx.setVariable("config", config);
        // La cabecera del menú público usa el nombre del restaurante.
        ctx.setVariable("systemConfig", config);
        ctx.setVariable("categories", categories);
        ctx.setVariable("itemsByCategory", itemsByCategory);
        ctx.setVariable("sizeItemsDto", sizes());
        // La cabecera de cocina enlaza a su tablero con el rol en la URL.
        ctx.setVariable("rolePrefix", "chef");
        ctx.setVariable("currentRole", "chef");
        ctx.setVariable("currentRoleLabel", "Cocinero");
        ctx.setVariable("isBarista", false);
        ctx.setVariable("isParrillero", false);

        String html = templateEngine.process(menu, ctx);
        assertNotNull(html);
        return html;
    }

    private AbstractContext webContext() {
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
        IWebExchange exchange = JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(request, response);
        WebContext context = new WebContext(exchange);
        // Las tarjetas usan @cloudinaryUrl para la imagen del platillo.
        context.setVariable(
                ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                new ThymeleafEvaluationContext(webApplicationContext, new DefaultConversionService()));
        return context;
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
