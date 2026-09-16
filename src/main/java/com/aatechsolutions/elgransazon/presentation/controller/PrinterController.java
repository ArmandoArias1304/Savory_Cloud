package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.ComandaEscPosService;
import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.application.service.PrintClaimService;
import com.aatechsolutions.elgransazon.application.service.PrinterService;
import com.aatechsolutions.elgransazon.application.service.TicketEscPosService;
import com.aatechsolutions.elgransazon.application.service.WebSocketNotificationService;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.Payment;
import com.aatechsolutions.elgransazon.domain.entity.Printer;
import com.aatechsolutions.elgransazon.domain.entity.PrinterType;
import com.aatechsolutions.elgransazon.domain.repository.PaymentRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Handles admin CRUD for Printer configuration and exposes API endpoints
 * used by the printer-agent page and by view.html manual print buttons.
 */
@Controller
@Slf4j
public class PrinterController {

    static final String STAFF = "hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_WAITER', 'ROLE_CHEF', 'ROLE_BARISTA', 'ROLE_PARRILLERO', 'ROLE_CASHIER')";

    /**
     * Roles allowed to manage (view/create/edit/delete) the comanda printers.
     * CASHIER is intentionally excluded: the cashier only opens the printing agent
     * (/printer-agent), which is covered by STAFF.
     */
    static final String PRINTERS_MANAGER = "hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')";

    /**
     * Response header carrying the order-detail ids that were actually printed, so
     * the
     * client can confirm them with the ack endpoint after a successful print.
     */
    private static final String HEADER_COMANDA_DETAIL_IDS = "X-Comanda-Detail-Ids";

    private final PrinterService printerService;
    private final ComandaEscPosService comandaEscPosService;
    private final TicketEscPosService ticketEscPosService;
    private final OrderService adminOrderService;
    private final PrintClaimService printClaimService;
    private final PaymentRepository paymentRepository;
    private final WebSocketNotificationService wsNotificationService;

    public PrinterController(
            PrinterService printerService,
            ComandaEscPosService comandaEscPosService,
            TicketEscPosService ticketEscPosService,
            @Qualifier("adminOrderService") OrderService adminOrderService,
            PrintClaimService printClaimService,
            PaymentRepository paymentRepository,
            WebSocketNotificationService wsNotificationService) {
        this.printerService = printerService;
        this.comandaEscPosService = comandaEscPosService;
        this.ticketEscPosService = ticketEscPosService;
        this.adminOrderService = adminOrderService;
        this.printClaimService = printClaimService;
        this.paymentRepository = paymentRepository;
        this.wsNotificationService = wsNotificationService;
    }

    // ═══════════════════════════════════════════
    // Admin CRUD pages (admin/manager only)
    // ═══════════════════════════════════════════

    /**
     * List / manage comanda printers for the current company.
     * GET /admin/printers
     */
    @GetMapping("/admin/printers")
    @PreAuthorize(PRINTERS_MANAGER)
    public String list(Model model) {
        List<Printer> printers = printerService.findAll();

        Map<PrinterType, Optional<Printer>> byType = new java.util.EnumMap<>(PrinterType.class);
        for (PrinterType type : PrinterType.values()) {
            byType.put(type, printers.stream().filter(p -> p.getPrinterType() == type).findFirst());
        }

        model.addAttribute("printersByType", byType);
        model.addAttribute("printerTypes", PrinterType.values());
        model.addAttribute("newPrinter", new Printer());
        model.addAttribute("companyId", CompanyContext.requireCurrentCompany().getIdCompany());
        return "admin/printers/list";
    }

    /**
     * Save (create or update) a comanda printer.
     * POST /admin/printers/save
     */
    @PostMapping("/admin/printers/save")
    @PreAuthorize(PRINTERS_MANAGER)
    public String save(
            @RequestParam(required = false) Long id,
            @RequestParam String name,
            @RequestParam PrinterType printerType,
            @RequestParam(required = false, defaultValue = "") String ipAddress,
            RedirectAttributes ra) {

        try {
            // The printer form sends an empty (or absent) ipAddress for most printers, so
            // null and blank must be treated the same; never call methods on the raw value.
            String normalizedIp = (ipAddress == null || ipAddress.isBlank()) ? null : ipAddress.trim();

            Printer printer = new Printer();
            printer.setId(id);
            printer.setName(name == null ? "" : name.trim());
            printer.setPrinterType(printerType);
            printer.setIpAddress(normalizedIp);

            printerService.save(printer);
            ra.addFlashAttribute("successMessage", "Impresora guardada correctamente");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error saving printer", e);
            ra.addFlashAttribute("errorMessage", "Error al guardar la impresora");
        }
        return "redirect:/admin/printers";
    }

    /**
     * Delete a comanda printer.
     * POST /admin/printers/{id}/delete
     */
    @PostMapping("/admin/printers/{id}/delete")
    @PreAuthorize(PRINTERS_MANAGER)
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            printerService.deleteById(id);
            ra.addFlashAttribute("successMessage", "Impresora eliminada correctamente");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting printer {}", id, e);
            ra.addFlashAttribute("errorMessage", "Error al eliminar la impresora");
        }
        return "redirect:/admin/printers";
    }

    // ═══════════════════════════════════════════
    // Printer-Agent page (staff only)
    // ═══════════════════════════════════════════

    /**
     * The printer-agent HTML page.
     * This page is opened on any PC that has a thermal printer and should
     * auto-print comandas via QZ Tray when orders arrive over WebSocket.
     *
     * GET /printer-agent
     */
    @GetMapping("/printer-agent")
    @PreAuthorize(STAFF)
    public String printerAgentPage(Model model, java.security.Principal principal) {
        Long companyId = CompanyContext.requireCurrentCompany().getIdCompany();
        model.addAttribute("companyId", companyId);
        model.addAttribute("currentUsername", principal != null ? principal.getName() : "");
        return "printer-agent";
    }

    // ═══════════════════════════════════════════
    // REST API endpoints (staff only)
    // ═══════════════════════════════════════════

    /**
     * Returns the list of configured comanda printers for the current company.
     * GET /api/printers
     */
    @GetMapping("/api/printers")
    @ResponseBody
    @PreAuthorize(STAFF)
    public ResponseEntity<List<Map<String, Object>>> apiListPrinters() {
        List<Printer> printers = printerService.findAll();
        List<Map<String, Object>> result = printers.stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.getId(),
                        "name", p.getName(),
                        "printerType", p.getPrinterType().name(),
                        "displayName", p.getPrinterType().getDisplayName(),
                        "ipAddress", p.getIpAddress() != null ? p.getIpAddress() : ""))
                .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Download ESC/POS comanda bytes for a given order and printer type.
     * GET /api/print/comanda/{orderId}?type=KITCHEN|BAR|PARRILLERO&mode=delta|full
     *
     * mode=delta (default): every item of that station that was NOT printed yet,
     * which is what
     * the printer agent uses automatically. A ticket never repeats what already
     * went out
     * (paper has no live status) and a missed event is picked up by the next
     * comanda.
     * Returns 204 when the station has nothing pending.
     * mode=full: complete comanda of the station, for manual reprints.
     */
    @GetMapping("/api/print/comanda/{orderId}")
    @ResponseBody
    @PreAuthorize(STAFF)
    public ResponseEntity<byte[]> apiDownloadComanda(
            @PathVariable Long orderId,
            @RequestParam String type,
            @RequestParam(required = false, defaultValue = "delta") String mode) {

        PrinterType printerType;
        try {
            printerType = PrinterType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Order order = adminOrderService.findByIdWithDetails(orderId).orElse(null);
            if (order == null) {
                return ResponseEntity.notFound().build();
            }

            boolean full = "full".equalsIgnoreCase(mode);
            List<OrderDetail> items = full
                    ? comandaEscPosService.stationItems(order, printerType)
                    : comandaEscPosService.pendingItems(order, printerType);

            byte[] bytes = comandaEscPosService.generateComanda(order, printerType, items, !full);
            if (bytes.length == 0) {
                log.debug("No pending comanda items for {} on order {} (mode={})", printerType, order.getOrderNumber(),
                        mode);
                return ResponseEntity.noContent().build();
            }

            String printedIds = items.stream()
                    .map(OrderDetail::getIdOrderDetail)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment",
                    "comanda_" + (full ? "completa_" : "nuevos_") + type.toLowerCase()
                            + "_" + order.getOrderNumber() + ".bin");
            headers.setCacheControl("no-cache, no-store, must-revalidate");
            headers.add(HEADER_COMANDA_DETAIL_IDS, printedIds);

            return ResponseEntity.ok().headers(headers).body(bytes);
        } catch (Exception e) {
            log.error("Error generating comanda for order {}", orderId, e);
            return internalServerError(e);
        }
    }

    /**
     * Confirms that a comanda was printed, so those items are never printed again
     * on this station.
     * POST /api/print/comanda/{orderId}/ack?type=KITCHEN&details=1,2,3 (or
     * &all=true)
     *
     * Called by the printer agent (and by the manual "solo pendientes" button)
     * AFTER a successful
     * print. If the print fails nothing is confirmed and the items go out again on
     * the next comanda.
     */
    @PostMapping("/api/print/comanda/{orderId}/ack")
    @ResponseBody
    @PreAuthorize(STAFF)
    public ResponseEntity<Map<String, Object>> apiAckComanda(
            @PathVariable Long orderId,
            @RequestParam String type,
            @RequestParam(required = false) List<Long> details,
            @RequestParam(required = false, defaultValue = "false") boolean all) {

        PrinterType printerType;
        try {
            printerType = PrinterType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        try {
            int marked = comandaEscPosService.markComandaPrinted(orderId, printerType, details, all);
            return ResponseEntity.ok(Map.of("marked", marked));
        } catch (Exception e) {
            log.error("Error marking comanda as printed for order {} ({})", orderId, printerType, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Hands a manual comanda reprint to the printer agents after local QZ printing
     * fails.
     * The agent performs the actual print and acknowledges items only after
     * success.
     */
    @PostMapping("/api/print/comanda/{orderId}/agent")
    @ResponseBody
    @PreAuthorize(STAFF)
    public ResponseEntity<Map<String, Object>> apiHandComandaToAgents(
            @PathVariable Long orderId,
            @RequestParam String type,
            @RequestParam(required = false, defaultValue = "delta") String mode) {
        PrinterType printerType;
        try {
            printerType = PrinterType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        Order order = adminOrderService.findByIdWithDetails(orderId).orElse(null);
        if (order == null)
            return ResponseEntity.notFound().build();

        boolean full = "full".equalsIgnoreCase(mode);
        List<OrderDetail> items = full
                ? comandaEscPosService.stationItems(order, printerType)
                : comandaEscPosService.pendingItems(order, printerType);
        if (items.isEmpty())
            return ResponseEntity.noContent().build();

        wsNotificationService.notifyPrintComanda(order, items, full ? "FULL" : "DELTA");
        return ResponseEntity.accepted().body(Map.of("sent", true, "mode", full ? "FULL" : "DELTA"));
    }

    /**
     * Download ESC/POS cobro ticket bytes for a paid order in the current company.
     * Used by printer-agent.html (any staff role) instead of the admin-only
     * download URL.
     * GET /api/print/ticket/{orderId}
     */
    @GetMapping("/api/print/ticket/{orderId}")
    @ResponseBody
    @PreAuthorize(STAFF)
    public ResponseEntity<byte[]> apiDownloadTicket(@PathVariable Long orderId) {
        try {
            Order order = adminOrderService.findByIdWithDetails(orderId).orElse(null);
            if (order == null) {
                return ResponseEntity.notFound().build();
            }

            byte[] bytes = ticketEscPosService.generateTicket(order);
            return octetStream(bytes, "ticket_" + order.getOrderNumber() + ".bin");
        } catch (Exception e) {
            log.error("Error generating ticket for order {}", orderId, e);
            return internalServerError(e);
        }
    }

    /**
     * Download the ESC/POS ticket of ONE account (Payment) of a split bill or a
     * departing-guest collection. Used by the printer agent and by the paying PC.
     * GET /api/print/ticket/{orderId}/payment/{paymentId}
     */
    @GetMapping("/api/print/ticket/{orderId}/payment/{paymentId}")
    @ResponseBody
    @PreAuthorize(STAFF)
    public ResponseEntity<byte[]> apiDownloadAccountTicket(
            @PathVariable Long orderId,
            @PathVariable Long paymentId) {
        try {
            Payment payment = paymentRepository.findByIdWithDetails(paymentId).orElse(null);
            if (payment == null || payment.getOrder() == null
                    || !orderId.equals(payment.getOrder().getIdOrder())) {
                return ResponseEntity.notFound().build();
            }
            // Multi-tenant guard: never print another company's account.
            Long accountCompanyId = payment.getCompany() != null
                    ? payment.getCompany().getIdCompany()
                    : (payment.getOrder().getCompany() != null
                            ? payment.getOrder().getCompany().getIdCompany()
                            : null);
            Long currentCompanyId = CompanyContext.getCurrentCompanyId();
            if (currentCompanyId != null && !currentCompanyId.equals(accountCompanyId)) {
                log.warn("Account ticket {} of order {} belongs to company {} (current {}) — denied",
                        paymentId, orderId, accountCompanyId, currentCompanyId);
                return ResponseEntity.notFound().build();
            }

            byte[] bytes = ticketEscPosService.generateTicket(payment);
            return octetStream(bytes, "ticket_" + payment.getPaymentFolio() + ".bin");
        } catch (Exception e) {
            log.error("Error generating account ticket {} of order {}", paymentId, orderId, e);
            return internalServerError(e);
        }
    }

    /**
     * Exactly-once claim for ONE account (Payment): the split-bill ticket must not
     * be
     * printed twice when several PCs share the same printer.
     * POST /api/print/ticket/payment/{paymentId}/claim
     */
    @PostMapping("/api/print/ticket/payment/{paymentId}/claim")
    @ResponseBody
    @PreAuthorize(STAFF)
    public ResponseEntity<Map<String, Object>> apiClaimAccountTicket(@PathVariable Long paymentId) {
        return ResponseEntity.ok(Map.of("claim", printClaimService.claim("account:" + paymentId)));
    }

    /**
     * Exactly-once claim for a company-wide ticket print.
     * POST /api/print/ticket/{orderId}/claim
     *
     * The ticket event is broadcast to every printer agent of the company; each
     * agent
     * that has the ticket printer connected asks for the claim first, so only one
     * PC
     * prints it even when the same printer is installed on several computers.
     */
    @PostMapping("/api/print/ticket/{orderId}/claim")
    @ResponseBody
    @PreAuthorize(STAFF)
    public ResponseEntity<Map<String, Object>> apiClaimTicket(@PathVariable Long orderId) {
        boolean claimed = printClaimService.claim("ticket:" + orderId);
        return ResponseEntity.ok(Map.of("claim", claimed));
    }

    /**
     * Hands the whole-order ticket back when the PC that claimed it could not print
     * it
     * (no printer installed, QZ Tray error, paper out...). Releasing the claim lets
     * the
     * printer agents take the ticket on the next event instead of losing it for the
     * claim TTL.
     * POST /api/print/ticket/{orderId}/release
     */
    @PostMapping("/api/print/ticket/{orderId}/release")
    @ResponseBody
    @PreAuthorize(STAFF)
    public ResponseEntity<Map<String, Object>> apiReleaseTicket(@PathVariable Long orderId) {
        printClaimService.release("ticket:" + orderId);
        handTicketToAgents(orderId);
        return ResponseEntity.ok(Map.of("released", true));
    }

    /**
     * Re-broadcasts a whole-order ticket the charging PC could not print. Never
     * fails the
     * request: the release already happened and a manual reprint stays available.
     */
    private void handTicketToAgents(Long orderId) {
        try {
            adminOrderService.findByIdWithDetails(orderId).ifPresent(
                    order -> wsNotificationService.notifyPrintTicketToAgents(order, List.of(), "local print failed"));
        } catch (Exception e) {
            log.warn("Could not hand ticket {} to the agents: {}", orderId, e.getMessage());
        }
    }

    /**
     * Same as {@link #handTicketToAgents(Long)} for ONE account of a split bill.
     */
    private void handAccountToAgents(Long paymentId) {
        try {
            Payment payment = paymentRepository.findByIdWithDetails(paymentId).orElse(null);
            if (payment == null || payment.getOrder() == null)
                return;
            wsNotificationService.notifyPrintTicketToAgents(
                    payment.getOrder(), List.of(paymentId), "local print failed");
        } catch (Exception e) {
            log.warn("Could not hand account {} to the agents: {}", paymentId, e.getMessage());
        }
    }

    /**
     * Hands ONE account (Payment) back when the PC that claimed it could not print
     * it.
     * POST /api/print/ticket/payment/{paymentId}/release
     */
    @PostMapping("/api/print/ticket/payment/{paymentId}/release")
    @ResponseBody
    @PreAuthorize(STAFF)
    public ResponseEntity<Map<String, Object>> apiReleaseAccountTicket(@PathVariable Long paymentId) {
        printClaimService.release("account:" + paymentId);
        handAccountToAgents(paymentId);
        return ResponseEntity.ok(Map.of("released", true));
    }

    /**
     * Short ESC/POS test page for the given station or the ticket printer.
     * GET /api/print/test?kind=KITCHEN|BAR|PARRILLERO|TICKET
     */
    @GetMapping("/api/print/test")
    @ResponseBody
    @PreAuthorize(STAFF)
    public ResponseEntity<byte[]> apiTestPrint(@RequestParam String kind) {
        try {
            byte[] bytes;
            if ("TICKET".equalsIgnoreCase(kind)) {
                bytes = ticketEscPosService.generateTestPage();
            } else {
                PrinterType printerType;
                try {
                    printerType = PrinterType.valueOf(kind.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().build();
                }
                bytes = comandaEscPosService.generateTestPage(printerType);
            }
            return octetStream(bytes, "prueba_" + kind.toLowerCase() + ".bin");
        } catch (Exception e) {
            log.error("Error generating test print for kind {}", kind, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 500 with a readable reason in the body: the printer agent shows it in its
     * log, so a
     * failure can be diagnosed from the PC that prints instead of from the server
     * console.
     */
    private ResponseEntity<byte[]> internalServerError(Exception e) {
        String detail = e.getClass().getSimpleName()
                + (e.getMessage() != null ? ": " + e.getMessage() : "");
        return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.TEXT_PLAIN)
                .body(detail.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<byte[]> octetStream(byte[] bytes, String filename) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
