package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.InventoryObject;
import com.aatechsolutions.elgransazon.domain.entity.InventoryObjectCategory;
import com.aatechsolutions.elgransazon.domain.entity.ObjectCondition;
import com.aatechsolutions.elgransazon.domain.entity.ObjectUnit;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas del inventario de objetos: todo queda amarrado a la empresa de la sesión,
 * los nombres no se repiten dentro de una empresa y las bajas no borran datos.
 */
@ExtendWith(MockitoExtension.class)
class InventoryObjectServiceTest {

    @Mock
    private InventoryObjectRepository objectRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private InventoryObjectServiceImpl service;

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
        InventoryObject television = inventoryObject(1L, "Televisión Samsung", new BigDecimal("3"));
        when(objectRepository.searchWithFiltersAndCompany(eq(company), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(television));

        List<InventoryObject> result = service.searchWithFilters("  ", null, null, null, null);

        assertThat(result).containsExactly(television);
        verify(objectRepository).searchWithFiltersAndCompany(company, null, null, null, null, null);
    }

    @Test
    @DisplayName("no se permiten dos objetos con el mismo nombre en la misma empresa")
    void duplicateNameIsRejected() {
        InventoryObject object = inventoryObject(null, "Olla express", new BigDecimal("2"));
        when(objectRepository.existsByNameAndCompany("Olla express", company)).thenReturn(true);

        assertThatThrownBy(() -> service.create(object))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe un objeto con el nombre");

        verify(objectRepository, never()).save(any());
    }

    @Test
    @DisplayName("al crear, el objeto queda asignado a la empresa de la sesión")
    void createAssignsTheCurrentCompany() {
        InventoryObject object = inventoryObject(null, "Mesa 4", new BigDecimal("1"));
        when(objectRepository.save(any(InventoryObject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryObject saved = service.create(object);

        assertThat(saved.getCompany()).isEqualTo(company);
        assertThat(saved.getIdObject()).isNull();
    }

    @Test
    @DisplayName("no se puede usar una categoría de otra empresa")
    void categoryFromAnotherCompanyIsRejected() {
        InventoryObject object = inventoryObject(null, "Control de TV", new BigDecimal("2"));
        object.getCategory().setCompany(Company.builder().idCompany(99L).name("Otra empresa").build());

        assertThatThrownBy(() -> service.create(object))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("categoría de objetos válida");
    }

    @Test
    @DisplayName("al editar, el nombre de otro objeto existente no se puede reutilizar")
    void updateRejectsADuplicateName() {
        InventoryObject stored = inventoryObject(7L, "Olla express", new BigDecimal("2"));
        when(objectRepository.findByIdObjectAndCompany(7L, company)).thenReturn(Optional.of(stored));
        when(objectRepository.existsByNameAndCompany("Televisión", company)).thenReturn(true);

        InventoryObject changes = inventoryObject(null, "Televisión", new BigDecimal("1"));

        assertThatThrownBy(() -> service.update(7L, changes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe un objeto con el nombre");
    }

    @Test
    @DisplayName("un objeto de otra empresa no se puede editar ni desactivar")
    void objectsFromAnotherCompanyAreNotReachable() {
        when(objectRepository.findByIdObjectAndCompany(50L, company)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(50L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Objeto no encontrado");

        assertThatThrownBy(() -> service.hardDelete(50L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Objeto no encontrado");
    }

    @Test
    @DisplayName("desactivar conserva el registro y activar lo devuelve al inventario")
    void softDeleteAndActivateKeepTheRecord() {
        InventoryObject object = inventoryObject(9L, "Mesa 1", new BigDecimal("1"));
        when(objectRepository.findByIdObjectAndCompany(9L, company)).thenReturn(Optional.of(object));
        when(objectRepository.save(object)).thenReturn(object);

        service.delete(9L);
        assertThat(object.getActive()).isFalse();

        service.activate(9L);
        assertThat(object.getActive()).isTrue();

        verify(objectRepository, never()).delete(any());
    }

    @Test
    @DisplayName("el valor del inventario suma cantidad por costo sólo de los objetos activos")
    void inventoryValueOnlyCountsActiveObjects() {
        InventoryObject television = inventoryObject(1L, "Televisión", new BigDecimal("3"));
        television.setPurchaseCost(new BigDecimal("8500.00"));
        InventoryObject olla = inventoryObject(2L, "Olla express", new BigDecimal("2"));
        olla.setPurchaseCost(new BigDecimal("750.50"));
        olla.setActive(false);
        InventoryObject sinCosto = inventoryObject(3L, "Control", new BigDecimal("5"));

        when(objectRepository.findByCompanyOrderByNameAsc(company))
                .thenReturn(List.of(television, olla, sinCosto));

        assertThat(service.getInventoryValue()).isEqualByComparingTo("25500.00");
    }

    private InventoryObject inventoryObject(Long id, String name, BigDecimal quantity) {
        return InventoryObject.builder()
                .idObject(id)
                .name(name)
                .quantity(quantity)
                .unit(ObjectUnit.PIEZA)
                .condition(ObjectCondition.BUENO)
                .active(true)
                .category(InventoryObjectCategory.builder()
                        .idCategory(4L)
                        .name("Electrónica")
                        .company(company)
                        .build())
                .build();
    }
}
