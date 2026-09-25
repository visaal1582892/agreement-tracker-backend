package com.medplus.agreement_tracker_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueRecognitionMonthDto {
    private String calendarMonth;
    private List<String> triggeredPeriods;
    private BigDecimal earnedPayout;
    
    // Phase 3: Payment Allocation
    private String paymentInterval;
    private BigDecimal finalPayableAmount;
    
    private Map<String, List<CommercialPayoutLineItem>> breakdownByPeriod;
}
