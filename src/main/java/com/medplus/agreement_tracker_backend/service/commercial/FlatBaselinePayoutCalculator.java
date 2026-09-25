package com.medplus.agreement_tracker_backend.service.commercial;

import com.medplus.agreement_tracker_backend.dto.response.CommercialPayoutLineItem;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
import com.medplus.agreement_tracker_backend.enums.SlabValueType;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.repository.CommercialCalculationDao;
import com.medplus.agreement_tracker_backend.repository.CommercialCalculationDao.PurchaseTotals;
import com.medplus.agreement_tracker_backend.util.DynamicFinancialYearPeriodGenerator;
import com.medplus.agreement_tracker_backend.util.DynamicFinancialYearPeriodGenerator.PeriodSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes flat-baseline payouts across the agreement's standard FY-aware time periods. Intervals are
 * the financial-year-aligned periods generated for the configured frequency (respecting
 * financial_year_start_month), not raw calendar buckets. A FIXED baseline pays the flat commercial
 * value per period; a PERCENTAGE baseline pays that percentage of the net value purchased within the
 * period. Only periods overlapping the requested months produce a line item.
 */
@Component
@RequiredArgsConstructor
public class FlatBaselinePayoutCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final CommercialCalculationDao commercialCalculationDao;

    public List<CommercialPayoutLineItem> calculateIntervals(String segmentType, CommercialCalculationContext context) {
        AgreementVersion version = context.version();
        BigDecimal commercialValue = version.getCommercialValue();
        if (commercialValue == null) {
            return List.of();
        }
        PayoutFrequency frequency = version.getFlatBaselineFrequency();
        if (frequency == null) {
            throw new BusinessException(
                    "Flat baseline frequency is not configured for agreement version " + version.getId());
        }
        if (version.getStartDate() == null || version.getExpiryDate() == null) {
            throw new BusinessException(
                    "Contract start and expiry dates are required to compute flat payouts for version " + version.getId());
        }

        int financialYearStartMonth =
                DynamicFinancialYearPeriodGenerator.resolveStartMonth(version.getFinancialYearStartMonth());
        List<PeriodSpec> periodSpecs = DynamicFinancialYearPeriodGenerator.generatePeriodSpecs(
                frequency, version.getStartDate(), version.getExpiryDate(), financialYearStartMonth);

        SlabValueType valueType = version.getFlatValueType() != null ? version.getFlatValueType() : SlabValueType.FIXED;
        List<Integer> requestedPeriodKeys = context.requestedPeriodKeys();
        List<CommercialPayoutLineItem> lineItems = new ArrayList<>();

        for (PeriodSpec spec : periodSpecs) {
            List<Integer> overlap = spec.months().stream()
                    .map(this::periodKey)
                    .filter(requestedPeriodKeys::contains)
                    .toList();
            if (overlap.isEmpty()) {
                continue;
            }

            LocalDate startDate = overlap.stream()
                    .map(key -> LocalDate.of(key / 100, key % 100, 1))
                    .min(LocalDate::compareTo)
                    .orElse(LocalDate.now());
            
            LocalDate endDate = overlap.stream()
                    .map(key -> YearMonth.of(key / 100, key % 100).atEndOfMonth())
                    .max(LocalDate::compareTo)
                    .orElse(LocalDate.now());

            PurchaseTotals totals = commercialCalculationDao.sumTotals(
                    overlap, context.supplierIds(), context.productIds(), null, context.stateCodes(), 
                    startDate, endDate, context.calculationBasis() == com.medplus.agreement_tracker_backend.enums.CalculationBasis.VENDOR_INVOICE);
            BigDecimal netValue = totals.netValue() != null ? totals.netValue() : BigDecimal.ZERO;

            BigDecimal payout;
            String detail;
            if (valueType == SlabValueType.PERCENTAGE) {
                payout = commercialValue.multiply(netValue).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                detail = commercialValue.stripTrailingZeros().toPlainString() + "% of net value " + netValue.toPlainString();
            } else {
                payout = commercialValue.setScale(2, RoundingMode.HALF_UP);
                detail = "Flat " + commercialValue.stripTrailingZeros().toPlainString() + " per " + frequency.name()
                        + " period";
            }

            lineItems.add(new CommercialPayoutLineItem(
                    segmentType, spec.name(), null, null, netValue, totals.netQty(), payout, true, false, detail,
                    "Payable", null, null, spec.frequency(),
                    overlap));
        }

        return lineItems;
    }

    private int periodKey(YearMonth yearMonth) {
        return yearMonth.getYear() * 100 + yearMonth.getMonthValue();
    }
}
