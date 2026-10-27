package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.dto.LoyaltyRewardForm;
import com.aatechsolutions.elgransazon.domain.entity.*;
import org.junit.jupiter.api.AfterEach;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders the loyalty screens with real templates so a broken expression or
 * fragment fails here instead of in front of a cashier.
 */
@SpringBootTest
class LoyaltyViewsRenderTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private AbstractContext context(String role) {
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webApplicationContext);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setContextPath("");
        SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "tester", "n/a", List.of(new SimpleGrantedAuthority(role))));
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
        context.setVariable("globalSystemConfig",
                GlobalSystemConfig.builder().systemName("Test").systemSlogan("Admin").build());
        context.setVariable("username", "admin");
        return context;
    }

    private ItemMenu item() {
        Company company = Company.builder().idCompany(1L).name("Test").build();
        return ItemMenu.builder()
                .idItemMenu(10L)
                .company(company)
                .name("Café americano")
                .price(new BigDecimal("35.00"))
                .active(true)
                .available(true)
                .internalOnly(false)
                .isCombo(false)
                .dineInOnly(false)
                .imageUrl("https://imagedelivery.net/test/hash/abc/public")
                .build();
    }

    private LoyaltyReward reward(Company company, ItemMenu item) {
        return LoyaltyReward.builder()
                .id(5L)
                .company(company)
                .itemMenu(item)
                .name("Café gratis")
                .description("Válido en tu próxima visita")
                .stampsCost(5)
                .freeQuantity(1)
                .active(true)
                .sortOrder(0)
                .build();
    }

    private Customer customer(Company company) {
        return Customer.builder()
                .idCustomer(7L)
                .company(company)
                .fullName("Ana López")
                .username("ana")
                .phone("5512345678")
                .email("ana@example.com")
                .password("x")
                .active(true)
                .build();
    }

    private LoyaltyProgram program(Company company) {
        return LoyaltyProgram.builder()
                .id(3L)
                .company(company)
                .stampsRequired(10)
                .minimumPurchaseAmount(BigDecimal.ZERO)
                .minimumItems(1)
                .maxStampsPerSale(1)
                .active(true)
                .cardTitle("Tarjeta de fidelidad")
                .cardMessage("Junta sellos")
                .rewardText("Café gratis")
                .build();
    }

    @Test
    void adminLoyaltyPagesRender() {
        Company company = Company.builder().idCompany(1L).name("Test").build();
        ItemMenu item = item();
        LoyaltyReward reward = reward(company, item);
        Customer customer = customer(company);
        LoyaltyAccount account = LoyaltyAccount.builder()
                .id(9L).company(company).customer(customer).balance(7)
                .lifetimeEarned(7).lifetimeRedeemed(0).active(true).build();
        LoyaltyRedemption redemption = LoyaltyRedemption.builder()
                .id(11L).company(company).account(account).reward(reward)
                .stampsSpent(5).rewardQuantity(1).orderId(77L)
                .quoteToken("token").appliedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now()).build();

        // Combos are valid rewards too: the selector marks them so the admin can tell
        // them apart from individual products.
        ItemMenu combo = ItemMenu.builder()
                .idItemMenu(12L).company(company).name("Combo pareja")
                .price(new BigDecimal("150.00")).active(true).available(true)
                .internalOnly(false).isCombo(true).dineInOnly(false).build();

        AbstractContext ctx = context("ROLE_ADMIN");
        ctx.setVariable("program", program(company));
        ctx.setVariable("rewardForm", new LoyaltyRewardForm());
        ctx.setVariable("rewards", List.of(reward));
        ctx.setVariable("items", List.of(item, combo));
        ctx.setVariable("accountsCount", 1L);
        ctx.setVariable("outstandingStamps", 7L);
        ctx.setVariable("redemptionsCount", 1L);
        ctx.setVariable("stampsRedeemed", 5L);
        ctx.setVariable("recentRedemptions", List.of(redemption));
        ctx.setVariable("activeMenu", "loyalty");

        String html = templateEngine.process("admin/loyalty/index", ctx);
        assertNotNull(html);
        assertTrue(html.contains("Catálogo de recompensas"));
        assertTrue(html.contains("Café gratis"));
        // Section where the admin picks both colours of the client card.
        assertTrue(html.contains("Colores de la tarjeta"));
        assertTrue(html.contains("Color del frente"));
        assertTrue(html.contains("Color del reverso"));
        assertTrue(html.contains("Combo pareja (combo)"));

        AbstractContext accountsCtx = context("ROLE_ADMIN");
        accountsCtx.setVariable("accounts", List.of(account));
        accountsCtx.setVariable("program", program(company));
        accountsCtx.setVariable("activeMenu", "loyalty");
        String accountsHtml = templateEngine.process("admin/loyalty/accounts", accountsCtx);
        assertTrue(accountsHtml.contains("Ana López"));
        assertTrue(accountsHtml.contains("Ajuste manual"));
    }

    @Test
    void programmerCompanyPageShowsTheLoyaltyAddOn() {
        Company company = Company.builder()
                .idCompany(1L)
                .name("Test")
                .slug("test")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        CompanyFeature feature = CompanyFeature.builder()
                .id(2L)
                .company(company)
                .featureKey(FeatureKey.LOYALTY)
                .status(CompanyFeatureStatus.ENABLED)
                .startsAt(LocalDateTime.now().minusDays(1))
                .endsAt(LocalDateTime.now().plusMonths(1))
                .price(new BigDecimal("199.00"))
                .billingCycle("MONTHLY")
                .build();

        AbstractContext ctx = context("ROLE_PROGRAMMER");
        ctx.setVariable("company", company);
        ctx.setVariable("loyaltyFeature", feature);
        ctx.setVariable("totalCfdis", 0L);
        ctx.setVariable("facturamaConfig", null);
        ctx.setVariable("facturamaLiveMode", false);
        ctx.setVariable("facturamaStatus", new com.aatechsolutions.elgransazon.application.service.FacturamaService.FacturamaConnectionStatus(
                false, false, false, 0, List.of(), false, false, LocalDateTime.now(), null));

        String html = templateEngine.process("programmer/companies/view", ctx);
        assertTrue(html.contains("Add-ons"));
        assertTrue(html.contains("Fidelización"));
        assertTrue(html.contains("Renovar"));
    }

    @Test
    void cashierAndClientLoyaltyCardsRender() {
        Company company = Company.builder().idCompany(1L).name("Test").build();
        ItemMenu item = item();
        LoyaltyReward reward = reward(company, item);
        Customer customer = customer(company);
        LoyaltyAccount account = LoyaltyAccount.builder()
                .id(9L).company(company).customer(customer).balance(7)
                .lifetimeEarned(7).lifetimeRedeemed(0).active(true).build();
        LoyaltyStampEvent event = LoyaltyStampEvent.builder()
                .id(1L).company(company).account(account)
                .order(Order.builder().orderNumber("ORD-1").build())
                .eventType(LoyaltyStampEventType.EARN).stamps(1).balanceAfter(7)
                .reason("Compra ORD-1").createdAt(LocalDateTime.now()).build();

        AbstractContext staffCtx = context("ROLE_CASHIER");
        staffCtx.setVariable("layout", "cashier");
        staffCtx.setVariable("showSettlement", true);
        staffCtx.setVariable("program", program(company));
        staffCtx.setVariable("rewards", List.of(reward));
        staffCtx.setVariable("term", "Ana");
        staffCtx.setVariable("selectedCustomer", customer);
        staffCtx.setVariable("selectedAccount", account);
        staffCtx.setVariable("recentEvents", List.of(event));
        staffCtx.setVariable("affordableRewards", 1L);
        staffCtx.setVariable("companyId", 1L);
        staffCtx.setVariable("pendingRewardOrders", List.of(
                new com.aatechsolutions.elgransazon.application.dto.LoyaltyRewardOrderView(
                        88L, "ORD-88", "Ana López", "Café gratis", "READY", "Listo", true)));
        staffCtx.setVariable("openOrders", List.of());
        String staffHtml = templateEngine.process("loyalty/redeem", staffCtx);
        assertTrue(staffHtml.contains("Ana López"));
        assertTrue(staffHtml.contains("Canjear recompensas"));
        assertTrue(staffHtml.contains("Café gratis"));
        assertTrue(staffHtml.contains("Pedido: ORD-1"));
        assertTrue(staffHtml.contains("data-add-reward=\"5\""));
        // Reward orders: live status row + confirmation dialog + WebSocket topics.
        assertTrue(staffHtml.contains("data-order-id=\"88\""));
        assertTrue(staffHtml.contains("class=\"reward-settle\""));
        assertTrue(staffHtml.contains("sweetalert2"));
        assertTrue(staffHtml.contains("/topic/admin/kitchen/"));
        assertTrue(staffHtml.contains("companyId: 1"));

        AbstractContext waiterCtx = context("ROLE_WAITER");
        waiterCtx.setVariable("layout", "waiter");
        waiterCtx.setVariable("showSettlement", false);
        waiterCtx.setVariable("program", program(company));
        waiterCtx.setVariable("rewards", List.of(reward));
        waiterCtx.setVariable("selectedCustomer", customer);
        waiterCtx.setVariable("selectedAccount", account);
        waiterCtx.setVariable("recentEvents", List.of(event));
        waiterCtx.setVariable("affordableRewards", 1L);
        waiterCtx.setVariable("openOrders", List.of(
                new com.aatechsolutions.elgransazon.application.dto.LoyaltyOpenOrderView(
                        77L, "ORD-77", "Mesa 4", "Ana López", "En preparación",
                        new BigDecimal("120.00"))));
        waiterCtx.setVariable("selectedOrderId", 77L);
        String waiterHtml = templateEngine.process("loyalty/redeem", waiterCtx);
        assertTrue(waiterHtml.contains("Pedido nuevo sin mesa"));
        assertTrue(waiterHtml.contains("Mesa 4"));

        AbstractContext clientCtx = context("ROLE_CLIENT");
        // Colours chosen in admin/loyalty: the client card must paint them and derive
        // the darker tones of the gradient.
        LoyaltyProgram clientProgram = program(company);
        clientProgram.setCardFrontColor("#123ABC");
        clientProgram.setCardBackColor("#654321");
        clientCtx.setVariable("program", clientProgram);
        clientCtx.setVariable("rewards", List.of(reward));
        clientCtx.setVariable("account", account);
        clientCtx.setVariable("customer", customer);
        clientCtx.setVariable("recentEvents", List.of(event));
        String clientHtml = templateEngine.process("client/loyalty", clientCtx);
        assertTrue(clientHtml.contains("Tarjeta de fidelidad"));
        assertTrue(clientHtml.contains("Café gratis"));
        assertTrue(clientHtml.contains("Recompensas disponibles"));
        assertTrue(clientHtml.contains("loyalty-rewards-scroll"));
        assertTrue(clientHtml.contains("max-height: 25.5rem"));
        // Reward card shows the menu item image when it has one, plus its progress.
        assertTrue(clientHtml.contains("https://imagedelivery.net/test/hash/abc/"));
        assertTrue(clientHtml.contains("¡Ya puedes canjearla!"));
        assertTrue(clientHtml.contains("loyalty-flip-card"));
        assertTrue(clientHtml.contains("--loyalty-front: #123ABC"));
        assertTrue(clientHtml.contains("--loyalty-back: #654321"));
        assertTrue(clientHtml.contains("loyalty-face-front"));
        assertTrue(clientHtml.contains("loyalty-face-back"));
        // The stamp history scrolls inside its own box with a visible scrollbar.
        assertTrue(clientHtml.contains("loyalty-history-disclosure"));
        assertTrue(clientHtml.contains("Pedido: ORD-1"));
        assertTrue(clientHtml.contains("<summary"));
        assertTrue(clientHtml.contains("Mostrar / ocultar"));
        assertTrue(clientHtml.contains("loyalty-history-scroll"));
        assertTrue(clientHtml.contains("max-h-80 overflow-y-auto"));
        assertTrue(clientHtml.contains("Ana López"));
    }
}
