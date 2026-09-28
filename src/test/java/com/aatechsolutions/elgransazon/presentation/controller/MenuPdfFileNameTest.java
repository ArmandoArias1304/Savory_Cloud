package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.dto.MenuStyle;
import com.aatechsolutions.elgransazon.application.service.BusinessHoursService;
import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.ComplementService;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.ImageStorageService;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.application.service.MenuPdfService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComplementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Name of the generated carta file: it must be "Menu &lt;restaurante&gt;.pdf", with the accents of
 * the restaurant name preserved, because it is the name the user sees when saving or printing it.
 */
class MenuPdfFileNameTest {

    private SystemConfigurationService systemConfigurationService;
    private ItemMenuController controller;

    @BeforeEach
    void setUp() throws Exception {
        systemConfigurationService = mock(SystemConfigurationService.class);
        MenuPdfService menuPdfService = mock(MenuPdfService.class);
        when(menuPdfService.generateMenuPdf(any(MenuStyle.class), any())).thenReturn(new byte[] {1, 2, 3});

        controller = new ItemMenuController(
                mock(ItemMenuService.class),
                mock(CategoryService.class),
                mock(IngredientService.class),
                mock(ImageStorageService.class),
                mock(ComplementService.class),
                mock(ItemMenuComplementRepository.class),
                mock(BusinessHoursService.class),
                systemConfigurationService,
                menuPdfService,
                mock(DateTimeService.class));
    }

    @Test
    void theFileIsNamedAfterTheMenuAndTheRestaurant() throws Exception {
        when(systemConfigurationService.getConfiguration()).thenReturn(configuration("Quinta El Paraíso"));

        String disposition = dispositionOf();

        assertTrue(disposition.startsWith("inline"), "el PDF debe abrirse en el visor: " + disposition);
        assertTrue(disposition.contains("Menu%20Quinta%20El%20Para%C3%ADso.pdf"),
                "el archivo no se llama Menu + restaurante: " + disposition);
    }

    @Test
    void charactersThatAreInvalidInAFileNameAreReplaced() throws Exception {
        when(systemConfigurationService.getConfiguration()).thenReturn(configuration("Bar/Rincón: \"El\" *Faro*"));

        String disposition = dispositionOf();

        assertTrue(disposition.contains("Menu%20Bar%20Rinc%C3%B3n%20El%20Faro.pdf"),
                "el nombre del archivo conserva caracteres inválidos: " + disposition);
    }

    private String dispositionOf() throws Exception {
        ResponseEntity<byte[]> response = controller.printMenuPdf(Map.of(), new MockHttpServletRequest());

        assertEquals(200, response.getStatusCode().value());
        HttpHeaders headers = response.getHeaders();
        assertEquals("application/pdf", headers.getContentType().toString());
        assertEquals("no-store, must-revalidate", headers.getCacheControl());
        String disposition = headers.getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition != null && !disposition.isBlank(), "el PDF no lleva nombre de archivo");
        return disposition;
    }

    private SystemConfiguration configuration(String restaurantName) {
        SystemConfiguration configuration = new SystemConfiguration();
        configuration.setRestaurantName(restaurantName);
        return configuration;
    }
}
