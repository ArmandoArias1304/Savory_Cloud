package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.BusinessHoursService;
import com.aatechsolutions.elgransazon.application.service.CloudflareImagesUrlHelper;
import com.aatechsolutions.elgransazon.application.service.GlobalSystemConfigService;
import com.aatechsolutions.elgransazon.application.service.ImageStorageService;
import com.aatechsolutions.elgransazon.application.service.LicenseService;
import com.aatechsolutions.elgransazon.application.service.SocialNetworkService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * El canvas del QR necesita el logo servido desde el mismo origen para no
 * contaminar la imagen; este proxy solo debe devolver el logo configurado.
 */
@ExtendWith(MockitoExtension.class)
class SystemConfigurationQrLogoTest {

    @Mock
    private SystemConfigurationService configurationService;
    @Mock
    private BusinessHoursService businessHoursService;
    @Mock
    private SocialNetworkService socialNetworkService;
    @Mock
    private LicenseService licenseService;
    @Mock
    private ImageStorageService imageStorageService;
    @Mock
    private GlobalSystemConfigService globalSystemConfigService;
    @Mock
    private CloudflareImagesUrlHelper cloudinaryUrl;
    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private SystemConfigurationController controller;

    @Test
    void servesTheConfiguredRestaurantLogoFromTheSameOrigin() {
        byte[] bytes = {1, 2, 3};
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);

        when(configurationService.getConfiguration())
                .thenReturn(SystemConfiguration.builder()
                        .restaurantLogoUrl("https://imagedelivery.net/account/logo/original")
                        .build());
        when(cloudinaryUrl.logoHero(anyString()))
                .thenReturn("https://imagedelivery.net/account/logo/w=600");
        when(restTemplate.getForEntity("https://imagedelivery.net/account/logo/w=600", byte[].class))
                .thenReturn(new ResponseEntity<>(bytes, headers, HttpStatus.OK));

        ResponseEntity<byte[]> response = controller.qrLogo();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(bytes, response.getBody());
        assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
    }

    @Test
    void returnsNotFoundWhenNoLogoIsConfigured() {
        when(configurationService.getConfiguration())
                .thenReturn(SystemConfiguration.builder().restaurantLogoUrl(null).build());

        ResponseEntity<byte[]> response = controller.qrLogo();

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void returnsBadGatewayWhenTheCdnFails() {
        when(configurationService.getConfiguration())
                .thenReturn(SystemConfiguration.builder()
                        .restaurantLogoUrl("https://imagedelivery.net/account/logo/original")
                        .build());
        when(cloudinaryUrl.logoHero(anyString()))
                .thenReturn("https://imagedelivery.net/account/logo/w=600");
        when(restTemplate.getForEntity(anyString(), eq(byte[].class)))
                .thenThrow(new RuntimeException("cdn down"));

        ResponseEntity<byte[]> response = controller.qrLogo();

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
    }
}
