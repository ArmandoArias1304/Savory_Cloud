package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.SystemUpdate;
import com.aatechsolutions.elgransazon.domain.entity.SystemUpdateRole;
import com.aatechsolutions.elgransazon.domain.entity.SystemUpdateView;
import com.aatechsolutions.elgransazon.domain.repository.SystemUpdateRepository;
import com.aatechsolutions.elgransazon.domain.repository.SystemUpdateViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SystemUpdateServiceImpl implements SystemUpdateService {

    private final SystemUpdateRepository updateRepository;
    private final SystemUpdateViewRepository viewRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SystemUpdate> findAll() {
        return updateRepository.findAllByOrderByCreatedAtDescIdDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SystemUpdate> findById(Long id) {
        return updateRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SystemUpdate> findLatestPublished() {
        return updateRepository.findTopByPublishedTrueOrderByPublishedAtDescIdDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SystemUpdate> findPendingFor(Long employeeId, Set<SystemUpdateRole> userRoles) {
        if (employeeId == null || userRoles == null || userRoles.isEmpty()) {
            return Optional.empty();
        }
        Optional<SystemUpdate> latest = findLatestPublished();
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        SystemUpdate update = latest.get();
        boolean targeted = update.getTargetRoles() != null
                && update.getTargetRoles().stream().anyMatch(userRoles::contains);
        if (!targeted) {
            return Optional.empty();
        }
        if (hasSeen(update.getId(), employeeId)) {
            return Optional.empty();
        }
        return Optional.of(update);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSeen(Long updateId, Long employeeId) {
        if (updateId == null || employeeId == null) {
            return false;
        }
        return viewRepository.existsByUpdateIdAndEmployeeId(updateId, employeeId);
    }

    @Override
    public void markSeen(Long updateId, Long employeeId) {
        if (updateId == null || employeeId == null) {
            return;
        }
        if (!updateRepository.existsById(updateId)) {
            return;
        }
        if (viewRepository.existsByUpdateIdAndEmployeeId(updateId, employeeId)) {
            return;
        }
        viewRepository.save(SystemUpdateView.builder()
                .updateId(updateId)
                .employeeId(employeeId)
                .viewedAt(LocalDateTime.now())
                .build());
    }

    @Override
    public SystemUpdate create(SystemUpdate update, String createdBy) {
        normalize(update);
        assertVersionAvailable(update.getVersion(), null);

        update.setId(null);
        update.setCreatedBy(createdBy);
        update.setCreatedAt(LocalDateTime.now());
        update.setUpdatedAt(null);
        if (Boolean.TRUE.equals(update.getPublished())) {
            update.setPublishedAt(LocalDateTime.now());
        } else {
            update.setPublishedAt(null);
        }

        SystemUpdate saved = updateRepository.save(update);
        log.info("System update created: version={}, published={}", saved.getVersion(), saved.getPublished());
        return saved;
    }

    @Override
    public SystemUpdate update(Long id, SystemUpdate incoming) {
        SystemUpdate existing = updateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Actualización no encontrada"));

        normalize(incoming);
        assertVersionAvailable(incoming.getVersion(), id);

        existing.setVersion(incoming.getVersion());
        existing.setTitle(incoming.getTitle());
        existing.setChanges(incoming.getChanges());
        existing.setTargetRoles(new HashSet<>(incoming.getTargetRoles()));

        boolean wasPublished = Boolean.TRUE.equals(existing.getPublished());
        boolean willBePublished = Boolean.TRUE.equals(incoming.getPublished());
        existing.setPublished(willBePublished);
        if (willBePublished && (!wasPublished || existing.getPublishedAt() == null)) {
            existing.setPublishedAt(LocalDateTime.now());
        }

        SystemUpdate saved = updateRepository.save(existing);
        log.info("System update updated: version={}, published={}", saved.getVersion(), saved.getPublished());
        return saved;
    }

    @Override
    public SystemUpdate togglePublished(Long id) {
        SystemUpdate update = updateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Actualización no encontrada"));
        boolean publish = !Boolean.TRUE.equals(update.getPublished());
        update.setPublished(publish);
        if (publish) {
            update.setPublishedAt(LocalDateTime.now());
        }
        return updateRepository.save(update);
    }

    @Override
    public void delete(Long id) {
        SystemUpdate update = updateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Actualización no encontrada"));
        viewRepository.deleteByUpdateId(id);
        updateRepository.delete(update);
        log.info("System update deleted: version={}", update.getVersion());
    }

    @Override
    @Transactional(readOnly = true)
    public long countAll() {
        return updateRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countPublished() {
        return updateRepository.countByPublishedTrue();
    }

    private void normalize(SystemUpdate update) {
        if (update.getVersion() != null) {
            update.setVersion(update.getVersion().trim());
        }
        if (update.getTitle() != null) {
            update.setTitle(update.getTitle().trim());
        }
        if (update.getChanges() != null) {
            update.setChanges(update.getChanges().trim());
        }
        Set<SystemUpdateRole> roles = update.getTargetRoles();
        update.setTargetRoles(roles == null ? new HashSet<>() : new HashSet<>(roles));
        if (update.getPublished() == null) {
            update.setPublished(false);
        }
    }

    private void assertVersionAvailable(String version, Long currentId) {
        if (version == null || version.isBlank()) {
            return;
        }
        boolean duplicate = currentId == null
                ? updateRepository.existsByVersion(version)
                : updateRepository.existsByVersionAndIdNot(version, currentId);
        if (duplicate) {
            throw new IllegalArgumentException("Ya existe una actualización con la versión " + version);
        }
    }
}
