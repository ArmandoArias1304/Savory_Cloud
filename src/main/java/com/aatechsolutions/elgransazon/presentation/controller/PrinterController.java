package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.ComandaEscPosService;
import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.application.service.PrinterService;
import com.aatechsolutions.elgransazon.application.service.TicketEscPosService;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.Printer;
import com.aatechsolutions.elgransazon.domain.entity.PrinterType;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles admin CRUD for Printer configuration and exposes API endpoints
 * used by the printer-agent page and by view.html manual print buttons.
 */
@Controller
@Slf4j
public class PrinterController {

    static final String STAFF =
            "hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_WAITER', 'ROLE_CHEF', 'ROLE_BARISTA', 'ROLE_PARRILLERO', 'ROLE_CASHIER')";

    private final PrinterService printerService;
    private final ComandaEscPosService comandaEscPosService;
    private final TicketEscPosService ticketEscPosService;
    private final OrderService adminOrderService;

    public PrinterController(
            PrinterService printerService,
            ComandaEscPosService comandaEscPosService,
            TicketEscPosService ticketEscPosService,
            @Qualifier("adminOrderService") OrderService adminOrderService) {
        this.printerService = printerService;
        this.comandaEscPosService = comandaEscPosService;
        this.ticketEscPosService = ticketEscPosService;
        this.adminOrderService = adminOrderService;
    }

    // ═══════════════════════════════════════════
    //  Admin CRUD pages (admin/manager only)
    // ═══════════════════════════════════════════

    /**
     * List / manage comanda printers for the current company.
     * GET /admin/printers
     */
    @GetMapping("/admin/printers")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
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
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
    public String save(
            @RequestParam(required = false) Long id,
            @RequestParam String name,
            @RequestParam PrinterType printerType,
            @RequestParam(required = false, defaultValue = "") String ipAddress,
            RedirectAttributes ra) {

        try {
            Printer printer = new Printer();
            printer.setId(id);
            printer.setName(name.trim());
            printer.setPrinterType(printerType);
            printer.setIpAddress(ipAddress.isBlank() ? null : ipAddress.trim());

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
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
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
    //  Printer-Agent page (staff only)
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
    //  REST API endpoints (staff only)
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
                "id",          p.getId(),
                "name",        p.getName(),
                "printerType", p.getPrinterType().name(),
                "displayName", p.getPrinterType().getDisplayName(),
                "ipAddress",   p.getIpAddress() != null ? p.getIpAddress() : ""
            ))
            .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Download ESC/POS comanda bytes for a given order and printer type.
     * GET /api/print/comanda/{orderId}?type=KITCHEN|BAR|PARRILLERO
     */
    @GetMapping("/api/print/comanda/{orderId}")
    @ResponseBody
    @PreAuthorize(STAFF)
    public ResponseEntity<byte[]> apiDownloadComanda(
            @PathVariable Long orderId,
            @RequestParam String type) {

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

            byte[] bytes = comandaEscPosService.generateComanda(order, printerType);
            if (bytes.length == 0) {
                return ResponseEntity.noContent().build();
            }

            return octetStream(bytes, "comanda_" + type.toLowerCase() + "_" + order.getOrderNumber() + ".bin");
        } catch (Exception e) {
            log.error("Error generating comanda for order {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download ESC/POS cobro ticket bytes for a paid order in the current company.
     * Used by printer-agent.html (any staff role) instead of the admin-only download URL.
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
            return ResponseEntity.internalServerError().build();
        }
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

    private ResponseEntity<byte[]> octetStream(byte[] bytes, String filename) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
