package com.medplus.agreement_tracker_backend.dto.response;

import com.medplus.agreement_tracker_backend.enums.PriceOffDiscountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record PriceOffParsedRowDto(
        int rowNumber,
        String productId,
        String productCode,
        String productName,
        LocalDate startDate,
        LocalDate endDate,
        Integer maxUnitCap,
        String locationLabel,
        String channelLabel,
        PriceOffDiscountType discountType,
        String discountTypeLabel,
        BigDecimal cp,
        BigDecimal mrp,
        BigDecimal baseOffer,
        BigDecimal medplusContribution,
        Integer fromQty,
        String remarks,
        Map<String, Integer> locationAllocations,
        Integer totalQty,
        BigDecimal creditNote,
        BigDecimal marginPercent,
        BigDecimal finalOffer,
        BigDecimal percentOff,
        BigDecimal finalMarginPercent,
        boolean negativeMargin
) {}
