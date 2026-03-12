package com.aatechsolutions.elgransazon.infrastructure.config;

import com.aatechsolutions.elgransazon.infrastructure.thymeleaf.TimezoneDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Thymeleaf extra configuration.
 * Registers the {@code #tz} expression object used for timezone-aware date formatting in templates.
 */
@Configuration
public class ThymeleafConfig {

    @Bean
    public TimezoneDialect timezoneDialect() {
        return new TimezoneDialect();
    }
}
