package com.aatechsolutions.elgransazon.infrastructure.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.Objects;

/**
 * Custom UserDetails implementation for employees that includes companyId
 * in the identity check. This ensures that employees with the same username
 * from different companies are treated as different users for session management.
 * 
 * MULTI-TENANT: Without this, Spring Security's concurrent session control
 * would treat "demian@company1" and "demian@company2" as the same user,
 * causing one to be logged out when the other logs in.
 */
@Getter
public class CustomEmployeeDetails extends User {

    /**
     * The company ID this employee belongs to.
     * Can be null for PROGRAMMER role (global user).
     */
    private final Long companyId;

    /**
     * The employee's ID for reference
     */
    private final Long employeeId;

    public CustomEmployeeDetails(String username, String password, boolean enabled,
                                  Collection<? extends GrantedAuthority> authorities,
                                  Long companyId, Long employeeId) {
        super(username, password, enabled, true, true, true, authorities);
        this.companyId = companyId;
        this.employeeId = employeeId;
    }

    /**
     * Two CustomEmployeeDetails are equal if they have the same username AND companyId.
     * This is critical for Spring Security's session management to work correctly
     * in a multi-tenant environment.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        CustomEmployeeDetails other = (CustomEmployeeDetails) obj;
        
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
        return "CustomEmployeeDetails{" +
                "username='" + getUsername() + '\'' +
                ", companyId=" + companyId +
                ", employeeId=" + employeeId +
                ", enabled=" + isEnabled() +
                ", authorities=" + getAuthorities() +
                '}';
    }
}
