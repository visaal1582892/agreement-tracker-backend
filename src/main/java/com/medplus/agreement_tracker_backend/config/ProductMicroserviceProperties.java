package com.medplus.agreement_tracker_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "product.microservice")
public class ProductMicroserviceProperties {

    private String baseUrl = "http://192.168.1.211:30021";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 30000;
}
