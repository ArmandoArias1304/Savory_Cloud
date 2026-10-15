package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.infrastructure.thymeleaf.TimezoneDialect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los botones "Volver a Pedidos" y "Volver al Listado" del detalle de un pedido tienen que llevar a
 * la lista de pedidos, y no a la pantalla de la que se venga.
 *
 * <p>Los dos botones usaban el origen de la petición: el atributo global {@code volverA}, que sale
 * del {@code Referer} del navegador. Cuando el mesero llegaba al detalle desde "Agregar Items al
 * Pedido", ese origen ERA la pantalla de agregar ítems, así que al darle a "Volver a Pedidos" no
 * volvía a la lista: entraba otra vez a agregar ítems, y de ahí al detalle, en círculo. Un botón
 * que dice "Volver a Pedidos" tiene que llevar a los pedidos.</p>
 *
 * <p>Aquí se renderizan las tres plantillas reales (mesero, cajero y admin/gerente) con el origen
 * puesto a propósito en la pantalla de agregar ítems, que es la situación que se reportó. La vista
 * del admin ya apuntaba a la lista: la prueba fija que las tres lo hagan igual.</p>
 */
class OrderViewBackToOrdersRenderTest {

    /** Cada vista con la lista que le corresponde. */
    private static final List<String[]> VIEWS = List.of(
            new String[]{"waiter", "/waiter/orders"},
            new String[]{"cashier", "/cashier/orders"},
            new String[]{"admin", "/admin/orders"});

    private TemplateEngine engine;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        // El mismo dialecto de la aplicación (SpEL + #tz), para evaluar la plantilla como en producción.
        engine.setDialect(new SpringStandardDialect());
        engine.addDialect(new TimezoneDialect());
    }

    @Test
    @DisplayName("Aunque se llegue desde \"Agregar Items\", volver lleva a la lista de pedidos")
    void theBackButtonsGoToTheOrderListEvenWhenComingFromAddItems() {
        for (String[] view : VIEWS) {
            String role = view[0];
            String list = view[1];
            String html = renderView(role, "/" + role + "/orders/110/add-items");

            for (String label : List.of("Volver a Pedidos", "Volver al Listado")) {
                String href = hrefOf(backLink(html, label, role));

                assertTrue(list.equals(href),
                        role + ": \"" + label + "\" lleva a la lista de pedidos, no a " + href);
            }
        }
    }

    @Test
    @DisplayName("Y sin origen (entrando directo por la URL) también llevan a la lista")
    void theBackButtonsGoToTheOrderListWithoutAnyOrigin() {
        for (String[] view : VIEWS) {
            String role = view[0];
            String html = renderView(role, null);

            for (String label : List.of("Volver a Pedidos", "Volver al Listado")) {
                assertTrue(view[1].equals(hrefOf(backLink(html, label, role))),
                        role + ": \"" + label + "\" lleva a la lista de pedidos");
            }
        }
    }

    /** El enlace que lleva ese rótulo, desde su {@code <a>} hasta su cierre. */
    private static String backLink(String html, String label, String role) {
        int labelIndex = html.indexOf(label);
        assertTrue(labelIndex > 0, role + ": la página ofrece el botón \"" + label + "\"");

        // El último enlace abierto antes del rótulo. Se busca con patrón porque la etiqueta puede
        // venir partida en varias líneas (atributos uno por línea), no como "<a ".
        int start = -1;
        Matcher tags = Pattern.compile("<a[\\s>]").matcher(html);
        while (tags.find() && tags.start() < labelIndex) {
            start = tags.start();
        }
        assertTrue(start >= 0, role + ": \"" + label + "\" es un enlace");

        int end = html.indexOf("</a>", labelIndex);
        assertTrue(end > start, role + ": el enlace \"" + label + "\" cierra bien");

        return html.substring(start, end);
    }

    private static String hrefOf(String link) {
        String needle = "href=\"";
        int start = link.indexOf(needle);
        assertTrue(start >= 0, "el enlace tiene href: " + link);

        int from = start + needle.length();
        return link.substring(from, link.indexOf('"', from));
    }

    /**
     * Renderiza el detalle con lo que le manda el controlador, más el origen de la petición tal
     * como lo pone el advice global ({@code volverA}) cuando el navegador manda un {@code Referer}.
     */
    private String renderView(String role, String origin) {
        Order order = new Order();
        order.setIdOrder(110L);
        order.setOrderNumber("ORD-20261015-110");
        order.setStatus(OrderStatus.READY);
        order.setOrderType(OrderType.DINE_IN);
        order.setEmployee(employee("meseroA", "Juan", "Pérez"));
        order.setCreatedBy("meseroA");

        SystemConfiguration config = new SystemConfiguration();
        config.setRestaurantName("El Gran Sazón");

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("globalSystemConfig",
                GlobalSystemConfig.builder().systemName("El Gran Sazón").build());
        variables.put("systemConfig", config);
        variables.put("config", config);
        variables.put("order", order);
        variables.put("orderDetails", order.getOrderDetails());
        variables.put("currentUsername", "meseroA");
        variables.put("currentRole", role);
        if (origin != null) {
            variables.put("volverA", origin);
        }

        JakartaServletWebApplication application = JakartaServletWebApplication
                .buildApplication(new MockServletContext());
        WebContext context = new WebContext(
                application.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse()));
        context.setVariables(variables);
        return engine.process(role + "/orders/view", context);
    }

    private static Employee employee(String username, String nombre, String apellido) {
        Employee employee = new Employee();
        employee.setUsername(username);
        employee.setNombre(nombre);
        employee.setApellido(apellido);
        employee.setEnabled(true);
        return employee;
    }
}
