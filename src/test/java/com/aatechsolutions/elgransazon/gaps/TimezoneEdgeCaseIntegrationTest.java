package com.aatechsolutions.elgransazon.gaps;

import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.EmailService;
import com.aatechsolutions.elgransazon.application.service.LicenseService;
import com.aatechsolutions.elgransazon.application.service.PromotionService;
import com.aatechsolutions.elgransazon.application.service.ReservationService;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.PromotionRepository;
import com.aatechsolutions.elgransazon.domain.repository.ReservationRepository;
import com.aatechsolutions.elgransazon.domain.repository.SystemLicenseRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GAP-FILL — Tests de edge-case de timezone para promociones, licencias y reservaciones.
 *
 * Verifica que a un mismo instante UTC, empresas en timezones muy diferentes
 * (Asia/Tokyo UTC+9 y America/Los_Angeles UTC-7/-8, ~16-17h de diferencia)
 * obtienen resultados correctos y distintos cuando la fecha local difiere.
 *
 * Cubre:
 * - Promotion.isValidNow() con fecha-frontera y dayOfWeek distinto por timezone
 * - PromotionService.findActivePromotions() con rangos de fecha frontera
 * - SystemLicense.isExpired() y daysUntilExpiration() con fecha frontera
 * - LicenseValidationFilter bloqueando/permitiendo acceso según timezone
 * - Reservation.isToday() / isUpcoming() / isPast() según timezone
 * - ReservationService.findTodayReservations() con timezone
 * - Aislamiento multi-tenant de reservaciones
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("GAP — Timezone Edge Cases: Promotions, Licenses & Reservations")
class TimezoneEdgeCaseIntegrationTest {

    private static final String SLUG_TOKYO = "tz-edge-tokyo";
    private static final String SLUG_LA    = "tz-edge-la";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper helper;
    @Autowired private DateTimeService dateTimeService;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private PromotionService promotionService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationService reservationService;
    @Autowired private SystemLicenseRepository licenseRepository;
    @Autowired private LicenseService licenseService;
    @MockBean  private EmailService emailService;

    private Company tokyoCompany, laCompany;
    private Employee adminTokyo, adminLA;
    private RestaurantTable tableTokyo, tableLA;

    @BeforeEach
    void setUp() {
        helper.cleanUpBySlug(SLUG_TOKYO);
        helper.cleanUpBySlug(SLUG_LA);

        tokyoCompany = helper.createActiveCompany(SLUG_TOKYO, "Asia/Tokyo");        // UTC+9
        laCompany    = helper.createActiveCompany(SLUG_LA, "America/Los_Angeles");   // UTC-7/-8

        adminTokyo = helper.createEmployee(tokyoCompany, "tz-edge-admin-tk", "Pass1234!", "ROLE_ADMIN");
        adminLA    = helper.createEmployee(laCompany, "tz-edge-admin-la", "Pass1234!", "ROLE_ADMIN");

        tableTokyo = helper.createRestaurantTable(tokyoCompany, 1, 4);
        tableLA    = helper.createRestaurantTable(laCompany, 1, 4);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        invalidateCacheFor(tokyoCompany);
        invalidateCacheFor(laCompany);
        helper.cleanUpBySlug(SLUG_TOKYO);
        helper.cleanUpBySlug(SLUG_LA);
    }

    // ================================================================
    //  PROMOCIONES — Timezone Edge Cases
    // ================================================================

    @Test
    @DisplayName("Promotion.isValidNow() usa fecha local de cada company — verificación directa")
    void promotionIsValidNow_usesCompanyLocalDate() {
        // Promo válida del "hoy de Tokyo" al "hoy de Tokyo" (solo un día)
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate tokyoToday = dateTimeService.todayLocal();

        Promotion promo = createPromotion(tokyoCompany, "Tokyo Day Promo",
                tokyoToday, tokyoToday);

        // En contexto Tokyo → debe ser válida
        CompanyContext.setCurrentCompany(tokyoCompany);
        assertThat(promo.isValidNow())
                .as("Promo debe ser válida en contexto Tokyo cuando rango incluye hoy-Tokyo")
                .isTrue();

        // En contexto LA → depende si la fecha local de LA coincide con tokyoToday
        CompanyContext.setCurrentCompany(laCompany);
        LocalDate laToday = dateTimeService.todayLocal();
        boolean expectedInLA = !laToday.isBefore(tokyoToday) && !laToday.isAfter(tokyoToday);
        assertThat(promo.isValidNow())
                .as("Promo isValidNow en contexto LA debe reflejar fecha local LA (%s) vs rango [%s, %s]",
                        laToday, tokyoToday, tokyoToday)
                .isEqualTo(expectedInLA);
    }

    @Test
    @DisplayName("Promoción válida solo 'mañana-LA' — no visible para LA hoy, sí si Tokyo ya está en esa fecha")
    void promotionStartsTomorrowLA_onlyVisibleIfTokyoAlreadyThere() {
        CompanyContext.setCurrentCompany(laCompany);
        LocalDate laTomorrow = dateTimeService.todayLocal().plusDays(1);

        // Promo que empieza "mañana de LA"
        Promotion promo = createPromotion(laCompany, "Future LA Promo",
                laTomorrow, laTomorrow.plusDays(5));

        // LA no debe verla activa
        List<Promotion> laActive = promotionService.findActivePromotions();
        assertThat(laActive).extracting(Promotion::getName)
                .doesNotContain("Future LA Promo");
    }

    @Test
    @DisplayName("Promoción expiró 'ayer-Tokyo' — Tokyo no la ve, LA aún podría verla si su ayer es diferente")
    void promotionExpiredYesterdayTokyo_tokyoDoesntSeeIt() {
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate tokyoYesterday = dateTimeService.todayLocal().minusDays(1);

        // Promo que terminó ayer según Tokyo
        Promotion promo = createPromotion(tokyoCompany, "Expired Tokyo Promo",
                tokyoYesterday.minusDays(10), tokyoYesterday);

        List<Promotion> tokyoActive = promotionService.findActivePromotions();
        assertThat(tokyoActive).extracting(Promotion::getName)
                .as("Tokyo no debe ver promo expirada ayer-Tokyo")
                .doesNotContain("Expired Tokyo Promo");
    }

    @Test
    @DisplayName("DayOfWeek de promoción difiere por timezone — validación correcta por contexto")
    void promotionDayOfWeek_respectsCompanyTimezone() {
        // Obtener el dayOfWeek actual de cada timezone
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate tokyoToday = dateTimeService.todayLocal();
        java.time.DayOfWeek tokyoDayOfWeek = tokyoToday.getDayOfWeek();

        CompanyContext.setCurrentCompany(laCompany);
        LocalDate laToday = dateTimeService.todayLocal();
        java.time.DayOfWeek laDayOfWeek = laToday.getDayOfWeek();

        // Crear promo en Tokyo válida SOLO para el dayOfWeek de Tokyo (amplio rango de fechas)
        Promotion tokyoDayPromo = createPromotionWithDays(tokyoCompany, "Tokyo DayOfWeek Promo",
                tokyoToday.minusDays(5), tokyoToday.plusDays(5),
                tokyoDayOfWeek.name());

        // En contexto Tokyo → el día coincide → debe ser válida
        CompanyContext.setCurrentCompany(tokyoCompany);
        assertThat(tokyoDayPromo.isValidNow())
                .as("Promo válida para %s debe aparecer en Tokyo (hoy=%s)", tokyoDayOfWeek, tokyoToday)
                .isTrue();

        // En contexto LA → depende si el dayOfWeek de LA coincide
        CompanyContext.setCurrentCompany(laCompany);
        boolean expectedInLA = laDayOfWeek == tokyoDayOfWeek
                && !laToday.isBefore(tokyoToday.minusDays(5))
                && !laToday.isAfter(tokyoToday.plusDays(5));
        assertThat(tokyoDayPromo.isValidNow())
                .as("Promo (día=%s) en contexto LA (hoy=%s, día=%s): expected=%s",
                        tokyoDayOfWeek, laToday, laDayOfWeek, expectedInLA)
                .isEqualTo(expectedInLA);
    }

    @Test
    @DisplayName("PromotionService.findActivePromotions() filtra por dayOfWeek de la timezone local")
    void findActivePromotions_filtersByLocalDayOfWeek() {
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate tokyoToday = dateTimeService.todayLocal();
        java.time.DayOfWeek tokyoDayOfWeek = tokyoToday.getDayOfWeek();
        // Día que NO es hoy en Tokyo
        java.time.DayOfWeek notTokyoDay = tokyoDayOfWeek.plus(1);

        // Promo válida solo para un día que NO es hoy en Tokyo
        createPromotionWithDays(tokyoCompany, "Wrong Day Promo",
                tokyoToday.minusDays(1), tokyoToday.plusDays(5),
                notTokyoDay.name());

        // Promo válida para hoy en Tokyo
        createPromotionWithDays(tokyoCompany, "Right Day Promo",
                tokyoToday.minusDays(1), tokyoToday.plusDays(5),
                tokyoDayOfWeek.name());

        List<Promotion> activePromos = promotionService.findActivePromotions();
        assertThat(activePromos).extracting(Promotion::getName)
                .contains("Right Day Promo")
                .doesNotContain("Wrong Day Promo");
    }

    // ================================================================
    //  LICENCIAS — Timezone Edge Cases
    // ================================================================

    @Test
    @DisplayName("SystemLicense.isExpired() usa CompanyLocalTime.today() — cada timezone evalúa diferente")
    void licenseIsExpired_usesCompanyLocalDate() {
        // Poner expirationDate = tokyoToday (para que en Tokyo esté expirada: !expirationDate.isAfter(today) → true)
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate tokyoToday = dateTimeService.todayLocal();

        SystemLicense tokyoLicense = licenseRepository.findByCompanyId(tokyoCompany.getIdCompany())
                .orElseThrow();
        tokyoLicense.setExpirationDate(tokyoToday);
        licenseRepository.save(tokyoLicense);

        // Verificar en contexto Tokyo → isExpired = true (expirationDate == today → !today.isAfter(today) = true)
        CompanyContext.setCurrentCompany(tokyoCompany);
        assertThat(tokyoLicense.isExpired())
                .as("Licencia con expirationDate=hoy-Tokyo debe ser expired en contexto Tokyo")
                .isTrue();

        // Poner la MISMA expirationDate en LA
        SystemLicense laLicense = licenseRepository.findByCompanyId(laCompany.getIdCompany())
                .orElseThrow();
        laLicense.setExpirationDate(tokyoToday); // misma fecha absoluta
        licenseRepository.save(laLicense);

        // Verificar en contexto LA
        CompanyContext.setCurrentCompany(laCompany);
        LocalDate laToday = dateTimeService.todayLocal();
        boolean expectedExpiredInLA = !tokyoToday.isAfter(laToday);
        assertThat(laLicense.isExpired())
                .as("Licencia con expirationDate=%s en contexto LA (today=%s): expired=%s",
                        tokyoToday, laToday, expectedExpiredInLA)
                .isEqualTo(expectedExpiredInLA);
    }

    @Test
    @DisplayName("SystemLicense.daysUntilExpiration() varía según timezone de la company")
    void licenseDaysUntilExpiration_variesByTimezone() {
        // Poner una fecha futura fija como expiración en ambas licencias
        LocalDate futureDate = LocalDate.of(2027, 6, 15);

        SystemLicense tokyoLicense = licenseRepository.findByCompanyId(tokyoCompany.getIdCompany())
                .orElseThrow();
        tokyoLicense.setExpirationDate(futureDate);
        licenseRepository.save(tokyoLicense);

        SystemLicense laLicense = licenseRepository.findByCompanyId(laCompany.getIdCompany())
                .orElseThrow();
        laLicense.setExpirationDate(futureDate);
        licenseRepository.save(laLicense);

        // Calcular daysUntil en cada contexto
        CompanyContext.setCurrentCompany(tokyoCompany);
        long tokyoDays = tokyoLicense.daysUntilExpiration();
        LocalDate tokyoToday = dateTimeService.todayLocal();
        long expectedTokyoDays = ChronoUnit.DAYS.between(tokyoToday, futureDate);
        assertThat(tokyoDays).isEqualTo(expectedTokyoDays);

        CompanyContext.setCurrentCompany(laCompany);
        long laDays = laLicense.daysUntilExpiration();
        LocalDate laToday = dateTimeService.todayLocal();
        long expectedLaDays = ChronoUnit.DAYS.between(laToday, futureDate);
        assertThat(laDays).isEqualTo(expectedLaDays);

        // Si las fechas locales difieren, los días deben diferir
        if (!tokyoToday.equals(laToday)) {
            assertThat(tokyoDays)
                    .as("Si Tokyo y LA están en distinta fecha, daysUntilExpiration debe diferir")
                    .isNotEqualTo(laDays);
        }
    }

    @Test
    @DisplayName("LicenseValidationFilter: licencia expirada hoy-Tokyo bloquea acceso en Tokyo")
    void licenseFilter_expiredInTokyo_blocksAccessInTokyo() throws Exception {
        // Expirar licencia en tokyoToday
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate tokyoToday = dateTimeService.todayLocal();

        helper.setLicenseDaysUntilExpiry(tokyoCompany.getIdCompany(), 0); // expira hoy → expired
        // Actualizar la fecha de expiración a exactamente hoy-Tokyo
        licenseRepository.findByCompanyId(tokyoCompany.getIdCompany()).ifPresent(lic -> {
            lic.setExpirationDate(tokyoToday);
            lic.setStatus(SystemLicense.LicenseStatus.ACTIVE); // keep active status, let isExpired() decide
            licenseRepository.save(lic);
        });
        invalidateCacheFor(tokyoCompany);

        // Tokyo admin accede → debe ser redirigido a /license-expired
        mockMvc.perform(get("/admin/dashboard")
                        .with(user("tz-edge-admin-tk").roles("ADMIN"))
                        .with(forCompany(SLUG_TOKYO)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/license-expired"));
    }

    @Test
    @DisplayName("LicenseValidationFilter: misma fecha de expiración, LA aún puede acceder si su today es anterior")
    void licenseFilter_sameDateButDifferentTimezones() throws Exception {
        // Usar tokyoToday como fecha de expiración para ambas
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate tokyoToday = dateTimeService.todayLocal();

        CompanyContext.setCurrentCompany(laCompany);
        LocalDate laToday = dateTimeService.todayLocal();

        // Si Tokyo está en un día diferente a LA, este test demuestra la verdadera edge-case
        // Poner expirationDate = tokyoToday para la licencia de LA
        licenseRepository.findByCompanyId(laCompany.getIdCompany()).ifPresent(lic -> {
            lic.setExpirationDate(tokyoToday);
            lic.setStatus(SystemLicense.LicenseStatus.ACTIVE);
            licenseRepository.save(lic);
        });
        invalidateCacheFor(laCompany);

        if (tokyoToday.isAfter(laToday)) {
            // Tokyo está "adelante" → expirationDate = tokyoToday > laToday → LA todavía no expiró
            mockMvc.perform(get("/admin/dashboard")
                            .with(user("tz-edge-admin-la").roles("ADMIN"))
                            .with(forCompany(SLUG_LA)))
                    .andExpect(status().isOk());
        } else {
            // Mismo día → expirationDate = today → expirada en ambos
            mockMvc.perform(get("/admin/dashboard")
                            .with(user("tz-edge-admin-la").roles("ADMIN"))
                            .with(forCompany(SLUG_LA)))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/license-expired"));
        }
    }

    @Test
    @DisplayName("License warning threshold: ≤5 días difiere por timezone cuando fecha local difiere")
    void licenseWarning_thresholdDiffersByTimezone() {
        // Poner expirationDate en exactamente 5 días desde tokyoToday
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate tokyoToday = dateTimeService.todayLocal();
        LocalDate expirationDate = tokyoToday.plusDays(5);

        SystemLicense tokyoLicense = licenseRepository.findByCompanyId(tokyoCompany.getIdCompany())
                .orElseThrow();
        tokyoLicense.setExpirationDate(expirationDate);
        tokyoLicense.setBillingCycle(SystemLicense.BillingCycle.MONTHLY); // MONTHLY ≤5 → needsNotification
        licenseRepository.save(tokyoLicense);

        CompanyContext.setCurrentCompany(tokyoCompany);
        long tokyoDaysLeft = tokyoLicense.daysUntilExpiration();
        assertThat(tokyoDaysLeft).isEqualTo(5L);

        // Poner la misma expirationDate para LA
        SystemLicense laLicense = licenseRepository.findByCompanyId(laCompany.getIdCompany())
                .orElseThrow();
        laLicense.setExpirationDate(expirationDate);
        laLicense.setBillingCycle(SystemLicense.BillingCycle.MONTHLY);
        licenseRepository.save(laLicense);

        CompanyContext.setCurrentCompany(laCompany);
        LocalDate laToday = dateTimeService.todayLocal();
        long laDaysLeft = laLicense.daysUntilExpiration();
        long expectedLaDays = ChronoUnit.DAYS.between(laToday, expirationDate);
        assertThat(laDaysLeft).isEqualTo(expectedLaDays);

        // Si Tokyo y LA no están en la misma fecha, los días serán diferentes
        if (!tokyoToday.equals(laToday)) {
            assertThat(tokyoDaysLeft).isNotEqualTo(laDaysLeft);
        }
    }

    // ================================================================
    //  RESERVACIONES — Timezone Edge Cases
    // ================================================================

    @Test
    @DisplayName("Reservation.isToday() respeta CompanyLocalTime.today() por company")
    void reservationIsToday_respectsCompanyTimezone() {
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate tokyoToday = dateTimeService.todayLocal();

        // Crear reservación con fecha = hoy-Tokyo
        Reservation res = createReservation(tokyoCompany, tableTokyo, "Tokyo Today Res",
                tokyoToday, LocalTime.of(14, 0));

        // En contexto Tokyo → isToday debe ser true
        CompanyContext.setCurrentCompany(tokyoCompany);
        assertThat(res.isToday())
                .as("Reservación fecha=%s debe ser isToday=true en contexto Tokyo (today=%s)",
                        tokyoToday, tokyoToday)
                .isTrue();

        // En contexto LA → depende de si laToday == tokyoToday
        CompanyContext.setCurrentCompany(laCompany);
        LocalDate laToday = dateTimeService.todayLocal();
        assertThat(res.isToday())
                .as("Reservación fecha=%s: isToday en contexto LA (today=%s) debe ser %s",
                        tokyoToday, laToday, tokyoToday.equals(laToday))
                .isEqualTo(tokyoToday.equals(laToday));
    }

    @Test
    @DisplayName("Reservation.isUpcoming() y isPast() varían por timezone cuando fecha local difiere")
    void reservationIsUpcomingIsPast_variesByTimezone() {
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate tokyoToday = dateTimeService.todayLocal();

        CompanyContext.setCurrentCompany(laCompany);
        LocalDate laToday = dateTimeService.todayLocal();

        // Crear reservación con fecha = tokyoToday
        Reservation res = createReservation(tokyoCompany, tableTokyo, "Timezone Boundary Res",
                tokyoToday, LocalTime.of(12, 0));

        // Tokyo: isUpcoming debe ser true (today == reservationDate → !isBefore → upcoming)
        CompanyContext.setCurrentCompany(tokyoCompany);
        assertThat(res.isUpcoming()).as("isUpcoming en Tokyo").isTrue();
        assertThat(res.isPast()).as("isPast en Tokyo").isFalse();

        // LA: depende de si laToday está antes o después de tokyoToday
        CompanyContext.setCurrentCompany(laCompany);
        if (laToday.isBefore(tokyoToday)) {
            // LA está "atrás" → reservationDate (tokyoToday) es futuro para LA
            assertThat(res.isUpcoming()).as("isUpcoming en LA cuando LA está atrás").isTrue();
            assertThat(res.isPast()).as("isPast en LA cuando LA está atrás").isFalse();
        } else {
            // Mismo día → upcoming
            assertThat(res.isUpcoming()).as("isUpcoming en LA cuando mismo día").isTrue();
            assertThat(res.isPast()).as("isPast en LA cuando mismo día").isFalse();
        }
    }

    @Test
    @DisplayName("ReservationService.findTodayReservations() filtra por fecha local de cada company")
    void findTodayReservations_usesCompanyLocalDate() {
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate tokyoToday = dateTimeService.todayLocal();

        CompanyContext.setCurrentCompany(laCompany);
        LocalDate laToday = dateTimeService.todayLocal();

        // Crear reservaciones: una para hoy-Tokyo, otra para hoy-LA
        Reservation tokyoRes = createReservation(tokyoCompany, tableTokyo, "Tokyo Today Guest",
                tokyoToday, LocalTime.of(19, 0));
        Reservation laRes = createReservation(laCompany, tableLA, "LA Today Guest",
                laToday, LocalTime.of(19, 0));

        // findTodayReservations en contexto Tokyo → solo la de Tokyo con fecha tokyoToday
        CompanyContext.setCurrentCompany(tokyoCompany);
        List<Reservation> tokyoTodayRes = reservationService.findTodayReservations();
        assertThat(tokyoTodayRes).extracting(Reservation::getCustomerName)
                .contains("Tokyo Today Guest")
                .doesNotContain("LA Today Guest");

        // findTodayReservations en contexto LA → solo la de LA con fecha laToday
        CompanyContext.setCurrentCompany(laCompany);
        List<Reservation> laTodayRes = reservationService.findTodayReservations();
        assertThat(laTodayRes).extracting(Reservation::getCustomerName)
                .contains("LA Today Guest")
                .doesNotContain("Tokyo Today Guest");
    }

    @Test
    @DisplayName("ReservationService.findUpcomingReservations() aislamiento multi-tenant con timezone")
    void findUpcomingReservations_multiTenantWithTimezone() {
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate tokyoToday = dateTimeService.todayLocal();

        CompanyContext.setCurrentCompany(laCompany);
        LocalDate laToday = dateTimeService.todayLocal();

        // Reservaciones futuras para cada empresa
        createReservation(tokyoCompany, tableTokyo, "Tokyo Future Guest",
                tokyoToday.plusDays(3), LocalTime.of(20, 0));
        createReservation(laCompany, tableLA, "LA Future Guest",
                laToday.plusDays(3), LocalTime.of(20, 0));

        // Tokyo solo ve sus upcoming
        CompanyContext.setCurrentCompany(tokyoCompany);
        List<Reservation> tokyoUpcoming = reservationService.findUpcomingReservations();
        assertThat(tokyoUpcoming).extracting(Reservation::getCustomerName)
                .contains("Tokyo Future Guest")
                .doesNotContain("LA Future Guest");

        // LA solo ve sus upcoming
        CompanyContext.setCurrentCompany(laCompany);
        List<Reservation> laUpcoming = reservationService.findUpcomingReservations();
        assertThat(laUpcoming).extracting(Reservation::getCustomerName)
                .contains("LA Future Guest")
                .doesNotContain("Tokyo Future Guest");
    }

    @Test
    @DisplayName("Reservación pasada solo en una timezone — isPast/isUpcoming coherentes con contexto")
    void reservationPastInOneTimezone_futureInAnother() {
        CompanyContext.setCurrentCompany(laCompany);
        LocalDate laToday = dateTimeService.todayLocal();
        LocalDate laYesterday = laToday.minusDays(1);

        // Reservación de "ayer-LA"
        Reservation pastLA = createReservation(laCompany, tableLA, "Yesterday LA Guest",
                laYesterday, LocalTime.of(18, 0));

        // En contexto LA → debe ser past
        CompanyContext.setCurrentCompany(laCompany);
        assertThat(pastLA.isPast()).as("ayer-LA debe ser past en contexto LA").isTrue();
        assertThat(pastLA.isUpcoming()).as("ayer-LA no debe ser upcoming en contexto LA").isFalse();

        // En contexto Tokyo → también debe ser past (porque tokyoToday >= laToday siempre)
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate tokyoToday = dateTimeService.todayLocal();
        assertThat(pastLA.isPast())
                .as("ayer-LA (%s) en contexto Tokyo (today=%s) debe ser past", laYesterday, tokyoToday)
                .isTrue();
    }

    @Test
    @DisplayName("Multiple reservaciones en distintas fechas — findTodayReservations filtra correctamente")
    void findTodayReservations_handlesMultipleDatesCorrectly() {
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate tokyoToday = dateTimeService.todayLocal();

        // Crear 3 reservaciones: ayer, hoy, mañana
        createReservation(tokyoCompany, tableTokyo, "Yesterday Guest",
                tokyoToday.minusDays(1), LocalTime.of(12, 0));
        createReservation(tokyoCompany, tableTokyo, "Today Guest",
                tokyoToday, LocalTime.of(14, 0));
        createReservation(tokyoCompany, tableTokyo, "Tomorrow Guest",
                tokyoToday.plusDays(1), LocalTime.of(16, 0));

        List<Reservation> todayRes = reservationService.findTodayReservations();
        assertThat(todayRes).extracting(Reservation::getCustomerName)
                .contains("Today Guest")
                .doesNotContain("Yesterday Guest", "Tomorrow Guest");
    }

    @Test
    @DisplayName("Reservation entity isToday/isUpcoming/isPast coherentes para fecha futura lejana")
    void reservationEntityMethods_futureDateCoherent() {
        CompanyContext.setCurrentCompany(tokyoCompany);
        LocalDate farFuture = LocalDate.of(2028, 12, 25);

        Reservation futureRes = createReservation(tokyoCompany, tableTokyo, "Far Future Guest",
                farFuture, LocalTime.of(19, 0));

        // Debe ser upcoming pero no today ni past
        assertThat(futureRes.isToday()).isFalse();
        assertThat(futureRes.isUpcoming()).isTrue();
        assertThat(futureRes.isPast()).isFalse();

        // Lo mismo en contexto LA
        CompanyContext.setCurrentCompany(laCompany);
        assertThat(futureRes.isToday()).isFalse();
        assertThat(futureRes.isUpcoming()).isTrue();
        assertThat(futureRes.isPast()).isFalse();
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private Promotion createPromotion(Company company, String name,
                                      LocalDate startDate, LocalDate endDate) {
        return createPromotionWithDays(company, name, startDate, endDate,
                "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY");
    }

    private Promotion createPromotionWithDays(Company company, String name,
                                               LocalDate startDate, LocalDate endDate,
                                               String validDays) {
        Promotion promo = Promotion.builder()
                .company(company)
                .name(name)
                .description("Test edge-case promo")
                .promotionType(PromotionType.PERCENTAGE_DISCOUNT)
                .discountPercentage(new BigDecimal("10"))
                .startDate(startDate)
                .endDate(endDate)
                .validDays(validDays)
                .active(true)
                .deleted(false)
                .priority(1)
                .build();
        return promotionRepository.save(promo);
    }

    private Reservation createReservation(Company company, RestaurantTable table,
                                           String customerName,
                                           LocalDate date, LocalTime time) {
        Reservation reservation = Reservation.builder()
                .company(company)
                .restaurantTable(table)
                .customerName(customerName)
                .customerPhone("5551234567")
                .numberOfGuests(2)
                .reservationDate(date)
                .reservationTime(time)
                .status(ReservationStatus.PENDING)
                .build();
        return reservationRepository.save(reservation);
    }

    private void invalidateCacheFor(Company company) {
        if (company != null) {
            CompanyContext.setCurrentCompany(company);
            try {
                licenseService.invalidateLicenseCache();
            } finally {
                CompanyContext.clear();
            }
        }
    }

    private RequestPostProcessor forCompany(String slug) {
        return request -> {
            request.setServerName(slug + ".localhost");
            return request;
        };
    }
}
