package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.ItemPreparation;
import com.aatechsolutions.elgransazon.domain.entity.PreparationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for internal production records.
 *
 * <p>Every query is scoped by company: two tenants can never see each other's batches.</p>
 */
@Repository
public interface ItemPreparationRepository extends JpaRepository<ItemPreparation, Long> {

    Optional<ItemPreparation> findByIdPreparationAndCompany(Long idPreparation, Company company);

    /**
     * Most recent batches of a company, newest first. Used by the history list.
     */
    @Query("SELECT p FROM ItemPreparation p " +
           "WHERE p.company = :company " +
           "ORDER BY p.preparedAt DESC, p.idPreparation DESC")
    List<ItemPreparation> findRecentByCompany(@Param("company") Company company, Pageable pageable);

    /**
     * Batches of a company filtered by text and/or status, newest first.
     *
     * <p>The text matches the dish name or whoever registered the batch, so the history can be
     * searched the same way it is read. Both filters are optional: {@code null} means "all".</p>
     */
    @Query("SELECT p FROM ItemPreparation p LEFT JOIN p.preparedBy e " +
           "WHERE p.company = :company " +
           "AND (:search IS NULL OR " +
           "LOWER(p.itemMenu.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(CONCAT(COALESCE(e.nombre, ''), ' ', COALESCE(e.apellido, ''))) " +
           "LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR p.status = :status) " +
           "ORDER BY p.preparedAt DESC, p.idPreparation DESC")
    List<ItemPreparation> searchByCompany(@Param("company") Company company,
                                          @Param("search") String search,
                                          @Param("status") PreparationStatus status,
                                          Pageable pageable);

    /**
     * Batches of a company produced inside a time range (both ends inclusive).
     * Used for the "today" summary.
     */
    @Query("SELECT p FROM ItemPreparation p " +
           "WHERE p.company = :company AND p.preparedAt BETWEEN :from AND :to " +
           "ORDER BY p.preparedAt DESC, p.idPreparation DESC")
    List<ItemPreparation> findByCompanyAndPeriod(@Param("company") Company company,
                                                 @Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);

    long countByCompany(Company company);
}
