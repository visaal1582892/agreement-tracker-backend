package com.medplus.agreement_tracker_backend.dto.request.master;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChannelMasterRequest {

    @NotBlank(message = "Channel name is required")
    @Size(max = 255, message = "Channel name must not exceed 255 characters")
    private String channelName;

    @NotBlank(message = "Channel code is required")
    @Size(max = 50, message = "Channel code must not exceed 50 characters")
    private String channelCode;

    private Boolean isActive;
}
