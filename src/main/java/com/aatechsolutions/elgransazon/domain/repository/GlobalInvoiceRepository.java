package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.GlobalInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for GlobalInvoice (factura global / público en general emissions).
 */
@Repository
public interface GlobalInvoiceRepository extends JpaRepository<GlobalInvoice, Long> {

    /**
     * History of global invoices for a company, most recent first.
     */
    List<GlobalInvoice> findByCompanyOrderByCreatedAtDesc(Company company);

    /**
     * Load a global invoice by id within the current company (tenant isolation).
     */
    Optional<GlobalInvoice> findByIdAndCompany(Long id, Company company);

    /**
     * Count global invoices emitted for the same period (used to build a unique folio).
     */
    long countByCompanyAndPeriodFromAndPeriodTo(Company company, java.time.LocalDate periodFrom, java.time.LocalDate periodTo);

    /**
     * Count global invoices created in a UTC date range (for the CFDI/timbre counter).
     */
    @Query("SELECT COUNT(g) FROM GlobalInvoice g " +
           "WHERE g.company = :company " +
           "  AND g.createdAt >= :startDate " +
           "  AND g.createdAt < :endDate")
    long countByCompanyAndCreatedAtRange(
            @Param("company") Company company,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count ALL global invoices emitted by a company (all-time total for the CFDI/timbre counter).
     */
    long countByCompany(Company company);
}