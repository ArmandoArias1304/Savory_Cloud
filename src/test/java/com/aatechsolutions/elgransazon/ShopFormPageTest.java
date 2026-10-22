package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.ShopProductService;
import com.aatechsolutions.elgransazon.domain.entity.ShopBillingType;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    @DisplayName("el alta obliga a elegir entre pago único y cobro mensual")
    void createFormAsksForTheBillingType() throws Exception {
        String html = mockMvc.perform(get("/programmer/shop/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Forma de cobro")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"billingType\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Se suma a la mensualidad del restaurante")))
                .andReturn().getResponse().getContentAsString();

        int oneTime = html.indexOf("value=\"ONE_TIME\"");
        int monthly = html.indexOf("value=\"MONTHLY\"");
        assertTrue(oneTime > 0 && monthly > oneTime, "las dos formas de cobro se ofrecen");

        String oneTimeInput = html.substring(html.lastIndexOf("<input", oneTime),
                html.indexOf(">", oneTime) + 1);
        String monthlyInput = html.substring(html.lastIndexOf("<input", monthly),
                html.indexOf(">", monthly) + 1);
        assertTrue(oneTimeInput.contains("checked"),
                "un producto nuevo se cobra una sola vez salvo que se elija mensual");
        assertFalse(monthlyInput.contains("checked"), "mensual no viene preseleccionado");
    }

    @Test
    @DisplayName("el alta ofrece precio fijo, rango de precios y precio a evaluar")
    void createFormOffersThePricingModes() throws Exception {
        String html = mockMvc.perform(get("/programmer/shop/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tipo de precio")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"pricingMode\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"RANGE\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"QUOTE\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"maxPrice\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"priceNote\"")))
                .andReturn().getResponse().getContentAsString();

        int fixed = html.indexOf("value=\"FIXED\"");
        assertTrue(fixed > 0, "el precio fijo se ofrece como opción");
        assertTrue(html.substring(html.lastIndexOf("<input", fixed),
                        html.indexOf(">", fixed) + 1).contains("checked"),
                "el precio fijo viene preseleccionado");
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
                .billingType(ShopBillingType.MONTHLY)
                .active(true)
                .featured(true)
                .build();
        when(shopProductService.findById(1L)).thenReturn(Optional.of(product));

        String html = mockMvc.perform(get("/programmer/shop/1/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Guardar cambios")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Reemplazar foto")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/programmer/shop/1\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("https://images.example.com/qr.png")))
                .andReturn().getResponse().getContentAsString();

        int monthly = html.indexOf("value=\"MONTHLY\"");
        int oneTime = html.indexOf("value=\"ONE_TIME\"");
        assertTrue(monthly > 0 && oneTime > 0);
        assertTrue(html.substring(html.lastIndexOf("<input", monthly),
                        html.indexOf(">", monthly) + 1).contains("checked"),
                "al editar se conserva el cobro mensual elegido");
        assertFalse(html.substring(html.lastIndexOf("<input", oneTime),
                        html.indexOf(">", oneTime) + 1).contains("checked"),
                "el pago único no aparece marcado en un producto mensual");
    }
}
