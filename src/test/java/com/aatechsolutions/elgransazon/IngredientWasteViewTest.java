package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.application.service.IngredientWasteService;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.IngredientWasteReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@WithMockUser(username = "admin", roles = "ADMIN")
class IngredientWasteViewTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private IngredientService ingredientService;

    @MockitoBean
    private IngredientWasteService ingredientWasteService;

    @MockitoBean
    private EmployeeService employeeService;

    private MockMvc mockMvc;
    private Employee employee;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        employee = Employee.builder()
                .idEmpleado(10L)
                .username("admin")
                .nombre("Admin")
                .apellido("Restaurante")
                .build();
        when(ingredientService.findAllActive()).thenReturn(List.of(Ingredient.builder()
                .idIngredient(1L)
                .name("Aceite")
                .currentStock(new BigDecimal("5.00"))
                .unitOfMeasure("LT")
                .build()));
        when(ingredientWasteService.findRecent(100)).thenReturn(List.of());
        when(employeeService.findByUsername("admin")).thenReturn(Optional.of(employee));
    }

    @Test
    void rendersWasteFormAndHistory() throws Exception {
        mockMvc.perform(get("/admin/inventory/waste"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Merma de ingredientes")))
                .andExpect(content().string(containsString("Cantidad perdida")))
                .andExpect(content().string(containsString("Desperdicio de preparación")))
                .andExpect(content().string(containsString("Registros recientes")))
                .andExpect(content().string(containsString("Disponible: 5.00 LT")));
    }

    @Test
    void submitsWasteRecord() throws Exception {
        LocalDate occurredOn = LocalDate.of(2026, 11, 4);

        mockMvc.perform(post("/admin/inventory/waste")
                        .with(csrf())
                        .principal(new UsernamePasswordAuthenticationToken(
                                "admin", null, List.of()))
                        .param("ingredientId", "1")
                        .param("quantity", "1.25")
                        .param("reason", "SPILLED")
                        .param("occurredOn", occurredOn.toString())
                        .param("note", "Se derramó"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/inventory/waste"));

        verify(ingredientWasteService).recordWaste(
                eq(1L), eq(new BigDecimal("1.25")), eq(IngredientWasteReason.SPILLED),
                eq(occurredOn), eq("Se derramó"), eq(employee));
    }
}
