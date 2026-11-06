package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.IngredientPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IngredientPurchaseRepository extends JpaRepository<IngredientPurchase, Long> {

    boolean existsByCompanyAndUuid(Company company, String uuid);

    Optional<IngredientPurchase> findByCompanyAndUuid(Company company, String uuid);

    Optional<IngredientPurchase> findByIdAndCompany(Long id, Company company);

    List<IngredientPurchase> findByCompanyOrderByIssuedAtDesc(Company company);
}