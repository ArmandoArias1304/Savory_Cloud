package com.aatechsolutions.elgransazon.application.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for uploading and managing images in Cloudinary.
 * Handles upload, deletion and URL generation for images stored in Cloudinary CDN.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    /**
     * Upload an image to Cloudinary
     * @param file the image file to upload
     * @param folder the Cloudinary folder (e.g., "MenuImages", "PromotionsImages")
     * @param fileName base name for the file (used as public_id prefix)
     * @return the secure URL of the uploaded image
     */
    @SuppressWarnings("unchecked")
    public String uploadImage(MultipartFile file, String folder, String fileName) throws IOException {
        log.info("Uploading image to Cloudinary folder: {}, fileName: {}", folder, fileName);
        
        // Clean file name for use as public_id (folder is set separately)
        String cleanName = cleanFileName(fileName);
        String publicId = cleanName + "_" + System.currentTimeMillis();

        Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "public_id", publicId,
                "folder", folder,  // Cloudinary creates the folder and places the image inside
                "overwrite", true,
                "resource_type", "image"
                // format and quality are NOT set here — CloudinaryUrlHelper already applies
                // f_auto (optimal format per browser) and q_auto via URL transformations.
                // Removing server-side conversion speeds up the upload significantly.
        ));

        String secureUrl = (String) uploadResult.get("secure_url");
        log.info("Image uploaded successfully to Cloudinary: {}", secureUrl);
        return secureUrl;
    }

    /**
     * Delete an image from Cloudinary by its URL
     * @param imageUrl the full Cloudinary URL of the image
     */
    @SuppressWarnings("unchecked")
    public void deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        // Fire-and-forget: delete asynchronously so it doesn't block the request
        CompletableFuture.runAsync(() -> {
            try {
                String publicId = extractPublicId(imageUrl);
                if (publicId != null) {
                    Map<String, Object> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
                    log.info("Cloudinary image deleted. Public ID: {}, Result: {}", publicId, result.get("result"));
                }
            } catch (Exception e) {
                log.error("Error deleting image from Cloudinary: {}", e.getMessage());
            }
        });
    }

    /**
     * Extract the public_id from a Cloudinary URL
     * URL format: https://res.cloudinary.com/{cloud_name}/image/upload/v{version}/{public_id}.{format}
     */
    private String extractPublicId(String url) {
        try {
            // Handle Cloudinary URLs
            if (url.contains("res.cloudinary.com")) {
                // Find the /upload/ or /image/upload/ part
                int uploadIndex = url.indexOf("/upload/");
                if (uploadIndex == -1) return null;
                
                // Get everything after /upload/v{version}/
                String afterUpload = url.substring(uploadIndex + "/upload/".length());
                
                // Remove version prefix (v1234567890/)
                if (afterUpload.startsWith("v") && afterUpload.contains("/")) {
                    afterUpload = afterUpload.substring(afterUpload.indexOf("/") + 1);
                }
                
                // Remove file extension
                int lastDot = afterUpload.lastIndexOf(".");
                if (lastDot != -1) {
                    afterUpload = afterUpload.substring(0, lastDot);
                }
                
                return afterUpload;
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting public_id from URL: {}", url, e);
            return null;
        }
    }

    /**
     * Clean file name: remove special characters, accents, and limit length
     */
    private String cleanFileName(String name) {
        if (name == null || name.isEmpty()) {
            return "image_" + System.currentTimeMillis();
        }

        String cleaned = name.toLowerCase()
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();

        if (cleaned.isEmpty()) {
            return "image_" + System.currentTimeMillis();
        }

        if (cleaned.length() > 50) {
            cleaned = cleaned.substring(0, 50);
        }

        return cleaned;
    }
}
