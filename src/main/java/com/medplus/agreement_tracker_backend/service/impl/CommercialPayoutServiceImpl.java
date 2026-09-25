package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.constants.IncomeTypeNames;
import com.medplus.agreement_tracker_backend.dto.request.PurchaseAggregationRequest;
import com.medplus.agreement_tracker_backend.dto.response.CommercialPayoutResponse;
import com.medplus.agreement_tracker_backend.dto.response.PurchaseAggregationResponse;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.entity.IncomeType;
import com.medplus.agreement_tracker_backend.enums.CalculationBasis;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.exception.ResourceNotFoundException;
import com.medplus.agreement_tracker_backend.repository.AgreementVersionRepository;
import com.medplus.agreement_tracker_backend.service.AgreementPurchaseScopeResolver;
import com.medplus.agreement_tracker_backend.service.CommercialPayoutService;
import com.medplus.agreement_tracker_backend.service.CommercialPurchaseAggregationService;
import com.medplus.agreement_tracker_backend.service.commercial.CommercialCalculationContext;
import com.medplus.agreement_tracker_backend.service.commercial.CommercialCalculationFactory;
import com.medplus.agreement_tracker_backend.service.commercial.CommercialCalculationStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommercialPayoutServiceImpl implements CommercialPayoutService {

    private final AgreementVersionRepository agreementVersionRepository;
    private final AgreementPurchaseScopeResolver scopeResolver;
    private final CommercialPurchaseAggregationService commercialPurchaseAggregationService;
    private final CommercialCalculationFactory calculationFactory;

    @Override
    @Transactional(readOnly = true)
    public CommercialPayoutResponse calculatePayouts(Long agreementVersionId, PurchaseAggregationRequest request) {
        AgreementVersion version = agreementVersionRepository.findById(agreementVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", agreementVersionId));

        boolean assetRental = isAssetRental(version);
        if (!assetRental && (request.periods() == null || request.periods().isEmpty())) {
            throw new BusinessException("At least one period (month/year) must be selected");
        }
        PurchaseAggregationResponse rawData = assetRental
                ? emptyAggregation(agreementVersionId, version.getCalculationBasis())
                : commercialPurchaseAggregationService.aggregatePurchases(agreementVersionId, request);

        List<Integer> periodKeys = scopeResolver.resolvePeriodKeys(request.periods());
        List<String> productIds = scopeResolver.resolveProductScope(agreementVersionId, request.productIds());
        List<Long> supplierIds = scopeResolver.resolveSupplierScope(agreementVersionId, request.supplierIds());
        var geo = scopeResolver.resolveGeoFilter(version, request.stateCodes(), request.cityCodes());

        CommercialCalculationContext context = new CommercialCalculationContext(
                version,
                version.getCalculationBasis(),
                periodKeys,
                productIds,
                supplierIds,
                geo.stateCodes(),
                geo.cityCodes(),
                rawData);

        CommercialCalculationStrategy strategy = calculationFactory.resolve(version);
        return strategy.calculatePayout(context);
    }

    private boolean isAssetRental(AgreementVersion version) {
        IncomeType incomeType = version.getIncomeType();
        return incomeType != null
                && incomeType.getName() != null
                && IncomeTypeNames.ASSET_RENTALS.equalsIgnoreCase(incomeType.getName());
    }

    private PurchaseAggregationResponse emptyAggregation(Long agreementVersionId, CalculationBasis basis) {
        return new PurchaseAggregationResponse(
                agreementVersionId,
                basis,
                null,
                0,
                0L,
                BigDecimal.ZERO,
                List.of());
    }
}
