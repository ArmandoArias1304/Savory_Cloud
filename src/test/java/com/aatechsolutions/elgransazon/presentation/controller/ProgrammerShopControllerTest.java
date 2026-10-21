package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.GlobalSystemConfigService;
import com.aatechsolutions.elgransazon.application.service.ImageStorageService;
import com.aatechsolutions.elgransazon.application.service.ShopProductService;
import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.ShopProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Programmer shop: the catalogue is global and every product needs a photo.
 */
@ExtendWith(MockitoExtension.class)
class ProgrammerShopControllerTest {

    @Mock
    private ShopProductService shopProductService;

    @Mock
    private ImageStorageService imageStorageService;

    @Mock
    private GlobalSystemConfigService globalSystemConfigService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProgrammerShopController(
                        shopProductService, imageStorageService, globalSystemConfigService))
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("el listado entrega productos, filtros y el WhatsApp configurado")
    void listShowsProductsAndSettings() throws Exception {
        ShopProduct product = product(1L, true);
        when(shopProductService.findForProgrammer("qr", "Tarjetas", true)).thenReturn(List.of(product));
        when(shopProductService.findCategories(false)).thenReturn(List.of("Tarjetas"));
        when(shopProductService.countAll()).thenReturn(3L);
        when(shopProductService.countActive()).thenReturn(2L);
        when(globalSystemConfigService.getConfiguration())
                .thenReturn(GlobalSystemConfig.builder().shopWhatsappNumber("525512345678").build());

        mockMvc.perform(get("/programmer/shop")
                        .param("search", "qr")
                        .param("category", "Tarjetas")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("programmer/shop/list"))
                .andExpect(model().attribute("products", List.of(product)))
                .andExpect(model().attribute("search", "qr"))
                .andExpect(model().attribute("category", "Tarjetas"))
                .andExpect(model().attribute("active", true))
                .andExpect(model().attribute("whatsappNumber", "525512345678"))
                .andExpect(model().attribute("totalCount", 3L))
                .andExpect(model().attribute("activeCount", 2L));
    }

    @Test
    @DisplayName("guardar el WhatsApp regresa al listado")
    void updatesWhatsappNumber() throws Exception {
        mockMvc.perform(post("/programmer/shop/whatsapp")
                        .param("shopWhatsappNumber", "55 1234 5678"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/programmer/shop"));

        verify(globalSystemConfigService).updateShopWhatsappNumber("55 1234 5678");
    }

    @Test
    @DisplayName("un WhatsApp sin lada suficiente no se guarda")
    void rejectsAnInvalidWhatsappNumber() throws Exception {
        mockMvc.perform(post("/programmer/shop/whatsapp")
                        .param("shopWhatsappNumber", "12345"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/programmer/shop"));

        verify(globalSystemConfigService, never()).updateShopWhatsappNumber(any());
    }

    @Test
    @DisplayName("el alta exige una foto y no guarda el producto sin ella")
    void createRequiresAPhoto() throws Exception {
        when(shopProductService.findCategories(false)).thenReturn(List.of());

        mockMvc.perform(multipart("/programmer/shop")
                        .param("name", "Tarjeta QR")
                        .param("price", "250.00")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("programmer/shop/form"))
                .andExpect(model().attributeHasFieldErrors("product", "imageUrl"));

        verify(shopProductService, never()).create(any());
    }

    @Test
    @DisplayName("con foto válida se sube la imagen y se guarda el producto")
    void createUploadsThePhoto() throws Exception {
        MockMultipartFile photo = new MockMultipartFile(
                "imageFile", "tarjeta.png", "image/png", new byte[]{1, 2, 3});
        when(imageStorageService.isValidImage(photo)).thenReturn(true);
        when(imageStorageService.saveImage(eq(photo), eq("shop-products"), eq("Tarjeta QR")))
                .thenReturn("https://images.example.com/tarjeta.png");
        when(shopProductService.create(any(ShopProduct.class))).thenAnswer(invocation -> {
            ShopProduct created = invocation.getArgument(0);
            created.setIdProduct(7L);
            return created;
        });

        mockMvc.perform(multipart("/programmer/shop")
                        .file(photo)
                        .param("name", "Tarjeta QR")
                        .param("price", "250.00")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/programmer/shop"));

        ArgumentCaptor<ShopProduct> captor = ArgumentCaptor.forClass(ShopProduct.class);
        verify(shopProductService).create(captor.capture());
        assertEquals("https://images.example.com/tarjeta.png", captor.getValue().getImageUrl());
        assertTrue(captor.getValue().getActive());
    }

    @Test
    @DisplayName("una imagen inválida se rechaza con mensaje de campo")
    void rejectsInvalidImages() throws Exception {
        MockMultipartFile photo = new MockMultipartFile(
                "imageFile", "archivo.txt", "text/plain", new byte[]{1});
        when(imageStorageService.isValidImage(photo)).thenReturn(false);
        when(shopProductService.findCategories(false)).thenReturn(List.of());

        mockMvc.perform(multipart("/programmer/shop")
                        .file(photo)
                        .param("name", "Tarjeta QR")
                        .param("price", "250.00"))
                .andExpect(status().isOk())
                .andExpect(view().name("programmer/shop/form"))
                .andExpect(model().attributeHasFieldErrors("product", "imageUrl"));

        verify(shopProductService, never()).create(any());
    }

    @Test
    @DisplayName("editar sin archivo conserva la foto actual")
    void updateKeepsTheExistingPhoto() throws Exception {
        ShopProduct existing = product(1L, true);
        existing.setImageUrl("https://images.example.com/old.png");
        when(shopProductService.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(multipart("/programmer/shop/1")
                        .param("name", "Tarjeta QR v2")
                        .param("price", "300.00")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/programmer/shop"));

        ArgumentCaptor<ShopProduct> captor = ArgumentCaptor.forClass(ShopProduct.class);
        verify(shopProductService).update(eq(1L), captor.capture());
        assertEquals("https://images.example.com/old.png", captor.getValue().getImageUrl());
        verify(imageStorageService, never()).deleteImage(anyString());
    }

    @Test
    @DisplayName("al reemplazar la foto se borra la anterior")
    void updateDeletesThePreviousPhoto() throws Exception {
        ShopProduct existing = product(1L, true);
        existing.setImageUrl("https://images.example.com/old.png");
        when(shopProductService.findById(1L)).thenReturn(Optional.of(existing));

        MockMultipartFile photo = new MockMultipartFile(
                "imageFile", "nueva.jpg", "image/jpeg", new byte[]{1});
        when(imageStorageService.isValidImage(photo)).thenReturn(true);
        when(imageStorageService.saveImage(eq(photo), eq("shop-products"), anyString()))
                .thenReturn("https://images.example.com/new.jpg");

        mockMvc.perform(multipart("/programmer/shop/1")
                        .file(photo)
                        .param("name", "Tarjeta QR v2")
                        .param("price", "300.00")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/programmer/shop"));

        verify(shopProductService).update(eq(1L), any(ShopProduct.class));
        verify(imageStorageService).deleteImage("https://images.example.com/old.png");
    }

    @Test
    @DisplayName("publicar/ocultar y eliminar regresan al listado")
    void toggleAndDeleteReturnToTheList() throws Exception {
        when(shopProductService.toggleActive(1L)).thenReturn(product(1L, false));
        mockMvc.perform(post("/programmer/shop/1/toggle"))
                .andExpect(redirectedUrl("/programmer/shop"));
        verify(shopProductService).toggleActive(1L);

        ShopProduct existing = product(1L, true);
        existing.setImageUrl("https://images.example.com/old.png");
        when(shopProductService.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/programmer/shop/1/delete"))
                .andExpect(redirectedUrl("/programmer/shop"));
        verify(shopProductService).delete(1L);
        verify(imageStorageService).deleteImage("https://images.example.com/old.png");
    }

    @Test
    @DisplayName("editar un producto inexistente regresa al listado")
    void editUnknownProductRedirects() throws Exception {
        when(shopProductService.findById(404L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/programmer/shop/404/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/programmer/shop"));
    }

    private ShopProduct product(Long id, boolean active) {
        return ShopProduct.builder()
                .idProduct(id)
                .name("Tarjeta QR para mesa")
                .description("Tarjeta impresa con código QR.")
                .price(new BigDecimal("250.00"))
                .category("Tarjetas")
                .imageUrl("https://images.example.com/qr.png")
                .active(active)
                .featured(true)
                .sortOrder(0)
                .build();
    }
}
