package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.BusinessHoursService;
import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.ComplementService;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.ImageStorageService;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.application.service.MenuPdfService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComplementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP que usa el formulario de platillos al crear o vincular una variante de tamaño.
 *
 * <p>La respuesta a "¿la variante adopta la receta del item padre?" viaja en el parámetro
 * {@code adoptRecipe}. Es el punto donde el JavaScript y el servidor se acoplan, así que si alguien
 * renombra el parámetro o cambia el valor por defecto el aviso de la pantalla deja de tener efecto
 * sin que nada más falle: por eso queda fijado aquí.</p>
 */
class MenuItemVariantRecipeAdoptionEndpointTest {

    private ItemMenuService itemMenuService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        itemMenuService = mock(ItemMenuService.class);

        CategoryService categoryService = mock(CategoryService.class);
        when(categoryService.getAllCategories()).thenReturn(List.of());

        IngredientService ingredientService = mock(IngredientService.class);
        when(ingredientService.findAll()).thenReturn(List.of());

        ItemMenuController controller = new ItemMenuController(
                itemMenuService,
                categoryService,
                ingredientService,
                mock(ImageStorageService.class),
                mock(ComplementService.class),
                mock(ItemMenuComplementRepository.class),
                mock(BusinessHoursService.class),
                mock(SystemConfigurationService.class),
                mock(MenuPdfService.class),
                mock(DateTimeService.class),
                new ObjectMapper());

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private ItemMenu sizeVariant() {
        return ItemMenu.builder()
                .idItemMenu(9L)
                .name("Pizza Grande")
                .sizeName("Grande")
                .price(new BigDecimal("250.00"))
                .requiresIngredients(false)
                .build();
    }

    @Test
    @DisplayName("al crear un tamaño la respuesta del aviso llega al servicio")
    void creatingASizeSendsTheAdoptionAnswer() throws Exception {
        when(itemMenuService.addSizeItem(eq(1L), any(), any(), eq(false))).thenReturn(sizeVariant());

        String body = mockMvc.perform(post("/admin/menu-items/1/size-items")
                        .param("sizeName", "Grande")
                        .param("price", "250.00")
                        .param("adoptRecipe", "false"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        verify(itemMenuService).addSizeItem(eq(1L), eq("Grande"), any(BigDecimal.class), eq(false));
        assertThat(body).contains("\"success\":true");
        assertThat(body).contains("\"recipeAdopted\":false");
    }

    @Test
    @DisplayName("un formulario viejo sin la respuesta sigue adoptando la receta")
    void anOldFormWithoutTheAnswerStillAdopts() throws Exception {
        when(itemMenuService.addSizeItem(eq(1L), any(), any(), eq(true))).thenReturn(sizeVariant());

        mockMvc.perform(post("/admin/menu-items/1/size-items")
                        .param("sizeName", "Grande")
                        .param("price", "250.00"))
                .andExpect(status().isOk());

        verify(itemMenuService).addSizeItem(eq(1L), eq("Grande"), any(BigDecimal.class), eq(true));
    }

    @Test
    @DisplayName("al vincular un item la respuesta del aviso también llega al servicio")
    void linkingAnItemSendsTheAdoptionAnswer() throws Exception {
        when(itemMenuService.linkExistingItemAsSize(eq(1L), any(), any(), eq(true))).thenReturn(sizeVariant());

        mockMvc.perform(post("/admin/menu-items/1/size-items/link")
                        .param("childId", "2")
                        .param("sizeName", "Familiar")
                        .param("adoptRecipe", "true"))
                .andExpect(status().isOk());

        verify(itemMenuService).linkExistingItemAsSize(eq(1L), eq(2L), eq("Familiar"), eq(true));
    }

    @Test
    @DisplayName("vincular sin respuesta no toca la receta del item")
    void linkingWithoutTheAnswerLeavesTheRecipeAlone() throws Exception {
        when(itemMenuService.linkExistingItemAsSize(eq(1L), any(), any(), any(Boolean.class)))
                .thenReturn(sizeVariant());

        mockMvc.perform(post("/admin/menu-items/1/size-items/link")
                        .param("childId", "2")
                        .param("sizeName", "Familiar"))
                .andExpect(status().isOk());

        verify(itemMenuService).linkExistingItemAsSize(eq(1L), eq(2L), eq("Familiar"), eq(false));
    }

    @Test
    @DisplayName("adoptar la receta del padre responde con las líneas copiadas")
    void adoptingTheParentRecipeAnswersWithTheCopiedLines() throws Exception {
        when(itemMenuService.adoptParentRecipe(5L)).thenReturn(sizeVariant());
        when(itemMenuService.getRecipe(5L)).thenReturn(List.of(
                com.aatechsolutions.elgransazon.domain.entity.ItemIngredient.builder()
                        .quantity(new BigDecimal("500")).build(),
                com.aatechsolutions.elgransazon.domain.entity.ItemIngredient.builder()
                        .quantity(new BigDecimal("300")).build()));

        String body = mockMvc.perform(post("/admin/menu-items/5/recipe/adopt-from-parent"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("\"success\":true");
        assertThat(body).contains("\"ingredients\":2");
    }

    @Test
    @DisplayName("si no se puede adoptar, la pantalla recibe el motivo")
    void aFailedAdoptionReportsWhy() throws Exception {
        when(itemMenuService.adoptParentRecipe(5L)).thenThrow(new IllegalArgumentException(
                "La variante 'Pizza Grande' ya tiene receta propia. Bórrala primero para reemplazarla "
                        + "por la del item padre."));

        String body = mockMvc.perform(post("/admin/menu-items/5/recipe/adopt-from-parent"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("\"success\":false");
        assertThat(body).contains("ya tiene receta propia");
    }
}
