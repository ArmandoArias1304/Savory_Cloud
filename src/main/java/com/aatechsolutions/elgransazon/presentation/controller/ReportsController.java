package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.IngredientCategoryService;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.application.service.ReportPdfService;
import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.domain.repository.CustomerRepository;
import com.aatechsolutions.elgransazon.domain.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
 * Controller for Reports (Reportes)
 * Generates reports based on PAID orders
 * Accessible by ADMIN, MANAGER, and WAITER roles
 */
@Controller
@RequestMapping("/admin/reports")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_WAITER')")
@Slf4j
public class ReportsController {

    private final OrderService orderService;
    private final ReportPdfService reportPdfService;
    private final IngredientService ingredientService;
    private final IngredientCategoryService ingredientCategoryService;
    private final CategoryService categoryService;
    private final CustomerRepository customerRepository;
    private final DateTimeService dateTimeService;

    // Constructor manual para inyectar adminOrderService específicamente
    public ReportsController(
            @Qualifier("adminOrderService") OrderService orderService,
            ReportPdfService reportPdfService,
            IngredientService ingredientService,
            IngredientCategoryService ingredientCategoryService,
            CategoryService categoryService,
            CustomerRepository customerRepository,
            DateTimeService dateTimeService) {
        this.orderService = orderService;
        this.reportPdfService = reportPdfService;
        this.ingredientService = ingredientService;
        this.ingredientCategoryService = ingredientCategoryService;
        this.categoryService = categoryService;
        this.customerRepository = customerRepository;
        this.dateTimeService = dateTimeService;
    }

    /**
     * Show reports view with data
     */
    @GetMapping
    public String showReports(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Model model) {
        
        log.debug("Displaying reports - startDate: {}, endDate: {}", startDate, endDate);

        // Get all PAID orders
        List<Order> paidOrders = orderService.findByStatus(OrderStatus.PAID);

        // Apply date filter
        if (startDate != null && !startDate.isEmpty()) {
            paidOrders = filterByDateRange(paidOrders, startDate, endDate);
        }

        // Sort by payment date (most recent first)
        paidOrders = paidOrders.stream()
            .sorted((o1, o2) -> {
                LocalDateTime date1 = o1.getPaidAt() != null ? o1.getPaidAt() : (o1.getUpdatedAt() != null ? o1.getUpdatedAt() : o1.getCreatedAt());
                LocalDateTime date2 = o2.getPaidAt() != null ? o2.getPaidAt() : (o2.getUpdatedAt() != null ? o2.getUpdatedAt() : o2.getCreatedAt());
                return date2.compareTo(date1);
            })
            .collect(Collectors.toList());

        // Calculate statistics
        BigDecimal totalSales = calculateTotalSales(paidOrders);
        long totalOrders = paidOrders.size();
        
        // Calculate sales by category
        Map<String, BigDecimal> salesByCategory = calculateSalesByCategory(paidOrders);
        
        // Calculate sales by employee
        List<Map<String, Object>> salesByEmployee = calculateSalesByEmployee(paidOrders);
        
        // Calculate sales by payment method
        Map<String, Long> ordersByPaymentMethod = calculateOrdersByPaymentMethod(paidOrders);
        
        // Top 10 best selling items
        List<Map<String, Object>> topSellingItems = calculateTopSellingItems(paidOrders, 10);

        // Top 10 best selling complements
        List<Map<String, Object>> topSellingComplements = calculateTopSellingComplements(paidOrders, 10);
        BigDecimal totalComplementsSales = calculateTotalComplementsSales(paidOrders);

        // Delivery cost totals (only DELIVERY orders contribute)
        List<Order> deliveryOrders = paidOrders.stream()
            .filter(o -> o.getOrderType() == OrderType.DELIVERY
                && o.getDeliveryCost() != null
                && o.getDeliveryCost().compareTo(BigDecimal.ZERO) > 0)
            .collect(Collectors.toList());
        BigDecimal totalDeliveryCost = deliveryOrders.stream()
            .map(Order::getDeliveryCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalDeliveryOrders = deliveryOrders.size();

        // Total item-only sales (sum of category sales, excludes complements)
        BigDecimal totalItemSales = salesByCategory.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("totalSales", totalSales);
        model.addAttribute("totalItemSales", totalItemSales);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("salesByCategory", salesByCategory);
        model.addAttribute("salesByEmployee", salesByEmployee);
        model.addAttribute("ordersByPaymentMethod", ordersByPaymentMethod);
        model.addAttribute("topSellingItems", topSellingItems);
        model.addAttribute("topSellingComplements", topSellingComplements);
        model.addAttribute("totalComplementsSales", totalComplementsSales);
        model.addAttribute("totalDeliveryCost", totalDeliveryCost);
        model.addAttribute("totalDeliveryOrders", totalDeliveryOrders);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "admin/reports/list";
    }

    /**
     * Filter orders by date range
     */
    private List<Order> filterByDateRange(List<Order> orders, String startDate, String endDate) {
        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            if (startDate != null && !startDate.isEmpty()) {
                startDateTime = dateTimeService.startOfDayUtc(LocalDate.parse(startDate, formatter));
            }

            if (endDate != null && !endDate.isEmpty()) {
                endDateTime = dateTimeService.endOfDayUtc(LocalDate.parse(endDate, formatter));
            } else if (startDateTime != null) {
                endDateTime = dateTimeService.endOfDayUtc(LocalDate.parse(startDate, formatter));
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
                // Filter PAID orders by their authoritative payment timestamp.
                // Fall back to updatedAt/createdAt only for legacy orders missing paidAt.
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
     * Calculate total sales amount (WITHOUT tip)
     * NOTE: Order.total already includes IVA
     */
    private BigDecimal calculateTotalSales(List<Order> orders) {
        return orders.stream()
            .map(order -> order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate sales by category (WITHOUT tip)
     * NOTE: OrderDetail.subtotal already includes IVA, no need to add it again
     */
    private Map<String, BigDecimal> calculateSalesByCategory(List<Order> orders) {
        Map<String, BigDecimal> salesByCategory = new HashMap<>();
        
        for (Order order : orders) {
            for (OrderDetail detail : order.getOrderDetails()) {
                // Skip combo children (subtotal=$0, already accounted in combo parent)
                if (detail.isComboChild()) {
                    continue;
                }
                
                String categoryName = detail.getItemMenu().getCategory().getName();
                // subtotal already includes IVA
                BigDecimal itemTotal = detail.getSubtotal();
                
                salesByCategory.merge(categoryName, itemTotal, BigDecimal::add);
            }
        }
        
        return salesByCategory;
    }

    /**
     * Calculate sales by employee (WITHOUT tip)
     * NOTE: Order.total already includes IVA
     * Uses paidBy field (who collected the payment) NOT employee (who created the order)
     * Returns list of maps with employee data: name, role, total
     */
    private List<Map<String, Object>> calculateSalesByEmployee(List<Order> orders) {
        Map<Long, Map<String, Object>> employeeDataMap = new HashMap<>();
        
        for (Order order : orders) {
            // Use paidBy (who collected payment) - this is what matters for sales attribution
            if (order.getPaidBy() != null) {
                Employee employee = order.getPaidBy();
                Long employeeId = employee.getIdEmpleado();
                
                if (!employeeDataMap.containsKey(employeeId)) {
                    Map<String, Object> employeeData = new HashMap<>();
                    employeeData.put("name", employee.getNombre() + " " + employee.getApellido());
                    employeeData.put("role", employee.getRoleDisplayName());
                    employeeData.put("initials", employee.getInitials());
                    employeeData.put("total", BigDecimal.ZERO);
                    employeeDataMap.put(employeeId, employeeData);
                }
                
                // Use total (subtotal + tax) without tip
                BigDecimal orderTotal = order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO;
                BigDecimal currentTotal = (BigDecimal) employeeDataMap.get(employeeId).get("total");
                employeeDataMap.get(employeeId).put("total", currentTotal.add(orderTotal));
            }
        }
        
        // Convert to list and sort by total (descending)
        List<Map<String, Object>> salesByEmployee = new ArrayList<>(employeeDataMap.values());
        salesByEmployee.sort((a, b) -> {
            BigDecimal totalA = (BigDecimal) a.get("total");
            BigDecimal totalB = (BigDecimal) b.get("total");
            return totalB.compareTo(totalA);
        });
        
        return salesByEmployee;
    }

    /**
     * Calculate orders by payment method
     */
    private Map<String, Long> calculateOrdersByPaymentMethod(List<Order> orders) {
        return orders.stream()
            .filter(order -> order.getPaymentMethod() != null)
            .collect(Collectors.groupingBy(
                order -> order.getPaymentMethod().getDisplayName(),
                Collectors.counting()
            ));
    }

    /**
     * Calculate top selling items (WITHOUT tip)
     * NOTE: OrderDetail.subtotal already includes IVA, no need to add it again
     * Combo children are excluded — only the combo parent is counted as a sold item
     */
    private List<Map<String, Object>> calculateTopSellingItems(List<Order> orders, int limit) {
        Map<Long, Map<String, Object>> itemSales = new HashMap<>();
        
        for (Order order : orders) {
            for (OrderDetail detail : order.getOrderDetails()) {
                // Skip combo children (they are part of a combo, not sold individually)
                if (detail.isComboChild()) {
                    continue;
                }
                
                Long itemId = detail.getItemMenu().getIdItemMenu();
                
                itemSales.putIfAbsent(itemId, new HashMap<>());
                Map<String, Object> itemData = itemSales.get(itemId);
                
                if (!itemData.containsKey("name")) {
                    itemData.put("name", detail.getItemMenu().getName());
                    itemData.put("category", detail.getItemMenu().getCategory().getName());
                    itemData.put("quantity", 0);
                    itemData.put("total", BigDecimal.ZERO);
                }
                
                int currentQuantity = (int) itemData.get("quantity");
                itemData.put("quantity", currentQuantity + detail.getQuantity());
                
                // subtotal already includes IVA
                BigDecimal itemTotal = detail.getSubtotal();
                
                BigDecimal currentTotal = (BigDecimal) itemData.get("total");
                itemData.put("total", currentTotal.add(itemTotal));
            }
        }
        
        return itemSales.values().stream()
            .sorted((a, b) -> {
                int qtyA = (int) a.get("quantity");
                int qtyB = (int) b.get("quantity");
                return Integer.compare(qtyB, qtyA);
            })
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Calculate top selling complements
     * Groups all OrderDetailComplements by complement name, sums quantities and subtotals
     * Includes complements from ALL order details (including combo children)
     * NOTE: Complement prices already include IVA
     * NOTE: Sauce complements (isSauce=true) have their stored subtotal and quantity
     *       multiplied by the OrderDetail.quantity (item quantity) because sauces are per-serving.
     *       Example: Combo qty=2 → Hamburguesa child qty=4 → sauce stored qty=1, real qty=1×4=4
     */
    private List<Map<String, Object>> calculateTopSellingComplements(List<Order> orders, int limit) {
        Map<Long, Map<String, Object>> complementSales = new HashMap<>();

        for (Order order : orders) {
            for (OrderDetail detail : order.getOrderDetails()) {
                if (detail.getSelectedComplements() == null) {
                    continue;
                }
                int itemQty = detail.getQuantity() != null ? detail.getQuantity() : 1;

                for (OrderDetailComplement odc : detail.getSelectedComplements()) {
                    Long complementId = odc.getComplement().getIdComplement();

                    complementSales.putIfAbsent(complementId, new HashMap<>());
                    Map<String, Object> data = complementSales.get(complementId);

                    if (!data.containsKey("name")) {
                        data.put("name", odc.getComplement().getName());
                        data.put("quantity", 0);
                        data.put("paidQuantity", 0);
                        data.put("total", BigDecimal.ZERO);
                    }

                    int compQty = odc.getQuantity();
                    BigDecimal compSubtotal = odc.getSubtotal() != null ? odc.getSubtotal() : BigDecimal.ZERO;
                    BigDecimal unitPrice = odc.getUnitPrice() != null ? odc.getUnitPrice() : BigDecimal.ZERO;

                    // Sauces are per-serving: multiply by item quantity
                    if (odc.getComplement() != null && Boolean.TRUE.equals(odc.getComplement().getIsSauce())) {
                        compQty = compQty * itemQty;
                        compSubtotal = compSubtotal.multiply(BigDecimal.valueOf(itemQty));
                    }

                    int currentQty = (int) data.get("quantity");
                    data.put("quantity", currentQty + compQty);

                    // paidQuantity: only count if unitPrice > 0
                    if (unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                        int currentPaidQty = (int) data.get("paidQuantity");
                        data.put("paidQuantity", currentPaidQty + compQty);
                    }

                    BigDecimal currentTotal = (BigDecimal) data.get("total");
                    data.put("total", currentTotal.add(compSubtotal));
                }
            }
        }

        return complementSales.values().stream()
            .sorted((a, b) -> {
                int qtyA = (int) a.get("quantity");
                int qtyB = (int) b.get("quantity");
                return Integer.compare(qtyB, qtyA);
            })
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Calculate total complement sales across all orders
     * Uses the same sauce multiplication logic as getComplementsTotal():
     * sauce subtotal × item quantity, non-sauce subtotal as-is
     * NOTE: Complement prices already include IVA
     */
    private BigDecimal calculateTotalComplementsSales(List<Order> orders) {
        BigDecimal total = BigDecimal.ZERO;
        for (Order order : orders) {
            for (OrderDetail detail : order.getOrderDetails()) {
                if (detail.getSelectedComplements() == null) {
                    continue;
                }
                int itemQty = detail.getQuantity() != null ? detail.getQuantity() : 1;

                for (OrderDetailComplement odc : detail.getSelectedComplements()) {
                    BigDecimal compSubtotal = odc.getSubtotal() != null ? odc.getSubtotal() : BigDecimal.ZERO;
                    // Sauces are per-serving: multiply by item quantity
                    if (odc.getComplement() != null && Boolean.TRUE.equals(odc.getComplement().getIsSauce())) {
                        compSubtotal = compSubtotal.multiply(BigDecimal.valueOf(itemQty));
                    }
                    total = total.add(compSubtotal);
                }
            }
        }
        return total;
    }

    // ========== PDF Download Endpoints ==========

    /**
     * Download Executive Report PDF
     */
    @GetMapping("/download/executive")
    public ResponseEntity<byte[]> downloadExecutivePdf(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        log.info("Generating Executive PDF Report - startDate: {}, endDate: {}", startDate, endDate);

        try {
            // Get all PAID orders
            List<Order> paidOrders = orderService.findByStatus(OrderStatus.PAID);
            
            // Apply date filter
            if (startDate != null && !startDate.isEmpty()) {
                paidOrders = filterByDateRange(paidOrders, startDate, endDate);
            }

            // Calculate statistics
            BigDecimal totalSales = calculateTotalSales(paidOrders);
            long totalOrders = paidOrders.size();
            Map<String, BigDecimal> salesByCategory = calculateSalesByCategory(paidOrders);
            List<Map<String, Object>> salesByEmployee = calculateSalesByEmployee(paidOrders);
            Map<String, Long> ordersByPaymentMethod = calculateOrdersByPaymentMethod(paidOrders);
            List<Map<String, Object>> topSellingItems = calculateTopSellingItems(paidOrders, 10);
            List<Map<String, Object>> topSellingComplements = calculateTopSellingComplements(paidOrders, 5);
            BigDecimal totalComplementsSales = calculateTotalComplementsSales(paidOrders);

            // Generate PDF
            byte[] pdfBytes = reportPdfService.generateExecutiveReport(
                paidOrders, startDate, endDate, totalSales, totalOrders,
                salesByCategory, salesByEmployee, ordersByPaymentMethod, topSellingItems,
                topSellingComplements, totalComplementsSales
            );

            // Prepare response
            String filename = "Reporte_Ejecutivo_" + getCurrentDateForFilename() + ".pdf";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);

        } catch (Exception e) {
            log.error("Error generating Executive PDF report", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download Products Report PDF
     */
    @GetMapping("/download/products")
    public ResponseEntity<byte[]> downloadProductsPdf(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        log.info("Generating Products PDF Report - startDate: {}, endDate: {}", startDate, endDate);

        try {
            // Get all PAID orders
            List<Order> paidOrders = orderService.findByStatus(OrderStatus.PAID);
            
            // Apply date filter
            if (startDate != null && !startDate.isEmpty()) {
                paidOrders = filterByDateRange(paidOrders, startDate, endDate);
            }

            // Calculate top selling items
            List<Map<String, Object>> topSellingItems = calculateTopSellingItems(paidOrders, 50);
            List<Map<String, Object>> allComplements = calculateTopSellingComplements(paidOrders, Integer.MAX_VALUE);
            BigDecimal totalComplementsSales = calculateTotalComplementsSales(paidOrders);

            // Generate PDF
            byte[] pdfBytes = reportPdfService.generateProductsReport(
                paidOrders, startDate, endDate, topSellingItems,
                allComplements, totalComplementsSales
            );

            // Prepare response
            String filename = "Reporte_Productos_" + getCurrentDateForFilename() + ".pdf";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);

        } catch (Exception e) {
            log.error("Error generating Products PDF report", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download Employees Report PDF
     * Excludes orders created by customers (web orders)
     */
    @GetMapping("/download/employees")
    public ResponseEntity<byte[]> downloadEmployeesPdf(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        log.info("Generating Employees PDF Report - startDate: {}, endDate: {}", startDate, endDate);

        try {
            // Get all PAID orders
            List<Order> paidOrders = orderService.findByStatus(OrderStatus.PAID);
            
            // Apply date filter
            if (startDate != null && !startDate.isEmpty()) {
                paidOrders = filterByDateRange(paidOrders, startDate, endDate);
            }

            // Exclude customer-created orders (web orders)
            List<Order> employeeOrders = paidOrders.stream()
                .filter(o -> o.getEmployee() != null && o.getCustomer() == null)
                .collect(Collectors.toList());

            // Calculate statistics (excluding web orders)
            BigDecimal totalSales = calculateTotalSales(employeeOrders);
            List<Map<String, Object>> salesByEmployee = calculateSalesByEmployee(employeeOrders);

            // Generate PDF
            byte[] pdfBytes = reportPdfService.generateEmployeesReport(
                employeeOrders, startDate, endDate, salesByEmployee, totalSales
            );

            // Prepare response
            String filename = "Reporte_Empleados_" + getCurrentDateForFilename() + ".pdf";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);

        } catch (Exception e) {
            log.error("Error generating Employees PDF report", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download Clients Report PDF
     * Only includes orders created by customers (web orders)
     */
    @GetMapping("/download/clients")
    public ResponseEntity<byte[]> downloadClientsPdf(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        log.info("Generating Clients PDF Report - startDate: {}, endDate: {}", startDate, endDate);

        try {
            // Get all PAID orders
            List<Order> paidOrders = orderService.findByStatus(OrderStatus.PAID);
            
            // Apply date filter
            if (startDate != null && !startDate.isEmpty()) {
                paidOrders = filterByDateRange(paidOrders, startDate, endDate);
            }

            // Only customer-created orders (web orders)
            List<Order> customerOrders = paidOrders.stream()
                .filter(o -> o.getCustomer() != null)
                .collect(Collectors.toList());

            // MULTI-TENANT: Get ALL customers registered in this company
            Company company = CompanyContext.requireCurrentCompany();
            List<Customer> relevantCustomers = customerRepository.findByCompany(company).stream()
                .sorted(Comparator.comparing(Customer::getFullName))
                .collect(Collectors.toList());

            // Generate PDF
            byte[] pdfBytes = reportPdfService.generateClientsReport(
                customerOrders, relevantCustomers, startDate, endDate
            );

            // Prepare response
            String filename = "Reporte_Clientes_" + getCurrentDateForFilename() + ".pdf";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);

        } catch (Exception e) {
            log.error("Error generating Clients PDF report", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get current date formatted for filename
     */
    private String getCurrentDateForFilename() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        return dateTimeService.nowLocal().format(formatter);
    }

    /**
     * Show Income and Expenses Report
     */
    @GetMapping("/income-expenses")
    public String showIncomeExpensesReport(Model model) {
        log.info("Showing income and expenses report");

        try {
            // Get total income and expenses
            BigDecimal totalIncome = orderService.getTotalIncome();
            BigDecimal totalExpenses = ingredientService.getTotalExpenses();
            BigDecimal netProfit = totalIncome.subtract(totalExpenses);

            // Calculate complement sales total
            List<Order> paidOrders = orderService.findByStatus(OrderStatus.PAID);
            BigDecimal totalComplementsSales = calculateTotalComplementsSales(paidOrders);
            BigDecimal totalItemIncome = totalIncome.subtract(totalComplementsSales);

            // Get categories
            List<IngredientCategory> ingredientCategories = ingredientCategoryService.findAll();
            List<Category> menuCategories = categoryService.getAllActiveCategories();

            // Get expenses by category
            Map<String, BigDecimal> expensesByCategory = ingredientService.getExpensesByCategory();

            // Get income by category
            Map<String, BigDecimal> incomeByCategory = orderService.getIncomeByCategory();

            model.addAttribute("totalIncome", totalIncome);
            model.addAttribute("totalExpenses", totalExpenses);
            model.addAttribute("netProfit", netProfit);
            model.addAttribute("totalComplementsSales", totalComplementsSales);
            model.addAttribute("totalItemIncome", totalItemIncome);
            model.addAttribute("ingredientCategories", ingredientCategories);
            model.addAttribute("menuCategories", menuCategories);
            model.addAttribute("expensesByCategory", expensesByCategory);
            model.addAttribute("incomeByCategory", incomeByCategory);

            return "admin/reports/income-expenses";

        } catch (Exception e) {
            log.error("Error loading income and expenses report: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Error al cargar el reporte: " + e.getMessage());
            return "admin/reports/income-expenses";
        }
    }

    /**
     * Get expense details by ingredient category (AJAX)
     */
    @GetMapping("/income-expenses/expenses/{categoryId}")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getExpensesByCategory(@PathVariable Long categoryId) {
        log.info("Getting expense details for ingredient category ID: {}", categoryId);

        try {
            List<Object[]> results = ingredientService.getExpenseDetailsByCategory(categoryId);
            List<Map<String, Object>> response = new ArrayList<>();

            for (Object[] row : results) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", row[0]); // ingredient name
                item.put("quantity", row[1]); // total quantity purchased
                item.put("total", row[2]); // total expense
                response.add(item);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting expenses by category: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get income details by menu category (AJAX)
     */
    @GetMapping("/income-expenses/income/{categoryId}")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getIncomeByCategory(@PathVariable Long categoryId) {
        log.info("Getting income details for menu category ID: {}", categoryId);

        try {
            List<Object[]> results = orderService.getItemSalesByCategory(categoryId);
            List<Map<String, Object>> response = new ArrayList<>();

            for (Object[] row : results) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", row[0]); // item name
                item.put("quantity", row[1]); // total quantity sold
                item.put("total", row[2]); // total sales
                response.add(item);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting income by category: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get complement sales details (AJAX)
     */
    @GetMapping("/income-expenses/income/complements")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getComplementSalesDetails() {
        log.info("Getting complement sales details for income-expenses report");

        try {
            List<Order> paidOrders = orderService.findByStatus(OrderStatus.PAID);
            List<Map<String, Object>> allComplements = calculateTopSellingComplements(paidOrders, Integer.MAX_VALUE);
            return ResponseEntity.ok(allComplements);

        } catch (Exception e) {
            log.error("Error getting complement sales details: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

