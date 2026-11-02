package com.aatechsolutions.elgransazon.util;

import com.aatechsolutions.elgransazon.application.dto.OccupiedTableInfoDTO;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.domain.entity.TableStatus;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Construye el resumen de las mesas ocupadas que la vista de crear pedido usa para
 * explicar quién está usando una mesa antes de bloquearla.
 *
 * <p>Se resuelve con una sola consulta para todas las mesas ocupadas, no una por
 * tarjeta. La consulta es deliberadamente independiente del mesero que está mirando:
 * el objetivo es que cualquiera pueda ver QUIÉN tiene la mesa; el enlace al pedido
 * solo se incluye cuando el rol actual tiene permiso para consultarlo.</p>
 */
public final class TableOccupancySupport {

    private static final DateTimeFormatter OPENED_AT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private TableOccupancySupport() {
    }

    public static Map<Long, OccupiedTableInfoDTO> build(
            Collection<RestaurantTable> tables,
            String role,
            String username,
            boolean canViewAnyOrder,
            OrderRepository orderRepository,
            DateTimeService dateTimeService) {

        if (tables == null || tables.isEmpty()) {
            return Map.of();
        }

        List<Long> occupiedTableIds = tables.stream()
                .filter(Objects::nonNull)
                .filter(table -> table.getStatus() == TableStatus.OCCUPIED)
                .map(RestaurantTable::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (occupiedTableIds.isEmpty()) {
            return Map.of();
        }

        Company company = CompanyContext.requireCurrentCompany();
        List<Order> activeOrders = orderRepository
                .findActiveOrdersWithDetailsByTableIds(company, occupiedTableIds);

        Map<Long, OccupiedTableInfoDTO> occupancy = new LinkedHashMap<>();
        for (Order order : activeOrders) {
            if (order == null || order.getTable() == null || order.getTable().getId() == null) {
                continue;
            }
            // Si por datos históricos hubiera más de un pedido activo en la misma mesa,
            // la consulta llega ordenada del más reciente al más antiguo y se conserva ese.
            occupancy.putIfAbsent(order.getTable().getId(),
                    toDto(order, role, username, canViewAnyOrder, dateTimeService));
        }
        return occupancy;
    }

    private static OccupiedTableInfoDTO toDto(
            Order order,
            String role,
            String username,
            boolean canViewAnyOrder,
            DateTimeService dateTimeService) {

        Employee employee = order.getEmployee();
        String waiterName = employee != null ? employee.getFullName() : trimToNull(order.getCreatedBy());
        if (waiterName == null) {
            waiterName = "Sin mesero asignado";
        }

        String waiterInitials = employee != null
                ? employee.getInitials()
                : initialsOf(waiterName);

        String customerName = firstNonBlank(
                order.getCustomerName(),
                customerFullName(order.getCustomer()),
                "Cliente no registrado");

        LocalDateTime openedAt = order.getCreatedAt() != null
                ? dateTimeService.toCompanyTime(order.getCreatedAt())
                : null;
        String openedAtLabel = openedAt != null ? openedAt.format(OPENED_AT_FORMAT) : "—";
        long occupiedMinutes = openedAt != null
                ? Math.max(0, Duration.between(openedAt, dateTimeService.nowLocal()).toMinutes())
                : 0;

        int itemCount = order.getOrderDetails() == null ? 0 : order.getOrderDetails().stream()
                .map(OrderDetail::getQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        OrderStatus status = order.getStatus() != null ? order.getStatus() : OrderStatus.PENDING;

        boolean canViewOrder = canViewAnyOrder
                || (username != null && (username.equalsIgnoreCase(order.getCreatedBy())
                || order.isOperatedBy(username)));

        String urlRole = "manager".equalsIgnoreCase(role) ? "admin" : role;
        String orderUrl = canViewOrder && order.getIdOrder() != null
                ? "/" + urlRole + "/orders/view/" + order.getIdOrder()
                : null;

        return new OccupiedTableInfoDTO(
                order.getIdOrder(),
                order.getOrderNumber(),
                waiterName,
                waiterInitials,
                customerName,
                openedAtLabel,
                occupiedMinutes,
                status.name(),
                status.getDisplayName(),
                itemCount,
                order.getTotal(),
                canViewOrder,
                orderUrl);
    }

    private static String customerFullName(Customer customer) {
        return customer != null ? customer.getFullName() : null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String initialsOf(String name) {
        String trimmed = trimToNull(name);
        if (trimmed == null) {
            return "?";
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }
}
