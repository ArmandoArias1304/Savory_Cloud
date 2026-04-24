package com.aatechsolutions.elgransazon.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Builds optimized Cloudflare Images delivery URLs using URL-based flexible variants.
 *
 * <p>Delivery URL format:
 *   https://imagedelivery.net/{accountHash}/{imageId}/{variant_or_flexible_params}
 *
 * <p>Examples of flexible parameters supported by Cloudflare:
 *   <ul>
 *     <li>w=400,fit=cover,quality=85,format=auto</li>
 *     <li>w=120,h=120,fit=cover,quality=85,format=auto</li>
 *     <li>w=600,h=400,fit=cover,gravity=auto,quality=85,format=auto</li>
 *   </ul>
 *
 * <p>format=auto serves AVIF/WebP/JPEG depending on the requesting browser
 * (best-format-per-client). quality=85 is the industry-standard sweet spot —
 * imperceptible loss with ~50% size reduction vs quality=100.
 *
 * <p>This bean is registered with name "cloudinaryUrl" (legacy) so existing
 * Thymeleaf templates (~30 of them) keep working without changes during the
 * Cloudflare migration. It is also registered as "imageUrl" for new templates.
 *
 * <p>Usage in Thymeleaf:
 * <pre>
 *   th:src="${@cloudinaryUrl.card(item.imageUrl)}"
 *   th:srcset="${@cloudinaryUrl.cardSrcset(item.imageUrl)}"
 *   th:src="${@cloudinaryUrl.thumb(item.imageUrl)}"
 *   th:src="${@cloudinaryUrl.modal(item.imageUrl)}"
 * </pre>
 */
@Component("cloudinaryUrl")
@RequiredArgsConstructor
public class CloudflareImagesUrlHelper {

    private static final String DELIVERY_HOST = "imagedelivery.net";

    /** Responsive widths for card srcset (covers mobile small to desktop). */
    private static final int[] CARD_WIDTHS = {200, 300, 400, 600};

    
    // ─── Card images (menu items list, grids) ───────────────────────────

    /** Fallback src for cards (used with srcset for browsers that don't support srcset). */
    public String card(String url) {
        return transform(url, "w=400,fit=cover,quality=85,format=auto");
    }

    /**
     * Generate srcset for responsive card images.
     * Returns: "url-200w 200w, url-300w 300w, url-400w 400w, url-600w 600w".
     * The browser picks the right size based on the `sizes` attribute.
     */
    public String cardSrcset(String url) {
        if (url == null || url.isEmpty() || !url.contains(DELIVERY_HOST)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CARD_WIDTHS.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(transform(url, "w=" + CARD_WIDTHS[i] + ",fit=cover,quality=85,format=auto"));
            sb.append(' ').append(CARD_WIDTHS[i]).append('w');
        }
        return sb.toString();
    }

    /** Thumbnails (cart items, small previews, admin lists). 120x120 cropped to fill. */
    public String thumb(String url) {
        return transform(url, "w=120,h=120,fit=cover,quality=85,format=auto");
    }

    /** Modal/detail images. 600x400 fixed area, gravity=auto picks salient region. */
    public String modal(String url) {
        return transform(url, "w=600,h=400,fit=cover,gravity=auto,quality=85,format=auto");
    }

    // ─── Logos (sidebar, navbar, login, hero) ───────────────────────────

    /** Sidebar/navbar logos in 48x48 containers (uses 96px for retina). */
    public String logoSmall(String url) {
        return transform(url, "w=96,h=96,fit=contain,quality=85,format=auto");
    }

    /** Login page logo in 96x96 container (uses 192px for retina). */
    public String logoLarge(String url) {
        return transform(url, "w=192,h=192,fit=contain,quality=85,format=auto");
    }

    /** Landing page hero logos for 300-340px containers (600px for 2x retina). */
    public String logoHero(String url) {
        return transform(url, "w=600,h=600,fit=contain,quality=90,format=auto");
    }

    // ─── Generic transformer (for printable tickets that need PNG) ──────

    /**
     * Apply arbitrary Cloudflare flexible variant parameters to a delivery URL.
     * Useful for printable tickets that require a specific format like PNG.
     *
     * <p>Example: {@code transform(url, "w=200,h=200,fit=contain,format=png,quality=85")}
     */
    public String transform(String url, String params) {
        if (url == null || url.isEmpty() || !url.contains(DELIVERY_HOST)) {
            return url;
        }

        // Strip the existing variant segment (everything after the last "/")
        // and append the flexible-params segment instead.
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash == -1) {
            return url;
        }
        return url.substring(0, lastSlash + 1) + params;
    }
}
