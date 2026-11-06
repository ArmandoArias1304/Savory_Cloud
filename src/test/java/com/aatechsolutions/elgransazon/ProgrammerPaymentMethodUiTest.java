package com.aatechsolutions.elgransazon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgrammerPaymentMethodUiTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    @Test
    void renewalAndCompanyFormsAskForThePaymentMethod() throws IOException {
        String dashboard = Files.readString(
                TEMPLATES.resolve("programmer/dashboard.html"), StandardCharsets.UTF_8);

        assertTrue(dashboard.contains("name=\"paymentMethod\""),
                "el modal de renovación debe pedir el método de pago");
        assertTrue(dashboard.contains("${paymentMethodTypes}"),
                "las opciones del método de pago deben venir del enum");

        String companyForm = Files.readString(
                TEMPLATES.resolve("programmer/companies/form.html"), StandardCharsets.UTF_8);

        assertTrue(companyForm.contains("th:field=\"*{paymentMethod}\""),
                "el alta de empresa debe ligar el método de pago");
        assertTrue(companyForm.contains("${paymentMethodTypes}"),
                "las opciones del método de pago deben venir del enum");
    }
}
