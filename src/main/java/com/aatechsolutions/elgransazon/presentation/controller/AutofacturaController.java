package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.FacturamaService;
import com.aatechsolutions.elgransazon.domain.entity.FacturamaConfig;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.Payment;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.domain.repository.PaymentRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.infrastructure.util.CompanyLocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public controller for the autofactura (self-invoicing) page.
 * No authentication required — the client accesses this via a URL printed on their ticket.
 *
 * Flow:
 * 1. Client pays at restaurant → ticket prints autofactura URL with unique key.
 *    For split bills, EACH account prints its own QR with its own key (Payment).
 * 2. Client visits URL (e.g. https://pizzamax.domain.com/autofactura/{key})
 * 3. Client enters their fiscal data (RFC, razón social, régimen fiscal, uso CFDI, C.P.)
 * 4. System creates a CFDI 4.0 via Facturama API Multiemisor (one CFDI per account)
 * 5. Client can download PDF and XML of their invoice
 */
@Controller
@RequestMapping("/autofactura")
@RequiredArgsConstructor
@Slf4j
public class AutofacturaController {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final FacturamaService facturamaService;

    // Guard against concurrent CFDI creation for the same autofactura key
    private final Set<String> activeCfdiCreations = ConcurrentHashMap.newKeySet();

    /**
     * Show the autofactura form for a specific ticket/account.
     */
    @GetMapping("/{key}")
    public String showAutofacturaForm(@PathVariable String key, Model model) {
        log.info("Autofactura page accessed with key: {}", key);

        // Validate key format (UUID)
        if (key == null || key.length() < 10) {
            model.addAttribute("error", "Enlace de autofactura inválido.");
            return "autofactura";
        }

        // Resolve the key: per-account Payment (split bills) first, legacy Order fallback.
        Payment payment = paymentRepository.findByAutofacturaKeyAndCompany(key, CompanyContext.getCurrentCompany())
                .orElse(null);
        Order order = null;
        if (payment == null) {
            order = orderRepository.findByAutofacturaKeyAndCompany(key, CompanyContext.getCurrentCompany())
                    .orElse(null);
        } else {
            order = payment.getOrder();
        }

        if (order == null) {
            model.addAttribute("error", "No se encontró la orden asociada a este enlace. " +
                    "Verifique que esté accediendo desde el enlace correcto.");
            return "autofactura";
        }

        // Always expose both status flags as booleans so the template can branch on them safely
        model.addAttribute("alreadyInvoiced", isAlreadyInvoiced(payment, order));
        model.addAttribute("inGlobalInvoice", isInGlobalInvoice(payment, order));

        // Block if the operation was already included in a global invoice (público en general)
        if (isInGlobalInvoice(payment, order)) {
            model.addAttribute("order", order);
            if (payment != null) {
                model.addAttribute("payment", payment);
            }
            model.addAttribute("inGlobalInvoice", true);
            return "autofactura";
        }

        // Check if already invoiced
        if (isAlreadyInvoiced(payment, order)) {
            model.addAttribute("order", order);
            if (payment != null) {
                model.addAttribute("payment", payment);
            }
            model.addAttribute("alreadyInvoiced", true);
            return "autofactura";
        }

        // Check if the invoicing window has expired (strict cutoff at end of payment month
        // in the company's local timezone — aligned with the SAT monthly declaration cycle).
        java.time.LocalDate deadline = (payment != null)
                ? payment.getInvoiceDeadline(CompanyLocalTime.getZone())
                : order.getInvoiceDeadline(CompanyLocalTime.getZone());
        if ((payment != null && payment.isAutofacturaExpired(CompanyLocalTime.getZone()))
                || (payment == null && order.isAutofacturaExpired(CompanyLocalTime.getZone()))) {
            String deadlineText = deadline.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            model.addAttribute("error", "El plazo para facturar esta orden venció el " + deadlineText);
            return "autofactura";
        }

        // Check if Facturama is configured for this company
        FacturamaConfig config = facturamaService.getConfigForCurrentCompany()
                .filter(FacturamaConfig::isReady)
                .orElse(null);

        if (config == null) {
            model.addAttribute("error", "La facturación electrónica no está disponible para este establecimiento en este momento.");
            return "autofactura";
        }

        model.addAttribute("order", order);
        if (payment != null) {
            model.addAttribute("payment", payment);
        }
        model.addAttribute("alreadyInvoiced", false);
        model.addAttribute("taxSystems", getTaxSystems());
        model.addAttribute("cfdiUses", getCfdiUses());

        return "autofactura";
    }

    /**
     * Process the autofactura request — create CFDI via Facturama.
     */
    @PostMapping("/{key}")
    public String processAutofactura(
            @PathVariable String key,
            @RequestParam String rfc,
            @RequestParam String legalName,
            @RequestParam String fiscalRegime,
            @RequestParam String cfdiUse,
            @RequestParam String zipCode,
            Model model) {

        log.info("Processing autofactura for key: {}, RFC: {}", key, rfc);

        // Resolve the key: per-account Payment first, legacy Order fallback.
        Payment payment = paymentRepository.findByAutofacturaKeyAndCompany(key, CompanyContext.getCurrentCompany())
                .orElse(null);
        Order order = null;
        if (payment == null) {
            order = orderRepository.findByAutofacturaKeyAndCompany(key, CompanyContext.getCurrentCompany())
                    .orElse(null);
        } else {
            order = payment.getOrder();
        }

        if (order == null) {
            model.addAttribute("error", "No se encontró la orden asociada a este enlace.");
            model.addAttribute("alreadyInvoiced", false);
            return "autofactura";
        }

        // Always expose both status flags as booleans so the template can branch on them safely
        model.addAttribute("alreadyInvoiced", isAlreadyInvoiced(payment, order));
        model.addAttribute("inGlobalInvoice", isInGlobalInvoice(payment, order));

        // Block if the operation was already included in a global invoice (público en general)
        if (isInGlobalInvoice(payment, order)) {
            model.addAttribute("order", order);
            if (payment != null) {
                model.addAttribute("payment", payment);
            }
            model.addAttribute("inGlobalInvoice", true);
            return "autofactura";
        }

        // Check if already invoiced
        if (isAlreadyInvoiced(payment, order)) {
            model.addAttribute("order", order);
            if (payment != null) {
                model.addAttribute("payment", payment);
            }
            model.addAttribute("alreadyInvoiced", true);
            return "autofactura";
        }

        // Re-check expiry on POST (defends against expiry crossing while user was filling the form)
        if ((payment != null && payment.isAutofacturaExpired(CompanyLocalTime.getZone()))
                || (payment == null && order.isAutofacturaExpired(CompanyLocalTime.getZone()))) {
            java.time.LocalDate deadline = (payment != null)
                    ? payment.getInvoiceDeadline(CompanyLocalTime.getZone())
                    : order.getInvoiceDeadline(CompanyLocalTime.getZone());
            String deadlineText = deadline.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            model.addAttribute("error", "El plazo para facturar esta orden venció el " + deadlineText);
            return "autofactura";
        }

        // Get Facturama config
        FacturamaConfig config = facturamaService.getConfigForCurrentCompany()
                .filter(FacturamaConfig::isReady)
                .orElse(null);

        if (config == null) {
            model.addAttribute("error", "La facturación electrónica no está disponible en este momento.");
            model.addAttribute("alreadyInvoiced", false);
            return "autofactura";
        }

        // Validate inputs
        if (rfc == null || !rfc.trim().toUpperCase().matches("^[A-ZÑ&]{3,4}\\d{6}[A-V1-9][0-9A-Z]\\d$")) {
            model.addAttribute("error", "El RFC ingresado no tiene un formato válido.");
            model.addAttribute("order", order);
            if (payment != null) {
                model.addAttribute("payment", payment);
            }
            model.addAttribute("alreadyInvoiced", false);
            model.addAttribute("taxSystems", getTaxSystems());
            model.addAttribute("cfdiUses", getCfdiUses());
            return "autofactura";
        }

        if (zipCode == null || !zipCode.trim().matches("^\\d{5}$")) {
            model.addAttribute("error", "El código postal fiscal debe ser de 5 dígitos.");
            model.addAttribute("order", order);
            if (payment != null) {
                model.addAttribute("payment", payment);
            }
            model.addAttribute("alreadyInvoiced", false);
            model.addAttribute("taxSystems", getTaxSystems());
            model.addAttribute("cfdiUses", getCfdiUses());
            return "autofactura";
        }

        // Prevent concurrent CFDI creation for the same key (double-click guard)
        if (!activeCfdiCreations.add(key)) {
            log.warn("Duplicate autofactura submission blocked for key: {}", key);
            model.addAttribute("error", "Ya se está procesando su factura, por favor espere.");
            model.addAttribute("order", order);
            if (payment != null) {
                model.addAttribute("payment", payment);
            }
            model.addAttribute("alreadyInvoiced", false);
            model.addAttribute("taxSystems", getTaxSystems());
            model.addAttribute("cfdiUses", getCfdiUses());
            return "autofactura";
        }

        try {
            // Re-check after acquiring lock (another request may have finished)
            Payment freshPayment = paymentRepository.findByAutofacturaKeyAndCompany(key, CompanyContext.getCurrentCompany())
                    .orElse(null);
            Order freshOrder = (freshPayment != null) ? freshPayment.getOrder()
                    : orderRepository.findByAutofacturaKeyAndCompany(key, CompanyContext.getCurrentCompany())
                            .orElse(null);
            if (freshOrder != null && isAlreadyInvoiced(freshPayment, freshOrder)) {
                model.addAttribute("order", freshOrder);
                if (freshPayment != null) {
                    model.addAttribute("payment", freshPayment);
                }
                model.addAttribute("alreadyInvoiced", true);
                return "autofactura";
            }

            // Create CFDI via Facturama (per-account overload when this key belongs to a Payment)
            Map<String, String> cfdiResult;
            if (payment != null) {
                cfdiResult = facturamaService.createCfdi(
                        payment, config,
                        rfc.trim().toUpperCase(),
                        legalName.trim().toUpperCase(),
                        fiscalRegime,
                        cfdiUse,
                        zipCode.trim()
                );
            } else {
                cfdiResult = facturamaService.createCfdi(
                        order, config,
                        rfc.trim().toUpperCase(),
                        legalName.trim().toUpperCase(),
                        fiscalRegime,
                        cfdiUse,
                        zipCode.trim()
                );
            }

            // Save CFDI data. paidAt is intentionally NOT touched here — it must keep the value
            // set when the account/order originally transitioned to PAID.
            if (payment != null) {
                payment.setFacturamaCfdiId(cfdiResult.get("cfdi_id"));
                payment.setFacturamaCfdiUuid(cfdiResult.get("cfdi_uuid"));
                payment.setFacturamaCfdiCreatedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
                payment.setUpdatedBy("AUTOFACTURA");
                paymentRepository.save(payment);
            } else {
                order.setFacturamaCfdiId(cfdiResult.get("cfdi_id"));
                order.setFacturamaCfdiUuid(cfdiResult.get("cfdi_uuid"));
                order.setFacturamaCfdiCreatedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
                order.setUpdatedBy("AUTOFACTURA");
                orderRepository.save(order);
            }

            log.info("Autofactura CFDI created for {}: {} (CFDI ID: {})",
                    payment != null ? "payment " + payment.getPaymentFolio() : "order " + order.getOrderNumber(),
                    cfdiResult.get("cfdi_id"));

            model.addAttribute("order", order);
            if (payment != null) {
                model.addAttribute("payment", payment);
            }
            model.addAttribute("alreadyInvoiced", true);
            model.addAttribute("successMessage", "¡Factura generada exitosamente!");

        } catch (Exception e) {
            log.error("Error creating autofactura CFDI: {}", e.getMessage());
            model.addAttribute("error", "Error al generar la factura: " + e.getMessage());
            model.addAttribute("order", order);
            if (payment != null) {
                model.addAttribute("payment", payment);
            }
            model.addAttribute("alreadyInvoiced", false);
            model.addAttribute("taxSystems", getTaxSystems());
            model.addAttribute("cfdiUses", getCfdiUses());
        } finally {
            activeCfdiCreations.remove(key);
        }

        return "autofactura";
    }

    /**
     * Download CFDI PDF for a specific ticket/account.
     */
    @GetMapping("/{key}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String key) {
        return downloadCfdi(key, "pdf");
    }

    /**
     * Download CFDI XML for a specific ticket/account.
     */
    @GetMapping("/{key}/xml")
    public ResponseEntity<byte[]> downloadXml(@PathVariable String key) {
        return downloadCfdi(key, "xml");
    }

    private ResponseEntity<byte[]> downloadCfdi(String key, String format) {
        Payment payment = paymentRepository.findByAutofacturaKeyAndCompany(key, CompanyContext.getCurrentCompany())
                .orElse(null);
        String cfdiId;
        String folio;
        if (payment != null) {
            cfdiId = payment.getFacturamaCfdiId();
            folio = payment.getPaymentFolio();
        } else {
            Order order = orderRepository.findByAutofacturaKeyAndCompany(key, CompanyContext.getCurrentCompany())
                    .orElse(null);
            if (order == null) {
                return ResponseEntity.notFound().build();
            }
            cfdiId = order.getFacturamaCfdiId();
            folio = order.getOrderNumber();
        }

        if (cfdiId == null || cfdiId.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] fileBytes = facturamaService.downloadCfdi(cfdiId, format);

            String extension = format.equals("pdf") ? ".pdf" : ".xml";
            String contentType = format.equals("pdf") ? "application/pdf" : "application/xml";
            String filename = "Factura_" + folio + extension;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentDispositionFormData("attachment", filename);

            return new ResponseEntity<>(fileBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error downloading CFDI {}: {}", format, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private boolean isAlreadyInvoiced(Payment payment, Order order) {
        if (payment != null) {
            return payment.getFacturamaCfdiId() != null && !payment.getFacturamaCfdiId().isBlank();
        }
        return order.getFacturamaCfdiId() != null && !order.getFacturamaCfdiId().isBlank();
    }

    private boolean isInGlobalInvoice(Payment payment, Order order) {
        if (payment != null) {
            return payment.getFacturaGlobalCfdiId() != null && !payment.getFacturaGlobalCfdiId().isBlank();
        }
        return order.getFacturaGlobalCfdiId() != null && !order.getFacturaGlobalCfdiId().isBlank();
    }

    // ========== Helpers ==========

    private java.util.Map<String, String> getTaxSystems() {
        java.util.LinkedHashMap<String, String> systems = new java.util.LinkedHashMap<>();
        systems.put("601", "601 - General de Ley Personas Morales");
        systems.put("603", "603 - Personas Morales con Fines no Lucrativos");
        systems.put("605", "605 - Sueldos y Salarios e Ingresos Asimilados a Salarios");
        systems.put("606", "606 - Arrendamiento");
        systems.put("607", "607 - Régimen de Enajenación o Adquisición de Bienes");
        systems.put("608", "608 - Demás ingresos");
        systems.put("609", "609 - Consolidación");
        systems.put("610", "610 - Residentes en el Extranjero sin Establecimiento Permanente en México");
        systems.put("611", "611 - Ingresos por Dividendos (socios y accionistas)");
        systems.put("612", "612 - Personas Físicas con Actividades Empresariales y Profesionales");
        systems.put("614", "614 - Ingresos por intereses");
        systems.put("615", "615 - Régimen de los ingresos por obtención de premios");
        systems.put("616", "616 - Sin obligaciones fiscales");
        systems.put("620", "620 - Sociedades Cooperativas de Producción que optan por diferir sus ingresos");
        systems.put("621", "621 - Incorporación Fiscal");
        systems.put("622", "622 - Actividades Agrícolas, Ganaderas, Silvícolas y Pesqueras");
        systems.put("623", "623 - Opcional para Grupos de Sociedades");
        systems.put("624", "624 - Coordinados");
        systems.put("625", "625 - Régimen de las Actividades Empresariales con ingresos a través de Plataformas Tecnológicas");
        systems.put("626", "626 - Régimen Simplificado de Confianza");
        systems.put("628", "628 - Hidrocarburos");
        systems.put("629", "629 - De los Regímenes Fiscales Preferentes y de las Empresas Multinacionales");
        systems.put("630", "630 - Enajenación de acciones en bolsa de valores");
        return systems;
    }

    private java.util.Map<String, String> getCfdiUses() {
        java.util.LinkedHashMap<String, String> uses = new java.util.LinkedHashMap<>();
        uses.put("G01", "G01 - Adquisición de mercancías");
        uses.put("G02", "G02 - Devoluciones, descuentos o bonificaciones");
        uses.put("G03", "G03 - Gastos en general");
        uses.put("I01", "I01 - Construcciones");
        uses.put("I02", "I02 - Mobiliario y equipo de oficina por inversiones");
        uses.put("I03", "I03 - Equipo de transporte");
        uses.put("I04", "I04 - Equipo de cómputo y accesorios");
        uses.put("I05", "I05 - Dados, troqueles, moldes, matrices y herramental");
        uses.put("I06", "I06 - Comunicaciones telefónicas");
        uses.put("I07", "I07 - Comunicaciones satelitales");
        uses.put("I08", "I08 - Otra maquinaria y equipo");
        uses.put("D01", "D01 - Honorarios médicos, dentales y gastos hospitalarios");
        uses.put("D02", "D02 - Gastos médicos por incapacidad o discapacidad");
        uses.put("D03", "D03 - Gastos funerales");
        uses.put("D04", "D04 - Donativos");
        uses.put("D05", "D05 - Intereses reales efectivamente pagados por créditos hipotecarios (casa habitación)");
        uses.put("D06", "D06 - Aportaciones voluntarias al SAR");
        uses.put("D07", "D07 - Primas por seguros de gastos médicos");
        uses.put("D08", "D08 - Gastos de transportación escolar obligatoria");
        uses.put("D09", "D09 - Depósitos en cuentas para el ahorro, primas que tengan como base planes de pensiones");
        uses.put("D10", "D10 - Pagos por servicios educativos (colegiaturas)");
        uses.put("S01", "S01 - Sin efectos fiscales");
        uses.put("CP01", "CP01 - Pagos");
        uses.put("CN01", "CN01 - Nómina");
        return uses;
    }
}