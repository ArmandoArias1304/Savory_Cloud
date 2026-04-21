package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating PDF reports
 * Generates 3 types of reports: Executive, Products, and Employees
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportPdfService {

    private final SystemConfigurationService systemConfigurationService;
    private final EmployeeRepository employeeRepository;
    private final DateTimeService dateTimeService;

    // Color palette - matching your theme
    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(56, 224, 123); // #38e07b
    private static final DeviceRgb PRIMARY_DARK = new DeviceRgb(43, 200, 102); // #2bc866
    private static final DeviceRgb DARK_COLOR = new DeviceRgb(45, 45, 45);
    private static final DeviceRgb GRAY_COLOR = new DeviceRgb(107, 114, 128);
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(249, 250, 251);
    private static final DeviceRgb WHITE = new DeviceRgb(255, 255, 255);

    /**
     * Generate Executive Report (All-in-one summary)
     */
    public byte[] generateExecutiveReport(
            java.util.List<Order> paidOrders,
            String startDate,
            String endDate,
            BigDecimal totalSales,
            long totalOrders,
            Map<String, BigDecimal> salesByCategory,
            java.util.List<Map<String, Object>> salesByEmployee,
            Map<String, Long> ordersByPaymentMethod,
            java.util.List<Map<String, Object>> topSellingItems,
            java.util.List<Map<String, Object>> topSellingComplements,
            BigDecimal totalComplementsSales) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, PageSize.LETTER);
        document.setMargins(40, 40, 40, 40);

        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        // Header
        addHeader(document, boldFont, regularFont, "REPORTE EJECUTIVO DE VENTAS");
        addDateRange(document, regularFont, startDate, endDate);
        document.add(new Paragraph("\n"));

        // Summary Section
        addSectionTitle(document, boldFont, "Resumen General");
        Table summaryTable = new Table(new float[]{1, 1, 1});
        summaryTable.setWidth(UnitValue.createPercentValue(100));
        
        addSummaryCell(summaryTable, boldFont, regularFont, "Total de Ventas", 
            String.format("$%,.2f", totalSales));
        addSummaryCell(summaryTable, boldFont, regularFont, "Órdenes Pagadas", 
            String.valueOf(totalOrders));
        addSummaryCell(summaryTable, boldFont, regularFont, "Ticket Promedio", 
            totalOrders > 0 ? String.format("$%,.2f", totalSales.divide(BigDecimal.valueOf(totalOrders), 2, java.math.RoundingMode.HALF_UP)) : "$0.00");
        
        document.add(summaryTable);
        document.add(new Paragraph("\n"));

        // Top 5 Products
        addSectionTitle(document, boldFont, "Top 5 Productos Más Vendidos");
        Table productsTable = new Table(new float[]{3, 1, 1, 2});
        productsTable.setWidth(UnitValue.createPercentValue(100));
        addTableHeader(productsTable, boldFont, "Producto", "Cant.", "Cat.", "Total");
        
        topSellingItems.stream().limit(5).forEach(item -> {
            addTableRow(productsTable, regularFont,
                item.get("name").toString(),
                item.get("quantity").toString(),
                item.get("category").toString(),
                String.format("$%,.2f", item.get("total"))
            );
        });
        document.add(productsTable);
        document.add(new Paragraph("\n"));

        // Top 5 Complements
        if (topSellingComplements != null && !topSellingComplements.isEmpty()) {
            addSectionTitle(document, boldFont, "Top 5 Complementos Más Vendidos");
            Table complementsTable = new Table(new float[]{3, 1, 1, 2});
            complementsTable.setWidth(UnitValue.createPercentValue(100));
            addTableHeader(complementsTable, boldFont, "Complemento", "Cant.", "Cant. Pagada", "Total");

            topSellingComplements.stream().limit(5).forEach(comp -> {
                addTableRow(complementsTable, regularFont,
                    comp.get("name").toString(),
                    comp.get("quantity").toString(),
                    comp.get("paidQuantity").toString(),
                    String.format("$%,.2f", comp.get("total"))
                );
            });
            document.add(complementsTable);

            // Total complements legend
            Paragraph complementsLegend = new Paragraph(
                String.format("Total ventas de complementos: $%,.2f", totalComplementsSales))
                .setFont(regularFont)
                .setFontSize(9)
                .setFontColor(GRAY_COLOR)
                .setItalic()
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginTop(4);
            document.add(complementsLegend);
            document.add(new Paragraph("\n"));
        }

        // Sales by Category (uses item-only totals, excluding complements)
        addSectionTitle(document, boldFont, "Ventas por Categoría (solo productos)");
        Table categoryTable = new Table(new float[]{3, 2, 2});
        categoryTable.setWidth(UnitValue.createPercentValue(100));
        addTableHeader(categoryTable, boldFont, "Categoría", "Total", "% Part.");
        
        // Sum of category values (item-only, no complements)
        final BigDecimal totalItemSales = salesByCategory.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        salesByCategory.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .forEach(entry -> {
                BigDecimal percentage = totalItemSales.compareTo(BigDecimal.ZERO) > 0
                    ? entry.getValue().multiply(BigDecimal.valueOf(100)).divide(totalItemSales, 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
                addTableRow(categoryTable, regularFont,
                    entry.getKey(),
                    String.format("$%,.2f", entry.getValue()),
                    String.format("%.2f%%", percentage)
                );
            });
        document.add(categoryTable);
        document.add(new Paragraph("\n"));

        // Sales by Employee
        if (!salesByEmployee.isEmpty()) {
            addSectionTitle(document, boldFont, "Ventas por Empleado (Cobradas)");
            Table employeeTable = new Table(new float[]{4, 2, 2, 1});
            employeeTable.setWidth(UnitValue.createPercentValue(100));
            addTableHeader(employeeTable, boldFont, "Empleado", "Rol", "Total Cobrado", "Órdenes");
            
            // Map to count orders by employee who collected payment (paidBy)
            Map<String, Long> ordersByEmployee = paidOrders.stream()
                .filter(o -> o.getPaidBy() != null)
                .collect(Collectors.groupingBy(
                    o -> o.getPaidBy().getNombre() + " " + o.getPaidBy().getApellido(),
                    Collectors.counting()
                ));
            
            for (Map<String, Object> employee : salesByEmployee) {
                String name = (String) employee.get("name");
                String role = (String) employee.get("role");
                BigDecimal total = (BigDecimal) employee.get("total");
                
                addTableRow(employeeTable, regularFont,
                    name,
                    role,
                    String.format("$%,.2f", total),
                    String.valueOf(ordersByEmployee.getOrDefault(name, 0L))
                );
            }
            document.add(employeeTable);
            document.add(new Paragraph("\n"));
        }

        // Payment Methods
        addSectionTitle(document, boldFont, "Métodos de Pago");
        Table paymentTable = new Table(new float[]{3, 2, 2});
        paymentTable.setWidth(UnitValue.createPercentValue(100));
        addTableHeader(paymentTable, boldFont, "Método", "Órdenes", "% Part.");
        
        ordersByPaymentMethod.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> {
                double percentage = totalOrders > 0 
                    ? (entry.getValue() * 100.0 / totalOrders) 
                    : 0.0;
                addTableRow(paymentTable, regularFont,
                    entry.getKey(),
                    String.valueOf(entry.getValue()),
                    String.format("%.2f%%", percentage)
                );
            });
        document.add(paymentTable);
        document.add(new Paragraph("\n"));

        // Web Orders Section (Orders created by customers)
        java.util.List<Order> webOrders = paidOrders.stream()
            .filter(order -> order.getCustomer() != null && order.getEmployee() == null)
            .collect(Collectors.toList());
        
        if (!webOrders.isEmpty()) {
            addSectionTitle(document, boldFont, "Pedidos Web (Clientes)");
            
            // Web orders summary
            BigDecimal webOrdersTotal = webOrders.stream()
                .map(order -> order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal webOrdersAverage = webOrders.size() > 0 
                ? webOrdersTotal.divide(BigDecimal.valueOf(webOrders.size()), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            
            Table webSummaryTable = new Table(new float[]{1, 1, 1});
            webSummaryTable.setWidth(UnitValue.createPercentValue(100));
            
            addSummaryCell(webSummaryTable, boldFont, regularFont, "Total Pedidos Web", 
                String.valueOf(webOrders.size()));
            addSummaryCell(webSummaryTable, boldFont, regularFont, "Ventas Totales", 
                String.format("$%,.2f", webOrdersTotal));
            addSummaryCell(webSummaryTable, boldFont, regularFont, "Ticket Promedio", 
                String.format("$%,.2f", webOrdersAverage));
            
            document.add(webSummaryTable);
            document.add(new Paragraph("\n"));
            
            // Web orders detail table
            Table webOrdersTable = new Table(new float[]{2, 3, 2, 2, 2});
            webOrdersTable.setWidth(UnitValue.createPercentValue(100));
            addTableHeader(webOrdersTable, boldFont, "Orden", "Cliente", "Tipo", "Total", "Pago");
            
            webOrders.stream()
                .sorted((o1, o2) -> {
                    LocalDateTime date1 = o1.getUpdatedAt() != null ? o1.getUpdatedAt() : o1.getCreatedAt();
                    LocalDateTime date2 = o2.getUpdatedAt() != null ? o2.getUpdatedAt() : o2.getCreatedAt();
                    return date2.compareTo(date1); // Most recent first
                })
                .forEach(order -> {
                    String customerName = order.getCustomer() != null 
                        ? order.getCustomer().getFullName()
                        : "N/A";
                    String orderType = order.getOrderType() != null 
                        ? order.getOrderType().getDisplayName()
                        : "N/A";
                    String paymentMethod = order.getPaymentMethod() != null
                        ? order.getPaymentMethod().getDisplayName()
                        : "N/A";
                    
                    addTableRow(webOrdersTable, regularFont,
                        order.getOrderNumber(),
                        customerName,
                        orderType,
                        String.format("$%,.2f", order.getTotal()),
                        paymentMethod
                    );
                });
            
            document.add(webOrdersTable);
            document.add(new Paragraph("\n"));
        }

        // Footer
        addFooter(document, regularFont);

        document.close();
        return baos.toByteArray();
    }

    /**
     * Generate Products Report (Top selling products detailed)
     */
    public byte[] generateProductsReport(
            java.util.List<Order> paidOrders,
            String startDate,
            String endDate,
            java.util.List<Map<String, Object>> topSellingItems,
            java.util.List<Map<String, Object>> allComplements,
            BigDecimal totalComplementsSales) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, PageSize.LETTER);
        document.setMargins(40, 40, 40, 40);

        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        // Header
        addHeader(document, boldFont, regularFont, "REPORTE DE PRODUCTOS MÁS VENDIDOS");
        addDateRange(document, regularFont, startDate, endDate);
        document.add(new Paragraph("\n"));

        // Summary
        BigDecimal totalProductSales = topSellingItems.stream()
            .map(item -> (BigDecimal) item.get("total"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        int totalQuantity = topSellingItems.stream()
            .mapToInt(item -> (Integer) item.get("quantity"))
            .sum();

        addSectionTitle(document, boldFont, "Resumen");
        Table summaryTable = new Table(new float[]{1, 1, 1});
        summaryTable.setWidth(UnitValue.createPercentValue(100));
        
        addSummaryCell(summaryTable, boldFont, regularFont, "Total Productos Vendidos", 
            String.valueOf(totalQuantity));
        addSummaryCell(summaryTable, boldFont, regularFont, "Variedades Diferentes", 
            String.valueOf(topSellingItems.size()));
        BigDecimal totalIncome = totalProductSales.add(
            totalComplementsSales != null ? totalComplementsSales : BigDecimal.ZERO);
        addSummaryCell(summaryTable, boldFont, regularFont, "Ingresos Generados", 
            String.format("$%,.2f", totalIncome));
        
        document.add(summaryTable);
        document.add(new Paragraph("\n"));

        // Products Table
        addSectionTitle(document, boldFont, "Detalle de Productos");
        Table table = new Table(new float[]{0.5f, 3, 2, 1, 2, 2});
        table.setWidth(UnitValue.createPercentValue(100));
        addTableHeader(table, boldFont, "#", "Producto", "Categoría", "Cant.", "Total", "% Part.");
        
        int rank = 1;
        for (Map<String, Object> item : topSellingItems) {
            BigDecimal itemTotal = (BigDecimal) item.get("total");
            double percentage = totalProductSales.compareTo(BigDecimal.ZERO) > 0
                ? itemTotal.multiply(BigDecimal.valueOf(100)).divide(totalProductSales, 2, java.math.RoundingMode.HALF_UP).doubleValue()
                : 0.0;
            
            addTableRow(table, regularFont,
                String.valueOf(rank++),
                item.get("name").toString(),
                item.get("category").toString(),
                item.get("quantity").toString(),
                String.format("$%,.2f", itemTotal),
                String.format("%.2f%%", percentage)
            );
        }
        document.add(table);
        document.add(new Paragraph("\n"));

        // Complements Section
        if (allComplements != null && !allComplements.isEmpty()) {
            // Complements Summary (similar to Products Summary)
            int totalComplementsQuantity = allComplements.stream()
                .mapToInt(comp -> (Integer) comp.get("quantity"))
                .sum();
            
            addSectionTitle(document, boldFont, "Resumen de Complementos");
            Table complementsSummaryTable = new Table(new float[]{1, 1, 1});
            complementsSummaryTable.setWidth(UnitValue.createPercentValue(100));
            
            addSummaryCell(complementsSummaryTable, boldFont, regularFont, "Total Complementos Vendidos", 
                String.valueOf(totalComplementsQuantity));
            addSummaryCell(complementsSummaryTable, boldFont, regularFont, "Variedades Diferentes", 
                String.valueOf(allComplements.size()));
            addSummaryCell(complementsSummaryTable, boldFont, regularFont, "Ingresos Generados", 
                String.format("$%,.2f", totalComplementsSales != null ? totalComplementsSales : BigDecimal.ZERO));
            
            document.add(complementsSummaryTable);
            document.add(new Paragraph("\n"));

            // Complements Table
            addSectionTitle(document, boldFont, "Detalle de Complementos Vendidos");

            Table compTable = new Table(new float[]{0.5f, 3, 1, 1, 2, 2});
            compTable.setWidth(UnitValue.createPercentValue(100));
            addTableHeader(compTable, boldFont, "#", "Complemento", "Cant.", "Cant. Pagada", "Total", "% Part.");

            BigDecimal totalCompSales = totalComplementsSales != null && totalComplementsSales.compareTo(BigDecimal.ZERO) > 0
                ? totalComplementsSales : BigDecimal.ONE;

            int compRank = 1;
            for (Map<String, Object> comp : allComplements) {
                BigDecimal compTotal = (BigDecimal) comp.get("total");
                double compPercentage = compTotal.multiply(BigDecimal.valueOf(100))
                    .divide(totalCompSales, 2, java.math.RoundingMode.HALF_UP).doubleValue();

                addTableRow(compTable, regularFont,
                    String.valueOf(compRank++),
                    comp.get("name").toString(),
                    comp.get("quantity").toString(),
                    comp.get("paidQuantity").toString(),
                    String.format("$%,.2f", compTotal),
                    String.format("%.2f%%", compPercentage)
                );
            }
            document.add(compTable);

            // Total complements legend
            Paragraph complementsLegend = new Paragraph(
                String.format("Total ventas de complementos: $%,.2f", totalComplementsSales))
                .setFont(boldFont)
                .setFontSize(10)
                .setFontColor(DARK_COLOR)
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginTop(6);
            document.add(complementsLegend);
        }

        // Footer
        addFooter(document, regularFont);

        document.close();
        return baos.toByteArray();
    }

    /**
     * Generate Employees Report (Employee performance by role)
     * Creates separate tables for each employee role with specific metrics
     * Excludes ONLINE (web) orders
     */
    public byte[] generateEmployeesReport(
            java.util.List<Order> paidOrders,
            String startDate,
            String endDate,
            java.util.List<Map<String, Object>> salesByEmployee,
            BigDecimal totalSales) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, PageSize.LETTER);
        document.setMargins(40, 40, 40, 40);

        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        // Header
        addHeader(document, boldFont, regularFont, "REPORTE DE DESEMPEÑO POR EMPLEADO");
        addDateRange(document, regularFont, startDate, endDate);
        document.add(new Paragraph("\n"));

        // Note about excluded orders
        Paragraph note = new Paragraph("📌 Nota: Este reporte excluye pedidos creados por clientes (pedidos realizados en línea)")
            .setFont(regularFont)
            .setFontSize(9)
            .setFontColor(GRAY_COLOR)
            .setItalic()
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(10);
        document.add(note);

        // Get ALL enabled employees (except Programmer) to always show all role sections
        Company currentCompany = CompanyContext.getCurrentCompany();
        java.util.List<Employee> allEnabledEmployees = (currentCompany != null
            ? employeeRepository.findByEnabledTrueAndCompany(currentCompany)
            : employeeRepository.findByEnabledTrue()).stream()
            .filter(emp -> !emp.hasRole(Role.PROGRAMMER))
            .collect(Collectors.toList());

        Set<Employee> allEmployees = new HashSet<>(allEnabledEmployees);

        // Group employees by their roles
        java.util.List<Employee> waiters = new ArrayList<>();
        java.util.List<Employee> chefs = new ArrayList<>();
        java.util.List<Employee> baristas = new ArrayList<>();
        java.util.List<Employee> cashiers = new ArrayList<>();
        java.util.List<Employee> deliveryPersons = new ArrayList<>();
        java.util.List<Employee> admins = new ArrayList<>();
        
        for (Employee emp : allEmployees) {
            if (emp.hasRole(Role.WAITER)) waiters.add(emp);
            if (emp.hasRole(Role.CHEF)) chefs.add(emp);
            if (emp.hasRole(Role.BARISTA)) baristas.add(emp);
            if (emp.hasRole(Role.CASHIER)) cashiers.add(emp);
            if (emp.hasRole(Role.DELIVERY)) deliveryPersons.add(emp);
            if (emp.hasRole(Role.ADMIN) || emp.hasRole(Role.MANAGER)) admins.add(emp);
        }

        // Summary
        addSectionTitle(document, boldFont, "Resumen General");
        Table summaryTable = new Table(new float[]{1, 1, 1});
        summaryTable.setWidth(UnitValue.createPercentValue(100));
        
        long totalEmployees = allEmployees.size();
        addSummaryCell(summaryTable, boldFont, regularFont, "Empleados Activos", 
            String.valueOf(totalEmployees));
        addSummaryCell(summaryTable, boldFont, regularFont, "Total Ventas (Sin contar pedidos en línea)", 
            String.format("$%,.2f", totalSales));
        addSummaryCell(summaryTable, boldFont, regularFont, "Promedio por Empleado", 
            totalEmployees > 0 ? String.format("$%,.2f", totalSales.divide(BigDecimal.valueOf(totalEmployees), 2, java.math.RoundingMode.HALF_UP)) : "$0.00");
        
        document.add(summaryTable);
        document.add(new Paragraph("\n"));

        // === MESEROS (Waiters) ===
        {
            addSectionTitle(document, boldFont, "👔 Meseros - Cobros Realizados");
            Table waitersTable = new Table(new float[]{0.5f, 2.5f, 1.8f, 1, 1, 1.5f, 1.2f});
            waitersTable.setWidth(UnitValue.createPercentValue(100));
            addTableHeader(waitersTable, boldFont, "#", "Nombre", "Total Cobrado", "Cobradas", "Creadas", "Promedio", "Propinas");
            
            int rank = 1;
            
            // Sort by sales (using paidBy - who collected payment)
            waiters.sort((a, b) -> {
                BigDecimal salesA = getEmployeeSales(salesByEmployee, a.getFullName());
                BigDecimal salesB = getEmployeeSales(salesByEmployee, b.getFullName());
                return salesB.compareTo(salesA);
            });
            
            for (Employee emp : waiters) {
                String empName = emp.getFullName();
                BigDecimal sales = getEmployeeSales(salesByEmployee, empName);
                
                // Count orders where this employee collected payment (paidBy)
                long ordersCobradas = paidOrders.stream()
                    .filter(o -> o.getPaidBy() != null && o.getPaidBy().getIdEmpleado().equals(emp.getIdEmpleado()))
                    .count();
                
                // Count orders created by this employee
                long ordersCreadas = paidOrders.stream()
                    .filter(o -> o.getEmployee() != null && o.getEmployee().getIdEmpleado().equals(emp.getIdEmpleado()))
                    .count();
                    
                BigDecimal avgPerOrder = ordersCobradas > 0 ? sales.divide(BigDecimal.valueOf(ordersCobradas), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
                
                // Tips from orders collected by this employee
                BigDecimal tips = paidOrders.stream()
                    .filter(o -> o.getPaidBy() != null && o.getPaidBy().getIdEmpleado().equals(emp.getIdEmpleado()))
                    .map(o -> o.getTip() != null ? o.getTip() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                addTableRow(waitersTable, regularFont,
                    String.valueOf(rank++),
                    empName,
                    String.format("$%,.2f", sales),
                    String.valueOf(ordersCobradas),
                    String.valueOf(ordersCreadas),
                    String.format("$%,.2f", avgPerOrder),
                    String.format("$%,.2f", tips)
                );
            }
            document.add(waitersTable);
            document.add(new Paragraph("\n"));
        }

        // === CHEFS ===
        {
            addSectionTitle(document, boldFont, "👨‍🍳 Chefs - Órdenes Preparadas");
            Table chefsTable = new Table(new float[]{0.5f, 3, 2, 2, 2});
            chefsTable.setWidth(UnitValue.createPercentValue(100));
            addTableHeader(chefsTable, boldFont, "#", "Nombre", "Órdenes Preparadas", "Platos Totales", "Promedio/Orden");
            
            int rank = 1;
            
            for (Employee emp : chefs) {
                // Count orders where this chef was preparedBy
                long ordersPrep = paidOrders.stream()
                    .filter(o -> o.getPreparedBy() != null && o.getPreparedBy().getIdEmpleado().equals(emp.getIdEmpleado()))
                    .count();
                
                // Count total dishes (order details)
                long totalDishes = paidOrders.stream()
                    .filter(o -> o.getPreparedBy() != null && o.getPreparedBy().getIdEmpleado().equals(emp.getIdEmpleado()))
                    .mapToLong(o -> o.getOrderDetails().size())
                    .sum();
                
                double avgDishesPerOrder = ordersPrep > 0 ? (double) totalDishes / ordersPrep : 0.0;
                
                addTableRow(chefsTable, regularFont,
                    String.valueOf(rank++),
                    emp.getFullName(),
                    String.valueOf(ordersPrep),
                    String.valueOf(totalDishes),
                    String.format("%.1f", avgDishesPerOrder)
                );
            }
            document.add(chefsTable);
            document.add(new Paragraph("\n"));
        }

        // === BARISTAS ===
        {
            addSectionTitle(document, boldFont, "☕ Baristas - Bebidas Preparadas");
            Table baristasTable = new Table(new float[]{0.5f, 3, 2, 2, 2});
            baristasTable.setWidth(UnitValue.createPercentValue(100));
            addTableHeader(baristasTable, boldFont, "#", "Nombre", "Órdenes Preparadas", "Bebidas Totales", "Promedio/Orden");
            
            int baristaRank = 1;
            
            for (Employee emp : baristas) {
                // Count orders where this barista was preparedByBarista
                long ordersPrep = paidOrders.stream()
                    .filter(o -> o.getPreparedByBarista() != null && o.getPreparedByBarista().getIdEmpleado().equals(emp.getIdEmpleado()))
                    .count();
                
                // Count total items (order details)
                long totalItems = paidOrders.stream()
                    .filter(o -> o.getPreparedByBarista() != null && o.getPreparedByBarista().getIdEmpleado().equals(emp.getIdEmpleado()))
                    .mapToLong(o -> o.getOrderDetails().size())
                    .sum();
                
                double avgItemsPerOrder = ordersPrep > 0 ? (double) totalItems / ordersPrep : 0.0;
                
                addTableRow(baristasTable, regularFont,
                    String.valueOf(baristaRank++),
                    emp.getFullName(),
                    String.valueOf(ordersPrep),
                    String.valueOf(totalItems),
                    String.format("%.1f", avgItemsPerOrder)
                );
            }
            document.add(baristasTable);
            document.add(new Paragraph("\n"));
        }

        // === CAJEROS (Cashiers) ===
        {
            addSectionTitle(document, boldFont, "💰 Cajeros - Cobros Realizados");
            Table cashiersTable = new Table(new float[]{0.5f, 2.5f, 2, 1, 1, 1.5f});
            cashiersTable.setWidth(UnitValue.createPercentValue(100));
            addTableHeader(cashiersTable, boldFont, "#", "Nombre", "Total Cobrado", "Cobradas", "Creadas", "Propinas");
            
            int rank = 1;
            
            // Sort by total collected (using paidBy)
            cashiers.sort((a, b) -> {
                BigDecimal salesA = getEmployeeSales(salesByEmployee, a.getFullName());
                BigDecimal salesB = getEmployeeSales(salesByEmployee, b.getFullName());
                return salesB.compareTo(salesA);
            });
            
            for (Employee emp : cashiers) {
                String empName = emp.getFullName();
                BigDecimal totalCollected = getEmployeeSales(salesByEmployee, empName);
                
                // Count orders where this cashier collected payment (paidBy)
                long ordersCobradas = paidOrders.stream()
                    .filter(o -> o.getPaidBy() != null && o.getPaidBy().getIdEmpleado().equals(emp.getIdEmpleado()))
                    .count();
                
                // Count orders created by this cashier
                long ordersCreadas = paidOrders.stream()
                    .filter(o -> o.getEmployee() != null && o.getEmployee().getIdEmpleado().equals(emp.getIdEmpleado()))
                    .count();
                    
                // Tips from orders collected by this cashier
                BigDecimal tips = paidOrders.stream()
                    .filter(o -> o.getPaidBy() != null && o.getPaidBy().getIdEmpleado().equals(emp.getIdEmpleado()))
                    .map(o -> o.getTip() != null ? o.getTip() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                addTableRow(cashiersTable, regularFont,
                    String.valueOf(rank++),
                    empName,
                    String.format("$%,.2f", totalCollected),
                    String.valueOf(ordersCobradas),
                    String.valueOf(ordersCreadas),
                    String.format("$%,.2f", tips)
                );
            }
            document.add(cashiersTable);
            document.add(new Paragraph("\n"));
        }

        // === REPARTIDORES (Delivery) ===
        {
            addSectionTitle(document, boldFont, "🚗 Repartidores - Entregas Realizadas");
            Table deliveryTable = new Table(new float[]{0.5f, 3, 1.5f, 2, 1.5f});
            deliveryTable.setWidth(UnitValue.createPercentValue(100));
            addTableHeader(deliveryTable, boldFont, "#", "Nombre", "Entregas", "Total Cobrado", "Propinas");
            
            int rank = 1;
            
            for (Employee emp : deliveryPersons) {
                // Count DELIVERY orders delivered by this person
                long deliveries = paidOrders.stream()
                    .filter(o -> o.getOrderType() == OrderType.DELIVERY)
                    .filter(o -> o.getDeliveredBy() != null && o.getDeliveredBy().getIdEmpleado().equals(emp.getIdEmpleado()))
                    .count();
                
                BigDecimal totalDelivered = paidOrders.stream()
                    .filter(o -> o.getOrderType() == OrderType.DELIVERY)
                    .filter(o -> o.getDeliveredBy() != null && o.getDeliveredBy().getIdEmpleado().equals(emp.getIdEmpleado()))
                    .map(o -> o.getTotal() != null ? o.getTotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                BigDecimal tips = paidOrders.stream()
                    .filter(o -> o.getOrderType() == OrderType.DELIVERY)
                    .filter(o -> o.getDeliveredBy() != null && o.getDeliveredBy().getIdEmpleado().equals(emp.getIdEmpleado()))
                    .map(o -> o.getTip() != null ? o.getTip() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                addTableRow(deliveryTable, regularFont,
                    String.valueOf(rank++),
                    emp.getFullName(),
                    String.valueOf(deliveries),
                    String.format("$%,.2f", totalDelivered),
                    String.format("$%,.2f", tips)
                );
            }
            document.add(deliveryTable);
            document.add(new Paragraph("\n"));
        }

        // === ADMINISTRADORES y GERENTES ===
        {
            admins = admins.stream().distinct().collect(Collectors.toList());
            
            addSectionTitle(document, boldFont, "👨‍💼 Administradores y Gerentes - Cobros Realizados");
            Table adminsTable = new Table(new float[]{0.5f, 2.5f, 1.8f, 1, 1, 1.8f});
            adminsTable.setWidth(UnitValue.createPercentValue(100));
            addTableHeader(adminsTable, boldFont, "#", "Nombre", "Rol", "Cobradas", "Creadas", "Total Cobrado");
            
            int rank = 1;
            
            for (Employee emp : admins) {
                String role = emp.hasRole(Role.ADMIN) ? "Administrador" : "Gerente";
                String empName = emp.getFullName();
                BigDecimal sales = getEmployeeSales(salesByEmployee, empName);
                
                // Count orders where this admin/manager collected payment (paidBy)
                long ordersCobradas = paidOrders.stream()
                    .filter(o -> o.getPaidBy() != null && o.getPaidBy().getIdEmpleado().equals(emp.getIdEmpleado()))
                    .count();
                
                // Count orders created by this admin/manager
                long ordersCreadas = paidOrders.stream()
                    .filter(o -> o.getEmployee() != null && o.getEmployee().getIdEmpleado().equals(emp.getIdEmpleado()))
                    .count();
                
                addTableRow(adminsTable, regularFont,
                    String.valueOf(rank++),
                    empName,
                    role,
                    String.valueOf(ordersCobradas),
                    String.valueOf(ordersCreadas),
                    String.format("$%,.2f", sales)
                );
            }
            document.add(adminsTable);
            document.add(new Paragraph("\n"));
        }

        // Footer
        addFooter(document, regularFont);

        document.close();
        return baos.toByteArray();
    }

    /**
     * Generate Clients Report (Customer activity and order history)
     * Shows all registered customers with their order statistics
     */
    public byte[] generateClientsReport(
            java.util.List<Order> customerOrders,
            java.util.List<Customer> allCustomers,
            String startDate,
            String endDate) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, PageSize.LETTER);
        document.setMargins(40, 40, 40, 40);

        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        // Header
        addHeader(document, boldFont, regularFont, "REPORTE DE CLIENTES");
        addDateRange(document, regularFont, startDate, endDate);
        document.add(new Paragraph("\n"));

        // ========== RESUMEN GENERAL ==========
        long totalCustomers = allCustomers.size();
        long activeCustomers = allCustomers.stream().filter(Customer::isActive).count();
        long inactiveCustomers = totalCustomers - activeCustomers;

        BigDecimal totalCustomerRevenue = customerOrders.stream()
            .map(o -> o.getTotal() != null ? o.getTotal() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalCustomerOrdersCount = customerOrders.size();

        BigDecimal avgTicket = totalCustomerOrdersCount > 0
            ? totalCustomerRevenue.divide(BigDecimal.valueOf(totalCustomerOrdersCount), 2, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        addSectionTitle(document, boldFont, "Resumen General");

        Table summaryTable = new Table(new float[]{1, 1, 1});
        summaryTable.setWidth(UnitValue.createPercentValue(100));

        addSummaryCell(summaryTable, boldFont, regularFont, "Clientes Registrados",
            String.valueOf(totalCustomers));
        addSummaryCell(summaryTable, boldFont, regularFont, "Pedidos de Clientes",
            String.valueOf(totalCustomerOrdersCount));
        addSummaryCell(summaryTable, boldFont, regularFont, "Ingresos de Clientes",
            String.format("$%,.2f", totalCustomerRevenue));

        document.add(summaryTable);
        document.add(new Paragraph("\n"));

        Table summaryTable2 = new Table(new float[]{1, 1, 1});
        summaryTable2.setWidth(UnitValue.createPercentValue(100));

        addSummaryCell(summaryTable2, boldFont, regularFont, "Clientes Activos",
            String.valueOf(activeCustomers));
        addSummaryCell(summaryTable2, boldFont, regularFont, "Clientes Inactivos",
            String.valueOf(inactiveCustomers));
        addSummaryCell(summaryTable2, boldFont, regularFont, "Ticket Promedio",
            String.format("$%,.2f", avgTicket));

        document.add(summaryTable2);
        document.add(new Paragraph("\n"));

        // ========== TOP CLIENTES POR GASTO ==========
        addSectionTitle(document, boldFont, "\ud83c\udfc6 Ranking de Clientes por Consumo");

        // Group orders by customer and calculate stats
        Map<Long, java.util.List<Order>> ordersByCustomer = customerOrders.stream()
            .filter(o -> o.getCustomer() != null)
            .collect(Collectors.groupingBy(o -> o.getCustomer().getIdCustomer()));

        // Build customer stats list
        java.util.List<Map<String, Object>> customerStats = new ArrayList<>();
        for (Customer customer : allCustomers) {
            Map<String, Object> stats = new HashMap<>();
            stats.put("customer", customer);

            java.util.List<Order> orders = ordersByCustomer.getOrDefault(customer.getIdCustomer(), new ArrayList<>());
            BigDecimal totalSpent = orders.stream()
                .map(o -> o.getTotal() != null ? o.getTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            long orderCount = orders.size();
            BigDecimal customerAvgTicket = orderCount > 0
                ? totalSpent.divide(BigDecimal.valueOf(orderCount), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            // Preferred order type
            String preferredOrderType = orders.stream()
                .collect(Collectors.groupingBy(Order::getOrderType, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey().getDisplayName())
                .orElse("Sin pedidos");

            // Preferred payment method
            String preferredPayment = orders.stream()
                .collect(Collectors.groupingBy(Order::getPaymentMethod, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey().getDisplayName())
                .orElse("Sin pedidos");

            stats.put("totalSpent", totalSpent);
            stats.put("orderCount", orderCount);
            stats.put("avgTicket", customerAvgTicket);
            stats.put("preferredOrderType", preferredOrderType);
            stats.put("preferredPayment", preferredPayment);

            customerStats.add(stats);
        }

        // Sort by total spent descending
        customerStats.sort((a, b) -> ((BigDecimal) b.get("totalSpent")).compareTo((BigDecimal) a.get("totalSpent")));

        Table topTable = new Table(new float[]{0.4f, 2.5f, 1.2f, 1, 1.2f, 1.5f, 1.5f});
        topTable.setWidth(UnitValue.createPercentValue(100));
        addTableHeader(topTable, boldFont, "#", "Cliente", "Total Gastado", "Pedidos", "Ticket Prom.", "Tipo Preferido", "M\u00e9todo Pago");

        int rank = 1;
        for (Map<String, Object> stats : customerStats) {
            Customer c = (Customer) stats.get("customer");
            addTableRow(topTable, regularFont,
                String.valueOf(rank++),
                c.getFullName(),
                String.format("$%,.2f", stats.get("totalSpent")),
                String.valueOf(stats.get("orderCount")),
                String.format("$%,.2f", stats.get("avgTicket")),
                (String) stats.get("preferredOrderType"),
                (String) stats.get("preferredPayment")
            );
        }
        document.add(topTable);
        document.add(new Paragraph("\n"));

        // ========== DISTRIBUCION POR TIPO DE ORDEN ==========
        addSectionTitle(document, boldFont, "\ud83d\udce6 Distribuci\u00f3n por Tipo de Orden");

        Map<OrderType, Long> ordersByType = customerOrders.stream()
            .collect(Collectors.groupingBy(Order::getOrderType, Collectors.counting()));

        Map<OrderType, BigDecimal> revenueByType = customerOrders.stream()
            .collect(Collectors.groupingBy(Order::getOrderType,
                Collectors.reducing(BigDecimal.ZERO,
                    o -> o.getTotal() != null ? o.getTotal() : BigDecimal.ZERO,
                    BigDecimal::add)));

        Table typeTable = new Table(new float[]{2, 1.5f, 2, 1.5f});
        typeTable.setWidth(UnitValue.createPercentValue(100));
        addTableHeader(typeTable, boldFont, "Tipo de Orden", "Cantidad", "Ingresos", "% del Total");

        for (OrderType type : OrderType.values()) {
            long count = ordersByType.getOrDefault(type, 0L);
            BigDecimal revenue = revenueByType.getOrDefault(type, BigDecimal.ZERO);
            String percentage = totalCustomerOrdersCount > 0
                ? String.format("%.1f%%", (double) count / totalCustomerOrdersCount * 100)
                : "0.0%";

            addTableRow(typeTable, regularFont,
                type.getDisplayName(),
                String.valueOf(count),
                String.format("$%,.2f", revenue),
                percentage
            );
        }
        document.add(typeTable);
        document.add(new Paragraph("\n"));

        // ========== DISTRIBUCION POR METODO DE PAGO ==========
        addSectionTitle(document, boldFont, "\ud83d\udcb3 M\u00e9todos de Pago Preferidos");

        Map<PaymentMethodType, Long> ordersByPayment = customerOrders.stream()
            .collect(Collectors.groupingBy(Order::getPaymentMethod, Collectors.counting()));

        Map<PaymentMethodType, BigDecimal> revenueByPayment = customerOrders.stream()
            .collect(Collectors.groupingBy(Order::getPaymentMethod,
                Collectors.reducing(BigDecimal.ZERO,
                    o -> o.getTotal() != null ? o.getTotal() : BigDecimal.ZERO,
                    BigDecimal::add)));

        Table paymentTable = new Table(new float[]{2, 1.5f, 2, 1.5f});
        paymentTable.setWidth(UnitValue.createPercentValue(100));
        addTableHeader(paymentTable, boldFont, "M\u00e9todo de Pago", "Cantidad", "Ingresos", "% del Total");

        for (PaymentMethodType pmt : PaymentMethodType.values()) {
            long count = ordersByPayment.getOrDefault(pmt, 0L);
            BigDecimal revenue = revenueByPayment.getOrDefault(pmt, BigDecimal.ZERO);
            String percentage = totalCustomerOrdersCount > 0
                ? String.format("%.1f%%", (double) count / totalCustomerOrdersCount * 100)
                : "0.0%";

            addTableRow(paymentTable, regularFont,
                pmt.getDisplayName(),
                String.valueOf(count),
                String.format("$%,.2f", revenue),
                percentage
            );
        }
        document.add(paymentTable);
        document.add(new Paragraph("\n"));

        // ========== DIRECTORIO DE CLIENTES ==========
        // Sección comentada - no se muestra en el reporte
        /*
        addSectionTitle(document, boldFont, "\ud83d\udccb Directorio de Clientes");

        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        Table directoryTable = new Table(new float[]{0.4f, 2f, 2f, 1.5f, 1.5f, 1f, 1.2f});
        directoryTable.setWidth(UnitValue.createPercentValue(100));
        addTableHeader(directoryTable, boldFont, "#", "Nombre", "Correo", "Tel\u00e9fono", "Registro", "Estado", "\u00daltimo Acceso");

        int dirRank = 1;
        // Sort by creation date descending (newest first)
        java.util.List<Customer> sortedCustomers = new ArrayList<>(allCustomers);
        sortedCustomers.sort((a, b) -> {
            LocalDateTime dateA = a.getCreatedAt() != null ? a.getCreatedAt() : LocalDateTime.MIN;
            LocalDateTime dateB = b.getCreatedAt() != null ? b.getCreatedAt() : LocalDateTime.MIN;
            return dateB.compareTo(dateA);
        });

        for (Customer c : sortedCustomers) {
            String registrationDate = c.getCreatedAt() != null ? c.getCreatedAt().format(dtf) : "N/A";
            String lastAccessDate = c.getLastAccess() != null ? c.getLastAccess().format(dtf) : "Nunca";
            String status = c.isActive() ? "Activo" : "Inactivo";

            addTableRow(directoryTable, regularFont,
                String.valueOf(dirRank++),
                c.getFullName(),
                c.getEmail(),
                c.getPhone(),
                registrationDate,
                status,
                lastAccessDate
            );
        }
        document.add(directoryTable);
        document.add(new Paragraph("\n"));
        */

        // Footer
        addFooter(document, regularFont);

        document.close();
        return baos.toByteArray();
    }

    // ========== Helper Methods ==========

    /**
     * Get employee sales from salesByEmployee list by name
     */
    private BigDecimal getEmployeeSales(java.util.List<Map<String, Object>> salesByEmployee, String employeeName) {
        return salesByEmployee.stream()
            .filter(emp -> employeeName.equals(emp.get("name")))
            .map(emp -> (BigDecimal) emp.get("total"))
            .findFirst()
            .orElse(BigDecimal.ZERO);
    }

    private void addHeader(Document document, PdfFont boldFont, PdfFont regularFont, String title) {
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        
        // Restaurant name with modern styling
        Paragraph restaurantName = new Paragraph(config.getRestaurantName())
            .setFont(boldFont)
            .setFontSize(24)
            .setFontColor(PRIMARY_COLOR)
            .setTextAlignment(TextAlignment.CENTER)
            .setBold()
            .setMarginBottom(2);
        document.add(restaurantName);

        // Subtitle line
        Paragraph subtitle = new Paragraph("Sistema de Reportes")
            .setFont(regularFont)
            .setFontSize(9)
            .setFontColor(GRAY_COLOR)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(15);
        document.add(subtitle);

        // Report title with background
        Table titleTable = new Table(1);
        titleTable.setWidth(UnitValue.createPercentValue(100));
        
        Cell titleCell = new Cell()
            .add(new Paragraph(title)
                .setFont(boldFont)
                .setFontSize(16)
                .setFontColor(WHITE)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER))
            .setBackgroundColor(PRIMARY_COLOR)
            .setPadding(12)
            .setBorder(Border.NO_BORDER)
            .setMarginBottom(5);
        
        titleTable.addCell(titleCell);
        document.add(titleTable);
    }

    private void addDateRange(Document document, PdfFont font, String startDate, String endDate) {
        String dateRange;
        if (startDate != null && !startDate.isEmpty()) {
            dateRange = "📅 Periodo: " + startDate + " al " + (endDate != null && !endDate.isEmpty() ? endDate : startDate);
        } else {
            dateRange = "📅 Periodo: Todos los registros";
        }
        
        // Date range in a subtle box
        Table dateTable = new Table(1);
        dateTable.setWidth(UnitValue.createPercentValue(100));
        
        Cell dateCell = new Cell()
            .add(new Paragraph(dateRange)
                .setFont(font)
                .setFontSize(10)
                .setFontColor(GRAY_COLOR)
                .setTextAlignment(TextAlignment.CENTER))
            .setBackgroundColor(LIGHT_GRAY)
            .setPadding(8)
            .setBorder(Border.NO_BORDER)
            .setMarginBottom(15);
        
        dateTable.addCell(dateCell);
        document.add(dateTable);
    }

    private void addSectionTitle(Document document, PdfFont boldFont, String title) {
        // Section title with left border accent
        Table sectionTable = new Table(new float[]{0.05f, 0.95f});
        sectionTable.setWidth(UnitValue.createPercentValue(100));
        
        // Accent bar
        Cell accentCell = new Cell()
            .setBackgroundColor(PRIMARY_COLOR)
            .setBorder(Border.NO_BORDER)
            .setHeight(20);
        
        // Title text
        Cell titleCell = new Cell()
            .add(new Paragraph(title)
                .setFont(boldFont)
                .setFontSize(13)
                .setFontColor(DARK_COLOR)
                .setBold())
            .setVerticalAlignment(VerticalAlignment.MIDDLE)
            .setBorder(Border.NO_BORDER)
            .setPaddingLeft(10);
        
        sectionTable.addCell(accentCell);
        sectionTable.addCell(titleCell);
        
        document.add(sectionTable.setMarginTop(10).setMarginBottom(10));
    }

    private void addSummaryCell(Table table, PdfFont boldFont, PdfFont regularFont, String label, String value) {
        Cell cell = new Cell()
            .setBorder(Border.NO_BORDER)
            .setBackgroundColor(LIGHT_GRAY)
            .setPadding(15)
            .setMarginRight(5);
        
        // Label
        cell.add(new Paragraph(label)
            .setFont(regularFont)
            .setFontSize(9)
            .setFontColor(GRAY_COLOR)
            .setMarginBottom(8)
            .setTextAlignment(TextAlignment.CENTER));
        
        // Value
        cell.add(new Paragraph(value)
            .setFont(boldFont)
            .setFontSize(18)
            .setFontColor(PRIMARY_DARK)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER));
        
        table.addCell(cell);
    }

    private void addTableHeader(Table table, PdfFont boldFont, String... headers) {
        for (String header : headers) {
            Cell cell = new Cell()
                .add(new Paragraph(header)
                    .setFont(boldFont)
                    .setFontSize(9)
                    .setBold())
                .setBackgroundColor(PRIMARY_COLOR)
                .setFontColor(WHITE)
                .setPadding(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER);
            table.addHeaderCell(cell);
        }
    }

    private void addTableRow(Table table, PdfFont font, String... values) {
        for (int i = 0; i < values.length; i++) {
            Cell cell = new Cell()
                .add(new Paragraph(values[i])
                    .setFont(font)
                    .setFontSize(9))
                .setPadding(8)
                .setBackgroundColor(i % 2 == 0 ? WHITE : LIGHT_GRAY)
                .setBorder(new SolidBorder(new DeviceRgb(229, 231, 235), 0.5f));
            
            // Align numbers to the right
            if (values[i].contains("$") || values[i].contains("%") || values[i].matches("\\d+")) {
                cell.setTextAlignment(TextAlignment.RIGHT);
            } else if (i == 0 && values[i].matches("\\d+")) {
                // Row number centered
                cell.setTextAlignment(TextAlignment.CENTER);
            }
            
            table.addCell(cell);
        }
    }

    private void addFooter(Document document, PdfFont font) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm:ss");
        String generatedDate = dateTimeService.nowLocal().format(formatter);
        
        // Footer with modern design
        Table footerTable = new Table(1);
        footerTable.setWidth(UnitValue.createPercentValue(100));
        
        Cell footerCell = new Cell()
            .add(new Paragraph("📄 Reporte generado el " + generatedDate)
                .setFont(font)
                .setFontSize(8)
                .setFontColor(GRAY_COLOR)
                .setTextAlignment(TextAlignment.CENTER))
            .setBackgroundColor(LIGHT_GRAY)
            .setPadding(10)
            .setBorder(Border.NO_BORDER)
            .setMarginTop(20);
        
        footerTable.addCell(footerCell);
        document.add(footerTable);
    }
}
