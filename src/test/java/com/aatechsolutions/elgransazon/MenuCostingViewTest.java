package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.dto.MenuItemCost;
import com.aatechsolutions.elgransazon.application.service.MenuCostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@WithMockUser(username = "admin", roles = "ADMIN")
class MenuCostingViewTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private MenuCostingService menuCostingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        MenuItemCost.Line line = MenuItemCost.Line.builder()
                .ingredientName("Carne")
                .quantity(new BigDecimal("0.20"))
                .unit("KG")
                .unitCost(new BigDecimal("50.00"))
                .subtotal(new BigDecimal("10.00"))
                .yieldPercentage(new BigDecimal("80.00"))
                .build();
        MenuItemCost row = MenuItemCost.builder()
                .idItemMenu(17L)
                .name("Bistec")
                .categoryName("Platos fuertes")
                .imageUrl("https://images.example.test/bistec.jpg")
                .price(new BigDecimal("100.00"))
                .cost(new BigDecimal("10.00"))
                .profit(new BigDecimal("90.00"))
                .marginPercentage(new BigDecimal("90.0"))
                .hasRecipe(true)
                .relatedVariants(List.of(MenuItemCost.Variant.builder()
                        .idItemMenu(18L)
                        .name("Bistec chico")
                        .sizeName("Chico")
                        .price(new BigDecimal("75.00"))
                        .build()))
                .lines(List.of(line))
                .build();
        when(menuCostingService.calculateForCurrentCompany(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 41));
    }

    @Test
    @DisplayName("la vista ofrece filtros y renderiza el detalle de costeo del platillo")
    void rendersFiltersAndDishDetails() throws Exception {
        mockMvc.perform(get("/admin/menu-items/costing"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Una categoría")))
                .andExpect(content().string(containsString("Limpiar filtros")))
                .andExpect(content().string(containsString("Con IVA")))
                .andExpect(content().string(containsString("Sin IVA")))
                .andExpect(content().string(containsString("data-mode=\"con\"")))
                .andExpect(content().string(containsString("antes del IVA")))
                .andExpect(content().string(containsString("Utilidad estimada*")))
                .andExpect(content().string(containsString("Margen estimado*")))
                .andExpect(content().string(containsString("sin ajustar el IVA de las compras")))
                .andExpect(content().string(containsString("Buscar platillo")))
                .andExpect(content().string(containsString("Preparación por porción")))
                .andExpect(content().string(containsString("Bistec")))
                .andExpect(content().string(containsString("80.0 %")))
                .andExpect(content().string(containsString("Precio de venta")))
                .andExpect(content().string(containsString("Semáforo de margen")))
                .andExpect(content().string(containsString("¿Quieres saber a qué precio venderlo según el margen que elijas?")))
                .andExpect(content().string(containsString("¿Qué margen quieres obtener sobre la venta antes del IVA?")))
                .andExpect(content().string(containsString("Precio sugerido con IVA")))
                .andExpect(content().string(containsString("Utilidad estimada")))
                .andExpect(content().string(containsString("Ver hijos (1)")))
                .andExpect(content().string(containsString("Chico")))
                .andExpect(content().string(containsString("value=\"60\"")))
                .andExpect(content().string(containsString("target-margin-input")))
                .andExpect(content().string(containsString("Mostrando")))
                .andExpect(content().string(containsString("Siguiente")));
        verify(menuCostingService).calculateForCurrentCompany(eq(PageRequest.of(0, 20)), eq(null), eq(null));
    }

    @Test
    @DisplayName("la búsqueda por platillo se consulta en el servidor")
    void searchesDishAcrossServerPages() throws Exception {
        mockMvc.perform(get("/admin/menu-items/costing")
                        .param("view", "dish")
                        .param("name", "cler"))
                .andExpect(status().isOk())
                        .andExpect(content().string(containsString("value=\"cler\"")));

        verify(menuCostingService).calculateForCurrentCompany(
                eq(PageRequest.of(0, 20)), eq(null), eq("cler"));
    }

    @Test
    @DisplayName("al volver a todos ignora el nombre de búsqueda que haya quedado en la URL")
    void ignoresStaleDishSearchWhenViewingAll() throws Exception {
        mockMvc.perform(get("/admin/menu-items/costing")
                        .param("view", "all")
                        .param("name", "clericot")
                        .param("category", "Bebidas"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Todos los platillos")));

        verify(menuCostingService).calculateForCurrentCompany(
                eq(PageRequest.of(0, 20)), eq(null), eq(null));
    }

    @Test
    @DisplayName("el modo sin IVA muestra el precio antes del impuesto")
    void rendersPriceWithoutTaxWhenSelected() throws Exception {
        mockMvc.perform(get("/admin/menu-items/costing").param("ivaMode", "sin"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-mode=\"sin\"")))
                .andExpect(content().string(containsString("Precio de venta sin IVA")))
                .andExpect(content().string(containsString("$86.21")));
    }

    @Test
    @DisplayName("no presenta utilidad ni margen como cifras confiables si el costo está incompleto")
    void doesNotPresentEstimatedProfitOrMarginForIncompleteCost() throws Exception {
        MenuItemCost incompleteRow = MenuItemCost.builder()
                .idItemMenu(19L)
                .name("Agua")
                .categoryName("Bebidas")
                .price(new BigDecimal("28.00"))
                .cost(BigDecimal.ZERO)
                .hasRecipe(false)
                .build();
        when(menuCostingService.calculateForCurrentCompany(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(incompleteRow), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/admin/menu-items/costing"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("dish-profit text-xl font-bold text-gray-500\">—</p>")))
                .andExpect(content().string(matchesPattern("(?s).*id=\"costingAverageMargin\"\\s*>\\s*—\\s*</p>.*")));
    }

    @Test
    @DisplayName("el endpoint de búsqueda entrega resultados para actualizar sin recargar la página")
    void returnsLiveSearchResultsAsJson() throws Exception {
        mockMvc.perform(get("/admin/menu-items/costing/search")
                                .param("page", "0")
                                .param("name", "cler"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith("application/json"))
                        .andExpect(content().string(containsString("\"name\":\"Bistec\"")))
                        .andExpect(content().string(containsString("\"imageUrl\":\"https://images.example.test/bistec.jpg\"")))
                        .andExpect(content().string(containsString("\"cost\":10.00")))
                        .andExpect(content().string(containsString("\"incomplete\":false")))
                        .andExpect(content().string(containsString("\"totalItems\":41")));

        verify(menuCostingService).calculateForCurrentCompany(
                        eq(PageRequest.of(0, 20)), eq(null), eq("cler"));
    }
}
