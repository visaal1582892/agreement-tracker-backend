package com.medplus.agreement_tracker_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueRecognitionPreviewResponse {
    private Long agreementVersionId;
    private List<RevenueRecognitionMonthDto> monthlyPreviews;
}
