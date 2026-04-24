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
 * Image storage service implementation backed by Cloudflare Images.
 *
 * <p>MULTI-TENANT organization (Cloudflare custom IDs use slashes as virtual folders):
 * <pre>
 *   savorycloud/system-logo-{ts}                 → Global system logo (programmer)
 *   {company-slug}/menu/{name}-{ts}              → Menu item images
 *   {company-slug}/promotions/{name}-{ts}        → Promotion images
 *   {company-slug}/logo/restaurant-{ts}          → Restaurant logo
 *   {company-slug}/landing/{section}-{pos}-{ts}  → Landing page images
 * </pre>
 *
 * <p>{ts} is appended so reuploads always create a unique ID (Cloudflare rejects
 * duplicate IDs and the timestamp also gives a clean audit trail).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageStorageServiceImpl implements ImageStorageService {

    private final CloudflareImagesService cloudflareImagesService;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    /** Maximum upload size enforced server-side. Mirrors the client-side validation. */
    private static final long MAX_FILE_SIZE = 3L * 1024 * 1024; // 3 MB

    /** Top-level virtual folder for global (non-company) assets. */
    private static final String GLOBAL_SYSTEM_FOLDER = "savorycloud";

    /** Logical folder name reserved for the global system logo. */
    private static final String FOLDER_SYSTEM_LOGO = "system-logo";

    /** Map logical folder names to subpaths used inside the company's virtual folder. */
    private static String mapToSubfolder(String folderType) {
        return switch (folderType) {
            case "menu-items" -> "menu";
            case "promotions" -> "promotions";
            case "landing" -> "landing";
            case "logo", "restaurant-logo", "company-logo" -> "logo";
            default -> folderType;
        };
    }

    // ───────────────────────────────────────────────────────────────────
    // Server-side upload (legacy fallback path)
    // ───────────────────────────────────────────────────────────────────

    @Override
    public String saveImage(MultipartFile file, String folder, String fileName) throws Exception {
        if (!isValidImage(file)) {
            throw new IllegalArgumentException("Archivo de imagen inválido");
        }
        String customId = buildCustomId(folder, fileName);
        log.info("Server-side uploading image to Cloudflare. customId={}", customId);
        return cloudflareImagesService.uploadImage(file, customId);
    }

    // ───────────────────────────────────────────────────────────────────
    // Direct Creator Upload (browser → Cloudflare, bypasses this server)
    // ───────────────────────────────────────────────────────────────────

    @Override
    public CloudflareImagesService.DirectUploadToken prepareDirectUpload(String folder, String fileName) {
        String customId = buildCustomId(folder, fileName);
        log.info("Issuing direct upload token. customId={}", customId);
        return cloudflareImagesService.createDirectUploadToken(customId);
    }

    // ───────────────────────────────────────────────────────────────────
    // Delete
    // ───────────────────────────────────────────────────────────────────

    @Override
    public void deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        cloudflareImagesService.deleteImage(imageUrl);
    }

    // ───────────────────────────────────────────────────────────────────
    // Validation
    // ───────────────────────────────────────────────────────────────────

    @Override
    public boolean isValidImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return false;
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            return false;
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return false;
        }
        String extension = getFileExtension(originalFilename);
        return ALLOWED_EXTENSIONS.contains(extension.toLowerCase());
    }

    // ───────────────────────────────────────────────────────────────────
    // Helpers
    // ───────────────────────────────────────────────────────────────────

    /**
     * Build a Cloudflare custom ID respecting the multi-tenant layout.
     * For global folders no company context is needed; for company folders the
     * current tenant slug is read from {@link CompanyContext}.
     */
    private String buildCustomId(String folder, String fileName) {
        long ts = System.currentTimeMillis();
        String cleanName = cleanFileName(fileName);

        if (FOLDER_SYSTEM_LOGO.equals(folder)) {
            return GLOBAL_SYSTEM_FOLDER + "/system-logo-" + ts;
        }

        Company company = CompanyContext.getCurrentCompany();
        if (company == null) {
            throw new IllegalStateException("No company context available for image upload");
        }
        String slug = company.getSlug();
        if (slug == null || slug.isEmpty()) {
            slug = "company-" + company.getIdCompany();
        }

        String subfolder = mapToSubfolder(folder);
        return slug + "/" + subfolder + "/" + cleanName + "-" + ts;
    }

    /** Sanitize a filename for use as part of a Cloudflare image ID. */
    static String cleanFileName(String name) {
        if (name == null || name.isEmpty()) {
            return "image";
        }
        String cleaned = name.toLowerCase()
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
        if (cleaned.isEmpty()) {
            return "image";
        }
        if (cleaned.length() > 50) {
            cleaned = cleaned.substring(0, 50);
        }
        return cleaned;
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }
}
