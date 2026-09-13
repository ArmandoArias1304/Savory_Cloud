package com.aatechsolutions.elgransazon.infrastructure.init;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drops uk_printer_company_name if it still exists.
 * Hibernate ddl-auto=update will not remove that unique index, and the same
 * Windows printer must be assignable to more than one comanda role.
 */
@Component
@Order(20)
@Slf4j
public class PrinterSchemaPatch implements CommandLineRunner {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        try {
            Number count = (Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM information_schema.statistics "
                            + "WHERE table_schema = DATABASE() "
                            + "AND table_name = 'printers' "
                            + "AND index_name = 'uk_printer_company_name'")
                    .getSingleResult();
            if (count != null && count.longValue() > 0) {
                entityManager.createNativeQuery("ALTER TABLE printers DROP INDEX uk_printer_company_name")
                        .executeUpdate();
                log.info("Dropped unique index uk_printer_company_name (same printer may be used by multiple roles)");
            }
        } catch (Exception e) {
            log.debug("Could not drop uk_printer_company_name (table or index may not exist yet): {}", e.getMessage());
        }
    }
}
