package com.medplus.agreement_tracker_backend.integration.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImageTransitDetails {

    @JsonAlias({"imageServerUrl", "image_server_url"})
    private String imageServerUrl;

    @JsonAlias({"accessToken", "access_token", "uploadToken", "upload_token"})
    private String accessToken;

    @JsonAlias({"clientId", "client_id"})
    private String clientId;
}
