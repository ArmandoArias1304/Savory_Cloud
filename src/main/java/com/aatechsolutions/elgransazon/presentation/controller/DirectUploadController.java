package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.CloudflareImagesService;
import com.aatechsolutions.elgransazon.application.service.ImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * REST controller that mints one-time direct-upload URLs for the browser.
 *
 * <p>Two paths are exposed so that Spring Security's path-based rules already in
 * {@code SecurityConfig} apply automatically:
 *  <ul>
 *    <li>{@code /admin/api/cf-images/upload-token}      — protected by ROLE_ADMIN/MANAGER</li>
 *    <li>{@code /programmer/api/cf-images/upload-token} — protected by ROLE_PROGRAMMER</li>
 *  </ul>
 *
 * <p>The "kind" parameter is whitelisted per role to prevent role escalation
 * (e.g., an admin cannot mint a token for the global system logo, only the
 * programmer can; admins are restricted to per-tenant folders).
 *
 * <p>After receiving the token the browser uploads the file directly to the
 * returned {@code uploadUrl}. The application persists the {@code finalUrl}
 * in the entity's imageUrl column when the form is submitted.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class DirectUploadController {

    /** Folder kinds an admin/manager can request tokens for. */
    private static final Set<String> ADMIN_KINDS = Set.of(
            "menu-items", "promotions", "restaurant-logo"
    );

    /** Folder kinds a programmer can request tokens for. */
    private static final Set<String> PROGRAMMER_KINDS = Set.of(
            "system-logo", "landing"
    );

    private final ImageStorageService imageStorageService;

    @PostMapping("/admin/api/cf-images/upload-token")
    public ResponseEntity<Map<String, Object>> adminUploadToken(
            @RequestParam("kind") String kind,
            @RequestParam("name") String name) {
        return mintToken(kind, name, ADMIN_KINDS);
    }

    @PostMapping("/programmer/api/cf-images/upload-token")
    public ResponseEntity<Map<String, Object>> programmerUploadToken(
            @RequestParam("kind") String kind,
            @RequestParam("name") String name) {
        return mintToken(kind, name, PROGRAMMER_KINDS);
    }

    private ResponseEntity<Map<String, Object>> mintToken(String kind, String name, Set<String> allowedKinds) {
        Map<String, Object> body = new HashMap<>();
        if (kind == null || !allowedKinds.contains(kind)) {
            body.put("success", false);
            body.put("message", "Tipo de carpeta no permitido: " + kind
                    + ". Permitidos: " + String.join(", ", allowedKinds));
            return ResponseEntity.badRequest().body(body);
        }
        try {
            CloudflareImagesService.DirectUploadToken token =
                    imageStorageService.prepareDirectUpload(kind, name == null ? "" : name);
            body.put("success", true);
            body.put("uploadUrl", token.uploadUrl());
            body.put("imageId", token.imageId());
            body.put("finalUrl", token.finalUrl());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("Error issuing direct upload token. kind={}, name={}, err={}", kind, name, e.getMessage());
            body.put("success", false);
            body.put("message", "No se pudo generar el token de carga: " + e.getMessage());
            return ResponseEntity.status(500).body(body);
        }
    }
}
