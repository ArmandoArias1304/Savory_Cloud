package com.aatechsolutions.elgransazon.infrastructure.thymeleaf;

import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.expression.IExpressionObjectFactory;

import java.util.Collections;
import java.util.Set;

public class TimezoneExpressionObjectFactory implements IExpressionObjectFactory {

    private static final String NAME = "tz";
    private static final Set<String> NAMES = Collections.singleton(NAME);

    @Override
    public Set<String> getAllExpressionObjectNames() {
        return NAMES;
    }

    @Override
    public Object buildObject(IExpressionContext context, String expressionObjectName) {
        if (NAME.equals(expressionObjectName)) {
            return new TimezoneExpressionObject();
        }
        return null;
    }

    @Override
    public boolean isCacheable(String expressionObjectName) {
        // A new instance per evaluation so it always reads the current CompanyContext
        return false;
    }
}
