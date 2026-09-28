package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.presentation.controller.SessionApiController;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El saludo de la pantalla verde del login muestra el nombre del empleado
 * ("Armando Arias"), no el usuario que se tecleó ("aarias"), porque ese dato
 * solo lo conoce el servidor. Y si no se puede resolver, la bienvenida nunca
 * debe saludar "null".
 */
class SessionWelcomeApiControllerTest {

    private static final String USERNAME = "aarias";

    private final EmployeeService employeeService = mock(EmployeeService.class);
    private final SessionApiController controller = new SessionApiController(employeeService);

    private Authentication authenticatedAs(String username) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(username);
        return authentication;
    }

    @Test
    void greetsTheEmployeeWithHisRealName() {
        when(employeeService.findByUsername(USERNAME))
                .thenReturn(Optional.of(Employee.builder()
                        .username(USERNAME)
                        .nombre("Armando")
                        .apellido("Arias")
                        .build()));

        ResponseEntity<java.util.Map<String, Object>> response =
                controller.welcome(authenticatedAs(USERNAME));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("fullName", "Armando Arias");
        assertThat(response.getBody()).containsEntry("username", USERNAME);
    }

    @Test
    void fallsBackToTheUsernameWhenTheAccountHasNoName() {
        when(employeeService.findByUsername(USERNAME))
                .thenReturn(Optional.of(Employee.builder().username(USERNAME).build()));

        assertThat(controller.welcome(authenticatedAs(USERNAME)).getBody())
                .containsEntry("fullName", USERNAME);
    }

    @Test
    void fallsBackToTheUsernameWhenTheEmployeeDoesNotExist() {
        when(employeeService.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThat(controller.welcome(authenticatedAs(USERNAME)).getBody())
                .containsEntry("fullName", USERNAME);
    }

    @Test
    void fallsBackToTheUsernameWhenTheLookupFails() {
        when(employeeService.findByUsername(USERNAME))
                .thenThrow(new RuntimeException("base de datos no disponible"));

        assertThat(controller.welcome(authenticatedAs(USERNAME)).getBody())
                .containsEntry("fullName", USERNAME);
    }

    @Test
    void rejectsRequestsWithoutSession() {
        Authentication anonymous = mock(Authentication.class);
        when(anonymous.isAuthenticated()).thenReturn(false);

        assertThat(controller.welcome(null).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.welcome(anonymous).getStatusCode().value()).isEqualTo(401);
    }
}
