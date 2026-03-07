package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.IngredientStockHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IngredientStockHistoryRepository extends JpaRepository<IngredientStockHistory, Long> {

    /**
     * Obtener historial de un ingrediente específico ordenado por fecha descendente
     */
    List<IngredientStockHistory> findByIngredientOrderByAddedAtDesc(Ingredient ingredient);

    /**
     * Obtener historial por rango de fechas
     */
    @Query("SELECT h FROM IngredientStockHistory h WHERE h.addedAt BETWEEN :startDate AND :endDate ORDER BY h.addedAt DESC")
    List<IngredientStockHistory> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Calcular gasto total por ingrediente
     */
    @Query("SELECT SUM(h.totalCost) FROM IngredientStockHistory h WHERE h.ingredient.id = :ingredientId")
    BigDecimal getTotalCostByIngredient(@Param("ingredientId") Long ingredientId);

    /**
     * Calcular gasto total por ingrediente en un rango de fechas
     */
    @Query("SELECT SUM(h.totalCost) FROM IngredientStockHistory h WHERE h.ingredient.id = :ingredientId " +
            "AND h.addedAt BETWEEN :startDate AND :endDate")
    BigDecimal getTotalCostByIngredientAndDateRange(
            @Param("ingredientId") Long ingredientId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Calcular gasto total de todos los ingredientes
     */
    @Query("SELECT SUM(h.totalCost) FROM IngredientStockHistory h")
    BigDecimal getTotalExpenses();

    /**
     * Calcular gasto total en un rango de fechas
     */
    @Query("SELECT SUM(h.totalCost) FROM IngredientStockHistory h WHERE h.addedAt BETWEEN :startDate AND :endDate")
    BigDecimal getTotalExpensesByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Obtener gastos agrupados por categoría
     */
    @Query("SELECT h.ingredient.category.name, SUM(h.totalCost) FROM IngredientStockHistory h " +
            "GROUP BY h.ingredient.category.id, h.ingredient.category.name ORDER BY SUM(h.totalCost) DESC")
    List<Object[]> getExpensesByCategory();

    /**
     * Obtener gastos agrupados por ingrediente (todos)
     */
    @Query("SELECT h.ingredient.name, SUM(h.totalCost), h.ingredient.unitOfMeasure " +
            "FROM IngredientStockHistory h GROUP BY h.ingredient.id, h.ingredient.name, h.ingredient.unitOfMeasure " +
            "ORDER BY SUM(h.totalCost) DESC")
    List<Object[]> getAllExpensesByIngredient();

    /**
     * Obtener gastos agrupados por ingrediente con rango de fechas
     */
    @Query("SELECT h.ingredient.name, SUM(h.totalCost), h.ingredient.unitOfMeasure, h.ingredient.category.name " +
            "FROM IngredientStockHistory h WHERE h.addedAt BETWEEN :startDate AND :endDate " +
            "GROUP BY h.ingredient.id, h.ingredient.name, h.ingredient.unitOfMeasure, h.ingredient.category.name " +
            "ORDER BY SUM(h.totalCost) DESC")
    List<Object[]> getExpensesByIngredientAndDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Obtener gastos detallados por categoría específica
     * Returns: [ingredientName, totalQuantityPurchased, totalExpense]
     */
    @Query("SELECT h.ingredient.name, SUM(h.quantityAdded), COALESCE(SUM(h.totalCost), 0) " +
            "FROM IngredientStockHistory h " +
            "WHERE h.ingredient.category.idCategory = :categoryId " +
            "GROUP BY h.ingredient.idIngredient, h.ingredient.name " +
            "ORDER BY SUM(h.totalCost) DESC")
    List<Object[]> getExpensesByIngredient(@Param("categoryId") Long categoryId);
}
