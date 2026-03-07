package com.aatechsolutions.elgransazon.infrastructure.context;

import com.aatechsolutions.elgransazon.domain.entity.Company;

/**
 * Thread-local context for storing the current company during a request.
 * This allows services to access the current company without passing it through every method.
 * 
 * Usage:
 * - CompanyContextFilter sets the company at the start of each request
 * - Services use CompanyContext.getCurrentCompany() to get the current company
 * - The context is cleared at the end of each request
 * 
 * IMPORTANT: Always call clear() at the end of the request to prevent memory leaks
 */
public class CompanyContext {

    private static final ThreadLocal<Company> currentCompany = new ThreadLocal<>();

    /**
     * Set the current company for this thread/request
     */
    public static void setCurrentCompany(Company company) {
        currentCompany.set(company);
    }

    /**
     * Get the current company for this thread/request
     * @return Company or null if not set
     */
    public static Company getCurrentCompany() {
        return currentCompany.get();
    }

    /**
     * Get the current company ID
     * @return Company ID or null if no company is set
     */
    public static Long getCurrentCompanyId() {
        Company company = currentCompany.get();
        return company != null ? company.getIdCompany() : null;
    }

    /**
     * Check if a company context is set
     */
    public static boolean hasCompany() {
        return currentCompany.get() != null;
    }

    /**
     * Clear the company context (call at end of request)
     */
    public static void clear() {
        currentCompany.remove();
    }

    /**
     * Get the current company or throw an exception if not set
     * Use this when company is required
     */
    public static Company requireCurrentCompany() {
        Company company = currentCompany.get();
        if (company == null) {
            throw new IllegalStateException("No company context is set. Ensure CompanyContextFilter is active.");
        }
        return company;
    }

    /**
     * Get the current company ID or throw an exception if not set
     */
    public static Long requireCurrentCompanyId() {
        return requireCurrentCompany().getIdCompany();
    }
}
