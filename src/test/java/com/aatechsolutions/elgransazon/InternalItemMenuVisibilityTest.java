package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.repository.CategoryRepository;
import com.aatechsolutions.elgransazon.domain.repository.CompanyRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un platillo interno (buffet) no puede aparecer donde se vende.
 *
 * <p>El ocultamiento no está en las plantillas: está en las consultas. Si alguien agrega un
 * platillo con {@code internal_only = true}, tiene que desaparecer de las tres consultas que
 * alimentan los menús (operativo, carta impresa y listado del admin) y seguir existiendo en su
 * propio módulo. Se comprueba contra la base real, no contra el texto de la consulta, porque el
 * filtro sólo sirve si efectivamente filtra.</p>
 *
 * <p>Todo corre dentro de una transacción que se revierte: la base de desarrollo no conserva
 * nada de lo que se crea aquí.</p>
 */
@SpringBootTest
@Transactional
class InternalItemMenuVisibilityTest {

    @Autowired
    private ItemMenuRepository itemMenuRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("el platillo interno no aparece en el menú operativo ni en la carta")
    void anInternalItemNeverReachesTheMenus() {
        Company company = companyRepository.findAll().stream().findFirst().orElseThrow();
        Category category = categoryFor(company);

        ItemMenu sellable = itemMenuRepository.save(item("Vendible " + suffix(), company, category, false));
        ItemMenu internal = itemMenuRepository.save(item("Buffet " + suffix(), company, category, true));

        List<ItemMenu> operationalMenu = itemMenuRepository.findByActiveTrueAndCompany(company);
        List<ItemMenu> printedMenu = itemMenuRepository.findAllByCompanyWithParentForMenu(company);

        assertThat(operationalMenu)
                .contains(sellable)
                .doesNotContain(internal);
        assertThat(printedMenu)
                .doesNotContain(internal);
    }

    @Test
    @DisplayName("el platillo interno sí aparece en su propio listado, y sólo ahí")
    void anInternalItemIsListedOnlyByItsOwnModule() {
        Company company = companyRepository.findAll().stream().findFirst().orElseThrow();
        Category category = categoryFor(company);

        ItemMenu internal = itemMenuRepository.save(item("Buffet " + suffix(), company, category, true));

        assertThat(itemMenuRepository.findByCompanyAndInternalOnlyTrueOrderByNameAsc(company))
                .contains(internal);
        assertThat(itemMenuRepository.findInternalByIdAndCompany(internal.getIdItemMenu(), company))
                .contains(internal);
        // Sigue sin poder usarse como parte de un combo ni como variante de tamaño.
        assertThat(itemMenuRepository.findByCompany(company))
                .doesNotContain(internal);
    }

    // ========== Helpers ==========

    private Category categoryFor(Company company) {
        return categoryRepository.findAll().stream()
                .filter(category -> category.getCompany() != null
                        && company.getIdCompany().equals(category.getCompany().getIdCompany()))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .company(company)
                        .name("Test " + suffix())
                        .active(true)
                        .build()));
    }

    private ItemMenu item(String name, Company company, Category category, boolean internal) {
        return ItemMenu.builder()
                .company(company)
                .category(category)
                .name(name)
                .price(internal ? BigDecimal.ZERO : new BigDecimal("99.00"))
                .active(true)
                .available(true)
                .internalOnly(internal)
                .requiresIngredients(true)
                .requiresPreparation(false)
                .build();
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
