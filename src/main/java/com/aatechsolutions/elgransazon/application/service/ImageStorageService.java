package com.aatechsolutions.elgransazon.application.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Storage facade for application images. Backed by Cloudflare Images.
 *
 * <p>Two upload paths are exposed:
 *  <ul>
 *    <li>{@link #saveImage(MultipartFile, String, String)} — server-side upload (file
 *        bytes pass through this Spring Boot server, then to Cloudflare).</li>
 *    <li>{@link #prepareDirectUpload(String, String)} — Direct Creator Upload: returns
 *        a one-time upload URL the browser will POST the file to. The bytes never
 *        traverse this server, saving bandwidth and JVM memory.</li>
 *  </ul>
 */
public interface ImageStorageService {

    /**
     * Server-side upload: stores the image and returns the public delivery URL.
     *
     * @param file     image bytes
     * @param folder   logical folder type ("menu-items", "promotions", "logo",
     *                 "restaurant-logo", "system-logo", "landing")
     * @param fileName base name for the image (e.g. the menu item name); will be
     *                 sanitized and a timestamp appended for uniqueness
     * @return public delivery URL persisted in the database
     */
    String saveImage(MultipartFile file, String folder, String fileName) throws Exception;

    /**
     * Build a tenant-scoped image ID and request a one-time direct upload URL from
     * Cloudflare. The browser POSTs the file directly to the returned uploadUrl;
     * after upload completes the application stores the returned finalUrl in the DB.
     *
     * @param folder   logical folder type (same values as {@link #saveImage})
     * @param fileName base name for the image
     * @return token containing imageId, uploadUrl and the finalUrl to persist
     */
    CloudflareImagesService.DirectUploadToken prepareDirectUpload(String folder, String fileName);

    /** Delete an image by its delivery URL. No-op for null/empty/legacy URLs. */
    void deleteImage(String imageUrl);

    /** Validate that the file is an acceptable image (size, MIME type, extension). */
    boolean isValidImage(MultipartFile file);
}
