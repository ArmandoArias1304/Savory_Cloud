package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.FacturamaConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FacturamaConfigRepository extends JpaRepository<FacturamaConfig, Long> {

    Optional<FacturamaConfig> findByCompany(Company company);

    boolean existsByCompany(Company company);

    Optional<FacturamaConfig> findByCompanyIdCompany(Long companyId);
}
