package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.ItemPreparation;
import com.aatechsolutions.elgransazon.domain.entity.PreparationStatus;
import com.aatechsolutions.elgransazon.domain.repository.CategoryRepository;
import com.aatechsolutions.elgransazon.domain.repository.CompanyRepository;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemPreparationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El buscador del historial de producción interna, ejecutado contra la base real.
 *
 * <p>Una consulta JPQL puede compilar y aun así no filtrar nada (o filtrar de más). Lo que se fija
 * aquí es lo que el usuario ve: buscar por platillo, buscar por quien lo registró y filtrar por
 * estado, todo opcional e independiente. Corre dentro de una transacción que se revierte.</p>
 */
@SpringBootTest
@Transactional
class InternalProductionHistorySearchTest {

    @Autowired
    private ItemPreparationRepository preparationRepository;

    @Autowired
    private ItemMenuRepository itemMenuRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    @DisplayName("el historial se filtra por platillo, por quién registró y por estado")
    void theHistoryIsFilteredOnTheRealDatabase() {
        Company company = companyRepository.findAll().stream().findFirst().orElseThrow();
        Category category = categoryFor(company);
        Employee employee = employeeRepository.findByCompany(company).stream().findFirst().orElse(null);

        ItemMenu guisado = itemMenuRepository.save(item("Guisado " + suffix(), company, category));
        ItemMenu camarones = itemMenuRepository.save(item("Camarones " + suffix(), company, category));

        ItemPreparation prepared = preparationRepository.save(
                batch(company, guisado, PreparationStatus.PREPARED, employee));
        ItemPreparation cancelled = preparationRepository.save(
                batch(company, camarones, PreparationStatus.CANCELLED, employee));

        PageRequest page = PageRequest.of(0, 40);

        // Texto: ignora mayúsculas y encuentra por una parte del nombre del platillo.
        assertThat(preparationRepository.searchByCompany(company, "guisado", null, page))
                .contains(prepared)
                .doesNotContain(cancelled);

        // Estado: deja fuera lo que no coincide aunque el texto esté vacío.
        assertThat(preparationRepository.searchByCompany(company, null, PreparationStatus.CANCELLED, page))
                .contains(cancelled)
                .doesNotContain(prepared);

        // Sin filtros no se pierde nada.
        assertThat(preparationRepository.searchByCompany(company, null, null, page))
                .contains(prepared, cancelled);

        // Quién lo registró: se busca por su nombre completo, igual que se lee en la tabla.
        if (employee != null && employee.getNombre() != null && !employee.getNombre().isBlank()) {
            assertThat(preparationRepository.searchByCompany(company, employee.getNombre(), null, page))
                    .contains(prepared, cancelled);
        }
    }

    @Test
    @DisplayName("un texto que no existe devuelve la lista vacía en vez de todo el historial")
    void anUnknownSearchReturnsNothing() {
        Company company = companyRepository.findAll().stream().findFirst().orElseThrow();
        Category category = categoryFor(company);
        ItemMenu item = itemMenuRepository.save(item("Sopa " + suffix(), company, category));
        preparationRepository.save(batch(company, item, PreparationStatus.PREPARED, null));

        assertThat(preparationRepository.searchByCompany(
                company, "no-existe-" + suffix(), null, PageRequest.of(0, 40)))
                .isEmpty();
    }

    // ========== Helpers ==========

    private Category categoryFor(Company company) {
        return categoryRepository.findAll().stream()
                .filter(category -> category.getCompany() != null
                        && company.getIdCompany().equals(category.getCompany().getIdCompany()))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .company(company)
                        .name("Busqueda " + suffix())
                        .active(true)
                        .build()));
    }

    private ItemMenu item(String name, Company company, Category category) {
        return ItemMenu.builder()
                .company(company)
                .category(category)
                .name(name)
                .price(BigDecimal.ZERO)
                .active(true)
                .available(true)
                .internalOnly(true)
                .requiresIngredients(true)
                .requiresPreparation(false)
                .build();
    }

    private ItemPreparation batch(Company company, ItemMenu item, PreparationStatus status,
                                  Employee employee) {
        return ItemPreparation.builder()
                .company(company)
                .itemMenu(item)
                .quantity(2)
                .status(status)
                .preparedAt(LocalDateTime.now())
                .preparedBy(employee)
                .stockReturned(status == PreparationStatus.CANCELLED)
                .build();
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
