package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.dto.response.PriceOffParsedRowDto;
import com.medplus.agreement_tracker_backend.integration.dto.IntegrationProductResponse;
import com.medplus.agreement_tracker_backend.enums.PriceOffDiscountType;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.repository.ConsumerPriceOffCampaignRepository;
import com.medplus.agreement_tracker_backend.service.PriceOffCampaignValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PriceOffCampaignValidationServiceImpl implements PriceOffCampaignValidationService {

    private static final String OVERLAP_MESSAGE =
            "An active campaign already exists for this product during this date range.";

    private final ConsumerPriceOffCampaignRepository campaignRepository;

    @Override
    public void validateCampaignDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new BusinessException("Start date is required");
        }
        if (endDate == null) {
            throw new BusinessException("End date is required");
        }
        if (startDate.isBefore(LocalDate.now())) {
            throw new BusinessException("Start date cannot be in the past");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException("End date must be on or after start date");
        }
    }

    @Override
    public void validateDiscountSanity(
            PriceOffDiscountType discountType,
            BigDecimal mrp,
            BigDecimal baseOffer,
            BigDecimal medplusContribution) {
        if (mrp == null || mrp.signum() <= 0) {
            throw new BusinessException("MRP must be greater than zero");
        }
        BigDecimal safeBaseOffer = baseOffer != null ? baseOffer : BigDecimal.ZERO;
        if (safeBaseOffer.signum() < 0) {
            throw new BusinessException("Base offer cannot be negative");
        }
        BigDecimal safeMedplus = medplusContribution != null ? medplusContribution : BigDecimal.ZERO;

        if (discountType == PriceOffDiscountType.DISC_VAL) {
            BigDecimal totalDiscount = safeBaseOffer.add(safeMedplus);
            if (totalDiscount.compareTo(mrp) >= 0) {
                throw new BusinessException("Total discount cannot exceed or equal the MRP");
            }
            return;
        }

        if (discountType == PriceOffDiscountType.DISC_PERCENT) {
            BigDecimal totalPercent = safeBaseOffer.add(safeMedplus);
            if (totalPercent.compareTo(new BigDecimal("100")) >= 0) {
                throw new BusinessException("Total discount percentage cannot exceed or equal 100%");
            }
        }
    }

    @Override
    public void validateNoDbOverlap(
            String productId,
            LocalDate startDate,
            LocalDate endDate,
            Long excludeCampaignId) {
        long overlaps = campaignRepository.countOverlappingCampaigns(
                productId, startDate, endDate, excludeCampaignId);
        if (overlaps > 0) {
            throw new BusinessException(OVERLAP_MESSAGE);
        }
    }

    @Override
    public void validateBatchInternalOverlaps(List<PriceOffParsedRowDto> validRows, Map<Integer, String> rowErrors) {
        for (int left = 0; left < validRows.size(); left++) {
            PriceOffParsedRowDto first = validRows.get(left);
            int firstRowIdx = first.rowNumber() - 1;
            if (rowErrors.containsKey(firstRowIdx)) {
                continue;
            }
            for (int right = left + 1; right < validRows.size(); right++) {
                PriceOffParsedRowDto second = validRows.get(right);
                int secondRowIdx = second.rowNumber() - 1;
                if (rowErrors.containsKey(secondRowIdx)) {
                    continue;
                }
                if (!first.productId().equals(second.productId())) {
                    continue;
                }
                if (datesOverlap(first.startDate(), first.endDate(), second.startDate(), second.endDate())) {
                    rowErrors.put(firstRowIdx, OVERLAP_MESSAGE);
                    rowErrors.put(secondRowIdx, OVERLAP_MESSAGE);
                }
            }
        }

        Iterator<PriceOffParsedRowDto> iterator = validRows.iterator();
        while (iterator.hasNext()) {
            PriceOffParsedRowDto row = iterator.next();
            if (rowErrors.containsKey(row.rowNumber() - 1)) {
                iterator.remove();
            }
        }
    }

    @Override
    public BigDecimal resolveAuthoritativeCp(IntegrationProductResponse product, BigDecimal fallbackCp) {
        if (product.getCp() != null && product.getCp().signum() > 0) {
            return product.getCp();
        }
        if (fallbackCp != null && fallbackCp.signum() > 0) {
            return fallbackCp;
        }
        throw new BusinessException(
                "Cost Price (CP) is not available for product '" + product.getId() + "'");
    }

    private boolean datesOverlap(
            LocalDate firstStart,
            LocalDate firstEnd,
            LocalDate secondStart,
            LocalDate secondEnd) {
        return !firstStart.isAfter(secondEnd) && !secondStart.isAfter(firstEnd);
    }
}
