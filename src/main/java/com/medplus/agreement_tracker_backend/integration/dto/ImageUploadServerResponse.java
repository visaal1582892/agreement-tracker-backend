package com.medplus.agreement_tracker_backend.integration.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImageUploadServerResponse {

    @JsonAlias({"imagePath", "image_path"})
    private String imagePath;

    @JsonAlias({"thumbnailPath", "thumbnail_path"})
    private String thumbnailPath;

    @JsonAlias({"originalImageName", "original_image_name"})
    private String originalImageName;
}
