package com.medplus.agreement_tracker_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "image.server")
public class ImageServerProperties {

    private String oauthTokenUrl =
            "https://marigold.medplusindia.com:6728/oauth-server/oauth/token?grant_type=client_credentials";
    private String transitUrl =
            "https://marigold.medplusindia.com:6426/diagnostics/transit/image-server"
                    + "?origin=marketing_automation&clientId=medplus_marketing_automation_app";
    private String oauthClientId = "meditimes_client";
    private String oauthClientSecret = "Med1T!me$CL";
    private String transitOrigin = "marketing_automation";
    private String transitClientId = "medplus_marketing_automation_app";
    private String imageType = "LT";
    private int connectTimeoutMs = 15_000;
    private int readTimeoutMs = 120_000;
}
