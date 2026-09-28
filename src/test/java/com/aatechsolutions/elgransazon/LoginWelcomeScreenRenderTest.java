package com.aatechsolutions.elgransazon;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pantalla verde que aparece al iniciar sesión: saluda al usuario por su nombre
 * y lleva el eslogan de SavoryCloud hasta abajo. El nombre lo entrega el
 * endpoint /api/session/welcome, que además no debe contestar nada a quien no
 * tenga sesión.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginWelcomeScreenRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void theLoginPageCarriesTheWelcomeScreenWithTheSlogan() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"welcomeUserName\"")))
                .andExpect(content().string(containsString("id=\"welcomeSlogan\"")))
                .andExpect(content().string(containsString("Tú pones el sazón, nosotros la gestión")))
                .andExpect(content().string(containsString("id=\"welcomeSystemName\"")))
                // El nombre real lo trae el servidor, no el campo de texto
                .andExpect(content().string(containsString("/api/session/welcome")));
    }

    /**
     * La pantalla verde ya no lleva la palomita: ahora muestra el logo del
     * sistema (el que se configura en el panel del programador) y, si no hay
     * logo configurado, el logo estático de SavoryCloud.
     */
    @Test
    void theWelcomeScreenShowsTheSystemLogoInsteadOfTheCheckIcon() throws Exception {
        String html = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("class=\"welcome-logo\"");
        assertThat(html).doesNotContain("welcome-check");
    }

    /**
     * El HTML ya viene resuelto (logo configurado o no), así que el respaldo se
     * revisa en la plantilla: si no hay logo del sistema, se muestra el logo
     * estático de SavoryCloud.
     */
    @Test
    void theWelcomeLogoFallsBackToTheStaticSavoryCloudLogo() throws Exception {
        String template = new String(
                new ClassPathResource("templates/auth/login.html").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(template).contains("globalSystemConfig.systemLogoUrl");
        assertThat(template).contains("/images/savoryCloudLogo.png");
        assertThat(template).as("el círculo de la palomita ya no existe").doesNotContain("welcome-check");
    }

    @Test
    void theWelcomeEndpointNeedsASession() throws Exception {
        mockMvc.perform(get("/api/session/welcome"))
                .andExpect(status().is3xxRedirection());
    }

    /**
     * La vista previa de la plantilla llama a esa ruta exacta, así que un cambio
     * de nombre en el controlador dejaría el saludo sin nombre.
     */
    @Test
    void theWelcomeEndpointIsRegisteredWhereTheLoginCallsIt() {
        Set<String> paths = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPathPatternsCondition() == null
                        ? Stream.empty()
                        : info.getPathPatternsCondition().getPatternValues().stream())
                .collect(Collectors.toSet());

        assertThat(paths).contains("/api/session/welcome");
    }
}
