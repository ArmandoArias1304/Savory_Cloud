package com.aatechsolutions.elgransazon.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for stateful session management
 * Configures authentication, authorization, and session handling
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomAuthenticationSuccessHandler authenticationSuccessHandler;
    private final CustomAuthenticationFailureHandler authenticationFailureHandler;
    private final CustomLogoutSuccessHandler logoutSuccessHandler;
    private final CompanyContextFilter companyContextFilter;
    private final UserValidationFilter userValidationFilter;
    private final LicenseInterceptor licenseInterceptor;
    private final PackageAccessFilter packageAccessFilter;
    private final LicenseValidationFilter licenseValidationFilter;
    private final UserLimitEnforcementFilter userLimitEnforcementFilter;

    /**
     * Authentication manager bean
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * Session registry bean for tracking active sessions
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * Security filter chain configuration
     * Configures stateful session management and authorization rules
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/home", "/home/menu", "/login", "/client/login", "/client/register", 
                                "/client/verify-email", "/client/forgot-password", "/client/reset-password", 
                                "/client/password-reset/**", "/help", "/support", "/helpClient", 
                                "/autofactura/**",
                                "/error", "/errores/**", "/css/**", "/js/**", 
                                "/images/**", "/flyers/**", "/system-landing/**", "/license-expired", "/favicon.ico",
                                "/.well-known/**").permitAll()
                        .requestMatchers("/programmer/**").hasRole("PROGRAMMER")
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/waiter/**").hasRole("WAITER")
                        .requestMatchers("/chef/**", "/barista/**").hasAnyRole("CHEF", "BARISTA")
                        .requestMatchers("/cashier/**").hasRole("CASHIER")
                        .requestMatchers("/delivery/**").hasRole("DELIVERY")
                        .requestMatchers("/client/**").hasRole("CLIENT")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/perform_login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(authenticationSuccessHandler)
                        .failureHandler(authenticationFailureHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(logoutSuccessHandler)
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                // Session management
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                        .expiredUrl("/login")  // Redirect to login without message
                        .sessionRegistry(sessionRegistry())
                )
                .csrf(csrf -> csrf.disable()) // For development, enable in production
                // Add filter to set company context based on host
                .addFilterBefore(companyContextFilter, UsernamePasswordAuthenticationFilter.class)
                // Add filter to validate user enabled status on each request
                .addFilterAfter(userValidationFilter, CompanyContextFilter.class)
                // Add filter to check license validity
                .addFilterAfter(licenseInterceptor, UserValidationFilter.class)
                // Add filter to validate license expiration (blocks expired licenses except PROGRAMMER)
                .addFilterAfter(licenseValidationFilter, LicenseInterceptor.class)
                // Add filter to restrict package-specific routes
                .addFilterAfter(packageAccessFilter, LicenseValidationFilter.class)
                // Add filter to enforce user limit when PROGRAMMER reduces maxUsers
                .addFilterAfter(userLimitEnforcementFilter, PackageAccessFilter.class);

        return http.build();
    }


}
