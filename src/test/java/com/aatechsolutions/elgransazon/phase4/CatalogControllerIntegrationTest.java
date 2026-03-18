package com.aatechsolutions.elgransazon.phase4;

import com.aatechsolutions.elgransazon.application.service.EmailService;
import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.IngredientCategory;
import com.aatechsolutions.elgransazon.domain.entity.Role;
import com.aatechsolutions.elgransazon.support.TestDataHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FASE 4 — Tests de integración para los controladores del catálogo.
 *
 * Cubre:
 * - CategoryController       (/admin/categories)
 * - IngredientCategoryController (/admin/ingredient-categories)
 * - IngredientController     (/admin/ingredients)
 * - SupplierController       (/admin/suppliers)
 * - ItemMenuController       (/admin/menu-items)
 * - ComplementController     (/api/complements) — REST
 *
 * Verifica:
 * - ADMIN/MANAGER pueden listar y crear entidades del catálogo
 * - WAITER/CASHIER reciben 403 en endpoints de admin
 * - El mismo nombre de categoría en distintas companies no genera conflicto
 * - Nombre duplicado en la misma company devuelve formulario (no redirect)
 * - Aislamiento multi-tenant: entidades de Company1 no visibles desde Company2
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("FASE 4 — Catalog Controller Integration Tests")
class CatalogControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper testData;

    @MockBean private EmailService emailService;

    private static final String SLUG_1 = "tp4-cat-c1";
    private static final String SLUG_2 = "tp4-cat-c2";

    private Company company1;
    private Company company2;
    private Employee admin1;
    private Employee manager1;
    private Employee waiter1;
    private Employee cashier1;
    private Employee admin2;

    @BeforeEach
    void setUp() {
        testData.cleanUpBySlug(SLUG_1);
        testData.cleanUpBySlug(SLUG_2);

        company1 = testData.createActiveCompany(SLUG_1, "America/Mexico_City");
        company2 = testData.createActiveCompany(SLUG_2, "America/Mexico_City");

        admin1   = testData.createEmployee(company1, "tp4-admin-1",   "pass", Role.ADMIN);
        manager1 = testData.createEmployee(company1, "tp4-mgr-1",     "pass", Role.MANAGER);
        waiter1  = testData.createEmployee(company1, "tp4-waiter-1",  "pass", Role.WAITER);
        cashier1 = testData.createEmployee(company1, "tp4-cashier-1", "pass", Role.CASHIER);
        admin2   = testData.createEmployee(company2, "tp4-admin-2",   "pass", Role.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCompanyAndAllData(company1.getIdCompany());
        testData.deleteCompanyAndAllData(company2.getIdCompany());
    }

    // ================================================================
    // CategoryController — /admin/categories
    // ================================================================

    @Test
    @DisplayName("ADMIN puede listar categorías de su company")
    void admin_canListCategories() throws Exception {
        testData.createCategory(company1, "Entradas");

        mockMvc.perform(get("/admin/categories")
                        .with(user(admin1.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories/list"));
    }

    @Test
    @DisplayName("MANAGER puede listar categorías")
    void manager_canListCategories() throws Exception {
        mockMvc.perform(get("/admin/categories")
                        .with(user(manager1.getUsername()).roles("MANAGER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories/list"));
    }

    @Test
    @DisplayName("WAITER recibe 403 al listar categorías")
    void waiter_cannotListCategories() throws Exception {
        mockMvc.perform(get("/admin/categories")
                        .with(user(waiter1.getUsername()).roles("WAITER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CASHIER recibe 403 al listar categorías")
    void cashier_cannotListCategories() throws Exception {
        mockMvc.perform(get("/admin/categories")
                        .with(user(cashier1.getUsername()).roles("CASHIER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN puede crear una categoría nueva")
    void admin_canCreateCategory() throws Exception {
        mockMvc.perform(post("/admin/categories")
                        .with(user(admin1.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_1))
                        .with(csrf())
                        .param("name", "Postres")
                        .param("description", "Postres y dulces")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"));
    }

    @Test
    @DisplayName("El mismo nombre de categoría en dos companies diferentes es válido")
    void category_sameNameInDifferentCompanies_isAllowed() throws Exception {
        // Create same name in company1
        mockMvc.perform(post("/admin/categories")
                        .with(user(admin1.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_1))
                        .with(csrf())
                        .param("name", "Bebidas")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection());

        // Create same name in company2 — should also succeed
        mockMvc.perform(post("/admin/categories")
                        .with(user(admin2.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_2))
                        .with(csrf())
                        .param("name", "Bebidas")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Nombre de categoría duplicado en la misma company devuelve formulario")
    void category_duplicateNameInSameCompany_returnsForm() throws Exception {
        testData.createCategory(company1, "Sopas");

        mockMvc.perform(post("/admin/categories")
                        .with(user(admin1.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_1))
                        .with(csrf())
                        .param("name", "Sopas")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories/form"));
    }

    @Test
    @DisplayName("Aislamiento: categorías de company1 no visibles desde company2")
    void categories_isolatedByCompany() throws Exception {
        testData.createCategory(company1, "Exclusiva C1");

        // admin2 in company2 lists categories — should not include company1's category
        mockMvc.perform(get("/admin/categories")
                        .with(user(admin2.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_2)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("categories",
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.hasItem(
                                        org.hamcrest.Matchers.hasProperty("name",
                                                org.hamcrest.Matchers.is("Exclusiva C1"))))));
    }

    // ================================================================
    // IngredientCategoryController — /admin/ingredient-categories
    // ================================================================

    @Test
    @DisplayName("ADMIN puede listar categorías de ingredientes")
    void admin_canListIngredientCategories() throws Exception {
        mockMvc.perform(get("/admin/ingredient-categories")
                        .with(user(admin1.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/ingredient-categories/list"));
    }

    @Test
    @DisplayName("WAITER recibe 403 al listar categorías de ingredientes")
    void waiter_cannotListIngredientCategories() throws Exception {
        mockMvc.perform(get("/admin/ingredient-categories")
                        .with(user(waiter1.getUsername()).roles("WAITER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN puede crear una categoría de ingrediente")
    void admin_canCreateIngredientCategory() throws Exception {
        mockMvc.perform(post("/admin/ingredient-categories")
                        .with(user(admin1.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_1))
                        .with(csrf())
                        .param("name", "Verduras")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/ingredient-categories"));
    }

    @Test
    @DisplayName("Mismo nombre de categoría de ingrediente en diferentes companies es válido")
    void ingredientCategory_sameNameInDifferentCompanies_isAllowed() throws Exception {
        mockMvc.perform(post("/admin/ingredient-categories")
                        .with(user(admin1.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_1))
                        .with(csrf())
                        .param("name", "Carnes"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/ingredient-categories")
                        .with(user(admin2.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_2))
                        .with(csrf())
                        .param("name", "Carnes"))
                .andExpect(status().is3xxRedirection());
    }

    // ================================================================
    // IngredientController — /admin/ingredients
    // ================================================================

    @Test
    @DisplayName("ADMIN puede listar ingredientes")
    void admin_canListIngredients() throws Exception {
        mockMvc.perform(get("/admin/ingredients")
                        .with(user(admin1.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/ingredients/list"));
    }

    @Test
    @DisplayName("WAITER recibe 403 al listar ingredientes")
    void waiter_cannotListIngredients() throws Exception {
        mockMvc.perform(get("/admin/ingredients")
                        .with(user(waiter1.getUsername()).roles("WAITER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN puede crear un ingrediente con categoría válida")
    void admin_canCreateIngredient() throws Exception {
        IngredientCategory ic = testData.createIngredientCategory(company1, "Lácteos");

        mockMvc.perform(post("/admin/ingredients")
                        .with(user(admin1.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_1))
                        .with(csrf())
                        .param("name", "Leche")
                        .param("currentStock", "50.00")
                        .param("minStock", "5.00")
                        .param("unitOfMeasure", "LT")
                        .param("currency", "MXN")
                        .param("active", "true")
                        .param("categoryId", ic.getIdCategory().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/ingredients"));
    }

    @Test
    @DisplayName("Mismo nombre de ingrediente en diferentes companies es válido")
    void ingredient_sameNameInDifferentCompanies_isAllowed() throws Exception {
        IngredientCategory ic1 = testData.createIngredientCategory(company1, "Especias-C1");
        IngredientCategory ic2 = testData.createIngredientCategory(company2, "Especias-C2");

        mockMvc.perform(post("/admin/ingredients")
                        .with(user(admin1.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_1))
                        .with(csrf())
                        .param("name", "Sal")
                        .param("currentStock", "10.00")
                        .param("minStock", "1.00")
                        .param("unitOfMeasure", "KG")
                        .param("currency", "MXN")
                        .param("active", "true")
                        .param("categoryId", ic1.getIdCategory().toString()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/ingredients")
                        .with(user(admin2.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_2))
                        .with(csrf())
                        .param("name", "Sal")
                        .param("currentStock", "10.00")
                        .param("minStock", "1.00")
                        .param("unitOfMeasure", "KG")
                        .param("currency", "MXN")
                        .param("active", "true")
                        .param("categoryId", ic2.getIdCategory().toString()))
                .andExpect(status().is3xxRedirection());
    }

    // ================================================================
    // SupplierController — /admin/suppliers
    // ================================================================

    @Test
    @DisplayName("ADMIN puede listar proveedores")
    void admin_canListSuppliers() throws Exception {
        mockMvc.perform(get("/admin/suppliers")
                        .with(user(admin1.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/suppliers/list"));
    }

    @Test
    @DisplayName("WAITER recibe 403 al listar proveedores")
    void waiter_cannotListSuppliers() throws Exception {
        mockMvc.perform(get("/admin/suppliers")
                        .with(user(waiter1.getUsername()).roles("WAITER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN puede crear un proveedor")
    void admin_canCreateSupplier() throws Exception {
        mockMvc.perform(post("/admin/suppliers")
                        .with(user(admin1.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_1))
                        .with(csrf())
                        .param("name", "Distribuidora ABC")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/suppliers"));
    }

    @Test
    @DisplayName("Aislamiento: proveedor de company1 no visible en company2")
    void supplier_isolatedByCompany() throws Exception {
        testData.createSupplier(company1, "Proveedor Exclusivo C1");

        mockMvc.perform(get("/admin/suppliers")
                        .with(user(admin2.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_2)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("suppliers",
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.hasItem(
                                        org.hamcrest.Matchers.hasProperty("name",
                                                org.hamcrest.Matchers.is("Proveedor Exclusivo C1"))))));
    }

    // ================================================================
    // ItemMenuController — /admin/menu-items
    // ================================================================

    @Test
    @DisplayName("ADMIN puede listar ítems del menú")
    void admin_canListMenuItems() throws Exception {
        mockMvc.perform(get("/admin/menu-items")
                        .with(user(admin1.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/menu-items/list"));
    }

    @Test
    @DisplayName("MANAGER puede listar ítems del menú")
    void manager_canListMenuItems() throws Exception {
        mockMvc.perform(get("/admin/menu-items")
                        .with(user(manager1.getUsername()).roles("MANAGER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/menu-items/list"));
    }

    @Test
    @DisplayName("WAITER recibe 403 al listar ítems del menú")
    void waiter_cannotListMenuItems() throws Exception {
        mockMvc.perform(get("/admin/menu-items")
                        .with(user(waiter1.getUsername()).roles("WAITER"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN ve el formulario de nuevo ítem de menú")
    void admin_canAccessNewMenuItemForm() throws Exception {
        testData.createCategory(company1, "Categoría Test");

        mockMvc.perform(get("/admin/menu-items/new")
                        .with(user(admin1.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_1)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/menu-items/form"));
    }

    // ================================================================
    // ComplementController — /api/complements (REST)
    // ================================================================

    @Test
    @DisplayName("ADMIN puede listar complementos vía API REST")
    void admin_canListComplements() throws Exception {
        mockMvc.perform(get("/api/complements")
                        .with(user(admin1.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_1))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("WAITER puede listar complementos vía API REST")
    void waiter_canListComplements() throws Exception {
        mockMvc.perform(get("/api/complements")
                        .with(user(waiter1.getUsername()).roles("WAITER"))
                        .with(forCompany(SLUG_1))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN puede crear un complemento vía API REST")
    void admin_canCreateComplement() throws Exception {
        String json = """
                {
                  "name": "Salsa Picante",
                  "description": "Salsa muy picante",
                  "extraPrice": 5.00,
                  "active": true
                }
                """;

        mockMvc.perform(post("/api/complements")
                        .with(user(admin1.getUsername()).roles("ADMIN"))
                        .with(forCompany(SLUG_1))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("WAITER no puede crear complementos (403)")
    void waiter_cannotCreateComplement() throws Exception {
        String json = """
                {
                  "name": "Ketchup",
                  "extraPrice": 0.00,
                  "active": true
                }
                """;

        mockMvc.perform(post("/api/complements")
                        .with(user(waiter1.getUsername()).roles("WAITER"))
                        .with(forCompany(SLUG_1))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    // Helper
    // ================================================================

    private RequestPostProcessor forCompany(String slug) {
        return request -> {
            request.setServerName(slug + ".localhost");
            return request;
        };
    }
}
