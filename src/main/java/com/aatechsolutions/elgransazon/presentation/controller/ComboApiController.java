package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.domain.entity.ItemMenuComboItem;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComboItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API Controller for combo item queries.
 * Accessible by all authenticated roles (waiter, cashier, chef, etc.)
 * under /api/combo/ path.
 */
@RestController
@RequestMapping("/api/combo")
@RequiredArgsConstructor
@Slf4j
public class ComboApiController {

    private final ItemMenuComboItemRepository itemMenuComboItemRepository;

    /**
     * Get combo children for a given combo ItemMenu
     * Used by order-menu.html modal across all roles
     */
    @GetMapping("/{comboItemId}/items")
    public ResponseEntity<Map<String, Object>> getComboItems(@PathVariable Long comboItemId) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<ItemMenuComboItem> comboItems = itemMenuComboItemRepository
                    .findByComboMenuIdItemMenuOrderByDisplayOrderAsc(comboItemId);

            List<Map<String, Object>> itemsList = comboItems.stream().map(ci -> {
                Map<String, Object> item = new HashMap<>();
                item.put("childItemId", ci.getChildMenu().getIdItemMenu());
                item.put("childItemName", ci.getChildMenu().getName());
                item.put("childItemPrice", ci.getChildMenu().getPrice());
                item.put("quantity", ci.getQuantity());
                item.put("displayOrder", ci.getDisplayOrder());
                item.put("childRequiresPreparation",
                        ci.getChildMenu().getRequiresPreparation() != null && ci.getChildMenu().getRequiresPreparation());
                item.put("childRequiresBaristaPreparation",
                        ci.getChildMenu().getRequiresBaristaPreparation() != null && ci.getChildMenu().getRequiresBaristaPreparation());
                item.put("childRequiresParrilleroPreparation",
                        ci.getChildMenu().getRequiresParrilleroPreparation() != null && ci.getChildMenu().getRequiresParrilleroPreparation());
                return item;
            }).collect(Collectors.toList());

            response.put("success", true);
            response.put("comboItems", itemsList);
        } catch (Exception e) {
            log.error("Error fetching combo items for {}: {}", comboItemId, e.getMessage());
            response.put("success", false);
            response.put("message", "Error al obtener items del combo");
        }
        return ResponseEntity.ok(response);
    }
}
