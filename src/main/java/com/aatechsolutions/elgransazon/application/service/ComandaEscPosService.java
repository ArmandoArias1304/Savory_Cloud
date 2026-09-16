package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.OrderDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final OrderDetailRepository orderDetailRepository;

    private static final int    LINE_WIDTH  = 32;
    private static final Charset CP1252     = Charset.forName("Cp1252");

    /** Width of the left label column in the order info block ("Mesero:  " = 9) */
    private static final int    LABEL_WIDTH = 9;
    private static final String LABEL_INDENT = " ".repeat(LABEL_WIDTH);

    // ── ESC/POS command bytes ──
    private static final byte[] INIT          = {0x1B, 0x40};
    private static final byte[] ALIGN_CENTER  = {0x1B, 0x61, 0x01};
    private static final byte[] ALIGN_LEFT    = {0x1B, 0x61, 0x00};
    private static final byte[] BOLD_ON       = {0x1B, 0x45, 0x01};
    private static final byte[] BOLD_OFF      = {0x1B, 0x45, 0x00};
    private static final byte[] FONT_B        = {0x1B, 0x4D, 0x01};
    private static final byte[] FONT_A        = {0x1B, 0x4D, 0x00};
    private static final byte[] FEED_CUT      = {0x1D, 0x56, 0x00};
    private static final byte[] SET_CP1252    = {0x1B, 0x74, 0x10};
    private static final byte   LF            = 0x0A;

    // ── Character sizes, biggest first (GS ! n: width multiplier in bits 7-4, height in bits 3-0).
    //    A comanda is read standing up, several steps away from the pass, so every tier was
    //    raised one step keeping the same order on the paper. The paper is only 32 Font A
    //    columns wide, so a line may only grow in WIDTH while its text still fits (headerSize);
    //    growing in HEIGHT costs paper but never forces the printer to wrap mid-word. ──
    private static final byte[] SIZE_TITLE  = textSize(2, 3);  // COMANDA — top of the hierarchy
    private static final byte[] SIZE_HEADER = textSize(2, 2);  // station, NUEVO PEDIDO, PEDIDO
    private static final byte[] SIZE_STRONG = textSize(1, 2);  // items and order info
    private static final byte[] SIZE_NORMAL = textSize(1, 1);  // notes: comments, complements, restaurant name

    /**
     * GS ! n — character size: bits 7-4 hold the width multiplier minus one, bits 3-0 the
     * height multiplier minus one (1x..8x). This is the ESC/POS command that can express
     * sizes beyond double width/height, which {@code ESC !} cannot.
     */
    private static byte[] textSize(int widthMultiplier, int heightMultiplier) {
        int width = Math.min(8, Math.max(1, widthMultiplier)) - 1;
        int height = Math.min(8, Math.max(1, heightMultiplier)) - 1;
        return new byte[]{0x1D, 0x21, (byte) ((width << 4) | height)};
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
     * Every item of the order that belongs to the given station, sorted by status.
     * Ignores whether the item was already printed (that is checked by the callers).
     */
    public List<OrderDetail> stationItems(Order order, PrinterType printerType) {
        if (order == null || order.getOrderDetails() == null) {
            return List.of();
        }
        return order.getOrderDetails().stream()
                .filter(d -> matchesType(d, printerType))
                // Chronological order (what was entered first goes first). No status grouping:
                // the printed ticket no longer shows statuses, that lives in the KDS/tablet.
                .sorted(Comparator.comparing(OrderDetail::getIdOrderDetail,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    /**
     * Items of the order that still have to go out on a comanda for the given station:
     * they belong to the station, were never printed there and are not waiting for acceptance.
     *
     * A printed comanda must never repeat items that already went out on a previous ticket,
     * because paper has no live status: the previous ones are already being prepared.
     */
    public List<OrderDetail> pendingItems(Order order, PrinterType printerType) {
        return stationItems(order, printerType).stream()
                .filter(d -> !d.isComandaPrintedFor(printerType))
                .filter(d -> d.getItemStatus() != OrderStatus.TO_ACCEPT)
                .collect(Collectors.toList());
    }

    /**
     * Marks items as already printed for the station so they never appear again on a comanda.
     * The printer agent calls this AFTER the ticket was physically printed; if the print fails
     * nothing is marked, so the items are included again in the next comanda of that station.
     *
     * @param detailIds detail ids to mark (ignored when {@code all} is true)
     * @param all       true to mark every station item of the order (manual full reprint)
     * @return how many items were marked by this call
     */
    @Transactional
    public int markComandaPrinted(Long orderId, PrinterType printerType, List<Long> detailIds, boolean all) {
        if (orderId == null || printerType == null) {
            return 0;
        }

        List<OrderDetail> candidates;
        if (all) {
            candidates = orderDetailRepository.findByOrderId(orderId).stream()
                    .filter(d -> matchesType(d, printerType))
                    .collect(Collectors.toList());
        } else if (detailIds != null && !detailIds.isEmpty()) {
            candidates = orderDetailRepository.findAllById(detailIds).stream()
                    .filter(d -> d.getOrder() != null && orderId.equals(d.getOrder().getIdOrder()))
                    .filter(d -> matchesType(d, printerType))
                    .collect(Collectors.toList());
        } else {
            return 0;
        }

        List<OrderDetail> toSave = candidates.stream()
                .filter(d -> !d.isComandaPrintedFor(printerType))
                .collect(Collectors.toList());
        toSave.forEach(d -> d.markComandaPrintedFor(printerType));
        if (!toSave.isEmpty()) {
            orderDetailRepository.saveAll(toSave);
        }

        log.info("Comanda {} of order {} marked as printed — {} item(s) (all={})",
                printerType, orderId, toSave.size(), all);
        return toSave.size();
    }

    /**
     * Full comanda: every item of the station in the order (manual reprint behavior).
     */
    public byte[] generateComanda(Order order, PrinterType printerType) throws IOException {
        return generateComanda(order, printerType, stationItems(order, printerType), false);
    }

    /**
     * Generate ESC/POS bytes for a comanda rendered with exactly the given items.
     * Returns an empty array when there is nothing to print.
     *
     * @param items items to print (already filtered by the caller)
     * @param delta true for an "addition" comanda (only newly added items); it prints an extra
     *              header line telling the cook how many of the station items are new
     */
    public byte[] generateComanda(Order order, PrinterType printerType,
                                  List<OrderDetail> items, boolean delta) throws IOException {

        if (items == null || items.isEmpty()) {
            log.debug("No items for {} comanda in order {}", printerType, order.getOrderNumber());
            return new byte[0];
        }

        SystemConfiguration config = systemConfigurationService.getConfiguration();
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);

        out.write(INIT);
        out.write(SET_CP1252);

        // ── Header: biggest elements first so the ticket reads by importance ──
        out.write(ALIGN_CENTER);
        out.write(BOLD_ON);
        out.write(SIZE_TITLE);
        printLine(out, "COMANDA");
        out.write(headerSize(printerType.getDisplayName().toUpperCase()));
        printLine(out, printerType.getDisplayName().toUpperCase());
        if (delta) {
            // Addition ticket: carries only the items added after the last printed comanda
            out.write(headerSize("NUEVO PEDIDO"));
            printLine(out, "NUEVO PEDIDO");
        }
        out.write(SIZE_NORMAL);
        out.write(BOLD_OFF);
        if (config != null && config.getRestaurantName() != null) {
            printWrapped(out, "", "", config.getRestaurantName());
        }
        printSeparator(out);

        // ── Order number: the anchor of the ticket (its own line, as big as the paper allows) ──
        out.write(BOLD_ON);
        out.write(headerSize("PEDIDO"));
        printLine(out, "PEDIDO");
        // The number goes on its own line and without the "#": keeping both the prefix and the
        // hash pushed the line over the 16 columns that fit at double width, and the number is
        // what the station reads from a distance.
        out.write(headerSize(order.getOrderNumber()));
        printLine(out, order.getOrderNumber());
        out.write(SIZE_NORMAL);
        out.write(BOLD_OFF);
        printSeparator(out);

        // ── Order info: values aligned in a column, service data first ──
        out.write(ALIGN_LEFT);
        out.write(SIZE_STRONG);
        if (order.getTable() != null) {
            printWrapped(out, label("Mesa:"), LABEL_INDENT, "#" + order.getTable().getTableNumber());
        }
        if (order.getOrderType() != null) {
            printWrapped(out, label("Tipo:"), LABEL_INDENT, order.getOrderType().getDisplayName());
        }
        if (order.getEmployee() != null) {
            String nombre = order.getEmployee().getNombre();
            String apellido = order.getEmployee().getApellido();
            printWrapped(out, label("Mesero:"), LABEL_INDENT, nombre + (apellido != null ? " " + apellido : ""));
        }
        String customerName = order.getCustomerName();
        if (customerName != null && !customerName.isBlank()) {
            printWrapped(out, label("Cliente:"), LABEL_INDENT, customerName);
        }
        String dateTime = dateTimeService.formatToCompanyTime(order.getCreatedAt(), "dd/MM/yyyy HH:mm");
        printWrapped(out, label("Hora:"), LABEL_INDENT, dateTime);
        out.write(SIZE_NORMAL);
        printSeparator(out);

        // ── Items: full name and comments, wrapped so nothing gets cut ──
        // No status labels here: paper carries no live status, that lives in the KDS/tablet.
        //
        // Combo children share ONE comment for the whole combo (the comment is copied to every
        // child when the order is created), so it is printed a single time at the bottom of the
        // combo group instead of repeated under each child. A regular item keeps its own comment.
        int index = 0;
        while (index < items.size()) {
            OrderDetail detail = items.get(index);
            String groupId = detail.getComboGroupId();

            if (groupId == null || groupId.isBlank()) {
                printComandaItem(out, detail);
                printComment(out, detail.getDisplayComments());
                index++;
                continue;
            }

            // Children of the same combo are created together, so they are adjacent in the list
            java.util.List<OrderDetail> comboChildren = new java.util.ArrayList<>();
            while (index < items.size() && groupId.equals(items.get(index).getComboGroupId())) {
                comboChildren.add(items.get(index));
                index++;
            }

            for (OrderDetail child : comboChildren) {
                printComandaItem(out, child);
            }

            // Global comment of the combo, once and at the bottom of its group
            java.util.LinkedHashSet<String> comboComments = new java.util.LinkedHashSet<>();
            for (OrderDetail child : comboChildren) {
                String comboComment = child.getDisplayComments();
                if (comboComment != null && !comboComment.isBlank()) {
                    comboComments.add(comboComment);
                }
            }
            for (String comboComment : comboComments) {
                printComment(out, comboComment);
            }
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
        out.write(SIZE_HEADER);
        printLine(out, "PRUEBA");
        out.write(SIZE_STRONG);
        printLine(out, printerType.getDisplayName().toUpperCase());
        out.write(BOLD_OFF);
        out.write(SIZE_NORMAL);
        if (config != null && config.getRestaurantName() != null) {
            out.write(FONT_B);
            printWrapped(out, "", "", config.getRestaurantName());
            out.write(FONT_A);
        }
        printSeparator(out);
        out.write(ALIGN_LEFT);
        out.write(SIZE_STRONG);
        out.write(FONT_B);
        String now = dateTimeService.nowLocal().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        printLine(out, "Hora:   " + now);
        printLine(out, "Si lees esto, la impresora");
        printLine(out, "funciona correctamente.");
        out.write(FONT_A);
        out.write(SIZE_NORMAL);
        out.write(new byte[]{LF, LF, LF});
        out.write(FEED_CUT);
        return out.toByteArray();
    }

    // ── helpers ──

    /**
     * Prints one order item: bold name (wrapped, never cut) plus its own complements.
     * Comments are printed by the caller: a combo child's comment belongs to the whole combo
     * and goes once at the bottom of its group (see generateComanda).
     */
    private void printComandaItem(ByteArrayOutputStream out, OrderDetail detail) throws IOException {
        // Item name bold and double height so it can be read on the move at the pass; its
        // complements keep the same size, exactly as before (both were plain size before).
        out.write(BOLD_ON);
        out.write(SIZE_STRONG);

        boolean isComboChild = Boolean.FALSE.equals(detail.getIsComboParentSnapshot())
                && detail.getComboGroupId() != null;
        String prefix = (isComboChild ? " +(C) " : "  ") + detail.getQuantity() + "x ";
        printWrapped(out, prefix, "     ", detail.getDisplayName());
        out.write(BOLD_OFF);

        // Complements belong to this specific item, so they stay right below it
        if (detail.getSelectedComplements() != null) {
            for (OrderDetailComplement odc : detail.getSelectedComplements()) {
                printWrapped(out, "      + ", "        ",
                        odc.getComplementName() + " x" + odc.getQuantity());
            }
        }
        out.write(SIZE_NORMAL);
    }

    /**
     * Prints an item note (its own comment or the shared comment of a combo) at the same size
     * as the item it belongs to, so the item/comment hierarchy on paper does not change.
     */
    private void printComment(ByteArrayOutputStream out, String comment) throws IOException {
        if (comment == null || comment.isBlank()) {
            return;
        }
        out.write(SIZE_STRONG);
        printWrapped(out, "      -> ", "         ", comment);
        out.write(SIZE_NORMAL);
    }

    /**
     * Size for a centered header line: double width and height when the text still fits on
     * the paper, otherwise double height only. A 32-column printer wraps text on its own and
     * would cut the number in half, so width is only granted to lines that fit.
     */
    private byte[] headerSize(String text) {
        boolean fitsDoubleWidth = text != null && text.length() * 2 <= LINE_WIDTH;
        return fitsDoubleWidth ? SIZE_HEADER : SIZE_STRONG;
    }

    /**
     * Left-pads a label to the width of the info column, so all values line up.
     */
    private String label(String text) {
        StringBuilder sb = new StringBuilder(text == null ? "" : text);
        while (sb.length() < LABEL_WIDTH) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * Prints text wrapped over as many lines as needed, so long dish names or comments are
     * never cut. The first line uses {@code prefix} and the following ones {@code continuation};
     * a single word longer than a full line is split.
     */
    private void printWrapped(ByteArrayOutputStream out, String prefix, String continuation, String text)
            throws IOException {
        if (text == null) {
            return;
        }
        String remaining = text.trim();
        if (remaining.isEmpty()) {
            return;
        }

        String currentPrefix = prefix == null ? "" : prefix;
        String contIndent = continuation == null ? currentPrefix : continuation;
        int width = Math.max(10, LINE_WIDTH - currentPrefix.length());
        int contWidth = Math.max(10, LINE_WIDTH - contIndent.length());
        StringBuilder line = new StringBuilder();

        for (String word : remaining.split("\\s+")) {
            while (word.length() > width) {
                if (line.length() > 0) {
                    printLine(out, currentPrefix + line);
                    line.setLength(0);
                    currentPrefix = contIndent;
                    width = contWidth;
                }
                printLine(out, currentPrefix + word.substring(0, width));
                word = word.substring(width);
            }

            if (line.length() == 0) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= width) {
                line.append(' ').append(word);
            } else {
                printLine(out, currentPrefix + line);
                currentPrefix = contIndent;
                width = contWidth;
                line.setLength(0);
                line.append(word);
            }
        }

        if (line.length() > 0) {
            printLine(out, currentPrefix + line);
        }
    }

    private void printLine(ByteArrayOutputStream out, String text) throws IOException {
        if (text == null) text = "";
        out.write(text.getBytes(CP1252));
        out.write(LF);
    }

    private void printSeparator(ByteArrayOutputStream out) throws IOException {
        printLine(out, "-".repeat(LINE_WIDTH));
    }

}
