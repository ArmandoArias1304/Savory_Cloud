package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.IngredientCategoryService;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.application.service.SupplierService;
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

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El listado de ingredientes tal como lo recibe el navegador.
 *
 * <p>El aviso de "revisa las cantidades" se pinta sólo cuando la redirección trae el atributo
 * {@code warningMessage}: es la señal que queda al usuario después de cambiar la unidad de medida de
 * un ingrediente que está en recetas. Si la plantilla deja de pintarlo, el cambio se vuelve
 * silencioso y las recetas quedan con cantidades que ya no significan lo mismo, sin que nada más
 * falle: por eso se fija aquí, contra la plantilla real renderizada.</p>
 */
@SpringBootTest
@WithMockUser(username = "admin", roles = "ADMIN")
class IngredientListUnitWarningRenderTest {

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
    }

    @Test
    @DisplayName("el aviso de revisar cantidades se pinta en el listado cuando la redirección lo trae")
    void theQuantityWarningIsRenderedAfterAUnitChange() throws Exception {
        String message = "Cambiaste la unidad de medida. Se actualizó en 3 recetas que usan este "
                + "ingrediente, pero las cantidades no se convierten: revisa cada receta y ajusta la "
                + "cantidad a la nueva unidad.";

        mockMvc.perform(get("/admin/ingredients").flashAttr("warningMessage", message))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(message)));
    }

    @Test
    @DisplayName("sin aviso pendiente el listado no muestra ninguna advertencia de unidad")
    void withoutAPendingWarningTheListStaysClean() throws Exception {
        mockMvc.perform(get("/admin/ingredients"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("revisa cada receta y ajusta la cantidad"))));
    }
}
