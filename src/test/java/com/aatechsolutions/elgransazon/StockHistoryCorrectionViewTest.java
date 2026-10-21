package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.IngredientStockHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La pantalla del historial de stock, ya con corrección y anulación.
 *
 * <p>Se renderiza la plantilla real porque lo que importa es que los botones y sus formularios
 * existan y apunten a la URL correcta. Los POST comprueban que el controlador mande al servicio
 * exactamente la cantidad, el costo y el motivo capturados, y que el error del servicio llegue
 * al usuario como mensaje en vez de como pantalla de error.</p>
 */
@SpringBootTest
@WithMockUser(username = "admin", roles = "ADMIN")
class StockHistoryCorrectionViewTest {

    private static final Long INGREDIENT_ID = 1L;
    private static final Long HISTORY_ID = 99L;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private IngredientService ingredientService;

    @MockitoBean
    private EmployeeService employeeService;

    private MockMvc mockMvc;
    private Employee admin;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        admin = Employee.builder().idEmpleado(5L).nombre("Admin").apellido("Quinta").build();

        Ingredient ingredient = Ingredient.builder()
                .idIngredient(INGREDIENT_ID)
                .name("Tomate")
                .unitOfMeasure("KG")
                .currentStock(new BigDecimal("16.00"))
                .minStock(BigDecimal.ZERO)
                .maxStock(new BigDecimal("100.00"))
                .active(true)
                .build();

        IngredientStockHistory record = IngredientStockHistory.builder()
                .id(HISTORY_ID)
                .ingredient(ingredient)
                .quantityAdded(new BigDecimal("10.00"))
                .costPerUnit(new BigDecimal("5.00"))
                .previousStock(new BigDecimal("6.00"))
                .newStock(new BigDecimal("16.00"))
                .addedAt(LocalDateTime.of(2026, 10, 19, 12, 0))
                .addedBy(admin)
                .build();

        when(ingredientService.findById(INGREDIENT_ID)).thenReturn(Optional.of(ingredient));
        when(ingredientService.getStockHistory(INGREDIENT_ID)).thenReturn(List.of(record));
        when(ingredientService.getTotalCostByIngredient(INGREDIENT_ID))
                .thenReturn(new BigDecimal("50.00"));
        when(employeeService.findByUsername("admin")).thenReturn(Optional.of(admin));
    }

    private Authentication adminAuth() {
        return new UsernamePasswordAuthenticationToken("admin", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("cada entrada tiene sus botones de editar y anular con la URL correcta")
    void eachEntryHasEditAndVoidActions() throws Exception {
        mockMvc.perform(get("/admin/ingredients/{id}/stock-history", INGREDIENT_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Stock al momento")))
                .andExpect(content().string(containsString("Editar")))
                .andExpect(content().string(containsString("Anular")))
                .andExpect(content().string(containsString(
                        "/admin/ingredients/1/stock-history/99/edit")))
                .andExpect(content().string(containsString(
                        "/admin/ingredients/1/stock-history/99/void")))
                .andExpect(content().string(containsString("Corregir una entrada")))
                .andExpect(content().string(containsString("¿Anular esta entrada?")))
                // El formulario de corrección vive en un modal propio (no en SweetAlert):
                // así los campos no se desbordan ni heredan el texto claro del tema oscuro.
                .andExpect(content().string(containsString("id=\"editModal\"")))
                .andExpect(content().string(containsString("name=\"quantityAdded\"")))
                .andExpect(content().string(containsString("name=\"costPerUnit\"")))
                .andExpect(content().string(containsString(
                        "data-edit-url=\"/admin/ingredients/1/stock-history/99/edit\"")));
    }

    @Test
    @DisplayName("editar manda la cantidad y el costo capturados y vuelve al historial")
    void editingSendsTheCapturedValues() throws Exception {
        mockMvc.perform(post("/admin/ingredients/{id}/stock-history/{historyId}/edit",
                        INGREDIENT_ID, HISTORY_ID)
                        .param("quantityAdded", "8")
                        .param("costPerUnit", "5.50")
                        .principal(adminAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/ingredients/1/stock-history"))
                .andExpect(flash().attribute("successMessage", containsString("Entrada corregida")));

        verify(ingredientService).updateStockHistory(
                eq(INGREDIENT_ID), eq(HISTORY_ID),
                argThat(quantity -> quantity.compareTo(new BigDecimal("8")) == 0),
                argThat(cost -> cost.compareTo(new BigDecimal("5.50")) == 0),
                eq(admin));
    }

    @Test
    @DisplayName("anular manda el motivo y vuelve al historial")
    void voidingSendsTheReason() throws Exception {
        mockMvc.perform(post("/admin/ingredients/{id}/stock-history/{historyId}/void",
                        INGREDIENT_ID, HISTORY_ID)
                        .param("reason", "compra duplicada")
                        .principal(adminAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/ingredients/1/stock-history"))
                .andExpect(flash().attribute("successMessage", containsString("Entrada anulada")));

        verify(ingredientService).voidStockHistory(
                INGREDIENT_ID, HISTORY_ID, "compra duplicada", admin);
    }

    @Test
    @DisplayName("el bloqueo del servicio se muestra como mensaje y no como error de la página")
    void aRejectedVoidIsShownAsAMessage() throws Exception {
        doThrow(new IllegalStateException(
                "No se puede anular: el stock actual es 8.00 KG y el resultado sería -2.00 KG."))
                .when(ingredientService).voidStockHistory(
                        eq(INGREDIENT_ID), eq(HISTORY_ID), eq("duplicada"), eq(admin));

        mockMvc.perform(post("/admin/ingredients/{id}/stock-history/{historyId}/void",
                        INGREDIENT_ID, HISTORY_ID)
                        .param("reason", "duplicada")
                        .principal(adminAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/ingredients/1/stock-history"))
                .andExpect(flash().attribute("errorMessage", containsString("No se puede anular")));
    }
}
