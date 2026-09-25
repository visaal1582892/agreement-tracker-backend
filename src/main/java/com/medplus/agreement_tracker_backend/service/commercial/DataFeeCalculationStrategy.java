package com.medplus.agreement_tracker_backend.service.commercial;

import com.medplus.agreement_tracker_backend.constants.IncomeTypeNames;
import com.medplus.agreement_tracker_backend.dto.response.CommercialPayoutLineItem;
import com.medplus.agreement_tracker_backend.dto.response.CommercialPayoutResponse;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Fee: a flat payout independent of purchase volume. The flat commercial value is paid for each
 * configured frequency interval; only intervals overlapping the requested periods are returned.
 */
@Component
@RequiredArgsConstructor
public class DataFeeCalculationStrategy implements CommercialCalculationStrategy {

    private static final String SEGMENT_TYPE = "DATA_FEE_INTERVAL";

    private final FlatBaselinePayoutCalculator flatBaselinePayoutCalculator;

    @Override
    public String supportedIncomeType() {
        return IncomeTypeNames.DATA_FEE;
    }

    @Override
    public CommercialPayoutResponse calculatePayout(CommercialCalculationContext context) {
        AgreementVersion version = context.version();
        List<String> notes = new ArrayList<>();

        List<CommercialPayoutLineItem> lineItems = flatBaselinePayoutCalculator.calculateIntervals(SEGMENT_TYPE, context);
        if (lineItems.isEmpty()) {
            notes.add("No Data Fee intervals overlap the requested periods, or no flat baseline is configured.");
        }

        BigDecimal totalPayout = lineItems.stream()
                .map(CommercialPayoutLineItem::payout)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CommercialPayoutResponse(
                version.getId(),
                IncomeTypeNames.DATA_FEE,
                version.getCommercialStructure(),
                totalPayout,
                lineItems.size(),
                lineItems,
                notes,
                context.rawData());
    }
}
