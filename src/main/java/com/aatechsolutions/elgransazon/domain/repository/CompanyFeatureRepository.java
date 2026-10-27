package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.CompanyFeature;
import com.aatechsolutions.elgransazon.domain.entity.FeatureKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyFeatureRepository extends JpaRepository<CompanyFeature, Long> {

    Optional<CompanyFeature> findByCompanyAndFeatureKey(Company company, FeatureKey featureKey);

    List<CompanyFeature> findByCompanyOrderByFeatureKeyAsc(Company company);

    boolean existsByCompanyAndFeatureKey(Company company, FeatureKey featureKey);
}
