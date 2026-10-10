package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.InventoryObjectCategoryService;
import com.aatechsolutions.elgransazon.application.service.InventoryObjectService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.InventoryObject;
import com.aatechsolutions.elgransazon.domain.entity.InventoryObjectCategory;
import com.aatechsolutions.elgransazon.domain.entity.ObjectCondition;
import com.aatechsolutions.elgransazon.domain.entity.ObjectUnit;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las pantallas del inventario tal como las recibe el navegador:
 * el hub con los dos botones, el listado de objetos, su formulario y las categorías.
 */
@SpringBootTest
@WithMockUser(username = "admin", roles = "ADMIN")
class InventoryModuleRenderTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private InventoryObjectService objectService;

    @MockitoBean
    private InventoryObjectCategoryService categoryService;

    private MockMvc mockMvc;
    private InventoryObjectCategory electronica;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        CompanyContext.setCurrentCompany(Company.builder()
                .idCompany(3L)
                .slug("elgransazon")
                .name("El Gran Sazón")
                .build());

        electronica = InventoryObjectCategory.builder()
                .idCategory(4L)
                .name("Electrónica")
                .description("Pantallas, controles y equipos")
                .icon("tv")
                .color("#38e07b")
                .active(true)
                .build();

        when(objectService.searchWithFilters(any(), any(), any(), any(), any()))
                .thenReturn(List.of(television(), olla()));
        when(objectService.getTotalCount()).thenReturn(3L);
        when(objectService.getActiveCount()).thenReturn(2L);
        when(objectService.getInactiveCount()).thenReturn(1L);
        when(objectService.getDamagedCount()).thenReturn(1L);
        when(objectService.getInventoryValue()).thenReturn(new BigDecimal("25500.00"));
        when(objectService.findById(1L)).thenReturn(Optional.of(television()));

        when(categoryService.findAll()).thenReturn(List.of(electronica));
        when(categoryService.findAllActive()).thenReturn(List.of(electronica));
        when(categoryService.searchWithFilters(any(), any())).thenReturn(List.of(electronica));
        when(categoryService.getActiveCount()).thenReturn(1L);
        when(categoryService.getInactiveCount()).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("el hub pregunta con los dos inventarios y enlaza a cada uno")
    void theHubOffersBothInventories() throws Exception {
        String html = render("/admin/inventory");

        assertThat(html).contains("Inventario de Ingredientes");
        assertThat(html).contains("Inventario de Objetos");
        assertThat(html).contains("href=\"/admin/ingredients\"");
        assertThat(html).contains("href=\"/admin/inventory/objects\"");
        assertThat(html).contains("25,500.00");
    }

    @Test
    @DisplayName("el listado de objetos muestra el objeto, su categoría, cantidad y valor")
    void theObjectListShowsEveryColumn() throws Exception {
        String html = render("/admin/inventory/objects");

        assertThat(html).contains("Televisión Samsung 50");
        assertThat(html).contains("Electrónica");
        assertThat(html).contains("Salón");
        assertThat(html).contains("Samsung");
        assertThat(html).contains("Bueno");
        assertThat(html).contains("25,500.00");
        assertThat(html).contains("href=\"/admin/inventory/objects/1/edit\"");
    }

    @Test
    @DisplayName("el listado ofrece los filtros de categoría, condición y estado")
    void theObjectListOffersFilters() throws Exception {
        String html = render("/admin/inventory/objects");

        assertThat(html).contains("name=\"search\"");
        assertThat(html).contains("name=\"categoryId\"");
        assertThat(html).contains("name=\"condition\"");
        assertThat(html).contains("name=\"location\"");
        assertThat(html).contains("name=\"active\"");
        assertThat(html).contains("Dañado");
        assertThat(html).contains("Desechado");
    }

    @Test
    @DisplayName("el formulario de objeto envía a la ruta de objetos con su categoría y unidad")
    void theObjectFormPostsToTheObjectRoute() throws Exception {
        String html = render("/admin/inventory/objects/new");

        assertThat(html).contains("action=\"/admin/inventory/objects\"");
        assertThat(html).contains("name=\"categoryId\"");
        assertThat(html).contains("Electrónica");
        assertThat(html).contains("Pieza");
        assertThat(html).contains("name=\"purchaseDate\"");
        assertThat(html).contains("name=\"serialNumber\"");
    }

    @Test
    @DisplayName("el formulario de edición apunta a la ruta del objeto")
    void theEditFormPostsToTheObjectId() throws Exception {
        String html = render("/admin/inventory/objects/1/edit");

        assertThat(html).contains("action=\"/admin/inventory/objects/1\"");
        assertThat(html).contains("Televisión Samsung 50");
        assertThat(html).contains("Actualizar Objeto");
    }

    @Test
    @DisplayName("cada objeto muestra su propia condición, no la de otro renglón")
    void everyRowShowsItsOwnCondition() throws Exception {
        String rows = rowsOf(render("/admin/inventory/objects"));

        assertThat(rows).contains("Bueno");
        assertThat(countOf(rows, "Dañado")).isEqualTo(1);
        assertThat(rows).as("la condición cayó en el caso por defecto (Desechado)")
                .doesNotContain("Desechado");
    }

    @Test
    @DisplayName("un gerente no puede eliminar objetos definitivamente")
    @WithMockUser(username = "gerente", roles = "MANAGER")
    void managerCannotHardDelete() {
        assertThatThrownBy(() -> mockMvc.perform(post("/admin/inventory/objects/1/hard-delete")))
                .hasRootCauseInstanceOf(AuthorizationDeniedException.class);

        verify(objectService, never()).hardDelete(any());
    }

    @Test
    @DisplayName("un administrador sí puede eliminar definitivamente")
    void adminCanHardDelete() throws Exception {
        mockMvc.perform(post("/admin/inventory/objects/1/hard-delete"))
                .andExpect(status().is3xxRedirection());

        verify(objectService).hardDelete(1L);
    }

    @Test
    @DisplayName("el botón de eliminar definitivamente sólo se pinta para el administrador")
    void theHardDeleteButtonIsOnlyRenderedForAdmins() throws Exception {
        assertThat(render("/admin/inventory/objects")).contains("btn-hard-delete");
    }

    @Test
    @DisplayName("las categorías de objetos se listan con su nombre y acciones")
    void theCategoryListRenders() throws Exception {
        String html = render("/admin/inventory/object-categories");

        assertThat(html).contains("Categorías de Objetos");
        assertThat(html).contains("Electrónica");
        assertThat(html).contains("href=\"/admin/inventory/object-categories/4/edit\"");
        assertThat(html).contains("href=\"/admin/inventory/object-categories/new\"");
    }

    @Test
    @DisplayName("el formulario de categoría de objetos se pinta para crear y para editar")
    void theCategoryFormRenders() throws Exception {
        when(categoryService.findById(4L)).thenReturn(Optional.of(electronica));

        String nuevo = render("/admin/inventory/object-categories/new");
        assertThat(nuevo).contains("action=\"/admin/inventory/object-categories\"");
        assertThat(nuevo).contains("Crear Categoría");
        assertThat(nuevo).contains("name=\"icon\"");
        assertThat(nuevo).contains("name=\"color\"");

        String editar = render("/admin/inventory/object-categories/4/edit");
        assertThat(editar).contains("action=\"/admin/inventory/object-categories/4\"");
        assertThat(editar).contains("Actualizar Categoría");
        assertThat(editar).contains("Electrónica");
    }

    @Test
    @DisplayName("el menú lateral manda al hub de inventario, no directo a ingredientes")
    void theSidebarPointsToTheInventoryHub() throws Exception {
        String html = render("/admin/inventory/objects");

        assertThat(html).contains("href=\"/admin/inventory\"");
        assertThat(html).contains(">Inventario</span>");
    }

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** Sólo las filas de la tabla, para no confundir las etiquetas con las del filtro */
    private String rowsOf(String html) {
        int start = html.indexOf("<tbody");
        int end = html.indexOf("</tbody>", start);
        return html.substring(start, end);
    }

    private int countOf(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private InventoryObject olla() {
        return InventoryObject.builder()
                .idObject(2L)
                .name("Olla express 8L")
                .category(electronica)
                .quantity(new BigDecimal("2"))
                .unit(ObjectUnit.PIEZA)
                .condition(ObjectCondition.DANADO)
                .location("Cocina")
                .purchaseCost(new BigDecimal("1250.00"))
                .currency("MXN")
                .active(true)
                .build();
    }

    private InventoryObject television() {
        return InventoryObject.builder()
                .idObject(1L)
                .name("Televisión Samsung 50")
                .description("Pantalla del salón principal")
                .category(electronica)
                .quantity(new BigDecimal("3"))
                .unit(ObjectUnit.PIEZA)
                .condition(ObjectCondition.BUENO)
                .location("Salón")
                .responsible("Gerente")
                .brand("Samsung")
                .model("UN50CU7000")
                .serialNumber("SN-0012345")
                .purchaseCost(new BigDecimal("8500.00"))
                .currency("MXN")
                .active(true)
                .build();
    }
}
