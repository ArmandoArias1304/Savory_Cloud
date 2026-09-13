package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Payment (per-person accounts of split bills).
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * All accounts of an order, ordered by account number.
     */
    List<Payment> findByOrderOrderByAccountNumberAsc(Order order);

    /**
     * All accounts of an order by order ID, ordered by account number.
     */
    @Query("SELECT p FROM Payment p WHERE p.order.idOrder = :orderId ORDER BY p.accountNumber ASC")
    List<Payment> findByOrderIdOrderByAccountNumberAsc(@Param("orderId") Long orderId);

    /**
     * Find a payment by its autofactura key within the current company
     * (for the public autofactura page).
     */
    @Query("SELECT p FROM Payment p " +
           "LEFT JOIN FETCH p.order " +
           "LEFT JOIN FETCH p.paymentDetails pd " +
           "LEFT JOIN FETCH pd.orderDetail " +
           "WHERE p.autofacturaKey = :key AND p.company = :company")
    Optional<Payment> findByAutofacturaKeyAndCompany(@Param("key") String key, @Param("company") Company company);

    /**
     * Find a payment by ID within the current company (tenant isolation).
     */
    Optional<Payment> findByIdPaymentAndCompany(Long idPayment, Company company);

    /**
     * Load a payment with its details and parent order (for ticket generation).
     */
    @Query("SELECT p FROM Payment p " +
           "LEFT JOIN FETCH p.order " +
           "LEFT JOIN FETCH p.paymentDetails pd " +
           "LEFT JOIN FETCH pd.orderDetail " +
           "WHERE p.idPayment = :id")
    Optional<Payment> findByIdWithDetails(@Param("id") Long id);

    /**
     * Count split orders (orders with at least one Payment row) for a company.
     */
    long countByCompany(Company company);

    /**
     * Aggregate PAID tickets (split accounts) for a company within a date range (UTC),
     * broken down by whether the account was invoiced (has a Facturama CFDI) or not.
     *
     * Each split account counts as one ticket; normal orders (no Payment rows) are
     * counted via {@link OrderRepository#sumPaidOrdersByCompanyAndDateRange}.
     *
     * Filters by {@code Payment.paidAt} (authoritative per-account payment timestamp).
     *
     * Returns a single row: [paidCount, paidTotal, invoicedCount, invoicedTotal].
     */
    @Query("SELECT " +
           "  COUNT(p), " +
           "  COALESCE(SUM(p.total), 0), " +
           "  SUM(CASE WHEN p.facturamaCfdiId IS NOT NULL OR p.facturaGlobalCfdiId IS NOT NULL THEN 1 ELSE 0 END), " +
           "  COALESCE(SUM(CASE WHEN p.facturamaCfdiId IS NOT NULL OR p.facturaGlobalCfdiId IS NOT NULL THEN p.total ELSE 0 END), 0) " +
           "FROM Payment p " +
           "WHERE p.company = :company " +
           "  AND p.paidAt >= :startDate " +
           "  AND p.paidAt < :endDate")
    List<Object[]> sumPaidByCompanyAndDateRange(
            @Param("company") Company company,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * PAID split accounts (per-person tickets) still pending the global invoice
     * for a company within a paid date range (UTC): no individual CFDI and not yet
     * included in a previous global invoice.
     *
     * Each account becomes one concept of the factura global (público en general),
     * per regla 2.7.1.21 RMF.
     */
    @Query("SELECT p FROM Payment p " +
           "WHERE p.company = :company " +
           "  AND p.paidAt >= :startDate " +
           "  AND p.paidAt < :endDate " +
           "  AND p.facturamaCfdiId IS NULL " +
           "  AND p.facturaGlobalCfdiId IS NULL " +
           "ORDER BY p.paidAt ASC")
    List<Payment> findPaidPendingGlobalInvoiceByDateRange(
            @Param("company") Company company,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count invoiced accounts (per-person tickets with CFDI) by company.
     * Used together with the order-level count so split invoices (saved on the
     * Payment, not the Order) are included in the programmer's CFDI/timbre counter.
     */
    @Query("SELECT COUNT(p) FROM Payment p " +
           "WHERE p.company = :company AND p.facturamaCfdiCreatedAt IS NOT NULL")
    long countByCompanyAndFacturamaCfdiCreatedAtIsNotNull(@Param("company") Company company);

    /**
     * Count invoiced accounts (per-person tickets with CFDI) by company and
     * CFDI creation date range (UTC).
     */
    @Query("SELECT COUNT(p) FROM Payment p " +
           "WHERE p.company = :company " +
           "  AND p.facturamaCfdiCreatedAt IS NOT NULL " +
           "  AND p.facturamaCfdiCreatedAt >= :startDate " +
           "  AND p.facturamaCfdiCreatedAt < :endDate")
    long countCfdisByCompanyAndDateRange(
            @Param("company") Company company,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}