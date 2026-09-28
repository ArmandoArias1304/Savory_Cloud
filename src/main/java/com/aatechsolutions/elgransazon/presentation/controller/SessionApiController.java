package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Datos de la sesión que acaba de iniciarse.
 *
 * La pantalla verde de bienvenida del login saluda al usuario por su nombre, y
 * ese dato solo existe del lado del servidor: el formulario únicamente conoce el
 * usuario que se tecleó (p. ej. "aarias"), no el nombre del empleado
 * ("Armando Arias"). Si la cuenta no tiene nombre capturado se devuelve el
 * usuario tal cual, para que la pantalla nunca salude "null".
 */
@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
@Slf4j
public class SessionApiController {

    private final EmployeeService employeeService;

    /**
     * Nombre para la bienvenida del login. Requiere sesión autenticada: al no
     * haberla, Spring Security manda al login antes de llegar aquí.
     */
    @GetMapping("/welcome")
    public ResponseEntity<Map<String, Object>> welcome(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Sesión no iniciada"));
        }

        String username = authentication.getName();
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("username", username);
        body.put("fullName", resolveFullName(username));
        return ResponseEntity.ok(body);
    }

    private String resolveFullName(String username) {
        try {
            Employee employee = employeeService.findByUsername(username).orElse(null);
            if (employee == null) {
                return username;
            }

            String nombre = employee.getNombre() == null ? "" : employee.getNombre().trim();
            String apellido = employee.getApellido() == null ? "" : employee.getApellido().trim();
            String fullName = (nombre + " " + apellido).trim();
            return fullName.isEmpty() ? username : fullName;
        } catch (Exception e) {
            // Nunca se rompe la bienvenida por un dato de adorno
            log.warn("No se pudo resolver el nombre de {}: {}", username, e.getMessage());
            return username;
        }
    }
}
