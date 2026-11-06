package com.aatechsolutions.elgransazon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SuppliersCardsViewTest {

        private static final Path TEMPLATE = Path.of("src/main/resources/templates/admin/suppliers/list.html");
        private static final Path SCRIPT = Path.of("src/main/resources/static/js/suppliers-view.js");
        private static final Path TOGGLE = Path.of("src/main/resources/templates/fragments/orders-view-toggle.html");

        @Test
        void supplierListOffersTableAndCardsUsingTheSameRows() throws IOException {
                String template = Files.readString(TEMPLATE, StandardCharsets.UTF_8);
                String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);
                String toggle = Files.readString(TOGGLE, StandardCharsets.UTF_8);

                assertTrue(template.contains("toggle('suppliers')"),
                                "the view toggle must belong to suppliers");
                assertTrue(toggle.contains("data-orders-view='table'")
                                && toggle.contains("data-orders-view='grid'"),
                                "the toggle must offer table and cards");
                assertTrue(template.contains("id=\"suppliersTableView\"")
                                && template.contains("id=\"suppliersCardsView\"")
                                && template.contains("id=\"suppliersCards\""),
                                "both views need separate containers");
                assertTrue(template.contains("th:data-supplier-id=\"${supplier.idSupplier}\""),
                                "each supplier row must identify the supplier");
                assertTrue(template.contains("th:data-supplier-address=\"${supplier.address ?: ''}\"")
                                && template.contains("th:data-supplier-notes=\"${supplier.notes ?: ''}\""),
                                "rows must publish the additional information shown by the modal");
                assertTrue(template.contains("supplier-status-badge")
                                && template.contains("supplier-actions-cell"),
                                "cards must reuse the server-rendered status and actions");
                assertTrue(template.contains("id=\"supplierDetailModal\"")
                                && template.contains("id=\"supplierDetailAddress\"")
                                && template.contains("id=\"supplierDetailNotes\""),
                                "the details modal must show supplier information");
                assertTrue(template.contains("/js/suppliers-view.js")
                                && template.contains("SuppliersView.init("),
                                "the page must load and initialize the cards view");
                assertTrue(script.contains("tr[data-supplier-id]")
                                && script.contains("supplier-status-badge")
                                && script.contains("supplier-actions-cell"),
                                "the cards must be built from supplier table rows and reuse their badges and actions");
                assertTrue(script.contains("openDetails(row)")
                                && script.contains("closeDetails")
                                && script.contains("storedView() !== \"grid\""),
                                "only a card in grid view should open the supplier detail modal");
        }
}
