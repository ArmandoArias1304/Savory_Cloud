package com.aatechsolutions.elgransazon.application.service;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Receta de una variante de tamaño: se pregunta si adopta la del item padre.
 *
 * <p>Adoptarla deja en la variante una copia propia, independiente de la del padre, y la marca como
 * que sí requiere ingredientes. No adoptarla la deja sin receta y sin descuento de inventario — una
 * variante con la bandera encendida y la lista de ingredientes vacía parecería llevar receta sin que
 * nunca se pudiera descontar nada. Y adoptar nunca debe pisar una receta que la variante ya tenía.</p>
 */
class SizeVariantRecipeAdoptionTest {

    private static final Long PARENT_ID = 1L;
    private static final Long VARIANT_ID = 2L;

    private ItemMenuRepository itemMenuRepository;
    private ItemIngredientRepository itemIngredientRepository;
    private ItemMenuServiceImpl service;

    @BeforeEach
    void setUp() {
        itemMenuRepository = mock(ItemMenuRepository.class);
        itemIngredientRepository = mock(ItemIngredientRepository.class);

        service = new ItemMenuServiceImpl(
                itemMenuRepository,
                itemIngredientRepository,
                mock(IngredientRepository.class),
                mock(CategoryRepository.class),
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

        when(itemMenuRepository.save(any(ItemMenu.class))).thenAnswer(i -> i.getArgument(0));
        when(itemIngredientRepository.save(any(ItemIngredient.class))).thenAnswer(i -> i.getArgument(0));
        // Por defecto ningún item tiene receta; cada caso estiba la que necesita.
        when(itemIngredientRepository.findByItemMenuId(any())).thenReturn(new ArrayList<>());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private ItemMenu parentItem() {
        return ItemMenu.builder()
                .idItemMenu(PARENT_ID)
                .name("Pizza")
                .price(new BigDecimal("200.00"))
                .active(true)
                .requiresIngredients(true)
                .build();
    }

    private ItemMenu variantOn(ItemMenu parent) {
        return ItemMenu.builder()
                .idItemMenu(VARIANT_ID)
                .name("Pizza Grande")
                .sizeName("Grande")
                .parentItem(parent)
                .price(new BigDecimal("250.00"))
                .active(true)
                .requiresIngredients(false)
                .build();
    }

    private ItemIngredient line(long ingredientId, String quantity) {
        return ItemIngredient.builder()
                .ingredient(Ingredient.builder()
                        .idIngredient(ingredientId)
                        .name("ING-" + ingredientId)
                        .unitOfMeasure("G")
                        .build())
                .quantity(new BigDecimal(quantity))
                .unit("G")
                .build();
    }

    /** Una variante con su propio id, para armar padres con varias. */
    private ItemMenu variantWithId(ItemMenu parent, long id, String sizeName) {
        return ItemMenu.builder()
                .idItemMenu(id)
                .name("Pizza " + sizeName)
                .sizeName(sizeName)
                .parentItem(parent)
                .price(new BigDecimal("250.00"))
                .active(true)
                .requiresIngredients(false)
                .build();
    }

    private void parentHasVariants(ItemMenu parent, ItemMenu... variants) {
        when(itemMenuRepository.findByIdItemMenuAndCompany(eq(PARENT_ID), any()))
                .thenReturn(Optional.of(parent));
        when(itemMenuRepository.findSizeItemsByParentId(PARENT_ID))
                .thenReturn(new ArrayList<>(List.of(variants)));
    }

    private void parentRecipeIs(ItemIngredient... lines) {
        when(itemIngredientRepository.findByItemMenuId(PARENT_ID))
                .thenReturn(new ArrayList<>(List.of(lines)));
    }

    private void variantRecipeIs(ItemIngredient... lines) {
        when(itemIngredientRepository.findByItemMenuId(VARIANT_ID))
                .thenReturn(new ArrayList<>(List.of(lines)));
    }

    private void stubsForCreatingASize(ItemMenu parent) {
        when(itemMenuRepository.findByIdItemMenuAndCompany(eq(PARENT_ID), any()))
                .thenReturn(Optional.of(parent));
        when(itemMenuRepository.countSizeItemsByParentId(PARENT_ID)).thenReturn(0L);
        when(itemMenuRepository.existsByNameAndCompany(any(), any())).thenReturn(false);
    }

    private void stubsForLinkingASize(ItemMenu parent, ItemMenu child) {
        when(itemMenuRepository.findByIdItemMenuAndCompany(eq(PARENT_ID), any()))
                .thenReturn(Optional.of(parent));
        when(itemMenuRepository.findByIdItemMenuAndCompany(eq(VARIANT_ID), any()))
                .thenReturn(Optional.of(child));
        when(itemMenuRepository.countSizeItemsByParentId(PARENT_ID)).thenReturn(0L);
        when(itemMenuRepository.countSizeItemsByParentId(VARIANT_ID)).thenReturn(0L);
        when(itemMenuRepository.existsByNameAndCompany(any(), any())).thenReturn(false);
    }

    // ---------- Adoptar desde el formulario de la variante ----------

    @Test
    @DisplayName("adoptar copia cada línea de la receta del padre en la variante")
    void adoptingCopiesEveryLineOfTheParentsRecipe() {
        ItemMenu parent = parentItem();
        parentRecipeIs(line(11L, "500"), line(12L, "300"));
        ItemMenu variant = variantOn(parent);
        when(itemMenuRepository.findByIdItemMenuAndCompany(eq(VARIANT_ID), any()))
                .thenReturn(Optional.of(variant));

        service.adoptParentRecipe(VARIANT_ID);

        ArgumentCaptor<ItemIngredient> saved = ArgumentCaptor.forClass(ItemIngredient.class);
        verify(itemIngredientRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(ItemIngredient::getQuantity)
                .containsExactlyInAnyOrder(new BigDecimal("500"), new BigDecimal("300"));
        assertThat(saved.getAllValues())
                .allSatisfy(copy -> assertThat(copy.getItemMenu()).isSameAs(variant));
        assertThat(variant.getRequiresIngredients())
                .as("la variante con receta debe descontar inventario")
                .isTrue();
    }

    @Test
    @DisplayName("una variante que ya tiene receta propia no se sobrescribe")
    void aVariantWithItsOwnRecipeIsNeverOverwritten() {
        ItemMenu parent = parentItem();
        parentRecipeIs(line(11L, "500"));
        ItemMenu variant = variantOn(parent);
        when(itemMenuRepository.findByIdItemMenuAndCompany(eq(VARIANT_ID), any()))
                .thenReturn(Optional.of(variant));
        variantRecipeIs(line(13L, "42"));

        assertThatThrownBy(() -> service.adoptParentRecipe(VARIANT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya tiene receta propia");

        verify(itemIngredientRepository, never()).deleteAll(any());
        verify(itemIngredientRepository, never()).save(any(ItemIngredient.class));
    }

    @Test
    @DisplayName("si el padre no tiene receta no hay nada que adoptar")
    void nothingToAdoptWhenTheParentHasNoRecipe() {
        ItemMenu parent = parentItem();
        parentRecipeIs();
        ItemMenu variant = variantOn(parent);
        when(itemMenuRepository.findByIdItemMenuAndCompany(eq(VARIANT_ID), any()))
                .thenReturn(Optional.of(variant));

        assertThatThrownBy(() -> service.adoptParentRecipe(VARIANT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tiene receta que adoptar");
    }

    @Test
    @DisplayName("un item que no es variante de nadie no puede adoptar")
    void aPlainItemCannotAdopt() {
        ItemMenu plain = ItemMenu.builder().idItemMenu(VARIANT_ID).name("Sopa").build();
        when(itemMenuRepository.findByIdItemMenuAndCompany(eq(VARIANT_ID), any()))
                .thenReturn(Optional.of(plain));

        assertThatThrownBy(() -> service.adoptParentRecipe(VARIANT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no es una variante");
    }

    // ---------- Copiar la receta del padre a las variantes ----------

    @Test
    @DisplayName("la receta del padre se copia sólo a las variantes que no tienen la suya")
    void theParentsRecipeIsCopiedOnlyToTheVariantsWithoutOne() {
        ItemMenu parent = parentItem();
        parentRecipeIs(line(11L, "500"), line(12L, "300"));
        ItemMenu withoutRecipe = variantWithId(parent, VARIANT_ID, "Grande");
        ItemMenu withOwnRecipe = variantWithId(parent, 3L, "Familiar");
        parentHasVariants(parent, withoutRecipe, withOwnRecipe);
        when(itemIngredientRepository.findByItemMenuId(3L)).thenReturn(new ArrayList<>(List.of(line(13L, "42"))));

        int copied = service.propagateRecipeToSizeVariants(PARENT_ID);

        assertThat(copied).as("sólo la variante vacía recibe la copia").isEqualTo(1);
        ArgumentCaptor<ItemIngredient> saved = ArgumentCaptor.forClass(ItemIngredient.class);
        verify(itemIngredientRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(copy ->
                assertThat(copy.getItemMenu()).isSameAs(withoutRecipe));
        assertThat(saved.getAllValues())
                .extracting(ItemIngredient::getQuantity)
                .containsExactlyInAnyOrder(new BigDecimal("500"), new BigDecimal("300"));
        assertThat(withoutRecipe.getRequiresIngredients())
                .as("la variante que recibe la receta debe descontar inventario")
                .isTrue();
        assertThat(withOwnRecipe.getRequiresIngredients())
                .as("la variante con receta propia no se toca")
                .isFalse();
    }

    @Test
    @DisplayName("no se copia por encima de las variantes que ya traen receta")
    void nothingIsCopiedWhenEveryVariantAlreadyHasItsOwnRecipe() {
        ItemMenu parent = parentItem();
        parentRecipeIs(line(11L, "500"));
        ItemMenu grande = variantWithId(parent, VARIANT_ID, "Grande");
        ItemMenu familiar = variantWithId(parent, 3L, "Familiar");
        parentHasVariants(parent, grande, familiar);
        when(itemIngredientRepository.findByItemMenuId(VARIANT_ID))
                .thenReturn(new ArrayList<>(List.of(line(13L, "42"))));
        when(itemIngredientRepository.findByItemMenuId(3L))
                .thenReturn(new ArrayList<>(List.of(line(13L, "84"))));

        assertThat(service.propagateRecipeToSizeVariants(PARENT_ID)).isZero();
        assertThat(service.countSizeVariantsWithoutRecipe(PARENT_ID)).isZero();
        verify(itemIngredientRepository, never()).save(any(ItemIngredient.class));
        verify(itemIngredientRepository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("se cuentan sólo las variantes sin receta propia")
    void onlyVariantsWithoutTheirOwnRecipeAreCounted() {
        ItemMenu parent = parentItem();
        parentHasVariants(parent, variantWithId(parent, VARIANT_ID, "Grande"),
                variantWithId(parent, 3L, "Familiar"));
        when(itemIngredientRepository.findByItemMenuId(VARIANT_ID))
                .thenReturn(new ArrayList<>(List.of(line(13L, "42"))));

        assertThat(service.countSizeVariantsWithoutRecipe(PARENT_ID))
                .as("de las dos variantes, sólo la vacía se contaría")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("un padre sin receta no tiene nada que copiar a sus variantes")
    void aParentWithoutRecipeHasNothingToPropagate() {
        ItemMenu parent = parentItem();
        parentRecipeIs();
        parentHasVariants(parent, variantWithId(parent, VARIANT_ID, "Grande"));

        assertThatThrownBy(() -> service.propagateRecipeToSizeVariants(PARENT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tiene receta que copiar");

        verify(itemIngredientRepository, never()).save(any(ItemIngredient.class));
    }

    // ---------- Crear la variante ----------

    @Test
    @DisplayName("crear la variante sin adoptar deja sin receta y sin descuento de inventario")
    void creatingWithoutAdoptingLeavesTheVariantWithoutRecipe() {
        ItemMenu parent = parentItem();
        parentRecipeIs(line(11L, "500"));
        stubsForCreatingASize(parent);

        ItemMenu child = service.addSizeItem(PARENT_ID, "Grande", new BigDecimal("250.00"), false);

        verify(itemIngredientRepository, never()).save(any(ItemIngredient.class));
        assertThat(child.getRequiresIngredients())
                .as("sin receta la variante no debe descontar inventario")
                .isFalse();
        assertThat(child.getParentItem()).isSameAs(parent);
    }

    @Test
    @DisplayName("crear la variante adoptando le copia la receta del padre")
    void creatingAdoptingCopiesTheParentsRecipe() {
        ItemMenu parent = parentItem();
        parentRecipeIs(line(11L, "500"), line(12L, "300"));
        stubsForCreatingASize(parent);

        ItemMenu child = service.addSizeItem(PARENT_ID, "Grande", new BigDecimal("250.00"), true);

        verify(itemIngredientRepository, times(2)).save(any(ItemIngredient.class));
        assertThat(child.getRequiresIngredients()).isTrue();
    }

    // ---------- Vincular un item existente ----------

    @Test
    @DisplayName("vincular sin adoptar respeta la receta que el item ya tenía")
    void linkingWithoutAdoptingKeepsTheItemsOwnRecipe() {
        ItemMenu parent = parentItem();
        parentRecipeIs(line(11L, "500"));
        ItemMenu child = ItemMenu.builder()
                .idItemMenu(VARIANT_ID)
                .name("Pizza familiar")
                .requiresIngredients(true)
                .build();
        stubsForLinkingASize(parent, child);
        variantRecipeIs(line(13L, "42"));

        service.linkExistingItemAsSize(PARENT_ID, VARIANT_ID, "Familiar", false);

        verify(itemIngredientRepository, never()).deleteAll(any());
        verify(itemIngredientRepository, never()).save(any(ItemIngredient.class));
        assertThat(child.getRequiresIngredients())
                .as("un item vinculado que ya tenía receta conserva su configuración")
                .isTrue();
    }

    @Test
    @DisplayName("vincular adoptando reemplaza la receta del item por la del padre")
    void linkingAdoptingReplacesTheItemsRecipe() {
        ItemMenu parent = parentItem();
        parentRecipeIs(line(11L, "500"));
        ItemMenu child = ItemMenu.builder()
                .idItemMenu(VARIANT_ID)
                .name("Pizza familiar")
                .requiresIngredients(true)
                .build();
        stubsForLinkingASize(parent, child);
        variantRecipeIs(line(13L, "42"));

        service.linkExistingItemAsSize(PARENT_ID, VARIANT_ID, "Familiar", true);

        verify(itemIngredientRepository).deleteAll(any());
        ArgumentCaptor<ItemIngredient> saved = ArgumentCaptor.forClass(ItemIngredient.class);
        verify(itemIngredientRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getQuantity()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(child.getRequiresIngredients()).isTrue();
    }
}
