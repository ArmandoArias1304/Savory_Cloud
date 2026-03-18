package com.aatechsolutions.elgransazon.support;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * Helper de soporte para crear y limpiar datos de prueba en tests de integración.
 *
 * Todos los métodos se ejecutan en su propia transacción para asegurar que los
 * datos estén visibles para el hilo del test y para los filtros de Spring Security.
 */
@Component
public class TestDataHelper {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired private CompanyRepository companyRepository;
    @Autowired private SystemLicenseRepository licenseRepository;
    @Autowired private SystemConfigurationRepository configRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private RestaurantTableRepository restaurantTableRepository;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private BusinessHoursRepository businessHoursRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private IngredientCategoryRepository ingredientCategoryRepository;
    @Autowired private IngredientRepository ingredientRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private ItemMenuRepository itemMenuRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // ========== Company ==========

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Company createActiveCompany(String slug, String timezone) {
        Company company = Company.builder()
                .slug(slug)
                .name("Test Company " + slug)
                .senderEmail("noreply@" + slug + ".test")
                .senderName("Test Company " + slug)
                .timezone(timezone)
                .active(true)
                .build();
        company = companyRepository.save(company);

        // Create ECOMMERCE license (active, long expiry) → allows all tests
        SystemLicense license = SystemLicense.builder()
                .company(company)
                .licenseKey("TEST-KEY-" + slug + "-" + System.nanoTime())
                .packageType(SystemLicense.PackageType.ECOMMERCE)
                .billingCycle(SystemLicense.BillingCycle.ANNUAL)
                .purchaseDate(LocalDate.now().minusDays(30))
                .expirationDate(LocalDate.now().plusYears(1))
                .installationDate(LocalDate.now().minusDays(30))
                .status(SystemLicense.LicenseStatus.ACTIVE)
                .maxUsers(50)
                .maxBranches(5)
                .build();
        licenseRepository.save(license);

        // Minimal system configuration
        SystemConfiguration config = SystemConfiguration.builder()
                .company(company)
                .restaurantName("Test Restaurant " + slug)
                .address("Test Address 123")
                .phone("5512345678")
                .email("test@" + slug + ".com")
                .taxRate(new BigDecimal("8.00"))
                .build();
        configRepository.save(config);

        // Create default business hours (Mon–Sun, 08:00–22:00, open)
        // Required by ShiftService.validateShiftDays / validateShiftHours
        for (DayOfWeek day : DayOfWeek.values()) {
            BusinessHours bh = BusinessHours.builder()
                    .dayOfWeek(day)
                    .openTime(LocalTime.of(8, 0))
                    .closeTime(LocalTime.of(22, 0))
                    .isClosed(false)
                    .systemConfiguration(config)
                    .build();
            businessHoursRepository.save(bh);
        }

        return company;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Company createBasicPackageCompany(String slug) {
        Company company = Company.builder()
                .slug(slug)
                .name("Basic Company " + slug)
                .senderEmail("noreply@" + slug + ".test")
                .senderName("Basic Company " + slug)
                .timezone("America/Mexico_City")
                .active(true)
                .build();
        company = companyRepository.save(company);

        SystemLicense license = SystemLicense.builder()
                .company(company)
                .licenseKey("BASIC-KEY-" + slug + "-" + System.nanoTime())
                .packageType(SystemLicense.PackageType.BASIC)
                .billingCycle(SystemLicense.BillingCycle.MONTHLY)
                .purchaseDate(LocalDate.now().minusDays(10))
                .expirationDate(LocalDate.now().plusMonths(1))
                .installationDate(LocalDate.now().minusDays(10))
                .status(SystemLicense.LicenseStatus.ACTIVE)
                .maxUsers(5)
                .maxBranches(1)
                .build();
        licenseRepository.save(license);

        SystemConfiguration config = SystemConfiguration.builder()
                .company(company)
                .restaurantName("Basic Restaurant " + slug)
                .address("Address 456")
                .phone("5598765432")
                .email("basic@" + slug + ".com")
                .taxRate(new BigDecimal("8.00"))
                .build();
        configRepository.save(config);

        return company;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Company createExpiredLicenseCompany(String slug) {
        Company company = Company.builder()
                .slug(slug)
                .name("Expired Company " + slug)
                .senderEmail("noreply@" + slug + ".test")
                .senderName("Expired Company " + slug)
                .timezone("America/Mexico_City")
                .active(true)
                .build();
        company = companyRepository.save(company);

        SystemLicense license = SystemLicense.builder()
                .company(company)
                .licenseKey("EXPIRED-KEY-" + slug + "-" + System.nanoTime())
                .packageType(SystemLicense.PackageType.ECOMMERCE)
                .billingCycle(SystemLicense.BillingCycle.MONTHLY)
                .purchaseDate(LocalDate.now().minusMonths(2))
                .expirationDate(LocalDate.now().minusDays(1))   // yesterday → expired
                .installationDate(LocalDate.now().minusMonths(2))
                .status(SystemLicense.LicenseStatus.EXPIRED)
                .maxUsers(5)
                .maxBranches(1)
                .build();
        licenseRepository.save(license);

        SystemConfiguration config = SystemConfiguration.builder()
                .company(company)
                .restaurantName("Expired Restaurant " + slug)
                .address("Expired St 789")
                .phone("5500000001")
                .email("expired@" + slug + ".com")
                .taxRate(new BigDecimal("8.00"))
                .build();
        configRepository.save(config);

        return company;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Company createSuspendedLicenseCompany(String slug) {
        Company company = Company.builder()
                .slug(slug)
                .name("Suspended Company " + slug)
                .senderEmail("noreply@" + slug + ".test")
                .senderName("Suspended Company " + slug)
                .timezone("America/Mexico_City")
                .active(true)
                .build();
        company = companyRepository.save(company);

        SystemLicense license = SystemLicense.builder()
                .company(company)
                .licenseKey("SUSPENDED-KEY-" + slug + "-" + System.nanoTime())
                .packageType(SystemLicense.PackageType.ECOMMERCE)
                .billingCycle(SystemLicense.BillingCycle.MONTHLY)
                .purchaseDate(LocalDate.now().minusDays(30))
                .expirationDate(LocalDate.now().plusMonths(1))
                .installationDate(LocalDate.now().minusDays(30))
                .status(SystemLicense.LicenseStatus.SUSPENDED)
                .maxUsers(5)
                .maxBranches(1)
                .build();
        licenseRepository.save(license);

        SystemConfiguration config = SystemConfiguration.builder()
                .company(company)
                .restaurantName("Suspended Restaurant " + slug)
                .address("Suspended Blvd 111")
                .phone("5500000002")
                .email("suspended@" + slug + ".com")
                .taxRate(new BigDecimal("8.00"))
                .build();
        configRepository.save(config);

        return company;
    }

    /**
     * Crea una company con licencia cuyo status en BD es ACTIVE, pero cuya
     * expirationDate ya pasó (ayer). Sirve para probar que LicenseValidationFilter
     * bloquea el acceso únicamente por comparación de fechas, sin necesitar que
     * el LicenseCheckJob haya actualizado el status a EXPIRED.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Company createActiveStatusExpiredDateCompany(String slug) {
        Company company = Company.builder()
                .slug(slug)
                .name("ActiveStatus-ExpiredDate Company " + slug)
                .senderEmail("noreply@" + slug + ".test")
                .senderName("ActiveStatus-ExpiredDate Company " + slug)
                .timezone("America/Mexico_City")
                .active(true)
                .build();
        company = companyRepository.save(company);

        SystemLicense license = SystemLicense.builder()
                .company(company)
                .licenseKey("ACTIVE-EXPIRED-KEY-" + slug + "-" + System.nanoTime())
                .packageType(SystemLicense.PackageType.ECOMMERCE)
                .billingCycle(SystemLicense.BillingCycle.MONTHLY)
                .purchaseDate(LocalDate.now().minusMonths(2))
                .expirationDate(LocalDate.now().minusDays(1))   // ayer → isExpired() == true
                .installationDate(LocalDate.now().minusMonths(2))
                .status(SystemLicense.LicenseStatus.ACTIVE)     // ← status sigue siendo ACTIVE en BD
                .maxUsers(5)
                .maxBranches(1)
                .build();
        licenseRepository.save(license);

        SystemConfiguration config = SystemConfiguration.builder()
                .company(company)
                .restaurantName("ActiveStatus-ExpiredDate Restaurant " + slug)
                .address("Test St 000")
                .phone("5500000009")
                .email("activeexpired@" + slug + ".com")
                .taxRate(new BigDecimal("8.00"))
                .build();
        configRepository.save(config);

        return company;
    }

    // ========== Employee ==========

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Employee createEmployee(Company company, String username, String password, String roleName) {
        Role role = roleRepository.findByNombreRol(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleName));

        Employee employee = new Employee();
        employee.setCompany(company);
        employee.setUsername(username);
        employee.setNombre("Test");
        employee.setApellido("User");
        employee.setEdad(25);
        employee.setContrasenia(passwordEncoder.encode(password));
        employee.setEnabled(true);
        employee.setRoles(Set.of(role));
        return employeeRepository.save(employee);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Employee createDisabledEmployee(Company company, String username, String password, String roleName) {
        Role role = roleRepository.findByNombreRol(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleName));

        Employee employee = new Employee();
        employee.setCompany(company);
        employee.setUsername(username);
        employee.setNombre("Disabled");
        employee.setApellido("Employee");
        employee.setEdad(30);
        employee.setContrasenia(passwordEncoder.encode(password));
        employee.setEnabled(false);  // disabled!
        employee.setRoles(Set.of(role));
        return employeeRepository.save(employee);
    }

    /**
     * Deletes a programmer employee (company=null) by username.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanUpProgrammerByUsername(String username) {
        employeeRepository.findByUsernameAndCompanyIsNull(username).ifPresent(emp -> {
            emp.getRoles().clear();
            employeeRepository.save(emp);
            employeeRepository.delete(emp);
            entityManager.flush();
        });
    }

    // ========== Customer ==========

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Customer createVerifiedCustomer(Company company, String username, String email, String phone, String password) {
        Customer customer = new Customer();
        customer.setCompany(company);
        customer.setFullName("Test Customer");
        customer.setUsername(username);
        customer.setEmail(email);
        customer.setPhone(phone);
        customer.setPassword(passwordEncoder.encode(password));
        customer.setActive(true);
        customer.setEmailVerified(true);
        return customerRepository.save(customer);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Customer createUnverifiedCustomer(Company company, String username, String email, String phone, String password) {
        Customer customer = new Customer();
        customer.setCompany(company);
        customer.setFullName("Unverified Customer");
        customer.setUsername(username);
        customer.setEmail(email);
        customer.setPhone(phone);
        customer.setPassword(passwordEncoder.encode(password));
        customer.setActive(true);
        customer.setEmailVerified(false);  // not verified
        return customerRepository.save(customer);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Customer createInactiveCustomer(Company company, String username, String email, String phone, String password) {
        Customer customer = new Customer();
        customer.setCompany(company);
        customer.setFullName("Inactive Customer");
        customer.setUsername(username);
        customer.setEmail(email);
        customer.setPhone(phone);
        customer.setPassword(passwordEncoder.encode(password));
        customer.setActive(false);  // inactive
        customer.setEmailVerified(true);
        return customerRepository.save(customer);
    }

    // ========== Catalog ==========

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Category createCategory(Company company, String name) {
        Category cat = Category.builder()
                .company(company).name(name).active(true).displayOrder(1).build();
        return categoryRepository.save(cat);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IngredientCategory createIngredientCategory(Company company, String name) {
        IngredientCategory ic = IngredientCategory.builder()
                .company(company).name(name).active(true).build();
        return ingredientCategoryRepository.save(ic);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Ingredient createIngredient(Company company, IngredientCategory category, String name) {
        Ingredient ing = Ingredient.builder()
                .company(company).category(category).name(name).active(true)
                .unitOfMeasure("KG").currency("MXN")
                .currentStock(BigDecimal.TEN).minStock(BigDecimal.ONE).build();
        return ingredientRepository.save(ing);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Supplier createSupplier(Company company, String name) {
        Supplier sup = Supplier.builder().company(company).name(name).active(true).build();
        return supplierRepository.save(sup);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ItemMenu createItemMenu(Company company, Category category, String name, BigDecimal price) {
        ItemMenu item = ItemMenu.builder()
                .company(company).category(category).name(name).price(price)
                .active(true).available(true).requiresPreparation(false)
                .isBuffet(true) // buffet = no ingredients needed
                .build();
        return itemMenuRepository.save(item);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RestaurantTable createRestaurantTable(Company company, int tableNumber, int capacity) {
        RestaurantTable table = RestaurantTable.builder()
                .company(company).tableNumber(tableNumber).capacity(capacity)
                .status(TableStatus.AVAILABLE).build();
        return restaurantTableRepository.save(table);
    }

    /**
     * Creates an Order in DELIVERED status, ready for payment processing tests.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order createDeliveredOrder(Company company, Employee employee,
                                      OrderType orderType, PaymentMethodType paymentMethod,
                                      String orderNumber) {
        Order order = Order.builder()
                .company(company)
                .employee(employee)
                .orderNumber(orderNumber)
                .orderType(orderType)
                .status(OrderStatus.DELIVERED)
                .paymentMethod(paymentMethod)
                .taxRate(new BigDecimal("16.00"))
                .subtotal(new BigDecimal("100.00"))
                .taxAmount(new BigDecimal("16.00"))
                .total(new BigDecimal("116.00"))
                .createdBy(employee.getUsername())
                .build();
        return orderRepository.save(order);
    }

    // ========== Order helpers ==========

    /**
     * Creates an Order in PENDING status for state-transition / idempotency tests.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order createPendingOrder(Company company, Employee employee,
                                    OrderType orderType, PaymentMethodType paymentMethod,
                                    String orderNumber) {
        Order order = Order.builder()
                .company(company)
                .employee(employee)
                .orderNumber(orderNumber)
                .orderType(orderType)
                .status(OrderStatus.PENDING)
                .paymentMethod(paymentMethod)
                .taxRate(new BigDecimal("16.00"))
                .subtotal(new BigDecimal("100.00"))
                .taxAmount(new BigDecimal("16.00"))
                .total(new BigDecimal("116.00"))
                .createdBy(employee.getUsername())
                .build();
        return orderRepository.save(order);
    }

    /**
     * Creates a non-buffet ItemMenu linked to an Ingredient (with recipe)
     * so stock-deduction logic is exercised during order creation.
     *
     * @param qtyPerUnit how much ingredient is consumed per 1 unit of this item
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ItemMenu createItemMenuWithIngredient(Company company, Category category,
                                                  String name, BigDecimal price,
                                                  Ingredient ingredient, BigDecimal qtyPerUnit) {
        ItemMenu item = ItemMenu.builder()
                .company(company).category(category).name(name).price(price)
                .active(true).available(true).requiresPreparation(true)
                .isBuffet(false)
                .build();

        ItemIngredient ii = ItemIngredient.builder()
                .ingredient(ingredient)
                .quantity(qtyPerUnit)
                .unit(ingredient.getUnitOfMeasure())
                .build();
        item.addIngredient(ii);

        return itemMenuRepository.save(item);
    }

    // ========== Cleanup ==========

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteCompanyAndAllData(Long companyId) {
        companyRepository.findById(companyId).ifPresent(company -> {
            Long cid = company.getIdCompany();

            // Null out FK → employee columns before deleting employees
            entityManager.createNativeQuery(
                    "UPDATE ingredient_categories SET created_by = NULL WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "UPDATE suppliers SET created_by = NULL WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            // Orders (must go before item_menu and employee deletes)
            entityManager.createNativeQuery(
                    "DELETE FROM order_detail_complements WHERE id_order_detail IN (SELECT id_order_detail FROM order_details WHERE id_order IN (SELECT id_order FROM orders WHERE company_id = :cid))")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM order_details WHERE id_order IN (SELECT id_order FROM orders WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM orders WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            // === Promotions (FK to company_id and item_menu) ===
            entityManager.createNativeQuery(
                    "DELETE FROM promotion_items WHERE id_promotion IN (SELECT id_promotion FROM promotions WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM promotions WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();

            // item_menu children first (cascade not guaranteed across sessions)
            entityManager.createNativeQuery(
                    "DELETE FROM item_menu_combo_items WHERE id_combo_menu IN (SELECT id_item_menu FROM item_menu WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM item_menu_availability WHERE id_item_menu IN (SELECT id_item_menu FROM item_menu WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM item_menu_complements WHERE id_item_menu IN (SELECT id_item_menu FROM item_menu WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM item_ingredients WHERE id_item_menu IN (SELECT id_item_menu FROM item_menu WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM item_menu WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            // complements (per company)
            entityManager.createNativeQuery(
                    "DELETE FROM complement_ingredients WHERE id_complement IN (SELECT id_complement FROM complements WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM complements WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            // ingredients (per company)
            entityManager.createNativeQuery("DELETE FROM ingredients WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            // suppliers and their category join (per company)
            entityManager.createNativeQuery(
                    "DELETE FROM supplier_ingredient_categories WHERE id_supplier IN (SELECT id_supplier FROM suppliers WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM suppliers WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            // ingredient categories (per company)
            entityManager.createNativeQuery("DELETE FROM ingredient_categories WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            // menu categories (per company)
            entityManager.createNativeQuery("DELETE FROM categories WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();

            // Delete shifts, restaurant tables, customers, employees (native SQL)
            entityManager.createNativeQuery(
                    "DELETE FROM employee_shifts WHERE shift_id IN (SELECT id FROM shifts WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM shift_work_days WHERE shift_id IN (SELECT id FROM shifts WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM shifts WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM reservations WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM restaurant_table WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM customers WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("UPDATE employee SET id_supervisor = NULL WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM employee_monthly_stats WHERE employee_id IN (SELECT id_empleado FROM employee WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM employee_roles WHERE id_empleado IN (SELECT id_empleado FROM employee WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM employee WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();

            // Delete all child tables of system_configuration (FK: system_configuration_id)
            entityManager.createNativeQuery(
                    "DELETE FROM business_hours WHERE system_configuration_id IN (SELECT id FROM system_configuration WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM system_payment_methods WHERE system_configuration_id IN (SELECT id FROM system_configuration WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM system_delivery_payment_methods WHERE system_configuration_id IN (SELECT id FROM system_configuration WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM social_networks WHERE system_configuration_id IN (SELECT id FROM system_configuration WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM system_configuration WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM system_license WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM daily_order_counters WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM companies WHERE id_company = :cid")
                    .setParameter("cid", cid).executeUpdate();
        });
    }

    /**
     * Finds a company by slug and deletes it and all its data.
     * Used in @BeforeEach to clean up orphans from previous failed runs.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanUpBySlug(String slug) {
        companyRepository.findBySlug(slug).ifPresent(company -> {
            Long cid = company.getIdCompany();

            // Shifts (JPA)
            List<Shift> shifts = shiftRepository.findByCompany(company);
            shifts.forEach(shift -> {
                shift.getEmployees().clear();
                shiftRepository.save(shift);
            });
            shiftRepository.deleteAll(shifts);
            entityManager.flush();

            // === Orders (must go before employee, restaurant_table, item_menu deletes) ===
            entityManager.createNativeQuery(
                    "DELETE FROM order_detail_complements WHERE id_order_detail IN (SELECT id_order_detail FROM order_details WHERE id_order IN (SELECT id_order FROM orders WHERE company_id = :cid))")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM order_details WHERE id_order IN (SELECT id_order FROM orders WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM orders WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();

            // Reservations (FK to restaurant_table)
            entityManager.createNativeQuery("DELETE FROM reservations WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();

            // Restaurant tables (after orders and reservations which FK to id_table)
            entityManager.createNativeQuery("DELETE FROM restaurant_table WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();

            // employee_monthly_stats (FK to employee)
            entityManager.createNativeQuery(
                    "DELETE FROM employee_monthly_stats WHERE employee_id IN (SELECT id_empleado FROM employee WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();

            // Null out FK → employee columns before deleting employees
            entityManager.createNativeQuery(
                    "UPDATE ingredient_categories SET created_by = NULL WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "UPDATE suppliers SET created_by = NULL WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            // Null out self-referential supervisor FK before deleting employees
            entityManager.createNativeQuery(
                    "UPDATE employee SET id_supervisor = NULL WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();

            employeeRepository.findByCompany(company).forEach(emp -> {
                emp.getRoles().clear();
                employeeRepository.save(emp);
                employeeRepository.delete(emp);
            });

            customerRepository.findByCompany(company).forEach(customerRepository::delete);

            entityManager.flush();

            // === Promotions (FK to company_id and item_menu) ===
            entityManager.createNativeQuery(
                    "DELETE FROM promotion_items WHERE id_promotion IN (SELECT id_promotion FROM promotions WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM promotions WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();

            // === Catalog data (FK to company_id) ===
            entityManager.createNativeQuery(
                    "DELETE FROM item_menu_combo_items WHERE id_combo_menu IN (SELECT id_item_menu FROM item_menu WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM item_menu_availability WHERE id_item_menu IN (SELECT id_item_menu FROM item_menu WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM item_menu_complements WHERE id_item_menu IN (SELECT id_item_menu FROM item_menu WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM item_ingredients WHERE id_item_menu IN (SELECT id_item_menu FROM item_menu WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM item_menu WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM complement_ingredients WHERE id_complement IN (SELECT id_complement FROM complements WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM complements WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM ingredients WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM supplier_ingredient_categories WHERE id_supplier IN (SELECT id_supplier FROM suppliers WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM suppliers WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM ingredient_categories WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM categories WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();

            entityManager.createNativeQuery(
                    "DELETE FROM business_hours WHERE system_configuration_id IN (SELECT id FROM system_configuration WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM system_payment_methods WHERE system_configuration_id IN (SELECT id FROM system_configuration WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM system_delivery_payment_methods WHERE system_configuration_id IN (SELECT id FROM system_configuration WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM social_networks WHERE system_configuration_id IN (SELECT id FROM system_configuration WHERE company_id = :cid)")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM system_configuration WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM system_license WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM daily_order_counters WHERE company_id = :cid")
                    .setParameter("cid", cid).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM companies WHERE id_company = :cid")
                    .setParameter("cid", cid).executeUpdate();
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteEmployee(Long employeeId) {
        employeeRepository.findById(employeeId).ifPresent(emp -> {
            emp.getRoles().clear();
            employeeRepository.save(emp);
            employeeRepository.delete(emp);
        });
    }

    /** Pre-cleanup: removes orphaned global (company=null) employee by username. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanUpGlobalEmployee(String username) {
        employeeRepository.findByUsernameAndCompanyIsNull(username).ifPresent(emp -> {
            emp.getRoles().clear();
            employeeRepository.save(emp);
            employeeRepository.delete(emp);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setSupervisor(Long employeeId, Long supervisorId) {
        Employee supervisor = employeeRepository.findById(supervisorId)
                .orElseThrow(() -> new IllegalStateException("Supervisor not found: " + supervisorId));
        employeeRepository.findById(employeeId).ifPresent(emp -> {
            emp.setSupervisor(supervisor);
            employeeRepository.save(emp);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setEmployeeEnabled(Long employeeId, boolean enabled) {
        employeeRepository.findById(employeeId).ifPresent(emp -> {
            emp.setEnabled(enabled);
            employeeRepository.save(emp);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setCustomerActive(Long customerId, boolean active) {
        customerRepository.findById(customerId).ifPresent(cust -> {
            cust.setActive(active);
            customerRepository.save(cust);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateLicenseStatus(Long companyId, SystemLicense.LicenseStatus status) {
        licenseRepository.findByCompanyId(companyId).ifPresent(lic -> {
            lic.setStatus(status);
            licenseRepository.save(lic);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setLicenseExpired(Long companyId) {
        licenseRepository.findByCompanyId(companyId).ifPresent(lic -> {
            lic.setExpirationDate(LocalDate.now().minusDays(1));
            lic.setStatus(SystemLicense.LicenseStatus.EXPIRED);
            licenseRepository.save(lic);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setLicenseMaxUsers(Long companyId, int maxUsers) {
        licenseRepository.findByCompanyId(companyId).ifPresent(lic -> {
            lic.setMaxUsers(maxUsers);
            licenseRepository.save(lic);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setLicensePackageType(Long companyId, SystemLicense.PackageType packageType) {
        licenseRepository.findByCompanyId(companyId).ifPresent(lic -> {
            lic.setPackageType(packageType);
            licenseRepository.save(lic);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setLicenseDaysUntilExpiry(Long companyId, int daysFromNow) {
        licenseRepository.findByCompanyId(companyId).ifPresent(lic -> {
            lic.setExpirationDate(LocalDate.now().plusDays(daysFromNow));
            if (daysFromNow > 0) {
                lic.setStatus(SystemLicense.LicenseStatus.ACTIVE);
            }
            licenseRepository.save(lic);
        });
    }
}
