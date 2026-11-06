package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.IngredientPurchase;
import com.aatechsolutions.elgransazon.domain.entity.IngredientPurchaseLine;
import com.aatechsolutions.elgransazon.domain.entity.IngredientPurchaseStatus;
import com.aatechsolutions.elgransazon.domain.entity.IngredientStockHistory;
import com.aatechsolutions.elgransazon.domain.repository.IngredientPurchaseRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngredientPurchaseServiceTest {

    private IngredientPurchaseRepository purchaseRepository;
    private IngredientService ingredientService;
    private IngredientPurchaseService service;
    private Company company;
    private Employee employee;

    @BeforeEach
    void setUp() {
        purchaseRepository = mock(IngredientPurchaseRepository.class);
        ingredientService = mock(IngredientService.class);
        service = new IngredientPurchaseService(purchaseRepository, new CfdiPurchaseXmlParser(), ingredientService);
        company = Company.builder().idCompany(1L).slug("ambar").name("Ambar").build();
        employee = Employee.builder().idEmpleado(5L).nombre("Ana").apellido("Ruiz").build();
        CompanyContext.setCurrentCompany(company);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void confirmedInvoiceCannotBeImportedAgain() {
        IngredientPurchase existing = IngredientPurchase.builder()
                .id(4L)
                .company(company)
                .uuid("ABC-123")
                .status(IngredientPurchaseStatus.CONFIRMED)
                .build();
        when(purchaseRepository.findByCompanyAndUuid(company, "ABC-123")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createDraft(cfdi(), employee))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya fue registrada");
    }

    @Test
    void xmlContentIsNotPersistedInThePurchaseRecord() {
        assertThatThrownBy(() -> IngredientPurchase.class.getDeclaredField("originalXml"))
                .isInstanceOf(NoSuchFieldException.class);
    }

    @Test
    void createsDraftForQuintaElParaisoCompany() {
        Company quinta = Company.builder()
                .idCompany(77L)
                .slug("quinta")
                .name("Quinta El Paraíso")
                .build();
        CompanyContext.setCurrentCompany(quinta);
        when(purchaseRepository.findByCompanyAndUuid(quinta, "ABC-123")).thenReturn(Optional.empty());
        when(purchaseRepository.saveAndFlush(any(IngredientPurchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IngredientPurchase saved = service.createDraft(cfdi(), employee);

        assertThat(saved.getCompany()).isEqualTo(quinta);
        assertThat(saved.getUuid()).isEqualTo("ABC-123");
        assertThat(saved.getIssuerName()).isEqualTo("Proveedor de prueba");
        assertThat(saved.getLines()).hasSize(1);
        assertThat(saved.getLines().get(0).getDescription()).isEqualTo("Tomate");
    }

    @Test
    void parsesTransferredVatForEachInvoiceConcept() {
        when(purchaseRepository.findByCompanyAndUuid(company, "ABC-123")).thenReturn(Optional.empty());
        when(purchaseRepository.saveAndFlush(any(IngredientPurchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IngredientPurchase saved = service.createDraft(cfdiWithVat(), employee);

        assertThat(saved.getLines()).hasSize(1);
        assertThat(saved.getLines().get(0).getVatAmount()).isEqualByComparingTo("16.00");
    }

    @Test
    void distributesInvoiceLevelVatAcrossConceptsWhenLineVatIsMissing() {
        when(purchaseRepository.findByCompanyAndUuid(company, "ABC-123")).thenReturn(Optional.empty());
        when(purchaseRepository.saveAndFlush(any(IngredientPurchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IngredientPurchase saved = service.createDraft(cfdiWithInvoiceLevelVat(), employee);

        assertThat(saved.getLines()).extracting(IngredientPurchaseLine::getVatAmount)
                .containsExactly(new BigDecimal("3.86"), new BigDecimal("17.24"));
    }

    @Test
    void reuploadingPendingInvoiceRefreshesParsedVatWithoutApplyingStock() {
        IngredientPurchaseLine oldLine = IngredientPurchaseLine.builder()
                .id(12L)
                .lineNumber(1)
                .description("Versión anterior")
                .purchasedQuantity(BigDecimal.ONE)
                .unitPrice(new BigDecimal("100.00"))
                .amount(new BigDecimal("100.00"))
                .build();
        IngredientPurchase pending = IngredientPurchase.builder()
                .id(6L)
                .company(company)
                .uuid("ABC-123")
                .issuerRfc("AAA010101AAA")
                .issuerName("Proveedor de prueba")
                .issuedAt(LocalDateTime.of(2026, 11, 5, 12, 0))
                .currency("MXN")
                .total(new BigDecimal("100.00"))
                .status(IngredientPurchaseStatus.PENDING)
                .lines(new ArrayList<>(List.of(oldLine)))
                .build();
        when(purchaseRepository.findByCompanyAndUuid(company, "ABC-123")).thenReturn(Optional.of(pending));
        when(purchaseRepository.saveAndFlush(any(IngredientPurchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IngredientPurchase refreshed = service.createDraft(cfdiWithVat(), employee);

        assertThat(refreshed.getLines()).hasSize(1);
        assertThat(refreshed.getLines().get(0).getDescription()).isEqualTo("Aceite");
        assertThat(refreshed.getLines().get(0).getVatAmount()).isEqualByComparingTo("16.00");
        verify(ingredientService, never()).addStockWithHistory(any(), any(), any(), any());
    }

    @Test
    void parsesMultiItemXmlForQuintaElParaiso() {
        Company quinta = Company.builder()
                .idCompany(77L)
                .slug("quinta")
                .name("Quinta El Paraíso")
                .build();
        CompanyContext.setCurrentCompany(quinta);
        when(purchaseRepository.findByCompanyAndUuid(quinta, "Q-2026-1001")).thenReturn(Optional.empty());
        when(purchaseRepository.saveAndFlush(any(IngredientPurchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IngredientPurchase saved = service.createDraft(multiConceptCfdi(), employee);

        assertThat(saved.getIssuerName()).isEqualTo("Distribuidora El Paraíso S.A. de C.V.");
        assertThat(saved.getLines()).hasSize(3);
        assertThat(saved.getLines()).extracting(IngredientPurchaseLine::getDescription)
                .containsExactly("Tomate rojo", "Cebolla morada", "Pimiento verde");
        assertThat(saved.getLines()).extracting(IngredientPurchaseLine::getPurchasedQuantity)
                .containsExactly(new BigDecimal("12.5"), new BigDecimal("8"), new BigDecimal("6"));
    }

    @Test
    void confirmationAddsConvertedQuantityAndNetUnitCost() {
        IngredientPurchaseLine line = IngredientPurchaseLine.builder()
                .id(12L)
                .lineNumber(1)
                .description("Tomate caja 20 kg")
                .purchasedQuantity(new BigDecimal("2"))
                .purchaseUnit("XBX")
                .amount(new BigDecimal("200.00"))
                .discount(new BigDecimal("10.00"))
                .build();
        IngredientPurchase purchase = IngredientPurchase.builder()
                .id(6L)
                .company(company)
                .uuid("ABC-123")
                .currency("MXN")
                .status(IngredientPurchaseStatus.PENDING)
                .lines(List.of(line))
                .build();
        Ingredient tomato = Ingredient.builder()
                .idIngredient(9L)
                .company(company)
                .name("Tomate")
                .unitOfMeasure("KG")
                .currency("MXN")
                .active(true)
                .build();
        IngredientStockHistory history = IngredientStockHistory.builder()
                .id(34L)
                .ingredient(tomato)
                .build();
        when(purchaseRepository.findByIdAndCompany(6L, company)).thenReturn(Optional.of(purchase));
        when(ingredientService.findById(9L)).thenReturn(Optional.of(tomato));
        when(ingredientService.addStockWithHistory(9L, new BigDecimal("40.00"),
                new BigDecimal("4.75"), employee)).thenReturn(history);
        when(purchaseRepository.save(any(IngredientPurchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IngredientPurchase confirmed = service.confirm(
                6L, List.of(12L), List.of(9L), List.of(new BigDecimal("20")), false, employee);

        assertThat(confirmed.getStatus()).isEqualTo(IngredientPurchaseStatus.CONFIRMED);
        assertThat(line.getStockQuantity()).isEqualByComparingTo("40.00");
        assertThat(line.getCostPerStockUnit()).isEqualByComparingTo("4.75");
        assertThat(line.getStockHistory()).isSameAs(history);
        verify(ingredientService).addStockWithHistory(9L,
                new BigDecimal("40.00"), new BigDecimal("4.75"), employee);
    }

    @Test
    void confirmationCanIncludeVatInTheSavedStockCost() {
        Ingredient tomato = Ingredient.builder()
                .idIngredient(9L)
                .company(company)
                .name("Tomate")
                .unitOfMeasure("KG")
                .currency("MXN")
                .active(true)
                .build();
        IngredientPurchaseLine line = IngredientPurchaseLine.builder()
                .id(12L)
                .purchasedQuantity(BigDecimal.ONE)
                .amount(new BigDecimal("100.00"))
                .discount(BigDecimal.ZERO)
                .vatAmount(new BigDecimal("16.00"))
                .build();
        IngredientPurchase purchase = IngredientPurchase.builder()
                .id(6L)
                .company(company)
                .uuid("ABC-123")
                .currency("MXN")
                .status(IngredientPurchaseStatus.PENDING)
                .lines(List.of(line))
                .build();
        IngredientStockHistory history = IngredientStockHistory.builder().id(34L).ingredient(tomato).build();
        when(purchaseRepository.findByIdAndCompany(6L, company)).thenReturn(Optional.of(purchase));
        when(ingredientService.findById(9L)).thenReturn(Optional.of(tomato));
        when(ingredientService.addStockWithHistory(9L, new BigDecimal("2.00"),
                new BigDecimal("58.00"), employee)).thenReturn(history);
        when(purchaseRepository.save(any(IngredientPurchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IngredientPurchase confirmed = service.confirm(
                6L, List.of(12L), List.of(9L), List.of(new BigDecimal("2")), true, employee);

        assertThat(confirmed.getIncludeVat()).isTrue();
        assertThat(line.getCostPerStockUnit()).isEqualByComparingTo("58.00");
        verify(ingredientService).addStockWithHistory(9L,
                new BigDecimal("2.00"), new BigDecimal("58.00"), employee);
    }

    @Test
    void cancellationVoidsLinkedStockEntriesAndKeepsInvoiceAsCancelled() {
        Ingredient tomato = Ingredient.builder().idIngredient(9L).company(company).name("Tomate").build();
        IngredientStockHistory history = IngredientStockHistory.builder().id(34L).ingredient(tomato).build();
        IngredientPurchaseLine line = IngredientPurchaseLine.builder()
                .id(12L)
                .ingredient(tomato)
                .stockHistory(history)
                .build();
        IngredientPurchase purchase = IngredientPurchase.builder()
                .id(6L)
                .company(company)
                .uuid("ABC-123")
                .status(IngredientPurchaseStatus.CONFIRMED)
                .lines(List.of(line))
                .build();
        when(purchaseRepository.findByIdAndCompany(6L, company)).thenReturn(Optional.of(purchase));
        when(purchaseRepository.save(any(IngredientPurchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IngredientPurchase cancelled = service.cancel(6L, employee);

        assertThat(cancelled.getStatus()).isEqualTo(IngredientPurchaseStatus.CANCELLED);
        assertThat(cancelled.getCancelledBy()).isSameAs(employee);
        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(cancelled.getCancellationReason()).contains("ABC-123");
        verify(ingredientService).voidStockHistory(9L, 34L,
                "Cancelación de factura CFDI ABC-123", employee);
    }

    @Test
    void cancellationRejectsLegacyInvoiceWithoutLinkedStockHistory() {
        Ingredient tomato = Ingredient.builder().idIngredient(9L).company(company).name("Tomate").build();
        IngredientPurchaseLine line = IngredientPurchaseLine.builder()
                .id(12L)
                .ingredient(tomato)
                .build();
        IngredientPurchase purchase = IngredientPurchase.builder()
                .id(6L)
                .company(company)
                .uuid("ABC-123")
                .status(IngredientPurchaseStatus.CONFIRMED)
                .lines(List.of(line))
                .build();
        when(purchaseRepository.findByIdAndCompany(6L, company)).thenReturn(Optional.of(purchase));

        assertThatThrownBy(() -> service.cancel(6L, employee))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no se puede cancelar automáticamente");

        verify(ingredientService, never()).voidStockHistory(any(), any(), any(), any());
    }

    private byte[] cfdi() {
        String xml = """
                <cfdi:Comprobante xmlns:cfdi="http://www.sat.gob.mx/cfd/4"
                                  xmlns:tfd="http://www.sat.gob.mx/TimbreFiscalDigital"
                                  Fecha="2026-11-05T12:00:00" Moneda="MXN" Total="232.00">
                  <cfdi:Emisor Rfc="AAA010101AAA" Nombre="Proveedor de prueba"/>
                  <cfdi:Conceptos>
                    <cfdi:Concepto Cantidad="1" ClaveUnidad="H87" Descripcion="Tomate"
                                   ValorUnitario="200.00" Importe="200.00"/>
                  </cfdi:Conceptos>
                  <cfdi:Complemento><tfd:TimbreFiscalDigital UUID="ABC-123"/></cfdi:Complemento>
                </cfdi:Comprobante>
                """;
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] multiConceptCfdi() {
        String xml = """
                <cfdi:Comprobante xmlns:cfdi="http://www.sat.gob.mx/cfd/4"
                                  xmlns:tfd="http://www.sat.gob.mx/TimbreFiscalDigital"
                                  Fecha="2026-11-05T12:00:00" Moneda="MXN" Total="1680.00">
                  <cfdi:Emisor Rfc="APA020305XYZ" Nombre="Distribuidora El Paraíso S.A. de C.V."/>
                  <cfdi:Conceptos>
                    <cfdi:Concepto Cantidad="12.5" Unidad="kg" ClaveUnidad="KGM" Descripcion="Tomate rojo" ValorUnitario="80.00" Importe="1000.00"/>
                    <cfdi:Concepto Cantidad="8" Unidad="kg" ClaveUnidad="KGM" Descripcion="Cebolla morada" ValorUnitario="50.00" Importe="400.00"/>
                    <cfdi:Concepto Cantidad="6" Unidad="kg" ClaveUnidad="KGM" Descripcion="Pimiento verde" ValorUnitario="46.67" Importe="280.02"/>
                  </cfdi:Conceptos>
                  <cfdi:Complemento><tfd:TimbreFiscalDigital UUID="Q-2026-1001"/></cfdi:Complemento>
                </cfdi:Comprobante>
                """;
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] cfdiWithVat() {
        String xml = """
                <cfdi:Comprobante xmlns:cfdi="http://www.sat.gob.mx/cfd/4"
                                                                                        xmlns:tfd="http://www.sat.gob.mx/TimbreFiscalDigital"
                                                                                        Fecha="2026-11-05T12:00:00" Moneda="MXN" Total="116.00">
                        <cfdi:Emisor Rfc="AAA010101AAA" Nombre="Proveedor de prueba"/>
                        <cfdi:Conceptos>
                                <cfdi:Concepto Cantidad="1" ClaveUnidad="H87" Descripcion="Aceite"
                                                                                         ValorUnitario="100.00" Importe="100.00">
                                        <cfdi:Impuestos>
                                                <cfdi:Traslados>
                                                        <cfdi:Traslado Base="100.00" Impuesto="002" TipoFactor="Tasa"
                                                                                                                        TasaOCuota="0.160000" Importe="16.00"/>
                                                </cfdi:Traslados>
                                        </cfdi:Impuestos>
                                </cfdi:Concepto>
                        </cfdi:Conceptos>
                        <cfdi:Complemento><tfd:TimbreFiscalDigital UUID="ABC-123"/></cfdi:Complemento>
                </cfdi:Comprobante>
                """;
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] cfdiWithInvoiceLevelVat() {
        String xml = """
                <cfdi:Comprobante xmlns:cfdi="http://www.sat.gob.mx/cfd/4"
                                                                                        xmlns:tfd="http://www.sat.gob.mx/TimbreFiscalDigital"
                                                                                        Fecha="2026-11-05T12:00:00" Moneda="MXN" Total="153.00">
                        <cfdi:Emisor Rfc="AAA010101AAA" Nombre="Proveedor de prueba"/>
                        <cfdi:Conceptos>
                                <cfdi:Concepto Cantidad="1" ClaveUnidad="E48" Descripcion="7UP"
                                                                                         ValorUnitario="24.14" Importe="24.14"/>
                                <cfdi:Concepto Cantidad="5" ClaveUnidad="E48" Descripcion="Agua Mineral"
                                                                                         ValorUnitario="21.552" Importe="107.76"/>
                        </cfdi:Conceptos>
                        <cfdi:Impuestos TotalImpuestosTrasladados="21.10">
                                <cfdi:Traslados>
                                        <cfdi:Traslado Base="131.90" Impuesto="002" TipoFactor="Tasa"
                                                                                                        TasaOCuota="0.160000" Importe="21.10"/>
                                </cfdi:Traslados>
                        </cfdi:Impuestos>
                        <cfdi:Complemento><tfd:TimbreFiscalDigital UUID="ABC-123"/></cfdi:Complemento>
                </cfdi:Comprobante>
                """;
        return xml.getBytes(StandardCharsets.UTF_8);
    }
}