package com.aatechsolutions.elgransazon.infrastructure.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.Objects;

/**
 * Custom UserDetails implementation for customers that includes companyId
 * in the identity check. This ensures that customers with the same username/email
 * from different companies are treated as different users for session management.
 * 
 * MULTI-TENANT: Without this, Spring Security's concurrent session control
 * would treat "john@example.com@company1" and "john@example.com@company2" as the same user,
 * causing one to be logged out when the other logs in.
 */
@Getter
public class CustomCustomerDetails extends User {

    /**
     * The company ID this customer is registered with.
     * Cannot be null - customers must always belong to a company.
     */
    private final Long companyId;

    /**
     * The customer's ID for reference
     */
    private final Long customerId;

    public CustomCustomerDetails(String username, String password, boolean enabled,
                                  Collection<? extends GrantedAuthority> authorities,
                                  Long companyId, Long customerId) {
        super(username, password, enabled, true, true, true, authorities);
        this.companyId = companyId;
        this.customerId = customerId;
    }

    /**
     * Two CustomCustomerDetails are equal if they have the same username AND companyId.
     * This is critical for Spring Security's session management to work correctly
     * in a multi-tenant environment.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        CustomCustomerDetails other = (CustomCustomerDetails) obj;
        
        // Compare username (case-insensitive) AND companyId
        return Objects.equals(getUsername().toLowerCase(), other.getUsername().toLowerCase())
                && Objects.equals(companyId, other.companyId);
    }

    /**
     * Hash code based on username AND companyId.
     * Must be consistent with equals().
     */
    @Override
    public int hashCode() {
        return Objects.hash(getUsername().toLowerCase(), companyId);
    }

    @Override
    public String toString() {
        return "CustomCustomerDetails{" +
                "username='" + getUsername() + '\'' +
                ", companyId=" + companyId +
                ", customerId=" + customerId +
                ", enabled=" + isEnabled() +
                ", authorities=" + getAuthorities() +
                '}';
    }
}
