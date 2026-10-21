package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.application.service.ItemPreparationService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Producción interna dejó de ser un módulo del sidebar: se entra desde el botón que está en la
 * página de Menú, junto a "Categorías" y "Carta en PDF".
 *
 * <p>Se renderiza la plantilla real porque lo que importa es lo que ve el navegador: si el botón
 * desaparece de la cabecera del Menú, no queda ninguna otra puerta de entrada para admin y gerente
 * (el chef la tiene en su panel). También se fija lo contrario: el sidebar ya no debe listar
 * "Producción interna" como un módulo propio.</p>
 */
@SpringBootTest
class MenuPageInternalProductionLinkTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private ItemMenuService itemMenuService;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private SystemConfigurationService systemConfigurationService;

    @MockitoBean
    private ItemPreparationService preparationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        when(itemMenuService.searchMenuItemsPage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.<ItemMenu>empty());
        when(itemMenuService.countAllByCompany()).thenReturn(0L);
        when(itemMenuService.countActiveByCompany()).thenReturn(0L);
        when(itemMenuService.countAvailableByCompany()).thenReturn(0L);
        when(itemMenuService.countUnavailableByCompany()).thenReturn(0L);
        when(categoryService.getAllCategories()).thenReturn(List.of());
        when(systemConfigurationService.getConfiguration()).thenReturn(null);

        when(preparationService.findInternalItems()).thenReturn(List.of());
        when(preparationService.findTodayPreparations()).thenReturn(List.of());
        when(preparationService.searchPreparations(any(), any(), anyInt())).thenReturn(List.of());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("el admin entra a Producción interna desde la página de Menú, no desde el sidebar")
    void theAdminReachesInternalProductionFromTheMenuPage() throws Exception {
        String menuPage = menuPage();

        assertThat(menuPage).contains("Producción interna");
        assertThat(countOccurrences(menuPage, "href=\"/internal-production\""))
                .as("el sidebar no debe repetir el acceso: sólo el botón del Menú")
                .isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "gerente", roles = "MANAGER")
    @DisplayName("el gerente también ve el botón en la página de Menú")
    void theManagerSeesTheSameButton() throws Exception {
        String menuPage = menuPage();

        assertThat(menuPage).contains("Producción interna");
        assertThat(countOccurrences(menuPage, "href=\"/internal-production\"")).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("dentro de Producción interna el sidebar sigue marcando Menú")
    void theSidebarKeepsMenuHighlightedInsideInternalProduction() throws Exception {
        String page = mockMvc.perform(get("/internal-production"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String menuLink = anchorFor(page, "/admin/menu-items");
        assertThat(menuLink)
                .as("Producción interna cuelga del Menú: el sidebar debe mostrar Menú activo")
                .contains("active");
        assertThat(page).doesNotContain(">Producción interna</span>");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("el admin vuelve al Menú con la flecha de regreso")
    void theAdminCanGoBackToTheMenu() throws Exception {
        String page = mockMvc.perform(get("/internal-production"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String backLink = lastAnchorFor(page, "/admin/menu-items");
        assertThat(backLink).as("la flecha debe apuntar al Menú").contains("Volver al menú");
        assertThat(backLink).contains("arrow_back");
    }

    @Test
    @WithMockUser(username = "gerente", roles = "MANAGER")
    @DisplayName("el gerente también tiene la flecha de regreso al Menú")
    void theManagerCanGoBackToTheMenu() throws Exception {
        String page = mockMvc.perform(get("/internal-production"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(lastAnchorFor(page, "/admin/menu-items")).contains("Volver al menú");
    }

    @Test
    @WithMockUser(username = "chef", roles = "CHEF")
    @DisplayName("el chef conserva su flecha al panel y no ve la del Menú")
    void theChefKeepsHisOwnBackLink() throws Exception {
        String page = mockMvc.perform(get("/internal-production"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Volver al panel");
        assertThat(page).doesNotContain("Volver al menú");
        // El chef no ve el sidebar de administración: tampoco debe aparecerle el enlace al Menú.
        assertThat(page).doesNotContain("href=\"/admin/menu-items\"");
    }

    private String menuPage() throws Exception {
        return mockMvc.perform(get("/admin/menu-items"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String anchorFor(String html, String href) {
        int start = html.indexOf("href=\"" + href + "\"");
        assertThat(start).as("no se encontró el enlace %s", href).isGreaterThanOrEqualTo(0);
        int end = html.indexOf("</a>", start);
        return html.substring(start, end < 0 ? html.length() : end);
    }

    /** El último enlace a {@code href}; el sidebar va antes del contenido, así que éste es el de la página. */
    private static String lastAnchorFor(String html, String href) {
        int start = html.lastIndexOf("href=\"" + href + "\"");
        assertThat(start).as("no se encontró el enlace %s", href).isGreaterThanOrEqualTo(0);
        int end = html.indexOf("</a>", start);
        return html.substring(start, end < 0 ? html.length() : end);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
