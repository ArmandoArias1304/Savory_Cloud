package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Company entity
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    /**
     * Find company by slug (subdomain)
     */
    Optional<Company> findBySlug(String slug);

    /**
     * Find company by custom domain
     */
    Optional<Company> findByCustomDomain(String customDomain);

    /**
     * Find company by slug or custom domain
     * Used to identify company from request host
     */
    @Query("SELECT c FROM Company c WHERE c.slug = :identifier OR c.customDomain = :identifier")
    Optional<Company> findBySlugOrCustomDomain(@Param("identifier") String identifier);

    /**
     * Find active company by slug
     */
    Optional<Company> findBySlugAndActiveTrue(String slug);

    /**
     * Find active company by custom domain
     */
    Optional<Company> findByCustomDomainAndActiveTrue(String customDomain);

    /**
     * Find all active companies
     */
    List<Company> findByActiveTrue();

    /**
     * Find all companies ordered by name
     */
    List<Company> findAllByOrderByNameAsc();

    /**
     * Check if slug exists
     */
    boolean existsBySlug(String slug);

    /**
     * Check if custom domain exists
     */
    boolean existsByCustomDomain(String customDomain);

    /**
     * Check if slug exists excluding a specific company (for updates)
     */
    @Query("SELECT COUNT(c) > 0 FROM Company c WHERE c.slug = :slug AND c.idCompany <> :excludeId")
    boolean existsBySlugAndIdNot(@Param("slug") String slug, @Param("excludeId") Long excludeId);

    /**
     * Check if custom domain exists excluding a specific company (for updates)
     */
    @Query("SELECT COUNT(c) > 0 FROM Company c WHERE c.customDomain = :domain AND c.idCompany <> :excludeId")
    boolean existsByCustomDomainAndIdNot(@Param("domain") String domain, @Param("excludeId") Long excludeId);

    /**
     * Search companies by name or slug (for pagination)
     */
    org.springframework.data.domain.Page<Company> findByNameContainingIgnoreCaseOrSlugContainingIgnoreCase(
            String name, String slug, org.springframework.data.domain.Pageable pageable);

    /**
     * Count active companies
     */
    long countByActiveTrue();

    /**
     * Count inactive companies
     */
    long countByActiveFalse();
}
