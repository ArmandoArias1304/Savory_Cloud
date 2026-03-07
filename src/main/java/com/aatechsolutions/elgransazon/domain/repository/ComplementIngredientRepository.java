package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.ComplementIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ComplementIngredient entity
 */
@Repository
public interface ComplementIngredientRepository extends JpaRepository<ComplementIngredient, Long> {

    /**
     * Find all ingredients for a specific complement
     */
    List<ComplementIngredient> findByComplementIdComplement(Long complementId);

    /**
     * Find all complements that use a specific ingredient
     */
    List<ComplementIngredient> findByIngredientIdIngredient(Long ingredientId);

    /**
     * Check if a complement already has a specific ingredient
     */
    boolean existsByComplementIdComplementAndIngredientIdIngredient(Long complementId, Long ingredientId);

    /**
     * Find specific complement-ingredient relationship
     */
    Optional<ComplementIngredient> findByComplementIdComplementAndIngredientIdIngredient(Long complementId, Long ingredientId);

    /**
     * Delete all ingredients for a complement
     */
    void deleteByComplementIdComplement(Long complementId);

    /**
     * Find complement ingredients with ingredient data loaded
     */
    @Query("SELECT ci FROM ComplementIngredient ci " +
           "JOIN FETCH ci.ingredient " +
           "WHERE ci.complement.idComplement = :complementId")
    List<ComplementIngredient> findByComplementIdWithIngredient(@Param("complementId") Long complementId);

    /**
     * Count ingredients in a complement
     */
    long countByComplementIdComplement(Long complementId);
}
