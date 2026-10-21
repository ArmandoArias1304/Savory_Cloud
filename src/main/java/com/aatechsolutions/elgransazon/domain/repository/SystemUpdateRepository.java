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

    long countByPublishedTrue();

    boolean existsByVersion(String version);

    boolean existsByVersionAndIdNot(String version, Long id);
}
