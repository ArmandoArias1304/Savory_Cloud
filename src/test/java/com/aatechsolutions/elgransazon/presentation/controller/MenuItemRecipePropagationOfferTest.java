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
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemIngredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComplementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El aviso que ofrece copiar la receta del padre a sus variantes.
 *
 * <p>Un item padre con variantes puede estrenar su receta más tarde que ellas: en la base hay
 * variantes creadas cuando el padre todavía no tenía receta, y agregársela al padre no las llenaba
 * de nada. Guardar la primera receta del padre ahora deja el aviso listo, pero sólo en ese momento:
 * si el padre ya la tenía, empujarla en cada guardado borraría las cantidades que el admin ajustó
 * dentro de cada variante, que es justo el motivo de que las copias sean independientes.</p>
 */
class MenuItemRecipePropagationOfferTest {

    private static final Long ITEM_ID = 432L;

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

    private ItemMenu savedParent() {
        return ItemMenu.builder()
                .idItemMenu(ITEM_ID)
                .name("Parrillada")
                .price(new BigDecimal("100.00"))
                .active(true)
                .requiresIngredients(true)
                .isCombo(false)
                .build();
    }

    /** Las líneas que el padre acaba de guardar. */
    private List<ItemIngredient> parentRecipe() {
        return List.of(ItemIngredient.builder()
                .ingredient(Ingredient.builder().idIngredient(11L).name("AGUACATE").unitOfMeasure("G").build())
                .quantity(new BigDecimal("500"))
                .unit("G")
                .build());
    }

    /** Guarda el padre con la receta que el admin acaba de capturar en el formulario. */
    private ResultActions saveParentWithRecipe() throws Exception {
        when(itemMenuService.update(eq(ITEM_ID), any(ItemMenu.class), anyList())).thenReturn(savedParent());
        return mockMvc.perform(post("/admin/menu-items/" + ITEM_ID)
                .param("name", "Parrillada")
                .param("price", "100.00")
                .param("requiresIngredients", "true")
                .param("recipeJson", "[{\"ingredientId\":11,\"quantity\":500}]"));
    }

    /** Guarda el padre como un item que no lleva receta. */
    private ResultActions saveParentWithoutRecipe() throws Exception {
        when(itemMenuService.update(eq(ITEM_ID), any(ItemMenu.class), anyList())).thenReturn(savedParent());
        MockHttpServletRequestBuilder request = post("/admin/menu-items/" + ITEM_ID)
                .param("name", "Parrillada")
                .param("price", "100.00")
                .param("requiresIngredients", "false");
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("guardar la primera receta de un padre con variantes deja listo el aviso")
    void savingTheFirstRecipeOffersToCopyItToTheVariants() throws Exception {
        when(itemMenuService.getRecipe(ITEM_ID))
                .thenReturn(List.of())          // antes de guardar: el padre no tenía receta
                .thenReturn(parentRecipe());    // después de guardar: ya la tiene
        when(itemMenuService.countSizeVariantsWithoutRecipe(ITEM_ID)).thenReturn(3);

        saveParentWithRecipe()
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("offerRecipeToSizeVariants", 3))
                .andExpect(flash().attribute("offerRecipeToSizeVariantsItemId", ITEM_ID))
                .andExpect(flash().attribute("offerRecipeToSizeVariantsItemName", "Parrillada"));
    }

    @Test
    @DisplayName("editar la receta de un padre que ya la tenía no vuelve a preguntar")
    void editingAnExistingRecipeDoesNotAskAgain() throws Exception {
        when(itemMenuService.getRecipe(ITEM_ID)).thenReturn(parentRecipe());

        saveParentWithRecipe()
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("offerRecipeToSizeVariants", nullValue()));

        verify(itemMenuService, never()).countSizeVariantsWithoutRecipe(any());
    }

    @Test
    @DisplayName("si todas las variantes ya tienen su receta no hay nada que ofrecer")
    void nothingIsOfferedWhenEveryVariantHasItsOwnRecipe() throws Exception {
        when(itemMenuService.getRecipe(ITEM_ID))
                .thenReturn(List.of())
                .thenReturn(parentRecipe());
        when(itemMenuService.countSizeVariantsWithoutRecipe(ITEM_ID)).thenReturn(0);

        saveParentWithRecipe()
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("offerRecipeToSizeVariants", nullValue()));
    }

    @Test
    @DisplayName("guardar el padre sin receta no ofrece nada")
    void leavingTheItemWithoutRecipeOffersNothing() throws Exception {
        when(itemMenuService.getRecipe(ITEM_ID)).thenReturn(List.of());

        saveParentWithoutRecipe()
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("offerRecipeToSizeVariants", nullValue()));

        verify(itemMenuService, never()).countSizeVariantsWithoutRecipe(any());
    }

    @Test
    @DisplayName("el aviso confirmado copia la receta y responde cuántas variantes se llenaron")
    void confirmingTheOfferCopiesTheRecipe() throws Exception {
        when(itemMenuService.propagateRecipeToSizeVariants(ITEM_ID)).thenReturn(2);

        String body = mockMvc.perform(post("/admin/menu-items/" + ITEM_ID + "/recipe/propagate-to-sizes"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        verify(itemMenuService).propagateRecipeToSizeVariants(ITEM_ID);
        assertThat(body).contains("\"success\":true");
        assertThat(body).contains("\"copied\":2");
    }

    @Test
    @DisplayName("si no se puede copiar, la pantalla recibe el motivo")
    void aFailedPropagationReportsWhy() throws Exception {
        when(itemMenuService.propagateRecipeToSizeVariants(ITEM_ID)).thenThrow(new IllegalArgumentException(
                "El item 'Parrillada' no tiene receta que copiar a sus variantes."));

        String body = mockMvc.perform(post("/admin/menu-items/" + ITEM_ID + "/recipe/propagate-to-sizes"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("\"success\":false");
        assertThat(body).contains("no tiene receta que copiar");
    }
}
