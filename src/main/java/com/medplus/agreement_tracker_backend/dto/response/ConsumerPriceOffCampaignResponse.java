package com.medplus.agreement_tracker_backend.dto.response;

import com.medplus.agreement_tracker_backend.enums.PriceOffApprovalStatus;
import com.medplus.agreement_tracker_backend.enums.PriceOffDiscountType;
import com.medplus.agreement_tracker_backend.enums.PriceOffDisplayStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record ConsumerPriceOffCampaignResponse(
        Long id,
        String productCode,
        String productId,
        String productName,
        String manufacturerName,
        String l3Category,
        LocalDate startDate,
        LocalDate endDate,
        Integer durationMonths,
        Integer maxUnitCap,
        Integer fromQty,
        Integer totalQty,
        BigDecimal creditNote,
        Map<String, Integer> locationAllocations,
        String remarks,
        String campaignId,
        LocalDateTime campaignIdUpdatedAt,
        Long campaignIdUpdatedByUserId,
        String locationLabel,
        java.util.List<String> stateNames,
        String channelLabel,
        java.util.List<String> channelNames,
        PriceOffDiscountType discountType,
        String discountTypeLabel,
        BigDecimal cp,
        BigDecimal mrp,
        BigDecimal baseOffer,
        BigDecimal medplusContribution,
        BigDecimal marginPercent,
        BigDecimal finalOffer,
        BigDecimal percentOff,
        BigDecimal finalMarginPercent,
        Boolean isNegativeMargin,
        PriceOffApprovalStatus approvalStatus,
        PriceOffDisplayStatus displayStatus,
        Integer unitsConsumed,
        Long submittedByUserId,
        Long approvedByUserId,
        String rejectionRemarks,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
