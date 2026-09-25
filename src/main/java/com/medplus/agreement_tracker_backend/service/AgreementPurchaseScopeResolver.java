package com.medplus.agreement_tracker_backend.service;

import com.medplus.agreement_tracker_backend.constants.IncomeTypeNames;
import com.medplus.agreement_tracker_backend.dto.request.PurchaseAggregationPeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementComputedProduct;
import com.medplus.agreement_tracker_backend.entity.AgreementLocation;
import com.medplus.agreement_tracker_backend.entity.AgreementVendor;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.repository.AgreementComputedProductRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementLocationRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves the purchase filtering scope for an agreement version. Shared by the raw aggregation
 * (Phase 1) and the commercial payout engine (Phase 2) so that grand totals and per-period sums are
 * always computed against an identical product/supplier/state/city/period scope.
 */
@Component
@RequiredArgsConstructor
public class AgreementPurchaseScopeResolver {

    private final AgreementComputedProductRepository computedProductRepository;
    private final AgreementVendorRepository vendorRepository;
    private final AgreementLocationRepository agreementLocationRepository;

    /**
     * Converts selected months into year*100+month keys.
     */
    public List<Integer> resolvePeriodKeys(List<PurchaseAggregationPeriod> periods) {
        if (periods == null || periods.isEmpty()) {
            return List.of();
        }
        return periods.stream()
                .map(period -> period.year() * 100 + period.month())
                .distinct()
                .toList();
    }

    public List<String> resolveProductScope(Long agreementVersionId, List<String> requestedProductIds) {
        if (requestedProductIds != null && !requestedProductIds.isEmpty()) {
            return requestedProductIds.stream().filter(Objects::nonNull).distinct().toList();
        }
        return computedProductRepository.findByAgreementVersionId(agreementVersionId).stream()
                .map(AgreementComputedProduct::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public List<Long> resolveSupplierScope(Long agreementVersionId, List<Long> requestedSupplierIds) {
        if (requestedSupplierIds != null && !requestedSupplierIds.isEmpty()) {
            return requestedSupplierIds.stream().filter(Objects::nonNull).distinct().toList();
        }
        return vendorRepository.findByAgreementVersionId(agreementVersionId).stream()
                .map(AgreementVendor::getVendorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public List<String> normalizeLocationCodes(List<String> requestedCodes) {
        if (requestedCodes == null) {
            return List.of();
        }
        return requestedCodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .distinct()
                .toList();
    }

    /** @deprecated use {@link #normalizeLocationCodes(List)} */
    @Deprecated
    public List<String> normalizeStateCodes(List<String> requestedStateCodes) {
        return normalizeLocationCodes(requestedStateCodes);
    }

    /**
     * Resolves state/city purchase filters from the normalized agreement_locations table.
     * <ul>
     *   <li>Data Fee → unrestricted: empty request = no geo filter; else use request as-is.</li>
     *   <li>Standard → empty request defaults to agreement locations; non-empty intersects with them.</li>
     * </ul>
     */
    public ResolvedGeoFilter resolveGeoFilter(
            AgreementVersion version,
            List<String> requestedStateCodes,
            List<String> requestedCityCodes) {
        List<String> requestedStates = normalizeLocationCodes(requestedStateCodes);
        List<String> requestedCities = normalizeLocationCodes(requestedCityCodes);

        if (isUnrestrictedGeography(version)) {
            return new ResolvedGeoFilter(requestedStates, requestedCities);
        }

        AgreementLocationScope allowed = loadAgreementLocationScope(version.getId());
        if (requestedStates.isEmpty() && requestedCities.isEmpty()) {
            return new ResolvedGeoFilter(allowed.stateCodes(), allowed.cityCodes());
        }

        Set<String> allowedStates = new LinkedHashSet<>(allowed.stateCodes());
        Set<String> allowedCities = new LinkedHashSet<>(allowed.cityCodes());
        List<String> states = requestedStates.stream().filter(allowedStates::contains).toList();
        List<String> cities = requestedCities.stream().filter(allowedCities::contains).toList();
        return new ResolvedGeoFilter(states, cities);
    }

    private boolean isUnrestrictedGeography(AgreementVersion version) {
        if (version.getIncomeType() != null
                && IncomeTypeNames.DATA_FEE.equalsIgnoreCase(version.getIncomeType().getName())) {
            return true;
        }
        // If no location rows exist at all → treat as unrestricted (ALL)
        List<AgreementLocation> locs = agreementLocationRepository.findByAgreementVersionId(version.getId());
        return locs.isEmpty();
    }

    private AgreementLocationScope loadAgreementLocationScope(Long versionId) {
        List<AgreementLocation> locs = agreementLocationRepository.findByAgreementVersionId(versionId);
        List<String> stateCodes = locs.stream()
                .filter(l -> "STATE".equalsIgnoreCase(l.getLocationType()) && l.getStateCode() != null)
                .map(AgreementLocation::getStateCode)
                .distinct()
                .toList();
        List<String> cityCodes = locs.stream()
                .filter(l -> "CITY".equalsIgnoreCase(l.getLocationType()) && l.getCityCode() != null)
                .map(AgreementLocation::getCityCode)
                .distinct()
                .toList();
        return new AgreementLocationScope(stateCodes, cityCodes);
    }

    public record ResolvedGeoFilter(List<String> stateCodes, List<String> cityCodes) {
        public boolean hasAny() {
            return (stateCodes != null && !stateCodes.isEmpty())
                    || (cityCodes != null && !cityCodes.isEmpty());
        }
    }

    private record AgreementLocationScope(List<String> stateCodes, List<String> cityCodes) {
    }
}
