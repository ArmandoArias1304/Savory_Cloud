package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;
import org.thymeleaf.context.AbstractContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RegisteredCustomerLookupRenderTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private AbstractContext webContext() {
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE,
                webApplicationContext);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        IWebExchange exchange = JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(request, response);
        return new WebContext(exchange);
    }

    @Test
    void rendersTheOptionalCustomerLookupWithAValidatedRolePath() {
        AbstractContext context = webContext();
        context.setVariable("rolePath", "cashier");
        context.setVariable("loyaltyEnabled", true);
        String html = templateEngine.process(
                "fragments/registered-customer-lookup",
                Set.of("registeredCustomerLookup"),
                context);

        assertTrue(html.contains("data-role=\"cashier\""));
        assertTrue(html.contains("name=\"customerId\""));
        assertTrue(html.contains("Buscar y asociar una cuenta de fidelización"));
    }

    @Test
    void hidesTheLookupWhenLoyaltyIsDisabled() {
        AbstractContext context = webContext();
        context.setVariable("rolePath", "cashier");
        context.setVariable("loyaltyEnabled", false);

        String html = templateEngine.process(
                "fragments/registered-customer-lookup",
                Set.of("registeredCustomerLookup"),
                context);

        assertFalse(html.contains("id=\"registeredCustomerEnabled\""));
    }

    @Test
    void rendersTheLookupScriptForTheExistingPhoneSearchEndpoint() {
        String html = templateEngine.process(
                "fragments/registered-customer-lookup",
                Set.of("registeredCustomerLookupScript"),
                webContext());

        assertTrue(html.contains("/orders/customers/by-phone?phone="));
        assertTrue(html.contains("No se encontraron cuentas activas"));
        assertTrue(html.contains("highlightPhone(checkbox.checked)"));
        assertTrue(html.contains("Escribe el teléfono en el campo resaltado"));
    }

    @Test
    void rendersTheCashierCustomerInfoPageWithTheLookupIncluded() {
        AbstractContext context = webContext();
        context.setVariable(
                "globalSystemConfig",
                GlobalSystemConfig.builder().systemName("Restaurante de prueba").build());
        context.setVariable("orderType", OrderType.DINE_IN);
        context.setVariable("selectedTable", null);
        context.setVariable("linkedReservation", null);
        context.setVariable("config", null);
        context.setVariable("currentRole", "cashier");
        context.setVariable("loyaltyEnabled", true);

        String html = templateEngine.process("cashier/orders/order-customer-info", context);

        assertTrue(html.contains("id=\"registeredCustomerEnabled\""));
        assertTrue(html.contains("data-role=\"cashier\""));
        assertTrue(
                html.indexOf("id=\"registeredCustomerEnabled\"")
                        < html.indexOf("id=\"customerName\""),
                "La búsqueda de fidelización debe aparecer antes de los datos de nombre y teléfono");
    }
}
