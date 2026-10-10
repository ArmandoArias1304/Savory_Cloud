package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.application.service.InventoryObjectCategoryService;
import com.aatechsolutions.elgransazon.application.service.InventoryObjectService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.InventoryObject;
import com.aatechsolutions.elgransazon.domain.entity.InventoryObjectCategory;
import com.aatechsolutions.elgransazon.domain.entity.ObjectCondition;
import com.aatechsolutions.elgransazon.domain.entity.ObjectUnit;
import com.aatechsolutions.elgransazon.domain.repository.CompanyRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La tabla nueva se crea con {@code ddl-auto=update}: aquí se comprueba contra la
 * base real que un objeto se guarda, se lee de vuelta y no se ve desde otra empresa.
 * Todo dentro de una transacción que se revierte al terminar.
 */
@SpringBootTest
@Transactional
class InventoryObjectPersistenceTest {

    @Autowired
    private InventoryObjectService objectService;

    @Autowired
    private InventoryObjectCategoryService categoryService;

    @Autowired
    private CompanyRepository companyRepository;

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("un objeto se guarda con su categoría y se lee de vuelta en la misma empresa")
    void objectIsStoredAndReadBack() {
        Company company = currentCompanyInDatabase();
        Assumptions.assumeTrue(company != null, "la base local no tiene empresas registradas");

        InventoryObjectCategory category = categoryService.create(InventoryObjectCategory.builder()
                .name("Electrónica de prueba " + System.nanoTime())
                .description("Categoría creada por la prueba")
                .icon("tv")
                .color("#38e07b")
                .active(true)
                .build());

        InventoryObject saved = objectService.create(InventoryObject.builder()
                .name("Televisión de prueba " + System.nanoTime())
                .description("Pantalla del salón")
                .category(category)
                .quantity(new BigDecimal("3"))
                .unit(ObjectUnit.PIEZA)
                .condition(ObjectCondition.BUENO)
                .location("Salón")
                .responsible("Gerente")
                .purchaseCost(new BigDecimal("8500.00"))
                .currency("MXN")
                .brand("Samsung")
                .model("UN50CU7000")
                .serialNumber("SN-TEST-001")
                .active(true)
                .build());

        assertThat(saved.getIdObject()).isNotNull();
        assertThat(saved.getCompany().getIdCompany()).isEqualTo(company.getIdCompany());
        assertThat(saved.getTotalValue()).isEqualByComparingTo("25500.00");

        InventoryObject reloaded = objectService.findById(saved.getIdObject()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo(saved.getName());
        assertThat(reloaded.getCategory().getIdCategory()).isEqualTo(category.getIdCategory());
        assertThat(reloaded.getCondition()).isEqualTo(ObjectCondition.BUENO);
        assertThat(reloaded.getUnit()).isEqualTo(ObjectUnit.PIEZA);
    }

    @Test
    @DisplayName("otra empresa no ve los objetos ni las categorías de esta")
    void anotherCompanyCannotSeeTheObjects() {
        Company company = currentCompanyInDatabase();
        Assumptions.assumeTrue(company != null, "la base local no tiene empresas registradas");

        InventoryObjectCategory category = categoryService.create(InventoryObjectCategory.builder()
                .name("Menaje de prueba " + System.nanoTime())
                .icon("soup_kitchen")
                .color("#38e07b")
                .active(true)
                .build());

        InventoryObject saved = objectService.create(InventoryObject.builder()
                .name("Olla de prueba " + System.nanoTime())
                .category(category)
                .quantity(new BigDecimal("2"))
                .unit(ObjectUnit.PIEZA)
                .condition(ObjectCondition.NUEVO)
                .active(true)
                .build());

        Company other = Company.builder().idCompany(company.getIdCompany() + 999_999L).build();
        CompanyContext.setCurrentCompany(other);

        assertThat(objectService.findById(saved.getIdObject())).isEmpty();
        assertThat(objectService.searchWithFilters(null, null, null, null, null)).isEmpty();
        assertThat(objectService.getActiveCount()).isZero();
        assertThat(categoryService.findById(category.getIdCategory())).isEmpty();
        assertThat(categoryService.findAllActive()).doesNotContain(category);

        CompanyContext.setCurrentCompany(company);
        assertThat(objectService.findById(saved.getIdObject())).isPresent();
        assertThat(objectService.getActiveCount()).isPositive();
    }

    @Test
    @DisplayName("las estadísticas del inventario cuentan sólo lo de la empresa actual")
    void statisticsOnlyCountTheCurrentCompany() {
        Company company = currentCompanyInDatabase();
        Assumptions.assumeTrue(company != null, "la base local no tiene empresas registradas");

        long before = objectService.getTotalCount();

        InventoryObjectCategory category = categoryService.create(InventoryObjectCategory.builder()
                .name("Limpieza de prueba " + System.nanoTime())
                .icon("cleaning_services")
                .color("#38e07b")
                .active(true)
                .build());

        objectService.create(InventoryObject.builder()
                .name("Trapeador de prueba " + System.nanoTime())
                .category(category)
                .quantity(new BigDecimal("4"))
                .unit(ObjectUnit.PIEZA)
                .condition(ObjectCondition.DANADO)
                .active(true)
                .build());

        assertThat(objectService.getTotalCount()).isEqualTo(before + 1);
        assertThat(objectService.getDamagedCount()).isPositive();

        List<InventoryObject> damaged = objectService.searchWithFilters(
                null, null, ObjectCondition.DANADO, null, null);
        assertThat(damaged).allMatch(o -> o.getCondition() == ObjectCondition.DANADO);
    }

    /**
     * Toma la primera empresa registrada y deja el contexto apuntando a ella
     */
    private Company currentCompanyInDatabase() {
        List<Company> companies = companyRepository.findAll();
        if (companies.isEmpty()) {
            return null;
        }
        Company company = companies.get(0);
        CompanyContext.setCurrentCompany(company);
        return company;
    }
}
