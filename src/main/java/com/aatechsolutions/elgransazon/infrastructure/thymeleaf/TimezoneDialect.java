package com.aatechsolutions.elgransazon.infrastructure.thymeleaf;

import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IExpressionObjectDialect;
import org.thymeleaf.expression.IExpressionObjectFactory;

/**
 * Thymeleaf dialect that registers the {@code #tz} expression object.
 *
 * Register as a Spring bean (see {@link com.aatechsolutions.elgransazon.infrastructure.config.ThymeleafConfig})
 * and Spring Boot auto-configuration will pick it up automatically.
 */
public class TimezoneDialect extends AbstractDialect implements IExpressionObjectDialect {

    public TimezoneDialect() {
        super("TimezoneDialect");
    }

    @Override
    public IExpressionObjectFactory getExpressionObjectFactory() {
        return new TimezoneExpressionObjectFactory();
    }
}
