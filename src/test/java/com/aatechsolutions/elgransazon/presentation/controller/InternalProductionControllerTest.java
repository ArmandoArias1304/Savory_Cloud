package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.application.service.ItemPreparationService;
import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.ItemPreparation;
import com.aatechsolutions.elgransazon.domain.entity.PreparationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.AbstractView;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * El registro de producción sin pedido: qué se manda al servicio y qué se le dice al usuario.
 *
 * <p>El módulo no crea órdenes, así que lo único que puede salir mal en el controlador es perder
 * la cantidad capturada (registrar 1 cuando se escribieron 8), tragarse el error de stock o no
 * devolver al listado. Eso es lo que se fija aquí.</p>
 */
class InternalProductionControllerTest {

    private MockMvc mockMvc;
    private ItemPreparationService preparationService;
    private ItemMenuService itemMenuService;
    private IngredientService ingredientService;
    private CategoryService categoryService;
    private EmployeeService employeeService;

    @BeforeEach
    void setUp() {
        preparationService = mock(ItemPreparationService.class);
        itemMenuService = mock(ItemMenuService.class);
        ingredientService = mock(IngredientService.class);
        categoryService = mock(CategoryService.class);
        employeeService = mock(EmployeeService.class);

        InternalProductionController controller = new InternalProductionController(
                preparationService, itemMenuService, ingredientService, categoryService, employeeService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                // Las aserciones leen el modelo y la redirección; el HTML no se renderiza aquí.
                .setViewResolvers((viewName, locale) -> noOpView(viewName))
                .build();

        when(employeeService.findByUsername(anyString())).thenReturn(Optional.of(employee()));
    }

    @Test
    @DisplayName("registrar producción manda la cantidad capturada y vuelve al listado")
    void registeringProductionSendsTheCapturedQuantity() throws Exception {
        ItemPreparation saved = preparation(8);
        when(preparationService.registerPreparation(eq(7L), eq(8), any())).thenReturn(saved);

        mockMvc.perform(post("/internal-production/7/prepare")
                        .param("quantity", "8")
                        .principal(chef()))
                .andExpect(view().name("redirect:/internal-production"))
                .andExpect(flash().attribute("successMessage", containsString("8")));

        verify(preparationService).registerPreparation(eq(7L), eq(8), any());
    }

    @Test
    @DisplayName("sin cantidad se registra una sola porción")
    void withoutQuantityItRegistersOnePortion() throws Exception {
        when(preparationService.registerPreparation(eq(7L), eq(1), any())).thenReturn(preparation(1));

        mockMvc.perform(post("/internal-production/7/prepare").principal(chef()))
                .andExpect(view().name("redirect:/internal-production"));

        verify(preparationService).registerPreparation(eq(7L), eq(1), any());
    }

    @Test
    @DisplayName("si no alcanza el stock el usuario ve el motivo y no se registra nada")
    void insufficientStockIsShownToTheUser() throws Exception {
        when(preparationService.registerPreparation(eq(7L), eq(3), any()))
                .thenThrow(new IllegalStateException(
                        "No hay stock suficiente para preparar 3 de 'Guisado del buffet': Tomate (faltan 2.00 KG)"));

        mockMvc.perform(post("/internal-production/7/prepare")
                        .param("quantity", "3")
                        .principal(chef()))
                .andExpect(view().name("redirect:/internal-production"))
                .andExpect(flash().attribute("errorMessage", containsString("faltan 2.00 KG")));
    }

    @Test
    @DisplayName("cancelar devuelve el motivo y confirma que el stock volvió")
    void cancellingReturnsTheReasonAndConfirmsTheStockIsBack() throws Exception {
        when(preparationService.cancelPreparation(eq(99L), any(), eq("no se consumió")))
                .thenReturn(cancelledPreparation());

        mockMvc.perform(post("/internal-production/preparations/99/cancel")
                        .param("reason", "no se consumió")
                        .principal(chef()))
                .andExpect(view().name("redirect:/internal-production"))
                .andExpect(flash().attribute("successMessage", containsString("volvieron al inventario")));

        verify(preparationService).cancelPreparation(eq(99L), any(), eq("no se consumió"));
    }

    @Test
    @DisplayName("cancelar un lote ya cancelado no rompe la pantalla")
    void cancellingTwiceOnlyShowsAnError() throws Exception {
        when(preparationService.cancelPreparation(eq(99L), any(), any()))
                .thenThrow(new IllegalStateException("Esta preparación ya fue cancelada."));

        mockMvc.perform(post("/internal-production/preparations/99/cancel").principal(chef()))
                .andExpect(view().name("redirect:/internal-production"))
                .andExpect(flash().attribute("errorMessage", "Esta preparación ya fue cancelada."));
    }

    @Test
    @DisplayName("el listado lleva platillos, resumen del día e historial")
    void theListCarriesItemsSummaryAndHistory() throws Exception {
        ItemMenu item = internalItem();
        when(preparationService.findInternalItems()).thenReturn(List.of(item));
        when(preparationService.findTodayPreparations()).thenReturn(List.of(preparation(2)));
        when(preparationService.searchPreparations(null, null, 40)).thenReturn(List.of(preparation(2)));
        when(preparationService.totalProducedQuantity(any())).thenReturn(2);
        when(preparationService.totalEstimatedCost(any())).thenReturn(new BigDecimal("40.00"));
        when(itemMenuService.getMaxAvailableQuantity(7L)).thenReturn(12);

        MvcResult result = mockMvc.perform(get("/internal-production").principal(chef()))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        assertThat(result.getModelAndView().getViewName()).isEqualTo("internal-production/list");
        assertThat(model.get("items")).isEqualTo(List.of(item));
        assertThat(model.get("todayQuantity")).isEqualTo(2);
        // Producción interna cuelga del Menú, no es un módulo propio del sidebar.
        assertThat(model.get("activeMenu")).isEqualTo("menu");
        @SuppressWarnings("unchecked")
        Map<Long, Integer> maxPortions = (Map<Long, Integer>) model.get("maxPortions");
        assertThat(maxPortions.get(7L)).isEqualTo(12);
    }

    @Test
    @DisplayName("el platillo más preparado del día ignora los lotes cancelados")
    void theTopDishOfTheDayIgnoresCancelledBatches() throws Exception {
        ItemMenu camarones = ItemMenu.builder()
                .idItemMenu(8L)
                .name("Camarones a la diabla")
                .price(BigDecimal.ZERO)
                .internalOnly(true)
                .build();
        ItemPreparation preparedGuisado = preparation(3);
        ItemPreparation cancelledCamarones = ItemPreparation.builder()
                .idPreparation(50L)
                .itemMenu(camarones)
                .quantity(9)
                .status(PreparationStatus.CANCELLED)
                .stockReturned(true)
                .build();

        when(preparationService.findInternalItems()).thenReturn(List.of(internalItem(), camarones));
        when(preparationService.findTodayPreparations())
                .thenReturn(List.of(preparedGuisado, cancelledCamarones));
        when(preparationService.searchPreparations(null, null, 40)).thenReturn(List.of());
        when(preparationService.totalProducedQuantity(any())).thenReturn(3);
        when(itemMenuService.getMaxAvailableQuantity(anyLong())).thenReturn(1);

        MvcResult result = mockMvc.perform(get("/internal-production").principal(chef()))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        assertThat(model.get("topItemName")).isEqualTo("Guisado del buffet");
        assertThat(model.get("topItemPortions")).isEqualTo(3);
        assertThat(model).doesNotContainKey("todayCost");
    }

    @Test
    @DisplayName("los filtros de platillos y de historial son independientes")
    void itemAndHistoryFiltersAreIndependent() throws Exception {
        ItemMenu guisado = internalItem();
        ItemMenu camarones = ItemMenu.builder()
                .idItemMenu(8L)
                .name("Camarones a la diabla")
                .price(BigDecimal.ZERO)
                .internalOnly(true)
                .build();

        when(preparationService.findInternalItems()).thenReturn(List.of(guisado, camarones));
        when(preparationService.findTodayPreparations()).thenReturn(List.of());
        when(preparationService.searchPreparations("guisado", PreparationStatus.CANCELLED, 40))
                .thenReturn(List.of());
        when(preparationService.totalProducedQuantity(any())).thenReturn(0);
        when(preparationService.totalEstimatedCost(any())).thenReturn(BigDecimal.ZERO);
        when(itemMenuService.getMaxAvailableQuantity(anyLong())).thenReturn(3);

        MvcResult result = mockMvc.perform(get("/internal-production")
                        .param("itemQuery", "camaron")
                        .param("historyQuery", "guisado")
                        .param("historyStatus", "cancelled")
                        .principal(chef()))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        @SuppressWarnings("unchecked")
        List<ItemMenu> items = (List<ItemMenu>) model.get("items");
        assertThat(items).extracting(ItemMenu::getName).containsExactly("Camarones a la diabla");
        assertThat(model.get("allItemsCount")).isEqualTo(2);
        assertThat(model.get("itemQuery")).isEqualTo("camaron");
        assertThat(model.get("historyQuery")).isEqualTo("guisado");
        assertThat(model.get("historyStatus")).isEqualTo("CANCELLED");
        assertThat(model.get("historyFiltersActive")).isEqualTo(true);
        verify(preparationService).searchPreparations("guisado", PreparationStatus.CANCELLED, 40);
    }

    @Test
    @DisplayName("filtrar el historial no recorta los platillos internos")
    void historyFilterLeavesItemsAlone() throws Exception {
        ItemMenu guisado = internalItem();
        ItemMenu camarones = ItemMenu.builder()
                .idItemMenu(8L)
                .name("Camarones a la diabla")
                .price(BigDecimal.ZERO)
                .internalOnly(true)
                .build();

        when(preparationService.findInternalItems()).thenReturn(List.of(guisado, camarones));
        when(preparationService.findTodayPreparations()).thenReturn(List.of());
        when(preparationService.searchPreparations("ana", null, 40)).thenReturn(List.of());
        when(preparationService.totalProducedQuantity(any())).thenReturn(0);
        when(preparationService.totalEstimatedCost(any())).thenReturn(BigDecimal.ZERO);
        when(itemMenuService.getMaxAvailableQuantity(anyLong())).thenReturn(3);

        MvcResult result = mockMvc.perform(get("/internal-production")
                        .param("historyQuery", "ana")
                        .principal(chef()))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        @SuppressWarnings("unchecked")
        List<ItemMenu> items = (List<ItemMenu>) model.get("items");
        assertThat(items).hasSize(2);
        assertThat(model.get("itemQuery")).isEqualTo("");
        assertThat(model.get("historyStatus")).isEqualTo("");
    }

    @Test
    @DisplayName("un estado desconocido se ignora en vez de romper el listado")
    void anUnknownStatusIsIgnored() throws Exception {
        when(preparationService.findInternalItems()).thenReturn(List.of());
        when(preparationService.findTodayPreparations()).thenReturn(List.of());
        when(preparationService.searchPreparations(null, null, 40)).thenReturn(List.of());
        when(preparationService.totalProducedQuantity(any())).thenReturn(0);
        when(preparationService.totalEstimatedCost(any())).thenReturn(BigDecimal.ZERO);

        MvcResult result = mockMvc.perform(get("/internal-production")
                        .param("historyStatus", "BORRADO")
                        .principal(chef()))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        assertThat(model.get("historyStatus")).isEqualTo("");
        assertThat(model.get("historyFiltersActive")).isEqualTo(false);
        verify(preparationService).searchPreparations(null, null, 40);
    }

    @Test
    @DisplayName("el formulario nuevo trae categorías e ingredientes para la receta")
    void theNewFormCarriesCategoriesAndIngredients() throws Exception {
        when(categoryService.getAllActiveCategories())
                .thenReturn(List.of(Category.builder().idCategory(1L).name("Buffet").active(true).build()));
        when(ingredientService.findAll())
                .thenReturn(List.of(Ingredient.builder().idIngredient(11L).name("Tomate")
                        .unitOfMeasure("KG").build()));

        MvcResult result = mockMvc.perform(get("/internal-production/new").principal(chef()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getModelAndView().getViewName()).isEqualTo("internal-production/form");
        assertThat(result.getModelAndView().getModel().get("formAction")).isEqualTo("/internal-production");
        assertThat(result.getModelAndView().getModel().get("isEdit")).isEqualTo(false);
    }

    @Test
    @DisplayName("crear sin ingredientes no llega al servicio")
    void creatingWithoutIngredientsNeverReachesTheService() throws Exception {
        when(categoryService.getCategoryById(1L))
                .thenReturn(Optional.of(Category.builder().idCategory(1L).name("Buffet").active(true).build()));

        mockMvc.perform(post("/internal-production")
                        .param("name", "Guisado del buffet")
                        .param("categoryId", "1")
                        .principal(chef()))
                .andExpect(status().isOk());

        verify(preparationService, never()).createInternalItem(any(), any());
    }

    // ========== Helpers ==========

    private Authentication chef() {
        return new UsernamePasswordAuthenticationToken("chef1", "n/a",
                Arrays.asList(new SimpleGrantedAuthority("ROLE_CHEF")));
    }

    private Employee employee() {
        return Employee.builder().idEmpleado(5L).nombre("Ana").apellido("Ruiz").username("chef1").build();
    }

    private ItemMenu internalItem() {
        return ItemMenu.builder()
                .idItemMenu(7L)
                .name("Guisado del buffet")
                .price(BigDecimal.ZERO)
                .internalOnly(true)
                .build();
    }

    private ItemPreparation preparation(int quantity) {
        return ItemPreparation.builder()
                .idPreparation(99L)
                .itemMenu(internalItem())
                .quantity(quantity)
                .status(PreparationStatus.PREPARED)
                .stockReturned(false)
                .build();
    }

    private ItemPreparation cancelledPreparation() {
        ItemPreparation preparation = preparation(3);
        preparation.setStatus(PreparationStatus.CANCELLED);
        preparation.setStockReturned(true);
        return preparation;
    }

    private static AbstractView noOpView(String viewName) {
        return new AbstractView() {
            @Override
            protected void renderMergedOutputModel(Map<String, Object> model,
                                                   jakarta.servlet.http.HttpServletRequest request,
                                                   jakarta.servlet.http.HttpServletResponse response) {
                // No HTML needed: the assertions read the view name and the model.
            }
        };
    }
}
