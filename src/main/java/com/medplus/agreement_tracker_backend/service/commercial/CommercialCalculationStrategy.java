package com.medplus.agreement_tracker_backend.service.commercial;

import com.medplus.agreement_tracker_backend.dto.response.CommercialPayoutResponse;

public interface CommercialCalculationStrategy {

    /**
     * @return the exact income type name (see {@code IncomeTypeNames}) this strategy handles.
     */
    String supportedIncomeType();

    CommercialPayoutResponse calculatePayout(CommercialCalculationContext context);
}
