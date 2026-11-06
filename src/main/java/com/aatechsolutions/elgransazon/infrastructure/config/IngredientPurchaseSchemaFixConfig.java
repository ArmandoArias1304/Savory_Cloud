package com.aatechsolutions.elgransazon.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

@Configuration
@Slf4j
public class IngredientPurchaseSchemaFixConfig {

    @Bean
    public CommandLineRunner fixLegacyIngredientPurchaseSchema(DataSource dataSource) {
        return args -> {
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                boolean legacyColumnExists;
                try (ResultSet columns = metaData.getColumns(connection.getCatalog(), null,
                        "ingredient_purchases", "original_xml")) {
                    legacyColumnExists = columns.next();
                }

                if (legacyColumnExists) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("ALTER TABLE ingredient_purchases DROP COLUMN original_xml");
                    }
                    log.info("Removed legacy ingredient_purchases.original_xml column.");
                }
            }
        };
    }
}
