package com.medplus.agreement_tracker_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "partner.microservice")
public class PartnerMicroserviceProperties {

    private String baseUrl = "http://192.168.0.181:32114";
    private int tenantId = 1;
    private int searchLimit = 50;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 5000;
}
