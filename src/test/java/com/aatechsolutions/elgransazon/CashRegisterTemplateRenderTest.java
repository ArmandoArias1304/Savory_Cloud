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
                .totalWithdrawals(BigDecimal.ZERO)
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
        ctx.setVariable("movementTypes", CashRegisterMovementType.values());
        ctx.setVariable("username", "ana");

        String html = templateEngine.process("cashier/cash-register/view", ctx);
        assertNotNull(html);
        assertTrue(html.contains("Hielo"), "the movement should be rendered");
        assertTrue(html.contains("/cashier/cash-register/session/1/pdf"),
                "the PDF link must carry the real session id, not null");
    }

    @Test
    void dayViewRendersWithNoSessionYet() {
        AbstractContext ctx = webContext();
        ctx.setVariable("globalSystemConfig", systemConfig());
        ctx.setVariable("cashSession", null);
        ctx.setVariable("sessionOpen", false);
        ctx.setVariable("allowOpen", true);
        ctx.setVariable("summary", summary(false));
        ctx.setVariable("movements", List.of());
        ctx.setVariable("movementTypes", CashRegisterMovementType.values());
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
}
