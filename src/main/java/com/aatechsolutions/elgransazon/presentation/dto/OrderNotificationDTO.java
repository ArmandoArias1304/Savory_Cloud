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
    private String notificationType; // "NEW_ORDER", "STATUS_CHANGE", "CHEF_ASSIGNED"
    private String message;
    private String chefName;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDTO {
        private String name;
        private Integer quantity;
        private Boolean requiresPreparation;
    }
}
