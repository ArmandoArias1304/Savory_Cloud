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
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The product form as the programmer's browser receives it.
 *
 * <p>Uses the full MVC pipeline (not a manual Thymeleaf context) because the
 * form relies on Spring's {@code #fields} helpers and multipart markup.</p>
 */
@SpringBootTest
@WithMockUser(username = "programmer", roles = "PROGRAMMER")
class ShopFormPageTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private ShopProductService shopProductService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        when(shopProductService.findCategories(false)).thenReturn(List.of("Tarjetas"));
    }

    @Test
    @DisplayName("el alta pide foto y envía el formulario como multipart")
    void createFormIsMultipartAndAsksForAPhoto() throws Exception {
        mockMvc.perform(get("/programmer/shop/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("multipart/form-data")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"imageFile\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Crear producto")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Subir foto")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/programmer/shop\"")));
    }

    @Test
    @DisplayName("la edición apunta al producto y conserva la foto actual")
    void editFormTargetsTheProductAndShowsItsPhoto() throws Exception {
        ShopProduct product = ShopProduct.builder()
                .idProduct(1L)
                .name("Tarjeta QR para mesa")
                .description("Tarjeta impresa con código QR.")
                .price(new BigDecimal("250.00"))
                .category("Tarjetas")
                .imageUrl("https://images.example.com/qr.png")
                .active(true)
                .featured(true)
                .build();
        when(shopProductService.findById(1L)).thenReturn(Optional.of(product));

        mockMvc.perform(get("/programmer/shop/1/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Guardar cambios")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Reemplazar foto")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/programmer/shop/1\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("https://images.example.com/qr.png")));
    }
}
