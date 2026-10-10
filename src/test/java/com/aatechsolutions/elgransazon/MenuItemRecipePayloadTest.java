package com.aatechsolutions.elgransazon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El formulario de platillos es multipart y Tomcat limita el número de partes por petición
 * ({@code Connector.maxPartCount}, 50 por defecto): al pasarse, el guardado fallaba con un 500 y
 * no había validación ni mensaje que lo explicara.
 *
 * <p>Por eso la receta viaja completa en un solo campo ({@code recipeJson}) y las filas dejaron de
 * aportar campos propios. Estas comprobaciones son el candado de esa decisión: si alguna fila
 * vuelve a enviar sus campos, el formulario crecería otra vez con cada ingrediente.</p>
 */
class MenuItemRecipePayloadTest {

    private static final String TEMPLATE = "templates/admin/menu-items/form.html";
    private static final String RECIPE_SECTION_START = "Ingredientes de la Receta";
    private static final String RECIPE_SECTION_END = "<!-- Botones de Acción -->";

    private String html;
    private String recipeSection;

    @BeforeEach
    void readTemplate() throws Exception {
        try (InputStream in = new ClassPathResource(TEMPLATE).getInputStream()) {
            html = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
        int start = html.indexOf(RECIPE_SECTION_START);
        int end = html.indexOf(RECIPE_SECTION_END);
        assertThat(start).as("no se encontró la sección de la receta").isGreaterThan(0);
        assertThat(end).as("no se encontró el final de la sección de la receta").isGreaterThan(start);
        recipeSection = html.substring(start, end);
    }

    @Test
    @DisplayName("la receta completa viaja en un solo campo del multipart")
    void theWholeRecipeTravelsInOneField() {
        assertThat(countOccurrences(recipeSection, "name=\"")).as(
                "la sección de la receta debe enviar exactamente una parte del multipart:\n" + recipeSection)
                .isEqualTo(1);
        assertThat(recipeSection).contains("name=\"recipeJson\"");
        assertThat(recipeSection).contains("th:value=\"${recipeJson}\"");
    }

    @Test
    @DisplayName("ninguna fila aporta campos propios al formulario")
    void rowsDoNotSendTheirOwnFields() {
        assertThat(html).doesNotContain("name=\"ingredientIds\"");
        assertThat(html).doesNotContain("name=\"quantities\"");
        assertThat(html).doesNotContain("name=\"units\"");
    }

    @Test
    @DisplayName("el JavaScript lee las filas por clase, no por name")
    void theScriptReadsRowsByClass() {
        assertThat(html).contains(".ingredient-select");
        assertThat(html).contains(".ingredient-quantity");
        assertThat(html).contains(".ingredient-unit");
        assertThat(html).doesNotContain("input[name=\"units\"]");
        assertThat(html).doesNotContain("select[name=\"ingredientIds\"]");
        assertThat(html).doesNotContain("input[name=\"quantities\"]");
    }

    @Test
    @DisplayName("al enviar, la receta se serializa en el campo oculto")
    void theRecipeIsSerializedOnSubmit() {
        assertThat(html).contains("function collectRecipeRows()");
        assertThat(html).contains("function syncRecipeJson()");
        assertThat(html).contains("syncRecipeJson();");
        assertThat(html).contains("if (!e.defaultPrevented)");
    }

    @Test
    @DisplayName("las filas se validan antes de enviar (sus campos ya no viajan solos)")
    void rowsAreValidatedBeforeSending() {
        assertThat(html).contains("function findIncompleteRecipeRow()");
        assertThat(html).contains("Cada ingrediente de la receta necesita estar seleccionado");
        assertThat(html).contains("está repetido en la receta");
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
