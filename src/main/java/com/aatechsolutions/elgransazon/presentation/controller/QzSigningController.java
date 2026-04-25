package com.aatechsolutions.elgransazon.presentation.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * QZ Tray message-signing endpoints.
 * Removes the "Allow / Remember this decision" dialog by signing every QZ Tray
 * print request with a trusted private key/certificate (override mechanism).
 *
 * Configure in application.properties:
 *   qz.signing.enabled=true
 *   qz.signing.certificate-path=/absolute/path/to/digital-certificate.txt
 *   qz.signing.private-key-path=/absolute/path/to/private-key.pem
 *
 * Endpoints (must be permitAll in SecurityConfig):
 *   GET /qz/certificate          -> returns the public certificate (text/plain)
 *   GET /qz/sign?request=...     -> returns SHA512withRSA signature, base64
 */
@RestController
@RequestMapping("/qz")
@Slf4j
public class QzSigningController {

    @Value("${qz.signing.enabled:false}")
    private boolean enabled;

    @Value("${qz.signing.certificate-path:}")
    private String certificatePath;

    @Value("${qz.signing.private-key-path:}")
    private String privateKeyPath;

    @GetMapping(value = "/certificate", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> certificate() {
        if (!enabled || certificatePath == null || certificatePath.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        try {
            Path p = Paths.get(certificatePath);
            if (!Files.exists(p)) {
                log.warn("QZ certificate file not found at: {}", certificatePath);
                return ResponseEntity.notFound().build();
            }
            String pem = Files.readString(p, StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-store")
                    .body(pem);
        } catch (Exception e) {
            log.error("Failed to read QZ certificate: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(value = "/sign", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> sign(@RequestParam("request") String request) {
        if (!enabled || privateKeyPath == null || privateKeyPath.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        try {
            PrivateKey key = loadPrivateKey(privateKeyPath);
            Signature signature = Signature.getInstance("SHA512withRSA");
            signature.initSign(key);
            signature.update(request.getBytes(StandardCharsets.UTF_8));
            String b64 = Base64.getEncoder().encodeToString(signature.sign());
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-store")
                    .body(b64);
        } catch (Exception e) {
            log.error("Failed to sign QZ request: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("");
        }
    }

    private PrivateKey loadPrivateKey(String path) throws Exception {
        String pem = Files.readString(Paths.get(path), StandardCharsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] der = Base64.getDecoder().decode(pem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }
}
