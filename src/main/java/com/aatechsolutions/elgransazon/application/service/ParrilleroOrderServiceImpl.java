package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ParrilleroOrderServiceImpl - Implementation for Parrillero role (grill cook)
 *
 * Mirrors ChefOrderServiceImpl but filters by requiresParrilleroPreparation
 * and uses Order.preparedByParrillero for ownership.
 */
@Service("parrilleroOrderService")
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ParrilleroOrderServiceImpl implements OrderService {

    private final OrderServiceImpl adminOrderService;
    private final OrderRepository orderRepository;
    private final EmployeeService employeeService;
    private final WebSocketNotificationService wsNotificationService;

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }

    // ========== CRUD Operations (restricted for parrillero) ==========

    @Override
    public Order create(Order order, List<OrderDetail> orderDetails) {
        throw new UnsupportedOperationException("El parrillero no puede crear pedidos");
    }

    @Override
    public Order update(Long id, Order order, List<OrderDetail> orderDetails) {
        throw new UnsupportedOperationException("El parrillero no puede modificar pedidos");
    }

    @Override
    public Order updateOrderInfo(Long id, Order updatedOrder) {
        throw new UnsupportedOperationException("El parrillero no puede modificar información de pedidos");
    }

    @Override
    public Order cancel(Long id, String username) {
        throw new UnsupportedOperationException("El parrillero no puede cancelar pedidos");
    }

    @Override
    public Order changeStatus(Long id, OrderStatus newStatus, String username) {
        throw new UnsupportedOperationException(
            "El parrillero no puede cambiar el estado general de la orden. " +
            "Use el control de items individuales. El estado de la orden se actualiza automáticamente."
        );
    }

    @Override
    public Order addItemsToExistingOrder(Long orderId, List<OrderDetail> newItems, String username) {
        throw new UnsupportedOperationException("El parrillero no puede agregar items a pedidos");
    }

    @Override
    public Order acceptOrderItems(Long orderId, List<Long> itemDetailIds, String username) {
        throw new UnsupportedOperationException("El parrillero no puede aceptar items de pedidos");
    }

    @Override
    public Order changeItemsStatus(Long orderId, List<Long> itemDetailIds, OrderStatus newStatus, String username) {
        Order order = findByIdOrThrow(orderId);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("No se puede cambiar el estado de items en una orden CANCELADA.");
        }
        if (order.getStatus() == OrderStatus.PAID) {
            throw new IllegalStateException("No se puede cambiar el estado de items en una orden PAGADA.");
        }

        String currentUsername = getCurrentUsername();

        Long currentEmployeeId = employeeService.findByUsername(currentUsername)
            .map(Employee::getIdEmpleado)
            .orElse(null);

        for (Long itemDetailId : itemDetailIds) {
            OrderDetail detail = order.getOrderDetails().stream()
                .filter(d -> d.getIdOrderDetail().equals(itemDetailId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Item detail no encontrado: " + itemDetailId
                ));

            if (!Boolean.TRUE.equals(detail.getItemMenu().getRequiresParrilleroPreparation())) {
                throw new IllegalStateException(
                    "Este item no requiere preparación por el parrillero: " +
                    detail.getItemMenu().getName()
                );
            }

            // Ownership lock: only the parrillero who accepted the order can advance its items
            if (detail.getItemStatus() == OrderStatus.IN_PREPARATION) {
                Employee orderParrillero = order.getPreparedByParrillero();
                if (orderParrillero != null && orderParrillero.getIdEmpleado() != null
                        && currentEmployeeId != null
                        && !orderParrillero.getIdEmpleado().equals(currentEmployeeId)) {
                    throw new IllegalStateException(
                        "Solo el parrillero que aceptó esta orden puede cambiar el estado de sus items: "
                        + detail.getItemMenu().getName()
                    );
                }
            }

            OrderStatus itemStatus = detail.getItemStatus();
            boolean isValidTransition =
                (itemStatus == OrderStatus.PENDING && newStatus == OrderStatus.IN_PREPARATION) ||
                (itemStatus == OrderStatus.IN_PREPARATION && newStatus == OrderStatus.READY) ||
                (itemStatus == OrderStatus.PENDING && newStatus == OrderStatus.READY);

            if (!isValidTransition) {
                throw new IllegalStateException(
                    "El parrillero solo puede cambiar items de PENDIENTE a EN PREPARACIÓN, " +
                    "de EN PREPARACIÓN a LISTO, o de PENDIENTE a LISTO (cuando marca todo como listo)"
                );
            }
        }

        log.info("Parrillero {} changing status of {} items in order {}",
            currentUsername, itemDetailIds.size(), orderId);

        Order updatedOrder = adminOrderService.changeItemsStatus(orderId, itemDetailIds, newStatus, username);

        if (newStatus == OrderStatus.IN_PREPARATION && updatedOrder.getPreparedByParrillero() == null) {
            try {
                Employee currentEmployee = employeeService.findByUsername(currentUsername)
                    .orElseThrow(() -> new IllegalStateException(
                        "Empleado no encontrado: " + currentUsername
                    ));

                updatedOrder.setPreparedByParrillero(currentEmployee);
                updatedOrder = orderRepository.save(updatedOrder);

                log.info("🔥 Assigned preparedByParrillero to {} for order {}",
                    currentUsername, updatedOrder.getOrderNumber());

                wsNotificationService.notifyOrderAccepted(updatedOrder, currentUsername, "parrillero");
                log.info("🔔 WebSocket sent: Order {} accepted by parrillero {}",
                    updatedOrder.getOrderNumber(), currentUsername);

            } catch (Exception e) {
                log.error("Failed to assign preparedByParrillero: {}", e.getMessage(), e);
            }
        }

        return updatedOrder;
    }

    /**
     * Change ALL parrillero items in an order to the next status.
     * Mirrors ChefOrderServiceImpl.changeAllChefItemsToNextStatus.
     */
    public Order changeAllParrilleroItemsToNextStatus(Long orderId, String username) {
        Order order = findByIdOrThrow(orderId);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("No se puede cambiar el estado de items en una orden CANCELADA.");
        }
        if (order.getStatus() == OrderStatus.PAID) {
            throw new IllegalStateException("No se puede cambiar el estado de items en una orden PAGADA.");
        }

        String currentUsername = getCurrentUsername();

        List<OrderDetail> parrilleroItems = order.getOrderDetails().stream()
            .filter(detail -> detail.getItemMenu() != null &&
                Boolean.TRUE.equals(detail.getItemMenu().getRequiresParrilleroPreparation()))
            .collect(Collectors.toList());

        if (parrilleroItems.isEmpty()) {
            throw new IllegalStateException("Esta orden no contiene items para el parrillero");
        }

        long pendingCount = parrilleroItems.stream()
            .filter(d -> d.getItemStatus() == OrderStatus.PENDING)
            .count();
        long inPrepCount = parrilleroItems.stream()
            .filter(d -> d.getItemStatus() == OrderStatus.IN_PREPARATION)
            .count();

        OrderStatus targetStatus;
        List<Long> itemsToChange = new ArrayList<>();

        if (inPrepCount > 0) {
            targetStatus = OrderStatus.READY;
            itemsToChange = parrilleroItems.stream()
                .filter(d -> d.getItemStatus() == OrderStatus.PENDING
                    || d.getItemStatus() == OrderStatus.IN_PREPARATION)
                .map(OrderDetail::getIdOrderDetail)
                .collect(Collectors.toList());

            log.info("Parrillero {} marking order {} as ready - moving {} items (PENDING+IN_PREP) to READY",
                currentUsername, orderId, itemsToChange.size());
        } else if (pendingCount > 0) {
            targetStatus = OrderStatus.IN_PREPARATION;
            itemsToChange = parrilleroItems.stream()
                .filter(d -> d.getItemStatus() == OrderStatus.PENDING)
                .map(OrderDetail::getIdOrderDetail)
                .collect(Collectors.toList());

            log.info("Parrillero {} accepting order {} - moving {} PENDING items to IN_PREPARATION",
                currentUsername, orderId, itemsToChange.size());
        } else {
            throw new IllegalStateException("Todos los items del parrillero ya están listos");
        }

        return changeItemsStatus(orderId, itemsToChange, targetStatus, username);
    }

    @Override
    public void delete(Long id) {
        throw new UnsupportedOperationException("El parrillero no puede eliminar pedidos");
    }

    // ========== Query Operations (filtered for parrillero) ==========

    @Override
    public List<Order> findAll() {
        Company company = CompanyContext.requireCurrentCompany();
        List<Order> allOrders = orderRepository.findOrdersWithParrilleroItemsByCompany(company);
        return allOrders.stream()
            .filter(this::hasItemsRequiringParrilleroPreparation)
            .collect(Collectors.toList());
    }

    private boolean hasItemsRequiringParrilleroPreparation(Order order) {
        if (order.getOrderDetails() == null || order.getOrderDetails().isEmpty()) {
            return false;
        }

        return order.getOrderDetails().stream()
            .anyMatch(detail -> {
                if (detail.getItemMenu() == null) {
                    return false;
                }
                if (!Boolean.TRUE.equals(detail.getItemMenu().getRequiresParrilleroPreparation())) {
                    return false;
                }
                OrderStatus itemStatus = detail.getItemStatus();
                return itemStatus == OrderStatus.TO_ACCEPT
                    || itemStatus == OrderStatus.PENDING
                    || itemStatus == OrderStatus.IN_PREPARATION;
            });
    }

    @Override
    public Optional<Order> findById(Long id) {
        return adminOrderService.findById(id);
    }

    @Override
    public Optional<Order> findByIdWithDetails(Long id) {
        return adminOrderService.findByIdWithDetails(id);
    }

    @Override
    public Optional<Order> findByOrderNumber(String orderNumber) {
        return adminOrderService.findByOrderNumber(orderNumber)
            .filter(order ->
                order.getStatus() == OrderStatus.PENDING ||
                order.getStatus() == OrderStatus.IN_PREPARATION ||
                order.getStatus() == OrderStatus.READY
            );
    }

    @Override
    public List<Order> findByTableId(Long tableId) {
        return adminOrderService.findByTableId(tableId).stream()
            .filter(this::hasItemsRequiringParrilleroPreparation)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<Order> findActiveOrderByTableId(Long tableId) {
        return adminOrderService.findActiveOrderByTableId(tableId)
            .filter(this::hasItemsRequiringParrilleroPreparation);
    }

    @Override
    public List<Order> findByEmployeeId(Long employeeId) {
        return adminOrderService.findByEmployeeId(employeeId).stream()
            .filter(this::hasItemsRequiringParrilleroPreparation)
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        return adminOrderService.findByStatus(status).stream()
            .filter(this::hasItemsRequiringParrilleroPreparation)
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByOrderType(OrderType orderType) {
        return adminOrderService.findByOrderType(orderType).stream()
            .filter(this::hasItemsRequiringParrilleroPreparation)
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findTodaysOrders() {
        return adminOrderService.findTodaysOrders().stream()
            .filter(this::hasItemsRequiringParrilleroPreparation)
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findActiveOrders() {
        return adminOrderService.findActiveOrders().stream()
            .filter(this::hasItemsRequiringParrilleroPreparation)
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return adminOrderService.findByDateRange(startDate, endDate).stream()
            .filter(this::hasItemsRequiringParrilleroPreparation)
            .collect(Collectors.toList());
    }

    // ========== Validation Operations (delegate to admin) ==========

    @Override
    public Map<Long, String> validateStock(List<OrderDetail> orderDetails) {
        return adminOrderService.validateStock(orderDetails);
    }

    @Override
    public boolean hasActiveOrder(Long tableId) {
        return adminOrderService.hasActiveOrder(tableId);
    }

    @Override
    public boolean isTableAvailableForOrder(Long tableId) {
        return adminOrderService.isTableAvailableForOrder(tableId);
    }

    @Override
    public String generateOrderNumber() {
        return adminOrderService.generateOrderNumber();
    }

    // ========== Statistics ==========

    @Override
    public long countByStatus(OrderStatus status) {
        if (status != OrderStatus.PENDING &&
            status != OrderStatus.IN_PREPARATION &&
            status != OrderStatus.READY) {
            return 0;
        }
        return adminOrderService.countByStatus(status);
    }

    @Override
    public long countTodaysOrders() {
        return findTodaysOrders().size();
    }

    @Override
    public long countTodaysOrdersByStatus(OrderStatus status) {
        if (status != OrderStatus.PENDING &&
            status != OrderStatus.IN_PREPARATION &&
            status != OrderStatus.READY) {
            return 0;
        }
        return findTodaysOrders().stream()
            .filter(order -> order.getStatus() == status)
            .count();
    }

    @Override
    public BigDecimal getTodaysRevenue() {
        return BigDecimal.ZERO;
    }

    @Override
    public Order findByIdOrThrow(Long id) {
        return findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado o no accesible"));
    }

    @Override
    public OrderDetail deleteOrderItem(Long orderId, Long itemDetailId, String username) {
        throw new UnsupportedOperationException("El parrillero no puede eliminar items de pedidos");
    }

    @Override
    public OrderDetailComplement deleteOrderItemComplement(Long orderId, Long itemDetailId, Long complementId, String username) {
        throw new UnsupportedOperationException("El parrillero no puede eliminar complementos de pedidos");
    }

    @Override
    public BigDecimal getTotalIncome() {
        return adminOrderService.getTotalIncome();
    }

    @Override
    public Map<String, BigDecimal> getIncomeByCategory() {
        return adminOrderService.getIncomeByCategory();
    }

    @Override
    public List<Object[]> getItemSalesByCategory(Long categoryId) {
        return adminOrderService.getItemSalesByCategory(categoryId);
    }

    // ========== Statistics by Date Range (delegate to admin) ==========

    @Override
    public long countPaidOrdersByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return adminOrderService.countPaidOrdersByDateRange(startDate, endDate);
    }

    @Override
    public long countPaidOrdersByUsernameAndDateRange(String username, LocalDateTime startDate, LocalDateTime endDate) {
        return adminOrderService.countPaidOrdersByUsernameAndDateRange(username, startDate, endDate);
    }

    @Override
    public long countByStatusAndDateRange(OrderStatus status, LocalDateTime startDate, LocalDateTime endDate) {
        return adminOrderService.countByStatusAndDateRange(status, startDate, endDate);
    }

    @Override
    public BigDecimal getRevenueByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return adminOrderService.getRevenueByDateRange(startDate, endDate);
    }

    @Override
    public BigDecimal getRevenueByUsernameAndDateRange(String username, LocalDateTime startDate, LocalDateTime endDate) {
        return adminOrderService.getRevenueByUsernameAndDateRange(username, startDate, endDate);
    }

    @Override
    public BigDecimal getRevenueCreatedByUserPaidByOthersAndDateRange(String username, LocalDateTime startDate, LocalDateTime endDate) {
        return adminOrderService.getRevenueCreatedByUserPaidByOthersAndDateRange(username, startDate, endDate);
    }

    @Override
    public BigDecimal getRevenueCreatedAndPaidBySameUserAndDateRange(String username, LocalDateTime startDate, LocalDateTime endDate) {
        return adminOrderService.getRevenueCreatedAndPaidBySameUserAndDateRange(username, startDate, endDate);
    }
}
