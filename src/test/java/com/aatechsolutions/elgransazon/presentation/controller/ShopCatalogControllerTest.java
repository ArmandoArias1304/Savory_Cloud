package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.GlobalSystemConfigService;
import com.aatechsolutions.elgransazon.application.service.ShopProductService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.ShopBillingType;
import com.aatechsolutions.elgransazon.domain.entity.ShopPricingMode;
import com.aatechsolutions.elgransazon.domain.entity.ShopProduct;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Restaurant side of the shop: active products only and a WhatsApp link that
 * identifies the restaurant and the product.
 */
@ExtendWith(MockitoExtension.class)
class ShopCatalogControllerTest {

    @Mock
    private ShopProductService shopProductService;

    @Mock
    private GlobalSystemConfigService globalSystemConfigService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ShopCatalogController(shopProductService, globalSystemConfigService))
                .build();

        CompanyContext.setCurrentCompany(Company.builder()
                .idCompany(3L)
                .name("El Gran Sazón")
                .build());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("el catálogo muestra solo activos y arma el enlace con restaurante y producto")
    void catalogueBuildsWhatsAppLinks() throws Exception {
        ShopProduct product = product();
        when(shopProductService.findActive("qr", "Tarjetas")).thenReturn(List.of(product));
        when(shopProductService.findCategories(true)).thenReturn(List.of("Tarjetas"));
        when(globalSystemConfigService.getConfiguration())
                .thenReturn(GlobalSystemConfig.builder().shopWhatsappNumber("55 1234 5678").build());

        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(get("/admin/shop")
                        .param("search", "qr")
                        .param("category", "Tarjetas"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/shop/list"))
                .andExpect(model().attribute("products", List.of(product)))
                .andExpect(model().attribute("whatsappConfigured", true))
                .andExpect(model().attribute("restaurantName", "El Gran Sazón"))
                .andExpect(model().attribute("categories", List.of("Tarjetas")))
                .andExpect(model().attribute("search", "qr"))
                .andExpect(model().attribute("category", "Tarjetas"))
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<Long, String> links = (Map<Long, String>) result.getModelAndView().getModel().get("whatsappLinks");

        String link = links.get(1L);
        assertTrue(link.startsWith("https://wa.me/525512345678?text="));
        assertTrue(link.contains("El%20Gran%20Saz%C3%B3n"));
        assertTrue(link.contains("Tarjeta%20QR%20para%20mesa"));
    }

    @Test
    @DisplayName("sin WhatsApp configurado el catálogo avisa y no genera enlaces")
    void withoutWhatsappThereAreNoLinks() throws Exception {
        when(shopProductService.findActive(null, null)).thenReturn(List.of(product()));
        when(shopProductService.findCategories(true)).thenReturn(List.of());
        when(globalSystemConfigService.getConfiguration())
                .thenReturn(GlobalSystemConfig.builder().shopWhatsappNumber(null).build());

        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(get("/admin/shop"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("whatsappConfigured", false))
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<Long, String> links = (Map<Long, String>) result.getModelAndView().getModel().get("whatsappLinks");
        org.junit.jupiter.api.Assertions.assertNull(links.get(1L));
    }

    @Test
    @DisplayName("el detalle abre el producto publicado y cotiza su precio con descuento")
    void detailShowsThePublishedProduct() throws Exception {
        ShopProduct product = product();
        product.setDiscountPrice(new BigDecimal("199.00"));
        when(shopProductService.findById(1L)).thenReturn(java.util.Optional.of(product));
        when(globalSystemConfigService.getConfiguration())
                .thenReturn(GlobalSystemConfig.builder().shopWhatsappNumber("55 1234 5678").build());

        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(get("/admin/shop/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/shop/detail"))
                .andExpect(model().attribute("product", product))
                .andExpect(model().attribute("whatsappConfigured", true))
                .andExpect(model().attribute("restaurantName", "El Gran Sazón"))
                .andReturn();

        String link = (String) result.getModelAndView().getModel().get("whatsappLink");
        assertTrue(link.startsWith("https://wa.me/525512345678?text="));
        assertTrue(link.contains("Tarjeta%20QR%20para%20mesa"));
        // The request quotes the promotional price, not the crossed-out one.
        assertTrue(link.contains("199.00"));
        assertTrue(!link.contains("250.00"));
    }

    @Test
    @DisplayName("el mensaje de WhatsApp avisa cuando el cobro es mensual")
    void monthlyProductLinkStatesTheBillingPeriod() throws Exception {
        ShopProduct product = product();
        product.setPrice(new BigDecimal("150.00"));
        product.setBillingType(ShopBillingType.MONTHLY);
        when(shopProductService.findById(1L)).thenReturn(java.util.Optional.of(product));
        when(globalSystemConfigService.getConfiguration())
                .thenReturn(GlobalSystemConfig.builder().shopWhatsappNumber("55 1234 5678").build());

        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(get("/admin/shop/1"))
                .andExpect(status().isOk())
                .andReturn();

        String link = (String) result.getModelAndView().getModel().get("whatsappLink");
        String message = java.net.URLDecoder.decode(
                link.substring(link.indexOf("text=") + 5), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(message.contains("$ 150.00 / mes"), "el restaurante sabe que se cobra cada mes");
    }

    @Test
    @DisplayName("un producto de pago único no menciona una mensualidad")
    void oneTimeProductLinkHasNoMonthlySuffix() throws Exception {
        ShopProduct product = product();
        when(shopProductService.findById(1L)).thenReturn(java.util.Optional.of(product));
        when(globalSystemConfigService.getConfiguration())
                .thenReturn(GlobalSystemConfig.builder().shopWhatsappNumber("55 1234 5678").build());

        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(get("/admin/shop/1"))
                .andExpect(status().isOk())
                .andReturn();

        String link = (String) result.getModelAndView().getModel().get("whatsappLink");
        String message = java.net.URLDecoder.decode(
                link.substring(link.indexOf("text=") + 5), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(message.contains("$ 250.00"));
        assertTrue(!message.contains("/ mes"));
    }

    @Test
    @DisplayName("un rango se cotiza con mínimo y máximo en el mensaje")
    void rangeProductLinkShowsBothBounds() throws Exception {
        ShopProduct product = product();
        product.setPricingMode(ShopPricingMode.RANGE);
        product.setPrice(new BigDecimal("200.00"));
        product.setMaxPrice(new BigDecimal("500.00"));
        when(shopProductService.findById(1L)).thenReturn(java.util.Optional.of(product));
        when(globalSystemConfigService.getConfiguration())
                .thenReturn(GlobalSystemConfig.builder().shopWhatsappNumber("55 1234 5678").build());

        String message = decodedLink(mockMvc.perform(get("/admin/shop/1"))
                .andExpect(status().isOk())
                .andReturn());

        assertTrue(message.contains("$ 200.00 – $ 500.00"),
                "el restaurante ve de cuánto a cuánto va el precio");
    }

    @Test
    @DisplayName("un producto a evaluar pide cotización en el mensaje")
    void quoteProductLinkAsksForAPrice() throws Exception {
        ShopProduct product = product();
        product.setPricingMode(ShopPricingMode.QUOTE);
        product.setPrice(BigDecimal.ZERO);
        product.setPriceNote("Depende de la distancia");
        when(shopProductService.findById(1L)).thenReturn(java.util.Optional.of(product));
        when(globalSystemConfigService.getConfiguration())
                .thenReturn(GlobalSystemConfig.builder().shopWhatsappNumber("55 1234 5678").build());

        String message = decodedLink(mockMvc.perform(get("/admin/shop/1"))
                .andExpect(status().isOk())
                .andReturn());

        assertTrue(message.contains("precio a evaluar"));
        assertTrue(!message.contains("$ 0.00"), "nunca se envía un precio de cero");
    }

    private String decodedLink(org.springframework.test.web.servlet.MvcResult result) {
        String link = (String) result.getModelAndView().getModel().get("whatsappLink");
        return java.net.URLDecoder.decode(link.substring(link.indexOf("text=") + 5),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("un producto oculto no abre su detalle: regresa al catálogo")
    void hiddenProductRedirectsToTheCatalogue() throws Exception {
        ShopProduct hidden = product();
        hidden.setActive(false);
        when(shopProductService.findById(1L)).thenReturn(java.util.Optional.of(hidden));

        mockMvc.perform(get("/admin/shop/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/shop"));
    }

    @Test
    @DisplayName("un producto inexistente tampoco abre el detalle")
    void unknownProductRedirectsToTheCatalogue() throws Exception {
        when(shopProductService.findById(99L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/admin/shop/99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/shop"));
    }

    private ShopProduct product() {
        return ShopProduct.builder()
                .idProduct(1L)
                .name("Tarjeta QR para mesa")
                .description("Tarjeta impresa con código QR.")
                .price(new BigDecimal("250.00"))
                .category("Tarjetas")
                .imageUrl("https://images.example.com/qr.png")
                .active(true)
                .build();
    }
}
