package com.medplus.agreement_tracker_backend.dto.response.master;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
public class ChannelMasterResponse {
    private Long id;
    private String channelName;
    private String channelCode;
    @Getter(onMethod_ = {@JsonProperty("isActive")})
    private boolean isActive;
}
