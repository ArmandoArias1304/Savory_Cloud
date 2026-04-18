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
            ArrayNode items = objectMapper.createArrayNode();

            for (OrderDetail detail : order.getOrderDetails()) {
                // Use promotional price when a promotion was applied, otherwise original price
                BigDecimal effectiveUnitPrice = detail.getPromotionAppliedPrice() != null
                        ? detail.getPromotionAppliedPrice()
                        : detail.getUnitPrice();

                // Add the item only if price > 0 (skip combo sub-items at $0)
                if (effectiveUnitPrice != null && effectiveUnitPrice.compareTo(BigDecimal.ZERO) > 0) {
                    addCfdiItem(items, detail.getItemMenu().getName(), detail.getQuantity(), effectiveUnitPrice);
                }

                // Always check complements — even zero-price items can have paid complements
                if (detail.getSelectedComplements() != null) {
                    for (OrderDetailComplement comp : detail.getSelectedComplements()) {
                        BigDecimal compPrice = comp.getUnitPrice();
                        if (compPrice == null || compPrice.compareTo(BigDecimal.ZERO) <= 0) {
                            continue;
                        }
                        // For sauces, quantity is per-serving; multiply by item qty for effective total
                        int effectiveQty = comp.getQuantity();
                        if (Boolean.TRUE.equals(comp.getComplement().getIsSauce())) {
                            effectiveQty = effectiveQty * detail.getQuantity();
                        }
                        addCfdiItem(items, "Complemento - " + comp.getComplement().getName(),
                                effectiveQty, compPrice);
                    }
                }
            }

            body.set("Items", items);

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
     * Add a CFDI line item with IVA 16% (desglosado from tax-included price).
     * Restaurant prices include IVA, so we decompose:
     *   UnitPrice (sin IVA) = price / 1.16
     *   Tax = expectedTotal - subtotal  (NOT subtotal * 0.16)
     *
     * We derive the tax as the difference between the tax-included total and the
     * subtotal sin IVA.  This guarantees each line's total equals the exact
     * tax-included price × quantity, so the CFDI grand total matches the order
     * total without accumulated rounding errors across lines.
     */
    private void addCfdiItem(ArrayNode items, String description, int quantity, BigDecimal taxIncludedPrice) {
        ObjectNode item = objectMapper.createObjectNode();

        BigDecimal unitPriceSinIva = taxIncludedPrice.divide(BigDecimal.valueOf(1.16), 6, RoundingMode.HALF_UP);
        BigDecimal subtotal = unitPriceSinIva.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        // Derive tax from the exact tax-included amount so line total = price × qty
        BigDecimal expectedTotal = taxIncludedPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = expectedTotal.subtract(subtotal);
        BigDecimal total = expectedTotal;

        item.put("ProductCode", "90101500"); // Restaurantes y comida para llevar
        item.put("Description", description);
        item.put("Unit", "Servicio");
        item.put("UnitCode", "E48"); // Unidad de servicio
        item.put("UnitPrice", unitPriceSinIva.setScale(6, RoundingMode.HALF_UP).doubleValue());
        item.put("Quantity", quantity);
        item.put("Subtotal", subtotal.doubleValue());
        item.put("TaxObject", "02"); // Sí objeto de impuesto
        item.put("Total", total.doubleValue());

        // IVA 16%
        ArrayNode taxes = objectMapper.createArrayNode();
        ObjectNode tax = objectMapper.createObjectNode();
        tax.put("Name", "IVA");
        tax.put("Rate", 0.16);
        tax.put("Base", subtotal.doubleValue());
        tax.put("Total", taxAmount.doubleValue());
        tax.put("IsRetention", false);
        taxes.add(tax);

        item.set("Taxes", taxes);
        items.add(item);
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
