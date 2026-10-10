package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.InventoryObjectCategoryService;
import com.aatechsolutions.elgransazon.application.service.InventoryObjectService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.InventoryObject;
import com.aatechsolutions.elgransazon.domain.entity.InventoryObjectCategory;
import com.aatechsolutions.elgransazon.domain.entity.ObjectCondition;
import com.aatechsolutions.elgransazon.domain.entity.ObjectUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Rutas del inventario de objetos: listado con filtros, alta, edición y bajas.
 */
@ExtendWith(MockitoExtension.class)
class InventoryObjectControllerTest {

    @Mock
    private InventoryObjectService objectService;

    @Mock
    private InventoryObjectCategoryService categoryService;

    private MockMvc mockMvc;
    private InventoryObjectCategory electronica;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new InventoryObjectController(objectService, categoryService))
                .setValidator(validator)
                .build();

        electronica = InventoryObjectCategory.builder()
                .idCategory(4L)
                .name("Electrónica")
                .company(Company.builder().idCompany(3L).name("El Gran Sazón").build())
                .active(true)
                .build();
    }

    @Test
    @DisplayName("el listado entrega los objetos, los filtros y los catálogos a la vista")
    void listPassesObjectsAndFiltersToTheView() throws Exception {
        InventoryObject television = television(1L);
        when(objectService.searchWithFilters(eq("tele"), eq(4L), eq(ObjectCondition.BUENO), eq("Salón"), eq(true)))
                .thenReturn(List.of(television));
        when(categoryService.findAll()).thenReturn(List.of(electronica));
        when(objectService.getTotalCount()).thenReturn(1L);
        when(objectService.getActiveCount()).thenReturn(1L);
        when(objectService.getInactiveCount()).thenReturn(0L);
        when(objectService.getDamagedCount()).thenReturn(0L);
        when(objectService.getInventoryValue()).thenReturn(new BigDecimal("25500.00"));

        mockMvc.perform(get("/admin/inventory/objects")
                        .param("search", "tele")
                        .param("categoryId", "4")
                        .param("condition", "BUENO")
                        .param("location", "Salón")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/inventory/objects/list"))
                .andExpect(model().attribute("objects", List.of(television)))
                .andExpect(model().attribute("search", "tele"))
                .andExpect(model().attribute("categoryId", 4L))
                .andExpect(model().attribute("condition", ObjectCondition.BUENO))
                .andExpect(model().attribute("location", "Salón"))
                .andExpect(model().attribute("active", true))
                .andExpect(model().attribute("allCategories", List.of(electronica)))
                .andExpect(model().attributeExists("conditions", "units", "inventoryValue"));
    }

    @Test
    @DisplayName("el formulario de alta arranca con los valores por defecto del objeto")
    void createFormStartsWithDefaults() throws Exception {
        when(categoryService.findAllActive()).thenReturn(List.of(electronica));

        mockMvc.perform(get("/admin/inventory/objects/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/inventory/objects/form"))
                .andExpect(model().attribute("isEdit", false))
                .andExpect(model().attributeExists("object", "allCategories", "conditions", "units"));
    }

    @Test
    @DisplayName("sin categorías activas no se puede abrir el formulario de alta")
    void createFormRedirectsWhenThereAreNoCategories() throws Exception {
        when(categoryService.findAllActive()).thenReturn(List.of());

        mockMvc.perform(get("/admin/inventory/objects/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/inventory/object-categories"));
    }

    @Test
    @DisplayName("crear un objeto guarda la categoría elegida y regresa al listado")
    void createStoresTheSelectedCategory() throws Exception {
        when(categoryService.findById(4L)).thenReturn(Optional.of(electronica));

        mockMvc.perform(post("/admin/inventory/objects")
                        .param("name", "Televisión Samsung 50")
                        .param("categoryId", "4")
                        .param("quantity", "3")
                        .param("unit", "PIEZA")
                        .param("condition", "NUEVO")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/inventory/objects"));

        verify(objectService).create(any(InventoryObject.class));
    }

    @Test
    @DisplayName("un objeto sin nombre no se guarda y el formulario se vuelve a mostrar con el error")
    void createRejectsAnObjectWithoutName() throws Exception {
        when(categoryService.findAllActive()).thenReturn(List.of(electronica));
        when(categoryService.findById(4L)).thenReturn(Optional.of(electronica));

        mockMvc.perform(post("/admin/inventory/objects")
                        .param("name", "   ")
                        .param("categoryId", "4")
                        .param("quantity", "3")
                        .param("unit", "PIEZA")
                        .param("condition", "BUENO"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/inventory/objects/form"))
                .andExpect(model().attributeHasFieldErrors("object", "name"));

        verify(objectService, never()).create(any());
    }

    @Test
    @DisplayName("sin categoría seleccionada el alta se rechaza con un error de campo")
    void createRejectsAMissingCategory() throws Exception {
        when(categoryService.findAllActive()).thenReturn(List.of(electronica));

        mockMvc.perform(post("/admin/inventory/objects")
                        .param("name", "Televisión Samsung 50")
                        .param("quantity", "3")
                        .param("unit", "PIEZA")
                        .param("condition", "BUENO"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/inventory/objects/form"))
                .andExpect(model().attributeHasFieldErrors("object", "category"));

        verify(objectService, never()).create(any());
    }

    @Test
    @DisplayName("el formulario de edición llega marcado como edición con el objeto cargado")
    void editFormLoadsTheObject() throws Exception {
        when(objectService.findById(1L)).thenReturn(Optional.of(television(1L)));
        when(categoryService.findAllActive()).thenReturn(List.of(electronica));

        mockMvc.perform(get("/admin/inventory/objects/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/inventory/objects/form"))
                .andExpect(model().attribute("isEdit", true))
                .andExpect(model().attributeExists("object"));
    }

    @Test
    @DisplayName("editar un objeto inexistente regresa al listado con aviso")
    void editFormRedirectsWhenTheObjectDoesNotExist() throws Exception {
        when(objectService.findById(404L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/inventory/objects/404/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/inventory/objects"));
    }

    @Test
    @DisplayName("actualizar conserva el id del objeto en el formulario")
    void updateKeepsTheObjectId() throws Exception {
        when(categoryService.findById(4L)).thenReturn(Optional.of(electronica));

        mockMvc.perform(post("/admin/inventory/objects/1")
                        .param("name", "Televisión Samsung 55")
                        .param("categoryId", "4")
                        .param("quantity", "4")
                        .param("unit", "PIEZA")
                        .param("condition", "DANADO")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/inventory/objects"));

        verify(objectService).update(eq(1L), any(InventoryObject.class));
    }

    @Test
    @DisplayName("desactivar, activar y eliminar definitivamente regresan al listado")
    void statusChangesAndHardDeleteReturnToTheList() throws Exception {
        mockMvc.perform(post("/admin/inventory/objects/1/delete"))
                .andExpect(redirectedUrl("/admin/inventory/objects"));
        verify(objectService).delete(1L);

        mockMvc.perform(post("/admin/inventory/objects/1/activate"))
                .andExpect(redirectedUrl("/admin/inventory/objects"));
        verify(objectService).activate(1L);

        mockMvc.perform(post("/admin/inventory/objects/1/hard-delete"))
                .andExpect(redirectedUrl("/admin/inventory/objects"));
        verify(objectService).hardDelete(1L);
    }

    @Test
    @DisplayName("los errores del servicio se muestran sin romper la pantalla")
    void serviceErrorsAreShownAsMessages() throws Exception {
        when(categoryService.findAllActive()).thenReturn(List.of(electronica));
        when(categoryService.findById(4L)).thenReturn(Optional.of(electronica));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Ya existe un objeto con el nombre: Televisión"))
                .when(objectService).create(any(InventoryObject.class));

        mockMvc.perform(post("/admin/inventory/objects")
                        .param("name", "Televisión")
                        .param("categoryId", "4")
                        .param("quantity", "1")
                        .param("unit", "PIEZA")
                        .param("condition", "BUENO"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/inventory/objects/form"))
                .andExpect(model().attribute("errorMessage", "Ya existe un objeto con el nombre: Televisión"));
    }

    @Test
    @DisplayName("el listado sin filtros no fuerza ningún estado ni categoría")
    void listWithoutFiltersDoesNotForceAnyState() throws Exception {
        when(objectService.searchWithFilters(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(categoryService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/inventory/objects"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/inventory/objects/list"));

        verify(objectService).searchWithFilters(null, null, null, null, null);
    }

    private InventoryObject television(Long id) {
        return InventoryObject.builder()
                .idObject(id)
                .name("Televisión Samsung 50")
                .category(electronica)
                .quantity(new BigDecimal("3"))
                .unit(ObjectUnit.PIEZA)
                .condition(ObjectCondition.BUENO)
                .location("Salón")
                .purchaseCost(new BigDecimal("8500.00"))
                .active(true)
                .build();
    }
}
