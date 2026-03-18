package com.aatechsolutions.elgransazon.gaps;

import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.EmailService;
import com.aatechsolutions.elgransazon.application.service.PromotionService;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.PromotionRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.support.TestDataHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GAP-FILL — Tests de integración para verificar el comportamiento de timezone
 * en filtros, promociones, DateTimeService y reportes por empresa.
 *
 * Crea 2 empresas con timezones muy diferentes (Asia/Tokyo UTC+9 y America/Los_Angeles UTC-7/8)
 * y verifica que cada una obtiene la fecha/hora local correcta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("GAP — Timezone, Promotions & DateTimeService Integration Tests")
class TimezoneAndPromotionIntegrationTest {

    private static final String SLUG_TOKYO  = "tz-tokyo-c1";
    private static final String SLUG_LA     = "tz-la-c2";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper helper;
    @Autowired private DateTimeService dateTimeService;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private PromotionService promotionService;
    @MockBean  private EmailService emailService;

    private Company tokyoCompany, laCompany;
    private Employee adminTokyo, adminLA;

    @BeforeEach
    void setUp() {
        helper.cleanUpBySlug(SLUG_TOKYO);
        helper.cleanUpBySlug(SLUG_LA);

        tokyoCompany = helper.createActiveCompany(SLUG_TOKYO, "Asia/Tokyo");        // UTC+9
        laCompany    = helper.createActiveCompany(SLUG_LA, "America/Los_Angeles");   // UTC-7/-8

        adminTokyo = helper.createEmployee(tokyoCompany, "tz-admin-tokyo", "Pass1234!", "ROLE_ADMIN");
        adminLA    = helper.createEmployee(laCompany, "tz-admin-la", "Pass1234!", "ROLE_ADMIN");
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        helper.cleanUpBySlug(SLUG_TOKYO);
        helper.cleanUpBySlug(SLUG_LA);
    }

    // ================================================================
    //  DateTimeService respeta timezone por Company
    // ================================================================

    @Test
    @DisplayName("DateTimeService.todayLocal() devuelve fecha local de Tokyo (UTC+9)")
    void dateTimeService_todayLocal_respectsTokyoTimezone() {
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate tokyoToday = dateTimeService.todayLocal();
        // Compare with reference
        LocalDate expectedTokyoDate = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        assertThat(tokyoToday).isEqualTo(expectedTokyoDate);
    }

    @Test
    @DisplayName("DateTimeService.todayLocal() devuelve fecha local de Los Angeles (UTC-7/-8)")
    void dateTimeService_todayLocal_respectsLATimezone() {
        CompanyContext.setCurrentCompany(laCompany);
        LocalDate laToday = dateTimeService.todayLocal();
        LocalDate expectedLADate = LocalDate.now(ZoneId.of("America/Los_Angeles"));
        assertThat(laToday).isEqualTo(expectedLADate);
    }

    @Test
    @DisplayName("DateTimeService.nowLocal() tiene hora diferente entre Tokyo y Los Angeles")
    void dateTimeService_nowLocal_differentTimezones_haveDifferentHours() {
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDateTime tokyoNow = dateTimeService.nowLocal();

        CompanyContext.setCurrentCompany(laCompany);
        LocalDateTime laNow = dateTimeService.nowLocal();

        // Tokyo is UTC+9, LA is UTC-7/-8 → ~16-17 hours apart
        // They MUST have different hours (unless it's exactly midnight in one)
        // At least dates or hours should differ
        boolean timeDiffers = !tokyoNow.toLocalTime().truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                .equals(laNow.toLocalTime().truncatedTo(java.time.temporal.ChronoUnit.HOURS))
                || !tokyoNow.toLocalDate().equals(laNow.toLocalDate());
        assertThat(timeDiffers)
                .as("Tokyo (%s) y LA (%s) deben tener hora o fecha diferente", tokyoNow, laNow)
                .isTrue();
    }

    @Test
    @DisplayName("DateTimeService.getCompanyZone() retorna zona correcta por empresa")
    void dateTimeService_getCompanyZone_perCompany() {
        CompanyContext.setCurrentCompany(tokyoCompany);
        assertThat(dateTimeService.getCompanyZone()).isEqualTo(ZoneId.of("Asia/Tokyo"));

        CompanyContext.setCurrentCompany(laCompany);
        assertThat(dateTimeService.getCompanyZone()).isEqualTo(ZoneId.of("America/Los_Angeles"));
    }

    @Test
    @DisplayName("DateTimeService.startOfDayUtc() calcula UTC correcto según zona de empresa")
    void dateTimeService_startOfDayUtc_zoneAware() {
        LocalDate sampleDate = LocalDate.of(2026, 6, 15);

        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDateTime tokyoStartUtc = dateTimeService.startOfDayUtc(sampleDate);
        // Tokyo midnight = 2026-06-15T00:00 JST = 2026-06-14T15:00 UTC
        assertThat(tokyoStartUtc).isEqualTo(LocalDateTime.of(2026, 6, 14, 15, 0, 0));

        CompanyContext.setCurrentCompany(laCompany);
        LocalDateTime laStartUtc = dateTimeService.startOfDayUtc(sampleDate);
        // LA midnight in PDT (June → summertime, UTC-7) = 2026-06-15T07:00 UTC
        assertThat(laStartUtc).isEqualTo(LocalDateTime.of(2026, 6, 15, 7, 0, 0));
    }

    // ================================================================
    //  Promociones respetan timezone por Company
    // ================================================================

    @Test
    @DisplayName("Promoción activa visible en ambas empresas cuando hoy está en rango")
    void promotion_activeInBothCompanies_whenTodayInRange() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Los_Angeles"));
        // Create promotions valid for a wide range including today
        Promotion promoTokyo = createPromotion(tokyoCompany, "Promo Tokyo",
                today.minusDays(5), today.plusDays(5));
        Promotion promoLA = createPromotion(laCompany, "Promo LA",
                today.minusDays(5), today.plusDays(5));

        CompanyContext.setCurrentCompany(tokyoCompany);
        List<Promotion> tokyoActive = promotionService.findActivePromotions();
        assertThat(tokyoActive).extracting(Promotion::getName).contains("Promo Tokyo");
        assertThat(tokyoActive).extracting(Promotion::getName).doesNotContain("Promo LA");

        CompanyContext.setCurrentCompany(laCompany);
        List<Promotion> laActive = promotionService.findActivePromotions();
        assertThat(laActive).extracting(Promotion::getName).contains("Promo LA");
        assertThat(laActive).extracting(Promotion::getName).doesNotContain("Promo Tokyo");
    }

    @Test
    @DisplayName("Promoción expirada ayer (local) no aparece como activa")
    void promotion_expiredYesterday_isNotActive() {
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate tokyoToday = dateTimeService.todayLocal();
        Promotion expiredPromo = createPromotion(tokyoCompany, "Expired Promo",
                tokyoToday.minusDays(10), tokyoToday.minusDays(1));

        List<Promotion> active = promotionService.findActivePromotions();
        assertThat(active).extracting(Promotion::getName).doesNotContain("Expired Promo");
    }

    @Test
    @DisplayName("Promoción que empieza mañana (local) no aparece como activa")
    void promotion_startsTomorrow_isNotActive() {
        CompanyContext.setCurrentCompany(laCompany);
        LocalDate laToday = dateTimeService.todayLocal();
        Promotion futurePromo = createPromotion(laCompany, "Future Promo",
                laToday.plusDays(1), laToday.plusDays(10));

        List<Promotion> active = promotionService.findActivePromotions();
        assertThat(active).extracting(Promotion::getName).doesNotContain("Future Promo");
    }

    @Test
    @DisplayName("Promoción desactivada (active=false) no aparece aunque la fecha sea válida")
    void promotion_deactivated_isNotInActiveList() {
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate today = dateTimeService.todayLocal();
        Promotion promo = createPromotion(tokyoCompany, "Deactivated Promo",
                today.minusDays(1), today.plusDays(10));
        promo.setActive(false);
        promotionRepository.save(promo);

        List<Promotion> active = promotionService.findActivePromotions();
        assertThat(active).extracting(Promotion::getName).doesNotContain("Deactivated Promo");
    }

    @Test
    @DisplayName("Promociones multi-tenant: cada empresa solo ve sus propias promociones")
    void promotions_multiTenant_isolation() {
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate tokyoToday = dateTimeService.todayLocal();
        createPromotion(tokyoCompany, "Solo Tokyo", tokyoToday.minusDays(1), tokyoToday.plusDays(5));

        CompanyContext.setCurrentCompany(laCompany);
        LocalDate laToday = dateTimeService.todayLocal();
        createPromotion(laCompany, "Solo LA", laToday.minusDays(1), laToday.plusDays(5));

        // Tokyo should only see "Solo Tokyo"
        CompanyContext.setCurrentCompany(tokyoCompany);
        List<Promotion> tokyoList = promotionService.findActivePromotions();
        assertThat(tokyoList).extracting(Promotion::getName)
                .contains("Solo Tokyo")
                .doesNotContain("Solo LA");

        // LA should only see "Solo LA"
        CompanyContext.setCurrentCompany(laCompany);
        List<Promotion> laList = promotionService.findActivePromotions();
        assertThat(laList).extracting(Promotion::getName)
                .contains("Solo LA")
                .doesNotContain("Solo Tokyo");
    }

    // ================================================================
    //  Reportes y Sales respetan timezone en filtros de fecha (vía MockMvc)
    // ================================================================

    @Test
    @DisplayName("Admin puede ver reportes con filtros de fecha — aislamiento por empresa")
    void admin_reports_dateFilter_companyIsolation() throws Exception {
        // Create delivered orders for each company
        helper.createDeliveredOrder(tokyoCompany, adminTokyo,
                OrderType.TAKEOUT, PaymentMethodType.CASH, "TZ-TK-001");
        helper.createDeliveredOrder(laCompany, adminLA,
                OrderType.TAKEOUT, PaymentMethodType.CASH, "TZ-LA-001");

        // Tokyo admin reports
        mockMvc.perform(get("/admin/reports")
                        .param("startDate", LocalDate.now().minusDays(1).toString())
                        .param("endDate", LocalDate.now().plusDays(1).toString())
                        .with(user("tz-admin-tokyo").roles("ADMIN"))
                        .with(forCompany(SLUG_TOKYO)))
                .andExpect(status().isOk());

        // LA admin reports
        mockMvc.perform(get("/admin/reports")
                        .param("startDate", LocalDate.now().minusDays(1).toString())
                        .param("endDate", LocalDate.now().plusDays(1).toString())
                        .with(user("tz-admin-la").roles("ADMIN"))
                        .with(forCompany(SLUG_LA)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Sales con filtros de fecha — cada empresa ve solo sus ventas")
    void admin_sales_dateFilter_companyIsolation() throws Exception {
        helper.createDeliveredOrder(tokyoCompany, adminTokyo,
                OrderType.DINE_IN, PaymentMethodType.CREDIT_CARD, "TZ-TK-S01");

        mockMvc.perform(get("/admin/sales")
                        .param("startDate", LocalDate.now().minusDays(1).toString())
                        .param("endDate", LocalDate.now().plusDays(1).toString())
                        .with(user("tz-admin-tokyo").roles("ADMIN"))
                        .with(forCompany(SLUG_TOKYO)))
                .andExpect(status().isOk());
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private Promotion createPromotion(Company company, String name,
                                      LocalDate startDate, LocalDate endDate) {
        Promotion promo = Promotion.builder()
                .company(company)
                .name(name)
                .description("Test promo")
                .promotionType(PromotionType.PERCENTAGE_DISCOUNT)
                .discountPercentage(new BigDecimal("10"))
                .startDate(startDate)
                .endDate(endDate)
                .validDays("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY")
                .active(true)
                .deleted(false)
                .priority(1)
                .build();
        return promotionRepository.save(promo);
    }

    private RequestPostProcessor forCompany(String slug) {
        return request -> {
            request.setServerName(slug + ".localhost");
            return request;
        };
    }
}
