package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.IngredientCategoryService;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.application.service.SupplierService;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.IngredientCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ir y volver entre una lista filtrada y el formulario, tal como lo vive el usuario en el navegador.
 *
 * <p>Se prueba con el interceptor real registrado en la aplicación (no con un servidor de prueba
 * armado a mano), porque lo que se quiere garantizar es justamente el cableado: que la lista
 * filtrada sea a la que se regresa después de guardar o activar, y que el botón "Volver" del
 * formulario apunte al mismo sitio.</p>
 */
@SpringBootTest
@WithMockUser(username = "admin", roles = "ADMIN")
class ListReturnAfterSaveIntegrationTest {

    private static final Long INGREDIENT_ID = 24L;
    private static final String FILTERED_LIST = "/admin/ingredients?search=tomate";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private IngredientService ingredientService;

    @MockitoBean
    private IngredientCategoryService categoryService;

    @MockitoBean
    private SupplierService supplierService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        when(ingredientService.searchWithAllFilters(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(ingredientService.countLowStock()).thenReturn(0L);
        when(ingredientService.countOutOfStock()).thenReturn(0L);
        when(ingredientService.getActiveCount()).thenReturn(0L);
        when(ingredientService.getInactiveCount()).thenReturn(0L);
        when(categoryService.findAllActive()).thenReturn(List.of());
        when(supplierService.findAllActive()).thenReturn(List.of());
        when(ingredientService.findById(INGREDIENT_ID)).thenReturn(Optional.of(activeIngredient()));
    }

    @Test
    @DisplayName("activar un ingrediente desde una lista filtrada regresa a esa misma lista")
    void activatingAnIngredientReturnsToTheFilteredList() throws Exception {
        MockHttpSession session = visitFilteredList();

        mockMvc.perform(post("/admin/ingredients/" + INGREDIENT_ID + "/activate").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(FILTERED_LIST));
    }

    @Test
    @DisplayName("el aviso de éxito sigue apareciendo al volver a la lista filtrada")
    void theSuccessMessageSurvivesTheReturnToTheFilteredList() throws Exception {
        MockHttpSession session = visitFilteredList();

        String location = mockMvc.perform(post("/admin/ingredients/" + INGREDIENT_ID + "/activate").session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(location).isEqualTo(FILTERED_LIST);

        mockMvc.perform(get(location).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Ingrediente activado exitosamente")));
    }

    @Test
    @DisplayName("el botón Volver del formulario apunta a la lista con su búsqueda")
    void theFormBackLinkPointsToTheFilteredList() throws Exception {
        MockHttpSession session = visitFilteredList();

        mockMvc.perform(get("/admin/ingredients/" + INGREDIENT_ID + "/edit")
                        .session(session)
                        .header("Referer", "http://localhost" + FILTERED_LIST))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(FILTERED_LIST)));
    }

    private MockHttpSession visitFilteredList() throws Exception {
        // La URL lleva la query de verdad: es lo que el interceptor recuerda para poder volver
        MvcResult result = mockMvc.perform(get(FILTERED_LIST))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private Ingredient activeIngredient() {
        return Ingredient.builder()
                .idIngredient(INGREDIENT_ID)
                .name("TOMATE")
                .unitOfMeasure("G")
                .currentStock(new BigDecimal("20"))
                .minStock(new BigDecimal("5"))
                .currency("MXN")
                .active(true)
                .category(IngredientCategory.builder().idCategory(7L).name("Verduras").build())
                .build();
    }
}
