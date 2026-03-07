package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.domain.repository.CustomerRepository;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service implementation for Customer management.
 * 
 * MULTI-TENANT: Customers are now scoped per company.
 * Each customer registration is tied to a specific company.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    // ========== MULTI-TENANT Methods ==========

    @Override
    public List<Customer> findAllByCompany(Company company) {
        log.debug("Finding all customers for company: {}", company.getIdCompany());
        return customerRepository.findByCompany(company);
    }

    @Override
    public List<Customer> findAllActiveByCompany(Company company) {
        log.debug("Finding all active customers for company: {}", company.getIdCompany());
        return customerRepository.findByCompanyAndActiveTrue(company);
    }

    @Override
    public Optional<Customer> findByIdAndCompany(Long id, Company company) {
        log.debug("Finding customer by ID: {} and company: {}", id, company.getIdCompany());
        return customerRepository.findByIdCustomerAndCompany(id, company);
    }

    @Override
    public Optional<Customer> findByEmailAndCompany(String email, Company company) {
        log.debug("Finding customer by email: {} and company: {}", email, company.getIdCompany());
        return customerRepository.findByEmailIgnoreCaseAndCompany(email, company);
    }

    @Override
    public Optional<Customer> findByUsernameAndCompany(String username, Company company) {
        log.debug("Finding customer by username: {} and company: {}", username, company.getIdCompany());
        return customerRepository.findByUsernameIgnoreCaseAndCompany(username, company);
    }

    @Override
    public Optional<Customer> findByUsernameOrEmailAndCompany(String usernameOrEmail, Company company) {
        log.debug("Finding customer by username or email: {} and company: {}", usernameOrEmail, company.getIdCompany());
        return customerRepository.findByUsernameOrEmailAndCompany(usernameOrEmail, company);
    }

    @Override
    @Transactional
    public Customer create(Customer customer, Company company) {
        log.info("Creating new customer: {} for company: {}", customer.getEmail(), company.getIdCompany());
        
        // Validate username doesn't exist in this company
        if (customerRepository.existsByUsernameIgnoreCaseAndCompany(customer.getUsername(), company)) {
            throw new IllegalArgumentException("El nombre de usuario ya está registrado en esta empresa");
        }
        
        // Validate email doesn't exist in this company
        if (customerRepository.existsByEmailIgnoreCaseAndCompany(customer.getEmail(), company)) {
            throw new IllegalArgumentException("El correo electrónico ya está registrado en esta empresa");
        }
        
        // Validate phone doesn't exist in this company
        if (customerRepository.existsByPhoneAndCompany(customer.getPhone(), company)) {
            throw new IllegalArgumentException("El teléfono ya está registrado en esta empresa");
        }
        
        // Hash password
        if (customer.getPassword() == null || customer.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("La contraseña es requerida");
        }
        customer.setPassword(passwordEncoder.encode(customer.getPassword()));
        
        // Set company for multi-tenant
        customer.setCompany(company);
        
        // Save customer
        Customer saved = customerRepository.save(customer);
        log.info("Customer created successfully with ID: {} for company: {}", saved.getIdCustomer(), company.getIdCompany());
        
        return saved;
    }

    @Override
    @Transactional
    public Customer update(Long id, Customer customer, Company company) {
        log.info("Updating customer with ID: {} for company: {}", id, company.getIdCompany());
        
        Customer existing = customerRepository.findByIdCustomerAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado en esta empresa"));
        
        // Update all fields from the customer object
        existing.setFullName(customer.getFullName());
        existing.setUsername(customer.getUsername());
        existing.setPhone(customer.getPhone());
        
        // If password is provided, it should already be encoded by the controller
        if (customer.getPassword() != null && !customer.getPassword().trim().isEmpty()) {
            existing.setPassword(customer.getPassword());
        }
        
        Customer updated = customerRepository.save(existing);
        log.info("Customer updated successfully: {} for company: {}", id, company.getIdCompany());
        
        return updated;
    }

    @Override
    @Transactional
    public void delete(Long id, Company company) {
        log.info("Deleting customer with ID: {} for company: {}", id, company.getIdCompany());
        
        Customer customer = customerRepository.findByIdCustomerAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado en esta empresa"));
        
        customerRepository.delete(customer);
        log.info("Customer deleted successfully: {} for company: {}", id, company.getIdCompany());
    }

    @Override
    public boolean existsByUsernameAndCompany(String username, Company company) {
        return customerRepository.existsByUsernameIgnoreCaseAndCompany(username, company);
    }

    @Override
    public boolean existsByEmailAndCompany(String email, Company company) {
        return customerRepository.existsByEmailIgnoreCaseAndCompany(email, company);
    }

    @Override
    public boolean existsByPhoneAndCompany(String phone, Company company) {
        return customerRepository.existsByPhoneAndCompany(phone, company);
    }

    @Override
    public boolean usernameExistsInEmployees(String username, Company company) {
        log.debug("Checking if username exists in employees table for company: {}", company.getIdCompany());
        return employeeRepository.existsByUsernameIgnoreCaseAndCompany(username, company);
    }

    @Override
    @Transactional
    public void updateLastAccess(String usernameOrEmail, Company company) {
        log.debug("Updating last access for customer: {} in company: {}", usernameOrEmail, company.getIdCompany());
        
        customerRepository.findByUsernameOrEmailAndCompany(usernameOrEmail, company).ifPresent(customer -> {
            customer.updateLastAccess();
            customerRepository.save(customer);
        });
    }

    @Override
    @Transactional
    public Customer activate(Long id, Company company) {
        log.info("Activating customer with ID: {} for company: {}", id, company.getIdCompany());
        
        Customer customer = customerRepository.findByIdCustomerAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado en esta empresa"));
        
        customer.setActive(true);
        Customer activated = customerRepository.save(customer);
        
        log.info("Customer activated successfully: {} for company: {}", id, company.getIdCompany());
        return activated;
    }

    @Override
    @Transactional
    public Customer deactivate(Long id, Company company) {
        log.info("Deactivating customer with ID: {} for company: {}", id, company.getIdCompany());
        
        Customer customer = customerRepository.findByIdCustomerAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado en esta empresa"));
        
        customer.setActive(false);
        Customer deactivated = customerRepository.save(customer);
        
        log.info("Customer deactivated successfully: {} for company: {}", id, company.getIdCompany());
        return deactivated;
    }

    @Override
    public long countByCompany(Company company) {
        return customerRepository.countByCompany(company);
    }

    @Override
    public long countActiveByCompany(Company company) {
        return customerRepository.countByCompanyAndActiveTrue(company);
    }

    // ========== Legacy Methods (deprecated - for backwards compatibility) ==========

    @Override
    @Deprecated
    public List<Customer> findAll() {
        log.warn("Using deprecated findAll() method - use findAllByCompany() instead");
        return customerRepository.findAll();
    }

    @Override
    @Deprecated
    public List<Customer> findAllActive() {
        log.warn("Using deprecated findAllActive() method - use findAllActiveByCompany() instead");
        return customerRepository.findByActiveTrue();
    }

    @Override
    @Deprecated
    public Optional<Customer> findById(Long id) {
        log.warn("Using deprecated findById() method - use findByIdAndCompany() instead");
        return customerRepository.findById(id);
    }

    @Override
    @Deprecated
    public Optional<Customer> findByEmail(String email) {
        log.warn("Using deprecated findByEmail() method - use findByEmailAndCompany() instead");
        return customerRepository.findByEmailIgnoreCase(email);
    }

    @Override
    @Deprecated
    public Optional<Customer> findByUsername(String username) {
        log.warn("Using deprecated findByUsername() method - use findByUsernameAndCompany() instead");
        return customerRepository.findByUsernameIgnoreCase(username);
    }

    @Override
    @Deprecated
    public Optional<Customer> findByUsernameOrEmail(String usernameOrEmail) {
        log.warn("Using deprecated findByUsernameOrEmail() method - use findByUsernameOrEmailAndCompany() instead");
        return customerRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(usernameOrEmail, usernameOrEmail);
    }

    @Override
    @Deprecated
    @Transactional
    public Customer create(Customer customer) {
        log.warn("Using deprecated create(Customer) method - use create(Customer, Company) instead");
        
        // Validate username doesn't exist globally (legacy behavior)
        if (customerRepository.existsByUsernameIgnoreCase(customer.getUsername())) {
            throw new IllegalArgumentException("El nombre de usuario ya está registrado");
        }
        
        // Validate email doesn't exist globally
        if (customerRepository.existsByEmailIgnoreCase(customer.getEmail())) {
            throw new IllegalArgumentException("El correo electrónico ya está registrado");
        }
        
        // Validate phone doesn't exist globally
        if (customerRepository.existsByPhone(customer.getPhone())) {
            throw new IllegalArgumentException("El teléfono ya está registrado");
        }
        
        // Hash password
        if (customer.getPassword() == null || customer.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("La contraseña es requerida");
        }
        customer.setPassword(passwordEncoder.encode(customer.getPassword()));
        
        // Save customer
        Customer saved = customerRepository.save(customer);
        log.info("Customer created successfully with ID: {}", saved.getIdCustomer());
        
        return saved;
    }

    @Override
    @Deprecated
    @Transactional
    public Customer update(Long id, Customer customer) {
        log.warn("Using deprecated update(Long, Customer) method - use update(Long, Customer, Company) instead");
        
        Customer existing = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
        
        existing.setFullName(customer.getFullName());
        existing.setUsername(customer.getUsername());
        existing.setPhone(customer.getPhone());
        
        if (customer.getPassword() != null && !customer.getPassword().trim().isEmpty()) {
            existing.setPassword(customer.getPassword());
        }
        
        Customer updated = customerRepository.save(existing);
        log.info("Customer updated successfully: {}", id);
        
        return updated;
    }

    @Override
    @Deprecated
    @Transactional
    public void delete(Long id) {
        log.warn("Using deprecated delete(Long) method - use delete(Long, Company) instead");
        
        if (!customerRepository.existsById(id)) {
            throw new IllegalArgumentException("Cliente no encontrado");
        }
        
        customerRepository.deleteById(id);
        log.info("Customer deleted successfully: {}", id);
    }

    @Override
    @Deprecated
    public boolean existsByUsername(String username) {
        log.warn("Using deprecated existsByUsername() method - use existsByUsernameAndCompany() instead");
        return customerRepository.existsByUsernameIgnoreCase(username);
    }

    @Override
    @Deprecated
    public boolean existsByEmail(String email) {
        log.warn("Using deprecated existsByEmail() method - use existsByEmailAndCompany() instead");
        return customerRepository.existsByEmailIgnoreCase(email);
    }

    @Override
    @Deprecated
    public boolean existsByPhone(String phone) {
        log.warn("Using deprecated existsByPhone() method - use existsByPhoneAndCompany() instead");
        return customerRepository.existsByPhone(phone);
    }

    @Override
    @Deprecated
    public boolean usernameExistsInEmployees(String username) {
        log.warn("Using deprecated usernameExistsInEmployees() method - use usernameExistsInEmployees(String, Company) instead");
        return employeeRepository.existsByUsername(username);
    }

    @Override
    @Deprecated
    @Transactional
    public void updateLastAccess(String usernameOrEmail) {
        log.warn("Using deprecated updateLastAccess() method - use updateLastAccess(String, Company) instead");
        
        customerRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(usernameOrEmail, usernameOrEmail).ifPresent(customer -> {
            customer.updateLastAccess();
            customerRepository.save(customer);
        });
    }

    @Override
    @Deprecated
    @Transactional
    public Customer activate(Long id) {
        log.warn("Using deprecated activate() method - use activate(Long, Company) instead");
        
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
        
        customer.setActive(true);
        Customer activated = customerRepository.save(customer);
        
        log.info("Customer activated successfully: {}", id);
        return activated;
    }

    @Override
    @Deprecated
    @Transactional
    public Customer deactivate(Long id) {
        log.warn("Using deprecated deactivate() method - use deactivate(Long, Company) instead");
        
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
        
        customer.setActive(false);
        Customer deactivated = customerRepository.save(customer);
        
        log.info("Customer deactivated successfully: {}", id);
        return deactivated;
    }
}
