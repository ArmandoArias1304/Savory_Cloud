package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Employee;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El historial de ventas se puede ver como tabla o como tarjetas, igual que los pedidos.
 *
 * <p>Las tarjetas no se pintan en el servidor: las construye {@code js/sales-view.js} desde las
 * filas de la tabla, que siguen siendo la única fuente de verdad. Eso crea un contrato silencioso
 * —la fila publica sus datos en atributos {@code data-sale-*} y la tarjeta los lee por
 * nombre—, así que aquí se renderiza la plantilla real y se comprueba lo que publica, además de
 * recorrer el JavaScript para verificar que cada atributo que lee existe en la plantilla.
 * Renombrar uno de los dos lados falla, en vez de dejar una tarjeta en blanco sin que nada avise.
 * </p>
 */
@SpringBootTest
class SalesCardsViewTest {

    private static final Path TEMPLATE =
            Path.of("src/main/resources/templates/admin/sales/list.html");

    private static final Path SHARED_JS =
            Path.of("src/main/resources/static/js/sales-view.js");

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
        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaServletWebApplication application =
                JakartaServletWebApplication.buildApplication(servletContext);
        IWebExchange exchange = application.buildExchange(request, response);
        WebContext context = new WebContext(exchange);
        context.setVariable(
                ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                new ThymeleafEvaluationContext(webApplicationContext, new DefaultConversionService()));
        return context;
    }

    private Employee employee(long id, String nombre, String apellido) {
        return Employee.builder()
                .idEmpleado(id)
                .nombre(nombre)
                .apellido(apellido)
                .username(nombre.toLowerCase())
                .contrasenia("x")
                .build();
    }

    private Order sale(long id, String orderNumber, OrderType type, BigDecimal total,
                       BigDecimal tip, PaymentMethodType method) {
        return Order.builder()
                .idOrder(id)
                .orderNumber(orderNumber)
                .orderType(type)
                .status(OrderStatus.PAID)
                .total(total)
                .tip(tip)
                .paymentMethod(method)
                .paidBy(employee(4L, "Carla", "Cajera"))
                .employee(employee(5L, "Mario", "Mesero"))
                .createdAt(LocalDateTime.of(2026, 9, 24, 19, 0))
                .paidAt(LocalDateTime.of(2026, 9, 24, 20, 30))
                .orderDetails(new ArrayList<>())
                .payments(new ArrayList<>())
                .build();
    }

    private void baseModel(AbstractContext ctx, List<Order> sales) {
        ctx.setVariable("globalSystemConfig",
                GlobalSystemConfig.builder().systemName("Test").systemLogoUrl(null).build());
        ctx.setVariable("sales", sales);
        ctx.setVariable("currentPage", 1);
        ctx.setVariable("totalPages", 1);
        ctx.setVariable("totalElements", sales.size());
        ctx.setVariable("pageSize", 15);
        ctx.setVariable("employees", List.of());
        ctx.setVariable("paymentMethods", PaymentMethodType.values());
        ctx.setVariable("totalSales", new BigDecimal("216.00"));
        ctx.setVariable("totalSalesWithoutTip", new BigDecimal("200.00"));
        ctx.setVariable("totalTips", new BigDecimal("16.00"));
        ctx.setVariable("totalCount", (long) sales.size());
        ctx.setVariable("selectedEmployeeId", null);
        ctx.setVariable("selectedPaymentMethod", null);
        ctx.setVariable("invoiceStatuses", SalesController.InvoiceStatusFilter.values());
        ctx.setVariable("selectedInvoiceStatus", null);
        ctx.setVariable("startDate", null);
        ctx.setVariable("endDate", null);
        ctx.setVariable("billingEnabled", false);
        ctx.setVariable("companyZone", ZoneId.of("America/Mexico_City"));
        ctx.setVariable("activeMenu", "sales");
        ctx.setVariable("username", "gerente");
    }

    private String render(List<Order> sales) {
        AbstractContext ctx = webContext();
        baseModel(ctx, sales);
        return templateEngine.process("admin/sales/list", ctx);
    }

    @Test
    void pageOffersTheCardsViewOfTheSameList() {
        String html = render(List.of(sale(7L, "ORD-20260924-007", OrderType.DINE_IN,
                new BigDecimal("116.00"), new BigDecimal("10.00"), PaymentMethodType.CASH)));

        assertNotNull(html);
        assertTrue(html.contains("data-orders-view=\"table\""), "the toggle must offer the table");
        assertTrue(html.contains("data-orders-view=\"grid\""), "the toggle must offer the cards");
        assertTrue(html.contains("data-orders-view-scope=\"sales\""),
                "the toggle must say which list it switches");
        assertTrue(html.contains("id=\"salesTableView\""), "the table is the table view");
        assertTrue(html.contains("id=\"salesCardsView\""), "the cards need their own container");
        assertTrue(html.contains("id=\"salesCards\""), "the cards need a grid to be built into");
        assertTrue(html.contains("id=\"salesCardsEmpty\""),
                "the cards view needs its own no-results notice");
        assertTrue(html.contains("/js/sales-view.js"),
                "the page must load the script that builds the cards");
        assertTrue(html.contains("SalesView.init("),
                "the script must be started with the view it remembers");
    }

    @Test
    void eachRowPublishesTheSaleCardData() {
        String html = render(List.of(sale(7L, "ORD-20260924-007", OrderType.DELIVERY,
                new BigDecimal("116.00"), new BigDecimal("10.00"), PaymentMethodType.CASH)));

        assertTrue(html.contains("th:data-sale-id") || html.contains("data-sale-id=\"7\""),
                "the row must identify the sale");
        assertTrue(html.contains("data-sale-id=\"7\""), "the row must identify the sale");
        assertTrue(html.contains("data-sale-number=\"ORD-20260924-007\""),
                "the card shows the order number");
        assertTrue(html.contains("data-sale-when=\"24/09/2026"),
                "the card shows the payment date, taken from paidAt");
        assertTrue(html.contains("data-sale-type=\"DELIVERY\""), "the card shows the sale type");
        assertTrue(html.contains("data-sale-type-label=\""), "the card shows the type in words");
        assertTrue(html.contains("data-sale-payment=\""), "the card shows how it was paid");
        assertTrue(html.contains("data-sale-paid-by=\"Carla Cajera\""),
                "the card shows who collected the payment");
        assertTrue(html.contains("data-sale-waiter=\"Mario Mesero\""),
                "the card shows the waiter");
        assertTrue(html.contains("data-sale-subtotal-label=\"Subtotal\""),
                "without a payment filter the money column is the subtotal");
        assertTrue(html.contains("data-sale-tip=\"$10.00\""), "the card shows the tip");
        assertTrue(html.contains("data-sale-total=\"$126.00\""),
                "the card shows the total with tip");
        assertTrue(html.contains("data-sale-href=\"/admin/orders/view/7\""),
                "the card opens the sale it describes");
    }

    @Test
    void theCardCopiesTheInvoiceBadgeAndTheRowActions() {
        Order split = sale(8L, "ORD-20260924-008", OrderType.DINE_IN,
                new BigDecimal("80.00"), BigDecimal.ZERO, PaymentMethodType.CASH);
        split.getPayments().add(Payment.builder()
                .idPayment(1L)
                .paymentFolio("ORD-20260924-008-01")
                .total(new BigDecimal("40.00"))
                .paidAt(LocalDateTime.of(2026, 9, 24, 21, 0))
                .facturamaCfdiId("cfdi-account-1")
                .build());
        split.getPayments().add(Payment.builder()
                .idPayment(2L)
                .paymentFolio("ORD-20260924-008-02")
                .total(new BigDecimal("40.00"))
                .paidAt(LocalDateTime.of(2026, 9, 24, 21, 5))
                .build());

        String html = render(List.of(split));

        assertTrue(html.contains("sale-invoice-badge"),
                "the card copies the server-side invoice badge");
        assertTrue(html.contains("sale-invoice-note"),
                "the card copies the partial split-bill note too");
        assertTrue(html.contains("1 de 2 cuentas"),
                "the note must say how many accounts are already invoiced");
        assertTrue(html.contains("sale-actions-cell"),
                "the card copies the actions of the row");
        assertTrue(html.contains("/admin/orders/view/8"),
                "the row keeps its detail action");
    }

    @Test
    void withAPaymentMethodFilterTheCardSaysWhatTheMoneyIs() {
        AbstractContext ctx = webContext();
        Order mixed = sale(9L, "ORD-20260924-009", OrderType.TAKEOUT,
                new BigDecimal("300.00"), new BigDecimal("20.00"), PaymentMethodType.CREDIT_CARD);
        baseModel(ctx, List.of(mixed));
        ctx.setVariable("selectedPaymentMethod", PaymentMethodType.CASH);
        ctx.setVariable("methodFilterActive", true);
        ctx.setVariable("methodAmounts", Map.of(9L, new BigDecimal("150.00")));

        String html = templateEngine.process("admin/sales/list", ctx);

        assertTrue(html.contains("data-sale-subtotal-label=\"Monto del método\""),
                "the card must not call a filtered portion a subtotal");
        assertTrue(html.contains("data-sale-tip=\"—\""),
                "a tip cannot be attributed to a payment method");
        assertTrue(html.contains("data-sale-total=\"$150.00\""),
                "the card totals only what was collected with the filtered method");
        assertFalse(html.contains("data-sale-total=\"$300.00\""),
                "the whole sale total must not pass as the filtered amount");
    }

    /**
     * El contrato entre la plantilla y el JavaScript: cada atributo que el script lee de la fila
     * tiene que estar publicado, y las clases que copia también.
     */
    @Test
    void everyAttributeTheScriptReadsIsPublishedByTheTemplate() throws IOException {
        String template = Files.readString(TEMPLATE, StandardCharsets.UTF_8);
        String script = Files.readString(SHARED_JS, StandardCharsets.UTF_8);

        Matcher matcher = Pattern.compile("attr\\(row, \"([a-z-]+)\"\\)").matcher(script);
        Set<String> read = new LinkedHashSet<>();
        while (matcher.find()) {
            read.add(matcher.group(1));
        }

        assertFalse(read.isEmpty(), "the script must read the rows through data-sale-* attributes");
        for (String attribute : read) {
            assertTrue(template.contains("th:data-sale-" + attribute + "="),
                    "the template must publish data-sale-" + attribute
                            + " (the card reads it and would come out empty)");
        }

        assertTrue(script.contains(".sale-actions-cell") && template.contains("sale-actions-cell"),
                "the actions cell the card copies must be marked in the row");
        assertTrue(script.contains(".sale-invoice-badge")
                        && template.contains("sale-invoice-badge"),
                "the invoice badge the card copies must be marked in the row");
        assertTrue(script.contains(".sale-invoice-note")
                        && template.contains("sale-invoice-note"),
                "the split-bill note the card copies must be marked in the row");
        assertTrue(script.contains("salesCardsView") && script.contains("salesCardsEmpty"),
                "the script must know the containers the page renders");
    }
}
