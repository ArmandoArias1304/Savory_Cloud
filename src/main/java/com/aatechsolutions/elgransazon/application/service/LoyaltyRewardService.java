package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.application.dto.LoyaltyRewardForm;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenuComboItem;
import com.aatechsolutions.elgransazon.domain.entity.LoyaltyReward;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComboItemRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuRepository;
import com.aatechsolutions.elgransazon.domain.repository.LoyaltyRewardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD for the reward catalog shown to staff and customers.
 *
 * <p>Every operation is scoped to one company. The item always comes from the
 * company's menu, so a restaurant can never give away another restaurant's
 * product or invent a price.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyRewardService {

    private final LoyaltyRewardRepository rewardRepository;
    private final ItemMenuRepository itemMenuRepository;
    private final ItemMenuComboItemRepository comboItemRepository;

    @Transactional(readOnly = true)
    public List<LoyaltyReward> findActive(Company company) {
        return rewardRepository.findByCompanyAndActiveTrueOrderBySortOrderAscStampsCostAsc(company);
    }

    @Transactional(readOnly = true)
    public List<LoyaltyReward> findAll(Company company) {
        return rewardRepository.findByCompanyOrderBySortOrderAscStampsCostAsc(company);
    }

    @Transactional(readOnly = true)
    public LoyaltyReward require(Company company, Long rewardId) {
        return rewardRepository.findByIdAndCompany(rewardId, company)
                .orElseThrow(() -> new IllegalArgumentException("Recompensa no encontrada"));
    }

    /**
     * Sellable items the admin can choose as a reward: individual products and combos.
     * Internal production items stay out because the repository already excludes them.
     */
    @Transactional(readOnly = true)
    public List<ItemMenu> eligibleItems(Company company) {
        return itemMenuRepository.findByActiveTrueAndCompany(company).stream()
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .toList();
    }

    @Transactional
    public LoyaltyReward save(Company company, LoyaltyRewardForm form) {
        Long itemId = form.getItemMenuId();
        if (itemId == null) {
            throw new IllegalArgumentException("Selecciona el producto que se regalará");
        }

        ItemMenu item = itemMenuRepository.findByIdItemMenuAndCompany(itemId, company)
                .orElseThrow(() -> new IllegalArgumentException("El producto seleccionado no pertenece a este restaurante"));

        if (!Boolean.TRUE.equals(item.getActive())) {
            throw new IllegalArgumentException("El producto seleccionado está inactivo");
        }
        if (Boolean.TRUE.equals(item.getInternalOnly())) {
            throw new IllegalArgumentException("Un producto de producción interna no puede regalarse");
        }
        // A combo is a valid reward as long as it has active children to send to the
        // kitchen/barista/parrilla; the price of the whole combo is waived at redemption.
        if (Boolean.TRUE.equals(item.getIsCombo())) {
            List<ItemMenuComboItem> children = comboItemRepository
                    .findByComboMenuIdItemMenuOrderByDisplayOrderAsc(item.getIdItemMenu());
            if (children.isEmpty()) {
                throw new IllegalArgumentException(
                        "El combo seleccionado no tiene artículos configurados. Agrégalos en el menú antes de regalarlo.");
            }
            for (ItemMenuComboItem child : children) {
                ItemMenu childItem = child.getChildMenu();
                if (childItem == null || !Boolean.TRUE.equals(childItem.getActive())) {
                    String childName = childItem != null ? childItem.getName() : "sin nombre";
                    throw new IllegalArgumentException(
                            "El combo incluye '" + childName
                                    + "', que está desactivado. Actívalo o retíralo del combo antes de regalarlo.");
                }
            }
        }
        if (form.getStampsCost() == null || form.getStampsCost() < 1 || form.getStampsCost() > 100) {
            throw new IllegalArgumentException("La recompensa debe costar entre 1 y 100 sellos");
        }
        if (form.getFreeQuantity() == null || form.getFreeQuantity() < 1 || form.getFreeQuantity() > 20) {
            throw new IllegalArgumentException("La recompensa debe incluir entre 1 y 20 artículos");
        }

        LoyaltyReward reward = form.getId() != null
                ? rewardRepository.findByIdAndCompany(form.getId(), company)
                    .orElseThrow(() -> new IllegalArgumentException("Recompensa no encontrada"))
                : LoyaltyReward.builder().company(company).build();

        reward.setItemMenu(item);
        reward.setName(form.getName() != null ? form.getName().trim() : null);
        reward.setDescription(trimToNull(form.getDescription()));
        reward.setStampsCost(form.getStampsCost());
        reward.setFreeQuantity(form.getFreeQuantity());
        reward.setSortOrder(form.getSortOrder() != null ? form.getSortOrder() : 0);
        reward.setActive(form.getActive() == null || form.getActive());

        LoyaltyReward saved = rewardRepository.save(reward);
        log.info("Loyalty reward '{}' saved for company {}", saved.getName(), company.getIdCompany());
        return saved;
    }

    @Transactional
    public LoyaltyReward setActive(Company company, Long rewardId, boolean active) {
        LoyaltyReward reward = require(company, rewardId);
        reward.setActive(active);
        return rewardRepository.save(reward);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
