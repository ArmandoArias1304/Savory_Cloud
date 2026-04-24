package com.aatechsolutions.elgransazon.infrastructure.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Configuration for Cloudflare Images REST API.
 *
 * Three credentials are needed (all set via environment variables in application.properties):
 *  - cloudflare.account-id        : Account ID (semi-public, found in Cloudflare dashboard).
 *  - cloudflare.images.api-token  : Bearer token with permission "Images: Edit". SECRET.
 *  - cloudflare.images.hash       : Public account hash that appears in delivery URLs
 *                                   (https://imagedelivery.net/{HASH}/{image_id}/{variant}).
 *
 * The {@link RestClient} bean preconfigures the base URL and the Authorization header
 * so service classes don't have to repeat them.
 */
@Configuration
@Getter
public class CloudflareImagesConfig {

    @Value("${cloudflare.account-id:}")
    private String accountId;

    @Value("${cloudflare.images.api-token:}")
    private String apiToken;

    /**
     * Public account hash used in delivery URLs.
     * Example URL: https://imagedelivery.net/{hash}/{image_id}/{variant}
     */
    @Value("${cloudflare.images.hash:}")
    private String accountHash;

    /**
     * Whether Cloudflare Images is properly configured. When false the application
     * starts but image uploads will fail fast with a clear error.
     */
    public boolean isConfigured() {
        return accountId != null && !accountId.isBlank()
                && apiToken != null && !apiToken.isBlank()
                && accountHash != null && !accountHash.isBlank();
    }

    @Bean
    public RestClient cloudflareImagesRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.cloudflare.com/client/v4/accounts/" + accountId + "/images")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .requestFactory(buildRequestFactory())
                .build();
    }

    private org.springframework.http.client.SimpleClientHttpRequestFactory buildRequestFactory() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        return factory;
    }
}
