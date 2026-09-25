package com.medplus.agreement_tracker_backend.service.commercial.jbp;

import com.medplus.agreement_tracker_backend.entity.AgreementJbpCommercialPeriod;
import java.util.List;
import java.util.Map;

public interface PeriodDetectionService {
    Map<String, AgreementJbpCommercialPeriod> indexByCell(List<AgreementJbpCommercialPeriod> rows);
    Map<String, List<AgreementJbpCommercialPeriod>> groupByConfigAndPeriod(List<AgreementJbpCommercialPeriod> rows);
}
