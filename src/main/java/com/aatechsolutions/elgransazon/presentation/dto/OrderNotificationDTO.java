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
    private Integer tableNumber;
    private BigDecimal total;
    private LocalDateTime createdAt;
    private Integer itemCount;
    private List<OrderItemDTO> items;
    private String notificationType; // "NEW_ORDER", "STATUS_CHANGE", "CHEF_ASSIGNED", "ORDER_DETAIL"
    private String message;
    private String chefName;
    
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
