package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.CustomerService;
import com.aatechsolutions.elgransazon.application.service.LicenseService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for managing registered customers (Admin/Manager only)
 * Only accessible when the system has the ECOMMERCE package
 * MULTI-TENANT: Only shows customers registered in the current company
 */
@Controller
@RequestMapping("/admin/customers")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class AdminCustomerController {

    private final CustomerService customerService;
    private final LicenseService licenseService;

    /**
     * Helper to get current company from context
     */
    private Company getCurrentCompany() {
        Company company = CompanyContext.getCurrentCompany();
        if (company == null) {
            throw new IllegalStateException("No company context available");
        }
        return company;
    }

    /**
     * Display all registered customers with filtering options
     * MULTI-TENANT: Only shows customers for current company
     */
    @GetMapping
    public String listCustomers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Model model) {

        log.debug("Admin accessing customers list with status: {}, search: {}", status, search);

        // Verify ECOMMERCE package access
        if (!licenseService.hasCustomerModuleAccess()) {
            return "redirect:/admin/dashboard";
        }

        try {
            Company currentCompany = getCurrentCompany();
            
            // MULTI-TENANT: Get customers for current company only
            List<Customer> allCustomers = customerService.findAllByCompany(currentCompany);

            // Apply filters
            List<Customer> filteredCustomers = allCustomers;

            // Filter by status
            if (status != null && !status.isEmpty() && !status.equals("ALL")) {
                if (status.equals("ACTIVE")) {
                    filteredCustomers = filteredCustomers.stream()
                            .filter(c -> Boolean.TRUE.equals(c.getActive()))
                            .toList();
                } else if (status.equals("INACTIVE")) {
                    filteredCustomers = filteredCustomers.stream()
                            .filter(c -> !Boolean.TRUE.equals(c.getActive()))
                            .toList();
                } else if (status.equals("VERIFIED")) {
                    filteredCustomers = filteredCustomers.stream()
                            .filter(c -> Boolean.TRUE.equals(c.getEmailVerified()))
                            .toList();
                } else if (status.equals("UNVERIFIED")) {
                    filteredCustomers = filteredCustomers.stream()
                            .filter(c -> !Boolean.TRUE.equals(c.getEmailVerified()))
                            .toList();
                }
            }

            // Filter by search term
            if (search != null && !search.trim().isEmpty()) {
                String searchLower = search.trim().toLowerCase();
                filteredCustomers = filteredCustomers.stream()
                        .filter(c -> c.getFullName().toLowerCase().contains(searchLower)
                                || c.getEmail().toLowerCase().contains(searchLower)
                                || c.getUsername().toLowerCase().contains(searchLower)
                                || c.getPhone().contains(searchLower))
                        .toList();
            }

            // Calculate stats
            long totalCount = allCustomers.size();
            long activeCount = allCustomers.stream().filter(c -> Boolean.TRUE.equals(c.getActive())).count();
            long inactiveCount = totalCount - activeCount;
            long verifiedCount = allCustomers.stream().filter(c -> Boolean.TRUE.equals(c.getEmailVerified())).count();
            long unverifiedCount = totalCount - verifiedCount;

            model.addAttribute("customers", filteredCustomers);
            model.addAttribute("currentFilter", status != null ? status : "ALL");
            model.addAttribute("searchTerm", search != null ? search : "");
            model.addAttribute("totalCount", totalCount);
            model.addAttribute("activeCount", activeCount);
            model.addAttribute("inactiveCount", inactiveCount);
            model.addAttribute("verifiedCount", verifiedCount);
            model.addAttribute("unverifiedCount", unverifiedCount);

            return "admin/customers/list";

        } catch (Exception e) {
            log.error("Error loading customers list", e);
            model.addAttribute("customers", List.of());
            model.addAttribute("currentFilter", "ALL");
            model.addAttribute("searchTerm", "");
            model.addAttribute("totalCount", 0L);
            model.addAttribute("activeCount", 0L);
            model.addAttribute("inactiveCount", 0L);
            model.addAttribute("verifiedCount", 0L);
            model.addAttribute("unverifiedCount", 0L);
            model.addAttribute("errorMessage", "Error al cargar los clientes: " + e.getMessage());
            return "admin/customers/list";
        }
    }

    /**
     * Activate a customer (AJAX endpoint)
     * MULTI-TENANT: Only activates customers from current company
     */
    @PostMapping(value = "/{id}/activate", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> activateCustomer(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();

        // Validate ECOMMERCE package
        if (!licenseService.hasCustomerModuleAccess()) {
            response.put("success", false);
            response.put("message", "No cuenta con el paquete correcto para realizar esta actualización. Se requiere el paquete E-Commerce.");
            response.put("packageError", true);
            return ResponseEntity.status(403).body(response);
        }

        try {
            Company currentCompany = getCurrentCompany();
            log.info("Attempting to activate customer with ID: {} for company: {}", id, currentCompany.getIdCompany());
            
            // MULTI-TENANT: Activate customer within company context
            Customer customer = customerService.activate(id, currentCompany);
            response.put("success", true);
            response.put("message", "Cliente " + customer.getFullName() + " activado exitosamente");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error activating customer {} - Type: {} - Message: {}", id, e.getClass().getSimpleName(), e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error al activar el cliente: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Deactivate a customer (AJAX endpoint)
     * MULTI-TENANT: Only deactivates customers from current company
     */
    @PostMapping(value = "/{id}/deactivate", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deactivateCustomer(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();

        // Validate ECOMMERCE package
        if (!licenseService.hasCustomerModuleAccess()) {
            response.put("success", false);
            response.put("message", "No cuenta con el paquete correcto para realizar esta actualización. Se requiere el paquete E-Commerce.");
            response.put("packageError", true);
            return ResponseEntity.status(403).body(response);
        }

        try {
            Company currentCompany = getCurrentCompany();
            
            // MULTI-TENANT: Deactivate customer within company context
            Customer customer = customerService.deactivate(id, currentCompany);
            response.put("success", true);
            response.put("message", "Cliente " + customer.getFullName() + " desactivado exitosamente");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error deactivating customer {} - Type: {} - Message: {}", id, e.getClass().getSimpleName(), e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error al desactivar el cliente: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get customer details (AJAX endpoint)
     * MULTI-TENANT: Only returns customers from current company
     */
    @GetMapping(value = "/{id}", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCustomerDetails(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            Company currentCompany = getCurrentCompany();
            
            // MULTI-TENANT: Find customer within company context
            Optional<Customer> customerOpt = customerService.findByIdAndCompany(id, currentCompany);
            if (customerOpt.isPresent()) {
                Customer customer = customerOpt.get();
                Map<String, Object> customerData = new HashMap<>();
                customerData.put("idCustomer", customer.getIdCustomer());
                customerData.put("fullName", customer.getFullName());
                customerData.put("username", customer.getUsername());
                customerData.put("email", customer.getEmail());
                customerData.put("phone", customer.getPhone());
                customerData.put("active", customer.getActive());
                customerData.put("emailVerified", customer.getEmailVerified());
                customerData.put("createdAt", customer.getCreatedAt() != null ? customer.getCreatedAt().toString() : null);
                customerData.put("lastAccess", customer.getLastAccess() != null ? customer.getLastAccess().toString() : null);
                
                response.put("success", true);
                response.put("customer", customerData);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Cliente no encontrado");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("Error getting customer details {}", id, e);
            response.put("success", false);
            response.put("message", "Error al obtener los detalles del cliente: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
