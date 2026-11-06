package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.FacturamaService;
import com.aatechsolutions.elgransazon.domain.entity.FacturamaConfig;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetailComplement;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.Payment;
import com.aatechsolutions.elgransazon.domain.entity.PaymentDetail;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the two concept modes of the autofactura:
 * - DETAILED: one CFDI concept per account line (current behaviour).
 * - SINGLE: the whole account folded into one "Venta de alimentos y bebidas"
 *   concept, keeping the same tax-included total and the order-level discount.
 */
class FacturamaServiceConceptModeTest {

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
    void resumidaFoldsEveryLineIntoOneConceptWithTheSameTotal() throws Exception {
        Payment payment = account(new BigDecimal("232.00"), BigDecimal.ZERO);

        JsonNode payload = payloadFor(payment, false);

        assertEquals(1, payload.path("Items").size(), "la factura resumida lleva un solo concepto");
        JsonNode item = payload.path("Items").get(0);
        assertEquals("Venta de alimentos y bebidas", item.path("Description").asText());
        assertEquals("90101500", item.path("ProductCode").asText());
        assertEquals(1, item.path("Quantity").asInt());
        assertEquals(0, new BigDecimal("232.00").compareTo(new BigDecimal(item.path("Total").asText())));
        assertEquals(0, new BigDecimal("200.00").compareTo(new BigDecimal(item.path("Subtotal").asText())));
        assertEquals(0, new BigDecimal("32.00")
                .compareTo(new BigDecimal(item.path("Taxes").get(0).path("Total").asText())));
        assertFalse(item.has("Discount"));
        assertFalse(payload.has("Discount"));
    }

    @Test
    void detalladaKeepsOneConceptPerAccountLine() throws Exception {
        Payment payment = account(new BigDecimal("232.00"), BigDecimal.ZERO);

        JsonNode payload = payloadFor(payment, true);

        assertEquals(2, payload.path("Items").size(), "la factura detallada conserva una línea por producto");
        assertEquals("Pizza", payload.path("Items").get(0).path("Description").asText());
        assertEquals("Refresco", payload.path("Items").get(1).path("Description").asText());
        assertEquals(0, new BigDecimal("116.00")
                .compareTo(new BigDecimal(payload.path("Items").get(0).path("Total").asText())));
    }

    @Test
    void resumidaStillDeclaresTheAccountDiscount() throws Exception {
        Payment payment = account(new BigDecimal("232.00"), new BigDecimal("11.60"));

        JsonNode payload = payloadFor(payment, false);

        JsonNode item = payload.path("Items").get(0);
        assertEquals(0, new BigDecimal("200.00").compareTo(new BigDecimal(item.path("Subtotal").asText())),
                "el subtotal declarado es el precio antes del descuento, sin IVA");
        assertEquals(0, new BigDecimal("10.00").compareTo(new BigDecimal(item.path("Discount").asText())),
                "descuento sin IVA del concepto");
        assertEquals(0, new BigDecimal("220.40").compareTo(new BigDecimal(item.path("Total").asText())),
                "total después del descuento");
        assertEquals(0, new BigDecimal("190.00")
                .compareTo(new BigDecimal(item.path("Taxes").get(0).path("Base").asText())));
        assertEquals(0, new BigDecimal("30.40")
                .compareTo(new BigDecimal(item.path("Taxes").get(0).path("Total").asText())));
        assertEquals(0, new BigDecimal("10.00").compareTo(new BigDecimal(payload.path("Discount").asText())));
    }

    @Test
    void resumidaFoldsAnOrderWithComplementsAndDeliveryIntoOneConcept() throws Exception {
        JsonNode payload = payloadFor(deliveryOrder(), false);

        assertEquals(1, payload.path("Items").size(),
                "la orden completa también se resume en un solo concepto");
        JsonNode item = payload.path("Items").get(0);
        assertEquals("Venta de alimentos y bebidas y servicio de entrega a domicilio",
                item.path("Description").asText());
        assertEquals(0, new BigDecimal("150.00").compareTo(new BigDecimal(item.path("Total").asText())),
                "platillo + complemento + envío");
        assertEquals(0, new BigDecimal("129.31").compareTo(new BigDecimal(item.path("Subtotal").asText())));
        assertEquals(0, new BigDecimal("20.69")
                .compareTo(new BigDecimal(item.path("Taxes").get(0).path("Total").asText())));
    }

    /**
     * Captures and parses the payload sent to Facturama for one account.
     */
    private JsonNode payloadFor(Payment payment, boolean itemized) throws Exception {
        ResponseEntity<JsonNode> response = mock(ResponseEntity.class);
        when(response.getBody()).thenReturn(objectMapper.readTree(
                "{\"Id\":\"cfdi-1\",\"Complement\":{\"TaxStamp\":{\"Uuid\":\"uuid-1\"}}}"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(
                eq("https://apisandbox.facturama.mx/api-lite/3/cfdis"),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(JsonNode.class))).thenReturn(response);

        service.createCfdi(payment, readyConfig(), "XAXX010101000", "CLIENTE PRUEBA",
                "616", "G03", "45000", itemized);

        return objectMapper.readTree(captor.getValue().getBody());
    }

    private JsonNode payloadFor(Order order, boolean itemized) throws Exception {
        ResponseEntity<JsonNode> response = mock(ResponseEntity.class);
        when(response.getBody()).thenReturn(objectMapper.readTree(
                "{\"Id\":\"cfdi-2\",\"Complement\":{\"TaxStamp\":{\"Uuid\":\"uuid-2\"}}}"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(
                eq("https://apisandbox.facturama.mx/api-lite/3/cfdis"),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(JsonNode.class))).thenReturn(response);

        service.createCfdi(order, readyConfig(), "XAXX010101000", "CLIENTE PRUEBA",
                "616", "G03", "45000", itemized);

        return objectMapper.readTree(captor.getValue().getBody());
    }

    private Order deliveryOrder() {
        OrderDetail pizza = OrderDetail.builder()
                .itemName("Pizza")
                .quantity(1)
                .unitPrice(new BigDecimal("100.00"))
                .subtotal(new BigDecimal("100.00"))
                .build();
        OrderDetailComplement extraCheese = OrderDetailComplement.builder()
                .complementName("Queso extra")
                .quantity(1)
                .unitPrice(new BigDecimal("16.00"))
                .subtotal(new BigDecimal("16.00"))
                .build();
        pizza.setSelectedComplements(new java.util.ArrayList<>(List.of(extraCheese)));

        return Order.builder()
                .orderNumber("ORD-20260906-002")
                .orderType(OrderType.DELIVERY)
                .total(new BigDecimal("150.00"))
                .deliveryCost(new BigDecimal("34.00"))
                .orderDiscount(BigDecimal.ZERO)
                .paymentMethod(PaymentMethodType.CASH)
                .orderDetails(new java.util.ArrayList<>(List.of(pizza)))
                .build();
    }

    private Payment account(BigDecimal total, BigDecimal orderDiscount) {
        return Payment.builder()
                .paymentFolio("ORD-20260906-001-01")
                .total(total)
                .orderDiscount(orderDiscount)
                .paymentMethod(PaymentMethodType.CASH)
                .paymentDetails(List.of(
                        PaymentDetail.builder()
                                .itemName("Pizza")
                                .quantity(BigDecimal.ONE)
                                .total(new BigDecimal("116.00"))
                                .build(),
                        PaymentDetail.builder()
                                .itemName("Refresco")
                                .quantity(BigDecimal.ONE)
                                .total(new BigDecimal("116.00"))
                                .build()))
                .build();
    }

    private FacturamaConfig readyConfig() {
        return FacturamaConfig.builder()
                .enabled(true)
                .csdUploaded(true)
                .legalDataConfigured(true)
                .rfc("AAA010101AAA")
                .legalName("RESTAURANTE PRUEBA SA DE CV")
                .fiscalRegime("601")
                .expeditionPlace("45000")
                .build();
    }
}
