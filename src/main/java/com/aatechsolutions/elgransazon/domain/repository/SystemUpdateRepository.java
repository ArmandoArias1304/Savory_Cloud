package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.SystemUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemUpdateRepository extends JpaRepository<SystemUpdate, Long> {

    List<SystemUpdate> findAllByOrderByCreatedAtDescIdDesc();

    Optional<SystemUpdate> findTopByPublishedTrueOrderByPublishedAtDescIdDesc();

    /**
     * Full version history: every published update, newest first. Unpublished
     * drafts are left out because they are not visible to restaurant users.
     */
    List<SystemUpdate> findByPublishedTrueOrderByPublishedAtDescIdDesc();

    long countByPublishedTrue();

    boolean existsByVersion(String version);

    boolean existsByVersionAndIdNot(String version, Long id);
}
