package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.IngredientWaste;
import com.aatechsolutions.elgransazon.domain.entity.IngredientWasteReason;
import com.aatechsolutions.elgransazon.domain.repository.IngredientRepository;
import com.aatechsolutions.elgransazon.domain.repository.IngredientWasteRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngredientWasteServiceImpl implements IngredientWasteService {

    private final IngredientRepository ingredientRepository;
    private final IngredientWasteRepository wasteRepository;

    @Override
    @Transactional
    public IngredientWaste recordWaste(Long ingredientId,
                                       BigDecimal quantity,
                                       IngredientWasteReason reason,
                                       LocalDate occurredOn,
                                       String note,
                                       Employee recordedBy) {
        Company company = CompanyContext.requireCurrentCompany();
        if (ingredientId == null || quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0
                || reason == null || occurredOn == null) {
            throw new IllegalArgumentException("Ingrediente, cantidad, motivo y fecha son obligatorios.");
        }
        if (note != null && note.length() > 500) {
            throw new IllegalArgumentException("La nota no puede exceder 500 caracteres.");
        }

        Ingredient ingredient = ingredientRepository.findByIdWithLock(ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado."));
        if (ingredient.getCompany() == null
                || !company.getIdCompany().equals(ingredient.getCompany().getIdCompany())) {
            throw new IllegalArgumentException("El ingrediente no pertenece a esta empresa.");
        }
        if ("UN".equalsIgnoreCase(ingredient.getUnitOfMeasure())
                && quantity.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Para ingredientes medidos en UN, la cantidad debe ser entera.");
        }

        BigDecimal currentStock = ingredient.getCurrentStock() == null
                ? BigDecimal.ZERO : ingredient.getCurrentStock();
        BigDecimal normalizedQuantity = quantity.setScale(2, RoundingMode.HALF_UP);
        if (currentStock.compareTo(normalizedQuantity) < 0) {
            throw new IllegalStateException(String.format(
                    "Stock insuficiente de %s. Disponible: %s %s.",
                    ingredient.getName(), currentStock.stripTrailingZeros().toPlainString(),
                    ingredient.getUnitOfMeasure()));
        }

        BigDecimal unitCost = ingredient.getCostPerUnit() == null
                ? BigDecimal.ZERO : ingredient.getCostPerUnit();
        IngredientWaste waste = IngredientWaste.builder()
                .ingredient(ingredient)
                .quantity(normalizedQuantity)
                .reason(reason)
                .occurredOn(occurredOn)
                .note(note == null || note.isBlank() ? null : note.trim())
                .unitCostSnapshot(unitCost)
                .totalCost(normalizedQuantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP))
                .recordedBy(recordedBy)
                .recordedAt(LocalDateTime.now())
                .build();

        ingredient.setCurrentStock(currentStock.subtract(normalizedQuantity).setScale(2, RoundingMode.HALF_UP));
        ingredient.setUpdatedAt(LocalDateTime.now());
        wasteRepository.save(waste);
        ingredientRepository.save(ingredient);

        log.info("Merma registrada: ingrediente {}, cantidad {} {}, motivo {}, costo {}",
                ingredient.getIdIngredient(), normalizedQuantity, ingredient.getUnitOfMeasure(),
                reason, waste.getTotalCost());
        return waste;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IngredientWaste> findRecent(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        return wasteRepository.findByIngredient_CompanyOrderByOccurredOnDescRecordedAtDesc(
                CompanyContext.requireCurrentCompany(), PageRequest.of(0, boundedLimit));
    }
}
