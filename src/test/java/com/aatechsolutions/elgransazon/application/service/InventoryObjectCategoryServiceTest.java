package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.InventoryObject;
import com.aatechsolutions.elgransazon.domain.entity.InventoryObjectCategory;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.domain.repository.InventoryObjectCategoryRepository;
import com.aatechsolutions.elgransazon.domain.repository.InventoryObjectRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de las categorías de objetos: nombres únicos por empresa y
 * eliminación definitiva sólo cuando la categoría está vacía.
 */
@ExtendWith(MockitoExtension.class)
class InventoryObjectCategoryServiceTest {

    @Mock
    private InventoryObjectCategoryRepository categoryRepository;

    @Mock
    private InventoryObjectRepository objectRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private InventoryObjectCategoryServiceImpl service;

    private Company company;

    @BeforeEach
    void setUp() {
        company = Company.builder().idCompany(3L).name("El Gran Sazón").slug("elgransazon").build();
        CompanyContext.setCurrentCompany(company);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("la búsqueda siempre filtra por la empresa de la sesión")
    void searchAlwaysScopesToTheCurrentCompany() {
        InventoryObjectCategory mesas = InventoryObjectCategory.builder()
                .idCategory(1L).name("Mesas").company(company).active(true).build();
        when(categoryRepository.searchWithFiltersAndCompany(company, "mes", true))
                .thenReturn(List.of(mesas));

        assertThat(service.searchWithFilters("  mes  ", true)).containsExactly(mesas);
    }

    @Test
    @DisplayName("no se permiten dos categorías con el mismo nombre en la misma empresa")
    void duplicateNameIsRejected() {
        InventoryObjectCategory category = InventoryObjectCategory.builder()
                .name("Mesas").company(company).active(true).build();
        when(categoryRepository.existsByNameAndCompany("Mesas", company)).thenReturn(true);

        assertThatThrownBy(() -> service.create(category))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe una categoría de objetos con el nombre");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("al crear, la categoría queda asignada a la empresa de la sesión")
    void createAssignsTheCurrentCompany() {
        InventoryObjectCategory category = InventoryObjectCategory.builder()
                .name("Menaje").active(true).build();
        when(categoryRepository.save(any(InventoryObjectCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(category).getCompany()).isEqualTo(company);
    }

    @Test
    @DisplayName("una categoría con objetos asignados no se puede eliminar definitivamente")
    void hardDeleteIsBlockedWhenTheCategoryHasObjects() {
        InventoryObjectCategory category = InventoryObjectCategory.builder()
                .idCategory(2L).name("Cocina").company(company).active(true).build();
        when(categoryRepository.findByIdCategoryAndCompany(2L, company)).thenReturn(Optional.of(category));
        when(objectRepository.findByCategoryIdCategoryAndCompany(2L, company))
                .thenReturn(List.of(InventoryObject.builder()
                        .idObject(10L)
                        .name("Olla express")
                        .quantity(BigDecimal.valueOf(2))
                        .category(category)
                        .build()));

        assertThatThrownBy(() -> service.hardDelete(2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 objeto(s) asignados");

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("la verificación previa reporta los objetos que bloquean la eliminación")
    void dependencyCheckReportsAssignedObjects() {
        InventoryObjectCategory category = InventoryObjectCategory.builder()
                .idCategory(2L).name("Cocina").company(company).active(true).build();
        when(categoryRepository.findByIdCategoryAndCompany(2L, company)).thenReturn(Optional.of(category));
        when(objectRepository.findByCategoryIdCategoryAndCompany(2L, company))
                .thenReturn(List.of(
                        InventoryObject.builder().idObject(10L).name("Olla express").quantity(BigDecimal.valueOf(2)).category(category).build(),
                        InventoryObject.builder().idObject(11L).name("Sartén").quantity(BigDecimal.ONE).category(category).build()));

        Map<String, Object> result = service.checkHardDeleteDependencies(2L);

        assertThat(result).containsEntry("canDelete", false)
                .containsEntry("objectCount", 2);
        assertThat(result.get("objectNames")).isEqualTo(List.of("Olla express", "Sartén"));
    }

    @Test
    @DisplayName("una categoría vacía sí se elimina definitivamente")
    void emptyCategoryIsHardDeleted() {
        InventoryObjectCategory category = InventoryObjectCategory.builder()
                .idCategory(5L).name("Decoración").company(company).active(true).build();
        when(categoryRepository.findByIdCategoryAndCompany(5L, company)).thenReturn(Optional.of(category));
        when(objectRepository.findByCategoryIdCategoryAndCompany(5L, company)).thenReturn(List.of());

        service.hardDelete(5L);

        verify(categoryRepository).delete(category);
    }
}
