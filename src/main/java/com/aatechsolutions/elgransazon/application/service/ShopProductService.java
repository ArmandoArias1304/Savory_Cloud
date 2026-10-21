package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.ShopProduct;

import java.util.List;
import java.util.Optional;

/**
 * Business logic for the global shop catalogue managed by the programmer.
 */
public interface ShopProductService {

    /** Full catalogue for the programmer, optionally filtered. */
    List<ShopProduct> findForProgrammer(String search, String category, Boolean active);

    /** Published catalogue for restaurants: active products only. */
    List<ShopProduct> findActive(String search, String category);

    /** Categories present in the catalogue; {@code onlyActive} limits it to published products. */
    List<String> findCategories(boolean onlyActive);

    Optional<ShopProduct> findById(Long id);

    ShopProduct create(ShopProduct product);

    ShopProduct update(Long id, ShopProduct product);

    /** Flip the published state (active/inactive). */
    ShopProduct toggleActive(Long id);

    void delete(Long id);

    long countActive();

    long countAll();
}
