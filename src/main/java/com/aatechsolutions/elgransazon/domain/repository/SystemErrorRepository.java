package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.SystemError;
import com.aatechsolutions.elgransazon.domain.entity.SystemError.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for SystemError entity
 */
@Repository
public interface SystemErrorRepository extends JpaRepository<SystemError, Long> {

    /**
     * Find unresolved errors
     */
    List<SystemError> findByResolvedFalseOrderByOccurredAtDesc();

    /**
     * Find errors by severity
     */
    List<SystemError> findBySeverityOrderByOccurredAtDesc(Severity severity);

    /**
     * Find recent errors (top N)
     */
    List<SystemError> findTop10ByOrderByOccurredAtDesc();

    /**
     * Find errors between dates
     */
    List<SystemError> findByOccurredAtBetweenOrderByOccurredAtDesc(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Count unresolved errors by severity
     */
    @Query("SELECT COUNT(e) FROM SystemError e WHERE e.resolved = false AND e.severity = :severity")
    long countUnresolvedBySeverity(@Param("severity") Severity severity);

    /**
     * Find critical unresolved errors
     */
    @Query("SELECT e FROM SystemError e WHERE e.resolved = false AND e.severity = 'CRITICAL' ORDER BY e.occurredAt DESC")
    List<SystemError> findCriticalUnresolvedErrors();

    /**
     * Count errors in the last N days
     */
    @Query("SELECT COUNT(e) FROM SystemError e WHERE e.occurredAt >= :since")
    long countErrorsSince(@Param("since") LocalDateTime since);
}
