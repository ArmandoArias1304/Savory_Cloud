package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemIngredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El formulario de platillos tal como lo recibe el navegador.
 *
 * <p>Lo que rompía el guardado: el formulario es multipart y Tomcat corta la petición cuando pasa
 * de {@code Connector.maxPartCount} partes (50 por defecto), así que con tres campos por ingrediente
 * un platillo de ocho ingredientes daba 500. Con la receta en un solo campo ({@code recipeJson}),
 * el número de partes ya no depende de cuántos ingredientes tenga el platillo: eso es lo que se
 * comprueba aquí comparando la página con 1 fila y con 20.</p>
 */
@SpringBootTest
@WithMockUser(username = "admin", roles = "ADMIN")
class MenuItemRecipeFormRenderTest {

    private static final Long ITEM_ID = 432L;
    private static final Long PARENT_ID = 431L;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private ItemMenuService itemMenuService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        CompanyContext.setCurrentCompany(Company.builder()
                .idCompany(2L)
                .slug("quinta")
                .name("Quinta El Paraíso")
                .build());
        when(itemMenuService.findAll()).thenReturn(List.of());
        when(itemMenuService.findById(ITEM_ID)).thenReturn(Optional.of(itemMenu()));
        when(itemMenuService.findSizeItems(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("el formulario envía las mismas partes con 1 ingrediente que con 20")
    void theFormDoesNotGrowWithTheRecipe() throws Exception {
        String withOneRow = renderForm(1);
        String withTwentyRows = renderForm(20);

        assertThat(partsOf(withTwentyRows))
                .as("el formulario crece con cada ingrediente: %d partes con 20 filas contra %d con 1",
                        partsOf(withTwentyRows), partsOf(withOneRow))
                .isEqualTo(partsOf(withOneRow));
        assertThat(withTwentyRows).contains("name=\"recipeJson\"");
        assertThat(partsOf(withOneRow)).as("el formulario debe enviar al menos una parte (la receta)")
                .isGreaterThan(1);
    }

    @Test
    @DisplayName("ninguna fila de la receta envía campos propios")
    void rowsDoNotSendTheirOwnFields() throws Exception {
        String html = renderForm(3);

        assertThat(html).doesNotContain("name=\"ingredientIds\"");
        assertThat(html).doesNotContain("name=\"quantities\"");
        assertThat(html).doesNotContain("name=\"units\"");
        // Las filas siguen ahí, sólo que ahora el JavaScript las lee por clase
        assertThat(html).contains("ingredient-select");
        assertThat(html).contains("ingredient-quantity");
        assertThat(html).contains("ingredient-unit");
    }

    @Test
    @DisplayName("el campo oculto sale con la receta que ya tenía el platillo")
    void theHiddenFieldCarriesTheStoredRecipe() throws Exception {
        String html = renderForm(2);

        String raw = attribute(html, "recipeJson");
        assertThat(raw).as("no se encontró el campo recipeJson en el formulario").isNotNull();

        JsonNode recipe = objectMapper.readTree(unescape(raw));
        assertThat(recipe).hasSize(2);
        assertThat(recipe.get(0).get("ingredientId").asLong()).isEqualTo(11L);
        assertThat(recipe.get(0).get("quantity").asDouble()).isEqualTo(50.0);
        assertThat(recipe.get(1).get("ingredientId").asLong()).isEqualTo(12L);
    }

    @Test
    @DisplayName("el formulario captura el tiempo promedio de preparación, que es opcional")
    void theFormCarriesTheOptionalPreparationTime() throws Exception {
        ItemMenu timed = itemMenu();
        timed.setPreparationTimeMinutes(15);
        when(itemMenuService.findById(ITEM_ID)).thenReturn(Optional.of(timed));

        String html = renderForm(0);

        assertThat(html).contains("name=\"preparationTimeMinutes\"");
        assertThat(html).contains("value=\"15\"");
        assertThat(html).contains("Tiempo promedio de preparación");
        assertThat(html).contains("opcional");
    }

    @Test
    @DisplayName("el listado del menú muestra el tiempo de preparación de cada platillo")
    void theListShowsThePreparationTimeColumn() throws Exception {
        ItemMenu timed = itemMenu();
        timed.setPreparationTimeMinutes(15);
        when(itemMenuService.searchMenuItemsPage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(timed)));

        String html = mockMvc.perform(get("/admin/menu-items"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("Tiempo prep.");
        assertThat(html).contains("15 min");
    }

    @Test
    @DisplayName("en una variante sin receta el formulario ofrece adoptar la del item padre")
    void variantFormOffersTheParentsRecipe() throws Exception {
        when(itemMenuService.findById(ITEM_ID)).thenReturn(Optional.of(variant()));
        when(itemMenuService.getRecipe(ITEM_ID)).thenReturn(List.of());
        when(itemMenuService.getRecipe(PARENT_ID)).thenReturn(recipeOf(2));

        String html = mockMvc.perform(get("/admin/menu-items/edit/" + ITEM_ID))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("VARIANT_PARENT_HAS_RECIPE = true");
        assertThat(html).contains("offerAdoptingParentRecipe");
        assertThat(html).contains("/recipe/adopt-from-parent");
    }

    @Test
    @DisplayName("un item que no es variante no ofrece adoptar la receta de nadie")
    void plainItemDoesNotOfferAdoption() throws Exception {
        String html = renderForm(2);

        assertThat(html).contains("VARIANT_PARENT_HAS_RECIPE = false");
    }

    @Test
    @DisplayName("al crear un tamaño se pregunta si adopta la receta del item padre")
    void theSizeQuestionIsWired() throws Exception {
        String html = renderForm(2);

        assertThat(html).contains("SIZE_PARENT_HAS_RECIPE = true");
        assertThat(html).contains("askAdoptParentRecipe");
        assertThat(html).contains("adoptRecipe:");
    }

    private String renderForm(int recipeRows) throws Exception {
        List<ItemIngredient> recipe = recipeOf(recipeRows);
        when(itemMenuService.getRecipe(ITEM_ID)).thenReturn(recipe);

        return mockMvc.perform(get("/admin/menu-items/edit/" + ITEM_ID))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private List<ItemIngredient> recipeOf(int rows) {
        List<ItemIngredient> recipe = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            boolean even = i % 2 == 0;
            recipe.add(ItemIngredient.builder()
                    .ingredient(Ingredient.builder()
                            .idIngredient(even ? 11L : 12L)
                            .name(even ? "AGUACATE" : "ARRACHERA DE RES")
                            .unitOfMeasure("G")
                            .currentStock(new BigDecimal("500"))
                            .build())
                    .quantity(new BigDecimal("50"))
                    .unit("G")
                    .build());
        }
        return recipe;
    }

    /** Una variante de tamaño: su item padre existe y tiene receta propia. */
    private ItemMenu variant() {
        return ItemMenu.builder()
                .idItemMenu(ITEM_ID)
                .name("Parrillada Grande")
                .sizeName("Grande")
                .parentItem(ItemMenu.builder()
                        .idItemMenu(PARENT_ID)
                        .name("Parrillada")
                        .price(new BigDecimal("450.00"))
                        .active(true)
                        .requiresIngredients(true)
                        .build())
                .price(new BigDecimal("550.00"))
                .active(true)
                .requiresIngredients(false)
                .category(Category.builder().idCategory(1L).name("Carnes").active(true).build())
                .build();
    }

    private ItemMenu itemMenu() {
        return ItemMenu.builder()
                .idItemMenu(ITEM_ID)
                .name("Parrillada")
                .description("Carne y mariscos")
                .price(new BigDecimal("450.00"))
                .active(true)
                .requiresIngredients(true)
                .category(Category.builder().idCategory(1L).name("Carnes").active(true).build())
                .build();
    }

    /** Partes que enviaría el multipart: un campo por cada {@code name="..."} dentro del form. */
    private int partsOf(String html) {
        int index = html.indexOf("<form");
        int end = html.indexOf("</form>", index);
        String form = html.substring(index, end);
        int count = 0;
        index = form.indexOf("name=\"");
        while (index >= 0) {
            count++;
            index = form.indexOf("name=\"", index + 6);
        }
        return count;
    }

    private String attribute(String html, String id) {
        Matcher matcher = Pattern.compile("<input[^>]*id=\"" + id + "\"[^>]*value=\"([^\"]*)\"").matcher(html);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String unescape(String value) {
        return value.replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
    }
}
