package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.ItemIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

/**
 * Repository for ItemIngredient entity
 */
@Repository
public interface ItemIngredientRepository extends JpaRepository<ItemIngredient, Long> {

    /**
     * Find all ingredients for a specific menu item
     */
    @Query("SELECT ii FROM ItemIngredient ii WHERE ii.itemMenu.idItemMenu = :itemMenuId ORDER BY ii.ingredient.name ASC")
    List<ItemIngredient> findByItemMenuId(@Param("itemMenuId") Long itemMenuId);

    /**
     * Todas las líneas de receta de la empresa, con su ingrediente ya cargado.
     *
     * <p>La pantalla de costeo necesita la receta de todos los platillos a la vez: pedirla
     * platillo por platillo dispararía una consulta por cada uno.</p>
     */
    @Query("SELECT ii FROM ItemIngredient ii JOIN FETCH ii.ingredient " +
           "WHERE ii.itemMenu.company = :company")
    List<ItemIngredient> findAllByCompany(@Param("company") Company company);

    @Query("SELECT ii FROM ItemIngredient ii JOIN FETCH ii.ingredient " +
           "WHERE ii.itemMenu.company = :company AND ii.itemMenu.idItemMenu IN :itemIds")
    List<ItemIngredient> findByMenuItemIds(@Param("company") Company company,
                                           @Param("itemIds") Collection<Long> itemIds);

    /**
     * Find all menu items that use a specific ingredient
     */
    @Query("SELECT ii FROM ItemIngredient ii WHERE ii.ingredient.idIngredient = :ingredientId ORDER BY ii.itemMenu.name ASC")
    List<ItemIngredient> findByIngredientId(@Param("ingredientId") Long ingredientId);

    /**
     * Find a specific ingredient in a menu item
     */
    @Query("SELECT ii FROM ItemIngredient ii WHERE ii.itemMenu.idItemMenu = :itemMenuId AND ii.ingredient.idIngredient = :ingredientId")
    Optional<ItemIngredient> findByItemMenuIdAndIngredientId(@Param("itemMenuId") Long itemMenuId, 
                                                               @Param("ingredientId") Long ingredientId);

    /**
     * Check if an ingredient is used in any menu item
     */
    @Query("SELECT CASE WHEN COUNT(ii) > 0 THEN true ELSE false END FROM ItemIngredient ii WHERE ii.ingredient.idIngredient = :ingredientId")
    boolean existsByIngredientId(@Param("ingredientId") Long ingredientId);

    /**
     * Count how many menu items use a specific ingredient
     */
    @Query("SELECT COUNT(ii) FROM ItemIngredient ii WHERE ii.ingredient.idIngredient = :ingredientId")
    long countByIngredientId(@Param("ingredientId") Long ingredientId);

    /**
     * Count how many ingredients are in a menu item's recipe
     */
    @Query("SELECT COUNT(ii) FROM ItemIngredient ii WHERE ii.itemMenu.idItemMenu = :itemMenuId")
    long countByItemMenuId(@Param("itemMenuId") Long itemMenuId);

    /**
     * Delete all ingredients for a specific menu item
     */
    @Query("DELETE FROM ItemIngredient ii WHERE ii.itemMenu.idItemMenu = :itemMenuId")
    void deleteByItemMenuId(@Param("itemMenuId") Long itemMenuId);

    /**
     * Find all menu items that are affected by low stock of a specific ingredient
     */
    @Query("SELECT DISTINCT ii.itemMenu FROM ItemIngredient ii " +
           "WHERE ii.ingredient.idIngredient = :ingredientId " +
           "AND ii.itemMenu.active = true")
    List<com.aatechsolutions.elgransazon.domain.entity.ItemMenu> findAffectedMenuItems(@Param("ingredientId") Long ingredientId);
}
