package com.medplus.agreement_tracker_backend.service.commercial.jbp;

import com.medplus.agreement_tracker_backend.entity.AgreementJbpCommercialPeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementTimePeriod;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface EarnedPayoutCalculationService {
    
    MutableJbpLineItem evaluateLadder(
            List<AgreementJbpCommercialPeriod> ladder,
            Map<String, AgreementJbpCommercialPeriod> rowByCell,
            Map<String, List<AgreementJbpCommercialPeriod>> ladders,
            BigDecimal achieved,
            long achievedQty,
            List<Integer> overlapKeys
    );

    void applyUpwardContagion(List<MutableJbpLineItem> items);

    List<Integer> periodKeysFor(AgreementTimePeriod timePeriod);
}
