package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.SystemUpdate;
import com.aatechsolutions.elgransazon.domain.entity.SystemUpdateRole;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Business logic for global system update announcements.
 */
public interface SystemUpdateService {

    List<SystemUpdate> findAll();

    Optional<SystemUpdate> findById(Long id);

    /** Latest published update, used by every version button. */
    Optional<SystemUpdate> findLatestPublished();

    /** Every published update, newest first; feeds the version history page. */
    List<SystemUpdate> findAllPublished();

    /**
     * Latest published update that should pop up automatically for this employee:
     * it must target one of their roles and not have been seen yet.
     */
    Optional<SystemUpdate> findPendingFor(Long employeeId, Set<SystemUpdateRole> userRoles);

    boolean hasSeen(Long updateId, Long employeeId);

    void markSeen(Long updateId, Long employeeId);

    SystemUpdate create(SystemUpdate update, String createdBy);

    SystemUpdate update(Long id, SystemUpdate update);

    SystemUpdate togglePublished(Long id);

    void delete(Long id);

    long countAll();

    long countPublished();
}
