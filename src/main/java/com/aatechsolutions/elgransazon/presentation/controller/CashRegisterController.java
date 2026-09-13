package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.CashRegisterService;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.ReportPdfService;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.presentation.dto.CashRegisterHistoryRow;
import com.aatechsolutions.elgransazon.presentation.dto.CashRegisterSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Cash-register (caja) pages for the CASHIER role.
 *
 * One drawer per cashier: open with an initial amount, register manual
 * movements (pagos / entradas / retiros), close with the counted cash, browse
 * past closings by day and download a PDF of the day.
 */
@Controller
@RequestMapping("/cashier/cash-register")
@PreAuthorize("hasRole('ROLE_CASHIER')")
@RequiredArgsConstructor
@Slf4j
public class CashRegisterController {

    private final CashRegisterService cashRegisterService;
    private final EmployeeService employeeService;
    private final DateTimeService dateTimeService;
    private final ReportPdfService reportPdfService;

    // ---------- Day view ----------

    @GetMapping
    public String view(Authentication authentication, Model model) {
        Employee cashier = currentCashier(authentication);
        Company company = CompanyContext.requireCurrentCompany();

        CashRegisterSession open = cashRegisterService.findOpenSession(company, cashier);
        boolean sessionOpen = open != null;
        CashRegisterSession session = open;
        if (session == null) {
            // No open drawer: show today's latest closing (if any) read-only.
            List<CashRegisterSession> today = cashRegisterService.listSessionsForDate(
                    company, cashier, dateTimeService.todayLocal());
            if (!today.isEmpty()) {
                session = today.get(0);
            }
        }

        // NB: the model attribute is NOT named "session" — that is a reserved Thymeleaf
        // expression object (the HTTP session), so "${session.id}" would resolve to null.
        model.addAttribute("cashSession", session);
        model.addAttribute("sessionOpen", sessionOpen);
        model.addAttribute("allowOpen", !sessionOpen);
        model.addAttribute("summary", cashRegisterService.buildSummary(session));
        model.addAttribute("movements", cashRegisterService.getMovements(session));
        model.addAttribute("movementTypes", CashRegisterMovementType.values());
        model.addAttribute("username", authentication.getName());
        return "cashier/cash-register/view";
    }

    // ---------- Open / close ----------

    @PostMapping("/open")
    public String open(@RequestParam(required = false) BigDecimal initialAmount,
                       @RequestParam(required = false) String notes,
                       Authentication authentication,
                       RedirectAttributes redirectAttributes) {
        try {
            Employee cashier = currentCashier(authentication);
            Company company = CompanyContext.requireCurrentCompany();
            CashRegisterSession session = cashRegisterService.openSession(
                    company, cashier, initialAmount, notes, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Caja abierta con un fondo de " + money(session.getInitialAmount()) + ".");
        } catch (Exception e) {
            log.error("Error opening cash register: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cashier/cash-register";
    }

    @PostMapping("/close")
    public String close(@RequestParam BigDecimal countedAmount,
                        @RequestParam(required = false) String closingNotes,
                        Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        try {
            Employee cashier = currentCashier(authentication);
            Company company = CompanyContext.requireCurrentCompany();
            CashRegisterSession session = cashRegisterService.findOpenSession(company, cashier);
            CashRegisterSession closed = cashRegisterService.closeSession(
                    session, countedAmount, closingNotes, cashier, authentication.getName());
            CashRegisterSummary summary = cashRegisterService.buildSummary(closed);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Caja cerrada. Esperado " + money(summary.getExpectedCash())
                            + " · contado " + money(summary.getCountedAmount())
                            + " · diferencia " + money(summary.getDifference()) + ".");
        } catch (Exception e) {
            log.error("Error closing cash register: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cashier/cash-register";
    }

    // ---------- Movements ----------

    @PostMapping("/movements")
    public String addMovement(@RequestParam CashRegisterMovementType type,
                              @RequestParam String concept,
                              @RequestParam BigDecimal amount,
                              @RequestParam(required = false) String notes,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            Employee cashier = currentCashier(authentication);
            Company company = CompanyContext.requireCurrentCompany();
            CashRegisterSession session = cashRegisterService.findOpenSession(company, cashier);
            CashRegisterMovement movement = cashRegisterService.addMovement(
                    company, session, type, concept, amount, notes, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage",
                    movement.getType().getDisplayName() + " registrado: " + movement.getConcept()
                            + " " + money(movement.getAmount()) + ".");
        } catch (Exception e) {
            log.error("Error registering cash register movement: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cashier/cash-register";
    }

    @PostMapping("/movements/{id}/delete")
    public String deleteMovement(@PathVariable Long id,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            Employee cashier = currentCashier(authentication);
            Company company = CompanyContext.requireCurrentCompany();
            cashRegisterService.deleteMovement(id, company, cashier);
            redirectAttributes.addFlashAttribute("successMessage", "Movimiento eliminado.");
        } catch (Exception e) {
            log.error("Error deleting cash register movement: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cashier/cash-register";
    }

    // ---------- History ----------

    @GetMapping("/history")
    public String history(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication,
            Model model) {
        Employee cashier = currentCashier(authentication);
        Company company = CompanyContext.requireCurrentCompany();

        LocalDate filterDate = date != null ? date : dateTimeService.todayLocal();
        List<CashRegisterHistoryRow> rows = cashRegisterService.listSessionsForDate(company, cashier, filterDate)
                .stream()
                .map(session -> new CashRegisterHistoryRow(session, cashRegisterService.buildSummary(session)))
                .toList();

        model.addAttribute("rows", rows);
        model.addAttribute("filterDate", filterDate);
        model.addAttribute("today", dateTimeService.todayLocal());
        model.addAttribute("username", authentication.getName());
        return "cashier/cash-register/history";
    }

    @GetMapping("/session/{id}")
    public String sessionDetail(@PathVariable Long id, Authentication authentication,
                                Model model, RedirectAttributes redirectAttributes) {
        Employee cashier = currentCashier(authentication);
        Company company = CompanyContext.requireCurrentCompany();

        CashRegisterSession session = cashRegisterService.getSession(id, company);
        if (!owns(session, cashier)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Esta caja no te pertenece.");
            return "redirect:/cashier/cash-register";
        }

        model.addAttribute("cashSession", session);
        model.addAttribute("sessionOpen", session.isOpen());
        model.addAttribute("allowOpen", false);
        model.addAttribute("summary", cashRegisterService.buildSummary(session));
        model.addAttribute("movements", cashRegisterService.getMovements(session));
        model.addAttribute("movementTypes", CashRegisterMovementType.values());
        model.addAttribute("username", authentication.getName());
        return "cashier/cash-register/view";
    }

    // ---------- PDF ----------

    @GetMapping("/session/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id, Authentication authentication,
                                              RedirectAttributes redirectAttributes) {
        try {
            Employee cashier = currentCashier(authentication);
            Company company = CompanyContext.requireCurrentCompany();

            CashRegisterSession session = cashRegisterService.getSession(id, company);
            if (!owns(session, cashier)) {
                return ResponseEntity.notFound().build();
            }

            CashRegisterSummary summary = cashRegisterService.buildSummary(session);
            List<CashRegisterMovement> movements = cashRegisterService.getMovements(session);

            String openedAt = dateTimeService.formatToCompanyTime(session.getOpenedAt(), "dd/MM/yyyy HH:mm");
            String closedAt = session.getClosedAt() != null
                    ? dateTimeService.formatToCompanyTime(session.getClosedAt(), "dd/MM/yyyy HH:mm")
                    : "Aún abierta";

            byte[] pdf = reportPdfService.generateCashRegisterReport(
                    session, summary, movements, cashier.getFullName(), openedAt, closedAt);

            String dateLabel = session.getOpenedAt() != null
                    ? session.getOpenedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    : "dia";
            String filename = "Corte_Caja_" + dateLabel + ".pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return ResponseEntity.ok().headers(headers).body(pdf);
        } catch (Exception e) {
            log.error("Error generating cash register PDF: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "No se pudo generar el PDF de la caja.");
            return ResponseEntity.internalServerError().build();
        }
    }

    // ---------- Helpers ----------

    private Employee currentCashier(Authentication authentication) {
        return employeeService.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Cajero no encontrado"));
    }

    private boolean owns(CashRegisterSession session, Employee cashier) {
        return session != null && session.getCashier() != null && cashier != null
                && session.getCashier().getIdEmpleado().equals(cashier.getIdEmpleado());
    }

    private String money(BigDecimal value) {
        return String.format("$%,.2f", value != null ? value : BigDecimal.ZERO);
    }
}
