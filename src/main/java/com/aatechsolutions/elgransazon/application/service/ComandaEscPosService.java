package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates ESC/POS comanda tickets for thermal printers.
 *
 * A comanda only contains items relevant to a specific role (KITCHEN / BAR / PARRILLERO).
 * Items are sorted: PENDING → IN_PREPARATION → READY so the chef knows what still needs work.
 *
 * Uses preparationTypeSnapshot (frozen at order creation) as the authoritative filter
 * so that later changes to ItemMenu configuration don't affect historical printing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComandaEscPosService {

    private final SystemConfigurationService systemConfigurationService;
    private final DateTimeService dateTimeService;

    private static final int    LINE_WIDTH  = 32;
    private static final Charset CP1252     = Charset.forName("Cp1252");

    // ── ESC/POS command bytes ──
    private static final byte[] INIT          = {0x1B, 0x40};
    private static final byte[] ALIGN_CENTER  = {0x1B, 0x61, 0x01};
    private static final byte[] ALIGN_LEFT    = {0x1B, 0x61, 0x00};
    private static final byte[] BOLD_ON       = {0x1B, 0x45, 0x01};
    private static final byte[] BOLD_OFF      = {0x1B, 0x45, 0x00};
    private static final byte[] DOUBLE_HEIGHT = {0x1B, 0x21, 0x10};
    private static final byte[] NORMAL_SIZE   = {0x1B, 0x21, 0x00};
    private static final byte[] FONT_B        = {0x1B, 0x4D, 0x01};
    private static final byte[] FONT_A        = {0x1B, 0x4D, 0x00};
    private static final byte[] FEED_CUT      = {0x1D, 0x56, 0x00};
    private static final byte[] SET_CP1252    = {0x1B, 0x74, 0x10};
    private static final byte   LF            = 0x0A;

    // ── Status sort order: lower = printed first ──
    private static int statusOrder(OrderStatus status) {
        if (status == null) return 0;
        return switch (status) {
            case PENDING        -> 0;
            case TO_ACCEPT      -> 1;
            case IN_PREPARATION -> 2;
            case READY          -> 3;
            default             -> 4;
        };
    }

    /**
     * Returns true when the given OrderDetail should appear in the comanda for printerType.
     *
     * Rules:
     * - Combo parents (isComboParentSnapshot=true or snapshot="COMBO") are skipped.
     *   Their children carry their own snapshot and appear individually.
     * - preparationTypeSnapshot is used when present (frozen at creation time).
     * - Falls back to live ItemMenu flags for legacy rows (null snapshot).
     */
    public boolean matchesType(OrderDetail detail, PrinterType printerType) {
        // Skip combo parents – they are just grouping headers, not real work items
        if (Boolean.TRUE.equals(detail.getIsComboParentSnapshot())) return false;
        String snap = detail.getPreparationTypeSnapshot();
        if ("COMBO".equals(snap)) return false;

        if (snap != null) {
            return printerType.getPreparationTypeSnapshot().equals(snap);
        }

        // Legacy fallback: use live ItemMenu flags
        if (detail.getItemMenu() == null) return false;
        return switch (printerType) {
            case KITCHEN    -> Boolean.TRUE.equals(detail.getItemMenu().getRequiresPreparation());
            case BAR        -> Boolean.TRUE.equals(detail.getItemMenu().getRequiresBaristaPreparation());
            case PARRILLERO -> Boolean.TRUE.equals(detail.getItemMenu().getRequiresParrilleroPreparation());
        };
    }

    /**
     * Generate ESC/POS bytes for a comanda of the given type.
     * Returns an empty array if the order has no items for that type.
     */
    public byte[] generateComanda(Order order, PrinterType printerType) throws IOException {
        List<OrderDetail> items = order.getOrderDetails() == null ? List.of() :
            order.getOrderDetails().stream()
                .filter(d -> matchesType(d, printerType))
                .sorted(Comparator.comparingInt(d -> statusOrder(d.getItemStatus())))
                .collect(Collectors.toList());

        if (items.isEmpty()) {
            log.debug("No items for {} comanda in order {}", printerType, order.getOrderNumber());
            return new byte[0];
        }

        SystemConfiguration config = systemConfigurationService.getConfiguration();
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);

        out.write(INIT);
        out.write(SET_CP1252);

        // ── Header ──
        out.write(ALIGN_CENTER);
        out.write(BOLD_ON);
        out.write(DOUBLE_HEIGHT);
        printLine(out, "COMANDA");
        out.write(NORMAL_SIZE);
        out.write(BOLD_ON);
        printLine(out, printerType.getDisplayName().toUpperCase());
        out.write(BOLD_OFF);
        if (config != null && config.getRestaurantName() != null) {
            out.write(FONT_B);
            printLine(out, truncate(config.getRestaurantName(), LINE_WIDTH));
            out.write(FONT_A);
        }
        printSeparator(out);

        // ── Order info (left-aligned, Font B) ──
        out.write(ALIGN_LEFT);
        out.write(FONT_B);
        out.write(BOLD_ON);
        printLine(out, "PEDIDO: #" + order.getOrderNumber());
        out.write(BOLD_OFF);

        if (order.getTable() != null) {
            printLine(out, "Mesa:   #" + order.getTable().getTableNumber());
        }
        if (order.getOrderType() != null) {
            printLine(out, "Tipo:   " + order.getOrderType().getDisplayName());
        }
        if (order.getEmployee() != null) {
            String nombre = order.getEmployee().getNombre();
            String apellido = order.getEmployee().getApellido();
            printLine(out, "Mesero: " + truncate(nombre + (apellido != null ? " " + apellido : ""), 22));
        }
        String customerName = order.getCustomerName();
        if (customerName != null && !customerName.isBlank()) {
            printLine(out, "Cliente:" + truncate(customerName, 22));
        }
        String dateTime = dateTimeService.formatToCompanyTime(order.getCreatedAt(), "dd/MM/yyyy HH:mm");
        printLine(out, "Hora:   " + dateTime);

        out.write(FONT_A);
        printSeparator(out);

        // ── Items grouped by status ──
        OrderStatus currentGroup = null;
        for (OrderDetail detail : items) {
            OrderStatus itemStatus = detail.getItemStatus();

            // Print status group header when it changes
            if (itemStatus != currentGroup) {
                currentGroup = itemStatus;
                out.write(BOLD_ON);
                out.write(FONT_B);
                printLine(out, "[ " + statusLabel(itemStatus) + " ]");
                out.write(BOLD_OFF);
                out.write(FONT_A);
            }

            out.write(FONT_B);

            // Build item label
            String itemName = detail.getDisplayName();
            boolean isComboChild = Boolean.FALSE.equals(detail.getIsComboParentSnapshot())
                && detail.getComboGroupId() != null;
            String prefix = isComboChild ? " +(C) " : "  ";
            printLine(out, prefix + detail.getQuantity() + "x " + truncate(itemName, 24));

            // Special instructions / comments
            String comments = detail.getDisplayComments();
            if (comments != null && !comments.isBlank()) {
                printLine(out, "      -> " + truncate(comments, 21));
            }

            // Complements
            if (detail.getSelectedComplements() != null) {
                for (OrderDetailComplement odc : detail.getSelectedComplements()) {
                    printLine(out, "      + " + truncate(odc.getComplementName(), 18)
                        + " x" + odc.getQuantity());
                }
            }

            out.write(FONT_A);
        }

        printSeparator(out);

        // Extra line feeds before cut
        out.write(new byte[]{LF, LF, LF});
        out.write(FEED_CUT);

        return out.toByteArray();
    }

    /**
     * Short ESC/POS test page for the given station, using the current company's restaurant name and timezone.
     */
    public byte[] generateTestPage(PrinterType printerType) throws IOException {
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        ByteArrayOutputStream out = new ByteArrayOutputStream(512);

        out.write(INIT);
        out.write(SET_CP1252);
        out.write(ALIGN_CENTER);
        out.write(BOLD_ON);
        out.write(DOUBLE_HEIGHT);
        printLine(out, "PRUEBA");
        out.write(NORMAL_SIZE);
        printLine(out, printerType.getDisplayName().toUpperCase());
        out.write(BOLD_OFF);
        if (config != null && config.getRestaurantName() != null) {
            out.write(FONT_B);
            printLine(out, truncate(config.getRestaurantName(), LINE_WIDTH));
            out.write(FONT_A);
        }
        printSeparator(out);
        out.write(ALIGN_LEFT);
        out.write(FONT_B);
        String now = dateTimeService.nowLocal().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        printLine(out, "Hora:   " + now);
        printLine(out, "Si lees esto, la impresora");
        printLine(out, "funciona correctamente.");
        out.write(FONT_A);
        out.write(new byte[]{LF, LF, LF});
        out.write(FEED_CUT);
        return out.toByteArray();
    }

    // ── helpers ──

    private String statusLabel(OrderStatus status) {
        if (status == null) return "PENDIENTE";
        return switch (status) {
            case PENDING        -> "PENDIENTE";
            case TO_ACCEPT      -> "POR ACEPTAR";
            case IN_PREPARATION -> "EN PREPARACION";
            case READY          -> "LISTO";
            default             -> status.name();
        };
    }

    private void printLine(ByteArrayOutputStream out, String text) throws IOException {
        if (text == null) text = "";
        out.write(text.getBytes(CP1252));
        out.write(LF);
    }

    private void printSeparator(ByteArrayOutputStream out) throws IOException {
        printLine(out, "-".repeat(LINE_WIDTH));
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 1) + "." : s;
    }
}
