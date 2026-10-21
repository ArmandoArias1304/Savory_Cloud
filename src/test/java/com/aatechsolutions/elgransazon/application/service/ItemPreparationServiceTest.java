package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemIngredient;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.ItemPreparation;
import com.aatechsolutions.elgransazon.domain.entity.PreparationStatus;
import com.aatechsolutions.elgransazon.domain.repository.ItemIngredientRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemPreparationRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Producción interna: lo que se descuenta al preparar y lo que se devuelve al cancelar.
 *
 * <p>El punto delicado es la simetría. Registrar 2 porciones descuenta la receta dos veces, y
 * cancelar ese lote tiene que devolver exactamente esas dos porciones —ni una más ni una
 * menos—, incluso si el botón se pulsa dos veces. También importa el camino de error: si el
 * guardado falla después de haber descontado, los ingredientes ya descontados tienen que volver,
 * porque esas deducciones viven en su propia transacción y no se revierten solas.</p>
 */
class ItemPreparationServiceTest {

    private ItemPreparationRepository preparationRepository;
    private ItemMenuRepository itemMenuRepository;
    private ItemIngredientRepository itemIngredientRepository;
    private IngredientStockService ingredientStockService;
    private ItemMenuService itemMenuService;
    private ItemPreparationServiceImpl service;

    private Company company;
    private Employee chef;

    @BeforeEach
    void setUp() {
        preparationRepository = mock(ItemPreparationRepository.class);
        itemMenuRepository = mock(ItemMenuRepository.class);
        itemIngredientRepository = mock(ItemIngredientRepository.class);
        ingredientStockService = mock(IngredientStockService.class);
        itemMenuService = mock(ItemMenuService.class);

        service = new ItemPreparationServiceImpl(
                preparationRepository,
                itemMenuRepository,
                itemIngredientRepository,
                ingredientStockService,
                itemMenuService,
                mock(DateTimeService.class));

        company = Company.builder().idCompany(1L).slug("ambar").name("Ambar").build();
        CompanyContext.setCurrentCompany(company);

        chef = Employee.builder().idEmpleado(5L).nombre("Ana").apellido("Ruiz").build();

        when(preparationRepository.save(any(ItemPreparation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("buscar en el historial recorta el texto y deja pasar el estado")
    void searchPreparationsTrimsTheTextAndPassesTheStatus() {
        ItemPreparation batch = ItemPreparation.builder().idPreparation(1L).quantity(1).build();
        when(preparationRepository.searchByCompany(eq(company), eq("guisado"),
                eq(PreparationStatus.CANCELLED), any())).thenReturn(List.of(batch));

        List<ItemPreparation> found =
                service.searchPreparations("  guisado  ", PreparationStatus.CANCELLED, 500);

        assertThat(found).containsExactly(batch);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(preparationRepository).searchByCompany(eq(company), eq("guisado"),
                eq(PreparationStatus.CANCELLED), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    @DisplayName("sin texto ni estado el historial busca igual que antes")
    void blankSearchAndNoStatusMeanNoFilter() {
        when(preparationRepository.searchByCompany(company, null, null, PageRequest.of(0, 40)))
                .thenReturn(List.of());

        service.searchPreparations("   ", null, 40);

        verify(preparationRepository).searchByCompany(company, null, null, PageRequest.of(0, 40));
    }

    @Test
    @DisplayName("registrar 2 porciones descuenta la receta dos veces")
    void registeringTwoPortionsDeductsTheRecipeTwice() {
        ItemMenu item = internalItem();
        mockInternalItem(item, List.of(recipeLine(item, "0.50", "KG", "10.00")));

        ItemPreparation preparation = service.registerPreparation(7L, 2, chef);

        // 0.50 KG por porción × 2 porciones = 1.00 KG
        verify(ingredientStockService).deductStockWithRetry(eq(11L), eq(new BigDecimal("1.00")), eq("KG"));
        assertThat(preparation.getQuantity()).isEqualTo(2);
        assertThat(preparation.getStatus()).isEqualTo(PreparationStatus.PREPARED);
        assertThat(preparation.getItemMenu()).isSameAs(item);
        assertThat(preparation.getPreparedBy()).isSameAs(chef);
        assertThat(preparation.getCompany()).isSameAs(company);
        assertThat(preparation.getStockReturned()).isFalse();
    }

    @Test
    @DisplayName("sin stock suficiente no se descuenta nada y se explica qué falta")
    void insufficientStockLeavesEverythingUntouched() {
        ItemMenu item = internalItem();
        mockInternalItem(item, List.of(recipeLine(item, "2.00", "KG", "1.00")));

        assertThatThrownBy(() -> service.registerPreparation(7L, 3, chef))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No hay stock suficiente")
                .hasMessageContaining("Tomate");

        verifyNoInteractions(ingredientStockService);
        verify(preparationRepository, never()).save(any(ItemPreparation.class));
    }

    @Test
    @DisplayName("si el guardado falla después de descontar, los ingredientes vuelven")
    void aFailedSaveReturnsWhatWasAlreadyDeducted() {
        ItemMenu item = internalItem();
        ItemIngredient tomatoLine = recipeLine(item, "0.50", "KG", "10.00");
        Ingredient onion = ingredient(12L, "Cebolla", "KG", "10.00");
        ItemIngredient onionLine = ItemIngredient.builder()
                .ingredient(onion).quantity(new BigDecimal("1.00")).unit("KG").itemMenu(item).build();

        mockInternalItem(item, List.of(tomatoLine, onionLine));
        doThrow(new IllegalStateException("Stock insuficiente de 'Cebolla'"))
                .when(ingredientStockService)
                .deductStockWithRetry(eq(12L), any(BigDecimal.class), anyString());

        assertThatThrownBy(() -> service.registerPreparation(7L, 1, chef))
                .isInstanceOf(IllegalStateException.class);

        // El tomate sí se había descontado: tiene que devolverse.
        verify(ingredientStockService).returnStockWithRetry(eq(11L), eq(new BigDecimal("0.50")), eq("KG"));
        // La cebolla nunca se descontó: no se devuelve.
        verify(ingredientStockService, never()).returnStockWithRetry(eq(12L), any(BigDecimal.class), anyString());
        verify(preparationRepository, never()).save(any(ItemPreparation.class));
    }

    @Test
    @DisplayName("cancelar devuelve la receta completa y marca el lote como cancelado")
    void cancellingReturnsTheWholeRecipe() {
        ItemMenu item = internalItem();
        ItemPreparation preparation = ItemPreparation.builder()
                .idPreparation(99L)
                .company(company)
                .itemMenu(item)
                .quantity(4)
                .status(PreparationStatus.PREPARED)
                .stockReturned(false)
                .build();

        when(preparationRepository.findByIdPreparationAndCompany(99L, company))
                .thenReturn(Optional.of(preparation));
        when(itemIngredientRepository.findByItemMenuId(7L))
                .thenReturn(List.of(recipeLine(item, "0.25", "KG", "10.00")));

        ItemPreparation cancelled = service.cancelPreparation(99L, chef, "  no se consumió  ");

        verify(ingredientStockService).returnStockWithRetry(eq(11L), eq(new BigDecimal("1.00")), eq("KG"));
        assertThat(cancelled.getStatus()).isEqualTo(PreparationStatus.CANCELLED);
        assertThat(cancelled.getStockReturned()).isTrue();
        assertThat(cancelled.getCancelReason()).isEqualTo("no se consumió");
        assertThat(cancelled.getCancelledBy()).isSameAs(chef);
    }

    @Test
    @DisplayName("cancelar dos veces no devuelve stock dos veces")
    void cancellingTwiceReturnsStockOnlyOnce() {
        ItemMenu item = internalItem();
        ItemPreparation preparation = ItemPreparation.builder()
                .idPreparation(99L)
                .company(company)
                .itemMenu(item)
                .quantity(1)
                .status(PreparationStatus.PREPARED)
                .stockReturned(false)
                .build();

        when(preparationRepository.findByIdPreparationAndCompany(99L, company))
                .thenReturn(Optional.of(preparation));
        when(itemIngredientRepository.findByItemMenuId(7L))
                .thenReturn(List.of(recipeLine(item, "0.25", "KG", "10.00")));

        service.cancelPreparation(99L, chef, null);

        assertThatThrownBy(() -> service.cancelPreparation(99L, chef, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya fue cancelada");

        // Una sola devolución pese a los dos intentos.
        verify(ingredientStockService, times(1))
                .returnStockWithRetry(eq(11L), any(BigDecimal.class), anyString());
    }

    @Test
    @DisplayName("los totales del día ignoran los lotes cancelados")
    void totalsIgnoreCancelledBatches() {
        ItemMenu item = internalItem();
        item.setIngredients(List.of(recipeLine(item, "1.00", "KG", "10.00")));

        ItemPreparation prepared = ItemPreparation.builder()
                .itemMenu(item).quantity(2).status(PreparationStatus.PREPARED).build();
        ItemPreparation cancelled = ItemPreparation.builder()
                .itemMenu(item).quantity(3).status(PreparationStatus.CANCELLED).stockReturned(true).build();

        assertThat(service.totalProducedQuantity(List.of(prepared, cancelled))).isEqualTo(2);
        // 1.00 KG × $20 por porción = $20; dos porciones = $40. El lote cancelado no suma.
        assertThat(service.totalEstimatedCost(List.of(prepared, cancelled)))
                .isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("un platillo que no es interno no se puede producir por esta vía")
    void aSellableItemCannotBeProducedAsInternal() {
        when(itemMenuRepository.findInternalByIdAndCompany(eq(7L), eq(company))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerPreparation(7L, 1, chef))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no encontrado");

        verify(ingredientStockService, never()).deductStockWithRetry(anyLong(), any(), anyString());
    }

    // ========== Helpers ==========

    private ItemMenu internalItem() {
        return ItemMenu.builder()
                .idItemMenu(7L)
                .name("Guisado del buffet")
                .company(company)
                .price(BigDecimal.ZERO)
                .internalOnly(true)
                .active(true)
                .requiresIngredients(true)
                .build();
    }

    private Ingredient ingredient(Long id, String name, String unit, String stock) {
        return Ingredient.builder()
                .idIngredient(id)
                .name(name)
                .unitOfMeasure(unit)
                .currentStock(new BigDecimal(stock))
                .costPerUnit(new BigDecimal("20.00"))
                .build();
    }

    private ItemIngredient recipeLine(ItemMenu item, String quantity, String unit, String stock) {
        return ItemIngredient.builder()
                .itemMenu(item)
                .ingredient(ingredient(11L, "Tomate", unit, stock))
                .quantity(new BigDecimal(quantity))
                .unit(unit)
                .build();
    }

    private void mockInternalItem(ItemMenu item, List<ItemIngredient> recipe) {
        when(itemMenuRepository.findInternalByIdAndCompany(eq(7L), eq(company))).thenReturn(Optional.of(item));
        when(itemIngredientRepository.findByItemMenuId(7L)).thenReturn(recipe);
    }
}
