package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El salvapantallas entra solo en todas las pantallas que usan el tema
 * (se engancha en {@code fragments/theme :: themeScript}) y muestra la marca del
 * restaurante que está en pantalla: logos, nombre, eslogan y "By SavoryCloud".
 */
@SpringBootTest
@WithMockUser(username = "admin", roles = "ADMIN")
class ScreensaverRenderTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private SystemConfigurationService systemConfigurationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        CompanyContext.setCurrentCompany(Company.builder()
                .idCompany(3L)
                .slug("elgransazon")
                .name("El Gran Sazón")
                .build());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("una pantalla del personal trae los dos logos, el nombre, el eslogan y By SavoryCloud")
    void staffScreensIncludeTheScreensaver() throws Exception {
        String html = render("/admin/inventory");
        SystemConfiguration config = systemConfigurationService.getConfiguration();

        assertThat(html).contains("id=\"savoryScreensaver\"");
        assertThat(html).contains("data-idle-minutes=\"5\"");
        assertThat(html).contains("sc-screen-dots");
        assertThat(countOf(html, "class=\"sc-screen-logo\"")).as("deben aparecer los dos logos").isEqualTo(2);
        assertThat(html).contains("sc-screen-name");
        assertThat(html).contains("sc-screen-by");
        assertThat(html).contains("SavoryCloud");
        assertThat(html).contains("Toca la pantalla o mueve el mouse para continuar");

        if (config != null && config.getRestaurantName() != null) {
            assertThat(html).contains(config.getRestaurantName());
        }
        if (config != null && config.getSlogan() != null && !config.getSlogan().isBlank()) {
            assertThat(html).contains(config.getSlogan());
        }
        if (config != null && config.getRestaurantLogoUrl() != null && !config.getRestaurantLogoUrl().isBlank()) {
            assertThat(html).contains("imagedelivery.net");
        }
    }

    @Test
    @DisplayName("la marca vive en el tema compartido, por eso la heredan todas las pantallas")
    void theBrandingLivesInTheSharedTheme() throws Exception {
        String tema = new String(new ClassPathResource("templates/fragments/theme.html")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(tema).contains("th:fragment=\"themeScript\"");
        assertThat(tema).contains("id=\"savoryScreensaver\"");
        assertThat(render("/admin/inventory/objects")).contains("id=\"savoryScreensaver\"");
    }

    @Test
    @WithAnonymousUser
    @DisplayName("el login público no trae salvapantallas")
    void publicPagesDoNotIncludeIt() throws Exception {
        String html = render("/login");

        assertThat(html).doesNotContain("id=\"savoryScreensaver\"");
        assertThat(html).doesNotContain("SavoryScreensaver");
    }

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private int countOf(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
