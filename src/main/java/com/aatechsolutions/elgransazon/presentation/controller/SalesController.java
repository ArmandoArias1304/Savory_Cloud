package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.FacturamaService;
import com.aatechsolutions.elgransazon.application.service.InvoiceLinkService;
import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for Sales (Ventas) reports
 * Displays all PAID orders with filters and statistics
 * Accessible by ADMIN, MANAGER, and WAITER roles
 */
@Controller
@RequestMapping("/admin/sales")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_WAITER')")
@Slf4j
public class SalesController {

    /**
     * Invoice-status filter of the sales view: a sale counts as invoiced when every one of its
     * invoiceable units (the ticket, or each account of a split bill) already has a fiscal
     * receipt (individual CFDI, factura global, or an operation invoiced outside the system).
     */
    public enum InvoiceStatusFilter {
        INVOICED("Facturadas"),
        NOT_INVOICED("No facturadas");

        private final String displayName;

        InvoiceStatusFilter(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final OrderService orderService;
    private final EmployeeService employeeService;
    private final DateTimeService dateTimeService;
    private final InvoiceLinkService invoiceLinkService;
    private final FacturamaService facturamaService;

    // Constructor manual para inyectar adminOrderService específicamente
    public SalesController(
            @Qualifier("adminOrderService") OrderService orderService,
            EmployeeService employeeService,
            DateTimeService dateTimeService,
            InvoiceLinkService invoiceLinkService,
            FacturamaService facturamaService) {
        this.orderService = orderService;
        this.employeeService = employeeService;
        this.dateTimeService = dateTimeService;
        this.invoiceLinkService = invoiceLinkService;
        this.facturamaService = facturamaService;
    }

    /**
     * Show sales report with filters
     */
    @GetMapping
    public String listSales(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) PaymentMethodType paymentMethod,
            @RequestParam(required = false) InvoiceStatusFilter invoiceStatus,
            @RequestParam(defaultValue = "1") int page,
            Model model) {
        
        log.debug("Displaying sales report with filters - startDate: {}, endDate: {}, employee: {}, paymentMethod: {}", 
                  startDate, endDate, employeeId, paymentMethod);

        // Get all PAID orders
        List<Order> paidOrders = orderService.findByStatus(OrderStatus.PAID);

        // Apply date filter
        if (startDate != null && !startDate.isEmpty()) {
            paidOrders = filterByDateRange(paidOrders, startDate, endDate);
        }

        // Filter by employee (who collected payment)
        if (employeeId != null) {
            paidOrders = paidOrders.stream()
                .filter(order -> order.getPaidBy() != null && 
                               order.getPaidBy().getIdEmpleado().equals(employeeId))
                .collect(Collectors.toList());
        }

        // Filter by payment method
        if (paymentMethod != null) {
            paidOrders = paidOrders.stream()
                .filter(order -> order.usesPaymentMethod(paymentMethod))
                .collect(Collectors.toList());
        }

        // Filter by invoice status (facturada / no facturada)
        if (invoiceStatus != null) {
            paidOrders = paidOrders.stream()
                .filter(order -> invoiceStatus == InvoiceStatusFilter.INVOICED
                        ? order.isFullyInvoiced()
                        : !order.isFullyInvoiced())
                .collect(Collectors.toList());
        }

        // Sort by payment date (most recent first). Use paidAt (authoritative payment timestamp);
        // fall back to updatedAt/createdAt only for legacy rows missing paidAt.
        paidOrders = paidOrders.stream()
            .sorted((o1, o2) -> {
                LocalDateTime date1 = o1.getPaidAt() != null ? o1.getPaidAt()
                        : (o1.getUpdatedAt() != null ? o1.getUpdatedAt() : o1.getCreatedAt());
                LocalDateTime date2 = o2.getPaidAt() != null ? o2.getPaidAt()
                        : (o2.getUpdatedAt() != null ? o2.getUpdatedAt() : o2.getCreatedAt());
                return date2.compareTo(date1);
            })
            .collect(Collectors.toList());

        // Calculate statistics
        BigDecimal totalSales = calculateTotalSales(paidOrders); // Total con propina
        BigDecimal totalSalesWithoutTip = calculateTotalSalesWithoutTip(paidOrders); // Total sin propina
        BigDecimal totalTips = calculateTotalTips(paidOrders); // Total de propinas
        long totalCount = paidOrders.size();

        // Get all employees for filter - Only employees that handle payments
        List<Employee> employees = employeeService.findAllEnabled().stream()
                .filter(e -> e.hasRole(Role.ADMIN) || 
                           e.hasRole(Role.WAITER) || 
                           e.hasRole(Role.CASHIER) || 
                           e.hasRole(Role.DELIVERY))
                .collect(Collectors.toList());

        // Get all payment methods
        PaymentMethodType[] paymentMethods = PaymentMethodType.values();

        // Server-side pagination
        int pageSize = 15;
        int totalElements = paidOrders.size();
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        if (totalPages == 0) totalPages = 1;
        page = Math.max(1, Math.min(page, totalPages));
        int startIdx = (page - 1) * pageSize;
        int endIdx = Math.min(startIdx + pageSize, totalElements);
        List<Order> pagedSales = totalElements > 0 ? paidOrders.subList(startIdx, endIdx) : paidOrders;

        model.addAttribute("sales", pagedSales);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalElements", totalElements);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("employees", employees);
        model.addAttribute("paymentMethods", paymentMethods);
        model.addAttribute("totalSales", totalSales);
        model.addAttribute("totalSalesWithoutTip", totalSalesWithoutTip);
        model.addAttribute("totalTips", totalTips);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("selectedEmployeeId", employeeId);
        model.addAttribute("selectedPaymentMethod", paymentMethod);
        model.addAttribute("invoiceStatuses", InvoiceStatusFilter.values());
        model.addAttribute("selectedInvoiceStatus", invoiceStatus);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        // Without a ready Facturama config the generated link would lead the client to a
        // "billing not available" page, so the sales view hides the action.
        model.addAttribute("billingEnabled", facturamaService.isFacturacionEnabled());
        // Used by the view to warn when the self-invoice deadline of a sale already passed.
        model.addAttribute("companyZone", dateTimeService.getCompanyZone());

        return "admin/sales/list";
    }

    /**
     * AJAX endpoint: generates the self-invoice link (the QR printed on the ticket) of a
     * PAID sale that does not have one yet, so the ticket can be reprinted with the QR and
     * the client can invoice the operation.
     *
     * <p>One link per invoiceable unit: the order itself for a whole-order ticket, or one per
     * account of a split bill. Units that already have a link, that were already invoiced
     * (individual CFDI or factura global) or that were excluded from the global invoice are
     * skipped, and the response tells how many of each.</p>
     */
    @PostMapping("/{orderId}/generate-invoice-link")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateInvoiceLink(
            @PathVariable Long orderId,
            HttpServletRequest request) {
        try {
            Company company = CompanyContext.getCurrentCompany();
            if (company == null) {
                return ResponseEntity.badRequest()
                        .body(jsonError("No hay un establecimiento activo"));
            }
            if (!facturamaService.isFacturacionEnabled()) {
                return ResponseEntity.badRequest().body(jsonError(
                        "La facturación electrónica no está disponible para este establecimiento"));
            }

            InvoiceLinkService.InvoiceLinkResult result = invoiceLinkService.generateForSale(
                    orderId, company, publicBaseUrl(request));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("generated", result.generated());
            body.put("skipped", result.skipped());
            body.put("links", result.links());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("Error generating the self-invoice link of sale {}: {}", orderId, e.getMessage(), e);
            // The message may be null (e.g. a bare NPE), and Map.of would then throw and turn
            // a readable JSON error into the HTML error page.
            return ResponseEntity.badRequest().body(jsonError(
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /**
     * Public base URL of the establishment, built the same way the payment flow does it when
     * it creates the key, so both links look identical.
     */
    private String publicBaseUrl(HttpServletRequest request) {
        int port = request.getServerPort();
        String host = request.getServerName()
                + (port == 80 || port == 443 ? "" : ":" + port);
        return request.getScheme() + "://" + host;
    }

    /** Null-safe error payload (never use Map.of with a message that can be null). */
    private Map<String, Object> jsonError(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return body;
    }

    /**
     * Filter orders by date range
     */
    private List<Order> filterByDateRange(List<Order> orders, String startDate, String endDate) {
        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.ZoneId companyZone = dateTimeService.getCompanyZone();
            java.time.ZoneId utcZone = java.time.ZoneId.of("UTC");

            if (startDate != null && !startDate.isEmpty()) {
                // Convertir inicio de día en TZ de empresa → UTC para comparar con valores almacenados
                startDateTime = LocalDate.parse(startDate, formatter)
                    .atStartOfDay(companyZone)
                    .withZoneSameInstant(utcZone)
                    .toLocalDateTime();
            }

            if (endDate != null && !endDate.isEmpty()) {
                // Convertir fin de día en TZ de empresa → UTC
                endDateTime = LocalDate.parse(endDate, formatter)
                    .atTime(23, 59, 59)
                    .atZone(companyZone)
                    .withZoneSameInstant(utcZone)
                    .toLocalDateTime();
            } else if (startDate != null && !startDate.isEmpty()) {
                // Si solo hay fecha de inicio, usar la misma como fin del día
                endDateTime = LocalDate.parse(startDate, formatter)
                    .atTime(23, 59, 59)
                    .atZone(companyZone)
                    .withZoneSameInstant(utcZone)
                    .toLocalDateTime();
            }
        } catch (Exception e) {
            log.error("Error parsing date range: {} - {}", startDate, endDate, e);
            return orders;
        }

        if (startDateTime == null && endDateTime == null) {
            return orders;
        }

        final LocalDateTime finalStartDateTime = startDateTime;
        final LocalDateTime finalEndDateTime = endDateTime;

        return orders.stream()
            .filter(order -> {
                // PAID orders are filtered by their authoritative payment timestamp (paidAt).
                // Legacy fallback to updatedAt/createdAt only when paidAt is missing.
                LocalDateTime orderDate = order.getPaidAt() != null
                        ? order.getPaidAt()
                        : (order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt());
                
                if (orderDate == null) return false;
                
                boolean afterStart = finalStartDateTime == null || !orderDate.isBefore(finalStartDateTime);
                boolean beforeEnd = finalEndDateTime == null || !orderDate.isAfter(finalEndDateTime);
                
                return afterStart && beforeEnd;
            })
            .collect(Collectors.toList());
    }

    /**
     * Calculate total sales amount (with tip)
     */
    private BigDecimal calculateTotalSales(List<Order> orders) {
        return orders.stream()
            .map(order -> order.getTotalWithTip() != null ? 
                         order.getTotalWithTip() : order.getTotal())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate total sales amount (without tip)
     */
    private BigDecimal calculateTotalSalesWithoutTip(List<Order> orders) {
        return orders.stream()
            .map(Order::getTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate total tips amount
     */
    private BigDecimal calculateTotalTips(List<Order> orders) {
        return orders.stream()
            .map(order -> order.getTip() != null ? order.getTip() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
