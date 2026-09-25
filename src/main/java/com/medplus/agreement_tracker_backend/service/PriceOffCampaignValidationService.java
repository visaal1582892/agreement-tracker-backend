package com.medplus.agreement_tracker_backend.service;

import com.medplus.agreement_tracker_backend.dto.response.PriceOffParsedRowDto;
import com.medplus.agreement_tracker_backend.integration.dto.IntegrationProductResponse;
import com.medplus.agreement_tracker_backend.enums.PriceOffDiscountType;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.repository.ConsumerPriceOffCampaignRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public interface PriceOffCampaignValidationService {

    void validateCampaignDates(LocalDate startDate, LocalDate endDate);

    void validateDiscountSanity(
            PriceOffDiscountType discountType,
            BigDecimal mrp,
            BigDecimal baseOffer,
            BigDecimal medplusContribution);

    void validateNoDbOverlap(String productId, LocalDate startDate, LocalDate endDate, Long excludeCampaignId);

    void validateBatchInternalOverlaps(List<PriceOffParsedRowDto> validRows, Map<Integer, String> rowErrors);

    BigDecimal resolveAuthoritativeCp(IntegrationProductResponse product, BigDecimal fallbackCp);
}
