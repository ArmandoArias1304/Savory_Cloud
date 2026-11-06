package com.aatechsolutions.elgransazon.application.service;

import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class CfdiPurchaseXmlParser {

    private static final String CFDI_33_NAMESPACE = "http://www.sat.gob.mx/cfd/3";
    private static final String CFDI_40_NAMESPACE = "http://www.sat.gob.mx/cfd/4";
    private static final String TIMBRE_NAMESPACE = "http://www.sat.gob.mx/TimbreFiscalDigital";

    public InvoiceData parse(byte[] xml) {
        if (xml == null || xml.length == 0) {
            throw new IllegalArgumentException("El archivo XML está vacío");
        }

        String uuid = null;
        String issuerRfc = null;
        String issuerName = null;
        LocalDateTime issuedAt = null;
        String currency = "MXN";
        BigDecimal total = null;
        BigDecimal invoiceVatAmount = BigDecimal.ZERO;
        List<ConceptData> concepts = new ArrayList<>();
        String cfdiNamespace = null;
        boolean rootSeen = false;

        try (ByteArrayInputStream input = new ByteArrayInputStream(xml)) {
            XMLStreamReader reader = secureFactory().createXMLStreamReader(input);
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
                    throw new XMLStreamException("DTD and entity references are not allowed");
                }
                if (event != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }

                String namespace = reader.getNamespaceURI();
                if (!rootSeen) {
                    if (!"Comprobante".equals(reader.getLocalName())
                            || !(CFDI_33_NAMESPACE.equals(namespace) || CFDI_40_NAMESPACE.equals(namespace))) {
                        throw new XMLStreamException("The document root is not a supported CFDI document");
                    }
                    rootSeen = true;
                    cfdiNamespace = namespace;
                }

                if (cfdiNamespace.equals(namespace)) {
                    switch (reader.getLocalName()) {
                        case "Comprobante" -> {
                            issuedAt = LocalDateTime.parse(requiredAttribute(reader, "Fecha"));
                            currency = attribute(reader, "Moneda", "MXN");
                            total = decimalAttribute(reader, "Total");
                        }
                        case "Emisor" -> {
                            issuerRfc = requiredAttribute(reader, "Rfc");
                            issuerName = attribute(reader, "Nombre", "");
                        }
                        case "Concepto" -> concepts.add(parseConcept(reader));
                        case "Traslado" -> {
                            if ("002".equals(attribute(reader, "Impuesto", ""))) {
                                invoiceVatAmount = invoiceVatAmount.add(
                                        decimalAttribute(reader, "Importe", BigDecimal.ZERO));
                            }
                        }
                        default -> {
                        }
                    }
                } else if (TIMBRE_NAMESPACE.equals(namespace)
                        && "TimbreFiscalDigital".equals(reader.getLocalName())) {
                    uuid = requiredAttribute(reader, "UUID");
                }
            }
            reader.close();
        } catch (XMLStreamException | DateTimeParseException | IOException exception) {
            throw new IllegalArgumentException("No se pudo leer el XML CFDI", exception);
        }

        if (!rootSeen || uuid == null || issuerRfc == null || issuedAt == null || total == null || concepts.isEmpty()) {
            throw new IllegalArgumentException("El XML no contiene los datos requeridos de una factura CFDI");
        }

        BigDecimal conceptVatAmount = concepts.stream()
                .map(ConceptData::vatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (conceptVatAmount.compareTo(BigDecimal.ZERO) == 0
                && invoiceVatAmount.compareTo(BigDecimal.ZERO) > 0) {
            concepts = distributeInvoiceVat(concepts, invoiceVatAmount);
        }

        return new InvoiceData(uuid, issuerRfc, issuerName, issuedAt, currency, total, List.copyOf(concepts));
    }

    private ConceptData parseConcept(XMLStreamReader reader) throws XMLStreamException {
        String productCode = attribute(reader, "ClaveProdServ", "");
        String description = requiredAttribute(reader, "Descripcion");
        BigDecimal quantity = decimalAttribute(reader, "Cantidad");
        String unit = attribute(reader, "ClaveUnidad", attribute(reader, "Unidad", ""));
        BigDecimal unitPrice = decimalAttribute(reader, "ValorUnitario");
        BigDecimal amount = decimalAttribute(reader, "Importe");
        BigDecimal discount = decimalAttribute(reader, "Descuento", BigDecimal.ZERO);
        BigDecimal vatAmount = BigDecimal.ZERO;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
                throw new XMLStreamException("DTD and entity references are not allowed");
            }
            if (event == XMLStreamConstants.START_ELEMENT
                    && (CFDI_33_NAMESPACE.equals(reader.getNamespaceURI())
                            || CFDI_40_NAMESPACE.equals(reader.getNamespaceURI()))
                    && "Traslado".equals(reader.getLocalName())
                    && "002".equals(attribute(reader, "Impuesto", ""))) {
                vatAmount = vatAmount.add(decimalAttribute(reader, "Importe", BigDecimal.ZERO));
            }
            if (event == XMLStreamConstants.END_ELEMENT
                    && "Concepto".equals(reader.getLocalName())) {
                break;
            }
        }

        return new ConceptData(productCode, description, quantity, unit, unitPrice, amount, discount, vatAmount);
    }

    private List<ConceptData> distributeInvoiceVat(List<ConceptData> concepts, BigDecimal invoiceVatAmount) {
        BigDecimal totalBase = concepts.stream()
                .map(concept -> concept.amount().subtract(concept.discount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalBase.compareTo(BigDecimal.ZERO) <= 0) {
            return concepts;
        }

        int remainderIndex = 0;
        BigDecimal largestBase = BigDecimal.ZERO;
        for (int index = 0; index < concepts.size(); index++) {
            BigDecimal base = concepts.get(index).amount().subtract(concepts.get(index).discount());
            if (base.compareTo(largestBase) > 0) {
                largestBase = base;
                remainderIndex = index;
            }
        }

        List<ConceptData> allocated = new ArrayList<>(concepts);
        BigDecimal remainingVat = invoiceVatAmount.setScale(2, java.math.RoundingMode.HALF_UP);
        for (int index = 0; index < concepts.size(); index++) {
            if (index == remainderIndex) {
                continue;
            }
            BigDecimal base = concepts.get(index).amount().subtract(concepts.get(index).discount());
            BigDecimal lineVat = invoiceVatAmount.multiply(base)
                    .divide(totalBase, 2, java.math.RoundingMode.DOWN);
            allocated.set(index, concepts.get(index).withVatAmount(lineVat));
            remainingVat = remainingVat.subtract(lineVat);
        }
        allocated.set(remainderIndex, concepts.get(remainderIndex).withVatAmount(remainingVat));
        return allocated;
    }

    private XMLInputFactory secureFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("External XML entities are not allowed");
        });
        return factory;
    }

    private String requiredAttribute(XMLStreamReader reader, String name) {
        String value = reader.getAttributeValue(null, name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta el atributo requerido " + name);
        }
        return value;
    }

    private String attribute(XMLStreamReader reader, String name, String fallback) {
        String value = reader.getAttributeValue(null, name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private BigDecimal decimalAttribute(XMLStreamReader reader, String name) {
        return new BigDecimal(requiredAttribute(reader, name));
    }

    private BigDecimal decimalAttribute(XMLStreamReader reader, String name, BigDecimal fallback) {
        String value = reader.getAttributeValue(null, name);
        return value == null || value.isBlank() ? fallback : new BigDecimal(value);
    }

    public record InvoiceData(
            String uuid,
            String issuerRfc,
            String issuerName,
            LocalDateTime issuedAt,
            String currency,
            BigDecimal total,
            List<ConceptData> concepts) {
    }

    public record ConceptData(
            String productCode,
            String description,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            BigDecimal amount,
            BigDecimal discount,
            BigDecimal vatAmount) {

        private ConceptData withVatAmount(BigDecimal vat) {
            return new ConceptData(productCode, description, quantity, unit, unitPrice, amount, discount, vat);
        }
    }
}