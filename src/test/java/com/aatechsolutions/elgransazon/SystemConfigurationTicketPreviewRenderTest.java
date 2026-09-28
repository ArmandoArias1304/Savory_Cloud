package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.DayOfWeek;
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
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.support.RequestContext;
import org.thymeleaf.context.AbstractContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.context.webmvc.SpringWebMvcThymeleafRequestContext;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.spring6.naming.SpringContextVariableNames;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The system-configuration screen carries a live preview of the printed ticket, so an
 * admin can see the header, the logo intensity and the footer legends without printing.
 * Processing the real template keeps the preview markup, the legend fields and the
 * script hooks in sync (it also catches Thymeleaf expression errors in the new block).
 */
@SpringBootTest
class SystemConfigurationTicketPreviewRenderTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private AbstractContext webContext(SystemConfiguration configuration) {
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webApplicationContext);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setContextPath("");
        // th:field needs the same binding the controller would publish with
        // @ModelAttribute("configuration"), otherwise Thymeleaf refuses to render it.
        request.setAttribute("org.springframework.validation.BindingResult.configuration",
                new BeanPropertyBindingResult(configuration, "configuration"));
        SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(securityContext);
        request.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        IWebExchange exchange = application.buildExchange(request, response);
        WebContext context = new WebContext(exchange);
        // @cloudinaryUrl / @licenseService are resolved while rendering this view.
        context.setVariable(
                ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                new ThymeleafEvaluationContext(webApplicationContext, new DefaultConversionService()));
        // This is what Spring MVC's ThymeleafView supplies so th:object / th:field work.
        context.setVariable(SpringContextVariableNames.THYMELEAF_REQUEST_CONTEXT,
                new SpringWebMvcThymeleafRequestContext(
                        new RequestContext(request, response), request));
        return context;
    }

    private SystemConfiguration configuration(String line1, String line2, String logoUrl, int opacity) {
        return SystemConfiguration.builder()
                .id(1L)
                .restaurantName("El Gran Sazón")
                .slogan("El mejor sabor de la ciudad")
                .address("Av. Juárez 123, Centro, Querétaro")
                .phone("4421234567")
                .email("hola@elgransazon.mx")
                .rfc("AAA010101AAA")
                .taxRate(new BigDecimal("16.00"))
                .ticketLogoOpacity(opacity)
                .ticketFooterLine1(line1)
                .ticketFooterLine2(line2)
                .restaurantLogoUrl(logoUrl)
                .defaultDeliveryCost(BigDecimal.ZERO)
                .build();
    }

    private void baseModel(AbstractContext ctx, SystemConfiguration configuration) {
        ctx.setVariable("globalSystemConfig",
                GlobalSystemConfig.builder().systemName("Test").systemLogoUrl(null).build());
        ctx.setVariable("configuration", configuration);
        ctx.setVariable("businessHoursMap", Map.of());
        ctx.setVariable("socialNetworks", List.of());
        ctx.setVariable("allDays", DayOfWeek.values());
        ctx.setVariable("license", null);
        ctx.setVariable("daysRemaining", 30);
        ctx.setVariable("totalRevenue", BigDecimal.ZERO);
        ctx.setVariable("renewalsWithAmount", List.of());
        ctx.setVariable("errorMessage", null);
        ctx.setVariable("successMessage", null);
        ctx.setVariable("activeMenu", "settings");
        ctx.setVariable("username", "admin");
    }

    private String render(SystemConfiguration configuration) {
        AbstractContext ctx = webContext(configuration);
        baseModel(ctx, configuration);
        String html = templateEngine.process("admin/system-configuration/form", ctx);
        assertNotNull(html);
        return html;
    }

    @Test
    void liveTicketPreviewIsRenderedInsideTheConfigurationForm() {
        String html = render(configuration("Pide tu factura aqui", "Conserva tu ticket",
                "https://cdn.example.com/logo.webp", 50));

        assertTrue(html.contains("id=\"ticketPreviewPanel\""), "the preview panel should render");
        assertTrue(html.contains("id=\"ticketPaper\""), "the paper mock should render");
        assertTrue(html.contains("id=\"ticketPreviewLines\""),
                "the script needs the container for the generated lines");
        assertTrue(html.contains("id=\"ticketPreviewLogo\""), "the logo mock should render");
        assertTrue(html.contains("id=\"tkModeQr\"") && html.contains("id=\"tkModePlain\""),
                "the QR / no QR switch should render");
        assertTrue(html.contains("data-system-name=\"Test\""),
                "the system branding line comes from the page model");
    }

    @Test
    void previewIsPartOfTheSameFormAsTheFieldsAndBeforeTheSaveButton() {
        String html = render(configuration(null, null, null, 50));

        int panel = html.indexOf("id=\"ticketPreviewPanel\"");
        int save = html.indexOf("id=\"btnSaveConfig\"");
        assertTrue(panel > 0 && save > panel,
                "the preview belongs to the configuration form, above the save button");
        assertTrue(html.contains("name=\"restaurantName\"") && html.contains("name=\"address\""),
                "wrapping the fields must not break the existing inputs");
    }

    @Test
    void logoIntensityIsAppliedToTheTicketPreview() {
        String html = render(configuration(null, null, "https://cdn.example.com/logo.webp", 75));

        assertTrue(html.contains("contrast(1.5)"),
                "75% intensity must darken the logo on the mock ticket");
    }

    @Test
    void configuredLegendsAreShownInTheEditableFields() {
        String html = render(configuration("Pide tu factura aqui", "Conserva tu ticket", null, 50));

        assertTrue(html.contains("id=\"ticketFooterLine1\"")
                        && html.contains("name=\"ticketFooterLine1\"")
                        && html.contains("value=\"Pide tu factura aqui\""),
                "the first legend must be editable and come prefilled");
        assertTrue(html.contains("id=\"ticketFooterLine2\"")
                        && html.contains("name=\"ticketFooterLine2\"")
                        && html.contains("value=\"Conserva tu ticket\""),
                "the second legend must be editable and come prefilled");
    }

    @Test
    void blankLegendsShowTheTextThatWillActuallyPrint() {
        String html = render(configuration(null, "  ", null, 50));

        // Thymeleaf escapes the opening inverted exclamation mark (&iexcl;) in the value.
        assertTrue(html.contains("value=\"&iexcl;Gracias por su preferencia!\""),
                "a blank legend must show the built-in text, because that is what prints: " + html);
        assertTrue(html.contains("value=\"" + SystemConfiguration.DEFAULT_TICKET_FOOTER_LINE_2 + "\""),
                "the second legend falls back to its built-in text too");
    }

    @Test
    void previewExplainsWhatItDoesNotPrint() {
        String html = render(configuration(null, null, null, 50));

        assertTrue(html.contains("no se imprime en el ticket"),
                "the preview should say the slogan is not printed");
        assertTrue(html.contains("Este no es un comprobante fiscal"),
                "the fixed fiscal disclaimer should be explained in the preview");
    }
}
