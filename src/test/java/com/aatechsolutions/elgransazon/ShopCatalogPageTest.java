package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.ShopProductService;
import com.aatechsolutions.elgransazon.domain.entity.ShopProduct;
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
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin/manager view of the shop through the real MVC pipeline: the sidebar
 * entry must be there and every card must carry its WhatsApp request.
 */
@SpringBootTest
@WithMockUser(username = "admin", roles = "ADMIN")
class ShopCatalogPageTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private ShopProductService shopProductService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        ShopProduct product = ShopProduct.builder()
                .idProduct(1L)
                .name("Tarjeta QR para mesa")
                .description("Tarjeta impresa con código QR.")
                .price(new BigDecimal("250.00"))
                .category("Tarjetas")
                .imageUrl("https://images.example.com/qr.png")
                .active(true)
                .build();

        when(shopProductService.findActive(null, null)).thenReturn(List.of(product));
        when(shopProductService.findCategories(true)).thenReturn(List.of("Tarjetas"));
    }

    @Test
    @DisplayName("el catálogo se pinta y el sidebar ofrece la entrada Tienda")
    void catalogueIsRenderedWithSidebarEntry() throws Exception {
        mockMvc.perform(get("/admin/shop"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tarjeta QR para mesa")))
                .andExpect(content().string(containsString("href=\"/admin/shop\"")))
                .andExpect(content().string(containsString(">Tienda</span>")));
    }
}
