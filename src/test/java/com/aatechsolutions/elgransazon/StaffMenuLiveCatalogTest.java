package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.repository.CompanyRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.presentation.controller.CashierController;
import com.aatechsolutions.elgransazon.presentation.controller.ChefController;
import com.aatechsolutions.elgransazon.presentation.controller.DeliveryController;
import com.aatechsolutions.elgransazon.presentation.controller.GlobalControllerAdvice;
import com.aatechsolutions.elgransazon.presentation.controller.WaiterController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.thymeleaf.context.AbstractContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las cartas del personal —cajero, cocina (chef, barista y parrillero), mesero y reparto—
 * contra la carta REAL de la empresa: los controladores de verdad, los servicios de verdad y
 * el catálogo que hay en la base, no datos de ejemplo.
 *
 * <p>Las pruebas del reparto ({@link com.aatechsolutions.elgransazon.presentation.controller.StaffMenuVariantsModelTest})
 * comprueban con datos fabricados que cada controlador pide los tamaños; la vista
 * ({@link MenuVariantsViewTest}) comprueba que la plantilla sabe pintarlos. Aquí se ata
 * todo: si el catálogo real devuelve un platillo con tamaños y la pantalla no lo anuncia o
 * el diálogo no los recibe, falla aquí. Se renderizan las plantillas de verdad, así que lo
 * que se revisa es el HTML que el navegador recibe.</p>
 *
 * <p>Es además la prueba que responde al «no se ven los tamaños en la carta del personal»:
 * recorre las seis pantallas con la carta que ya está cargada y dice cuántos tamaños hay
 * en el grid y cuántos platillos los ofrecen.</p>
 */
@SpringBootTest
@Transactional
class StaffMenuLiveCatalogTest {

    /** Quinta El Paraíso: la empresa con la que se probó a mano y la única con tamaños hoy. */
    private static final long COMPANY_ID = 2L;

    private static final Pattern MAPPING = Pattern.compile(
            "const sizeItemsMapping = ([\\s\\S]*?);");

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ItemMenuRepository itemMenuRepository;

    @Autowired
    private CashierController cashierController;

    @Autowired
    private ChefController chefController;

    @Autowired
    private WaiterController waiterController;

    @Autowired
    private DeliveryController deliveryController;

    @Autowired
    private GlobalControllerAdvice advice;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private Company company;

    @BeforeEach
    void entrarComoEmpresa() {
        company = companyRepository.findById(COMPANY_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Empresa " + COMPANY_ID + " no encontrada: esta prueba corre contra la base real"));
        CompanyContext.setCurrentCompany(company);
    }

    @AfterEach
    void salir() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("la carta real tiene platillos con tamaños: sin ellos esta prueba no verificaría nada")
    void laCartaRealTienePlatillosConTamanos() {
        List<ItemMenu> tamanos = tamanosDelCatalogo();

        System.out.printf(
                "%n[carta en vivo] empresa %d (%s): %d platillos con tamaños, %d tamaños en total%n",
                company.getIdCompany(), company.getName(), platillosConTamanos().size(), tamanos.size());

        assertFalse(
                tamanos.isEmpty(),
                "La empresa " + COMPANY_ID + " no tiene platillos con tamaños en item_menu (parent_item_id). "
                        + "Si eso cambió a propósito, ajusta COMPANY_ID o el escenario de esta prueba: "
                        + "el reparto con datos de ejemplo está en StaffMenuVariantsModelTest.");
        assertFalse(
                platillosConTamanos().isEmpty(),
                "Hay tamaños sueltos que no cuelgan de ningún platillo activo de la empresa");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"cashier", "chef", "barista", "parrillero", "waiter", "delivery"})
    @DisplayName("la carta del personal anuncia los tamaños y le manda sus variantes al diálogo")
    void laCartaDelPersonalOfreceLasVariantes(String role) {
        Carta carta = render(role);

        Map<Long, List<Map<String, Object>>> tamanos = tamanos(carta);
        Set<Long> dibujados = dibujados(carta);
        Set<Long> conTamanos = tamanos.keySet().stream()
                .filter(dibujados::contains)
                .collect(Collectors.toSet());

        assertTrue(
                carta.html().contains("/js/menu-item-dialog.js"),
                carta.view() + ": debe incluir el diálogo compartido donde viven los tamaños");
        assertFalse(
                conTamanos.isEmpty(),
                carta.view() + ": ningún platillo del grid tiene tamaños, así que la carta no los ofrece");
        assertEquals(
                conTamanos.size(),
                occurrences(carta.html(), ">Tamaños<"),
                carta.view() + ": cada platillo con tamaños lo dice en su tarjeta, y sólo esos");

        for (ItemMenu hijo : tamanosDelCatalogo()) {
            assertFalse(
                    dibujados.contains(hijo.getIdItemMenu()),
                    carta.view() + ": el tamaño «" + hijo.getName()
                            + "» no debe ser una tarjeta suelta; se ve dentro de su platillo");
        }

        String mapping = mappingOf(carta);
        for (Long platillo : conTamanos) {
            for (Map<String, Object> dto : tamanos.get(platillo)) {
                assertTrue(
                        mapping.contains(String.valueOf(dto.get("sizeName"))),
                        carta.view() + ": el tamaño «" + dto.get("sizeName")
                                + "» del platillo " + platillo + " no llega al diálogo");
            }
        }
    }

    // ---------- render ----------

    /** La carta que ve un rol concreto: controlador real, catálogo real y plantilla real. */
    private Carta render(String role) {
        Authentication usuario = auth(switch (role) {
            case "cashier" -> "ROLE_CASHIER";
            case "waiter" -> "ROLE_WAITER";
            case "delivery" -> "ROLE_DELIVERY";
            default -> "ROLE_" + role.toUpperCase();
        }, role);
        // La carta del cajero está protegida con @PreAuthorize, así que la sesión tiene que estar
        // puesta antes de llamar al controlador, igual que en una petición de verdad.
        SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(usuario);
        SecurityContextHolder.setContext(securityContext);

        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        String view;
        switch (role) {
            case "cashier" -> view = cashierController.viewMenu(model, flash);
            case "waiter" -> view = waiterController.viewMenu(model, flash);
            case "delivery" -> view = deliveryController.viewMenu(usuario, model, flash);
            default -> {
                // Chef, barista y parrillero comparten la pantalla de cocina: sólo cambia el
                // prefijo de la URL con el que el enlace de vuelta encuentra su tablero.
                chefController.addRolePrefix(role, model);
                view = chefController.viewMenu(role, usuario, model, flash);
            }
        }
        assertTrue(
                view.endsWith("/menu/view"),
                "la carta de " + role + " debe ser su plantilla, no " + view
                        + " (un redirect aquí significa que el controlador cayó en su catch de error)");

        // Los dos atributos globales los pone GlobalControllerAdvice en cada petición.
        Map<String, Object> variables = new LinkedHashMap<>(model);
        variables.put("globalSystemConfig", advice.addGlobalSystemConfiguration());
        variables.put("systemConfig", advice.addSystemConfiguration());

        return new Carta(role, view, variables, templateEngine.process(view, context(variables, securityContext)));
    }

    private Authentication auth(String authority, String username) {
        return new UsernamePasswordAuthenticationToken(
                username, "n/a", List.of(new SimpleGrantedAuthority(authority)));
    }

    private AbstractContext context(Map<String, Object> variables, SecurityContextImpl securityContext) {
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webApplicationContext);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setContextPath("");
        request.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        IWebExchange exchange = JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(request, new MockHttpServletResponse());
        WebContext context = new WebContext(exchange);
        variables.forEach(context::setVariable);
        // Las tarjetas usan @cloudinaryUrl para la imagen del platillo.
        context.setVariable(
                ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                new ThymeleafEvaluationContext(webApplicationContext, new DefaultConversionService()));
        return context;
    }

    // ---------- datos reales ----------

    /** Los items activos de la empresa tal como los ve el menú (padres y tamaños). */
    private List<ItemMenu> catalogo() {
        return itemMenuRepository.findByActiveTrueAndCompany(company);
    }

    private List<ItemMenu> tamanosDelCatalogo() {
        return catalogo().stream().filter(item -> item.getParentItem() != null).toList();
    }

    private Set<Long> platillosConTamanos() {
        return tamanosDelCatalogo().stream()
                .map(item -> item.getParentItem().getIdItemMenu())
                .collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    private Map<Long, List<Map<String, Object>>> tamanos(Carta carta) {
        Object sizes = carta.variables().get("sizeItemsDto");
        assertTrue(sizes instanceof Map, carta.view() + ": el controlador debe mandar sizeItemsDto");
        return (Map<Long, List<Map<String, Object>>>) sizes;
    }

    @SuppressWarnings("unchecked")
    private Set<Long> dibujados(Carta carta) {
        Object byCategory = carta.variables().get("itemsByCategory");
        assertTrue(
                byCategory instanceof Map,
                carta.view() + ": el controlador debe mandar los platillos por categoría");
        return ((Map<Long, List<ItemMenu>>) byCategory).values().stream()
                .flatMap(List::stream)
                .map(ItemMenu::getIdItemMenu)
                .collect(Collectors.toSet());
    }

    private String mappingOf(Carta carta) {
        Matcher matcher = MAPPING.matcher(carta.html());
        assertTrue(matcher.find(), carta.view() + ": la plantilla debe escribir el mapa de tamaños");
        return matcher.group(1);
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

    /** Una carta renderizada: de dónde salió, con qué datos y el HTML que recibiría el navegador. */
    private record Carta(
            String role,
            String view,
            Map<String, Object> variables,
            String html) {
    }
}
