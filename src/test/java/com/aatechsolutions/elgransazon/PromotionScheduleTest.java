package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.Promotion;
import com.aatechsolutions.elgransazon.domain.entity.PromotionType;
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
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Promotions are valid on a date range, on selected days of the week, and — the new
 * part — only inside an optional daily time window, e.g. Monday/Wednesday/Friday
 * from 07:00 to 12:00.
 */
@SpringBootTest
class PromotionScheduleTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private WebApplicationContext webApplicationContext;

    /** Mon/Wed/Fri, 12 Sep – 19 Sep, from 7am to noon. */
    private Promotion morningPromotion() {
        return Promotion.builder()
                .idPromotion(1L)
                .name("Café de la mañana 2x1")
                .promotionType(PromotionType.PERCENTAGE_DISCOUNT)
                .discountPercentage(new BigDecimal("10"))
                .startDate(LocalDate.of(2026, 9, 12))
                .endDate(LocalDate.of(2026, 9, 19))
                .validDays("MONDAY,WEDNESDAY,FRIDAY")
                .startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(12, 0))
                .active(true)
                .build();
    }

    // 2026-09-14 is a Monday, 2026-09-16 a Wednesday, 2026-09-15 a Tuesday.

    @Test
    void appliesOnValidDaysInsideTheTimeWindow() {
        Promotion promotion = morningPromotion();
        assertTrue(promotion.isValidAt(LocalDateTime.of(2026, 9, 14, 7, 0)), "monday at 07:00 opens the window");
        assertTrue(promotion.isValidAt(LocalDateTime.of(2026, 9, 14, 11, 59)), "monday at 11:59 is still inside");
        assertTrue(promotion.isValidAt(LocalDateTime.of(2026, 9, 16, 9, 30)), "wednesday at 09:30 is inside");
        assertTrue(promotion.isValidAt(LocalDateTime.of(2026, 9, 18, 10, 0)), "friday at 10:00 is inside");
    }

    @Test
    void stopsOutsideTheTimeWindow() {
        Promotion promotion = morningPromotion();
        assertFalse(promotion.isValidAt(LocalDateTime.of(2026, 9, 14, 6, 59)), "before 07:00 is not valid");
        assertFalse(promotion.isValidAt(LocalDateTime.of(2026, 9, 14, 12, 0)), "the window ends at 12:00");
        assertFalse(promotion.isValidAt(LocalDateTime.of(2026, 9, 14, 15, 0)), "the afternoon is not valid");
    }

    @Test
    void stillRespectsDaysAndDates() {
        Promotion promotion = morningPromotion();
        assertFalse(promotion.isValidAt(LocalDateTime.of(2026, 9, 15, 9, 0)), "tuesday is not a selected day");
        assertFalse(promotion.isValidAt(LocalDateTime.of(2026, 9, 21, 9, 0)), "monday after the end date");
        assertFalse(promotion.isValidAt(LocalDateTime.of(2026, 9, 11, 9, 0)), "friday before the start date");

        Promotion inactive = morningPromotion();
        inactive.setActive(false);
        assertFalse(inactive.isValidAt(LocalDateTime.of(2026, 9, 14, 9, 0)), "an inactive promotion is never valid");
    }

    @Test
    void withoutTimeWindowItAppliesAllDay() {
        Promotion promotion = morningPromotion();
        promotion.setStartTime(null);
        promotion.setEndTime(null);

        assertFalse(promotion.hasTimeWindow());
        assertEquals("Todo el día", promotion.getTimeWindowLabel());
        assertTrue(promotion.isValidAt(LocalDateTime.of(2026, 9, 14, 6, 0)), "all day: early morning");
        assertTrue(promotion.isValidAt(LocalDateTime.of(2026, 9, 14, 23, 30)), "all day: late night");
        assertFalse(promotion.isValidAt(LocalDateTime.of(2026, 9, 15, 12, 0)), "tuesday stays excluded");
    }

    @Test
    void aWindowCrossingMidnightBelongsToThePreviousDay() {
        // "Viernes de 22:00 a 02:00" (2026-09-18 is a Friday)
        Promotion promotion = morningPromotion();
        promotion.setValidDays("FRIDAY");
        promotion.setStartTime(LocalTime.of(22, 0));
        promotion.setEndTime(LocalTime.of(2, 0));

        assertEquals("22:00 a 02:00 (día siguiente)", promotion.getTimeWindowLabel());
        assertTrue(promotion.isValidAt(LocalDateTime.of(2026, 9, 18, 23, 0)), "friday night");
        assertTrue(promotion.isValidAt(LocalDateTime.of(2026, 9, 19, 1, 0)), "saturday early morning belongs to friday");
        assertFalse(promotion.isValidAt(LocalDateTime.of(2026, 9, 19, 3, 0)), "after 02:00 is over");
        assertFalse(promotion.isValidAt(LocalDateTime.of(2026, 9, 19, 23, 0)), "saturday night is a different day");
    }

    @Test
    void sameStartAndEndMeansAllDay() {
        Promotion promotion = morningPromotion();
        promotion.setStartTime(LocalTime.of(9, 0));
        promotion.setEndTime(LocalTime.of(9, 0));

        assertFalse(promotion.hasTimeWindow());
        assertTrue(promotion.isValidAt(LocalDateTime.of(2026, 9, 14, 20, 0)));
    }

    @Test
    void validDaysSetFeedsTheListBadges() {
        assertEquals(3, morningPromotion().getValidDaysSet().size());
        assertTrue(morningPromotion().getValidDaysSet().contains(DayOfWeek.MONDAY));
    }

    // ========== The promotions list shows the days and the window ==========

    private AbstractContext webContext() {
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webApplicationContext);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setContextPath("");
        SecurityContextImpl securityContext = new SecurityContextImpl();
        // ROLE_CASHIER keeps the sidebar's @licenseService-guarded links out of the render.
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "ana", "n/a", List.of(new SimpleGrantedAuthority("ROLE_CASHIER"))));
        SecurityContextHolder.setContext(securityContext);
        request.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        IWebExchange exchange = JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(request, response);
        return new WebContext(exchange);
    }

    @Test
    void promotionsListRendersDaysAndTimeWindow() {
        AbstractContext ctx = webContext();

        Promotion allDay = morningPromotion();
        allDay.setIdPromotion(2L);
        allDay.setStartTime(null);
        allDay.setEndTime(null);

        ctx.setVariable("globalSystemConfig", GlobalSystemConfig.builder().systemName("Test").build());
        ctx.setVariable("promotions", List.of(morningPromotion(), allDay));
        ctx.setVariable("promotionTypes", PromotionType.values());
        ctx.setVariable("selectedType", null);
        ctx.setVariable("selectedActive", null);
        ctx.setVariable("totalCount", 2L);
        ctx.setVariable("activeCount", 2L);
        ctx.setVariable("endingSoonCount", 0L);
        ctx.setVariable("username", "ana");

        String html = templateEngine.process("admin/promotions/list", ctx);

        assertTrue(html.contains("Lun"), "the selected days should be rendered");
        assertTrue(html.contains("🕒 07:00 a 12:00"), "the time window should be rendered");
        assertTrue(html.contains("🕒 Todo el día"), "a promotion without a window applies all day");
    }
}
