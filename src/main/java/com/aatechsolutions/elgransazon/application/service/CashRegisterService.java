package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.CashRegisterMovementRepository;
import com.aatechsolutions.elgransazon.domain.repository.CashRegisterSessionRepository;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.presentation.dto.CashRegisterSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Cash-register (caja) business logic for the CASHIER role.
 *
 * A cashier owns one drawer at a time: opens it with an initial amount, records
 * the manual movements (pagos, entradas, retiros) made during the day and closes
 * it with the counted physical cash. The day summary is derived, never copied:
 * sales come from the PAID orders the cashier collected in the session window.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CashRegisterService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999.99");

    private final CashRegisterSessionRepository sessionRepository;
    private final CashRegisterMovementRepository movementRepository;
    private final OrderRepository orderRepository;
    private final DateTimeService dateTimeService;

    // ---------- Queries ----------

    @Transactional(readOnly = true)
    public CashRegisterSession findOpenSession(Company company, Employee cashier) {
        return sessionRepository
                .findFirstByCompanyAndCashierAndStatusOrderByOpenedAtDesc(company, cashier, CashRegisterStatus.OPEN)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public CashRegisterSession findLatestSession(Company company, Employee cashier) {
        return sessionRepository.findFirstByCompanyAndCashierOrderByOpenedAtDesc(company, cashier).orElse(null);
    }

    /** Sessions of this cashier opened on the given company-local day. */
    @Transactional(readOnly = true)
    public List<CashRegisterSession> listSessionsForDate(Company company, Employee cashier, LocalDate date) {
        LocalDate day = date != null ? date : dateTimeService.todayLocal();
        return sessionRepository.findByCompanyAndCashierAndOpenedAtBetweenOrderByOpenedAtDesc(
                company, cashier,
                dateTimeService.startOfDayUtc(day),
                dateTimeService.endOfDayUtc(day));
    }

    @Transactional(readOnly = true)
    public CashRegisterSession getSession(Long id, Company company) {
        return sessionRepository.findByIdAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada."));
    }

    @Transactional(readOnly = true)
    public List<CashRegisterMovement> getMovements(CashRegisterSession session) {
        if (session == null) {
            return List.of();
        }
        return movementRepository.findBySessionOrderByOccurredAtAsc(session);
    }

    // ---------- Open / close ----------

    @Transactional
    public CashRegisterSession openSession(Company company, Employee cashier, BigDecimal initialAmount,
                                           String notes, String username) {
        if (findOpenSession(company, cashier) != null) {
            throw new IllegalStateException("Ya tienes una caja abierta. Ciérrala antes de abrir una nueva.");
        }
        BigDecimal amount = normalize(initialAmount, "El monto inicial");

        CashRegisterSession session = CashRegisterSession.builder()
                .company(company)
                .cashier(cashier)
                .status(CashRegisterStatus.OPEN)
                .openedAt(LocalDateTime.now(ZoneOffset.UTC))
                .initialAmount(amount)
                .notes(trimToNull(notes))
                .createdBy(username)
                .updatedBy(username)
                .build();

        CashRegisterSession saved = sessionRepository.save(session);
        log.info("Cash register opened by {} with initial amount ${}", username, amount);
        return saved;
    }

    @Transactional
    public CashRegisterSession closeSession(CashRegisterSession session, BigDecimal countedAmount,
                                            String closingNotes, Employee closedBy, String username) {
        if (session == null || !session.isOpen()) {
            throw new IllegalStateException("No hay una caja abierta para cerrar.");
        }
        BigDecimal counted = normalize(countedAmount, "El efectivo contado");

        session.setCountedAmount(counted);
        session.setClosedAt(LocalDateTime.now(ZoneOffset.UTC));
        session.setClosedBy(closedBy);
        session.setClosingNotes(trimToNull(closingNotes));
        session.setStatus(CashRegisterStatus.CLOSED);
        session.setUpdatedBy(username);

        CashRegisterSession saved = sessionRepository.save(session);
        log.info("Cash register closed by {} for cashier {} (counted ${})",
                username, session.getCashier() != null ? session.getCashier().getUsername() : "?", counted);
        return saved;
    }

    // ---------- Movements ----------

    @Transactional
    public CashRegisterMovement addMovement(Company company, CashRegisterSession session, CashRegisterMovementType type,
                                            String concept, BigDecimal amount, String notes, String username) {
        if (session == null || !session.isOpen()) {
            throw new IllegalStateException("No hay una caja abierta para registrar el movimiento.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Selecciona el tipo de movimiento.");
        }
        if (concept == null || concept.isBlank()) {
            throw new IllegalArgumentException("El concepto es obligatorio (ej. Hielo, Gas).");
        }
        BigDecimal value = normalize(amount, "El monto");

        CashRegisterMovement movement = CashRegisterMovement.builder()
                .company(company)
                .session(session)
                .type(type)
                .concept(concept.trim())
                .amount(value)
                .notes(trimToNull(notes))
                .occurredAt(LocalDateTime.now(ZoneOffset.UTC))
                .createdBy(username)
                .build();

        CashRegisterMovement saved = movementRepository.save(movement);
        log.info("Cash register movement {} of ${} ('{}') registered by {}",
                type, value, concept, username);
        return saved;
    }

    @Transactional
    public void deleteMovement(Long movementId, Company company, Employee cashier) {
        CashRegisterMovement movement = movementRepository.findById(movementId)
                .orElseThrow(() -> new IllegalArgumentException("Movimiento no encontrado."));
        if (movement.getCompany() == null || company == null
                || !Objects.equals(movement.getCompany().getIdCompany(), company.getIdCompany())) {
            throw new IllegalArgumentException("Movimiento no encontrado.");
        }
        CashRegisterSession session = movement.getSession();
        if (session == null || !session.isOpen()) {
            throw new IllegalStateException("No se pueden modificar movimientos de una caja cerrada.");
        }
        if (session.getCashier() == null || cashier == null
                || !Objects.equals(session.getCashier().getIdEmpleado(), cashier.getIdEmpleado())) {
            throw new IllegalStateException("Esta caja no te pertenece.");
        }
        movementRepository.delete(movement);
    }

    // ---------- Summary ----------

    /**
     * Builds the day summary of a session: starting cash, sales collected by the
     * cashier during the session window, manual movements and the expected cash
     * (plus the counted cash/difference once the drawer was closed).
     */
    @Transactional(readOnly = true)
    public CashRegisterSummary buildSummary(CashRegisterSession session) {
        if (session == null) {
            return CashRegisterSummary.builder()
                    .initialAmount(BigDecimal.ZERO)
                    .totalSales(BigDecimal.ZERO)
                    .totalTips(BigDecimal.ZERO)
                    .salesByMethod(emptyByMethod())
                    .totalExpenses(BigDecimal.ZERO)
                    .totalIncomes(BigDecimal.ZERO)
                    .totalWithdrawals(BigDecimal.ZERO)
                    .cashSales(BigDecimal.ZERO)
                    .expectedCash(BigDecimal.ZERO)
                    .build();
        }

        LocalDateTime start = session.getOpenedAt();
        LocalDateTime end = session.getClosedAt() != null ? session.getClosedAt() : LocalDateTime.now(ZoneOffset.UTC);
        String cashierUsername = session.getCashier() != null ? session.getCashier().getUsername() : null;

        List<Order> orders = cashierUsername == null
                ? List.of()
                : orderRepository.findPaidByCollectorAndPaidAtRangeAndCompany(
                        cashierUsername, start, end, session.getCompany());

        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalTips = BigDecimal.ZERO;
        BigDecimal cashSales = BigDecimal.ZERO;
        Map<PaymentMethodType, BigDecimal> byMethod = emptyByMethod();

        for (Order order : orders) {
            BigDecimal orderTotal = order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO;
            totalSales = totalSales.add(orderTotal);
            totalTips = totalTips.add(order.getTip() != null ? order.getTip() : BigDecimal.ZERO);

            // A split bill carries one Payment per account, each with its own method.
            if (order.getPayments() != null && !order.getPayments().isEmpty()) {
                for (Payment payment : order.getPayments()) {
                    PaymentMethodType method = payment.getPaymentMethod();
                    BigDecimal value = payment.getTotal() != null ? payment.getTotal() : BigDecimal.ZERO;
                    addToMethod(byMethod, method, value);
                    if (method == PaymentMethodType.CASH) {
                        cashSales = cashSales.add(value);
                    }
                }
            } else {
                PaymentMethodType method = order.getPaymentMethod();
                if (method != null) {
                    addToMethod(byMethod, method, orderTotal);
                    if (method == PaymentMethodType.CASH) {
                        cashSales = cashSales.add(orderTotal);
                    }
                }
            }
        }

        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal totalIncomes = BigDecimal.ZERO;
        BigDecimal totalWithdrawals = BigDecimal.ZERO;
        for (CashRegisterMovement movement : getMovements(session)) {
            BigDecimal value = movement.getAmount() != null ? movement.getAmount() : BigDecimal.ZERO;
            switch (movement.getType()) {
                case EXPENSE -> totalExpenses = totalExpenses.add(value);
                case INCOME -> totalIncomes = totalIncomes.add(value);
                case WITHDRAWAL -> totalWithdrawals = totalWithdrawals.add(value);
            }
        }

        BigDecimal initial = session.getInitialAmount() != null ? session.getInitialAmount() : BigDecimal.ZERO;
        BigDecimal expectedCash = initial
                .add(cashSales)
                .add(totalIncomes)
                .subtract(totalExpenses)
                .subtract(totalWithdrawals)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal counted = session.getCountedAmount();
        BigDecimal difference = counted != null
                ? counted.subtract(expectedCash).setScale(2, RoundingMode.HALF_UP)
                : null;

        return CashRegisterSummary.builder()
                .initialAmount(initial)
                .totalSales(totalSales.setScale(2, RoundingMode.HALF_UP))
                .totalTips(totalTips.setScale(2, RoundingMode.HALF_UP))
                .salesCount(orders.size())
                .salesByMethod(byMethod)
                .totalExpenses(totalExpenses.setScale(2, RoundingMode.HALF_UP))
                .totalIncomes(totalIncomes.setScale(2, RoundingMode.HALF_UP))
                .totalWithdrawals(totalWithdrawals.setScale(2, RoundingMode.HALF_UP))
                .cashSales(cashSales.setScale(2, RoundingMode.HALF_UP))
                .expectedCash(expectedCash)
                .countedAmount(counted)
                .difference(difference)
                .build();
    }

    // ---------- Helpers ----------

    private Map<PaymentMethodType, BigDecimal> emptyByMethod() {
        Map<PaymentMethodType, BigDecimal> map = new LinkedHashMap<>();
        for (PaymentMethodType method : PaymentMethodType.values()) {
            map.put(method, BigDecimal.ZERO);
        }
        return map;
    }

    private void addToMethod(Map<PaymentMethodType, BigDecimal> map, PaymentMethodType method, BigDecimal value) {
        if (method == null) {
            return;
        }
        map.merge(method, value, BigDecimal::add);
    }

    private BigDecimal normalize(BigDecimal value, String label) {
        BigDecimal amount = value != null ? value : BigDecimal.ZERO;
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(label + " no puede ser negativo.");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException(label + " no puede ser mayor a $999,999.99.");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException(label + " solo permite hasta 2 decimales.");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
