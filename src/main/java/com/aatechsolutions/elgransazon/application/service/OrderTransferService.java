package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.Role;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Traspaso de un pedido entre meseros.
 *
 * <p>El mesero que atiende un pedido puede pasarle el pedido a un compañero (se retira,
 * cambia de turno, cubre otra zona...). El traspaso es una <b>solicitud</b>: el mesero
 * destino recibe un aviso en tiempo real y decide con Aceptar / Denegar. Hasta que
 * acepte, el pedido sigue siendo del mesero actual.</p>
 *
 * <p>Al aceptar, {@code Order.employee} (el responsable vigente) pasa al mesero destino:
 * él opera el pedido, lo cobra, recibe su propina y suma en sus estadísticas. El
 * responsable anterior queda registrado en {@code Order.transferredFrom} para la leyenda
 * de trazabilidad, mientras que {@code Order.createdBy} <b>nunca</b> se reescribe: sigue
 * siendo la bitácora de quién capturó el pedido, por eso el creador conserva acceso de
 * solo lectura al pedido que transfirió.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderTransferService {

    private final OrderRepository orderRepository;
    private final EmployeeRepository employeeRepository;
    private final WebSocketNotificationService wsNotificationService;

    /**
     * Solicita transferir un pedido a otro mesero. Un pedido solo puede solicitarse por
     * quien lo atiende (<i>no</i> por quien lo creó y ya lo transfirió).
     *
     * <p>Sin una solicitud previa, el pedido no se puede solicitar de nuevo; sin embargo,
     * si ya había una solicitud pendiente sin responder, esta se <b>reemplaza</b> para no
     * dejar al mesero amarrado a un compañero que no contesta.</p>
     *
     * @param orderId          pedido a transferir
     * @param targetEmployeeId mesero destino
     * @param requesterUsername usuario autenticado que solicita el traspaso
     * @return el pedido con la solicitud pendiente registrada
     */
    public Order requestTransfer(Long orderId, Long targetEmployeeId, String requesterUsername) {
        Order order = lockOrderOfCurrentCompany(orderId);

        if (!order.isOperatedBy(requesterUsername)) {
            throw new IllegalStateException("Solo el mesero que atiende el pedido puede transferirlo");
        }
        if (!order.canBeTransferred()) {
            throw new IllegalStateException(
                    "No se pueden transferir pedidos pagados o cancelados");
        }

        Employee target = employeeRepository.findById(targetEmployeeId)
                .orElseThrow(() -> new IllegalArgumentException("Mesero destino no encontrado"));
        validateTarget(target);

        Employee currentOwner = order.getEmployee();
        if (currentOwner != null && currentOwner.getIdEmpleado() != null
                && currentOwner.getIdEmpleado().equals(target.getIdEmpleado())) {
            throw new IllegalStateException("El pedido ya le pertenece a ese mesero");
        }

        boolean replacing = order.hasPendingTransfer();
        order.setTransferRequestedTo(target);
        order.setTransferRequestedAt(LocalDateTime.now());
        order.setUpdatedBy(requesterUsername);
        Order saved = orderRepository.save(order);

        log.info("Mesero {} {} el pedido {} a {}",
                requesterUsername, replacing ? "re-solicitó" : "solicitó transferir",
                saved.getOrderNumber(), target.getUsername());

        wsNotificationService.notifyTransferRequested(saved, currentOwner, target);
        return saved;
    }

    /**
     * Acepta una transferencia pendiente: el pedido pasa a ser del mesero que acepta.
     *
     * @param orderId  pedido transferido
     * @param username mesero destino (quien acepta)
     * @return el pedido ya reasignado
     */
    public Order acceptTransfer(Long orderId, String username) {
        Order order = lockOrderOfCurrentCompany(orderId);
        Employee target = pendingTargetFor(order, username);

        if (!order.canBeTransferred()) {
            // El pedido se cobró o se canceló mientras la solicitud estaba pendiente:
            // una venta cerrada no cambia de dueño, así que la solicitud se descarta.
            clearPendingTransfer(order);
            order.setUpdatedBy(username);
            orderRepository.save(order);
            throw new IllegalStateException(
                    "El pedido ya fue pagado o cancelado, por lo que la transferencia se canceló");
        }

        Employee previousOwner = order.getEmployee();
        order.setTransferredFrom(previousOwner);
        order.setTransferredAt(LocalDateTime.now());
        order.setEmployee(target);
        clearPendingTransfer(order);
        order.setUpdatedBy(username);
        Order saved = orderRepository.save(order);

        log.info("Mesero {} aceptó el pedido {} (antes de {})",
                username, saved.getOrderNumber(),
                previousOwner != null ? previousOwner.getUsername() : "sin responsable");

        wsNotificationService.notifyTransferResolved(saved, previousOwner, target, true);
        return saved;
    }

    /**
     * Deniega una transferencia pendiente: el pedido se queda con su responsable actual.
     *
     * @param orderId  pedido transferido
     * @param username mesero destino (quien deniega)
     * @return el pedido sin cambios de responsable
     */
    public Order denyTransfer(Long orderId, String username) {
        Order order = lockOrderOfCurrentCompany(orderId);
        Employee target = pendingTargetFor(order, username);
        Employee currentOwner = order.getEmployee();

        clearPendingTransfer(order);
        order.setUpdatedBy(username);
        Order saved = orderRepository.save(order);

        log.info("Mesero {} denegó el pedido {}", username, saved.getOrderNumber());
        wsNotificationService.notifyTransferResolved(saved, currentOwner, target, false);
        return saved;
    }

    /**
     * Transferencias pendientes dirigidas al usuario autenticado, para volver a mostrarle
     * la solicitud cuando entra a su lista (si se perdió el aviso en vivo).
     */
    @Transactional(readOnly = true)
    public List<Order> findPendingTransfersFor(String username) {
        if (username == null || username.isBlank()) {
            return List.of();
        }
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.findPendingTransfersForUserAndCompany(username, company);
    }

    // ========== Helpers ==========

    /**
     * Bloquea la fila del pedido para resolver la transferencia sin que dos peticiones
     * concurrentes (aceptar y denegar, o dos aceptaciones) se pisen.
     */
    private Order lockOrderOfCurrentCompany(Long orderId) {
        Company company = CompanyContext.requireCurrentCompany();
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
        if (order.getCompany() == null
                || !company.getIdCompany().equals(order.getCompany().getIdCompany())) {
            throw new IllegalStateException("El pedido no pertenece a esta empresa");
        }
        return order;
    }

    /**
     * Valida que la solicitud pendiente del pedido apunte al usuario autenticado.
     */
    private Employee pendingTargetFor(Order order, String username) {
        Employee target = order.getTransferRequestedTo();
        if (target == null || target.getUsername() == null
                || !target.getUsername().equalsIgnoreCase(username)) {
            throw new IllegalStateException("No tienes una transferencia pendiente para este pedido");
        }
        return target;
    }

    private void clearPendingTransfer(Order order) {
        order.setTransferRequestedTo(null);
        order.setTransferRequestedAt(null);
    }

    /**
     * Solo se transfiere a un mesero activo de la misma empresa.
     */
    private void validateTarget(Employee target) {
        Company company = CompanyContext.requireCurrentCompany();
        if (target.getCompany() == null
                || !company.getIdCompany().equals(target.getCompany().getIdCompany())) {
            throw new IllegalArgumentException("El mesero destino no pertenece a esta empresa");
        }
        if (target.getEnabled() != null && !target.getEnabled()) {
            throw new IllegalStateException("El mesero destino está inactivo");
        }
        if (!target.hasRole(Role.WAITER)) {
            throw new IllegalStateException("Solo se puede transferir un pedido a un mesero");
        }
    }
}
