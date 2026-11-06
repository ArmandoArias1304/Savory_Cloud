package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.Payment;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.AbstractContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for the public autofactura page: processing the real template catches
 * Thymeleaf syntax/expression errors and verifies that operations excluded from the
 * global invoice (already invoiced by the establishment) can no longer be self-invoiced.
 */
@SpringBootTest
class AutofacturaExclusionRenderTest {

    private static final String EXCLUDED_HEADING = "Operación ya facturada por el establecimiento";
    private static final String GLOBAL_HEADING = "Operación incluida en factura global";
    private static final String FORM_MARKER = "name=\"rfc\"";

    @Autowired
    private SpringTemplateEngine templateEngine;

    private AbstractContext webContext() {
        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setContextPath("");
        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        IWebExchange exchange = application.buildExchange(request, response);
        return new WebContext(exchange);
    }

    private Order order(boolean excluded) {
        return Order.builder()
                .idOrder(1L)
                .orderNumber("ORD-1")
                .orderType(OrderType.DINE_IN)
                .status(OrderStatus.PAID)
                .total(new BigDecimal("150.00"))
                .paymentMethod(PaymentMethodType.CASH)
                .createdAt(LocalDateTime.now())
                .autofacturaKey("order-key")
                .facturaGlobalExcluida(excluded)
                .build();
    }

    private Payment payment(boolean excluded) {
        return Payment.builder()
                .idPayment(2L)
                .paymentFolio("PAY-1")
                .total(new BigDecimal("75.00"))
                .paymentMethod(PaymentMethodType.CASH)
                .paidAt(LocalDateTime.now())
                .autofacturaKey("payment-key")
                .facturaGlobalExcluida(excluded)
                .build();
    }

    private void baseVariables(AbstractContext ctx) {
        ctx.setVariable("globalSystemConfig",
                GlobalSystemConfig.builder().systemName("Test").systemLogoUrl(null).build());
        ctx.setVariable("taxSystems", Map.of("601", "601 - General de Ley Personas Morales"));
        ctx.setVariable("cfdiUses", Map.of("G03", "G03 - Gastos en general"));
        ctx.setVariable("alreadyInvoiced", false);
        ctx.setVariable("inGlobalInvoice", false);
        ctx.setVariable("excludedFromGlobalInvoice", false);
        ctx.setVariable("invDelivery", BigDecimal.ZERO);
        ctx.setVariable("invDiscount", BigDecimal.ZERO);
    }

    private String render(boolean excluded, boolean inGlobalInvoice, Payment payment) {
        AbstractContext ctx = webContext();
        baseVariables(ctx);
        ctx.setVariable("excludedFromGlobalInvoice", excluded);
        ctx.setVariable("inGlobalInvoice", inGlobalInvoice);
        ctx.setVariable("order", order(excluded));
        if (payment != null) {
            ctx.setVariable("payment", payment);
        }
        String html = templateEngine.process("autofactura", ctx);
        assertNotNull(html);
        return html;
    }

    @Test
    void excludedWholeOrderCannotBeSelfInvoiced() {
        String html = render(true, false, null);

        assertTrue(html.contains(EXCLUDED_HEADING), "the excluded notice should render");
        assertFalse(html.contains(FORM_MARKER), "the self-invoice form must not render");
        assertFalse(html.contains(GLOBAL_HEADING), "the global-invoice notice should not render");
    }

    @Test
    void excludedSplitAccountCannotBeSelfInvoiced() {
        String html = render(true, false, payment(true));

        assertTrue(html.contains(EXCLUDED_HEADING), "the excluded notice should render");
        assertFalse(html.contains(FORM_MARKER), "the self-invoice form must not render");
    }

    @Test
    void globalInvoiceNoticeWinsOverExcludedNotice() {
        String html = render(true, true, null);

        assertTrue(html.contains(GLOBAL_HEADING), "the global-invoice notice should render");
        assertFalse(html.contains(EXCLUDED_HEADING), "only one notice should render");
        assertFalse(html.contains(FORM_MARKER), "the self-invoice form must not render");
    }

    @Test
    void eligibleOperationStillRendersTheForm() {
        String html = render(false, false, null);

        assertTrue(html.contains(FORM_MARKER), "the self-invoice form should render");
        assertFalse(html.contains(EXCLUDED_HEADING), "the excluded notice should not render");
        assertTrue(html.contains("Método de pago"), "whole-order summary should show payment method");
        assertTrue(html.contains("Efectivo"), "whole-order summary should show the recorded method");
        assertTrue(html.contains("name=\"conceptMode\""), "the page must ask for the concept mode");
        assertTrue(html.contains("value=\"DETAILED\""), "the itemized option must be offered");
        assertTrue(html.contains("value=\"SINGLE\""), "the single-concept option must be offered");
        assertTrue(html.matches("(?s).*value=\"DETAILED\"[^>]*checked.*"),
                "itemized must be the default selection");
    }

    @Test
    void selectedSingleConceptModeSurvivesValidationErrors() {
        AbstractContext ctx = webContext();
        baseVariables(ctx);
        ctx.setVariable("order", order(false));
        ctx.setVariable("conceptMode", "SINGLE");

        String html = templateEngine.process("autofactura", ctx);

        assertTrue(html.matches("(?s).*value=\"SINGLE\"[^>]*checked.*"),
                "the resumida choice must stay selected after a validation error");
        assertFalse(html.matches("(?s).*value=\"DETAILED\"[^>]*checked.*"),
                "detallada must not be selected when the client chose resumida");
    }

    @Test
    void nonExcludedSplitAccountStillRendersTheForm() {
        String html = render(false, false, payment(false));

        assertTrue(html.contains(FORM_MARKER), "the self-invoice form should render");
        assertFalse(html.contains(EXCLUDED_HEADING), "the excluded notice should not render");
        assertTrue(html.contains("Método de pago"), "split-account summary should show payment method");
        assertTrue(html.contains("Efectivo"), "split-account summary should show the recorded method");
    }
}
