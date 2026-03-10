package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.LandingImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LandingImageRepository extends JpaRepository<LandingImage, Long> {

    List<LandingImage> findByCompany(Company company);

    List<LandingImage> findByCompanyAndSection(Company company, LandingImage.Section section);

    Optional<LandingImage> findByCompanyAndSectionAndPosition(Company company, LandingImage.Section section, Integer position);

    void deleteByCompanyAndSectionAndPosition(Company company, LandingImage.Section section, Integer position);
}
