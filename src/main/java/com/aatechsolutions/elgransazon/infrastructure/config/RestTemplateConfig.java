package com.aatechsolutions.elgransazon.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@Slf4j
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, mergedTrustManagers(), null);
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        return new RestTemplate();
    }

    /**
     * Merges JDK cacerts + OS trust store (Windows-ROOT on Windows).
     * On Linux/Docker falls back to JDK cacerts only.
     */
    private TrustManager[] mergedTrustManagers() throws Exception {
        TrustManagerFactory defaultTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        defaultTmf.init((KeyStore) null);

        TrustManagerFactory osTmf = null;
        try {
            KeyStore osStore = KeyStore.getInstance("Windows-ROOT");
            osStore.load(null, null);
            osTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            osTmf.init(osStore);
            log.info("Loaded Windows-ROOT trust store for HTTPS connections");
        } catch (Exception ignored) {
            // Not on Windows — just use JDK defaults
        }

        if (osTmf == null) {
            return defaultTmf.getTrustManagers();
        }

        X509TrustManager defaultTm = extractX509(defaultTmf);
        X509TrustManager osTm = extractX509(osTmf);
        return new TrustManager[]{new CompositeX509TrustManager(defaultTm, osTm)};
    }

    private X509TrustManager extractX509(TrustManagerFactory tmf) {
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager x509) {
                return x509;
            }
        }
        throw new IllegalStateException("No X509TrustManager found");
    }

    private record CompositeX509TrustManager(X509TrustManager... delegates) implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegates[0].checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            for (X509TrustManager tm : delegates) {
                try {
                    tm.checkServerTrusted(chain, authType);
                    return;
                } catch (CertificateException ignored) {
                }
            }
            delegates[0].checkServerTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> all = new ArrayList<>();
            for (X509TrustManager tm : delegates) {
                all.addAll(Arrays.asList(tm.getAcceptedIssuers()));
            }
            return all.toArray(new X509Certificate[0]);
        }
    }
}
