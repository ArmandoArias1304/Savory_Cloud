package com.aatechsolutions.elgransazon.phase1;

import com.aatechsolutions.elgransazon.infrastructure.security.CustomCustomerDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FASE 1 — Pruebas unitarias para CustomCustomerDetails.
 *
 * Verifica el aislamiento multi-tenant de sesiones de clientes:
 * el mismo email en dos companies distintas debe tratarse como
 * usuarios independientes en Spring Security.
 */
@DisplayName("CustomCustomerDetails — Igualdad multi-tenant")
class CustomCustomerDetailsTest {

    private static final Long COMPANY_1 = 1L;
    private static final Long COMPANY_2 = 2L;

    // ------------------- equals() -------------------

    @Test
    @DisplayName("Mismo username e igual companyId → son iguales")
    void sameUsernameAndCompany_areEqual() {
        var a = cust("john@example.com", COMPANY_1, 100L);
        var b = cust("john@example.com", COMPANY_1, 101L);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("Mismo email/username en distinta company → NO son iguales (CRÍTICO: sin esto hay bug de sesión)")
    void sameEmailDifferentCompany_areNotEqual() {
        var a = cust("john@example.com", COMPANY_1, 100L);
        var b = cust("john@example.com", COMPANY_2, 200L);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Username/email es case-insensitive: 'John@Example.com' == 'john@example.com' en misma company")
    void usernameCaseInsensitive_sameCompany() {
        var a = cust("John@Example.com", COMPANY_1, 100L);
        var b = cust("john@example.com", COMPANY_1, 101L);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("Mismo email case-insensitive, distintas companies → NO son iguales")
    void usernameCaseInsensitive_differentCompany_notEqual() {
        var a = cust("JOHN@EXAMPLE.COM", COMPANY_1, 100L);
        var b = cust("john@example.com", COMPANY_2, 200L);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Distintos emails, misma company → NO son iguales")
    void differentEmail_sameCompany_notEqual() {
        var a = cust("alice@example.com", COMPANY_1, 100L);
        var b = cust("bob@example.com", COMPANY_1, 101L);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Misma instancia → es igual a sí misma")
    void sameInstance_equalToItself() {
        var a = cust("user@co.com", COMPANY_1, 1L);
        assertThat(a).isEqualTo(a);
    }

    @Test
    @DisplayName("Comparación con null → falso")
    void compareWithNull_isFalse() {
        var a = cust("user@co.com", COMPANY_1, 1L);
        assertThat(a.equals(null)).isFalse();
    }

    @Test
    @DisplayName("hashCode distinto para distintas companies con mismo email")
    void differentCompany_differentHashCode() {
        var a = cust("john@example.com", COMPANY_1, 100L);
        var b = cust("john@example.com", COMPANY_2, 200L);
        assertThat(a.hashCode()).isNotEqualTo(b.hashCode());
    }

    // ------------------- getters -------------------

    @Test
    @DisplayName("getCompanyId() y getCustomerId() retornan valores correctos")
    void getters_returnCorrectValues() {
        var details = cust("alice@test.com", COMPANY_1, 99L);
        assertThat(details.getCompanyId()).isEqualTo(COMPANY_1);
        assertThat(details.getCustomerId()).isEqualTo(99L);
        assertThat(details.getUsername()).isEqualTo("alice@test.com");
    }

    @Test
    @DisplayName("Cliente inactivo tiene enabled=false")
    void inactiveCustomer_hasEnabledFalse() {
        var details = new CustomCustomerDetails(
                "inactive@co.com", "pass", false,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENT")),
                COMPANY_1, 5L);
        assertThat(details.isEnabled()).isFalse();
    }

    // ------------------- helper -------------------

    private CustomCustomerDetails cust(String email, Long companyId, Long customerId) {
        return new CustomCustomerDetails(
                email, "encoded-pass", true,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENT")),
                companyId, customerId);
    }
}
