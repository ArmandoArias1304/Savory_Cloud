package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.ItemIngredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.ItemPreparation;
import com.aatechsolutions.elgransazon.domain.entity.PreparationStatus;
import com.aatechsolutions.elgransazon.domain.repository.ItemIngredientRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemPreparationRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Internal production: buffet dishes and any other dish that is produced but never sold.
 *
 * <p>Stock handling reuses {@link IngredientStockService}, so a batch is discounted with the same
 * pessimistic lock + retry used by orders: two people registering at the same time cannot
 * oversell the inventory. Registering and cancelling are symmetric — same recipe, same
 * quantities — so a cancelled batch leaves the stock exactly as it was.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemPreparationServiceImpl implements ItemPreparationService {

    private final ItemPreparationRepository preparationRepository;
    private final ItemMenuRepository itemMenuRepository;
    private final ItemIngredientRepository itemIngredientRepository;
    private final IngredientStockService ingredientStockService;
    private final ItemMenuService itemMenuService;
    private final DateTimeService dateTimeService;

    // ========== Internal items ==========

    @Override
    @Transactional(readOnly = true)
    public List<ItemMenu> findInternalItems() {
        return itemMenuRepository.findByCompanyAndInternalOnlyTrueOrderByNameAsc(
                CompanyContext.requireCurrentCompany());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ItemMenu> findInternalItem(Long itemMenuId) {
        if (itemMenuId == null) {
            return Optional.empty();
        }
        return itemMenuRepository.findInternalByIdAndCompany(itemMenuId,
                CompanyContext.requireCurrentCompany());
    }

    @Override
    @Transactional
    public ItemMenu createInternalItem(ItemMenu item, List<ItemIngredient> recipe) {
        prepareForInternalUse(item);
        if (recipe == null || recipe.isEmpty()) {
            throw new IllegalArgumentException("Debe agregar al menos un ingrediente a la receta");
        }
        ItemMenu created = itemMenuService.create(item, recipe);
        log.info("Internal item '{}' created (ID: {})", created.getName(), created.getIdItemMenu());
        return created;
    }

    @Override
    @Transactional
    public ItemMenu updateInternalItem(Long itemMenuId, ItemMenu item, List<ItemIngredient> recipe) {
        ItemMenu existing = findInternalItem(itemMenuId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Platillo de producción interna no encontrado: " + itemMenuId));

        prepareForInternalUse(item);
        // The flag is owned by this module: an edit can never turn an internal item into a
        // sellable one (or vice versa) by accident.
        item.setInternalOnly(true);
        // This form does not manage images; keep whatever the item already had.
        item.setImageUrl(existing.getImageUrl());

        if (recipe == null || recipe.isEmpty()) {
            throw new IllegalArgumentException("Debe agregar al menos un ingrediente a la receta");
        }

        ItemMenu updated = itemMenuService.update(existing.getIdItemMenu(), item, recipe);
        log.info("Internal item '{}' updated (ID: {})", updated.getName(), updated.getIdItemMenu());
        return updated;
    }

    /**
     * Force the configuration an internal item must have, whatever the form sent.
     * It never requires preparation by chef/barista/parrillero (there is no order and no
     * kitchen screen), it is not a combo, and its price is zero because it is not sold.
     */
    private void prepareForInternalUse(ItemMenu item) {
        item.setInternalOnly(true);
        item.setRequiresIngredients(true);
        item.setIsCombo(false);
        item.setRequiresPreparation(false);
        item.setRequiresBaristaPreparation(false);
        item.setRequiresParrilleroPreparation(false);
        item.setDineInOnly(true);
        item.setMaxSauces(0);
        item.setMinSauces(0);
        item.setMaxSpecialities(0);
        item.setMinSpecialities(0);
        item.setHasCustomSchedule(false);
        item.setPrice(BigDecimal.ZERO);
        if (item.getActive() == null) {
            item.setActive(true);
        }
    }

    // ========== Production batches ==========

    @Override
    @Transactional(readOnly = true)
    public List<ItemPreparation> findRecentPreparations(int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        return preparationRepository.findRecentByCompany(
                CompanyContext.requireCurrentCompany(), PageRequest.of(0, size));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemPreparation> searchPreparations(String search, PreparationStatus status, int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        String term = search == null || search.isBlank() ? null : search.trim();
        return preparationRepository.searchByCompany(
                CompanyContext.requireCurrentCompany(), term, status, PageRequest.of(0, size));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemPreparation> findTodayPreparations() {
        LocalDate today = dateTimeService.todayLocal();
        return preparationRepository.findByCompanyAndPeriod(
                CompanyContext.requireCurrentCompany(),
                dateTimeService.startOfDayUtc(today),
                dateTimeService.endOfDayUtc(today));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemPreparation> findPreparationsForReport(LocalDate from, LocalDate to) {
        Company company = CompanyContext.requireCurrentCompany();
        LocalDate start = from != null ? from : to;
        LocalDate end = to != null ? to : from;
        if (start == null) {
            return preparationRepository.findRecentByCompany(company, Pageable.unpaged());
        }
        return preparationRepository.findByCompanyAndPeriod(
                company,
                dateTimeService.startOfDayUtc(start),
                dateTimeService.endOfDayUtc(end));
    }

    @Override
    @Transactional
    public ItemPreparation registerPreparation(Long itemMenuId, int quantity, Employee employee) {
        if (quantity < 1) {
            throw new IllegalArgumentException("La cantidad a preparar debe ser al menos 1");
        }

        Company company = CompanyContext.requireCurrentCompany();
        ItemMenu item = itemMenuRepository.findInternalByIdAndCompany(itemMenuId, company)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Platillo de producción interna no encontrado: " + itemMenuId));

        List<ItemIngredient> recipe = itemIngredientRepository.findByItemMenuId(item.getIdItemMenu());
        if (recipe.isEmpty()) {
            throw new IllegalStateException(
                    "El platillo '" + item.getName() + "' no tiene receta, no hay nada que descontar.");
        }

        assertEnoughStock(item, recipe, quantity);

        // Deductions run in their own REQUIRES_NEW transactions (pessimistic lock + retry), so
        // the outer transaction cannot undo them on its own: keep track of what was discounted
        // to be able to compensate if saving the batch fails afterwards.
        List<ItemIngredient> deducted = new ArrayList<>();
        try {
            for (ItemIngredient line : recipe) {
                ingredientStockService.deductStockWithRetry(
                        line.getIngredient().getIdIngredient(),
                        neededQuantity(line, quantity),
                        line.getUnit());
                deducted.add(line);
            }

            ItemPreparation preparation = ItemPreparation.builder()
                    .company(company)
                    .itemMenu(item)
                    .quantity(quantity)
                    .status(PreparationStatus.PREPARED)
                    .preparedAt(LocalDateTime.now())
                    .preparedBy(employee)
                    .stockReturned(false)
                    .build();

            ItemPreparation saved = preparationRepository.save(preparation);
            log.info("Registered internal production: {} x '{}' (batch {})",
                    quantity, item.getName(), saved.getIdPreparation());
            return saved;

        } catch (RuntimeException ex) {
            log.warn("Registering internal production of '{}' failed ({}). Returning {} already deducted ingredient(s).",
                    item.getName(), ex.getMessage(), deducted.size());
            returnStockBestEffort(deducted, quantity);
            throw ex;
        }
    }

    @Override
    @Transactional
    public ItemPreparation cancelPreparation(Long preparationId, Employee employee, String reason) {
        ItemPreparation preparation = preparationRepository
                .findByIdPreparationAndCompany(preparationId, CompanyContext.requireCurrentCompany())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Preparación no encontrada: " + preparationId));

        if (!preparation.canBeCancelled()) {
            throw new IllegalStateException("Esta preparación ya fue cancelada.");
        }

        List<ItemIngredient> recipe = itemIngredientRepository
                .findByItemMenuId(preparation.getItemMenu().getIdItemMenu());

        // Strict: if an ingredient cannot be returned we want the transaction to fail and the
        // batch to stay PREPARED, rather than marking it cancelled without its stock back.
        for (ItemIngredient line : recipe) {
            ingredientStockService.returnStockWithRetry(
                    line.getIngredient().getIdIngredient(),
                    neededQuantity(line, preparation.getQuantity()),
                    line.getUnit());
        }

        preparation.cancel(employee, reason);
        ItemPreparation saved = preparationRepository.save(preparation);
        log.info("Cancelled internal production batch {} ({} x '{}'); stock returned",
                preparationId, preparation.getQuantity(), preparation.getItemName());
        return saved;
    }

    // ========== Totals ==========

    @Override
    public BigDecimal totalEstimatedCost(Collection<ItemPreparation> preparations) {
        if (preparations == null) {
            return BigDecimal.ZERO;
        }
        return preparations.stream()
                .filter(ItemPreparation::isPrepared)
                .map(ItemPreparation::getEstimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public int totalProducedQuantity(Collection<ItemPreparation> preparations) {
        if (preparations == null) {
            return 0;
        }
        return preparations.stream().mapToInt(ItemPreparation::getActiveQuantity).sum();
    }

    // ========== Helpers ==========

    private BigDecimal neededQuantity(ItemIngredient line, int quantity) {
        return line.getQuantity()
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Fail fast with a message that names every ingredient that is short, instead of the first
     * one the deduction happens to hit. The authoritative check is still the locked deduction.
     */
    private void assertEnoughStock(ItemMenu item, List<ItemIngredient> recipe, int quantity) {
        List<String> missing = new ArrayList<>();
        for (ItemIngredient line : recipe) {
            BigDecimal needed = neededQuantity(line, quantity);
            BigDecimal available = line.getIngredient().getCurrentStock() != null
                    ? line.getIngredient().getCurrentStock()
                    : BigDecimal.ZERO;
            if (available.compareTo(needed) < 0) {
                missing.add(String.format("%s (faltan %s %s)",
                        line.getIngredientName(),
                        needed.subtract(available).stripTrailingZeros().toPlainString(),
                        line.getUnit()));
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "No hay stock suficiente para preparar " + quantity + " de '" + item.getName()
                            + "': " + String.join(", ", missing));
        }
    }

    /** Best-effort compensation: a failure here must never hide the original error. */
    private void returnStockBestEffort(List<ItemIngredient> lines, int quantity) {
        for (ItemIngredient line : lines) {
            try {
                ingredientStockService.returnStockWithRetry(
                        line.getIngredient().getIdIngredient(),
                        neededQuantity(line, quantity),
                        line.getUnit());
            } catch (RuntimeException e) {
                log.error("Could not compensate stock of '{}' after a failed production record: {}",
                        line.getIngredientName(), e.getMessage());
            }
        }
    }
}
