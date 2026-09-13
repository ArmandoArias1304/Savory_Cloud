package com.aatechsolutions.elgransazon.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket payload sent to printer-agent subscribers when a comanda should be printed.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PrintComandaNotificationDTO {

    /** DB id of the order whose comanda must be printed */
    private Long orderId;

    /** Human-readable order number for display/logging */
    private String orderNumber;

    /** Company id — used to scope WebSocket topics (multi-tenant) */
    private Long companyId;

    /**
     * Which printer type should print this comanda.
     * Values: "KITCHEN", "BAR", "PARRILLERO"  (matches PrinterType enum names)
     */
    private String printerType;
}
