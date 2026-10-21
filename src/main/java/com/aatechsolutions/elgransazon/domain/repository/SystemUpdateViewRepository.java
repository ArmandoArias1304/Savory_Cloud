package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.SystemUpdateView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemUpdateViewRepository extends JpaRepository<SystemUpdateView, Long> {

    boolean existsByUpdateIdAndEmployeeId(Long updateId, Long employeeId);

    void deleteByUpdateId(Long updateId);
}
