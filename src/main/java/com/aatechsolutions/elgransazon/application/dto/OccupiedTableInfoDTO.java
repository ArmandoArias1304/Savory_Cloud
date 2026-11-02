package com.aatechsolutions.elgransazon.application.dto;

import java.math.BigDecimal;

/**
 * Resumen de la ocupación de una mesa que se muestra cuando otro empleado intenta
 * crear un pedido sobre ella.
 *
 * <p>No expone datos sensibles del cliente: solo el nombre y el total que ya son
 * visibles para el personal del restaurante. La URL del pedido llega en null cuando
 * el rol actual no tiene permiso para consultarlo (por ejemplo, un mesero mirando
 * la mesa de un compañero).</p>
 */
public record OccupiedTableInfoDTO(
        Long orderId,
        String orderNumber,
        String waiterName,
        String waiterInitials,
        String customerName,
        String openedAt,
        long occupiedMinutes,
        String status,
        String statusDisplay,
        int itemCount,
        BigDecimal total,
        boolean canViewOrder,
        String orderUrl
) {
}
