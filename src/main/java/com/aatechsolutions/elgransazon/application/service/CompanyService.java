package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for Company management
 */
public interface CompanyService {

    /**
     * Find company by ID
     */
    Optional<Company> findById(Long id);

    /**
     * Find company by slug (subdomain)
     */
    Optional<Company> findBySlug(String slug);

    /**
     * Find company by custom domain
     */
    Optional<Company> findByCustomDomain(String customDomain);

    /**
     * Find company by domain or slug from request host
     * This method analyzes the host to determine if it's:
     * - A custom domain (e.g., www.pizzamax.com)
     * - A subdomain (e.g., pizzamax.misistema.com)
     * - localhost for development
     */
    Optional<Company> findByHost(String host);

    /**
     * Find all companies
     */
    List<Company> findAll();

    /**
     * Find all active companies
     */
    List<Company> findAllActive();

    /**
     * Create a new company with default configuration, license, and admin
     */
    Company create(Company company);

    /**
     * Create a new company with default configuration, license, and admin
     * Overloaded method for creating company with individual parameters
     *
     * @param slug Company slug (subdomain)
     * @param name Company name
     * @param customDomain Optional custom domain
     * @param senderEmail Email for SendGrid (Company contact email)
     * @param senderName Sender name for emails
     * @param contactEmail Public contact email
     * @param contactPhone Contact phone number
     * @param address Company physical address
     * @param rfc Tax identification number (RFC)
     * @param adminUsername Username for admin login
     * @param adminFirstName Admin first name
     * @param adminLastName Admin last name
     * @param adminPassword Admin password
     * @return Created company
     */
    Company create(String slug, String name, String customDomain,
                   String senderEmail, String senderName, String contactEmail,
                   String contactPhone, String address, String rfc, String timezone,
                   String adminUsername, String adminFirstName, String adminLastName, String adminPassword,
                   boolean freeTrial, String packageType, String billingCycle, int licenseMonths, Double licenseAmount,
                   java.math.BigDecimal taxRate,
                   String performedBy);

    /**
     * Update an existing company
     */
    Company update(Long id, Company company);

    /**
     * Activate/deactivate a company
     */
    Company setActive(Long id, boolean active);

    /**
     * Delete a company (use with caution - cascades to all data)
     */
    void delete(Long id);

    /**
     * Check if slug exists
     */
    boolean existsBySlug(String slug);

    /**
     * Check if custom domain exists
     */
    boolean existsByCustomDomain(String customDomain);

    /**
     * Count all companies
     */
    long count();
}
