package com.aatechsolutions.elgransazon.phase1;

import com.aatechsolutions.elgransazon.infrastructure.security.CustomEmployeeDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FASE 1 — Pruebas unitarias para CustomEmployeeDetails.
 *
 * Verifica que la igualdad multi-tenant funcione correctamente:
 * dos empleados con el MISMO username pero en DISTINTAS companies
 * deben ser considerados usuarios DIFERENTES por Spring Security,
 * evitando que el login de uno invalide la sesión del otro.
 */
@DisplayName("CustomEmployeeDetails — Igualdad multi-tenant")
class CustomEmployeeDetailsTest {

    private static final Long COMPANY_1 = 1L;
    private static final Long COMPANY_2 = 2L;

    // ------------------- equals() -------------------

    @Test
    @DisplayName("Mismo username e igual companyId → son iguales")
    void sameUsernameAndCompany_areEqual() {
        var a = emp("admin", COMPANY_1, 10L);
        var b = emp("admin", COMPANY_1, 11L); // distinto employeeId no importa
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("Mismo username pero distinta companyId → NO son iguales (aislamiento multi-tenant)")
    void sameUsernameButDifferentCompany_areNotEqual() {
        var a = emp("admin", COMPANY_1, 10L);
        var b = emp("admin", COMPANY_2, 10L);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Username es case-insensitive: 'Admin' == 'admin' en la misma company")
    void usernameIsCaseInsensitive_sameCompany() {
        var a = emp("Admin", COMPANY_1, 10L);
        var b = emp("admin", COMPANY_1, 11L);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("Username case-insensitive: 'ADMIN@co1' != 'admin@co2' (distintas companies)")
    void usernameIsCaseInsensitive_differentCompany_stillNotEqual() {
        var a = emp("ADMIN", COMPANY_1, 10L);
        var b = emp("admin", COMPANY_2, 11L);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("PROGRAMMER (companyId=null) con mismo username → son iguales")
    void nullCompanyEmployees_withSameUsername_areEqual() {
        var a = emp("programmer", null, 1L);
        var b = emp("programmer", null, 2L);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("companyId=null vs companyId=1 → NO son iguales (PROGRAMMER vs empleado)")
    void nullCompanyVsNonNullCompany_areNotEqual() {
        var a = emp("user", null, 1L);
        var b = emp("user", COMPANY_1, 1L);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Misma instancia → es igual a sí misma")
    void sameInstance_isEqualToItself() {
        var a = emp("admin", COMPANY_1, 10L);
        assertThat(a).isEqualTo(a);
    }

    @Test
    @DisplayName("Comparación con null → falso")
    void compareWithNull_returnsFalse() {
        var a = emp("admin", COMPANY_1, 10L);
        assertThat(a.equals(null)).isFalse();
    }

    @Test
    @DisplayName("Comparación con tipo diferente → falso")
    void compareWithDifferentType_returnsFalse() {
        var a = emp("admin", COMPANY_1, 10L);
        assertThat(a.equals("some string")).isFalse();
    }

    // ------------------- hashCode() -------------------

    @Test
    @DisplayName("hashCode es consistente con equals: distintas companies → distintos hashCodes")
    void differentCompany_produceDifferentHashCodes() {
        var a = emp("admin", COMPANY_1, 10L);
        var b = emp("admin", COMPANY_2, 10L);
        // No garantizado matemáticamente, pero muy probable con Objects.hash()
        assertThat(a.hashCode()).isNotEqualTo(b.hashCode());
    }

    // ------------------- getters -------------------

    @Test
    @DisplayName("getCompanyId() y getEmployeeId() retornan valores correctos")
    void getters_returnCorrectValues() {
        var details = emp("admin", COMPANY_1, 42L);
        assertThat(details.getCompanyId()).isEqualTo(COMPANY_1);
        assertThat(details.getEmployeeId()).isEqualTo(42L);
        assertThat(details.getUsername()).isEqualTo("admin");
    }

    @Test
    @DisplayName("Empleado deshabilitado tiene enabled=false")
    void disabledEmployee_hasEnabledFalse() {
        var details = new CustomEmployeeDetails(
                "user", "pass", false,
                List.of(new SimpleGrantedAuthority("ROLE_WAITER")),
                COMPANY_1, 5L);
        assertThat(details.isEnabled()).isFalse();
    }

    // ------------------- helper -------------------

    private CustomEmployeeDetails emp(String username, Long companyId, Long employeeId) {
        return new CustomEmployeeDetails(
                username, "encoded-pass", true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                companyId, employeeId);
    }
}
