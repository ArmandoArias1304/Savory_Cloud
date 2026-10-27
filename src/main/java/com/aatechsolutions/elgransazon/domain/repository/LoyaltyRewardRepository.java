package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.LoyaltyReward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoyaltyRewardRepository extends JpaRepository<LoyaltyReward, Long> {

    List<LoyaltyReward> findByCompanyAndActiveTrueOrderBySortOrderAscStampsCostAsc(Company company);

    List<LoyaltyReward> findByCompanyOrderBySortOrderAscStampsCostAsc(Company company);

    Optional<LoyaltyReward> findByIdAndCompany(Long id, Company company);

    long countByCompanyAndActiveTrue(Company company);
}
