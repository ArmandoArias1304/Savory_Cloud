package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.LandingImage;
import com.aatechsolutions.elgransazon.domain.repository.LandingImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LandingImageService {

    private final LandingImageRepository landingImageRepository;
    private final ImageStorageService imageStorageService;

    public List<LandingImage> findByCompany(Company company) {
        return landingImageRepository.findByCompany(company);
    }

    public List<LandingImage> findByCompanyAndSection(Company company, LandingImage.Section section) {
        return landingImageRepository.findByCompanyAndSection(company, section);
    }

    /**
     * Returns a map of "SECTION_POSITION" -> imageUrl for easy Thymeleaf access
     */
    public Map<String, String> getImageMapForCompany(Company company) {
        List<LandingImage> images = landingImageRepository.findByCompany(company);
        Map<String, String> imageMap = new HashMap<>();
        for (LandingImage img : images) {
            String key = img.getSection().name() + "_" + img.getPosition();
            imageMap.put(key, img.getImageUrl());
        }
        return imageMap;
    }

    @Transactional
    public LandingImage uploadImage(Company company, LandingImage.Section section, int position,
                                     MultipartFile file) throws Exception {
        // Folder type "landing" is mapped to "{slug}/landing/..." by ImageStorageServiceImpl.
        // The fileName combines section + position so it is meaningful inside the CDN.
        String fileName = section.name().toLowerCase() + "-" + position;

        // Check if image already exists for this slot
        Optional<LandingImage> existing = landingImageRepository
                .findByCompanyAndSectionAndPosition(company, section, position);

        // Delete old image from Cloudflare if replacing (fire-and-forget; safe to call before upload)
        existing.ifPresent(img -> {
            imageStorageService.deleteImage(img.getImageUrl());
            log.info("Deleted old landing image for {}/{}/{}", company.getSlug(), section, position);
        });

        // Upload new image (server-side fallback path; the JS UI uses direct upload instead).
        String imageUrl = imageStorageService.saveImage(file, "landing", fileName);

        if (existing.isPresent()) {
            LandingImage img = existing.get();
            img.setImageUrl(imageUrl);
            log.info("Updated landing image {}/{}/{}", company.getSlug(), section, position);
            return landingImageRepository.save(img);
        } else {
            LandingImage img = LandingImage.builder()
                    .company(company)
                    .section(section)
                    .position(position)
                    .imageUrl(imageUrl)
                    .build();
            log.info("Created new landing image {}/{}/{}", company.getSlug(), section, position);
            return landingImageRepository.save(img);
        }
    }

    @Transactional
    public void deleteImage(Company company, LandingImage.Section section, int position) {
        Optional<LandingImage> existing = landingImageRepository
                .findByCompanyAndSectionAndPosition(company, section, position);

        existing.ifPresent(img -> {
            imageStorageService.deleteImage(img.getImageUrl());
            landingImageRepository.delete(img);
            log.info("Deleted landing image {}/{}/{}", company.getSlug(), section, position);
        });
    }

    /**
     * Direct Upload variant: persist a landing image whose file was already uploaded
     * to Cloudflare directly by the browser. The {@code imageUrl} returned by the
     * upload-token flow is stored as-is; no file bytes pass through this server.
     */
    @Transactional
    public LandingImage saveImageUrl(Company company, LandingImage.Section section, int position, String imageUrl) {
        Optional<LandingImage> existing = landingImageRepository
                .findByCompanyAndSectionAndPosition(company, section, position);

        // Replace: delete the old image from Cloudflare (fire-and-forget)
        existing.ifPresent(img -> {
            if (img.getImageUrl() != null && !img.getImageUrl().equals(imageUrl)) {
                imageStorageService.deleteImage(img.getImageUrl());
                log.info("Replaced old landing image for {}/{}/{}", company.getSlug(), section, position);
            }
        });

        if (existing.isPresent()) {
            LandingImage img = existing.get();
            img.setImageUrl(imageUrl);
            return landingImageRepository.save(img);
        } else {
            LandingImage img = LandingImage.builder()
                    .company(company)
                    .section(section)
                    .position(position)
                    .imageUrl(imageUrl)
                    .build();
            return landingImageRepository.save(img);
        }
    }
}
