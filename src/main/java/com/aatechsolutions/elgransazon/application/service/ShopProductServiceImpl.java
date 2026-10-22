package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.ShopBillingType;
import com.aatechsolutions.elgransazon.domain.entity.ShopPricingMode;
import com.aatechsolutions.elgransazon.domain.entity.ShopProduct;
import com.aatechsolutions.elgransazon.domain.repository.ShopProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of the global shop catalogue.
 *
 * <p>These products are NOT tenant scoped: the programmer publishes them once
 * and every restaurant sees the same list. No {@code CompanyContext} is used
 * here on purpose.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ShopProductServiceImpl implements ShopProductService {

    private final ShopProductRepository shopProductRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ShopProduct> findForProgrammer(String search, String category, Boolean active) {
        return shopProductRepository.searchForProgrammer(
                blankToNull(search), blankToNull(category), active);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShopProduct> findActive(String search, String category) {
        return shopProductRepository.searchActive(blankToNull(search), blankToNull(category));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findCategories(boolean onlyActive) {
        return onlyActive
                ? shopProductRepository.findDistinctActiveCategories()
                : shopProductRepository.findDistinctCategories();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShopProduct> findById(Long id) {
        return shopProductRepository.findById(id);
    }

    @Override
    public ShopProduct create(ShopProduct product) {
        normalize(product);
        product.setIdProduct(null);
        ShopProduct saved = shopProductRepository.save(product);
        log.info("Shop product created: id={}, name={}", saved.getIdProduct(), saved.getName());
        return saved;
    }

    @Override
    public ShopProduct update(Long id, ShopProduct product) {
        ShopProduct existing = shopProductRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        normalize(product);
        existing.setName(product.getName());
        existing.setDescription(product.getDescription());
        existing.setPricingMode(product.getPricingModeOrDefault());
        existing.setPrice(product.getPrice());
        existing.setMaxPrice(product.getMaxPrice());
        existing.setPriceNote(product.getPriceNote());
        existing.setDiscountPrice(product.getDiscountPrice());
        existing.setBillingType(product.getBillingTypeOrDefault());
        existing.setCategory(product.getCategory());
        existing.setCurrency(product.getCurrency());
        existing.setFeatured(Boolean.TRUE.equals(product.getFeatured()));
        existing.setActive(Boolean.TRUE.equals(product.getActive()));
        existing.setSortOrder(product.getSortOrder());
        // A blank image keeps the current photo instead of erasing it.
        if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
            existing.setImageUrl(product.getImageUrl());
        }

        ShopProduct saved = shopProductRepository.save(existing);
        log.info("Shop product updated: id={}, name={}", saved.getIdProduct(), saved.getName());
        return saved;
    }

    @Override
    public ShopProduct toggleActive(Long id) {
        ShopProduct product = shopProductRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
        product.setActive(!Boolean.TRUE.equals(product.getActive()));
        return shopProductRepository.save(product);
    }

    @Override
    public void delete(Long id) {
        ShopProduct product = shopProductRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
        shopProductRepository.delete(product);
        log.info("Shop product deleted: id={}, name={}", id, product.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public long countActive() {
        return shopProductRepository.countByActiveTrue();
    }

    @Override
    @Transactional(readOnly = true)
    public long countAll() {
        return shopProductRepository.count();
    }

    /**
     * Trim text fields and fill defaults so the database never stores blanks.
     */
    private void normalize(ShopProduct product) {
        if (product.getName() != null) {
            product.setName(product.getName().trim());
        }
        if (product.getDescription() != null) {
            product.setDescription(product.getDescription().trim());
        }
        if (product.getCategory() != null) {
            String category = product.getCategory().trim();
            product.setCategory(category.isEmpty() ? null : category);
        }
        if (product.getCurrency() == null || product.getCurrency().isBlank()) {
            product.setCurrency("MXN");
        }
        normalizePricing(product);
        if (product.getFeatured() == null) {
            product.setFeatured(false);
        }
        if (product.getBillingType() == null) {
            product.setBillingType(ShopBillingType.ONE_TIME);
        }
        if (product.getActive() == null) {
            product.setActive(true);
        }
        if (product.getSortOrder() == null) {
            product.setSortOrder(0);
        }
    }

    /**
     * Keep the published price consistent with the chosen mode: a range needs
     * its maximum, a quote publishes no amount, and only a fixed price can
     * carry a promotion.
     */
    private void normalizePricing(ShopProduct product) {
        ShopPricingMode mode = product.getPricingModeOrDefault();
        product.setPricingMode(mode);

        String note = product.getPriceNote();
        product.setPriceNote(note == null || note.isBlank() ? null : note.trim());

        if (mode == ShopPricingMode.QUOTE) {
            // Nothing is published, so no amount may leak into the store.
            product.setPrice(BigDecimal.ZERO);
            product.setMaxPrice(null);
            product.setDiscountPrice(null);
            return;
        }

        if (mode == ShopPricingMode.RANGE) {
            product.setDiscountPrice(null);
            if (product.getPrice() == null) {
                product.setPrice(BigDecimal.ZERO);
            }
            boolean validRange = product.getMaxPrice() != null
                    && product.getMaxPrice().compareTo(product.getPrice()) > 0;
            if (!validRange) {
                // A range without a higher maximum would show a broken price.
                product.setPricingMode(ShopPricingMode.FIXED);
                product.setMaxPrice(null);
            }
            return;
        }

        // Fixed price: neither the range nor its note apply.
        product.setMaxPrice(null);
        product.setPriceNote(null);
        if (product.getPrice() == null) {
            product.setPrice(BigDecimal.ZERO);
        }
        // A promotion only exists when it really lowers the price.
        if (product.getDiscountPrice() != null
                && (product.getDiscountPrice().compareTo(BigDecimal.ZERO) <= 0
                    || product.getDiscountPrice().compareTo(product.getPrice()) >= 0)) {
            product.setDiscountPrice(null);
        }
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
