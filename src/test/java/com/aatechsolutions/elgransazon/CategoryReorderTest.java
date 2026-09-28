package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.CategoryServiceImpl;
import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.repository.CategoryRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuRepository;
import com.aatechsolutions.elgransazon.domain.repository.OrderDetailRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The order the categories follow in the printed menu (carta): what the drag &amp; drop of
 * the categories view stores in {@code categories.display_order}.
 */
class CategoryReorderTest {

    private CategoryRepository categoryRepository;
    private CategoryService service;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        service = new CategoryServiceImpl(categoryRepository,
                mock(ItemMenuRepository.class), mock(OrderDetailRepository.class));
        CompanyContext.setCurrentCompany(Company.builder()
                .idCompany(1L)
                .slug("quinta")
                .name("Quinta El Paraíso")
                .build());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void storesTheReceivedOrderAsOneToN() {
        givenCategories(category(5L, "Postres", 2), category(7L, "Entradas", 1), category(9L, "Bebidas", 3));
        when(categoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.reorderCategories(List.of(7L, 5L, 9L));

        List<Category> saved = savedCategories();
        assertEquals(List.of(7L, 5L, 9L), ids(saved), "no se guardó el orden recibido");
        assertEquals(List.of(1, 2, 3), saved.stream().map(Category::getDisplayOrder).toList(),
                "las posiciones guardadas deben ser 1..N");
    }

    @Test
    void keepsCategoriesThatWereNotSentAfterTheReceivedOnes() {
        givenCategories(category(5L, "Postres", 2), category(7L, "Entradas", 1),
                category(9L, "Bebidas", 3), category(11L, "Niños", null));
        when(categoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.reorderCategories(List.of(9L, 7L));

        List<Category> saved = savedCategories();
        assertEquals(List.of(9L, 7L, 5L, 11L), ids(saved),
                "las categorías no enviadas deben conservar su orden relativo al final");
        assertEquals(List.of(1, 2, 3, 4), saved.stream().map(Category::getDisplayOrder).toList(),
                "toda la tabla debe quedar numerada sin huecos ni empates");
    }

    @Test
    void ignoresNullAndRepeatedIds() {
        givenCategories(category(5L, "Postres", 1), category(7L, "Entradas", 2));
        when(categoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.reorderCategories(Arrays.asList(7L, null, 7L, 5L));

        assertEquals(List.of(7L, 5L), ids(savedCategories()), "los ids repetidos o nulos deben ignorarse");
    }

    @Test
    void refusesAnEmptyOrder() {
        assertThrows(IllegalArgumentException.class, () -> service.reorderCategories(List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.reorderCategories(null));
        verify(categoryRepository, never()).saveAll(any());
    }

    @Test
    void refusesCategoriesFromAnotherEstablishment() {
        // Only 5 and 7 belong to this company: 99 is from somewhere else.
        givenCategories(category(5L, "Postres", 1), category(7L, "Entradas", 2));

        assertThrows(IllegalArgumentException.class, () -> service.reorderCategories(List.of(7L, 99L)));
        verify(categoryRepository, never()).saveAll(any());
    }

    // ==================== helpers ====================

    private void givenCategories(Category... categories) {
        when(categoryRepository.findAllByCompanyOrderedForMenu(any())).thenReturn(List.of(categories));
    }

    @SuppressWarnings("unchecked")
    private List<Category> savedCategories() {
        ArgumentCaptor<List<Category>> captor = ArgumentCaptor.forClass(List.class);
        verify(categoryRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private List<Long> ids(List<Category> categories) {
        return categories.stream().map(Category::getIdCategory).toList();
    }

    private Category category(Long id, String name, Integer displayOrder) {
        return Category.builder()
                .idCategory(id)
                .name(name)
                .active(true)
                .displayOrder(displayOrder)
                .build();
    }
}
