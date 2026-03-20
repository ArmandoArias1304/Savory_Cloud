package com.aatechsolutions.elgransazon.application.service;

import org.springframework.stereotype.Component;

/**
 * Utility bean for transforming Cloudinary URLs with optimization parameters.
 * Uses responsive srcset to serve the exact image size each device/container needs.
 * 
 * Usage in Thymeleaf:
 *   th:src="${@cloudinaryUrl.card(item.imageUrl)}"       → fallback 400px for cards
 *   th:data-srcset="${@cloudinaryUrl.cardSrcset(item.imageUrl)}" → responsive srcset
 *   th:src="${@cloudinaryUrl.thumb(item.imageUrl)}"      → 120px for cart thumbnails
 *   th:src="${@cloudinaryUrl.modal(item.imageUrl)}"      → 600px fixed for modals
 */
@Component("cloudinaryUrl")
public class CloudinaryUrlHelper {

    // Responsive widths for card srcset (covers mobile small to desktop)
    private static final int[] CARD_WIDTHS = {200, 300, 400, 600};

    /**
     * Fallback src for cards (used with srcset for browsers that don't support it)
     */
    public String card(String url) {
        return transform(url, "w_400,c_fill,q_85,f_auto");
    }

    /**
     * Generate srcset string for responsive card images.
     * Returns: "url_200w 200w, url_300w 300w, url_400w 400w, url_600w 600w"
     * The browser picks the right size based on the `sizes` attribute.
     */
    public String cardSrcset(String url) {
        if (url == null || url.isEmpty() || !url.contains("res.cloudinary.com")) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CARD_WIDTHS.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(transform(url, "w_" + CARD_WIDTHS[i] + ",c_fill,q_85,f_auto"));
            sb.append(" ").append(CARD_WIDTHS[i]).append("w");
        }
        return sb.toString();
    }

    /**
     * Transform URL for thumbnail images (cart items, small previews, admin list)
     * Serves at 120px width
     */
    public String thumb(String url) {
        return transform(url, "w_120,h_120,c_fill,q_85,f_auto");
    }

    /**
     * Transform URL for modal/detail images.
     * Fixed 600x400 for consistent modal container sizing.
     */
    public String modal(String url) {
        return transform(url, "w_600,h_400,c_fill,g_auto,q_85,f_auto");
    }

    /**
     * Transform URL for sidebar/navbar logos (48x48px containers).
     */
    public String logoSmall(String url) {
        return transform(url, "w_96,h_96,c_fit,q_85,f_auto");
    }

    /**
     * Transform URL for login page logo (96x96px container).
     */
    public String logoLarge(String url) {
        return transform(url, "w_192,h_192,c_fit,q_85,f_auto");
    }

    /**
     * Transform URL for landing page hero/splash logos (300-340px containers).
     * Uses 600px to cover 2× retina displays without blurriness.
     */
    public String logoHero(String url) {
        return transform(url, "w_600,h_600,c_fit,q_90,f_auto");
    }

    /**
     * Insert Cloudinary transformations into a Cloudinary URL.
     * Only transforms URLs from res.cloudinary.com; returns others unchanged.
     * 
     * Input:  https://res.cloudinary.com/xxx/image/upload/v1234/folder/image.webp
     * Output: https://res.cloudinary.com/xxx/image/upload/{transformations}/v1234/folder/image.webp
     */
    public String transform(String url, String transformations) {
        if (url == null || url.isEmpty() || !url.contains("res.cloudinary.com")) {
            return url;
        }

        // Insert transformations after /upload/
        int uploadIndex = url.indexOf("/upload/");
        if (uploadIndex == -1) {
            return url;
        }

        String before = url.substring(0, uploadIndex + "/upload/".length());
        String after = url.substring(uploadIndex + "/upload/".length());

        return before + transformations + "/" + after;
    }
}
