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
    private final CloudinaryService cloudinaryService;

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
        String folder = company.getSlug() + "/landing";
        String fileName = section.name().toLowerCase() + "-" + position;

        // Check if image already exists for this slot
        Optional<LandingImage> existing = landingImageRepository
                .findByCompanyAndSectionAndPosition(company, section, position);

        // Delete old image from Cloudinary if replacing
        existing.ifPresent(img -> {
            cloudinaryService.deleteImage(img.getImageUrl());
            log.info("Deleted old landing image for {}/{}/{}", company.getSlug(), section, position);
        });

        // Upload new image to Cloudinary
        String imageUrl = cloudinaryService.uploadImage(file, folder, fileName);

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
            cloudinaryService.deleteImage(img.getImageUrl());
            landingImageRepository.delete(img);
            log.info("Deleted landing image {}/{}/{}", company.getSlug(), section, position);
        });
    }
}
