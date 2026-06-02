package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Complement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Complement entity
 */
@Repository
public interface ComplementRepository extends JpaRepository<Complement, Long> {

    /**
     * Find complement by name
     */
    Optional<Complement> findByName(String name);

    /**
     * Find complement by name ignoring case
     */
    Optional<Complement> findByNameIgnoreCase(String name);

    /**
     * Check if a complement with the given name exists
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Find all active complements
     */
    List<Complement> findByActiveTrue();

    /**
     * Find all available complements (active and in stock)
     */
    List<Complement> findByActiveTrueAndAvailableTrue();

    /**
     * Find all active complements ordered by name
     */
    List<Complement> findByActiveTrueOrderByNameAsc();

    /**
     * Find complements by partial name match (for search)
     */
    List<Complement> findByNameContainingIgnoreCaseAndActiveTrue(String name);

    /**
     * Find complement with its ingredients loaded
     */
    @Query("SELECT c FROM Complement c LEFT JOIN FETCH c.ingredients WHERE c.idComplement = :id")
    Optional<Complement> findByIdWithIngredients(@Param("id") Long id);

    /**
     * Find complement with its ingredients loaded (filtered by company)
     */
    @Query("SELECT c FROM Complement c LEFT JOIN FETCH c.ingredients WHERE c.idComplement = :id AND c.company = :company")
    Optional<Complement> findByIdWithIngredientsAndCompany(@Param("id") Long id, @Param("company") Company company);

    /**
     * Find all complements with their ingredients loaded
     */
    @Query("SELECT DISTINCT c FROM Complement c LEFT JOIN FETCH c.ingredients WHERE c.active = true")
    List<Complement> findAllActiveWithIngredients();

    /**
     * Find all complements with their ingredients loaded (filtered by company)
     */
    @Query("SELECT DISTINCT c FROM Complement c LEFT JOIN FETCH c.ingredients WHERE c.active = true AND c.company = :company")
    List<Complement> findAllActiveWithIngredientsAndCompany(@Param("company") Company company);

    /**
     * Find all complements (active and inactive) with their ingredients loaded
     */
    @Query("SELECT DISTINCT c FROM Complement c LEFT JOIN FETCH c.ingredients")
    List<Complement> findAllWithIngredients();

    /**
     * Find all complements (active and inactive) with their ingredients loaded (filtered by company)
     */
    @Query("SELECT DISTINCT c FROM Complement c LEFT JOIN FETCH c.ingredients WHERE c.company = :company")
    List<Complement> findAllWithIngredientsAndCompany(@Param("company") Company company);

    /**
     * Find complements available for a specific item menu
     */
    @Query("SELECT c FROM Complement c " +
           "JOIN c.itemMenuComplements imc " +
           "WHERE imc.itemMenu.idItemMenu = :itemMenuId " +
           "AND imc.active = true " +
           "AND c.active = true " +
           "AND c.available = true " +
           "ORDER BY imc.displayOrder ASC")
    List<Complement> findAvailableComplementsForItemMenu(@Param("itemMenuId") Long itemMenuId);

    /**
     * Find sauces available for a specific item menu
     */
    @Query("SELECT c FROM Complement c " +
           "JOIN c.itemMenuComplements imc " +
           "WHERE imc.itemMenu.idItemMenu = :itemMenuId " +
           "AND imc.active = true " +
           "AND c.active = true " +
           "AND c.available = true " +
           "AND c.isSauce = true " +
           "ORDER BY imc.displayOrder ASC")
    List<Complement> findAvailableSaucesForItemMenu(@Param("itemMenuId") Long itemMenuId);

    /**
     * Find regular complements (non-sauces) available for a specific item menu
     */
    @Query("SELECT c FROM Complement c " +
           "JOIN c.itemMenuComplements imc " +
           "WHERE imc.itemMenu.idItemMenu = :itemMenuId " +
           "AND imc.active = true " +
           "AND c.active = true " +
           "AND c.available = true " +
           "AND c.isSauce = false " +
           "ORDER BY imc.displayOrder ASC")
    List<Complement> findAvailableRegularComplementsForItemMenu(@Param("itemMenuId") Long itemMenuId);

    /**
     * Find all active sauces
     */
    List<Complement> findByActiveTrueAndIsSauceTrue();

    /**
     * Find all active sauces by company
     */
    List<Complement> findByActiveTrueAndIsSauceTrueAndCompany(Company company);

    /**
     * Find all active regular complements (non-sauces)
     */
    List<Complement> findByActiveTrueAndIsSauceFalse();

    /**
     * Find specialities available for a specific item menu
     */
    @Query("SELECT c FROM Complement c " +
           "JOIN c.itemMenuComplements imc " +
           "WHERE imc.itemMenu.idItemMenu = :itemMenuId " +
           "AND imc.active = true " +
           "AND c.active = true " +
           "AND c.available = true " +
           "AND c.isSpeciality = true " +
           "ORDER BY imc.displayOrder ASC")
    List<Complement> findAvailableSpecialitiesForItemMenu(@Param("itemMenuId") Long itemMenuId);

    /**
     * Find all active specialities by company
     */
    List<Complement> findByActiveTrueAndIsSpecialityTrueAndCompany(Company company);

    /**
     * Count active complements
     */
    long countByActiveTrue();

    /**
     * Count available complements
     */
    long countByActiveTrueAndAvailableTrue();

    // ========== Multi-Tenant Methods (by Company) ==========

    /**
     * Find all complements by company
     */
    List<Complement> findByCompany(Company company);

    /**
     * Find complement by ID and company (for security validation)
     */
    Optional<Complement> findByIdComplementAndCompany(Long idComplement, Company company);

    /**
     * Find all active complements by company
     */
    List<Complement> findByActiveTrueAndCompany(Company company);

    /**
     * Find all available complements by company
     */
    List<Complement> findByActiveTrueAndAvailableTrueAndCompany(Company company);

    /**
     * Check if complement name exists for company
     */
    boolean existsByNameIgnoreCaseAndCompany(String name, Company company);

    /**
     * Count active complements by company
     */
    long countByActiveTrueAndCompany(Company company);

    // ========== Migration Helper Methods ==========

    /**
     * Find all complements without company (for data migration)
     */
    List<Complement> findByCompanyIsNull();
}
