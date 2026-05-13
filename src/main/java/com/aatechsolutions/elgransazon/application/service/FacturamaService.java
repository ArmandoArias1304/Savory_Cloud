package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.FacturamaConfigRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Service for interacting with the Facturama REST API (Multiemisor mode).
 *
 * Handles:
 * - CSD certificate upload per RFC (forwarded to Facturama, never stored locally)
 * - CFDI 4.0 creation for autofactura (client fills RFC data, we create the CFDI)
 * - PDF/XML download from Facturama
 *
 * SECURITY:
 * - CSD files (.cer, .key) and CSD password are NEVER stored — only forwarded to Facturama.
 * - Facturama credentials (user/password) are stored as environment variables, never in DB.
 * - Basic Auth is used (base64 of user:password).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FacturamaService {

    private static final String SANDBOX_URL = "https://apisandbox.facturama.mx/";
    private static final String PRODUCTION_URL = "https://api.facturama.mx/";

    // SAT c_ClaveProdServ codes
    private static final String PROD_CODE_RESTAURANT = "90101500"; // Restaurantes y comida para llevar
    private static final String PROD_CODE_DELIVERY = "78102203";   // Servicios de mensajería de entrega rápida

    private final FacturamaConfigRepository facturamaConfigRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${facturama.user:}")
    private String facturamaUser;

    @Value("${facturama.password:}")
    private String facturamaPassword;

    @Value("${facturama.default-live-mode:false}")
    private boolean defaultLiveMode;

    // ========== CSD Management (Multiemisor) ==========

    /**
     * Upload CSD certificates to Facturama for a specific RFC.
     * Files are forwarded directly and NEVER stored locally.
     *
     * @param config      The Facturama configuration for the company
     * @param cerFile     The .cer certificate file
     * @param keyFile     The .key private key file
     * @param password    The CSD password
     * @param rfc         The RFC associated with the CSD
     */
    public void uploadCsd(FacturamaConfig config, MultipartFile cerFile, MultipartFile keyFile,
                          String password, String rfc) {
        validateCredentials();

        log.info("Uploading CSD certificates to Facturama for RFC: {}", rfc);

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("Certificate", Base64.getEncoder().encodeToString(cerFile.getBytes()));
            body.put("PrivateKey", Base64.getEncoder().encodeToString(keyFile.getBytes()));
            body.put("PrivateKeyPassword", password);
            body.put("Rfc", rfc);

            HttpHeaders headers = authHeaders(defaultLiveMode);
            headers.setContentType(MediaType.APPLICATION_JSON);

            restTemplate.exchange(
                    getBaseUrl(defaultLiveMode) + "api-lite/csds",
                    HttpMethod.POST,
                    new HttpEntity<>(body.toString(), headers),
                    JsonNode.class
            );

            config.setRfc(rfc);
            config.setCsdUploaded(true);
            facturamaConfigRepository.save(config);

            log.info("CSD certificates uploaded successfully for RFC: {}", rfc);
        } catch (Exception e) {
            log.error("Error uploading CSD certificates: {}", e.getMessage());
            throw new RuntimeException("Error al subir los certificados CSD: " + parseFacturamaError(e), e);
        }
    }

    /**
     * Remove CSD certificates from Facturama for a specific RFC.
     */
    public void removeCsd(FacturamaConfig config) {
        validateCredentials();
        if (config.getRfc() == null || config.getRfc().isBlank()) {
            throw new IllegalStateException("No hay RFC configurado para eliminar CSD");
        }

        try {
            HttpHeaders headers = authHeaders(defaultLiveMode);
            restTemplate.exchange(
                    getBaseUrl(defaultLiveMode) + "api-lite/csds/" + config.getRfc(),
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class
            );
            config.setCsdUploaded(false);
            facturamaConfigRepository.save(config);
            log.info("CSD removed for RFC: {}", config.getRfc());
        } catch (Exception e) {
            log.error("Error removing CSD: {}", e.getMessage());
            throw new RuntimeException("Error al eliminar CSD de Facturama: " + parseFacturamaError(e), e);
        }
    }

    // ========== Legal Data ==========

    /**
     * Save legal/fiscal data for the company (stored locally, used when creating CFDIs).
     *
     * @param config         The Facturama configuration
     * @param legalName      Legal name (razón social)
     * @param fiscalRegime   Fiscal regime code (e.g. "601")
     * @param expeditionPlace Zip code from where invoices are issued
     */
    public void updateLegalData(FacturamaConfig config, String legalName,
                                String fiscalRegime, String expeditionPlace) {
        config.setLegalName(legalName);
        config.setFiscalRegime(fiscalRegime);
        config.setExpeditionPlace(expeditionPlace);
        config.setLegalDataConfigured(true);
        facturamaConfigRepository.save(config);
        log.info("Legal data saved for RFC: {}", config.getRfc());
    }

    // ========== CFDI Creation (Autofactura) ==========

    /**
     * Create a CFDI 4.0 (Ingreso) via Facturama API Multiemisor for a paid order.
     * Called when the client fills the autofactura form with their fiscal data.
     *
     * @param order            The paid order
     * @param config           The Facturama configuration for the issuing company
     * @param receiverRfc      Client's RFC
     * @param receiverName     Client's legal name (razón social)
     * @param receiverRegime   Client's fiscal regime code
     * @param receiverCfdiUse  Client's CFDI use code (e.g. "G03")
     * @param receiverZipCode  Client's fiscal zip code
     * @return Map with cfdi_id and cfdi_uuid
     */
    public Map<String, String> createCfdi(Order order, FacturamaConfig config,
                                          String receiverRfc, String receiverName,
                                          String receiverRegime, String receiverCfdiUse,
                                          String receiverZipCode) {
        validateCredentials();

        if (!config.isReady()) {
            throw new IllegalStateException("Facturama no está configurado para esta empresa");
        }

        log.info("Creating CFDI 4.0 for order: {} (receiver RFC: {})", order.getOrderNumber(), receiverRfc);

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("CfdiType", "I"); // Ingreso
            body.put("PaymentForm", mapPaymentForm(order.getPaymentMethod()));
            body.put("PaymentMethod", "PUE"); // Pago en Una sola Exhibición
            body.put("Currency", "MXN");
            body.put("ExpeditionPlace", config.getExpeditionPlace());

            // Folio: use full order number as unique string (e.g. "ORD-2026-04-15-001")
            body.put("Folio", order.getOrderNumber());

            // Issuer (company data from config)
            ObjectNode issuer = objectMapper.createObjectNode();
            issuer.put("Rfc", config.getRfc());
            issuer.put("Name", config.getLegalName());
            issuer.put("FiscalRegime", config.getFiscalRegime());
            body.set("Issuer", issuer);

            // Receiver (client data from autofactura form)
            ObjectNode receiver = objectMapper.createObjectNode();
            receiver.put("Rfc", receiverRfc);
            receiver.put("Name", receiverName);
            receiver.put("CfdiUse", receiverCfdiUse);
            receiver.put("FiscalRegime", receiverRegime);
            receiver.put("TaxZipCode", receiverZipCode);
            body.set("Receiver", receiver);

            // Items — each OrderDetail and each complement becomes its own CFDI line.
            // No merging: combos, items with complements, and simple items all stay
            // as individual lines to preserve the exact detail of the order.
            //
            // Two-pass build:
            //   1) Collect all candidate lines with their tax-included totals (pre-discount).
            //   2) If order.hasOrderDiscount(), distribute the discount pro-rata across the
            //      lines (with last-line residual absorption) and emit Concepto.Descuento per
            //      line — required by SAT CFDI 4.0, which validates
            //      Comprobante.Discount == Σ Concepto.Descuento and disallows negative concepts.
            List<CfdiLine> candidateLines = new ArrayList<>();

            for (OrderDetail detail : order.getOrderDetails()) {
                // Use the authoritative line subtotal stored on the OrderDetail
                // (it already reflects any promotion: PERCENTAGE_DISCOUNT,
                // FIXED_AMOUNT_DISCOUNT, BUY_X_PAY_Y, combo, etc.).
                BigDecimal lineSubtotal = detail.getSubtotal();

                // Add the item only if subtotal > 0 (skip combo sub-items at $0)
                if (lineSubtotal != null && lineSubtotal.compareTo(BigDecimal.ZERO) > 0) {
                    candidateLines.add(new CfdiLine(detail.getDisplayName(),
                            detail.getQuantity(), lineSubtotal, PROD_CODE_RESTAURANT));
                }

                // Always check complements — even zero-price items can have paid complements
                if (detail.getSelectedComplements() != null) {
                    for (OrderDetailComplement comp : detail.getSelectedComplements()) {
                        BigDecimal compPrice = comp.getUnitPrice();
                        if (compPrice == null || compPrice.compareTo(BigDecimal.ZERO) <= 0) {
                            continue;
                        }
                        // OrderDetailComplement.quantity is already the effective qty
                        // (sauces are pre-multiplied by item qty at insert time).
                        int effectiveQty = comp.getQuantity();
                        BigDecimal compLineTotal = comp.getSubtotal();
                        candidateLines.add(new CfdiLine("Complemento - " + comp.getComplementName(),
                                effectiveQty, compLineTotal, PROD_CODE_RESTAURANT));
                    }
                }
            }

            // Delivery cost (only for DELIVERY orders, includes IVA like the other items)
            // SAT ProductCode 78102203 = Servicios de mensajería de entrega rápida (delivery local).
            if (order.getOrderType() == OrderType.DELIVERY
                    && order.getDeliveryCost() != null
                    && order.getDeliveryCost().compareTo(BigDecimal.ZERO) > 0) {
                candidateLines.add(new CfdiLine("Costo de envío a domicilio", 1,
                        order.getDeliveryCost(), PROD_CODE_DELIVERY));
            }

            // Pro-rata distribution of orderDiscount across line totals (con IVA).
            // After distribution, Σ adjustedLineTotal == order.getTotal() exactly
            // (the last line absorbs any rounding residual). When no discount,
            // adjustedLineTotal[i] == lineTotalConIva[i].
            BigDecimal[] adjustedLineTotal = new BigDecimal[candidateLines.size()];
            BigDecimal sumLineTotalConIva = candidateLines.stream()
                    .map(l -> l.lineTotalConIva)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            if (order.hasOrderDiscount() && sumLineTotalConIva.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal orderDiscountConIva = order.getOrderDiscount().setScale(2, RoundingMode.HALF_UP);
                BigDecimal expectedTotalConIva = sumLineTotalConIva.subtract(orderDiscountConIva);
                BigDecimal accumulated = BigDecimal.ZERO;
                for (int i = 0; i < candidateLines.size(); i++) {
                    if (i == candidateLines.size() - 1) {
                        // Last line absorbs the residual so Σ matches expectedTotalConIva exactly
                        adjustedLineTotal[i] = expectedTotalConIva.subtract(accumulated)
                                .setScale(2, RoundingMode.HALF_UP);
                    } else {
                        BigDecimal share = candidateLines.get(i).lineTotalConIva
                                .multiply(expectedTotalConIva)
                                .divide(sumLineTotalConIva, 10, RoundingMode.HALF_UP)
                                .setScale(2, RoundingMode.HALF_UP);
                        adjustedLineTotal[i] = share;
                        accumulated = accumulated.add(share);
                    }
                }
            } else {
                for (int i = 0; i < candidateLines.size(); i++) {
                    adjustedLineTotal[i] = candidateLines.get(i).lineTotalConIva
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }

            // Emit CFDI items and accumulate Comprobante-level discount (sin IVA).
            ArrayNode items = objectMapper.createArrayNode();
            BigDecimal sumConceptDiscount = BigDecimal.ZERO;
            for (int i = 0; i < candidateLines.size(); i++) {
                CfdiLine cl = candidateLines.get(i);
                BigDecimal conceptDiscount = addCfdiItem(items, cl.description, cl.quantity,
                        cl.lineTotalConIva, adjustedLineTotal[i], cl.productCode);
                sumConceptDiscount = sumConceptDiscount.add(conceptDiscount);
            }

            body.set("Items", items);

            // Comprobante.Discount must equal Σ Concepto.Descuento (sin IVA), per CFDI 4.0 spec.
            if (sumConceptDiscount.compareTo(BigDecimal.ZERO) > 0) {
                body.put("Discount", sumConceptDiscount.toPlainString());
            }

            HttpHeaders headers = authHeaders(defaultLiveMode);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // CFDI 4.0 Multiemisor endpoint
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    getBaseUrl(defaultLiveMode) + "api-lite/3/cfdis",
                    HttpMethod.POST,
                    new HttpEntity<>(body.toString(), headers),
                    JsonNode.class
            );

            JsonNode respBody = response.getBody();
            if (respBody == null) {
                throw new RuntimeException("Facturama devolvió una respuesta vacía al crear el CFDI");
            }

            String cfdiId = respBody.path("Id").asText();
            String cfdiUuid = respBody.path("Complement").path("TaxStamp").path("Uuid").asText(null);

            log.info("CFDI created: id={}, uuid={}", cfdiId, cfdiUuid);

            Map<String, String> result = new HashMap<>();
            result.put("cfdi_id", cfdiId);
            result.put("cfdi_uuid", cfdiUuid != null ? cfdiUuid : "");
            return result;

        } catch (Exception e) {
            log.error("Error creating CFDI for order {}: {}", order.getOrderNumber(), e.getMessage());
            throw new RuntimeException("Error al crear el CFDI: " + parseFacturamaError(e), e);
        }
    }

    /**
     * Download a CFDI file (PDF or XML) from Facturama.
     * Facturama returns a JSON object with "Content" field containing Base64-encoded data.
     * Uses the global liveMode setting (FACTURAMA_LIVE_MODE env var).
     *
     * @param cfdiId   The Facturama CFDI ID
     * @param format   "pdf" or "xml"
     * @return byte[] of the decoded file content
     */
    public byte[] downloadCfdi(String cfdiId, String format) {
        validateCredentials();

        HttpHeaders headers = authHeaders(defaultLiveMode);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                getBaseUrl(defaultLiveMode) + "cfdi/" + format + "/issuedLite/" + cfdiId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class
        );

        JsonNode body = response.getBody();
        if (body == null) {
            throw new RuntimeException("Facturama devolvió una respuesta vacía al descargar el CFDI");
        }

        String base64Content = body.path("Content").asText(null);
        if (base64Content == null || base64Content.isBlank()) {
            throw new RuntimeException("El archivo descargado de Facturama no contiene datos");
        }

        return Base64.getDecoder().decode(base64Content);
    }

    // ========== Query Methods ==========

    /**
     * Get the FacturamaConfig for the current company (from CompanyContext).
     */
    public Optional<FacturamaConfig> getConfigForCurrentCompany() {
        Company company = CompanyContext.getCurrentCompany();
        if (company == null) {
            return Optional.empty();
        }
        return facturamaConfigRepository.findByCompany(company);
    }

    /**
     * Get the FacturamaConfig for a specific company.
     */
    public Optional<FacturamaConfig> getConfigForCompany(Company company) {
        return facturamaConfigRepository.findByCompany(company);
    }

    /**
     * Check if Facturama integration is enabled and ready for the current company.
     */
    public boolean isFacturacionEnabled() {
        return getConfigForCurrentCompany()
                .map(FacturamaConfig::isReady)
                .orElse(false);
    }

    /**
     * Enable the integration.
     */
    public void enableIntegration(FacturamaConfig config) {
        config.setEnabled(true);
        facturamaConfigRepository.save(config);
        log.info("Facturama integration enabled for RFC: {}", config.getRfc());
    }

    /**
     * Disable the integration.
     */
    public void disableIntegration(FacturamaConfig config) {
        config.setEnabled(false);
        facturamaConfigRepository.save(config);
        log.info("Facturama integration disabled for RFC: {}", config.getRfc());
    }

    /**
     * Get whether the system is in live (production) mode.
     * This is a global setting controlled by the FACTURAMA_LIVE_MODE env var.
     */
    public boolean isLiveMode() {
        return defaultLiveMode;
    }

    /**
     * Initialize a new FacturamaConfig for a company.
     */
    public FacturamaConfig initConfig(Company company) {
        validateCredentials();

        FacturamaConfig config = facturamaConfigRepository.findByCompany(company)
                .orElse(FacturamaConfig.builder().company(company).build());

        if (config.getId() == null) {
            config.setEnabled(false);
            config.setLiveMode(defaultLiveMode);
            config = facturamaConfigRepository.save(config);
            log.info("Facturama config initialized for company: {}", company.getSlug());
        }
        return config;
    }

    // ========== Private Helpers ==========

    private void validateCredentials() {
        if (facturamaUser == null || facturamaUser.isBlank() ||
            facturamaPassword == null || facturamaPassword.isBlank()) {
            throw new IllegalStateException(
                    "FACTURAMA_USER y FACTURAMA_PASSWORD no están configurados. Contacte al administrador del sistema.");
        }
    }

    private String getBaseUrl(boolean liveMode) {
        return liveMode ? PRODUCTION_URL : SANDBOX_URL;
    }

    private HttpHeaders authHeaders(boolean liveMode) {
        HttpHeaders headers = new HttpHeaders();
        String credentials = facturamaUser + ":" + facturamaPassword;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
        headers.set("Authorization", "Basic " + encodedCredentials);
        return headers;
    }

    /**
     * Add a CFDI line item with IVA 16% (desglosado) supporting per-line discount.
     *
     * The caller passes:
     *   - {@code lineTotalConIva}: ORIGINAL tax-included total of the line (pre-discount).
     *     Used to declare Concepto.Subtotal (Importe) and UnitPrice — these reflect the
     *     full price of the line BEFORE the order-level discount is applied.
     *   - {@code lineTotalConIvaAfterDiscount}: tax-included total AFTER the pro-rata
     *     share of the order-level discount has been subtracted. Drives Base / IVA / Total
     *     so that {@code Σ Concepto.Total == order.getTotal()} exactly (last line absorbs
     *     residual upstream).
     *
     * Per-concept invariants enforced here:
     *   Subtotal − Discount + Tax.Total == Total           (exact)
     *   Base == Subtotal − Discount                         (exact)
     *   Tax.Total == Total − Base                           (exact, NOT base × 0.16)
     *   |UnitPrice × Quantity − Subtotal| ≤ 0.01            (6-decimal UnitPrice keeps tolerance)
     *
     * @return the per-line Discount (sin IVA) emitted, so the caller can sum it for
     *         Comprobante.Discount (which SAT requires == Σ Concepto.Descuento).
     */
    private BigDecimal addCfdiItem(ArrayNode items, String description, int quantity,
                                   BigDecimal lineTotalConIva,
                                   BigDecimal lineTotalConIvaAfterDiscount,
                                   String productCode) {
        ObjectNode item = objectMapper.createObjectNode();

        // Snap both totals to 2 decimals defensively
        BigDecimal totalOriginal = lineTotalConIva.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAfter = lineTotalConIvaAfterDiscount.setScale(2, RoundingMode.HALF_UP);

        // UnitPrice sin IVA: derived from the ORIGINAL (pre-discount) per-unit price,
        // 6 decimals so |UnitPrice × Quantity − Subtotal| ≤ 1¢ for any quantity.
        BigDecimal unitPriceConIva = totalOriginal.divide(BigDecimal.valueOf(quantity), 6, RoundingMode.HALF_UP);
        BigDecimal unitPriceSinIva = unitPriceConIva.divide(BigDecimal.valueOf(1.16), 6, RoundingMode.HALF_UP);

        // Concepto.Subtotal (Importe) — sin IVA, BEFORE discount
        BigDecimal subtotal = totalOriginal.divide(BigDecimal.valueOf(1.16), 2, RoundingMode.HALF_UP);

        // Base imponible (after discount) and IVA — derived from POST-discount total
        BigDecimal base = totalAfter.divide(BigDecimal.valueOf(1.16), 2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = totalAfter.subtract(base);

        // Concepto.Descuento = Subtotal − Base, so Subtotal − Discount + Tax = Total exactly
        BigDecimal discount = subtotal.subtract(base);

        item.put("ProductCode", productCode);
        item.put("Description", description);
        item.put("Unit", "Servicio");
        item.put("UnitCode", "E48"); // Unidad de servicio
        item.put("UnitPrice", unitPriceSinIva.doubleValue());
        item.put("Quantity", quantity);
        item.put("Subtotal", subtotal.doubleValue());
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            item.put("Discount", discount.doubleValue());
        }
        item.put("TaxObject", "02"); // Sí objeto de impuesto
        item.put("Total", totalAfter.doubleValue());

        // IVA 16%
        ArrayNode taxes = objectMapper.createArrayNode();
        ObjectNode tax = objectMapper.createObjectNode();
        tax.put("Name", "IVA");
        tax.put("Rate", 0.16);
        tax.put("Base", base.doubleValue());
        tax.put("Total", taxAmount.doubleValue());
        tax.put("IsRetention", false);
        taxes.add(tax);

        item.set("Taxes", taxes);
        items.add(item);

        return discount;
    }

    /**
     * Internal candidate line used during the two-pass CFDI build (collect → distribute discount → emit).
     */
    private static final class CfdiLine {
        final String description;
        final int quantity;
        final BigDecimal lineTotalConIva;
        final String productCode;

        CfdiLine(String description, int quantity, BigDecimal lineTotalConIva, String productCode) {
            this.description = description;
            this.quantity = quantity;
            this.lineTotalConIva = lineTotalConIva;
            this.productCode = productCode;
        }
    }

    /**
     * Map internal PaymentMethodType to SAT payment form code (c_FormaPago).
     */
    private String mapPaymentForm(PaymentMethodType paymentMethod) {
        if (paymentMethod == null) {
            return "99"; // Por definir
        }
        return switch (paymentMethod) {
            case CASH -> "01";           // Efectivo
            case CREDIT_CARD -> "04";    // Tarjeta de crédito
            case DEBIT_CARD -> "28";     // Tarjeta de débito
            case TRANSFER -> "03";       // Transferencia electrónica
        };
    }

    /**
     * Parse a Facturama API error into a user-friendly Spanish message.
     * Facturama returns JSON like: {"Message":"...","ModelState":{"field":["error msg"]}}
     */
    private String parseFacturamaError(Exception e) {
        if (e instanceof RestClientResponseException restEx) {
            try {
                String body = restEx.getResponseBodyAsString();
                JsonNode json = objectMapper.readTree(body);

                // Extract individual error messages from ModelState
                JsonNode modelState = json.path("ModelState");
                if (!modelState.isMissingNode() && modelState.isObject()) {
                    List<String> messages = new ArrayList<>();
                    modelState.properties().forEach(entry -> {
                        for (JsonNode msg : entry.getValue()) {
                            String text = msg.asText();
                            // Sanitize: remove RFC values leaked by Facturama to avoid information disclosure
                            text = text.replaceAll("(?i)pertenece al RFC\\s*'[A-Z0-9]+'", "no corresponde al RFC ingresado");
                            text = text.replaceAll("(?i)(el rfc|RFC)[:\\s]+[A-Z&Ñ0-9]{10,14}", "$1 configurado");
                            messages.add(text);
                        }
                    });
                    if (!messages.isEmpty()) {
                        return String.join(". ", messages) + ".";
                    }
                }

                // Fallback to top-level Message
                String message = json.path("Message").asText(null);
                if (message != null && !message.isBlank()) {
                    return message;
                }
            } catch (Exception ignored) {
                // Could not parse — fall through
            }
        }
        // Generic fallback — strip class names and HTTP noise
        String msg = e.getMessage();
        if (msg != null && msg.length() > 200) {
            msg = msg.substring(0, 200) + "...";
        }
        return msg != null ? msg : "Error desconocido. Intente de nuevo.";
    }
}
