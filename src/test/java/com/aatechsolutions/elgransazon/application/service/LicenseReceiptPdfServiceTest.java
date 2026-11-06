package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.GlobalSystemConfig;
import com.aatechsolutions.elgransazon.domain.entity.LicenseEvent;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.entity.SystemLicense;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LicenseReceiptPdfServiceTest {

    private final LicenseReceiptPdfService service =
            new LicenseReceiptPdfService(mock(RestTemplate.class), new CloudflareImagesUrlHelper());

    @Test
    void generatesPdfWithPaymentAndCustomerDetails() throws Exception {
        LicenseEvent event = LicenseEvent.builder()
                .id(42L)
                .licenseId(7L)
                .eventType(LicenseEvent.EventType.RENEWED)
                .eventDate(LocalDateTime.of(2026, 11, 4, 21, 17))
                .description("Licencia renovada por 12 meses")
                .performedBy("admin")
                .amount(1591.00)
                .months(12)
                .paymentMethod(PaymentMethodType.TRANSFER)
                .build();
        SystemLicense license = SystemLicense.builder()
                .id(7L)
                .ownerName("María González")
                .ownerEmail("maria@example.com")
                .ownerPhone("5551234567")
                .ownerRfc("GOMA800101AA1")
                .restaurantName("Café del Centro")
                .build();
        SystemConfiguration restaurant = SystemConfiguration.builder()
                .restaurantName("Café del Centro")
                .phone("5559876543")
                .email("contacto@cafedelcentro.mx")
                .rfc("CCD010101AB2")
                .address("Av. Reforma 100, Centro")
                .build();
        GlobalSystemConfig system = GlobalSystemConfig.builder()
                .systemName("SavoryCloud")
                .systemSlogan("Sistema de Gestión Restaurantera")
                .build();

        byte[] bytes = service.generateReceipt(event, license, restaurant, system);

        assertThat(bytes).startsWith("%PDF-".getBytes());
        try (PdfDocument pdf = new PdfDocument(new PdfReader(new ByteArrayInputStream(bytes)))) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            String text = PdfTextExtractor.getTextFromPage(pdf.getFirstPage());
            assertThat(text)
                    .contains("SavoryCloud", "Sistema de Gestión Restaurantera",
                            "RECIBO DE PAGO", "Folio", "LIC-42",
                            "PAGADO", "TOTAL PAGADO", "Pesos mexicanos (MXN)",
                            "DATOS DEL PAGO", "DATOS DEL CLIENTE", "DATOS DEL RESTAURANTE",
                            "DETALLE DEL PAGO",
                            "María González", "maria@example.com", "5551234567",
                            "GOMA800101AA1", "Licencia renovada por 12 meses", "admin",
                            "Renovación de licencia", "12 meses", "FORMA DE PAGO", "Transferencia",
                            "1,591.00", "Centro", "5559876543",
                            "contacto@cafedelcentro.mx", "CCD010101AB2", "Av. Reforma 100",
                            "no sustituye una factura fiscal",
                            "Documento generado electrónicamente");
        }
    }

    @Test
    void legacyEventWithoutPaymentMethodStillShowsNotRegistered() throws Exception {
        LicenseEvent event = LicenseEvent.builder()
                .id(43L)
                .licenseId(7L)
                .eventType(LicenseEvent.EventType.CREATED)
                .eventDate(LocalDateTime.of(2026, 1, 15, 10, 0))
                .description("Licencia creada")
                .amount(500.0)
                .months(1)
                .build();
        SystemLicense license = SystemLicense.builder()
                .id(7L)
                .ownerName("Cliente sin método")
                .build();

        byte[] bytes = service.generateReceipt(event, license, null, null);

        try (PdfDocument pdf = new PdfDocument(new PdfReader(new ByteArrayInputStream(bytes)))) {
            String text = PdfTextExtractor.getTextFromPage(pdf.getFirstPage());
            assertThat(text)
                    .contains("No registrada", "Alta de licencia", "LIC-43", "1 mes");
        }
    }
}
