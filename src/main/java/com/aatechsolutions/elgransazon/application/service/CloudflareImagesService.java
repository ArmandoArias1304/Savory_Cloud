package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.infrastructure.config.CloudflareImagesConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for uploading and managing images in Cloudflare Images.
 *
 * Supports two upload modes:
 *  1. Server-side upload: file flows through this Spring Boot server, then to Cloudflare.
 *     Used as a fallback when Direct Upload from the browser is not available.
 *  2. Direct Creator Upload: server only requests a one-time upload URL from Cloudflare;
 *     the browser uploads the file directly. The image bytes never traverse this server.
 *
 * Delivery URLs returned have the form:
 *   https://imagedelivery.net/{accountHash}/{imageId}/public
 *
 * Where {imageId} is a custom path like "el-buen-sazon/menu/pizza-margarita-1733...".
 * The /public segment is the default variant Cloudflare provides; URL-based flexible
 * transformations (w=400,fit=cover,quality=85,format=auto) are applied by
 * {@link CloudflareImagesUrlHelper} when serving images to the browser.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CloudflareImagesService {

    private static final String DELIVERY_HOST = "imagedelivery.net";
    private static final String DEFAULT_VARIANT = "public";

    private final RestClient cloudflareImagesRestClient;
    private final CloudflareImagesConfig config;

    /**
     * Result of a Direct Creator Upload token request.
     *
     * @param imageId   the custom or generated image ID assigned by Cloudflare
     * @param uploadUrl one-time URL the browser will POST the image to (valid ~30 minutes)
     * @param finalUrl  the public delivery URL the application will store in the DB
     */
    public record DirectUploadToken(String imageId, String uploadUrl, String finalUrl) {}

    // ───────────────────────────────────────────────────────────────────
    // Server-side upload (file goes through this server)
    // ───────────────────────────────────────────────────────────────────

    /**
     * Upload an image file to Cloudflare Images from the server.
     *
     * @param file      the image bytes
     * @param customId  custom image ID (e.g. "el-buen-sazon/menu/pizza-1733..."); slashes
     *                  are allowed and Cloudflare uses them as a virtual folder structure.
     *                  Must be unique; if it already exists the upload fails.
     * @return public delivery URL (https://imagedelivery.net/{hash}/{customId}/public)
     */
    @SuppressWarnings("unchecked")
    public String uploadImage(MultipartFile file, String customId) throws IOException {
        ensureConfigured();
        log.info("Uploading image to Cloudflare Images. customId={}, size={} bytes", customId, file.getSize());

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new MultipartByteArrayResource(file.getBytes(), file.getOriginalFilename()));
        form.add("id", customId);
        form.add("requireSignedURLs", "false");

        Map<String, Object> response = cloudflareImagesRestClient.post()
                .uri("/v1")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    String body = new String(res.getBody().readAllBytes());
                    log.error("Cloudflare upload failed. status={}, body={}", res.getStatusCode(), body);
                    throw new IOException("Cloudflare image upload failed: " + body);
                })
                .body(Map.class);

        if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
            throw new IOException("Cloudflare did not return a successful response");
        }

        String url = buildDeliveryUrl(customId);
        log.info("Image uploaded to Cloudflare Images: {}", url);
        return url;
    }

    // ───────────────────────────────────────────────────────────────────
    // Direct Creator Upload (file uploaded by the browser, server only mints a token)
    // ───────────────────────────────────────────────────────────────────

    /**
     * Request a one-time direct upload URL so the browser can upload an image
     * straight to Cloudflare without the bytes passing through this server.
     *
     * @param customId custom image ID (e.g. "el-buen-sazon/menu/pizza-1733...");
     *                 slashes serve as virtual folders. Must be globally unique
     *                 within the account; reuse will be rejected by Cloudflare.
     * @return token containing the imageId, the uploadUrl the browser will POST to,
     *         and the final delivery URL we will persist after upload completes.
     */
    @SuppressWarnings("unchecked")
    public DirectUploadToken createDirectUploadToken(String customId) {
        ensureConfigured();
        log.info("Requesting Cloudflare direct upload token. customId={}", customId);

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("id", customId);
        form.add("requireSignedURLs", "false");

        Map<String, Object> response = cloudflareImagesRestClient.post()
                .uri("/v2/direct_upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    String body = new String(res.getBody().readAllBytes());
                    log.error("Cloudflare direct upload token request failed. status={}, body={}",
                            res.getStatusCode(), body);
                    throw new IllegalStateException("Cloudflare direct upload request failed: " + body);
                })
                .body(Map.class);

        if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
            throw new IllegalStateException("Cloudflare did not return a successful direct upload response");
        }

        Map<String, Object> result = (Map<String, Object>) response.get("result");
        if (result == null) {
            throw new IllegalStateException("Cloudflare response missing 'result' field");
        }

        String imageId = (String) result.get("id");
        String uploadUrl = (String) result.get("uploadURL");
        String finalUrl = buildDeliveryUrl(imageId);

        log.info("Cloudflare direct upload token issued. imageId={}", imageId);
        return new DirectUploadToken(imageId, uploadUrl, finalUrl);
    }

    // ───────────────────────────────────────────────────────────────────
    // Delete
    // ───────────────────────────────────────────────────────────────────

    /**
     * Delete an image from Cloudflare Images by its delivery URL.
     * Operates fire-and-forget so it never blocks the calling request.
     */
    public void deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        if (!imageUrl.contains(DELIVERY_HOST)) {
            // Not a Cloudflare URL (legacy local path or already-removed entry); ignore.
            log.debug("Skipping non-Cloudflare image deletion: {}", imageUrl);
            return;
        }

        String imageId = extractImageId(imageUrl);
        if (imageId == null) {
            log.warn("Could not extract image ID from URL: {}", imageUrl);
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                ensureConfigured();
                cloudflareImagesRestClient.delete()
                        .uri("/v1/{id}", imageId)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Cloudflare image deleted: {}", imageId);
            } catch (Exception e) {
                log.error("Error deleting Cloudflare image {}: {}", imageId, e.getMessage());
            }
        });
    }

    // ───────────────────────────────────────────────────────────────────
    // Helpers
    // ───────────────────────────────────────────────────────────────────

    /**
     * Extract the image ID (which may contain slashes) from a delivery URL.
     * URL format: https://imagedelivery.net/{hash}/{image_id}/{variant}
     */
    String extractImageId(String url) {
        try {
            int hostIdx = url.indexOf(DELIVERY_HOST);
            if (hostIdx == -1) return null;
            String afterHost = url.substring(hostIdx + DELIVERY_HOST.length() + 1); // skip "/"
            int hashEnd = afterHost.indexOf('/');
            if (hashEnd == -1) return null;
            String afterHash = afterHost.substring(hashEnd + 1);
            // Strip the trailing variant segment (everything after the last "/").
            int lastSlash = afterHash.lastIndexOf('/');
            if (lastSlash == -1) return afterHash; // no variant suffix
            return afterHash.substring(0, lastSlash);
        } catch (Exception e) {
            log.warn("Error extracting image ID from URL '{}': {}", url, e.getMessage());
            return null;
        }
    }

    String buildDeliveryUrl(String imageId) {
        return "https://" + DELIVERY_HOST + "/" + config.getAccountHash() + "/" + imageId + "/" + DEFAULT_VARIANT;
    }

    private void ensureConfigured() {
        if (!config.isConfigured()) {
            throw new IllegalStateException(
                    "Cloudflare Images is not configured. Set CLOUDFLARE_ACCOUNT_ID, "
                            + "CLOUDFLARE_IMAGES_API_TOKEN and CLOUDFLARE_IMAGES_HASH environment variables.");
        }
    }

    /**
     * ByteArrayResource that exposes a filename so multipart upload sets the
     * "filename" parameter Cloudflare needs to accept the part as a file.
     */
    private static class MultipartByteArrayResource extends ByteArrayResource {
        private final String filename;

        MultipartByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = (filename == null || filename.isBlank()) ? "image" : filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
