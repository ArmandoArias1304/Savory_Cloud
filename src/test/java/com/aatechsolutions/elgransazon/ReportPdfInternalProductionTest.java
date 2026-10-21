package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.ReportPdfService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.ItemPreparation;
import com.aatechsolutions.elgransazon.domain.entity.PreparationStatus;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Internal production (buffet dishes, staff meals) never sells, so it must never move a sales
 * figure. The PDFs may still say how much was cooked: what is checked here is exactly that
 * boundary — the portions appear, the money of the sold products does not change.
 *
 * <p>The real iText PDF is generated and its text read back, so what is checked is what the
 * client gets on the printed page and not an intermediate structure.</p>
 */
class ReportPdfInternalProductionTest {

    private ReportPdfService reportPdfService;

    @BeforeEach
    void setUp() {
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(systemConfigurationService.getConfiguration()).thenReturn(
                SystemConfiguration.builder().restaurantName("El Gran Sazón").build());

        DateTimeService dateTimeService = mock(DateTimeService.class);
        when(dateTimeService.nowLocal()).thenReturn(LocalDateTime.of(2026, 10, 1, 20, 30));

        reportPdfService = new ReportPdfService(
                systemConfigurationService, mock(EmployeeRepository.class), dateTimeService);
    }

    private ItemMenu dish(long id, String name, String categoryName) {
        Category category = Category.builder().idCategory(1L).name(categoryName).build();
        return ItemMenu.builder().idItemMenu(id).name(name).category(category).build();
    }

    private ItemPreparation batch(ItemMenu dish, int quantity, PreparationStatus status) {
        return ItemPreparation.builder()
                .itemMenu(dish)
                .quantity(quantity)
                .status(status)
                .build();
    }

    /** Two vigentes batches of Pozole (40 + 15), one of Ensalada (25) and one cancelled. */
    private List<ItemPreparation> internalBatches() {
        ItemMenu pozole = dish(101L, "Pozole", "Buffet");
        ItemMenu ensalada = dish(102L, "Ensalada", "Buffet");
        ItemMenu flan = dish(103L, "Flan", "Postres");
        return List.of(
                batch(pozole, 40, PreparationStatus.PREPARED),
                batch(pozole, 15, PreparationStatus.PREPARED),
                batch(ensalada, 25, PreparationStatus.PREPARED),
                batch(flan, 30, PreparationStatus.CANCELLED));
    }

    /** Two sold hamburgers for $200.00, the only thing the sales figures may count. */
    private List<Map<String, Object>> soldItems() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "Hamburguesa");
        row.put("quantity", 2);
        row.put("category", "Platos Fuertes");
        row.put("total", new BigDecimal("200.00"));
        return List.of(row);
    }

    private String textOf(byte[] pdf) throws Exception {
        StringBuilder text = new StringBuilder();
        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                text.append(PdfTextExtractor.getTextFromPage(document.getPage(page))).append('\n');
            }
        }
        return text.toString();
    }

    @Test
    @DisplayName("El PDF de productos muestra las porciones internas y deja intactas las ventas")
    void productsReportShowsInternalProductionWithoutTouchingSales() throws Exception {
        byte[] pdf = reportPdfService.generateProductsReport(
                List.of(), "2026-10-01", "2026-10-01",
                soldItems(), List.of(), BigDecimal.ZERO, internalBatches());

        String text = textOf(pdf);
        String flat = text.replaceAll("\\s+", " ");

        assertTrue(text.contains("Producción Interna (platillos consumo propio o buffet)"),
                "el bloque debe titularse consumo propio o buffet, texto=" + flat);
        assertTrue(text.contains("Pozole") && text.contains("Ensalada"), "faltan los platillos internos");
        // iText writes the labels first and the values under them: 80 porciones, 2 platillos, 3 lotes.
        assertTrue(flat.contains("Porciones Producidas Platillos Diferentes Lotes Registrados 80 2 3"),
                "solo suman los lotes vigentes (40 + 15 + 25), en 3 lotes de 2 platillos, texto=" + flat);
        assertFalse(text.contains("Flan"),
                "un lote cancelado no se reporta como producido");

        // The sales block keeps counting only what was sold.
        // Labels first, then their values: 2 vendidos, 1 variedad, $200.00 de ingreso.
        assertTrue(flat.contains("Total Productos Vendidos Variedades Diferentes Ingresos Generados 2 1 $200.00"),
                "las porciones internas no pueden inflar los productos vendidos ni el ingreso, texto=" + flat);
        assertTrue(text.contains("100.00%"),
                "el porcentaje del producto vendido no se diluye con la producción interna");
    }

    @Test
    @DisplayName("El reporte ejecutivo resume la producción interna sin tocar el total de ventas")
    void executiveReportSummarizesInternalProduction() throws Exception {
        byte[] pdf = reportPdfService.generateExecutiveReport(
                List.of(), "2026-10-01", "2026-10-01",
                new BigDecimal("200.00"), 1L,
                Map.of(), List.of(), Map.of(), List.of(), List.of(),
                BigDecimal.ZERO, internalBatches());

        String flat = textOf(pdf).replaceAll("\\s+", " ");

        assertTrue(flat.contains("Producción Interna (platillos consumo propio o buffet)"),
                "el ejecutivo debe traer el bloque con el título nuevo, texto=" + flat);
        assertTrue(flat.contains("Porciones Producidas Platillos Diferentes Lotes Registrados 80 2 3"),
                "el ejecutivo debe decir cuántas porciones se hicieron, texto=" + flat);
        // The executive report also lists the dishes: the summary alone cannot say which ones.
        assertTrue(flat.contains("# Platillo Categoría Porciones Lotes"),
                "el ejecutivo debe traer la tabla de platillos, texto=" + flat);
        assertTrue(flat.contains("Pozole") && flat.contains("Ensalada"),
                "el ejecutivo debe nombrar los platillos internos, texto=" + flat);
        assertTrue(flat.contains("55") && flat.contains("25"),
                "el ejecutivo debe traer las porciones por platillo, texto=" + flat);
        // Ventas $200.00, una orden pagada, ticket promedio $200.00: nada de la producción interna.
        assertTrue(flat.contains("$200.00 1 $200.00"),
                "el total de ventas no puede incluir la producción interna, texto=" + flat);
        assertTrue(flat.contains("no forman parte de las ventas del periodo"),
                "el bloque debe aclarar que no es venta, texto=" + flat);
    }

    @Test
    @DisplayName("Sin producción interna el PDF no imprime la sección")
    void reportsWithoutInternalProductionPrintNoSection() throws Exception {
        byte[] products = reportPdfService.generateProductsReport(
                List.of(), "2026-10-01", "2026-10-01",
                soldItems(), List.of(), BigDecimal.ZERO, List.of());
        byte[] executive = reportPdfService.generateExecutiveReport(
                List.of(), "2026-10-01", "2026-10-01",
                new BigDecimal("200.00"), 1L,
                Map.of(), List.of(), Map.of(), List.of(), List.of(),
                BigDecimal.ZERO, null);

        assertFalse(textOf(products).contains("Producción Interna (platillos"),
                "sin lotes el reporte de productos no debe traer una sección vacía");
        assertFalse(textOf(executive).contains("Producción Interna (platillos"),
                "sin lotes el ejecutivo no debe traer una sección vacía");
    }
}
