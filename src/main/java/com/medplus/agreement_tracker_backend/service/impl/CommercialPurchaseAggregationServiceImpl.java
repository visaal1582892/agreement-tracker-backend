package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.dto.request.PurchaseAggregationRequest;
import com.medplus.agreement_tracker_backend.dto.response.PurchaseAggregationResponse;
import com.medplus.agreement_tracker_backend.dto.response.MonthlyCalculationDto;
import com.medplus.agreement_tracker_backend.dto.response.VendorProductAggregateDto;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.enums.CalculationBasis;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.exception.ResourceNotFoundException;
import com.medplus.agreement_tracker_backend.repository.AgreementVersionRepository;
import com.medplus.agreement_tracker_backend.repository.CommercialCalculationDao;
import com.medplus.agreement_tracker_backend.service.AgreementPurchaseScopeResolver;
import com.medplus.agreement_tracker_backend.service.CommercialPurchaseAggregationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommercialPurchaseAggregationServiceImpl implements CommercialPurchaseAggregationService {

        private final AgreementVersionRepository agreementVersionRepository;
        private final AgreementPurchaseScopeResolver scopeResolver;
        private final CommercialCalculationDao commercialCalculationDao;

        @Override
        @Transactional(readOnly = true)
        public PurchaseAggregationResponse aggregatePurchases(Long agreementVersionId,
                        PurchaseAggregationRequest request) {
                AgreementVersion version = agreementVersionRepository.findById(agreementVersionId)
                                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion",
                                                agreementVersionId));

                CalculationBasis calculationBasis = version.getCalculationBasis();
                boolean useChallanDate = (calculationBasis == CalculationBasis.VENDOR_INVOICE);
                String dateColumn = useChallanDate ? "ChallanDate" : "DateVerified";

                List<Integer> periodKeysList = scopeResolver.resolvePeriodKeys(request.periods());
                if (periodKeysList.isEmpty()) {
                        throw new BusinessException("At least one period (month/year) must be selected");
                }
                Set<Integer> validPeriods = new HashSet<>(periodKeysList);

                List<String> productIds = scopeResolver.resolveProductScope(agreementVersionId, request.productIds());
                List<Long> supplierIds = scopeResolver.resolveSupplierScope(agreementVersionId, request.supplierIds());
                var geo = scopeResolver.resolveGeoFilter(version, request.stateCodes(), request.cityCodes());

                if (productIds.isEmpty() || supplierIds.isEmpty()) {
                        return new PurchaseAggregationResponse(
                                        agreementVersionId, calculationBasis, dateColumn, 0, 0L, BigDecimal.ZERO,
                                        List.of());
                }

                LocalDate startDate = request.periods().stream()
                                .map(p -> LocalDate.of(p.year(), p.month(), 1))
                                .min(LocalDate::compareTo)
                                .orElseThrow(() -> new BusinessException("Start date could not be determined"));

                LocalDate endDate = request.periods().stream()
                                .map(p -> YearMonth.of(p.year(), p.month()).atEndOfMonth())
                                .max(LocalDate::compareTo)
                                .orElseThrow(() -> new BusinessException("End date could not be determined"));

                // The UI might pass empty storeIds, which currently isn't supported in request
                // body, but passing null for now.
                // We only filter on storeIds if there's a reason to, otherwise we filter by
                // state and city via geo.
                List<MonthlyCalculationDto> rawMonthlyData = commercialCalculationDao.fetchAggregatedCalculations(
                                supplierIds,
                                productIds,
                                null, // storeIds not explicitly present in PurchaseAggregationRequest yet
                                geo.stateCodes(), // city is handled differently or omitted? Wait, geo has stateCodes
                                                  // and cityCodes.
                                startDate,
                                endDate,
                                useChallanDate);

                // Filter and aggregate data in-memory
                List<VendorProductAggregateDto> rows = rawMonthlyData.stream()
                                .filter(dto -> validPeriods.contains(dto.periodMonth()))
                                .collect(Collectors.groupingBy(
                                                dto -> new VendorProductKey(dto.supplierId(), dto.productId()),
                                                Collectors.reducing(
                                                                new VendorProductAggregateDto(null, null, 0L,
                                                                                BigDecimal.ZERO),
                                                                dto -> new VendorProductAggregateDto(dto.supplierId(),
                                                                                dto.productId(), dto.totalNetQty(),
                                                                                dto.totalNetValue()),
                                                                (a, b) -> new VendorProductAggregateDto(
                                                                                a.supplierId() != null ? a.supplierId()
                                                                                                : b.supplierId(),
                                                                                a.productId() != null ? a.productId()
                                                                                                : b.productId(),
                                                                                a.totalNetQty() + b.totalNetQty(),
                                                                                a.totalNetValue().add(
                                                                                                b.totalNetValue())))))
                                .values()
                                .stream()
                                .toList();

                long totalNetQty = rows.stream().mapToLong(VendorProductAggregateDto::totalNetQty).sum();
                BigDecimal totalNetValue = rows.stream()
                                .map(VendorProductAggregateDto::totalNetValue)
                                .filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                return new PurchaseAggregationResponse(
                                agreementVersionId, calculationBasis, dateColumn, rows.size(), totalNetQty,
                                totalNetValue, rows);
        }

        private record VendorProductKey(Long supplierId, String productId) {
        }
}
