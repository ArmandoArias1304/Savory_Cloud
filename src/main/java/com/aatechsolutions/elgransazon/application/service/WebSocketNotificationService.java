package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.presentation.dto.KitchenStatsDTO;
import com.aatechsolutions.elgransazon.presentation.dto.OrderNotificationDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Service for sending real-time WebSocket notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * MULTI-TENANT: Build a company-specific topic path
     * @param baseTopic Base topic like "/topic/chef/orders"
     * @param order The order to get company ID from
     * @return Topic with company ID appended, e.g., "/topic/chef/orders/1"
     */
    private String getCompanyTopic(String baseTopic, Order order) {
        if (order != null && order.getCompany() != null && order.getCompany().getIdCompany() != null) {
            return baseTopic + "/" + order.getCompany().getIdCompany();
        }
        // Fallback to base topic if no company (should not happen in production)
        log.warn("WebSocket: No company found for order {}, using base topic", 
            order != null ? order.getOrderNumber() : "null");
        return baseTopic;
    }

    /**
     * Notifies about a new order
     * Only notifies the roles (chef/barista) that have items to prepare
     */
    public void notifyNewOrder(Order order) {
        if (order.getOrderDetails() == null || order.getOrderDetails().isEmpty()) {
            log.warn("notifyNewOrder called with order without items: {}", order.getOrderNumber());
            return;
        }
        
        // Detect what type of items the order has
        boolean hasChefItems = order.getOrderDetails().stream()
            .anyMatch(detail -> detail.getItemMenu() != null && 
                Boolean.TRUE.equals(detail.getItemMenu().getRequiresPreparation()));
        
        boolean hasBaristaItems = order.getOrderDetails().stream()
            .anyMatch(detail -> detail.getItemMenu() != null && 
                Boolean.TRUE.equals(detail.getItemMenu().getRequiresBaristaPreparation()));
        
        OrderNotificationDTO notification = buildOrderNotification(order, "NEW_ORDER", 
            "Nuevo pedido #" + order.getOrderNumber());
        
        // Only notify CHEF if order has chef items
        if (hasChefItems) {
            messagingTemplate.convertAndSend(getCompanyTopic("/topic/chef/orders", order), notification);
            log.info("👨‍🍳 WebSocket: Notifying CHEF - New order {} with chef items", order.getOrderNumber());
        }
        
        // Only notify BARISTA if order has barista items
        if (hasBaristaItems) {
            messagingTemplate.convertAndSend(getCompanyTopic("/topic/barista/orders", order), notification);
            log.info("☕ WebSocket: Notifying BARISTA - New order {} with barista items", order.getOrderNumber());
        }
        
        // Always send to admin kitchen view
        messagingTemplate.convertAndSend(getCompanyTopic("/topic/admin/kitchen", order), notification);
        
        log.info("🔔 WebSocket: New order notification sent - {} - Chef: {}, Barista: {}", 
            order.getOrderNumber(), hasChefItems, hasBaristaItems);
    }

    /**
     * Notifies about order status change
     * Sends to both chef and barista channels so all roles can update their views
     * Also notifies DELIVERY when order becomes READY or changes status (ON_THE_WAY, DELIVERED)
     */
    public void notifyOrderStatusChange(Order order, String message) {
        notifyOrderStatusChange(order, message, null);
    }

    /**
     * Notifies about order status change
     * Only notifies the specific chef/barista assigned to the order
     * 
     * @param order The order that changed status
     * @param message The status change message
     * @param roleWhoChanged The role that triggered the change ("chef", "barista", or null for all)
     */
    public void notifyOrderStatusChange(Order order, String message, String roleWhoChanged) {
        OrderNotificationDTO notification = buildOrderNotification(order, "STATUS_CHANGE", message);
        
        // Skip chef/barista notifications for DELIVERED and PAID status changes
        // These transitions are handled by cashier/admin and don't concern kitchen staff
        boolean skipKitchenNotification = order.getStatus() == com.aatechsolutions.elgransazon.domain.entity.OrderStatus.DELIVERED
            || order.getStatus() == com.aatechsolutions.elgransazon.domain.entity.OrderStatus.PAID;
        
        // If roleWhoChanged is specified, only notify the ASSIGNED user of that role
        if (skipKitchenNotification) {
            log.debug("Skipping chef/barista notification for order {} - status is {}", 
                order.getOrderNumber(), order.getStatus());
        } else if (roleWhoChanged != null) {
            if ("chef".equalsIgnoreCase(roleWhoChanged)) {
                // Only send to the assigned chef, NOT to all chefs
                if (order.getPreparedBy() != null) {
                    messagingTemplate.convertAndSendToUser(
                        order.getPreparedBy().getUsername(),
                        "/queue/orders",
                        notification
                    );
                    log.debug("👨‍🍳 WebSocket: Notifying ONLY assigned chef {} - Order {} status changed", 
                        order.getPreparedBy().getUsername(), order.getOrderNumber());
                } else {
                    log.warn("⚠️ Chef role specified but no chef assigned to order {}", order.getOrderNumber());
                }
            } else if ("barista".equalsIgnoreCase(roleWhoChanged)) {
                // Only send to the assigned barista, NOT to all baristas
                if (order.getPreparedByBarista() != null) {
                    messagingTemplate.convertAndSendToUser(
                        order.getPreparedByBarista().getUsername(),
                        "/queue/orders",
                        notification
                    );
                    log.debug("☕ WebSocket: Notifying ONLY assigned barista {} - Order {} status changed", 
                        order.getPreparedByBarista().getUsername(), order.getOrderNumber());
                } else {
                    log.warn("⚠️ Barista role specified but no barista assigned to order {}", order.getOrderNumber());
                }
            }
        } else {
            // roleWhoChanged is null: check if order has assignments
            boolean hasAssignedChef = order.getPreparedBy() != null;
            boolean hasAssignedBarista = order.getPreparedByBarista() != null;
            
            // If order has assignments, only notify the assigned users
            if (hasAssignedChef || hasAssignedBarista) {
                if (hasAssignedChef) {
                    messagingTemplate.convertAndSendToUser(
                        order.getPreparedBy().getUsername(),
                        "/queue/orders",
                        notification
                    );
                    log.debug("👨‍🍳 WebSocket: Notifying assigned chef {} - Order {} status changed", 
                        order.getPreparedBy().getUsername(), order.getOrderNumber());
                }
                
                if (hasAssignedBarista) {
                    messagingTemplate.convertAndSendToUser(
                        order.getPreparedByBarista().getUsername(),
                        "/queue/orders",
                        notification
                    );
                    log.debug("☕ WebSocket: Notifying assigned barista {} - Order {} status changed", 
                        order.getPreparedByBarista().getUsername(), order.getOrderNumber());
                }
            } else {
                // No assignments: notify all roles that have items (broadcast for pending orders)
                boolean hasChefItems = order.getOrderDetails() != null && order.getOrderDetails().stream()
                    .anyMatch(detail -> detail.getItemMenu() != null && 
                        Boolean.TRUE.equals(detail.getItemMenu().getRequiresPreparation()));
                
                boolean hasBaristaItems = order.getOrderDetails() != null && order.getOrderDetails().stream()
                    .anyMatch(detail -> detail.getItemMenu() != null && 
                        Boolean.TRUE.equals(detail.getItemMenu().getRequiresBaristaPreparation()));
                
                if (hasChefItems) {
                    messagingTemplate.convertAndSend(getCompanyTopic("/topic/chef/orders", order), notification);
                    log.debug("👨‍🍳 WebSocket: Notifying ALL CHEFS - Order {} status changed (no assignment)", 
                        order.getOrderNumber());
                }
                
                if (hasBaristaItems) {
                    messagingTemplate.convertAndSend(getCompanyTopic("/topic/barista/orders", order), notification);
                    log.debug("☕ WebSocket: Notifying ALL BARISTAS - Order {} status changed (no assignment)", 
                        order.getOrderNumber());
                }
            }
        }
        
        // Always send to admin kitchen
        messagingTemplate.convertAndSend(getCompanyTopic("/topic/admin/kitchen", order), notification);
        
        // Notify DELIVERY role if order type is DELIVERY and status is READY, ON_THE_WAY, or DELIVERED
        if (order.getOrderType() == com.aatechsolutions.elgransazon.domain.entity.OrderType.DELIVERY) {
            if (order.getStatus() == com.aatechsolutions.elgransazon.domain.entity.OrderStatus.READY ||
                order.getStatus() == com.aatechsolutions.elgransazon.domain.entity.OrderStatus.ON_THE_WAY ||
                order.getStatus() == com.aatechsolutions.elgransazon.domain.entity.OrderStatus.DELIVERED) {
                messagingTemplate.convertAndSend(getCompanyTopic("/topic/delivery/orders", order), notification);
                log.info("🚚 WebSocket: Notifying DELIVERY - Order {} status changed to {}", 
                    order.getOrderNumber(), order.getStatus());
            }
        }
        
        log.debug("WebSocket: Order status change - {} - {}", order.getOrderNumber(), message);
    }

    /**
     * Notifies when items are added to an existing order
     * Implements smart notification routing based on existing assignments and item types
     * 
     * @param order The order with new items
     * @param newItems The list of new OrderDetails that were added
     */
    public void notifyItemsAdded(Order order, java.util.List<com.aatechsolutions.elgransazon.domain.entity.OrderDetail> newItems) {
        if (newItems == null || newItems.isEmpty()) {
            log.warn("notifyItemsAdded called with empty items list");
            return;
        }
        
        // Detect what type of items were added
        boolean hasChefItems = newItems.stream()
            .anyMatch(detail -> detail.getItemMenu() != null && 
                Boolean.TRUE.equals(detail.getItemMenu().getRequiresPreparation()));
        
        boolean hasBaristaItems = newItems.stream()
            .anyMatch(detail -> detail.getItemMenu() != null && 
                Boolean.TRUE.equals(detail.getItemMenu().getRequiresBaristaPreparation()));
        
        // Check current assignments
        boolean hasAssignedChef = order.getPreparedBy() != null;
        boolean hasAssignedBarista = order.getPreparedByBarista() != null;
        
        String message = String.format("Se agregaron %d item(s) al pedido %s", newItems.size(), order.getOrderNumber());
        OrderNotificationDTO notification = buildOrderNotification(order, "ITEMS_ADDED", message);
        
        // SMART NOTIFICATION ROUTING LOGIC
        // Case 1: No chef and no barista assigned → Notify ALL chefs and ALL baristas
        if (!hasAssignedChef && !hasAssignedBarista) {
            if (hasChefItems) {
                messagingTemplate.convertAndSend(getCompanyTopic("/topic/chef/orders", order), notification);
                log.info("👨‍🍳 WebSocket: Notifying ALL CHEFS - Order {} has no assignments, {} chef items added", 
                    order.getOrderNumber(),
                    newItems.stream().filter(d -> Boolean.TRUE.equals(d.getItemMenu().getRequiresPreparation())).count());
            }
            if (hasBaristaItems) {
                messagingTemplate.convertAndSend(getCompanyTopic("/topic/barista/orders", order), notification);
                log.info("☕ WebSocket: Notifying ALL BARISTAS - Order {} has no assignments, {} barista items added", 
                    order.getOrderNumber(),
                    newItems.stream().filter(d -> Boolean.TRUE.equals(d.getItemMenu().getRequiresBaristaPreparation())).count());
            }
        }
        // Case 2: Has chef only, incoming chef+barista items → Notify that chef + all baristas
        else if (hasAssignedChef && !hasAssignedBarista && hasChefItems && hasBaristaItems) {
            messagingTemplate.convertAndSendToUser(
                order.getPreparedBy().getUsername(),
                "/queue/orders",
                notification
            );
            log.info("👨‍🍳 WebSocket: Notifying assigned CHEF {} - Mixed items added to order {}", 
                order.getPreparedBy().getUsername(), order.getOrderNumber());
            
            messagingTemplate.convertAndSend(getCompanyTopic("/topic/barista/orders", order), notification);
            log.info("☕ WebSocket: Notifying ALL BARISTAS - Chef-assigned order {} has new barista items", 
                order.getOrderNumber());
        }
        // Case 3: Has chef only, incoming chef items only → Notify that chef only
        else if (hasAssignedChef && !hasAssignedBarista && hasChefItems && !hasBaristaItems) {
            messagingTemplate.convertAndSendToUser(
                order.getPreparedBy().getUsername(),
                "/queue/orders",
                notification
            );
            log.info("👨‍🍳 WebSocket: Notifying assigned CHEF {} ONLY - Chef items added to order {}", 
                order.getPreparedBy().getUsername(), order.getOrderNumber());
        }
        // Case 4: Has barista only, incoming chef+barista items → Notify that barista + all chefs
        else if (!hasAssignedChef && hasAssignedBarista && hasChefItems && hasBaristaItems) {
            messagingTemplate.convertAndSendToUser(
                order.getPreparedByBarista().getUsername(),
                "/queue/orders",
                notification
            );
            log.info("☕ WebSocket: Notifying assigned BARISTA {} - Mixed items added to order {}", 
                order.getPreparedByBarista().getUsername(), order.getOrderNumber());
            
            messagingTemplate.convertAndSend(getCompanyTopic("/topic/chef/orders", order), notification);
            log.info("👨‍🍳 WebSocket: Notifying ALL CHEFS - Barista-assigned order {} has new chef items", 
                order.getOrderNumber());
        }
        // Case 5: Has barista only, incoming barista items only → Notify that barista only
        else if (!hasAssignedChef && hasAssignedBarista && !hasChefItems && hasBaristaItems) {
            messagingTemplate.convertAndSendToUser(
                order.getPreparedByBarista().getUsername(),
                "/queue/orders",
                notification
            );
            log.info("☕ WebSocket: Notifying assigned BARISTA {} ONLY - Barista items added to order {}", 
                order.getPreparedByBarista().getUsername(), order.getOrderNumber());
        }
        // Case 6: Has both, incoming chef+barista items → Notify both assigned
        else if (hasAssignedChef && hasAssignedBarista && hasChefItems && hasBaristaItems) {
            messagingTemplate.convertAndSendToUser(
                order.getPreparedBy().getUsername(),
                "/queue/orders",
                notification
            );
            messagingTemplate.convertAndSendToUser(
                order.getPreparedByBarista().getUsername(),
                "/queue/orders",
                notification
            );
            log.info("👨‍🍳☕ WebSocket: Notifying BOTH assigned - Chef {} and Barista {} - Mixed items added to order {}", 
                order.getPreparedBy().getUsername(), 
                order.getPreparedByBarista().getUsername(), 
                order.getOrderNumber());
        }
        // Case 7: Has both, incoming barista items only → Notify assigned barista only
        else if (hasAssignedChef && hasAssignedBarista && !hasChefItems && hasBaristaItems) {
            messagingTemplate.convertAndSendToUser(
                order.getPreparedByBarista().getUsername(),
                "/queue/orders",
                notification
            );
            log.info("☕ WebSocket: Notifying assigned BARISTA {} ONLY - Barista items added to order {} (chef already assigned)", 
                order.getPreparedByBarista().getUsername(), order.getOrderNumber());
        }
        // Case 8: Has both, incoming chef items only → Notify assigned chef only
        else if (hasAssignedChef && hasAssignedBarista && hasChefItems && !hasBaristaItems) {
            messagingTemplate.convertAndSendToUser(
                order.getPreparedBy().getUsername(),
                "/queue/orders",
                notification
            );
            log.info("👨‍🍳 WebSocket: Notifying assigned CHEF {} ONLY - Chef items added to order {} (barista already assigned)", 
                order.getPreparedBy().getUsername(), order.getOrderNumber());
        }
        // Case 9: Fallback for edge cases → Notify all
        else {
            log.warn("⚠️ WebSocket: Unexpected notification scenario for order {} - Notifying all as fallback", 
                order.getOrderNumber());
            if (hasChefItems) {
                messagingTemplate.convertAndSend(getCompanyTopic("/topic/chef/orders", order), notification);
            }
            if (hasBaristaItems) {
                messagingTemplate.convertAndSend(getCompanyTopic("/topic/barista/orders", order), notification);
            }
        }
        
        // Always send to admin kitchen
        messagingTemplate.convertAndSend(getCompanyTopic("/topic/admin/kitchen", order), notification);
        
        // Send to general orders topic for view updates
        messagingTemplate.convertAndSend(getCompanyTopic("/topic/orders", order), notification);
        
        log.info("🔔 WebSocket: Items added notification completed - Order {} - Chef items: {}, Barista items: {} - Assigned Chef: {}, Assigned Barista: {}", 
            order.getOrderNumber(), hasChefItems, hasBaristaItems, hasAssignedChef, hasAssignedBarista);
    }

    /**
     * Notifies when a chef is assigned to an order
     */
    public void notifyChefAssigned(Order order, String chefName) {
        OrderNotificationDTO notification = buildOrderNotification(order, "CHEF_ASSIGNED",
            "Pedido asignado a " + chefName);
        notification.setChefName(chefName);
        
        // Send to all to update kitchen view
        messagingTemplate.convertAndSend(getCompanyTopic("/topic/chef/orders", order), notification);
        messagingTemplate.convertAndSend(getCompanyTopic("/topic/admin/kitchen", order), notification);
        
        log.info("WebSocket: Chef assignment - {} assigned to {}", 
            order.getOrderNumber(), chefName);
    }

    /**
     * Notifies when an order is accepted by a chef or barista
     * This will hide the order from other chefs/baristas who didn't accept it
     * 
     * @param order The order that was accepted
     * @param acceptedBy The username of who accepted it
     * @param role The role ("chef" or "barista")
     */
    public void notifyOrderAccepted(Order order, String acceptedBy, String role) {
        OrderNotificationDTO notification = buildOrderNotification(order, "ORDER_ACCEPTED",
            "Pedido #" + order.getOrderNumber() + " fue aceptado por " + acceptedBy);
        notification.setChefName(acceptedBy);
        
        // Send to the appropriate channel so OTHER chefs/baristas hide it
        if ("chef".equalsIgnoreCase(role)) {
            messagingTemplate.convertAndSend(getCompanyTopic("/topic/chef/orders", order), notification);
            log.info("👨‍🍳 WebSocket: Notifying ALL CHEFS - Order {} accepted by chef {}", 
                order.getOrderNumber(), acceptedBy);
        } else if ("barista".equalsIgnoreCase(role)) {
            messagingTemplate.convertAndSend(getCompanyTopic("/topic/barista/orders", order), notification);
            log.info("☕ WebSocket: Notifying ALL BARISTAS - Order {} accepted by barista {}", 
                order.getOrderNumber(), acceptedBy);
        }
        
        // Send to admin kitchen
        messagingTemplate.convertAndSend(getCompanyTopic("/topic/admin/kitchen", order), notification);
        
        log.info("🔔 WebSocket: Order accepted notification sent - {} by {} ({})", 
            order.getOrderNumber(), acceptedBy, role);
    }

    /**
     * Updates kitchen statistics in real-time
     */
    public void updateKitchenStats(KitchenStatsDTO stats) {
        messagingTemplate.convertAndSend("/topic/kitchen/stats", stats);
        log.debug("WebSocket: Kitchen stats updated - pending={}, inPrep={}", 
            stats.getPendingCount(), stats.getInPreparationCount());
    }

    /**
     * Sends notification to administrators
     */
    public void notifyAdmins(String message, Object data) {
        AdminNotification notification = new AdminNotification(message, data);
        messagingTemplate.convertAndSend("/topic/admin/notifications", notification);
        log.info("WebSocket: Admin notification sent - {}", message);
    }

    /**
     * Notifies about order deletion
     */
    public void notifyOrderDeleted(Long orderId, String orderNumber) {
        OrderDeletionNotification notification = new OrderDeletionNotification(orderId, orderNumber);
        messagingTemplate.convertAndSend("/topic/chef/orders", notification);
        messagingTemplate.convertAndSend("/topic/admin/kitchen", notification);
        log.info("WebSocket: Order deletion notification - {}", orderNumber);
    }

    /**
     * Notifies about order cancellation
     * Only notifies chef if order has chef items, barista if order has barista items
     * Sends to delivery only if applicable
     */
    public void notifyOrderCancelled(Order order) {
        OrderNotificationDTO notification = buildOrderNotification(order, "ORDER_CANCELLED",
            "Pedido #" + order.getOrderNumber() + " ha sido cancelado");
        
        // Check if order has items that require chef preparation
        boolean hasChefItems = order.getOrderDetails().stream()
            .anyMatch(detail -> detail.getItemMenu() != null && 
                      Boolean.TRUE.equals(detail.getItemMenu().getRequiresPreparation()));
        
        // Check if order has items that require barista preparation
        boolean hasBaristaItems = order.getOrderDetails().stream()
            .anyMatch(detail -> detail.getItemMenu() != null && 
                      Boolean.TRUE.equals(detail.getItemMenu().getRequiresBaristaPreparation()));
        
        // Send to chefs only if order has chef items
        // Rule: if the order has already been taken by a chef (preparedBy != null),
        // notify ONLY that chef via their personal queue. Other chefs no longer see
        // this order in their pending view, so they should not receive cancellation
        // notifications for orders that don't belong to them.
        if (hasChefItems) {
            if (order.getPreparedBy() != null) {
                messagingTemplate.convertAndSendToUser(
                    order.getPreparedBy().getUsername(),
                    "/queue/orders",
                    notification
                );
                log.info("👨‍🍳 WebSocket: Notifying owner CHEF '{}' - Order {} cancelled",
                        order.getPreparedBy().getUsername(), order.getOrderNumber());
            } else {
                // Order is still pending / unassigned → broadcast to all chefs
                messagingTemplate.convertAndSend(getCompanyTopic("/topic/chef/orders", order), notification);
                log.info("👨‍🍳 WebSocket: Broadcasting to ALL CHEFS - Order {} cancelled (unassigned)", order.getOrderNumber());
            }
        }
        
        // Send to baristas only if order has barista items (same rule as chef)
        if (hasBaristaItems) {
            if (order.getPreparedByBarista() != null) {
                messagingTemplate.convertAndSendToUser(
                    order.getPreparedByBarista().getUsername(),
                    "/queue/orders",
                    notification
                );
                log.info("☕ WebSocket: Notifying owner BARISTA '{}' - Order {} cancelled",
                        order.getPreparedByBarista().getUsername(), order.getOrderNumber());
            } else {
                // Order is still pending / unassigned → broadcast to all baristas
                messagingTemplate.convertAndSend(getCompanyTopic("/topic/barista/orders", order), notification);
                log.info("☕ WebSocket: Broadcasting to ALL BARISTAS - Order {} cancelled (unassigned)", order.getOrderNumber());
            }
        }
        
        // Send to all delivery persons if order type is DELIVERY
        if (order.getOrderType() == com.aatechsolutions.elgransazon.domain.entity.OrderType.DELIVERY) {
            messagingTemplate.convertAndSend(getCompanyTopic("/topic/delivery/orders", order), notification);
            log.info("🚚 WebSocket: Notifying DELIVERY - Order {} cancelled", order.getOrderNumber());
        }
        
        // Send to admin kitchen
        messagingTemplate.convertAndSend(getCompanyTopic("/topic/admin/kitchen", order), notification);
        
        log.info("WebSocket: Order cancellation notification - {} (chef: {}, barista: {})", 
                 order.getOrderNumber(), hasChefItems, hasBaristaItems);
    }

    /**
     * Notifies when an item is deleted from an order
     * Sends update to chef/barista so they can remove the item from their view
     * 
     * @param order The order with the deleted item
     * @param deletedItem The OrderDetail that was deleted
     */
    public void notifyItemDeleted(Order order, com.aatechsolutions.elgransazon.domain.entity.OrderDetail deletedItem) {
        if (deletedItem == null || deletedItem.getItemMenu() == null) {
            log.warn("notifyItemDeleted called with null item");
            return;
        }
        
        String itemName = deletedItem.getItemMenu().getName();
        String message = String.format("Item '%s' eliminado del pedido %s", itemName, order.getOrderNumber());
        
        // Build notification with deleted item info
        ItemDeletedNotification notification = new ItemDeletedNotification(
            order.getIdOrder(),
            order.getOrderNumber(),
            deletedItem.getIdOrderDetail(),
            itemName,
            message
        );
        
        // Determine if item requires chef or barista preparation
        boolean requiresChef = Boolean.TRUE.equals(deletedItem.getItemMenu().getRequiresPreparation());
        boolean requiresBarista = Boolean.TRUE.equals(deletedItem.getItemMenu().getRequiresBaristaPreparation());
        
        // Send to appropriate role-specific topics
        if (requiresChef) {
            messagingTemplate.convertAndSend(getCompanyTopic("/topic/chef/orders", order), notification);
            log.info("👨‍🍳 WebSocket: Notifying chefs - Item '{}' deleted from order {}", 
                itemName, order.getOrderNumber());
            
            // If chef was assigned, send personal notification
            if (order.getPreparedBy() != null) {
                messagingTemplate.convertAndSendToUser(
                    order.getPreparedBy().getUsername(),
                    "/queue/orders",
                    notification
                );
            }
        }
        
        if (requiresBarista) {
            messagingTemplate.convertAndSend(getCompanyTopic("/topic/barista/orders", order), notification);
            log.info("☕ WebSocket: Notifying baristas - Item '{}' deleted from order {}", 
                itemName, order.getOrderNumber());
            
            // If barista was assigned, send personal notification
            if (order.getPreparedByBarista() != null) {
                messagingTemplate.convertAndSendToUser(
                    order.getPreparedByBarista().getUsername(),
                    "/queue/orders",
                    notification
                );
            }
        }
        
        // Send to admin kitchen
        messagingTemplate.convertAndSend(getCompanyTopic("/topic/admin/kitchen", order), notification);
        
        log.info("WebSocket: Item deletion notification - {} from order {}", itemName, order.getOrderNumber());
    }

    // Helper method to build order notification DTO
    private OrderNotificationDTO buildOrderNotification(Order order, String type, String message) {
        return OrderNotificationDTO.builder()
            .orderId(order.getIdOrder())
            .orderNumber(order.getOrderNumber())
            .status(order.getStatus())
            .orderType(order.getOrderType())
            .tableNumber(order.getTable() != null ? order.getTable().getTableNumber() : null)
            .total(order.getTotal())
            .createdAt(order.getCreatedAt())
            .itemCount(order.getOrderDetails() != null ? order.getOrderDetails().size() : 0)
            .items(order.getOrderDetails() != null ? order.getOrderDetails().stream()
                .map(detail -> OrderNotificationDTO.OrderItemDTO.builder()
                    .name(detail.getItemMenu().getName())
                    .quantity(detail.getQuantity())
                    .requiresPreparation(detail.getItemMenu().getRequiresPreparation())
                    .build())
                .collect(Collectors.toList()) : null)
            .notificationType(type)
            .message(message)
            .chefName(order.getPreparedBy() != null ? 
                order.getPreparedBy().getNombre() + " " + order.getPreparedBy().getApellido() : null)
            .build();
    }

    // Inner classes for specific notification types
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class AdminNotification {
        private String message;
        private Object data;
    }

    @lombok.Data
    private static class OrderDeletionNotification {
        private Long orderId;
        private String orderNumber;
        private String notificationType = "ORDER_DELETED";

        public OrderDeletionNotification(Long orderId, String orderNumber) {
            this.orderId = orderId;
            this.orderNumber = orderNumber;
        }
    }
    
    @lombok.Data
    private static class ItemDeletedNotification {
        private Long orderId;
        private String orderNumber;
        private Long itemDetailId;
        private String itemName;
        private String message;
        private String notificationType = "ITEM_DELETED";
        
        public ItemDeletedNotification(Long orderId, String orderNumber, Long itemDetailId, String itemName, String message) {
            this.orderId = orderId;
            this.orderNumber = orderNumber;
            this.itemDetailId = itemDetailId;
            this.itemName = itemName;
            this.message = message;
        }
    }
}
