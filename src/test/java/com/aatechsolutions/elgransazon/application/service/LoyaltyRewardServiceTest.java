package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.application.dto.LoyaltyRewardForm;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenuComboItem;
import com.aatechsolutions.elgransazon.domain.entity.LoyaltyReward;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComboItemRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuRepository;
import com.aatechsolutions.elgransazon.domain.repository.LoyaltyRewardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reward catalog: combos must be selectable and savable as rewards, just like an
 * individual product, as long as they have active children to send to the kitchen.
 */
@ExtendWith(MockitoExtension.class)
class LoyaltyRewardServiceTest {

    @Mock private LoyaltyRewardRepository rewardRepository;
    @Mock private ItemMenuRepository itemMenuRepository;
    @Mock private ItemMenuComboItemRepository comboItemRepository;

    private LoyaltyRewardService service;
    private Company company;

    @BeforeEach
    void setUp() {
        service = new LoyaltyRewardService(rewardRepository, itemMenuRepository, comboItemRepository);
        company = Company.builder().idCompany(1L).name("Test").build();
    }

    private ItemMenu item(long id, String name, boolean combo) {
        return ItemMenu.builder()
                .idItemMenu(id)
                .company(company)
                .name(name)
                .price(new BigDecimal("80.00"))
                .active(true)
                .available(true)
                .internalOnly(false)
                .isCombo(combo)
                .dineInOnly(false)
                .build();
    }

    @Test
    void eligibleItemsIncludeCombos() {
        ItemMenu coffee = item(1L, "Café americano", false);
        ItemMenu combo = item(2L, "Combo pareja", true);
        when(itemMenuRepository.findByActiveTrueAndCompany(company))
                .thenReturn(List.of(coffee, combo));

        List<ItemMenu> items = service.eligibleItems(company);

        assertThat(items).containsExactly(coffee, combo);
    }

    @Test
    void savesAComboRewardWithActiveChildren() {
        ItemMenu combo = item(2L, "Combo pareja", true);
        ItemMenu burger = item(3L, "Hamburguesa", false);
        ItemMenuComboItem child = ItemMenuComboItem.builder()
                .comboMenu(combo).childMenu(burger).quantity(1).displayOrder(0).build();

        LoyaltyRewardForm form = new LoyaltyRewardForm();
        form.setItemMenuId(2L);
        form.setName("Combo gratis");
        form.setStampsCost(10);
        form.setFreeQuantity(1);
        form.setActive(true);

        when(itemMenuRepository.findByIdItemMenuAndCompany(2L, company)).thenReturn(Optional.of(combo));
        when(comboItemRepository.findByComboMenuIdItemMenuOrderByDisplayOrderAsc(2L))
                .thenReturn(List.of(child));
        when(rewardRepository.save(any(LoyaltyReward.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LoyaltyReward saved = service.save(company, form);

        assertThat(saved.getItemMenu()).isSameAs(combo);
        assertThat(saved.getName()).isEqualTo("Combo gratis");
        assertThat(saved.getStampsCost()).isEqualTo(10);
    }

    @Test
    void rejectsAComboWithoutConfiguredChildren() {
        ItemMenu combo = item(2L, "Combo vacío", true);

        LoyaltyRewardForm form = new LoyaltyRewardForm();
        form.setItemMenuId(2L);
        form.setName("Combo gratis");
        form.setStampsCost(10);
        form.setFreeQuantity(1);
        form.setActive(true);

        when(itemMenuRepository.findByIdItemMenuAndCompany(2L, company)).thenReturn(Optional.of(combo));
        when(comboItemRepository.findByComboMenuIdItemMenuOrderByDisplayOrderAsc(2L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.save(company, form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tiene artículos configurados");

        verify(rewardRepository, never()).save(any());
    }
}
