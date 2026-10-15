package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El aviso de "copiar la receta a las variantes" tal como lo recibe el navegador.
 *
 * <p>El servidor no dibuja el aviso: deja los datos reales (cuántas variantes y de qué item) en un
 * div oculto y un script al final de la página los lee para preguntar. Es justo el cableado que se
 * rompe en silencio si alguien renombra un atributo o el endpoint, así que aquí se fija contra la
 * plantilla real: sin los datos el aviso no existe, y con ellos sale con el número que mandó el
 * servidor, no con uno adivinado por el navegador.</p>
 */
@SpringBootTest
@WithMockUser(username = "admin", roles = "ADMIN")
class MenuItemRecipePropagationRenderTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private ItemMenuService itemMenuService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        CompanyContext.setCurrentCompany(Company.builder()
                .idCompany(2L)
                .slug("quinta")
                .name("Quinta El Paraíso")
                .build());

        when(itemMenuService.searchMenuItemsPage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(emptyPage());
        when(itemMenuService.countAllByCompany()).thenReturn(0L);
        when(itemMenuService.countActiveByCompany()).thenReturn(0L);
        when(itemMenuService.countAvailableByCompany()).thenReturn(0L);
        when(itemMenuService.countUnavailableByCompany()).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private Page<ItemMenu> emptyPage() {
        return new PageImpl<>(List.of());
    }

    private String renderListWithOffer() throws Exception {
        return mockMvc.perform(get("/admin/menu-items")
                        .flashAttr("offerRecipeToSizeVariants", 3)
                        .flashAttr("offerRecipeToSizeVariantsItemId", 432L)
                        .flashAttr("offerRecipeToSizeVariantsItemName", "Parrillada"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    @DisplayName("tras guardar la receta del padre, la lista sale con los datos reales del aviso")
    void theListCarriesTheRealNumbersForTheOffer() throws Exception {
        String html = renderListWithOffer();

        assertThat(html).contains("id=\"offerRecipeToSizeVariants\"");
        assertThat(html).contains("data-variants=\"3\"");
        assertThat(html).contains("data-item-id=\"432\"");
        assertThat(html).contains("data-item-name=\"Parrillada\"");
        assertThat(html)
                .as("el aviso debe llamar al endpoint que copia la receta")
                .contains("/recipe/propagate-to-sizes");
    }

    @Test
    @DisplayName("una lista normal no trae datos de aviso ninguno")
    void aPlainListCarriesNoOfferData() throws Exception {
        String html = mockMvc.perform(get("/admin/menu-items"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).doesNotContain("data-variants=");
        assertThat(html).doesNotContain("offerRecipeToSizeVariantsItemId");
        assertThat(html)
                .as("el script del aviso siempre está; sin datos simplemente no pregunta")
                .contains("/recipe/propagate-to-sizes");
    }
}
