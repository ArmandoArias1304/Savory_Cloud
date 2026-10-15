package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.IngredientCategoryService;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.application.service.SupplierService;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.IngredientCategory;
import com.aatechsolutions.elgransazon.domain.repository.ComplementIngredientRepository;
import com.aatechsolutions.elgransazon.domain.repository.IngredientStockHistoryRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComboItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aviso que ve el usuario al cambiar la unidad de medida de un ingrediente.
 *
 * <p>El cambio re-etiqueta las recetas que usan el ingrediente, pero las cantidades no se convierten
 * (el sistema no conoce un factor de conversión). El aviso es la única señal que tiene el usuario de
 * que debe revisar esos números: si el mensaje desaparece, el cambio se vuelve silencioso y las
 * recetas quedan con cantidades que ya no significan lo mismo. Por eso se fija aquí.</p>
 */
class IngredientUnitChangeWarningTest {

    private static final Long INGREDIENT_ID = 24L;

    private IngredientService ingredientService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ingredientService = mock(IngredientService.class);

        IngredientCategoryService categoryService = mock(IngredientCategoryService.class);
        when(categoryService.findById(7L)).thenReturn(Optional.of(
                IngredientCategory.builder().idCategory(7L).name("Verduras").build()));

        IngredientController controller = new IngredientController(
                ingredientService,
                categoryService,
                mock(SupplierService.class),
                mock(EmployeeService.class),
                mock(ItemMenuService.class),
                mock(DateTimeService.class),
                mock(IngredientStockHistoryRepository.class),
                mock(ComplementIngredientRepository.class),
                mock(ItemMenuComboItemRepository.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(ingredientService.findById(INGREDIENT_ID)).thenReturn(Optional.of(Ingredient.builder()
                .idIngredient(INGREDIENT_ID)
                .name("TOMATE")
                .unitOfMeasure("UN")
                .currentStock(new BigDecimal("20"))
                .minStock(new BigDecimal("5"))
                .currency("MXN")
                .active(true)
                .build()));
    }

    @Test
    @DisplayName("cambiar la unidad de un ingrediente que está en recetas avisa que hay que revisar cantidades")
    void changingTheUnitOfAnIngredientUsedInRecipesWarnsAboutQuantities() throws Exception {
        when(ingredientService.countRecipeUsages(INGREDIENT_ID)).thenReturn(3);

        mockMvc.perform(updateRequest().param("unitOfMeasure", "G"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/ingredients"))
                .andExpect(flash().attribute("successMessage", containsString("actualizado exitosamente")))
                .andExpect(flash().attribute("warningMessage", containsString("3 recetas")))
                .andExpect(flash().attribute("warningMessage", containsString("no se convierten")));
    }

    @Test
    @DisplayName("el aviso habla en singular cuando la usa una sola receta")
    void theWarningUsesTheSingularForOneRecipe() throws Exception {
        when(ingredientService.countRecipeUsages(INGREDIENT_ID)).thenReturn(1);

        mockMvc.perform(updateRequest().param("unitOfMeasure", "G"))
                .andExpect(flash().attribute("warningMessage", containsString("1 receta ")));
    }

    @Test
    @DisplayName("si la unidad no cambia no hay nada que revisar y no se avisa")
    void keepingTheSameUnitDoesNotWarn() throws Exception {
        mockMvc.perform(updateRequest().param("unitOfMeasure", "UN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("warningMessage", nullValue()));
    }

    @Test
    @DisplayName("si ninguna receta usa el ingrediente no se avisa")
    void anIngredientOutsideEveryRecipeDoesNotWarn() throws Exception {
        when(ingredientService.countRecipeUsages(INGREDIENT_ID)).thenReturn(0);

        mockMvc.perform(updateRequest().param("unitOfMeasure", "G"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("warningMessage", nullValue()));
    }

    /** Petición como la que manda el formulario de edición, con la unidad por decidir. */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder updateRequest() {
        return post("/admin/ingredients/" + INGREDIENT_ID)
                .param("name", "TOMATE")
                .param("categoryId", "7")
                .param("currentStock", "20")
                .param("minStock", "5")
                .param("currency", "MXN")
                .param("active", "true");
    }
}
