package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.FacturamaService;
import com.aatechsolutions.elgransazon.domain.entity.FacturamaConfig;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Public controller for the autofactura (self-invoicing) page.
 * No authentication required — the client accesses this via a URL printed on their ticket.
 *
 * Flow:
 * 1. Client pays at restaurant → ticket prints autofactura URL with unique key
 * 2. Client visits URL (e.g. https://pizzamax.domain.com/autofactura/{key})
 * 3. Client enters their fiscal data (RFC, razón social, régimen fiscal, uso CFDI, C.P.)
 * 4. System creates a CFDI 4.0 via Facturama API Multiemisor
 * 5. Client can download PDF and XML of their invoice
 */
@Controller
@RequestMapping("/autofactura")
@RequiredArgsConstructor
@Slf4j
public class AutofacturaController {

    private final OrderRepository orderRepository;
    private final FacturamaService facturamaService;

    /**
     * Show the autofactura form for a specific order.
     */
    @GetMapping("/{key}")
    public String showAutofacturaForm(@PathVariable String key, Model model) {
        log.info("Autofactura page accessed with key: {}", key);

        // Validate key format (UUID)
        if (key == null || key.length() < 10) {
            model.addAttribute("error", "Enlace de autofactura inválido.");
            return "autofactura";
        }

        // Find order by autofactura key within the current company context
        Order order = orderRepository.findByAutofacturaKeyAndCompany(key, CompanyContext.getCurrentCompany())
                .orElse(null);

        if (order == null) {
            model.addAttribute("error", "No se encontró la orden asociada a este enlace. " +
                    "Verifique que esté accediendo desde el enlace correcto.");
            return "autofactura";
        }

        // Check if already invoiced
        if (order.getFacturamaCfdiId() != null && !order.getFacturamaCfdiId().isBlank()) {
            model.addAttribute("order", order);
            model.addAttribute("alreadyInvoiced", true);
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

        // Find order
        Order order = orderRepository.findByAutofacturaKeyAndCompany(key, CompanyContext.getCurrentCompany())
                .orElse(null);

        if (order == null) {
            model.addAttribute("error", "No se encontró la orden asociada a este enlace.");
            model.addAttribute("alreadyInvoiced", false);
            return "autofactura";
        }

        // Check if already invoiced
        if (order.getFacturamaCfdiId() != null && !order.getFacturamaCfdiId().isBlank()) {
            model.addAttribute("order", order);
            model.addAttribute("alreadyInvoiced", true);
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
            model.addAttribute("alreadyInvoiced", false);
            model.addAttribute("taxSystems", getTaxSystems());
            model.addAttribute("cfdiUses", getCfdiUses());
            return "autofactura";
        }

        if (zipCode == null || !zipCode.trim().matches("^\\d{5}$")) {
            model.addAttribute("error", "El código postal fiscal debe ser de 5 dígitos.");
            model.addAttribute("order", order);
            model.addAttribute("alreadyInvoiced", false);
            model.addAttribute("taxSystems", getTaxSystems());
            model.addAttribute("cfdiUses", getCfdiUses());
            return "autofactura";
        }

        try {
            // Create CFDI via Facturama
            Map<String, String> cfdiResult = facturamaService.createCfdi(
                    order, config,
                    rfc.trim().toUpperCase(),
                    legalName.trim().toUpperCase(),
                    fiscalRegime,
                    cfdiUse,
                    zipCode.trim()
            );

            // Save CFDI data on order
            order.setFacturamaCfdiId(cfdiResult.get("cfdi_id"));
            order.setFacturamaCfdiUuid(cfdiResult.get("cfdi_uuid"));
            order.setFacturamaCfdiCreatedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
            orderRepository.save(order);

            log.info("Autofactura CFDI created for order: {} (CFDI ID: {})",
                    order.getOrderNumber(), cfdiResult.get("cfdi_id"));

            model.addAttribute("order", order);
            model.addAttribute("alreadyInvoiced", true);
            model.addAttribute("successMessage", "¡Factura generada exitosamente!");

        } catch (Exception e) {
            log.error("Error creating autofactura CFDI: {}", e.getMessage());
            model.addAttribute("error", "Error al generar la factura: " + e.getMessage());
            model.addAttribute("order", order);
            model.addAttribute("alreadyInvoiced", false);
            model.addAttribute("taxSystems", getTaxSystems());
            model.addAttribute("cfdiUses", getCfdiUses());
        }

        return "autofactura";
    }

    /**
     * Download CFDI PDF for a specific order.
     */
    @GetMapping("/{key}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String key) {
        return downloadCfdi(key, "pdf");
    }

    /**
     * Download CFDI XML for a specific order.
     */
    @GetMapping("/{key}/xml")
    public ResponseEntity<byte[]> downloadXml(@PathVariable String key) {
        return downloadCfdi(key, "xml");
    }

    private ResponseEntity<byte[]> downloadCfdi(String key, String format) {
        Order order = orderRepository.findByAutofacturaKeyAndCompany(key, CompanyContext.getCurrentCompany())
                .orElse(null);

        if (order == null || order.getFacturamaCfdiId() == null || order.getFacturamaCfdiId().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] fileBytes = facturamaService.downloadCfdi(order.getFacturamaCfdiId(), format);

            String extension = format.equals("pdf") ? ".pdf" : ".xml";
            String contentType = format.equals("pdf") ? "application/pdf" : "application/xml";
            String filename = "Factura_" + order.getOrderNumber() + extension;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentDispositionFormData("attachment", filename);

            return new ResponseEntity<>(fileBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error downloading CFDI {}: {}", format, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
