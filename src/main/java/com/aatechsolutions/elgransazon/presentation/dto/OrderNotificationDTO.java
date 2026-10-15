package com.aatechsolutions.elgransazon.presentation.dto;

import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for WebSocket order notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderNotificationDTO {
    private Long orderId;
    private String orderNumber;
    private OrderStatus status;
    private OrderType orderType;

    /**
     * El tipo de pedido ya rotulado como lo muestra el sistema ("Para comer aquí", "Para pasar a
     * recoger", "Entrega a domicilio").
     *
     * <p>Viaja rotulado para que las pantallas que avisan NO tengan que repetir la traducción: los
     * rótulos de negocio viven en {@code OrderType} y desde aquí se leen tal cual. Un pedido sin
     * mesa se anuncia por su tipo, que es lo único que lo identifica en el salón.</p>
     */
    private String orderTypeLabel;

    private Integer tableNumber;
    private BigDecimal total;
    private LocalDateTime createdAt;
    private Integer itemCount;
    private List<OrderItemDTO> items;
    private String notificationType; // "NEW_ORDER", "STATUS_CHANGE", "CHEF_ASSIGNED", "ORDER_DETAIL"
    private String message;
    private String chefName;

    /**
     * Usuario que capturó el pedido. Nunca se reescribe, ni con un traspaso: es la bitácora de
     * quién lo levantó.
     *
     * <p>Es lo que cada pantalla compara con su propio usuario para decidir si un aviso es suyo
     * (por ejemplo el de "pedido listo"), sin necesidad de una cola por usuario ni de un segundo
     * canal.</p>
     */
    private String createdBy;

    /**
     * Usuario responsable AHORA del pedido (el mesero al que se transfirió, si hubo traspaso), o
     * {@code null} si nadie lo tiene asignado.
     *
     * <p>Espeja {@code Order.isOperatedBy}: cuando viene null, el responsable es
     * {@link #createdBy}. Así quien atiende un pedido traspasado recibe sus avisos y quien lo
     * levantó y ya no lo atiende no los recibe.</p>
     */
    private String operatedBy;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDTO {
        /** OrderDetail id, so a client can map an item back to its own row. */
        private Long idOrderDetail;
        private String name;
        private Integer quantity;
        private Boolean requiresPreparation;
        /**
         * Per-item status (PENDING, IN_PREPARATION, READY, DELIVERED, TO_ACCEPT,
         * CANCELLED). The order detail views use it to refresh each item badge in
         * place when a notification arrives, without reloading the page.
         */
        private OrderStatus itemStatus;
    }
}
