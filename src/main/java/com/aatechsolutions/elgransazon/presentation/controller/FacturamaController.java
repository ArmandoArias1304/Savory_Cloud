package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.FacturamaService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.FacturamaConfig;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * Controller for managing Facturama electronic invoicing (facturación electrónica).
 * Only accessible by ADMIN role.
 *
 * Flow (Facturama API Multiemisor):
 * 1. Programmer initializes the billing configuration from the company panel
 * 2. Admin uploads CSD certificates (.cer, .key) — forwarded to Facturama, never stored
 * 3. Admin enters legal/fiscal data (razón social, régimen fiscal, C.P.)
 * 4. Admin enables the integration
 *
 * After enabled, each paid order gets an autofactura key/URL printed on the ticket.
 * The client visits the URL and fills their fiscal data to generate their CFDI.
 */
@Controller
@RequestMapping("/admin/facturacion")
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class FacturamaController {

    private final FacturamaService facturamaService;
    private final OrderRepository orderRepository;

    /**
     * Display the billing configuration page.
     */
    @GetMapping
    public String showBillingConfig(Model model) {
        log.debug("Displaying billing configuration page");

        FacturamaConfig config = facturamaService.getConfigForCurrentCompany().orElse(null);

        model.addAttribute("facturamaConfig", config);
        model.addAttribute("taxSystems", getTaxSystems());

        var company = CompanyContext.getCurrentCompany();
        model.addAttribute("companyName", company != null ? company.getName() : "");

        return "admin/facturacion/form";
    }

    /**
     * Step 1: Upload CSD certificates (.cer, .key + password + RFC).
     */
    @PostMapping("/upload-csd")
    public String uploadCsd(
            @RequestParam("cerFile") MultipartFile cerFile,
            @RequestParam("keyFile") MultipartFile keyFile,
            @RequestParam("csdPassword") String csdPassword,
            @RequestParam("rfc") String rfc,
            RedirectAttributes redirectAttributes) {

        log.info("Uploading CSD certificates for RFC: {}", rfc);

        try {
            if (cerFile.isEmpty() || keyFile.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Debe seleccionar ambos archivos (.cer y .key)");
                return "redirect:/admin/facturacion";
            }

            String cerName = cerFile.getOriginalFilename();
            String keyName = keyFile.getOriginalFilename();
            if (cerName == null || !cerName.toLowerCase().endsWith(".cer")) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "El archivo del certificado debe tener extensión .cer");
                return "redirect:/admin/facturacion";
            }
            if (keyName == null || !keyName.toLowerCase().endsWith(".key")) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "El archivo de la llave privada debe tener extensión .key");
                return "redirect:/admin/facturacion";
            }

            if (csdPassword == null || csdPassword.isBlank()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "La contraseña del CSD es requerida");
                return "redirect:/admin/facturacion";
            }

            if (rfc == null || !rfc.matches("^[A-ZÑ&]{3,4}\\d{6}[A-V1-9][0-9A-Z]\\d$")) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "El RFC no tiene un formato válido");
                return "redirect:/admin/facturacion";
            }

            FacturamaConfig config = facturamaService.getConfigForCurrentCompany()
                    .orElseThrow(() -> new IllegalStateException("Primero debe inicializar la configuración"));

            facturamaService.uploadCsd(config, cerFile, keyFile, csdPassword, rfc.toUpperCase().trim());

            redirectAttributes.addFlashAttribute("successMessage",
                    "Certificados CSD subidos exitosamente a Facturama. Los archivos NO fueron almacenados en nuestro sistema.");

        } catch (Exception e) {
            log.error("Error uploading CSD: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al subir los certificados: " + e.getMessage());
        }

        return "redirect:/admin/facturacion";
    }

    /**
     * Step 3: Save legal/fiscal data.
     */
    @PostMapping("/update-legal")
    public String updateLegalData(
            @RequestParam String legalName,
            @RequestParam String taxSystem,
            @RequestParam String zipCode,
            RedirectAttributes redirectAttributes) {

        log.info("Updating legal data");

        try {
            if (legalName == null || legalName.isBlank()) {
                redirectAttributes.addFlashAttribute("errorMessage", "La razón social es requerida");
                return "redirect:/admin/facturacion";
            }
            if (zipCode == null || !zipCode.matches("^\\d{5}$")) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "El código postal fiscal debe ser de 5 dígitos");
                return "redirect:/admin/facturacion";
            }

            FacturamaConfig config = facturamaService.getConfigForCurrentCompany()
                    .orElseThrow(() -> new IllegalStateException("Primero debe inicializar la configuración"));

            facturamaService.updateLegalData(config, legalName.trim(), taxSystem, zipCode.trim());

            redirectAttributes.addFlashAttribute("successMessage",
                    "Datos fiscales guardados exitosamente.");

        } catch (Exception e) {
            log.error("Error updating legal data: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al guardar los datos fiscales: " + e.getMessage());
        }

        return "redirect:/admin/facturacion";
    }

    // Toggle activation is managed by PROGRAMMER role via CompanyController

    /**
     * AJAX endpoint: count CFDIs for the current company within a date range.
     * Dates are received as local dates in the company's timezone and converted to UTC.
     */
    @GetMapping("/api/cfdi-count")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> countCfdis(
            @RequestParam String from,
            @RequestParam String to) {
        try {
            Company company = CompanyContext.getCurrentCompany();
            if (company == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Sin contexto de empresa"));
            }

            ZoneId zone = resolveZone(company);

            LocalDate fromDate = LocalDate.parse(from);
            LocalDate toDate = LocalDate.parse(to);

            if (fromDate.isAfter(toDate)) {
                return ResponseEntity.badRequest().body(Map.of("error", "La fecha 'desde' debe ser menor o igual a 'hasta'"));
            }

            LocalDateTime startUtc = fromDate.atStartOfDay(zone).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
            LocalDateTime endUtc = toDate.plusDays(1).atStartOfDay(zone).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();

            long count = orderRepository.countCfdisByCompanyAndDateRange(company, startUtc, endUtc);

            return ResponseEntity.ok(Map.of("count", count));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * AJAX endpoint: report of PAID orders within a date range, broken down by whether they were
     * invoiced (have a CFDI) or not. Dates are interpreted in the company's timezone.
     *
     * Filters by {@code Order.paidAt} (authoritative payment timestamp; never overwritten after
     * the order transitions to PAID, so it is safe even after autofactura CFDI saves).
     */
    @GetMapping("/api/paid-orders-report")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> paidOrdersReport(
            @RequestParam String from,
            @RequestParam String to) {
        try {
            Company company = CompanyContext.getCurrentCompany();
            if (company == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Sin contexto de empresa"));
            }

            ZoneId zone = resolveZone(company);

            LocalDate fromDate = LocalDate.parse(from);
            LocalDate toDate = LocalDate.parse(to);

            if (fromDate.isAfter(toDate)) {
                return ResponseEntity.badRequest().body(Map.of("error", "La fecha 'desde' debe ser menor o igual a 'hasta'"));
            }

            LocalDateTime startUtc = fromDate.atStartOfDay(zone).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
            LocalDateTime endUtc = toDate.plusDays(1).atStartOfDay(zone).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();

            java.util.List<Object[]> rows = orderRepository.sumPaidOrdersByCompanyAndDateRange(company, startUtc, endUtc);
            Object[] row = (rows != null && !rows.isEmpty()) ? rows.get(0) : null;

            long paidCount = row != null && row[0] != null ? ((Number) row[0]).longValue() : 0L;
            java.math.BigDecimal paidTotal = row != null && row[1] != null
                    ? new java.math.BigDecimal(row[1].toString())
                    : java.math.BigDecimal.ZERO;
            long invoicedCount = row != null && row[2] != null ? ((Number) row[2]).longValue() : 0L;
            java.math.BigDecimal invoicedTotal = row != null && row[3] != null
                    ? new java.math.BigDecimal(row[3].toString())
                    : java.math.BigDecimal.ZERO;

            long notInvoicedCount = paidCount - invoicedCount;
            java.math.BigDecimal notInvoicedTotal = paidTotal.subtract(invoicedTotal);

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("paidCount", paidCount);
            result.put("paidTotal", paidTotal);
            result.put("invoicedCount", invoicedCount);
            result.put("invoicedTotal", invoicedTotal);
            result.put("notInvoicedCount", notInvoicedCount);
            result.put("notInvoicedTotal", notInvoicedTotal);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error generating paid orders report: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private ZoneId resolveZone(Company company) {
        if (company.getTimezone() != null && !company.getTimezone().isBlank()) {
            try {
                return ZoneId.of(company.getTimezone());
            } catch (Exception ignored) {
            }
        }
        return ZoneId.of("America/Mexico_City");
    }

    // ========== Private Helpers ==========

    /**
     * SAT tax systems (régimen fiscal) — most common for restaurants.
     */
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
}
