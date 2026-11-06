package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.IngredientWaste;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IngredientWasteRepository extends JpaRepository<IngredientWaste, Long> {

    List<IngredientWaste> findByIngredient_CompanyOrderByOccurredOnDescRecordedAtDesc(
            Company company, Pageable pageable);
}
