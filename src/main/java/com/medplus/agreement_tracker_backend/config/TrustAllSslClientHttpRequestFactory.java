package com.medplus.agreement_tracker_backend.config;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * HTTP request factory that trusts self-signed certificates on internal MedPlus hosts.
 */
public class TrustAllSslClientHttpRequestFactory extends SimpleClientHttpRequestFactory {

    private final SSLContext sslContext;

    public TrustAllSslClientHttpRequestFactory(int connectTimeoutMs, int readTimeoutMs) {
        setConnectTimeout(connectTimeoutMs);
        setReadTimeout(readTimeoutMs);
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            }, new SecureRandom());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize trust-all SSL context", ex);
        }
    }

    @Override
    protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
        if (connection instanceof HttpsURLConnection httpsConnection) {
            httpsConnection.setSSLSocketFactory(sslContext.getSocketFactory());
            httpsConnection.setHostnameVerifier((hostname, session) -> true);
        }
        super.prepareConnection(connection, httpMethod);
    }
}
