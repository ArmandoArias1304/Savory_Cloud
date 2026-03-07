package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * Image storage service implementation using Cloudinary.
 * Uploads images to Cloudinary CDN and returns the secure URL.
 * 
 * MULTI-TENANT: Images are organized by company inside the existing "Home" folder:
 * - SavoryCloud/                      -> Global system logo 
 * - {company-slug}/MenuImages         -> Menu item images
 * - {company-slug}/PromotionsImages   -> Promotion images
 * - {company-slug}/RestaurantLogo     -> Restaurant logo
 * - {company-slug}/LandingImages      -> Landing page images
 * 
 * NOTE: "Home" folder already exists in Cloudinary, so we don't prefix with "Home/"
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageStorageServiceImpl implements ImageStorageService {

    private final CloudinaryService cloudinaryService;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
        "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

    // Global system folder (programmer-level - no company context)
    // "Home" folder already exists in Cloudinary, so just use "SavoryCloud"
    private static final String GLOBAL_SYSTEM_FOLDER = "SavoryCloud";
    
    // Folder type constants
    private static final String FOLDER_SYSTEM_LOGO = "system-logo";
    
    /**
     * Map folder type names to Cloudinary sub-folder names within company folder
     */
    private static String mapToCloudinarySubfolder(String folderType) {
        return switch (folderType) {
            case "menu-items" -> "MenuImages";
            case "promotions" -> "PromotionsImages";
            case "landing" -> "LandingImages";
            case "logo", "restaurant-logo", "company-logo" -> "RestaurantLogo";
            default -> folderType;
        };
    }

    @Override
    public String saveImage(MultipartFile file, String folder, String fileName) throws Exception {
        if (!isValidImage(file)) {
            throw new IllegalArgumentException("Archivo de imagen inválido");
        }

        String cloudinaryFolder;
        
        // Check if this is a global system image (no company context needed)
        if (FOLDER_SYSTEM_LOGO.equals(folder)) {
            cloudinaryFolder = GLOBAL_SYSTEM_FOLDER;
            log.info("Saving GLOBAL system image. Folder: {}, FileName: {}", cloudinaryFolder, fileName);
        } else {
            // Per-company image - requires company context
            Company company = CompanyContext.getCurrentCompany();
            if (company == null) {
                throw new IllegalStateException("No company context available for image upload");
            }
            
            String companySlug = company.getSlug();
            if (companySlug == null || companySlug.isEmpty()) {
                companySlug = "company-" + company.getIdCompany();
            }
            
            String subfolder = mapToCloudinarySubfolder(folder);
            cloudinaryFolder = companySlug + "/" + subfolder;
            
            log.info("Saving company image. Company: {} ({}), Folder: {} -> {}, FileName: {}", 
                company.getName(), companySlug, folder, cloudinaryFolder, fileName);
        }
        
        // Upload to Cloudinary and return the secure URL
        return cloudinaryService.uploadImage(file, cloudinaryFolder, fileName);
    }

    @Override
    public void deleteImage(String imagePath) {
        if (imagePath == null || imagePath.isEmpty()) {
            return;
        }

        // Only delete Cloudinary images (URLs containing cloudinary.com)
        if (imagePath.contains("cloudinary.com")) {
            cloudinaryService.deleteImage(imagePath);
        } else {
            // Legacy local path - log but don't attempt to delete
            log.info("Skipping deletion of non-Cloudinary image: {}", imagePath);
        }
    }

    @Override
    public boolean isValidImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        // Validate size
        if (file.getSize() > MAX_FILE_SIZE) {
            return false;
        }

        // Validate MIME type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            return false;
        }

        // Validate extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return false;
        }

        String extension = getFileExtension(originalFilename);
        return ALLOWED_EXTENSIONS.contains(extension.toLowerCase());
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }
}
