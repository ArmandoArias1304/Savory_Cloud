package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.CashRegisterMovement;
import com.aatechsolutions.elgransazon.domain.entity.CashRegisterMovementType;
import com.aatechsolutions.elgransazon.domain.entity.CashRegisterSession;
import com.aatechsolutions.elgransazon.domain.entity.CashRegisterStatus;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.presentation.dto.CashRegisterHistoryRow;
import com.aatechsolutions.elgransazon.presentation.dto.CashRegisterSummary;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for the cash-register views: processing the templates with the
 * real engine catches Thymeleaf syntax/expression errors without a server.
 */
@SpringBootTest
class CashRegisterTemplateRenderTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    private Map<PaymentMethodType, BigDecimal> salesByMethod() {
        Map<PaymentMethodType, BigDecimal> map = new LinkedHashMap<>();
        map.put(PaymentMethodType.CASH, new BigDecimal("100.00"));
        map.put(PaymentMethodType.CREDIT_CARD, new BigDecimal("50.00"));
        map.put(PaymentMethodType.TRANSFER, BigDecimal.ZERO);
        return map;
    }

    /** A real web context so @{...} links inside the theme fragment resolve. */
    private AbstractContext webContext() {
        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setContextPath("");
        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        IWebExchange exchange = application.buildExchange(request, response);
        return new WebContext(exchange);
    }

    private GlobalSystemConfig systemConfig() {
        return GlobalSystemConfig.builder().systemName("Test").systemLogoUrl(null).build();
    }

    private CashRegisterSession session(boolean open) {
        Employee cashier = Employee.builder()
                .idEmpleado(1L)
                .nombre("Ana")
                .apellido("Lopez")
                .username("ana")
                .build();
        return CashRegisterSession.builder()
                .id(1L)
                .cashier(cashier)
                .status(open ? CashRegisterStatus.OPEN : CashRegisterStatus.CLOSED)
                .openedAt(LocalDateTime.now())
                .closedAt(open ? null : LocalDateTime.now())
                .initialAmount(new BigDecimal("200.00"))
                .countedAmount(open ? null : new BigDecimal("280.00"))
                .build();
    }

    private CashRegisterSummary summary(boolean closed) {
        CashRegisterSummary.CashRegisterSummaryBuilder b = CashRegisterSummary.builder()
                .initialAmount(new BigDecimal("200.00"))
                .totalSales(new BigDecimal("150.00"))
                .totalTips(new BigDecimal("10.00"))
                .salesCount(2)
                .salesByMethod(salesByMethod())
                .totalExpenses(new BigDecimal("20.00"))
                .totalIncomes(BigDecimal.ZERO)
                .totalCashTips(new BigDecimal("35.00"))
                .cashSales(new BigDecimal("100.00"))
                .expectedCash(new BigDecimal("280.00"));
        if (closed) {
            b.countedAmount(new BigDecimal("275.00")).difference(new BigDecimal("-5.00"));
        }
        return b.build();
    }

    @Test
    void dayViewRenders() {
        AbstractContext ctx = webContext();
        ctx.setVariable("globalSystemConfig", systemConfig());
        ctx.setVariable("rolePrefix", "cashier");
        // NOTE: "session" is a reserved Thymeleaf expression object (the HTTP session),
        // so the controller exposes the entity as "cashSession".
        ctx.setVariable("cashSession", session(true));
        ctx.setVariable("sessionOpen", true);
        ctx.setVariable("allowOpen", false);
        ctx.setVariable("summary", summary(false));
        ctx.setVariable("movements", List.of(CashRegisterMovement.builder()
                .id(1L)
                .type(CashRegisterMovementType.EXPENSE)
                .concept("Hielo")
                .amount(new BigDecimal("20.00"))
                .occurredAt(LocalDateTime.now())
                .build()));
        ctx.setVariable("movementTypes", CashRegisterMovementType.selectable());
        ctx.setVariable("username", "ana");

        String html = templateEngine.process("cashier/cash-register/view", ctx);
        assertNotNull(html);
        assertTrue(html.contains("Hielo"), "the movement should be rendered");
        assertTrue(html.contains("/cashier/cash-register/session/1/pdf"),
                "the PDF link must carry the real session id, not null");
        // Saving a movement and deleting one must both ask for confirmation
        assertTrue(html.contains("id=\"movementForm\""),
                "the movement form must be wired to its confirmation dialog");
        assertTrue(html.contains("js-delete-movement"),
                "every movement delete button must be wired to its confirmation dialog");
        // Closing the drawer captures the counted cash in a modal, not in the footer
        // panel
        assertTrue(html.contains("onclick=\"openCloseDrawer()\""),
                "the close-drawer button must open the modal");
        assertTrue(html.contains("closeCountedInput") && html.contains("CLOSE_EXPECTED_TEXT"),
                "the modal must ask for the counted cash and show the expected one");
        // Las propinas en efectivo son una entrada: el concepto se ofrece una sola vez
        // (el valor legado WITHDRAWAL también se llama "Propinas efectivo" y ya no se ofrece)
        assertTrue(html.contains("Propinas efectivo"),
                "the cash-tip concept must be offered in the form");
        assertTrue(!html.contains("WITHDRAWAL"),
                "the legacy cash-tip value must not be offered again");
        assertTrue(html.contains("propinas efectivo"),
                "the day cards must show the cash tips registered in the drawer");
    }

    @Test
    void dayViewRendersWithNoSessionYet() {
        AbstractContext ctx = webContext();
        ctx.setVariable("globalSystemConfig", systemConfig());
        ctx.setVariable("rolePrefix", "cashier");
        ctx.setVariable("cashSession", null);
        ctx.setVariable("sessionOpen", false);
        ctx.setVariable("allowOpen", true);
        ctx.setVariable("summary", summary(false));
        ctx.setVariable("movements", List.of());
        ctx.setVariable("movementTypes", CashRegisterMovementType.selectable());
        ctx.setVariable("username", "ana");

        String html = templateEngine.process("cashier/cash-register/view", ctx);
        assertNotNull(html);
        assertTrue(html.contains("Abrir caja"), "the open-drawer form should be offered");
        assertTrue(!html.contains("/pdf"), "no PDF link without a session");
    }

    @Test
    void historyRenders() {
        AbstractContext ctx = webContext();
        ctx.setVariable("globalSystemConfig", systemConfig());
        ctx.setVariable("rolePrefix", "cashier");
        ctx.setVariable("rows", List.of(new CashRegisterHistoryRow(session(false), summary(true))));
        ctx.setVariable("filterDate", LocalDate.now());
        ctx.setVariable("today", LocalDate.now());
        ctx.setVariable("username", "ana");

        String html = templateEngine.process("cashier/cash-register/history", ctx);
        assertNotNull(html);
        assertTrue(html.contains("Hielo") || html.contains("Cerrada"), "history rows should be rendered");
        assertTrue(html.contains("/cashier/cash-register/session/1"),
                "the history links must carry the real session id, not null");
    }

    /**
     * Caja links and form actions follow the area the page was opened in: an admin opens
     * /admin/cash-register and never /cashier/... (the URL must not say another role).
     */
    @Test
    void dayViewLinksFollowTheAreaItWasOpenedIn() {
        AbstractContext ctx = webContext();
        ctx.setVariable("globalSystemConfig", systemConfig());
        ctx.setVariable("rolePrefix", "admin");
        ctx.setVariable("cashSession", session(true));
        // Open drawer so the open / movements / close forms are all rendered
        ctx.setVariable("sessionOpen", true);
        ctx.setVariable("allowOpen", true);
        ctx.setVariable("summary", summary(false));
        ctx.setVariable("movements", List.of(CashRegisterMovement.builder()
                .id(1L)
                .type(CashRegisterMovementType.EXPENSE)
                .concept("Hielo")
                .amount(new BigDecimal("20.00"))
                .occurredAt(LocalDateTime.now())
                .build()));
        ctx.setVariable("movementTypes", CashRegisterMovementType.selectable());
        ctx.setVariable("username", "ana");

        String html = templateEngine.process("cashier/cash-register/view", ctx);

        assertTrue(html.contains("/admin/cash-register/session/1/pdf"),
                "the PDF link must use the admin area");
        assertTrue(html.contains("/admin/cash-register/history"),
                "the history link must use the admin area");
        assertTrue(html.contains("action=\"/admin/cash-register/open\""),
                "the open form must post to the admin area");
        assertTrue(html.contains("action=\"/admin/cash-register/movements\""),
                "the movements form must post to the admin area");
        assertTrue(html.contains("action=\"/admin/cash-register/movements/1/delete\""),
                "the delete form must post to the admin area");
        assertTrue(html.contains("action=\"/admin/cash-register/close\""),
                "the close form must post to the admin area");
        assertFalse(html.contains("/cashier/cash-register"),
                "nothing may stay hardcoded to the cashier area");
    }
}
