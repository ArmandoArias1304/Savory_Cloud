package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemIngredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.repository.CategoryRepository;
import com.aatechsolutions.elgransazon.domain.repository.IngredientRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemIngredientRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuAvailabilityRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComboItemRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComplementRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuRepository;
import com.aatechsolutions.elgransazon.domain.repository.OrderDetailRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La unidad de cada fila de la receta sale del ingrediente, no del formulario.
 *
 * <p>El formulario de platillos ya no la envía (la receta viaja como JSON con sólo el id del
 * ingrediente y la cantidad), así que el servicio la completa al guardar; y si un ingrediente
 * cambió de unidad (por ejemplo de G a KG) la receta se queda con la unidad vigente en lugar de
 * guardar una que ya no existe en el inventario.</p>
 */
class ItemMenuRecipeUnitTest {

    private ItemMenuRepository itemMenuRepository;
    private ItemIngredientRepository itemIngredientRepository;
    private IngredientRepository ingredientRepository;
    private CategoryRepository categoryRepository;
    private ItemMenuServiceImpl service;

    @BeforeEach
    void setUp() {
        itemMenuRepository = mock(ItemMenuRepository.class);
        itemIngredientRepository = mock(ItemIngredientRepository.class);
        ingredientRepository = mock(IngredientRepository.class);
        categoryRepository = mock(CategoryRepository.class);

        service = new ItemMenuServiceImpl(
                itemMenuRepository,
                itemIngredientRepository,
                ingredientRepository,
                categoryRepository,
                mock(ItemMenuAvailabilityRepository.class),
                mock(BusinessHoursService.class),
                mock(ItemMenuComboItemRepository.class),
                mock(OrderDetailRepository.class),
                mock(DateTimeService.class),
                mock(ItemMenuComplementRepository.class));

        CompanyContext.setCurrentCompany(Company.builder()
                .idCompany(2L)
                .slug("quinta")
                .name("Quinta El Paraíso")
                .build());

        when(itemMenuRepository.save(any(ItemMenu.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemIngredientRepository.save(any(ItemIngredient.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(itemIngredientRepository.findByItemMenuId(7L)).thenReturn(new ArrayList<>());
        when(ingredientRepository.findById(11L)).thenReturn(Optional.of(aguacate()));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("al actualizar la receta se completa la unidad con la del ingrediente")
    void updatingTheRecipeFillsTheMissingUnit() {
        when(itemMenuRepository.findByIdItemMenuAndCompany(any(), any())).thenReturn(Optional.of(item()));

        service.updateRecipe(7L, List.of(rowWithoutUnit(new BigDecimal("50"))));

        assertThat(savedRow().getUnit()).isEqualTo("G");
    }

    @Test
    @DisplayName("una unidad vieja se reemplaza por la vigente del ingrediente")
    void aStaleUnitIsReplaced() {
        when(itemMenuRepository.findByIdItemMenuAndCompany(any(), any())).thenReturn(Optional.of(item()));

        ItemIngredient row = rowWithoutUnit(new BigDecimal("50"));
        row.setUnit("ONZAS");

        service.updateRecipe(7L, List.of(row));

        assertThat(savedRow().getUnit()).isEqualTo("G");
    }

    @Test
    @DisplayName("al crear el platillo pasa lo mismo")
    void creatingAnItemAlsoFillsTheUnit() {
        when(itemMenuRepository.existsByNameAndCompany(any(), any())).thenReturn(false);
        when(itemMenuRepository.findByIdItemMenuAndCompany(any(), any())).thenReturn(Optional.of(item()));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(Category.builder()
                .idCategory(1L).name("Carnes").active(true).build()));

        ItemMenu itemMenu = ItemMenu.builder()
                .name("Parrillada")
                .price(new BigDecimal("100.00"))
                .category(Category.builder().idCategory(1L).build())
                .build();

        service.create(itemMenu, List.of(rowWithoutUnit(new BigDecimal("300"))));

        assertThat(savedRow().getUnit()).isEqualTo("G");
        assertThat(savedRow().getQuantity()).isEqualByComparingTo("300");
    }

    private ItemMenu item() {
        return ItemMenu.builder()
                .idItemMenu(7L)
                .name("Parrillada")
                .active(true)
                .available(true)
                .price(new BigDecimal("100.00"))
                .build();
    }

    private Ingredient aguacate() {
        return Ingredient.builder()
                .idIngredient(11L)
                .name("AGUACATE")
                .unitOfMeasure("G")
                .currentStock(new BigDecimal("500"))
                .build();
    }

    /** Fila como la arma el controlador desde el JSON: sólo ingrediente y cantidad. */
    private ItemIngredient rowWithoutUnit(BigDecimal quantity) {
        return ItemIngredient.builder()
                .ingredient(Ingredient.builder().idIngredient(11L).build())
                .quantity(quantity)
                .build();
    }

    private ItemIngredient savedRow() {
        ArgumentCaptor<ItemIngredient> captor = ArgumentCaptor.forClass(ItemIngredient.class);
        verify(itemIngredientRepository).save(captor.capture());
        return captor.getValue();
    }
}
