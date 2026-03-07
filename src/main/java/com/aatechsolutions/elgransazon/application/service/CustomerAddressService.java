package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.domain.entity.CustomerAddress;
import com.aatechsolutions.elgransazon.domain.repository.CustomerAddressRepository;
import com.aatechsolutions.elgransazon.domain.repository.CustomerRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerAddressService {

    private final CustomerAddressRepository addressRepository;
    private final CustomerRepository customerRepository;

    /**
     * Get all active addresses for a customer
     */
    @Transactional(readOnly = true)
    public List<CustomerAddress> getAddressesByCustomerId(Long customerId) {
        Company company = CompanyContext.requireCurrentCompany();
        return addressRepository.findByCustomerIdCustomerAndCompanyAndActiveTrueOrderByIsDefaultDescCreatedAtDesc(customerId, company);
    }

    /**
     * Get default address for a customer
     */
    @Transactional(readOnly = true)
    public Optional<CustomerAddress> getDefaultAddress(Long customerId) {
        Company company = CompanyContext.requireCurrentCompany();
        return addressRepository.findByCustomerIdCustomerAndCompanyAndIsDefaultTrueAndActiveTrue(customerId, company);
    }

    /**
     * Get address by id (with customer validation)
     */
    @Transactional(readOnly = true)
    public Optional<CustomerAddress> getAddressById(Long addressId, Long customerId) {
        Company company = CompanyContext.requireCurrentCompany();
        return addressRepository.findByIdAddressAndCustomerIdCustomerAndCompanyAndActiveTrue(addressId, customerId, company);
    }

    /**
     * Create a new address
     */
    public CustomerAddress createAddress(Long customerId, String label, String address, 
                                         String reference, Double latitude, Double longitude, 
                                         boolean setAsDefault) {
        Company company = CompanyContext.requireCurrentCompany();
        Customer customer = customerRepository.findByIdCustomerAndCompany(customerId, company)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        // If this is the first address or setAsDefault is true, make it default
        boolean isFirstAddress = addressRepository.countByCustomerIdCustomerAndCompanyAndActiveTrue(customerId, company) == 0;
        boolean shouldBeDefault = isFirstAddress || setAsDefault;

        if (shouldBeDefault) {
            // Reset other defaults
            addressRepository.resetDefaultForCustomerAndCompany(customerId, company);
        }

        CustomerAddress newAddress = CustomerAddress.builder()
                .customer(customer)
                .company(company)
                .label(label)
                .address(address)
                .reference(reference)
                .latitude(latitude)
                .longitude(longitude)
                .isDefault(shouldBeDefault)
                .active(true)
                .build();

        return addressRepository.save(newAddress);
    }

    /**
     * Update an existing address
     */
    public CustomerAddress updateAddress(Long addressId, Long customerId, String label, 
                                         String address, String reference, 
                                         Double latitude, Double longitude, 
                                         boolean setAsDefault) {
        Company company = CompanyContext.requireCurrentCompany();
        CustomerAddress existingAddress = addressRepository
                .findByIdAddressAndCustomerIdCustomerAndCompanyAndActiveTrue(addressId, customerId, company)
                .orElseThrow(() -> new RuntimeException("Dirección no encontrada"));

        existingAddress.setLabel(label);
        existingAddress.setAddress(address);
        existingAddress.setReference(reference);
        existingAddress.setLatitude(latitude);
        existingAddress.setLongitude(longitude);

        if (setAsDefault && !existingAddress.getIsDefault()) {
            addressRepository.resetDefaultForCustomerAndCompany(customerId, company);
            existingAddress.setIsDefault(true);
        }

        return addressRepository.save(existingAddress);
    }

    /**
     * Set an address as default
     */
    public void setAsDefault(Long addressId, Long customerId) {
        Company company = CompanyContext.requireCurrentCompany();
        CustomerAddress address = addressRepository
                .findByIdAddressAndCustomerIdCustomerAndCompanyAndActiveTrue(addressId, customerId, company)
                .orElseThrow(() -> new RuntimeException("Dirección no encontrada"));

        addressRepository.resetDefaultForCustomerAndCompany(customerId, company);
        address.setIsDefault(true);
        addressRepository.save(address);
    }

    /**
     * Delete an address (soft delete)
     */
    public boolean deleteAddress(Long addressId, Long customerId) {
        Company company = CompanyContext.requireCurrentCompany();
        // Check if it's the default address
        Optional<CustomerAddress> addressOpt = addressRepository
                .findByIdAddressAndCustomerIdCustomerAndCompanyAndActiveTrue(addressId, customerId, company);
        
        if (addressOpt.isEmpty()) {
            return false;
        }

        CustomerAddress address = addressOpt.get();
        boolean wasDefault = address.getIsDefault();

        int deleted = addressRepository.softDeleteByIdAndCustomerId(addressId, customerId);
        
        // If deleted address was default, set another one as default
        if (deleted > 0 && wasDefault) {
            List<CustomerAddress> remaining = getAddressesByCustomerId(customerId);
            if (!remaining.isEmpty()) {
                CustomerAddress newDefault = remaining.get(0);
                newDefault.setIsDefault(true);
                addressRepository.save(newDefault);
            }
        }

        return deleted > 0;
    }

    /**
     * Count addresses for a customer
     */
    @Transactional(readOnly = true)
    public long countAddresses(Long customerId) {
        Company company = CompanyContext.requireCurrentCompany();
        return addressRepository.countByCustomerIdCustomerAndCompanyAndActiveTrue(customerId, company);
    }
}
