package com.medplus.agreement_tracker_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient productMicroserviceRestClient(ProductMicroserviceProperties properties) {
        return buildRestClient(properties.getBaseUrl(), properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
    }

    @Bean
    public RestClient partnerMicroserviceRestClient(PartnerMicroserviceProperties properties) {
        return buildRestClient(properties.getBaseUrl(), properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
    }

    @Bean
    public RestClient imageServerRestClient(ImageServerProperties properties) {
        TrustAllSslClientHttpRequestFactory requestFactory = new TrustAllSslClientHttpRequestFactory(
                properties.getConnectTimeoutMs(),
                properties.getReadTimeoutMs());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    private RestClient buildRestClient(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
