package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.LoyaltyProgram;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoyaltyProgramRepository extends JpaRepository<LoyaltyProgram, Long> {

    Optional<LoyaltyProgram> findByCompany(Company company);

    Optional<LoyaltyProgram> findByCompany_IdCompany(Long companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM LoyaltyProgram p WHERE p.company = :company")
    Optional<LoyaltyProgram> findByCompanyForUpdate(@Param("company") Company company);

    List<LoyaltyProgram> findByStampCycleDeadlineIsNotNull();

    @Query("SELECT p.company.idCompany FROM LoyaltyProgram p WHERE p.stampCycleDeadline IS NOT NULL")
    List<Long> findCompanyIdsWithStampCycleDeadline();
}
