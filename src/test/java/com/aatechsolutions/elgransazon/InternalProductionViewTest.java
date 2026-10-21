package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.application.service.ItemPreparationService;
import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemIngredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.ItemPreparation;
import com.aatechsolutions.elgransazon.domain.entity.PreparationStatus;
import org.springframework.security.authorization.AuthorizationDeniedException;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las pantallas de producción interna tal como las recibe el navegador.
 *
 * <p>Se renderiza la plantilla real: un error de SpEL o un atributo mal escrito en Thymeleaf no
 * rompe la compilación, sólo la página, así que la única forma de saber que la pantalla existe es
 * pedirla. También se fija quién puede entrar: admin, gerente y chef sí; mesero y cajero no, porque
 * un platillo interno nunca se vende.</p>
 */
@SpringBootTest
@WithMockUser(username = "admin", roles = "ADMIN")
class InternalProductionViewTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private ItemPreparationService preparationService;

    @MockitoBean
    private ItemMenuService itemMenuService;

    @MockitoBean
    private IngredientService ingredientService;

    @MockitoBean
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        when(preparationService.findInternalItems()).thenReturn(List.of(internalItem()));
        when(preparationService.findTodayPreparations()).thenReturn(List.of(prepared(2)));
        when(preparationService.searchPreparations(any(), any(), anyInt()))
                .thenReturn(List.of(prepared(2), cancelled(4)));
        when(preparationService.totalProducedQuantity(any())).thenReturn(2);
        when(preparationService.totalEstimatedCost(any())).thenReturn(new BigDecimal("40.00"));
        when(itemMenuService.getMaxAvailableQuantity(7L)).thenReturn(12);

        when(categoryService.getAllActiveCategories())
                .thenReturn(List.of(Category.builder().idCategory(1L).name("Buffet").active(true).build()));
        when(ingredientService.findAll())
                .thenReturn(List.of(Ingredient.builder().idIngredient(11L).name("Tomate")
                        .unitOfMeasure("KG").build()));
    }

    @Test
    @DisplayName("el listado muestra los platillos internos y el botón de registrar producción")
    void theListShowsInternalItemsAndTheRegisterButton() throws Exception {
        mockMvc.perform(get("/internal-production"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Producción interna")))
                .andExpect(content().string(containsString("Guisado del buffet")))
                .andExpect(content().string(containsString("Registrar producción")))
                .andExpect(content().string(containsString("Alcanza para")))
                .andExpect(content().string(containsString("12")));
    }

    @Test
    @DisplayName("el resumen muestra el platillo que más porciones movió, no el costo")
    void theSummaryShowsTheTopDishInsteadOfTheCost() throws Exception {
        mockMvc.perform(get("/internal-production"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Platillo más preparado hoy")))
                .andExpect(content().string(containsString("Guisado del buffet")))
                .andExpect(content().string(containsString("2 porciones")))
                .andExpect(content().string(not(containsString("Costo estimado de ingredientes hoy"))));
    }

    @Test
    @DisplayName("el historial distingue una preparación vigente de una cancelada")
    void theHistoryDistinguishesPreparedFromCancelled() throws Exception {
        mockMvc.perform(get("/internal-production"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Preparada")))
                .andExpect(content().string(containsString("Cancelada")))
                .andExpect(content().string(containsString("no se consumió")));
    }

    @Test
    @DisplayName("cada lista tiene su propio buscador y aplicar uno no borra el otro")
    void eachListHasItsOwnIndependentSearch() throws Exception {
        when(preparationService.findInternalItems()).thenReturn(List.of(internalItem(), camarones()));
        when(preparationService.searchPreparations("guisado", PreparationStatus.CANCELLED, 40))
                .thenReturn(List.of());

        mockMvc.perform(get("/internal-production")
                        .param("itemQuery", "camaron")
                        .param("historyQuery", "guisado")
                        .param("historyStatus", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"itemQuery\"")))
                .andExpect(content().string(containsString("value=\"camaron\"")))
                .andExpect(content().string(containsString("name=\"historyQuery\"")))
                .andExpect(content().string(containsString("value=\"guisado\"")))
                .andExpect(content().string(containsString("(1 de 2)")))
                .andExpect(content().string(containsString("Camarones a la diabla")))
                // Solo la tarjeta de Camarones sobrevive al filtro (su input es qty-8);
                // la de Guisado (qty-7) desaparece de la lista.
                .andExpect(content().string(containsString("id=\"qty-8\"")))
                .andExpect(content().string(not(containsString("id=\"qty-7\""))))
                .andExpect(content().string(containsString("Ninguna preparación coincide con los filtros.")));
    }

    @Test
    @DisplayName("sin coincidencias se explica el resultado vacío en vez de dejar la sección muda")
    void noMatchingItemShowsAnExplanation() throws Exception {
        mockMvc.perform(get("/internal-production").param("itemQuery", "pizza"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ningún platillo interno coincide con")))
                .andExpect(content().string(containsString("«pizza»")));
    }

    @Test
    @DisplayName("el filtro del historial no oculta los platillos internos")
    void historyFilterLeavesTheItemsListVisible() throws Exception {
        when(preparationService.searchPreparations(null, PreparationStatus.CANCELLED, 40))
                .thenReturn(List.of(cancelled(4)));

        mockMvc.perform(get("/internal-production").param("historyStatus", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Guisado del buffet")))
                .andExpect(content().string(containsString("Registrar producción")))
                .andExpect(content().string(containsString("no se consumió")));
    }

    @Test
    @DisplayName("el formulario pinta la receta con los ingredientes del inventario")
    void theFormRendersTheRecipeEditor() throws Exception {
        mockMvc.perform(get("/internal-production/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Receta (por una porción)")))
                .andExpect(content().string(containsString("Tomate (KG)")))
                .andExpect(content().string(containsString("Crear platillo")));
    }

    @Test
    @DisplayName("editar pinta la receta guardada con su índice de binding")
    void theEditFormRendersTheSavedRecipe() throws Exception {
        when(preparationService.findInternalItem(7L)).thenReturn(java.util.Optional.of(internalItem()));
        when(itemMenuService.getRecipe(7L)).thenReturn(List.of(
                ItemIngredient.builder()
                        .ingredient(Ingredient.builder().idIngredient(11L).name("Tomate")
                                .unitOfMeasure("KG").build())
                        .quantity(new BigDecimal("0.25"))
                        .unit("KG")
                        .build()));

        mockMvc.perform(get("/internal-production/7/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Editar platillo interno")))
                .andExpect(content().string(containsString("recipe[0].ingredientId")))
                .andExpect(content().string(containsString("0.25")));
    }

    @Test
    @WithMockUser(username = "chef1", roles = "CHEF")
    @DisplayName("el chef entra al módulo desde su propio panel")
    void theChefCanOpenTheModule() throws Exception {
        mockMvc.perform(get("/internal-production"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Volver al panel")));
    }

    @Test
    @WithMockUser(username = "mesero1", roles = "WAITER")
    @DisplayName("un mesero no puede entrar: los platillos internos no se venden")
    void aWaiterCannotOpenTheModule() {
        // La ruta está además cerrada en SecurityConfig; aquí se fija el guardia del propio
        // controlador, que es el que sigue en pie aunque alguien cambie las reglas de URL.
        assertThatThrownBy(() -> mockMvc.perform(get("/internal-production")))
                .hasRootCauseInstanceOf(AuthorizationDeniedException.class);
    }

    // ========== Helpers ==========

    private ItemMenu internalItem() {
        return ItemMenu.builder()
                .idItemMenu(7L)
                .name("Guisado del buffet")
                .price(BigDecimal.ZERO)
                .internalOnly(true)
                .active(true)
                .category(Category.builder().idCategory(1L).name("Buffet").build())
                .build();
    }

    private ItemMenu camarones() {
        return ItemMenu.builder()
                .idItemMenu(8L)
                .name("Camarones a la diabla")
                .price(BigDecimal.ZERO)
                .internalOnly(true)
                .active(true)
                .category(Category.builder().idCategory(1L).name("Buffet").build())
                .build();
    }

    private ItemPreparation prepared(int quantity) {
        return ItemPreparation.builder()
                .idPreparation(1L)
                .itemMenu(internalItem())
                .quantity(quantity)
                .status(PreparationStatus.PREPARED)
                .stockReturned(false)
                .preparedAt(LocalDateTime.of(2026, 10, 19, 13, 30))
                .build();
    }

    private ItemPreparation cancelled(int quantity) {
        return ItemPreparation.builder()
                .idPreparation(2L)
                .itemMenu(internalItem())
                .quantity(quantity)
                .status(PreparationStatus.CANCELLED)
                .stockReturned(true)
                .cancelReason("no se consumió")
                .preparedAt(LocalDateTime.of(2026, 10, 19, 11, 0))
                .build();
    }
}
