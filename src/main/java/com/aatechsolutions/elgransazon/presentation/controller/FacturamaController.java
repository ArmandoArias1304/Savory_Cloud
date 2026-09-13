package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.FacturamaService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.FacturamaConfig;
import com.aatechsolutions.elgransazon.domain.entity.GlobalInvoice;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.Payment;
import com.aatechsolutions.elgransazon.domain.repository.GlobalInvoiceRepository;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.domain.repository.PaymentRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private final PaymentRepository paymentRepository;
    private final GlobalInvoiceRepository globalInvoiceRepository;

    // Guard against concurrent global invoice creation per company (double-click / double-tab)
    private final Set<String> activeGlobalCreations = ConcurrentHashMap.newKeySet();

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
        model.addAttribute("globalInvoices",
                company != null ? globalInvoiceRepository.findByCompanyOrderByCreatedAtDesc(company) : List.of());

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

            // Order-level invoices (normal single-ticket orders) + per-account
            // invoices (split bills) + global invoices (público en general):
            // every generated CFDI/timbre is counted.
            long count = orderRepository.countCfdisByCompanyAndDateRange(company, startUtc, endUtc)
                    + paymentRepository.countCfdisByCompanyAndDateRange(company, startUtc, endUtc)
                    + globalInvoiceRepository.countByCompanyAndCreatedAtRange(company, startUtc, endUtc);

            // All-time total since the company started using the system:
            // individual order CFDIs + per-account (split bill) CFDIs + every global invoice.
            long total = orderRepository.countByCompanyAndFacturamaCfdiCreatedAtIsNotNull(company)
                    + paymentRepository.countByCompanyAndFacturamaCfdiCreatedAtIsNotNull(company)
                    + globalInvoiceRepository.countByCompany(company);

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("count", count);
            result.put("total", total);
            return ResponseEntity.ok(result);
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

            // Ticket-level aggregation: normal orders (no Payment rows) count as one
            // ticket each; split bills count account-by-account (each person = 1 ticket),
            // because per-person invoices are saved on the Payment, not on the Order.
            java.util.List<Object[]> orderRows = orderRepository.sumPaidOrdersByCompanyAndDateRange(company, startUtc, endUtc);
            Object[] orderRow = (orderRows != null && !orderRows.isEmpty()) ? orderRows.get(0) : null;

            java.util.List<Object[]> payRows = paymentRepository.sumPaidByCompanyAndDateRange(company, startUtc, endUtc);
            Object[] payRow = (payRows != null && !payRows.isEmpty()) ? payRows.get(0) : null;

            long paidCount = 0L;
            java.math.BigDecimal paidTotal = java.math.BigDecimal.ZERO;
            long invoicedCount = 0L;
            java.math.BigDecimal invoicedTotal = java.math.BigDecimal.ZERO;

            if (orderRow != null) {
                paidCount += orderRow[0] != null ? ((Number) orderRow[0]).longValue() : 0L;
                paidTotal = paidTotal.add(orderRow[1] != null
                        ? new java.math.BigDecimal(orderRow[1].toString())
                        : java.math.BigDecimal.ZERO);
                invoicedCount += orderRow[2] != null ? ((Number) orderRow[2]).longValue() : 0L;
                invoicedTotal = invoicedTotal.add(orderRow[3] != null
                        ? new java.math.BigDecimal(orderRow[3].toString())
                        : java.math.BigDecimal.ZERO);
            }
            if (payRow != null) {
                paidCount += payRow[0] != null ? ((Number) payRow[0]).longValue() : 0L;
                paidTotal = paidTotal.add(payRow[1] != null
                        ? new java.math.BigDecimal(payRow[1].toString())
                        : java.math.BigDecimal.ZERO);
                invoicedCount += payRow[2] != null ? ((Number) payRow[2]).longValue() : 0L;
                invoicedTotal = invoicedTotal.add(payRow[3] != null
                        ? new java.math.BigDecimal(payRow[3].toString())
                        : java.math.BigDecimal.ZERO);
            }

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

    // ========== Factura Global (Público en General) ==========

    /**
     * AJAX endpoint: preview of the paid tickets (without individual CFDI and not yet
     * included in a previous global invoice) within a period, ready to be amparados by
     * a global invoice. The period must be a single day or one full month (SAT regla
     * 2.7.1.21 — the global invoice covers daily/weekly/monthly operations).
     */
    @GetMapping("/api/global-invoice-preview")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> globalInvoicePreview(
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

            String periodicity = validateGlobalPeriod(fromDate, toDate);

            LocalDateTime startUtc = fromDate.atStartOfDay(zone).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
            LocalDateTime endUtc = toDate.plusDays(1).atStartOfDay(zone).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();

            List<Map<String, Object>> tickets = buildGlobalTicketList(company, zone, startUtc, endUtc);

            BigDecimal total = tickets.stream()
                    .map(t -> (BigDecimal) t.get("total"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("from", fromDate.toString());
            result.put("to", toDate.toString());
            result.put("periodicity", periodicity);
            result.put("month", fromDate.getMonthValue());
            result.put("year", fromDate.getYear());
            result.put("count", tickets.size());
            result.put("total", total);
            result.put("tickets", tickets);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error building global invoice preview: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * AJAX endpoint: emit the global invoice (público en general) covering every paid
     * ticket in the period that has no individual CFDI and is not yet included in a
     * previous global invoice. Creates ONE CFDI 4.0 via Facturama (one concept per
     * ticket), then marks every included ticket so it can never be individually invoiced.
     */
    @PostMapping("/api/global-invoice")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> emitGlobalInvoice(
            @RequestParam String from,
            @RequestParam String to) {
        Company company = CompanyContext.getCurrentCompany();
        if (company == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sin contexto de empresa"));
        }

        String companyKey = company.getIdCompany() != null ? company.getIdCompany().toString() : "unknown";
        if (!activeGlobalCreations.add(companyKey)) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Ya se está procesando una factura global para este establecimiento, espere un momento."));
        }

        try {
            ZoneId zone = resolveZone(company);

            LocalDate fromDate = LocalDate.parse(from);
            LocalDate toDate = LocalDate.parse(to);

            String periodicity = validateGlobalPeriod(fromDate, toDate);

            LocalDateTime startUtc = fromDate.atStartOfDay(zone).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
            LocalDateTime endUtc = toDate.plusDays(1).atStartOfDay(zone).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();

            FacturamaConfig config = facturamaService.getConfigForCurrentCompany()
                    .filter(FacturamaConfig::isReady)
                    .orElse(null);
            if (config == null) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "La facturación electrónica no está configurada o habilitada para este establecimiento."));
            }

            // Fresh fetch under the lock: only tickets still pending are included
            List<Order> orders = orderRepository
                    .findPaidOrdersPendingGlobalInvoiceByDateRange(company, startUtc, endUtc);
            List<Payment> payments = paymentRepository
                    .findPaidPendingGlobalInvoiceByDateRange(company, startUtc, endUtc);

            if (orders.isEmpty() && payments.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "No hay operaciones pendientes de facturar en el periodo seleccionado."));
            }

            // One concept per ticket: the description carries the ticket folio
            List<FacturamaService.GlobalCfdiTicket> tickets = new ArrayList<>();
            for (Order o : orders) {
                tickets.add(new FacturamaService.GlobalCfdiTicket(
                        "Venta de alimentos y bebidas - " + o.getOrderNumber(),
                        o.getTotal() != null ? o.getTotal() : BigDecimal.ZERO,
                        o.getPaymentMethod()));
            }
            for (Payment p : payments) {
                tickets.add(new FacturamaService.GlobalCfdiTicket(
                        "Venta de alimentos y bebidas - " + p.getPaymentFolio(),
                        p.getTotal() != null ? p.getTotal() : BigDecimal.ZERO,
                        p.getPaymentMethod()));
            }

            // Unique folio for the period (re-running the same period appends a suffix)
            long samePeriod = globalInvoiceRepository
                    .countByCompanyAndPeriodFromAndPeriodTo(company, fromDate, toDate);
            String folio = "GLOBAL-" + (periodicity.equals("01")
                    ? fromDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                    : fromDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")));
            if (samePeriod > 0) {
                folio = folio + "-" + (samePeriod + 1);
            }

            Map<String, String> cfdiResult = facturamaService.createGlobalCfdi(
                    config, tickets, periodicity, fromDate.getMonthValue(), fromDate.getYear(), folio);

            LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
            String username = currentUsername();

            // Mark every included ticket: never individually invoiceable again
            for (Order o : orders) {
                o.setFacturaGlobalCfdiId(cfdiResult.get("cfdi_id"));
                o.setFacturaGlobalCfdiUuid(cfdiResult.get("cfdi_uuid"));
                o.setFacturaGlobalCfdiCreatedAt(nowUtc);
                o.setUpdatedBy("GLOBAL_INVOICE");
            }
            orderRepository.saveAll(orders);

            for (Payment p : payments) {
                p.setFacturaGlobalCfdiId(cfdiResult.get("cfdi_id"));
                p.setFacturaGlobalCfdiUuid(cfdiResult.get("cfdi_uuid"));
                p.setFacturaGlobalCfdiCreatedAt(nowUtc);
                p.setUpdatedBy("GLOBAL_INVOICE");
            }
            paymentRepository.saveAll(payments);

            BigDecimal total = tickets.stream()
                    .map(FacturamaService.GlobalCfdiTicket::totalConIva)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            GlobalInvoice globalInvoice = GlobalInvoice.builder()
                    .company(company)
                    .cfdiId(cfdiResult.get("cfdi_id"))
                    .cfdiUuid(cfdiResult.get("cfdi_uuid"))
                    .folio(folio)
                    .periodType(periodicity.equals("01") ? "DAILY" : "MONTHLY")
                    .periodFrom(fromDate)
                    .periodTo(toDate)
                    .total(total)
                    .ticketCount(tickets.size())
                    .paymentForm(facturamaService.dominantPaymentForm(tickets))
                    .createdBy(username)
                    .build();
            globalInvoiceRepository.save(globalInvoice);

            log.info("Global invoice emitted: folio={}, tickets={}, total={}, cfdiId={}",
                    folio, tickets.size(), total, cfdiResult.get("cfdi_id"));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("id", globalInvoice.getId());
            result.put("folio", folio);
            result.put("cfdiUuid", cfdiResult.get("cfdi_uuid"));
            result.put("total", total);
            result.put("count", tickets.size());
            result.put("paymentForm", globalInvoice.getPaymentForm());
            result.put("periodType", globalInvoice.getPeriodType());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error emitting global invoice: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } finally {
            activeGlobalCreations.remove(companyKey);
        }
    }

    /**
     * Download the PDF of a previously emitted global invoice.
     */
    @GetMapping("/global-invoice/{id}/pdf")
    public ResponseEntity<byte[]> downloadGlobalPdf(@PathVariable Long id) {
        return downloadGlobalCfdi(id, "pdf");
    }

    /**
     * Download the XML of a previously emitted global invoice.
     */
    @GetMapping("/global-invoice/{id}/xml")
    public ResponseEntity<byte[]> downloadGlobalXml(@PathVariable Long id) {
        return downloadGlobalCfdi(id, "xml");
    }

    private ResponseEntity<byte[]> downloadGlobalCfdi(Long id, String format) {
        Company company = CompanyContext.getCurrentCompany();
        if (company == null) {
            return ResponseEntity.notFound().build();
        }
        GlobalInvoice globalInvoice = globalInvoiceRepository.findByIdAndCompany(id, company)
                .orElse(null);
        if (globalInvoice == null || globalInvoice.getCfdiId() == null || globalInvoice.getCfdiId().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] fileBytes = facturamaService.downloadCfdi(globalInvoice.getCfdiId(), format);

            String extension = format.equals("pdf") ? ".pdf" : ".xml";
            String contentType = format.equals("pdf") ? "application/pdf" : "application/xml";
            String filename = "FacturaGlobal_" + globalInvoice.getFolio() + extension;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentDispositionFormData("attachment", filename);

            return new ResponseEntity<>(fileBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error downloading global CFDI {}: {}", format, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Validate that the range is a single day (periodicity "01" = diario) or one full
     * month (periodicity "04" = mensual); otherwise the Facturama InformacionGlobal
     * node cannot be built. Returns the SAT periodicity code.
     */
    private String validateGlobalPeriod(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("Ambas fechas son requeridas");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("La fecha 'desde' debe ser menor o igual a 'hasta'");
        }
        if (fromDate.equals(toDate)) {
            return "01"; // Diario
        }
        if (fromDate.getDayOfMonth() == 1
                && toDate.equals(fromDate.withDayOfMonth(fromDate.lengthOfMonth()))) {
            return "04"; // Mensual
        }
        throw new IllegalArgumentException("El periodo debe ser un solo día o un mes completo");
    }

    /**
     * Build the preview ticket list (folio, local date, total, payment method label)
     * for the paid tickets pending global invoicing in the UTC range.
     */
    private List<Map<String, Object>> buildGlobalTicketList(Company company, ZoneId zone,
                                                            LocalDateTime startUtc, LocalDateTime endUtc) {
        List<Map<String, Object>> tickets = new ArrayList<>();
        for (Order o : orderRepository.findPaidOrdersPendingGlobalInvoiceByDateRange(company, startUtc, endUtc)) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("folio", o.getOrderNumber());
            t.put("date", o.getPaidAt() != null
                    ? o.getPaidAt().atZone(ZoneId.of("UTC")).withZoneSameInstant(zone).toLocalDate().toString()
                    : "");
            t.put("total", o.getTotal() != null ? o.getTotal() : BigDecimal.ZERO);
            t.put("paymentMethod", o.getPaymentMethod() != null ? o.getPaymentMethod().getDisplayName() : "");
            tickets.add(t);
        }
        for (Payment p : paymentRepository.findPaidPendingGlobalInvoiceByDateRange(company, startUtc, endUtc)) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("folio", p.getPaymentFolio());
            t.put("date", p.getPaidAt() != null
                    ? p.getPaidAt().atZone(ZoneId.of("UTC")).withZoneSameInstant(zone).toLocalDate().toString()
                    : "");
            t.put("total", p.getTotal() != null ? p.getTotal() : BigDecimal.ZERO);
            t.put("paymentMethod", p.getPaymentMethod() != null ? p.getPaymentMethod().getDisplayName() : "");
            tickets.add(t);
        }
        return tickets;
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getName() != null ? authentication.getName() : "ADMIN";
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
