package com.aatechsolutions.elgransazon.phase1;

import com.aatechsolutions.elgransazon.infrastructure.security.CustomAuthenticationFailureHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FASE 1 — Pruebas unitarias para CustomAuthenticationFailureHandler.
 *
 * Verifica que el handler redirija al formulario de login correcto
 * según de qué página provino el intento de login.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomAuthenticationFailureHandler — Redirección por error")
class CustomAuthenticationFailureHandlerTest {

    private CustomAuthenticationFailureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CustomAuthenticationFailureHandler();
    }

    @Test
    @DisplayName("Login fallido desde /client/login → redirige a /client/login?error=true")
    void clientLoginFailure_redirectsToClientLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Referer", "http://mybusiness.localhost:8080/client/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("Bad credentials"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/client/login?error=true");
    }

    @Test
    @DisplayName("Login fallido desde /login (empleado) → redirige a /login?error=true")
    void employeeLoginFailure_redirectsToEmployeeLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Referer", "http://mybusiness.localhost:8080/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("Bad credentials"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=true");
    }

    @Test
    @DisplayName("Sin Referer → redirige a /login?error=true por defecto")
    void noReferer_redirectsToDefaultLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // No Referer header
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("Bad credentials"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=true");
    }

    @Test
    @DisplayName("Referer vacío → redirige a /login?error=true")
    void emptyReferer_redirectsToDefaultLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Referer", "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new DisabledException("User disabled"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=true");
    }

    @Test
    @DisplayName("Referer con /client/login en URL de producción → redirige a /client/login?error=true")
    void productionClientLogin_redirectsToClientLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Referer", "https://pizzaplace.savorycloud.com/client/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("fail"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/client/login?error=true");
    }

    @Test
    @DisplayName("Referer con /login pero NO /client/login → redirige a /login?error=true")
    void employeeLoginRefererWithLoginPath_redirectsToEmployeeLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Referer", "http://myco.localhost:8080/login?error=true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("fail"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=true");
    }
}
