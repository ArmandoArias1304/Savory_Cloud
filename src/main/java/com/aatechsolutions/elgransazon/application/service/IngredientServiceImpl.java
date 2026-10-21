package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.ComplementIngredient;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemIngredient;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.domain.entity.IngredientCategory;
import com.aatechsolutions.elgransazon.domain.entity.IngredientStockHistory;
import com.aatechsolutions.elgransazon.domain.entity.Supplier;
import com.aatechsolutions.elgransazon.domain.repository.ComplementIngredientRepository;
import com.aatechsolutions.elgransazon.domain.repository.IngredientCategoryRepository;
import com.aatechsolutions.elgransazon.domain.repository.IngredientRepository;
import com.aatechsolutions.elgransazon.domain.repository.IngredientStockHistoryRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemIngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service implementation for Ingredient management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngredientServiceImpl implements IngredientService {

    private final IngredientRepository ingredientRepository;
    private final IngredientCategoryRepository categoryRepository;
    private final IngredientStockHistoryRepository stockHistoryRepository;
    private final ItemIngredientRepository itemIngredientRepository;
    private final ComplementIngredientRepository complementIngredientRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Ingredient> findAll() {
        Company company = CompanyContext.requireCurrentCompany();
        log.info("Finding all ingredients for company: {}", company.getIdCompany());
        return ingredientRepository.findByCompanyOrderByNameAsc(company);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Ingredient> findById(Long id) {
        log.info("Finding ingredient by id: {}", id);
        Company company = CompanyContext.requireCurrentCompany();
        return ingredientRepository.findByIdIngredientAndCompany(id, company);
    }

    @Override
    @Transactional
    public Ingredient create(Ingredient ingredient) {
        Company company = CompanyContext.requireCurrentCompany();
        log.info("Creating new ingredient: {} for company: {}", ingredient.getName(), company.getIdCompany());

        // Validate unique name within company
        if (ingredientRepository.existsByNameAndCompany(ingredient.getName(), company)) {
            log.error("Ingredient with name {} already exists for company {}", ingredient.getName(), company.getIdCompany());
            throw new IllegalArgumentException("Ya existe un ingrediente con el nombre: " + ingredient.getName());
        }

        // Validate category is required
        if (ingredient.getCategory() == null || ingredient.getCategory().getIdCategory() == null) {
            log.error("Category is required for ingredient");
            throw new IllegalArgumentException("Debe seleccionar una categoría válida");
        }

        // Validate category exists
        IngredientCategory category = categoryRepository.findById(ingredient.getCategory().getIdCategory())
                .orElseThrow(() -> {
                    log.error("Category not found with id: {}", ingredient.getCategory().getIdCategory());
                    return new IllegalArgumentException("La categoría seleccionada no existe");
                });

        ingredient.setCategory(category);
        ingredient.setCompany(company);

        Ingredient savedIngredient = ingredientRepository.save(ingredient);
        log.info("Ingredient created successfully with id: {}", savedIngredient.getIdIngredient());
        return savedIngredient;
    }

    @Override
    @Transactional
    public Ingredient update(Long id, Ingredient ingredientDetails) {
        Company company = CompanyContext.requireCurrentCompany();
        log.info("Updating ingredient with id: {} for company: {}", id, company.getIdCompany());

        Ingredient ingredient = ingredientRepository.findByIdIngredientAndCompany(id, company)
                .orElseThrow(() -> {
                    log.error("Ingredient not found with id: {} for company: {}", id, company.getIdCompany());
                    return new IllegalArgumentException("Ingrediente no encontrado con id: " + id);
                });

        // Validate unique name if changed (within company)
        if (!ingredient.getName().equals(ingredientDetails.getName()) &&
            ingredientRepository.existsByNameAndCompany(ingredientDetails.getName(), company)) {
            log.error("Ingredient with name {} already exists for company {}", ingredientDetails.getName(), company.getIdCompany());
            throw new IllegalArgumentException("Ya existe un ingrediente con el nombre: " + ingredientDetails.getName());
        }

        // Validate category
        if (ingredientDetails.getCategory() == null || ingredientDetails.getCategory().getIdCategory() == null) {
            log.error("Category is required for ingredient");
            throw new IllegalArgumentException("Debe seleccionar una categoría válida");
        }

        IngredientCategory category = categoryRepository.findById(ingredientDetails.getCategory().getIdCategory())
                .orElseThrow(() -> {
                    log.error("Category not found with id: {}", ingredientDetails.getCategory().getIdCategory());
                    return new IllegalArgumentException("La categoría seleccionada no existe");
                });

        // Update fields
        ingredient.setName(ingredientDetails.getName());
        ingredient.setDescription(ingredientDetails.getDescription());
        
        // Check if unit of measure is changing to UN (unidades) and current values have decimals
        boolean isChangingToUnidades = "UN".equals(ingredientDetails.getUnitOfMeasure()) && 
                                       !"UN".equals(ingredient.getUnitOfMeasure());
        boolean currentStockHasDecimals = ingredient.getCurrentStock() != null && 
                                         ingredient.getCurrentStock().stripTrailingZeros().scale() > 0;
        boolean maxStockHasDecimals = ingredient.getMaxStock() != null && 
                                     ingredient.getMaxStock().stripTrailingZeros().scale() > 0;
        
        // Allow updating currentStock if:
        // 1. New value is provided and greater than zero, OR
        // 2. Changing to UN and current stock has decimals (need to round)
        if ((ingredientDetails.getCurrentStock() != null && 
             ingredientDetails.getCurrentStock().compareTo(BigDecimal.ZERO) > 0) ||
            (isChangingToUnidades && currentStockHasDecimals)) {
            
            BigDecimal newCurrentStock = ingredientDetails.getCurrentStock();
            if (newCurrentStock != null && newCurrentStock.compareTo(BigDecimal.ZERO) > 0) {
                // Validate integer for UN unit
                if ("UN".equals(ingredientDetails.getUnitOfMeasure()) && 
                    newCurrentStock.stripTrailingZeros().scale() > 0) {
                    throw new IllegalArgumentException(
                        "Para unidades (UN), el stock actual debe ser un número entero. " +
                        "Valor recibido: " + newCurrentStock
                    );
                }
                ingredient.setCurrentStock(newCurrentStock);
            }
        }
        // Note: currentStock is typically managed through addStock() method
        
        ingredient.setMinStock(ingredientDetails.getMinStock());
        
        // Allow updating maxStock if:
        // 1. New value is provided and greater than zero, OR
        // 2. Changing to UN and max stock has decimals (need to round)
        if ((ingredientDetails.getMaxStock() != null && 
             ingredientDetails.getMaxStock().compareTo(BigDecimal.ZERO) > 0) ||
            (isChangingToUnidades && maxStockHasDecimals)) {
            
            BigDecimal newMaxStock = ingredientDetails.getMaxStock();
            if (newMaxStock != null && newMaxStock.compareTo(BigDecimal.ZERO) > 0) {
                // Validate integer for UN unit
                if ("UN".equals(ingredientDetails.getUnitOfMeasure()) && 
                    newMaxStock.stripTrailingZeros().scale() > 0) {
                    throw new IllegalArgumentException(
                        "Para unidades (UN), el stock máximo debe ser un número entero. " +
                        "Valor recibido: " + newMaxStock
                    );
                }
                ingredient.setMaxStock(newMaxStock);
            }
        }
        
        String previousUnit = ingredient.getUnitOfMeasure();
        ingredient.setUnitOfMeasure(ingredientDetails.getUnitOfMeasure());
        // costPerUnit is managed through addStock() method, preserve existing value
        // ingredient.setCostPerUnit(ingredientDetails.getCostPerUnit());
        ingredient.setCurrency(ingredientDetails.getCurrency());
        ingredient.setActive(ingredientDetails.getActive());
        ingredient.setCategory(category);

        Ingredient updatedIngredient = ingredientRepository.save(ingredient);

        String newUnit = updatedIngredient.getUnitOfMeasure();
        int repairedRecipeRows = reconcileRecipeUnits(updatedIngredient);
        if (previousUnit != null && !previousUnit.equalsIgnoreCase(newUnit)) {
            log.info("Unidad de medida del ingrediente '{}' cambiada de '{}' a '{}'; "
                            + "{} fila(s) de receta sincronizadas",
                    updatedIngredient.getName(), previousUnit, newUnit, repairedRecipeRows);
        } else if (repairedRecipeRows > 0) {
            log.info("Se sincronizaron {} fila(s) de receta que seguian con la unidad vieja del "
                            + "ingrediente '{}' ({})",
                    repairedRecipeRows, updatedIngredient.getName(), newUnit);
        }

        log.info("Ingredient updated successfully: {}", id);
        return updatedIngredient;
    }

    /**
     * Deja las recetas en sincronía con la unidad vigente del ingrediente.
     *
     * <p>Cada fila de receta (platillos y complementos) guarda su propia copia de la unidad y se
     * valida contra la del ingrediente al guardarse: si el ingrediente pasa de {@code UN} a
     * {@code G} y las filas se quedan con {@code UN}, cualquier edición posterior de esa receta
     * falla con "la unidad de medida no coincide". Este método corrige la copia de todas las filas
     * que no coincidan, y se ejecuta en cada guardado del ingrediente para que además repare las
     * recetas que quedaron desfasadas antes de que existiera este arreglo.</p>
     *
     * <p><strong>No convierte las cantidades.</strong> El sistema no conoce un factor de conversión
     * (cuántos gramos pesa una unidad), así que solo cambia la unidad; el usuario debe revisar y
     * ajustar las cantidades a mano.</p>
     *
     * @return cuántas filas de receta hubo que corregir
     */
    private int reconcileRecipeUnits(Ingredient ingredient) {
        String currentUnit = ingredient.getUnitOfMeasure();
        if (currentUnit == null || currentUnit.isBlank()) {
            // Sin unidad vigente no hay nada que sincronizar: la unidad de la fila es obligatoria.
            return 0;
        }

        Long ingredientId = ingredient.getIdIngredient();

        List<ItemIngredient> itemRecipeRows = itemIngredientRepository.findByIngredientId(ingredientId);
        List<ItemIngredient> staleItemRows = itemRecipeRows.stream()
                .filter(row -> !currentUnit.equalsIgnoreCase(row.getUnit()))
                .collect(Collectors.toList());
        if (!staleItemRows.isEmpty()) {
            staleItemRows.forEach(row -> row.setUnit(currentUnit));
            itemIngredientRepository.saveAll(staleItemRows);
        }

        List<ComplementIngredient> complementRecipeRows =
                complementIngredientRepository.findByIngredientIdIngredient(ingredientId);
        List<ComplementIngredient> staleComplementRows = complementRecipeRows.stream()
                .filter(row -> !currentUnit.equalsIgnoreCase(row.getUnit()))
                .collect(Collectors.toList());
        if (!staleComplementRows.isEmpty()) {
            staleComplementRows.forEach(row -> row.setUnit(currentUnit));
            complementIngredientRepository.saveAll(staleComplementRows);
        }

        return staleItemRows.size() + staleComplementRows.size();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Company company = CompanyContext.requireCurrentCompany();
        log.info("Deactivating ingredient with id: {} for company: {}", id, company.getIdCompany());

        Ingredient ingredient = ingredientRepository.findByIdIngredientAndCompany(id, company)
                .orElseThrow(() -> {
                    log.error("Ingredient not found with id: {} for company: {}", id, company.getIdCompany());
                    return new IllegalArgumentException("Ingrediente no encontrado con id: " + id);
                });

        // Set stock to zero when deactivating (this makes related items appear as "Sin Stock")
        ingredient.setCurrentStock(BigDecimal.ZERO);
        ingredient.setActive(false);
        ingredientRepository.save(ingredient);
        log.info("Ingredient deactivated and stock set to zero: {}", id);
    }

    @Override
    @Transactional
    public void activate(Long id) {
        log.info("Activating ingredient with id: {}", id);

        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Ingredient not found with id: {}", id);
                    return new IllegalArgumentException("Ingrediente no encontrado con id: " + id);
                });

        ingredient.setActive(true);
        ingredientRepository.save(ingredient);
        log.info("Ingredient activated successfully: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ingredient> searchWithAllFilters(String search, Long categoryId, Long supplierId, 
                                                 String sortBy, Boolean active) {
        log.info("Searching ingredients with filters - search: {}, categoryId: {}, supplierId: {}, sortBy: {}, active: {}",
                search, categoryId, supplierId, sortBy, active);

        // Normalize search string
        String normalizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        // Get current company for multi-tenant filtering
        Company company = CompanyContext.requireCurrentCompany();
        
        List<Ingredient> ingredients;

        // If supplier filter is provided, get ingredients for that supplier through category
        if (supplierId != null) {
            ingredients = ingredientRepository.findBySupplierAndCompany(supplierId, company);
            
            // Apply additional filters
            if (normalizedSearch != null) {
                final String searchLower = normalizedSearch.toLowerCase();
                ingredients = ingredients.stream()
                    .filter(i -> i.getName().toLowerCase().contains(searchLower) ||
                               (i.getDescription() != null && i.getDescription().toLowerCase().contains(searchLower)))
                    .collect(Collectors.toList());
            }
            
            if (categoryId != null) {
                ingredients = ingredients.stream()
                    .filter(i -> i.getCategory() != null && i.getCategory().getIdCategory().equals(categoryId))
                    .collect(Collectors.toList());
            }
            
            if (active != null) {
                ingredients = ingredients.stream()
                    .filter(i -> i.getActive().equals(active))
                    .collect(Collectors.toList());
            }
        } else {
            // Normal search without supplier filter
            ingredients = ingredientRepository.searchWithFiltersAndCompany(normalizedSearch, categoryId, active, company);
        }

        // Apply sorting based on sortBy parameter
        // ALWAYS sort by active status first (active ingredients first, then inactive)
        if (sortBy != null && !sortBy.isEmpty()) {
            if ("stock-asc".equals(sortBy)) {
                // Sort by active status first, then by stock ascending (menor a mayor)
                ingredients.sort(Comparator.comparing(Ingredient::getActive).reversed()
                    .thenComparing(Ingredient::getCurrentStock));
            } else if ("stock-desc".equals(sortBy)) {
                // Sort by active status first, then by stock descending (mayor a menor)
                ingredients.sort(Comparator.comparing(Ingredient::getActive).reversed()
                    .thenComparing(Ingredient::getCurrentStock, Comparator.reverseOrder()));
            } else {
                // Sort by active status first, then alphabetically by name
                ingredients.sort(Comparator.comparing(Ingredient::getActive).reversed()
                    .thenComparing(Ingredient::getName, String.CASE_INSENSITIVE_ORDER));
            }
        } else {
            // Default: Sort by active status first, then alphabetically by name
            ingredients.sort(Comparator.comparing(Ingredient::getActive).reversed()
                .thenComparing(Ingredient::getName, String.CASE_INSENSITIVE_ORDER));
        }

        log.info("Found {} ingredients with filters", ingredients.size());
        return ingredients;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ingredient> findByCategoryId(Long categoryId) {
        log.info("Finding ingredients for category ID: {}", categoryId);
        return ingredientRepository.findByCategoryIdCategoryOrderByNameAsc(categoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Supplier> getSuppliersForIngredient(Long ingredientId) {
        log.info("Getting suppliers for ingredient ID: {}", ingredientId);

        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> {
                    log.error("Ingredient not found with id: {}", ingredientId);
                    return new IllegalArgumentException("Ingrediente no encontrado con id: " + ingredientId);
                });

        if (ingredient.getCategory() == null) {
            log.warn("Ingredient {} has no category, returning empty supplier list", ingredientId);
            return Collections.emptyList();
        }

        List<Supplier> suppliers = new ArrayList<>(ingredient.getCategory().getSuppliers());
        suppliers.sort(Comparator.comparing(Supplier::getName, String.CASE_INSENSITIVE_ORDER));

        log.info("Found {} suppliers for ingredient {}", suppliers.size(), ingredientId);
        return suppliers;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ingredient> getLowStockIngredients() {
        log.info("Finding low stock ingredients");
        Company company = CompanyContext.requireCurrentCompany();
        return ingredientRepository.findLowStockIngredientsByCompany(company);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ingredient> getOutOfStockIngredients() {
        log.info("Finding out of stock ingredients");
        Company company = CompanyContext.requireCurrentCompany();
        return ingredientRepository.findOutOfStockIngredientsByCompany(company);
    }

    @Override
    @Transactional(readOnly = true)
    public long countLowStock() {
        Company company = CompanyContext.requireCurrentCompany();
        return ingredientRepository.countLowStockIngredientsByCompany(company);
    }

    @Override
    @Transactional(readOnly = true)
    public long countOutOfStock() {
        Company company = CompanyContext.requireCurrentCompany();
        return ingredientRepository.countOutOfStockIngredientsByCompany(company);
    }

    @Override
    @Transactional(readOnly = true)
    public long getActiveCount() {
        Company company = CompanyContext.requireCurrentCompany();
        return ingredientRepository.countByActiveAndCompany(true, company);
    }

    @Override
    @Transactional(readOnly = true)
    public long getInactiveCount() {
        Company company = CompanyContext.requireCurrentCompany();
        return ingredientRepository.countByActiveAndCompany(false, company);
    }

    @Override
    @Transactional
    public Ingredient addStock(Long ingredientId, BigDecimal quantityToAdd, BigDecimal costPerUnit, Employee addedBy) {
        log.info("Adding stock to ingredient ID: {} - Quantity: {} - Cost per unit: ${}", 
                ingredientId, quantityToAdd, costPerUnit);

        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> {
                    log.error("Ingredient not found with id: {}", ingredientId);
                    return new IllegalArgumentException("Ingrediente no encontrado con ID: " + ingredientId);
                });

        // Guardar stock anterior
        BigDecimal previousStock = ingredient.getCurrentStock();

        // Calcular nuevo stock
        BigDecimal newStock = previousStock.add(quantityToAdd).setScale(2, RoundingMode.HALF_UP);

        // Actualizar stock del ingrediente
        ingredient.setCurrentStock(newStock);
        ingredient.setUpdatedAt(LocalDateTime.now());

        Ingredient savedIngredient = ingredientRepository.save(ingredient);

        // Crear registro en el historial
        IngredientStockHistory history = IngredientStockHistory.builder()
                .ingredient(ingredient)
                .quantityAdded(quantityToAdd)
                .costPerUnit(costPerUnit)
                .previousStock(previousStock)
                .newStock(newStock)
                .addedAt(LocalDateTime.now())
                .addedBy(addedBy)
                .build();

        stockHistoryRepository.save(history);

        log.info("Stock agregado exitosamente al ingrediente '{}': +{} {} (Stock anterior: {}, Nuevo stock: {}, Costo total: ${})",
                ingredient.getName(), quantityToAdd, ingredient.getUnitOfMeasure(),
                previousStock, newStock, history.getTotalCost());

        return savedIngredient;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IngredientStockHistory> getStockHistory(Long ingredientId) {
        log.info("Getting stock history for ingredient ID: {}", ingredientId);

        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> {
                    log.error("Ingredient not found with id: {}", ingredientId);
                    return new IllegalArgumentException("Ingrediente no encontrado con ID: " + ingredientId);
                });

        return stockHistoryRepository.findByIngredientAndVoidedAtIsNullOrderByAddedAtDesc(ingredient);
    }

    @Override
    @Transactional
    public IngredientStockHistory updateStockHistory(Long ingredientId, Long historyId,
                                                     BigDecimal quantityAdded, BigDecimal costPerUnit,
                                                     Employee editedBy) {
        IngredientStockHistory record = requireHistoryEntry(ingredientId, historyId);
        if (record.isVoided()) {
            throw new IllegalStateException("Esta entrada ya fue anulada y no se puede editar.");
        }

        Ingredient ingredient = record.getIngredient();
        validateHistoryValues(ingredient, quantityAdded, costPerUnit);

        BigDecimal oldQuantity = record.getQuantityAdded();
        BigDecimal oldCost = record.getCostPerUnit();
        BigDecimal currentStock = ingredient.getCurrentStock() != null
                ? ingredient.getCurrentStock()
                : BigDecimal.ZERO;
        BigDecimal delta = quantityAdded.subtract(oldQuantity);
        BigDecimal newStock = currentStock.add(delta).setScale(2, RoundingMode.HALF_UP);
        assertNotNegative(newStock, ingredient, "corregir la cantidad");

        record.setQuantityAdded(quantityAdded);
        record.setCostPerUnit(costPerUnit);
        record.setTotalCost(quantityAdded.multiply(costPerUnit).setScale(2, RoundingMode.HALF_UP));
        // La foto "stock después" se recalcula con la cantidad corregida; las filas posteriores
        // conservan lo que se registró en su momento (el sistema no reescribe la cadena).
        if (record.getPreviousStock() != null) {
            record.setNewStock(record.getPreviousStock().add(quantityAdded)
                    .setScale(2, RoundingMode.HALF_UP));
        }
        record.setEditedAt(LocalDateTime.now());
        record.setEditedBy(editedBy);

        ingredient.setCurrentStock(newStock);
        ingredient.setUpdatedAt(LocalDateTime.now());
        ingredientRepository.save(ingredient);
        IngredientStockHistory saved = stockHistoryRepository.save(record);

        log.info("Stock history entry {} of ingredient '{}' corrected: quantity {} -> {}, cost {} -> {} (total {}, stock {})",
                historyId, ingredient.getName(), oldQuantity, quantityAdded,
                oldCost, costPerUnit, saved.getTotalCost(), newStock);
        return saved;
    }

    @Override
    @Transactional
    public IngredientStockHistory voidStockHistory(Long ingredientId, Long historyId,
                                                   String reason, Employee voidedBy) {
        IngredientStockHistory record = requireHistoryEntry(ingredientId, historyId);
        if (record.isVoided()) {
            throw new IllegalStateException("Esta entrada ya fue anulada.");
        }

        Ingredient ingredient = record.getIngredient();
        BigDecimal currentStock = ingredient.getCurrentStock() != null
                ? ingredient.getCurrentStock()
                : BigDecimal.ZERO;
        BigDecimal newStock = currentStock.subtract(record.getQuantityAdded())
                .setScale(2, RoundingMode.HALF_UP);
        assertNotNegative(newStock, ingredient, "anular esta entrada");

        ingredient.setCurrentStock(newStock);
        ingredient.setUpdatedAt(LocalDateTime.now());
        ingredientRepository.save(ingredient);

        record.setVoidedAt(LocalDateTime.now());
        record.setVoidedBy(voidedBy);
        record.setVoidReason(trimReason(reason));
        IngredientStockHistory saved = stockHistoryRepository.save(record);

        log.info("Stock history entry {} of ingredient '{}' voided: -{} {} (stock {})",
                historyId, ingredient.getName(), record.getQuantityAdded(),
                ingredient.getUnitOfMeasure(), newStock);
        return saved;
    }

    /**
     * Una entrada del historial de la empresa actual, validando que pertenezca al ingrediente
     * de la ruta para que nadie edite el historial de otro ingrediente cambiando el id.
     */
    private IngredientStockHistory requireHistoryEntry(Long ingredientId, Long historyId) {
        IngredientStockHistory record = stockHistoryRepository
                .findByIdAndIngredient_Company(historyId, CompanyContext.requireCurrentCompany())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Entrada del historial no encontrada: " + historyId));

        if (ingredientId == null || record.getIngredient() == null
                || !ingredientId.equals(record.getIngredient().getIdIngredient())) {
            throw new IllegalArgumentException(
                    "La entrada del historial no pertenece a este ingrediente.");
        }
        return record;
    }

    private void validateHistoryValues(Ingredient ingredient, BigDecimal quantity, BigDecimal costPerUnit) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
        }
        if (costPerUnit == null || costPerUnit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El costo por unidad debe ser mayor o igual a 0");
        }
        if ("UN".equals(ingredient.getUnitOfMeasure()) && quantity.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                    "Para unidades (UN), solo se permiten números enteros. No puede corregir a cantidades decimales de unidades.");
        }
    }

    /**
     * El inventario nunca puede quedar negativo: si la corrección lo dejaría así, la entrada ya
     * tenía consumo encima y hay que arreglar el dato de otra forma.
     */
    private void assertNotNegative(BigDecimal newStock, Ingredient ingredient, String action) {
        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(String.format(
                    "No se puede %s: el stock actual es %s %s y el resultado sería %s %s. " +
                            "Corrige la cantidad o registra la compra que falta.",
                    action,
                    ingredient.getCurrentStock() == null ? "0" : ingredient.getCurrentStock().toPlainString(),
                    ingredient.getUnitOfMeasure(),
                    newStock.toPlainString(), ingredient.getUnitOfMeasure()));
        }
    }

    private String trimReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalCostByIngredient(Long ingredientId) {
        log.info("Calculating total cost for ingredient ID: {}", ingredientId);

        BigDecimal totalCost = stockHistoryRepository.getTotalCostByIngredient(ingredientId);
        return totalCost != null ? totalCost : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalExpenses() {
        Company company = CompanyContext.requireCurrentCompany();
        log.info("Calculating total expenses for all ingredients (company: {})", company.getIdCompany());

        BigDecimal totalExpenses = stockHistoryRepository.getTotalExpensesByCompany(company);
        return totalExpenses != null ? totalExpenses : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getExpensesByCategory() {
        Company company = CompanyContext.requireCurrentCompany();
        log.info("Getting expenses grouped by category (company: {})", company.getIdCompany());

        List<Object[]> results = stockHistoryRepository.getExpensesByCategory(company);
        Map<String, BigDecimal> expenseMap = new java.util.LinkedHashMap<>();

        for (Object[] row : results) {
            String categoryName = (String) row[0];
            BigDecimal totalExpense = (BigDecimal) row[1];
            expenseMap.put(categoryName, totalExpense != null ? totalExpense : BigDecimal.ZERO);
        }

        return expenseMap;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Object[]> getExpenseDetailsByCategory(Long categoryId) {
        Company company = CompanyContext.requireCurrentCompany();
        log.info("Getting expense details for category ID: {} (company: {})", categoryId, company.getIdCompany());

        return stockHistoryRepository.getExpensesByIngredient(categoryId, company);
    }

    @Override
    @Transactional(readOnly = true)
    public int countRecipeUsages(Long ingredientId) {
        return itemIngredientRepository.findByIngredientId(ingredientId).size()
                + complementIngredientRepository.findByIngredientIdIngredient(ingredientId).size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ingredient> findAllActive() {
        Company company = CompanyContext.requireCurrentCompany();
        log.info("Finding all active ingredients for company: {}", company.getIdCompany());
        return ingredientRepository.findByActiveTrueAndCompany(company);
    }
}
