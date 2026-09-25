package com.medplus.agreement_tracker_backend.service;

import java.util.List;

public interface RevenueRecognitionCalculationService {

    /**
     * Executes the revenue recognition process for a specific range of months.
     * Deletes existing rollup records for the targeted months and recalculates them.
     */
    void calculateAndStoreRevenue(List<Integer> monthKeys, Long supplierId, Long agreementId);
    
    /**
     * Executes the revenue recognition based on the lookback window setting.
     */
    void runScheduledTask();
}
