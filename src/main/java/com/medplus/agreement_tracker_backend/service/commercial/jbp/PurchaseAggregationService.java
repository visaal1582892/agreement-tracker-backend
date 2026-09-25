package com.medplus.agreement_tracker_backend.service.commercial.jbp;

import com.medplus.agreement_tracker_backend.repository.CommercialCalculationDao;
import com.medplus.agreement_tracker_backend.service.commercial.CommercialCalculationContext;
import java.util.List;

public interface PurchaseAggregationService {
    CommercialCalculationDao.PurchaseTotals sumTotals(List<Integer> overlap, CommercialCalculationContext context);
}
