package com.aatechsolutions.elgransazon.application.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CfdiPurchaseXmlParserTest {

    private final CfdiPurchaseXmlParser parser = new CfdiPurchaseXmlParser();

    @Test
    void parsesNamespacedCfdiAndItsConcepts() {
        CfdiPurchaseXmlParser.InvoiceData invoice = parser.parse(cfdi().getBytes(StandardCharsets.UTF_8));

        assertThat(invoice.uuid()).isEqualTo("ABC-123");
        assertThat(invoice.issuerRfc()).isEqualTo("AAA010101AAA");
        assertThat(invoice.issuerName()).isEqualTo("Proveedor de prueba");
        assertThat(invoice.currency()).isEqualTo("MXN");
        assertThat(invoice.total()).isEqualByComparingTo("232.00");
        assertThat(invoice.concepts()).hasSize(1);
        assertThat(invoice.concepts().get(0).description()).isEqualTo("Tomate saladet caja 20 kg");
        assertThat(invoice.concepts().get(0).quantity()).isEqualByComparingTo("2");
        assertThat(invoice.concepts().get(0).unit()).isEqualTo("XBX");
        assertThat(invoice.concepts().get(0).amount()).isEqualByComparingTo("200.00");
        assertThat(invoice.concepts().get(0).discount()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsDtdAndExternalEntities() {
        String xml = "<!DOCTYPE foo [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]>" + cfdi();

        assertThatThrownBy(() -> parser.parse(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String cfdi() {
        return """
                <cfdi:Comprobante xmlns:cfdi="http://www.sat.gob.mx/cfd/4"
                                  xmlns:tfd="http://www.sat.gob.mx/TimbreFiscalDigital"
                                  Fecha="2026-11-05T12:00:00" Moneda="MXN" Total="232.00">
                  <cfdi:Emisor Rfc="AAA010101AAA" Nombre="Proveedor de prueba"/>
                  <cfdi:Conceptos>
                    <cfdi:Concepto ClaveProdServ="50202306" Cantidad="2" ClaveUnidad="XBX"
                                   Descripcion="Tomate saladet caja 20 kg" ValorUnitario="100.00"
                                   Importe="200.00"/>
                  </cfdi:Conceptos>
                  <cfdi:Complemento>
                    <tfd:TimbreFiscalDigital UUID="ABC-123"/>
                  </cfdi:Complemento>
                </cfdi:Comprobante>
                """;
    }
}