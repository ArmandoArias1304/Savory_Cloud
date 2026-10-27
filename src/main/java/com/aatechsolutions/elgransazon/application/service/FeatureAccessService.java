package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.CompanyFeature;
import com.aatechsolutions.elgransazon.domain.entity.CompanyFeatureStatus;
import com.aatechsolutions.elgransazon.domain.entity.FeatureKey;
import com.aatechsolutions.elgransazon.domain.repository.CompanyFeatureRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side authority for optional modules.
 *
 * <p>Hiding a menu item is never enough: controllers and services call
 * {@link #requireEnabled(FeatureKey)} and the filter calls
 * {@link #isEnabled(FeatureKey)}. The 30-second cache keeps the filter cheap
 * and is invalidated whenever the programmer enables, renews or disables an
 * add-on.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureAccessService {

    private static final long CACHE_TTL_MS = 30_000;

    private final CompanyFeatureRepository featureRepository;

    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();

    public boolean isEnabled(FeatureKey key) {
        Company company = CompanyContext.getCurrentCompany();
        return company != null && isEnabled(company, key);
    }

    /**
     * Convenience for Thymeleaf expressions such as
     * {@code ${@featureAccessService.isEnabled('LOYALTY')}}.
     */
    public boolean isEnabled(String key) {
        if (key == null) {
            return false;
        }
        try {
            return isEnabled(FeatureKey.valueOf(key.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isEnabled(Company company, FeatureKey key) {
        if (company == null || key == null) {
            return false;
        }

        String cacheKey = cacheKey(company, key);
        long now = System.currentTimeMillis();
        Long timestamp = cacheTimestamps.get(cacheKey);
        if (timestamp != null && (now - timestamp) < CACHE_TTL_MS) {
            Boolean cached = cache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        boolean enabled = featureRepository.findByCompanyAndFeatureKey(company, key)
                .map(CompanyFeature::isActive)
                .orElse(false);

        cache.put(cacheKey, enabled);
        cacheTimestamps.put(cacheKey, now);
        return enabled;
    }

    public void requireEnabled(FeatureKey key) {
        Company company = CompanyContext.getCurrentCompany();
        if (company == null || !isEnabled(company, key)) {
            throw new IllegalStateException(
                    "El módulo de " + key.getDisplayName().toLowerCase()
                            + " no está activo para este restaurante.");
        }
    }

    public void requireEnabled(Company company, FeatureKey key) {
        if (!isEnabled(company, key)) {
            throw new IllegalStateException(
                    "El módulo de " + key.getDisplayName().toLowerCase()
                            + " no está activo para este restaurante.");
        }
    }

    @Transactional(readOnly = true)
    public Optional<CompanyFeature> find(Company company, FeatureKey key) {
        return featureRepository.findByCompanyAndFeatureKey(company, key);
    }

    @Transactional(readOnly = true)
    public List<CompanyFeature> findAll(Company company) {
        return featureRepository.findByCompanyOrderByFeatureKeyAsc(company);
    }

    @Transactional
    public CompanyFeature enable(Company company,
                                 FeatureKey key,
                                 LocalDateTime endsAt,
                                 BigDecimal price,
                                 String billingCycle,
                                 String notes,
                                 String performedBy) {
        CompanyFeature feature = featureRepository.findByCompanyAndFeatureKey(company, key)
                .orElseGet(() -> CompanyFeature.builder()
                        .company(company)
                        .featureKey(key)
                        .createdBy(performedBy)
                        .build());

        feature.setStatus(CompanyFeatureStatus.ENABLED);
        feature.setStartsAt(feature.getStartsAt() != null ? feature.getStartsAt() : LocalDateTime.now());
        feature.setEndsAt(endsAt);
        feature.setPrice(price != null ? price : BigDecimal.ZERO);
        feature.setBillingCycle(billingCycle != null && !billingCycle.isBlank() ? billingCycle : "MONTHLY");
        feature.setNotes(notes);
        feature.setUpdatedBy(performedBy);
        if (feature.getCreatedBy() == null) {
            feature.setCreatedBy(performedBy);
        }

        CompanyFeature saved = featureRepository.save(feature);
        invalidate(company);
        log.info("Feature {} enabled for company {} by {} (ends {})",
                key, company.getIdCompany(), performedBy, endsAt);
        return saved;
    }

    @Transactional
    public CompanyFeature renew(Company company,
                                FeatureKey key,
                                int months,
                                BigDecimal amount,
                                String performedBy) {
        if (months <= 0) {
            throw new IllegalArgumentException("Los meses a renovar deben ser mayores a cero");
        }

        CompanyFeature feature = featureRepository.findByCompanyAndFeatureKey(company, key)
                .orElseThrow(() -> new IllegalStateException("La empresa no tiene contratado este módulo"));

        LocalDateTime base = feature.getEndsAt() != null && feature.getEndsAt().isAfter(LocalDateTime.now())
                ? feature.getEndsAt()
                : LocalDateTime.now();
        feature.setEndsAt(base.plusMonths(months));
        feature.setStatus(CompanyFeatureStatus.ENABLED);
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            feature.setPrice(amount);
        }
        feature.setUpdatedBy(performedBy);
        CompanyFeature saved = featureRepository.save(feature);
        invalidate(company);
        log.info("Feature {} renewed for {} months for company {} by {}",
                key, months, company.getIdCompany(), performedBy);
        return saved;
    }

    @Transactional
    public CompanyFeature disable(Company company,
                                  FeatureKey key,
                                  String reason,
                                  String performedBy) {
        CompanyFeature feature = featureRepository.findByCompanyAndFeatureKey(company, key)
                .orElseThrow(() -> new IllegalStateException("La empresa no tiene contratado este módulo"));

        feature.setStatus(CompanyFeatureStatus.DISABLED);
        feature.setUpdatedBy(performedBy);
        if (reason != null && !reason.isBlank()) {
            feature.setNotes(reason.trim());
        }
        CompanyFeature saved = featureRepository.save(feature);
        invalidate(company);
        log.info("Feature {} disabled for company {} by {}: {}",
                key, company.getIdCompany(), performedBy, reason);
        return saved;
    }

    public void invalidate(Company company) {
        if (company == null) {
            cache.clear();
            cacheTimestamps.clear();
            return;
        }
        for (FeatureKey key : FeatureKey.values()) {
            String cacheKey = cacheKey(company, key);
            cache.remove(cacheKey);
            cacheTimestamps.remove(cacheKey);
        }
    }

    private String cacheKey(Company company, FeatureKey key) {
        return company.getIdCompany() + ":" + key.name();
    }
}
