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
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * La receta de un platillo viaja en un solo campo ({@code recipeJson}) en vez de tres campos
 * (ingredientIds / quantities / units) por ingrediente.
 *
 * <p>El motivo es el formulario multipart: Tomcat sólo acepta un número limitado de partes por
 * petición ({@code Connector.maxPartCount}, 50 por defecto), así que tres campos por fila hacían
 * que un platillo con ocho ingredientes fallara con un 500 al guardar. Aquí se comprueba el
 * cableado real del endpoint: el JSON, el formato viejo (que debe seguir funcionando) y los
 * errores que el usuario ve en pantalla.</p>
 */
class ItemMenuRecipeJsonTest {

    private ItemMenuService itemMenuService;
    private IngredientService ingredientService;
    private BusinessHoursService businessHoursService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        itemMenuService = mock(ItemMenuService.class);
        ingredientService = mock(IngredientService.class);
        businessHoursService = mock(BusinessHoursService.class);

        CategoryService categoryService = mock(CategoryService.class);
        when(categoryService.getAllCategories()).thenReturn(List.of());
        when(ingredientService.findAll()).thenReturn(List.of());
        when(itemMenuService.findAll()).thenReturn(List.of());
        when(businessHoursService.getAllBusinessHours()).thenReturn(List.of());

        ItemMenuController controller = new ItemMenuController(
                itemMenuService,
                categoryService,
                ingredientService,
                mock(ImageStorageService.class),
                mock(ComplementService.class),
                mock(ItemMenuComplementRepository.class),
                businessHoursService,
                mock(SystemConfigurationService.class),
                mock(MenuPdfService.class),
                mock(DateTimeService.class),
                new ObjectMapper());

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("un solo campo JSON lleva toda la receta")
    void theWholeRecipeTravelsInASingleJsonField() throws Exception {
        givenItemIsSaved();

        mockMvc.perform(post("/admin/menu-items")
                        .param("name", "Parrillada")
                        .param("price", "100.00")
                        .param("requiresIngredients", "true")
                        .param("recipeJson", """
                                [{"ingredientId":11,"quantity":50},
                                 {"ingredientId":12,"quantity":300.5},
                                 {"ingredientId":13,"quantity":0.25}]"""))
                .andExpect(status().is3xxRedirection());

        List<ItemIngredient> recipe = recipeSentToCreate();
        assertThat(recipe).hasSize(3);
        assertThat(recipe).extracting(row -> row.getIngredient().getIdIngredient())
                .containsExactly(11L, 12L, 13L);
        assertThat(recipe).extracting(ItemIngredient::getQuantity)
                .containsExactly(new BigDecimal("50"), new BigDecimal("300.5"), new BigDecimal("0.25"));
    }

    @Test
    @DisplayName("la unidad no viaja en el JSON: la pone el servicio desde el ingrediente")
    void theUnitIsNotSentAndStaysForTheService() throws Exception {
        givenItemIsSaved();

        mockMvc.perform(post("/admin/menu-items")
                        .param("name", "Parrillada")
                        .param("price", "100.00")
                        .param("requiresIngredients", "true")
                        .param("recipeJson", "[{\"ingredientId\":11,\"quantity\":50}]"))
                .andExpect(status().is3xxRedirection());

        assertThat(recipeSentToCreate()).allMatch(row -> row.getUnit() == null);
    }

    @Test
    @DisplayName("las filas sin cantidad se ignoran, como en el formato viejo")
    void rowsWithoutQuantityAreIgnored() throws Exception {
        givenItemIsSaved();

        mockMvc.perform(post("/admin/menu-items")
                        .param("name", "Parrillada")
                        .param("price", "100.00")
                        .param("requiresIngredients", "true")
                        .param("recipeJson", """
                                [{"ingredientId":11,"quantity":50},
                                 {"ingredientId":12,"quantity":0},
                                 {"ingredientId":13,"quantity":-1}]"""))
                .andExpect(status().is3xxRedirection());

        assertThat(recipeSentToCreate())
                .extracting(row -> row.getIngredient().getIdIngredient())
                .containsExactly(11L);
    }

    @Test
    @DisplayName("un formulario viejo que siga abierto sigue guardando su receta")
    void theOldThreeListsStillWork() throws Exception {
        givenItemIsSaved();

        mockMvc.perform(post("/admin/menu-items")
                        .param("name", "Parrillada")
                        .param("price", "100.00")
                        .param("requiresIngredients", "true")
                        .param("ingredientIds", "11", "12")
                        .param("quantities", "50", "300")
                        .param("units", "G", "G"))
                .andExpect(status().is3xxRedirection());

        List<ItemIngredient> recipe = recipeSentToCreate();
        assertThat(recipe).extracting(row -> row.getIngredient().getIdIngredient())
                .containsExactly(11L, 12L);
        assertThat(recipe).extracting(ItemIngredient::getUnit).containsExactly("G", "G");
    }

    @Test
    @DisplayName("si llegan las dos cosas manda el JSON")
    void jsonWinsOverTheOldFields() throws Exception {
        givenItemIsSaved();

        mockMvc.perform(post("/admin/menu-items")
                        .param("name", "Parrillada")
                        .param("price", "100.00")
                        .param("requiresIngredients", "true")
                        .param("recipeJson", "[{\"ingredientId\":13,\"quantity\":1}]")
                        .param("ingredientIds", "11")
                        .param("quantities", "50")
                        .param("units", "G"))
                .andExpect(status().is3xxRedirection());

        assertThat(recipeSentToCreate())
                .extracting(row -> row.getIngredient().getIdIngredient())
                .containsExactly(13L);
    }

    @Test
    @DisplayName("un platillo con receta no se guarda sin ingredientes")
    void anItemWithRecipeCannotBeSavedWithoutIngredients() throws Exception {
        mockMvc.perform(post("/admin/menu-items")
                        .param("name", "Parrillada")
                        .param("price", "100.00")
                        .param("requiresIngredients", "true")
                        .param("recipeJson", "[]"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/menu-items/form"))
                .andExpect(model().attribute("errorMessage", "Debe agregar al menos un ingrediente a la receta"));

        verify(itemMenuService, never()).create(any(ItemMenu.class), anyList());
    }

    @Test
    @DisplayName("un ingrediente repetido se rechaza con el mismo mensaje de siempre")
    void aRepeatedIngredientIsRejected() throws Exception {
        when(ingredientService.findById(11L)).thenReturn(Optional.of(Ingredient.builder()
                .idIngredient(11L).name("AGUACATE").unitOfMeasure("G").build()));

        mockMvc.perform(post("/admin/menu-items")
                        .param("name", "Parrillada")
                        .param("price", "100.00")
                        .param("requiresIngredients", "true")
                        .param("recipeJson", """
                                [{"ingredientId":11,"quantity":50},
                                 {"ingredientId":11,"quantity":80}]"""))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/menu-items/form"))
                .andExpect(model().attribute("errorMessage", containsString("AGUACATE")))
                .andExpect(model().attribute("errorMessage", containsString("repetido")));

        verify(itemMenuService, never()).create(any(ItemMenu.class), anyList());
    }

    @Test
    @DisplayName("una receta ilegible da un mensaje claro, no un 500")
    void aBrokenPayloadShowsAFriendlyMessage() throws Exception {
        mockMvc.perform(post("/admin/menu-items")
                        .param("name", "Parrillada")
                        .param("price", "100.00")
                        .param("requiresIngredients", "true")
                        .param("recipeJson", "esto-no-es-json"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/menu-items/form"))
                .andExpect(model().attribute("errorMessage", containsString("no tiene un formato válido")));

        verify(itemMenuService, never()).create(any(ItemMenu.class), anyList());
    }

    @Test
    @DisplayName("la edición también recibe la receta en el campo JSON")
    void updatingAnItemUsesTheJsonRecipe() throws Exception {
        ItemMenu updated = ItemMenu.builder().idItemMenu(7L).name("Parrillada").build();
        when(itemMenuService.update(eq(7L), any(ItemMenu.class), anyList())).thenReturn(updated);

        mockMvc.perform(post("/admin/menu-items/7")
                        .param("name", "Parrillada")
                        .param("price", "100.00")
                        .param("requiresIngredients", "true")
                        .param("recipeJson", """
                                [{"ingredientId":11,"quantity":50},
                                 {"ingredientId":12,"quantity":300}]"""))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<List<ItemIngredient>> captor = recipeCaptor();
        verify(itemMenuService).update(eq(7L), any(ItemMenu.class), captor.capture());
        assertThat(captor.getValue())
                .extracting(row -> row.getIngredient().getIdIngredient())
                .containsExactly(11L, 12L);
    }

    private void givenItemIsSaved() {
        ItemMenu created = ItemMenu.builder()
                .idItemMenu(99L)
                .name("Parrillada")
                .price(new BigDecimal("100.00"))
                .build();
        when(itemMenuService.create(any(ItemMenu.class), anyList())).thenReturn(created);
    }

    private List<ItemIngredient> recipeSentToCreate() {
        ArgumentCaptor<List<ItemIngredient>> captor = recipeCaptor();
        verify(itemMenuService).create(any(ItemMenu.class), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<ItemIngredient>> recipeCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
