package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.dto.MenuStyle;
import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
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
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Menu view: the "Carta en PDF" button opens a dialog that customizes the printed menu and
 * previews the real PDF. Rendering the actual template proves the dialog markup, the option
 * catalogs built from the enums and the default values of the saved style.
 */
@SpringBootTest
class MenuCartaDialogRenderTest {

    /** Marker unique to the carta script, used to check it runs after the DOM is ready. */
    private static final String SCRIPT_MARKER = "cartaGenerate";

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

    private String render(MenuStyle style) {
        AbstractContext ctx = webContext();
        ctx.setVariable("globalSystemConfig",
                GlobalSystemConfig.builder().systemName("Test").systemLogoUrl(null).build());
        ctx.setVariable("activeMenu", "menu-items");
        ctx.setVariable("username", "gerente");
        ctx.setVariable("companyZone", ZoneId.of("America/Mexico_City"));

        // The list itself is not what this test is about: the empty state renders fine.
        ctx.setVariable("menuItems", List.of());
        ctx.setVariable("categories", List.of());
        ctx.setVariable("currentPage", 0);
        ctx.setVariable("totalPages", 0);
        ctx.setVariable("totalElements", 0L);
        ctx.setVariable("pageSize", 20);
        ctx.setVariable("pageStart", 0L);
        ctx.setVariable("pageEnd", 0L);
        ctx.setVariable("pageNumbers", List.of());
        ctx.setVariable("totalCount", 0L);
        ctx.setVariable("activeCount", 0L);
        ctx.setVariable("availableCount", 0L);
        ctx.setVariable("unavailableCount", 0L);
        ctx.setVariable("nameFilter", "");
        ctx.setVariable("categoryFilter", "");
        ctx.setVariable("priceFilter", "");
        ctx.setVariable("availabilityFilter", "");
        ctx.setVariable("statusFilter", "");
        ctx.setVariable("hasFilters", false);

        ctx.setVariable("menuStyle", style);
        ctx.setVariable("menuFontOptions", MenuStyle.FontFamily.values());
        ctx.setVariable("menuPaperOptions", MenuStyle.PaperSize.values());
        ctx.setVariable("menuPageColorPresets", MenuStyle.pageColorPresets());
        ctx.setVariable("menuFooterText", MenuStyle.footerFor("Quinta El Paraíso"));

        String html = templateEngine.process("admin/menu-items/list", ctx);
        assertNotNull(html);
        return html;
    }

    @Test
    void offersTheCartaButtonAndTheDialog() {
        String html = render(MenuStyle.defaults());

        assertTrue(html.contains("id=\"btnCartaPdf\""), "falta el botón de la carta");
        assertTrue(html.contains("Carta en PDF"), "el botón no dice qué hace");
        assertTrue(html.contains("id=\"cartaModal\""), "falta el modal de personalización");
        assertTrue(html.contains("id=\"cartaPreview\""), "falta la vista previa de la carta");
        assertTrue(html.contains("id=\"cartaGenerate\""), "falta el botón de generar el PDF");
    }

    @Test
    void wiresTheEndpointsOfTheCarta() {
        String html = render(MenuStyle.defaults());

        assertTrue(html.contains("/admin/menu-items/menu-pdf"), "no se conectó el endpoint del PDF");
        assertTrue(html.contains("/admin/menu-items/menu-pdf/style"), "no se conectó el guardado del estilo");
    }

    @Test
    void listsEveryTypographyAndPaperOption() {
        String html = render(MenuStyle.defaults());

        for (MenuStyle.FontFamily family : MenuStyle.FontFamily.values()) {
            assertTrue(html.contains("value=\"" + family.name() + "\""),
                    "falta la tipografía " + family.name() + " en el selector");
            assertTrue(html.contains(family.getLabel()), "falta la etiqueta de " + family.name());
        }
        for (MenuStyle.PaperSize paper : MenuStyle.PaperSize.values()) {
            assertTrue(html.contains("value=\"" + paper.name() + "\""),
                    "falta el formato " + paper.name() + " en el selector");
        }
    }

    @Test
    void preselectsTheSavedStyle() {
        MenuStyle saved = new MenuStyle(MenuStyle.FontFamily.ELEGANTE, 14, "#8b1e3f", "#5b6472", "#fdf6e3",
                MenuStyle.PaperSize.HALF_LETTER, 2, false, true, false, true, true);

        String html = render(saved);

        assertTrue(Pattern.compile("value=\"ELEGANTE\"[^>]*selected").matcher(html).find(),
                "la tipografía guardada no viene seleccionada");
        assertTrue(Pattern.compile("value=\"HALF_LETTER\"[^>]*selected").matcher(html).find(),
                "el formato guardado no viene seleccionado");
        assertTrue(Pattern.compile("value=\"2\"[^>]*selected").matcher(html).find(),
                "las columnas guardadas no vienen seleccionadas");
        assertTrue(Pattern.compile("id=\"cartaFontSize\"[^>]*value=\"14\"").matcher(html).find(),
                "el tamaño guardado no viene en el control");
        assertTrue(html.contains("#8b1e3f") && html.contains("#5b6472"),
                "los colores guardados no vienen en los selectores");
        assertTrue(html.contains("#fdf6e3"), "el color de hoja guardado no viene en el selector");
        assertTrue(html.contains("Quinta El Paraíso by Savory Cloud"),
                "el pie no muestra el nombre del restaurante con la marca");
        assertTrue(Pattern.compile("id=\"cartaShowImages\"[^>]*checked").matcher(html).find(),
                "no se marcó la opción de fotos guardada");
        assertTrue(Pattern.compile("id=\"cartaShowQr\"[^>]*checked").matcher(html).find(),
                "no se marcó la opción del QR guardada");
    }

    @Test
    void defaultsMatchTheBuiltInDesign() {
        String html = render(MenuStyle.from(new SystemConfiguration()));

        assertTrue(Pattern.compile("value=\"MODERNA\"[^>]*selected").matcher(html).find(),
                "la tipografía por defecto no es la moderna");
        assertTrue(Pattern.compile("value=\"LETTER\"[^>]*selected").matcher(html).find(),
                "el formato por defecto no es carta");
        assertTrue(Pattern.compile("id=\"cartaShowDescriptions\"[^>]*checked").matcher(html).find(),
                "las descripciones deberían venir activadas");
        assertTrue(Pattern.compile("id=\"cartaShowPrices\"[^>]*checked").matcher(html).find(),
                "los precios deberían venir activados");
        assertTrue(html.contains("Savory Cloud"), "el pie por defecto no es la marca del sistema");
    }

    @Test
    void rebuildsThePreviewAsABlobAndReportsFailures() {
        String html = render(MenuStyle.defaults());

        assertTrue(html.contains("id=\"cartaPreviewMessage\""), "falta el aviso de la vista previa");
        assertTrue(html.contains("URL.createObjectURL"),
                "la vista previa no se refresca con el PDF recién generado");
        // Sin el token, al deslizar el tamaño de letra una respuesta vieja podía pintar la
        // carta anterior y parecía que el control no hacía nada.
        assertTrue(html.contains("previewToken"), "la vista previa no descarta las respuestas viejas");
        assertTrue(html.contains("URL.revokeObjectURL"), "no se liberan las vistas previas anteriores");
        assertTrue(html.contains("No se pudo generar la carta"),
                "un error de la carta no se le muestra al usuario");
        assertTrue(html.contains("addEventListener(\"input\""),
                "el tamaño de letra solo refresca al soltar el control");
    }

    /**
     * El pie ya no se edita (lo arma el sistema con el nombre del restaurante) y en su lugar el
     * diálogo ofrece el color de la hoja, con tonos claros predefinidos.
     */
    @Test
    void offersTheSheetColorAndShowsTheFooterAsReadOnly() {
        SystemConfiguration config = new SystemConfiguration();
        config.setRestaurantName("Quinta El Paraíso");

        String html = render(MenuStyle.from(config));

        assertTrue(html.contains("Color de la hoja"), "falta el control del color de la hoja");
        assertTrue(html.contains("id=\"cartaPageColor\""), "falta el selector del color de la hoja");
        for (MenuStyle.PageColorPreset preset : MenuStyle.pageColorPresets()) {
            assertTrue(html.contains("data-color=\"" + preset.color() + "\""),
                    "falta el tono de hoja " + preset.label());
        }

        assertTrue(html.contains("by Savory Cloud"),
                "el pie no muestra la marca del sistema");
        assertFalse(Pattern.compile("<input[^>]*id=\"cartaFooterText\"").matcher(html).find(),
                "el pie sigue siendo editable");
        assertFalse(html.contains("controls.footerText"),
                "el diálogo todavía manda el pie al servidor");
    }

    @Test
    void initializesTheDialogOnlyAfterTheDomIsReady() {
        String html = render(MenuStyle.defaults());

        // The dialog markup must exist before the script that looks up its elements, and the
        // script must wait for DOMContentLoaded: doing both is what kept the previous modal
        // from working at all.
        int modalIndex = html.indexOf("id=\"cartaModal\"");
        int scriptIndex = html.indexOf(SCRIPT_MARKER);
        assertTrue(modalIndex > 0 && scriptIndex > 0, "falta el modal o su script");
        assertTrue(modalIndex < scriptIndex, "el script de la carta va antes que el modal");
        assertTrue(html.contains("document.addEventListener(\"DOMContentLoaded\""),
                "el script de la carta no espera a que el DOM esté listo");
    }
}
