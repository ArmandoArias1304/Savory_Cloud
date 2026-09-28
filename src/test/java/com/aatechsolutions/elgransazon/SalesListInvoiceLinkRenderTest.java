package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.Payment;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.presentation.controller.SalesController;
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
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import org.springframework.core.convert.support.DefaultConversionService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sales view (admin/manager): the "generar link de facturación" action must appear only on
 * sales that still need a link, and only while the establishment has billing enabled.
 * Rendering the real template also catches Thymeleaf expression errors in the new button.
 */
@SpringBootTest
class SalesListInvoiceLinkRenderTest {

    /** The button markup (the class is also mentioned by the delegated click listener). */
    private static final String BUTTON = "class=\"btn-generate-invoice-link";

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
        // MANAGER: enough to render the button (sec:authorize) while the sidebar keeps its
        // ADMIN-only links hidden, which is what the standalone render needs.
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
        // The admin/manager sidebar resolves beans (@licenseService) when this role is used, and
        // a hand built WebContext has no bean resolver unless it is provided explicitly.
        context.setVariable(
                ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                new ThymeleafEvaluationContext(webApplicationContext, new DefaultConversionService()));
        return context;
    }

    private Order sale(long id, String orderNumber) {
        return Order.builder()
                .idOrder(id)
                .orderNumber(orderNumber)
                .orderType(OrderType.DINE_IN)
                .status(OrderStatus.PAID)
                .total(new BigDecimal("116.00"))
                .tip(new BigDecimal("10.00"))
                .paymentMethod(PaymentMethodType.CASH)
                .createdAt(LocalDateTime.of(2026, 9, 24, 20, 0))
                .paidAt(LocalDateTime.of(2026, 9, 24, 20, 30))
                .orderDetails(new ArrayList<>())
                .payments(new ArrayList<>())
                .build();
    }

    private void baseModel(AbstractContext ctx, List<Order> sales, boolean billingEnabled) {
        ctx.setVariable("globalSystemConfig",
                GlobalSystemConfig.builder().systemName("Test").systemLogoUrl(null).build());
        ctx.setVariable("sales", sales);
        ctx.setVariable("currentPage", 1);
        ctx.setVariable("totalPages", 1);
        ctx.setVariable("totalElements", sales.size());
        ctx.setVariable("pageSize", 15);
        ctx.setVariable("employees", List.of());
        ctx.setVariable("paymentMethods", PaymentMethodType.values());
        ctx.setVariable("totalSales", new BigDecimal("116.00"));
        ctx.setVariable("totalSalesWithoutTip", new BigDecimal("106.00"));
        ctx.setVariable("totalTips", new BigDecimal("10.00"));
        ctx.setVariable("totalCount", (long) sales.size());
        ctx.setVariable("selectedEmployeeId", null);
        ctx.setVariable("selectedPaymentMethod", null);
        ctx.setVariable("invoiceStatuses", SalesController.InvoiceStatusFilter.values());
        ctx.setVariable("selectedInvoiceStatus", null);
        ctx.setVariable("startDate", null);
        ctx.setVariable("endDate", null);
        ctx.setVariable("billingEnabled", billingEnabled);
        ctx.setVariable("companyZone", ZoneId.of("America/Mexico_City"));
        ctx.setVariable("activeMenu", "sales");
        ctx.setVariable("username", "gerente");
    }

    @Test
    void actionIsOfferedForSalesWithoutLink() {
        AbstractContext ctx = webContext();
        Order pending = sale(7L, "ORD-20260924-007");
        baseModel(ctx, List.of(pending), true);

        String html = templateEngine.process("admin/sales/list", ctx);

        assertNotNull(html);
        assertTrue(html.contains(BUTTON), "the generate-link action should render");
        assertTrue(html.contains("data-order-id=\"7\""), "the button must carry the sale id");
        assertTrue(html.contains("qr_code_2"), "the action should use the QR icon");
        assertTrue(html.contains("/generate-invoice-link"), "the endpoint path must be wired");
    }

    @Test
    void actionIsHiddenForSalesThatAlreadyHaveALink() {
        AbstractContext ctx = webContext();
        Order linked = sale(8L, "ORD-20260924-008");
        linked.setAutofacturaKey("some-key");
        linked.setSelfInvoiceUrl("https://resto.example.com/autofactura/some-key");
        baseModel(ctx, List.of(linked), true);

        assertFalse(templateEngine.process("admin/sales/list", ctx).contains(BUTTON),
                "a sale that already has its link must not offer the action");
    }

    @Test
    void actionIsHiddenForInvoicedOrExcludedSales() {
        AbstractContext ctx = webContext();
        Order invoicedGlobally = sale(9L, "ORD-20260924-009");
        invoicedGlobally.setFacturaGlobalCfdiId("global-1");
        baseModel(ctx, List.of(invoicedGlobally), true);

        assertFalse(templateEngine.process("admin/sales/list", ctx).contains(BUTTON),
                "a sale amparada by a factura global must not offer the action");

        AbstractContext excludedCtx = webContext();
        Order excluded = sale(10L, "ORD-20260924-010");
        excluded.setFacturaGlobalExcluida(true);
        baseModel(excludedCtx, List.of(excluded), true);

        assertFalse(templateEngine.process("admin/sales/list", excludedCtx).contains(BUTTON),
                "a sale excluded from the factura global must not offer the action");
    }

    @Test
    void actionIsHiddenWhenBillingIsNotEnabled() {
        AbstractContext ctx = webContext();
        baseModel(ctx, List.of(sale(11L, "ORD-20260924-011")), false);

        assertFalse(templateEngine.process("admin/sales/list", ctx).contains(BUTTON),
                "without a ready Facturama config the link would be useless");
    }

    @Test
    void invoiceStatusColumnAndFilterAreRendered() {
        AbstractContext ctx = webContext();
        Order pending = sale(20L, "ORD-20260924-020");
        Order invoiced = sale(21L, "ORD-20260924-021");
        invoiced.setFacturamaCfdiId("cfdi-1");
        baseModel(ctx, List.of(pending, invoiced), true);

        String html = templateEngine.process("admin/sales/list", ctx);

        assertTrue(html.contains("Facturación"),
                "the invoice status column header (and filter label) should render");
        assertTrue(html.contains("Facturada"), "an invoiced sale must say Facturada");
        assertTrue(html.contains("No facturada"), "a pending sale must say No facturada");
        assertTrue(html.contains("id=\"filterInvoiceStatus\""),
                "the invoice status filter should render");
        assertTrue(html.contains("Facturadas") && html.contains("No facturadas"),
                "the filter should offer both options");
    }

    @Test
    void emptyStateSpansTheNewColumn() {
        AbstractContext ctx = webContext();
        baseModel(ctx, List.of(), true);

        String html = templateEngine.process("admin/sales/list", ctx);

        assertTrue(html.contains("colspan=\"11\""),
                "the \"no sales\" message must span the added column");
    }

    @Test
    void partialSplitBillShowsHowManyAccountsAreInvoiced() {
        AbstractContext ctx = webContext();
        Order split = sale(22L, "ORD-20260924-022");
        Payment invoicedAccount = Payment.builder()
                .idPayment(1L)
                .paymentFolio("ORD-20260924-022-01")
                .total(new BigDecimal("58.00"))
                .paidAt(LocalDateTime.of(2026, 9, 24, 21, 0))
                .facturamaCfdiId("cfdi-account-1")
                .build();
        Payment pendingAccount = Payment.builder()
                .idPayment(2L)
                .paymentFolio("ORD-20260924-022-02")
                .total(new BigDecimal("58.00"))
                .paidAt(LocalDateTime.of(2026, 9, 24, 21, 5))
                .build();
        split.getPayments().add(invoicedAccount);
        split.getPayments().add(pendingAccount);
        baseModel(ctx, List.of(split), true);

        String html = templateEngine.process("admin/sales/list", ctx);

        assertTrue(html.contains("No facturada"),
                "a half invoiced split bill is not fully invoiced yet");
        assertTrue(html.contains("1 de 2 cuentas"),
                "the column must explain how many accounts are already invoiced");
    }

    @Test
    void selectedInvoiceStatusIsMarkedInTheFilter() {
        AbstractContext ctx = webContext();
        baseModel(ctx, List.of(sale(23L, "ORD-20260924-023")), true);
        ctx.setVariable("selectedInvoiceStatus", SalesController.InvoiceStatusFilter.INVOICED);

        String html = templateEngine.process("admin/sales/list", ctx);

        int index = html.indexOf("value=\"INVOICED\"");
        assertTrue(index >= 0, "the invoiced option must be rendered");
        assertTrue(html.substring(index, Math.min(index + 60, html.length())).contains("selected"),
                "the active filter must come back selected after searching");
    }

    @Test
    void splitSaleOffersTheActionWhenAnAccountStillHasNoLink() {
        AbstractContext ctx = webContext();
        Order split = sale(12L, "ORD-20260924-012");
        Payment linkedAccount = Payment.builder()
                .idPayment(1L)
                .paymentFolio("ORD-20260924-012-01")
                .total(new BigDecimal("58.00"))
                .paidAt(LocalDateTime.of(2026, 9, 24, 21, 0))
                .autofacturaKey("account-key")
                .selfInvoiceUrl("https://resto.example.com/autofactura/account-key")
                .build();
        Payment pendingAccount = Payment.builder()
                .idPayment(2L)
                .paymentFolio("ORD-20260924-012-02")
                .total(new BigDecimal("58.00"))
                .paidAt(LocalDateTime.of(2026, 9, 24, 21, 5))
                .build();
        split.getPayments().add(linkedAccount);
        split.getPayments().add(pendingAccount);
        baseModel(ctx, List.of(split), true);

        String html = templateEngine.process("admin/sales/list", ctx);

        assertTrue(html.contains(BUTTON),
                "a split bill with an account without link should still offer the action");
        assertTrue(html.contains("data-order-id=\"12\""), "the button must carry the sale id");
    }
}
