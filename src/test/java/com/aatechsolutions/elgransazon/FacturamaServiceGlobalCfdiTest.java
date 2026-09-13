package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.FacturamaService;
import com.aatechsolutions.elgransazon.domain.entity.FacturamaConfig;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.repository.FacturamaConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verifies the factura global (público en general) payload built by
 * FacturamaService.createGlobalCfdi:
 *   - receptor genérico XAXX010101000 / PUBLICO EN GENERAL / régimen 616 / uso S01
 *   - InformacionGlobal con periodicidad, mes y año
 *   - un concepto por ticket (con su folio) y IVA 16% desglosado
 *   - PaymentForm = forma de pago de mayor monto entre las operaciones incluidas
 */
class FacturamaServiceGlobalCfdiTest {

    private FacturamaService service;
    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        FacturamaConfigRepository configRepository = mock(FacturamaConfigRepository.class);
        restTemplate = mock(RestTemplate.class);
        service = new FacturamaService(configRepository, objectMapper, restTemplate);
        ReflectionTestUtils.setField(service, "facturamaUser", "user");
        ReflectionTestUtils.setField(service, "facturamaPassword", "pass");
        ReflectionTestUtils.setField(service, "defaultLiveMode", false);
    }

    @Test
    void globalCfdiPayloadUsesPublicoEnGeneralAndOneConceptPerTicket() throws Exception {
        FacturamaConfig config = FacturamaConfig.builder()
                .enabled(true)
                .csdUploaded(true)
                .legalDataConfigured(true)
                .rfc("AAA010101AAA")
                .legalName("RESTAURANTE PRUEBA SA DE CV")
                .fiscalRegime("601")
                .expeditionPlace("45000")
                .build();

        // CASH total = 350, CREDIT_CARD total = 200 → PaymentForm debe ser "01" (Efectivo)
        List<FacturamaService.GlobalCfdiTicket> tickets = List.of(
                new FacturamaService.GlobalCfdiTicket(
                        "Venta de alimentos y bebidas - ORD-20260906-001",
                        new BigDecimal("100.00"), PaymentMethodType.CASH),
                new FacturamaService.GlobalCfdiTicket(
                        "Venta de alimentos y bebidas - ORD-20260906-001-02",
                        new BigDecimal("200.00"), PaymentMethodType.CREDIT_CARD),
                new FacturamaService.GlobalCfdiTicket(
                        "Venta de alimentos y bebidas - ORD-20260906-002",
                        new BigDecimal("250.00"), PaymentMethodType.CASH));

        JsonNode responseBody = objectMapper.readTree(
                "{\"Id\":\"cfdi-global-1\",\"Complement\":{\"TaxStamp\":{\"Uuid\":\"uuid-global-1\"}}}");
        ResponseEntity<JsonNode> response = mock(ResponseEntity.class);
        when(response.getBody()).thenReturn(responseBody);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(
                eq("https://apisandbox.facturama.mx/api-lite/3/cfdis"),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(JsonNode.class))).thenReturn(response);

        Map<String, String> result = service.createGlobalCfdi(
                config, tickets, "01", 9, 2026, "GLOBAL-20260906");

        assertEquals("cfdi-global-1", result.get("cfdi_id"));
        assertEquals("uuid-global-1", result.get("cfdi_uuid"));

        JsonNode payload = objectMapper.readTree(captor.getValue().getBody());
        assertEquals("I", payload.path("CfdiType").asText());
        assertEquals("PUE", payload.path("PaymentMethod").asText());
        assertEquals("01", payload.path("PaymentForm").asText(), "forma de pago de mayor monto (CASH)");
        assertEquals("45000", payload.path("ExpeditionPlace").asText());
        assertEquals("GLOBAL-20260906", payload.path("Folio").asText());

        // Información Global (periodo cubierto)
        assertEquals("01", payload.path("GlobalInformation").path("Periodicity").asText());
        assertEquals("09", payload.path("GlobalInformation").path("Months").asText());
        assertEquals(2026, payload.path("GlobalInformation").path("Year").asInt());

        // Emisor = datos fiscales de la empresa
        assertEquals("AAA010101AAA", payload.path("Issuer").path("Rfc").asText());
        assertEquals("RESTAURANTE PRUEBA SA DE CV", payload.path("Issuer").path("Name").asText());
        assertEquals("601", payload.path("Issuer").path("FiscalRegime").asText());

        // Receptor = público en general (RFC genérico SAT)
        assertEquals("XAXX010101000", payload.path("Receiver").path("Rfc").asText());
        assertEquals("PUBLICO EN GENERAL", payload.path("Receiver").path("Name").asText());
        assertEquals("616", payload.path("Receiver").path("FiscalRegime").asText());
        assertEquals("S01", payload.path("Receiver").path("CfdiUse").asText());
        assertEquals("45000", payload.path("Receiver").path("TaxZipCode").asText());

        // Un concepto por ticket, sin descuento, IVA desglosado
        assertEquals(3, payload.path("Items").size());
        JsonNode first = payload.path("Items").get(0);
        assertEquals("Venta de alimentos y bebidas - ORD-20260906-001", first.path("Description").asText());
        assertEquals("90101500", first.path("ProductCode").asText());
        assertEquals("1", first.path("Quantity").asText().trim());
        assertEquals(0, new BigDecimal("100.00").compareTo(new BigDecimal(first.path("Total").asText())));
        assertFalse(first.has("Discount"));
        assertEquals(1, first.path("Taxes").size());
        assertEquals("IVA", first.path("Taxes").get(0).path("Name").asText());
        assertEquals("0.16", first.path("Taxes").get(0).path("Rate").asText());

        // IVA desglosado: Subtotal = Total / 1.16 = 86.21, IVA = 13.79
        assertEquals(0, new BigDecimal("86.21").compareTo(new BigDecimal(first.path("Subtotal").asText())));
        assertEquals(0, new BigDecimal("13.79").compareTo(new BigDecimal(first.path("Taxes").get(0).path("Total").asText())));
    }

    @Test
    void globalCfdiRejectsEmptyTicketList() {
        FacturamaConfig config = FacturamaConfig.builder()
                .enabled(true)
                .csdUploaded(true)
                .legalDataConfigured(true)
                .rfc("AAA010101AAA")
                .legalName("RESTAURANTE PRUEBA SA DE CV")
                .fiscalRegime("601")
                .expeditionPlace("45000")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createGlobalCfdi(config, List.of(), "04", 9, 2026, "GLOBAL-202609"));
        assertTrue(ex.getMessage().contains("No hay operaciones pendientes"));
        verifyNoInteractions(restTemplate);
    }
}