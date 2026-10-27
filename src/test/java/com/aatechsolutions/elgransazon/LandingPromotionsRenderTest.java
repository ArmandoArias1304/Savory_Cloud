package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.Promotion;
import com.aatechsolutions.elgransazon.domain.entity.PromotionType;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La landing anuncia las promociones activas aunque se visiten fuera de su horario, y ofrece los
 * anuncios que se abren al entrar con su carrusel.
 *
 * <p>Lo primero es una decisión del servicio ({@code findActiveForLanding()} ignora la hora y el
 * día); aquí se comprueba que la tarjeta <b>sí</b> imprime el horario para que el cliente sepa
 * cuándo vuelve, y que el fragmento compartido pinta las tarjetas y el modal tal como lo espera
 * {@code js/landing-promotions.js}.</p>
 */
@SpringBootTest
class LandingPromotionsRenderTest {

    private static final Path FRAGMENT =
            Path.of("src/main/resources/templates/fragments/landing-promotions.html");

    private static final Path SCRIPT =
            Path.of("src/main/resources/static/js/landing-promotions.js");

    private static final Path STYLES =
            Path.of("src/main/resources/static/css/landing-promotions.css");

    private static final Path LEGAL_FRAGMENT =
            Path.of("src/main/resources/templates/fragments/legal-documents.html");

    private static final List<Path> LANDINGS = List.of(
            Path.of("src/main/resources/templates/home/landingAmbarGourmet.html"),
            Path.of("src/main/resources/templates/home/landingRestaurant.html"),
            Path.of("src/main/resources/templates/home/landingQuintaElParaiso.html"));

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
        MockHttpServletResponse response = new MockHttpServletResponse();
        IWebExchange exchange = JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(request, response);
        return new WebContext(exchange);
    }

    private String render(List<Promotion> promotions) {
        AbstractContext context = webContext();
        context.setVariable("promotions", promotions);
        context.setVariable("accent", "#38e07b");
        return templateEngine.process("fragments/landing-promotions", context);
    }

    /** Friday 18:00–21:00: today the client may read it on a Wednesday afternoon. */
    private Promotion fridayNightPromotion() {
        Promotion promotion = Promotion.builder()
                .idPromotion(1L)
                .name("Noches de viernes 2x1")
                .description("Dos pizzas por el precio de una, solo los viernes por la noche.")
                .imageUrl("https://cdn.example.com/promo.jpg")
                .promotionType(PromotionType.BUY_X_PAY_Y)
                .buyQuantity(2)
                .payQuantity(1)
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 31))
                .validDays("FRIDAY")
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(21, 0))
                .active(true)
                .items(new ArrayList<>(List.of(
                        ItemMenu.builder().idItemMenu(1L).name("Pizza Margarita").build(),
                        ItemMenu.builder().idItemMenu(2L).name("Pizza Pepperoni").build(),
                        ItemMenu.builder().idItemMenu(3L).name("Pizza Hawaiana").build(),
                        ItemMenu.builder().idItemMenu(4L).name("Pizza Vegetariana").build())))
                .build();
        return promotion;
    }

    private Promotion allDayPromotion() {
        return Promotion.builder()
                .idPromotion(2L)
                .name("Café de la casa al 20%")
                .description("Todos los días, todo el día.")
                .promotionType(PromotionType.PERCENTAGE_DISCOUNT)
                .discountPercentage(new BigDecimal("20"))
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 31))
                .validDays("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY")
                .active(true)
                .items(new ArrayList<>())
                .build();
    }

    // ========== La sección ==========

    @Test
    void sectionAnnouncesEachPromotionWithItsWindowEvenOutsideThatWindow() {
        String html = render(List.of(fridayNightPromotion(), allDayPromotion()));

        assertTrue(html.contains("promo-showcase"), "the promotions section should render");
        assertTrue(html.contains("--promo-accent: #38e07b"), "the accent reaches the CSS variable");
        assertTrue(html.contains("promo-grid-couple"),
                "two promotions get the two-card layout instead of a stretched grid");
        assertEquals(2, count(html, "class=\"promo-tile\""), "one card per promotion");

        assertTrue(html.contains("Noches de viernes 2x1"));
        assertTrue(html.contains("2x1"), "the big label shows the offer");
        assertTrue(html.contains("Viernes · 18:00 a 21:00"),
                "the card must state the day and the time window, even when viewed on another day");
        assertTrue(html.contains("01/10/2026") && html.contains("31/10/2026"),
                "the validity dates are printed");
        assertTrue(html.contains("Café de la casa al 20%"));
        assertTrue(html.contains("20% OFF"));
        assertTrue(html.contains("Todos los días"),
                "a promotion without a time window applies all day");

        assertTrue(html.contains("Pizza Margarita"));
        assertTrue(html.contains("Pizza Pepperoni"));
        assertTrue(html.contains("Pizza Hawaiana"));
        assertTrue(html.contains("+1 más"), "the rest are summarized on the card");
        // The card lists only the first three items; the modal below lists all of
        // them so the customer knows exactly what the promotion includes.
        int cardStart = html.indexOf("class=\"promo-tile\"");
        String firstCard = html.substring(cardStart, html.indexOf("</article>", cardStart));
        assertFalse(firstCard.contains("Pizza Vegetariana"),
                "only the first three items fit on the card");
        assertTrue(html.contains("/home/menu"), "the card leads to the public menu");
    }

    @Test
    void withoutPromotionsNeitherTheSectionNorTheAnnouncementsRender() {
        String html = render(List.of());

        assertFalse(html.contains("promo-showcase"));
        assertFalse(html.contains("Ofertas para compartir"));
        assertFalse(html.contains("data-promo-announcements"));
    }

    // ========== Los anuncios ==========

    @Test
    void announcementsOpenWithOneSlidePerPromotionAndCarouselControls() {
        String html = render(List.of(fridayNightPromotion(), allDayPromotion()));

        assertTrue(html.contains("data-promo-announcements"), "the announcement modal is rendered");
        assertTrue(html.contains("data-promo-modal-dialog"));
        assertTrue(html.contains("hidden"), "it starts hidden until the JS opens it");
        assertEquals(2, count(html, "data-promo-slide=\""), "one slide per promotion");
        assertEquals(1, count(html, "promo-announcement is-active"),
                "only the first slide is visible");
        assertEquals(2, count(html, "data-promo-dot=\""), "one dot per promotion");
        assertTrue(html.contains("data-promo-nav"), "several promotions get the carousel controls");
        assertTrue(html.contains("data-promo-prev") && html.contains("data-promo-next"));
        assertTrue(html.contains("Promoción 1 de"));
        assertTrue(html.contains("Cerrar los anuncios"), "the X has an accessible label");

        // Every item of the promotion must also be listed inside the modal, not just
        // on the section card where only the first three fit.
        String modal = html.substring(html.indexOf("data-promo-announcements"));
        assertTrue(modal.contains("promo-announcement__items"));
        assertTrue(modal.contains("Artículos incluidos"));
        assertTrue(modal.contains("Pizza Margarita"));
        assertTrue(modal.contains("Pizza Pepperoni"));
        assertTrue(modal.contains("Pizza Hawaiana"));
        assertTrue(modal.contains("Pizza Vegetariana"),
                "the modal shows every included item, even the ones the card summarizes");
    }

    @Test
    void aSinglePromotionIsAnnouncedWithoutCarouselChrome() {
        String html = render(List.of(fridayNightPromotion()));

        assertEquals(1, count(html, "data-promo-slide=\""));
        assertTrue(html.contains("promo-grid-single"),
                "a single promotion gets its own centered layout");
        assertFalse(html.contains("data-promo-nav"), "no arrows for a single announcement");
        assertFalse(html.contains("data-promo-dot=\""), "no dots for a single announcement");
        assertFalse(html.contains("data-promo-counter"));
    }

    // ========== Contrato con las landings y el JavaScript ==========

    @Test
    void everyLandingUsesTheSharedFragmentAndItsAssets() throws IOException {
        for (Path landing : LANDINGS) {
            String html = Files.readString(landing, StandardCharsets.UTF_8);

            assertTrue(html.contains("fragments/landing-promotions :: promotions(${landingPromotions}"),
                    landing + " must render the shared promotions section");
            int carouselPosition = html.indexOf("id=\"restaurantCarousel\"");
            int promotionsPosition =
                    html.indexOf("fragments/landing-promotions :: promotions(${landingPromotions}");
            int historyPosition = html.indexOf("<h2 class=\"section-title\">Nuestra Historia</h2>");
            assertTrue(carouselPosition < promotionsPosition && promotionsPosition < historyPosition,
                    landing + " must place promotions after the carousel and before the history section");
            assertTrue(html.contains("fragments/landing-promotions :: announcements(${landingPromotions}"),
                    landing + " must render the announcements modal");
            assertTrue(html.contains("fragments/legal-documents :: dialogs"),
                    landing + " must render the privacy and terms dialogs");
            assertTrue(html.contains("data-bs-target=\"#privacyModal\"")
                            && html.contains("data-bs-target=\"#termsModal\""),
                    landing + " footer links must open their respective legal dialogs");
            assertTrue(html.contains("/css/landing-promotions.css"),
                    landing + " must load the promotions styles");
            assertTrue(html.contains("/js/landing-promotions.js"),
                    landing + " must load the announcements script");
            assertFalse(html.contains("promoCombo"),
                    landing + " must not keep the old three-card section");
            assertFalse(html.contains("class=\"promociones-section"),
                    landing + " must not keep the old promotions container");

            // The shared section must live inside the landing's dark container so it
            // inherits the same surface and the wave dividers of the other sections.
            assertTrue(html.indexOf("class=\"white-container\"")
                            < html.indexOf("fragments/landing-promotions :: promotions"),
                    landing + " must render the promotions inside the dark container");
        }
    }

    @Test
    void legalDialogsShowConfiguredBusinessContactAndCoverTheMainLegalTopics() {
        AbstractContext context = webContext();
        context.setVariable("systemConfig", SystemConfiguration.builder()
                .restaurantName("Quinta El Paraíso")
                .address("Domicilio de prueba")
                .email("privacidad@example.com")
                .phone("5512345678")
                .build());

        String html = templateEngine.process("fragments/legal-documents", context);

        assertTrue(html.contains("id=\"privacyModal\""));
        assertTrue(html.contains("id=\"termsModal\""));
        assertTrue(html.contains("Quinta El Paraíso"));
        assertTrue(html.contains("Domicilio de prueba"));
        assertTrue(html.contains("privacidad@example.com"));
        assertTrue(html.contains("5512345678"));
        assertTrue(html.contains("derechos ARCO"));
        assertTrue(html.contains("datos fiscales"));
        assertTrue(html.contains("18 años o más"));
        assertTrue(html.contains("Términos y condiciones"));
    }

    @Test
    void sectionUsesTheLandingSurfaceAndWavyDividersInsteadOfTheBlueGradient() throws IOException {
        String css = Files.readString(STYLES, StandardCharsets.UTF_8);

        assertFalse(css.contains("#10243a"), "the old blue gradient must be gone");
        assertFalse(css.contains("#0a1523"), "the old blue gradient must be gone");
        assertTrue(css.contains("var(--promo-surface-start"));
        assertTrue(css.contains("var(--promo-surface-end"));
        assertTrue(css.contains("--promo-wave-color"),
                "the wave colour is configurable per landing");
        assertTrue(css.contains(".promo-showcase::before"));
        assertTrue(css.contains(".promo-showcase::after"));
        assertTrue(css.contains("mask: url("),
                "the straight cuts are replaced by the same curved dividers as the landing");

        String ambar = Files.readString(LANDINGS.get(0), StandardCharsets.UTF_8);
        assertTrue(ambar.contains("--promo-wave-color: #123252"),
                "Ambar keeps its navy waves and cream surface instead of the generic blue");
    }

    @Test
    void theScriptOnlyReadsAttributesTheFragmentPrints() throws IOException {
        String fragment = Files.readString(FRAGMENT, StandardCharsets.UTF_8);
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        Matcher matcher = Pattern.compile("\\[(data-promo-[a-z-]+)\\]").matcher(script);
        List<String> selectors = new ArrayList<>();
        while (matcher.find()) {
            selectors.add(matcher.group(1));
        }

        assertFalse(selectors.isEmpty(), "the script should read its hooks by data attribute");
        assertTrue(selectors.contains("data-promo-announcements"));
        assertTrue(selectors.contains("data-promo-slide"));

        for (String selector : selectors) {
            assertTrue(fragment.contains(selector),
                    "the fragment must print " + selector + ", which the script expects");
        }
    }

    private static int count(String text, String needle) {
        int total = 0;
        int index = text.indexOf(needle);
        while (index != -1) {
            total++;
            index = text.indexOf(needle, index + needle.length());
        }
        return total;
    }
}
