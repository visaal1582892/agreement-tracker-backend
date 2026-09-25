package com.medplus.agreement_tracker_backend.service.commercial.jbp;

import com.medplus.agreement_tracker_backend.dto.response.RevenueRecognitionMonthDto;
import com.medplus.agreement_tracker_backend.entity.AgreementTimePeriod;
import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;

import java.util.List;

public interface PaymentAllocationService {
    void allocatePayments(List<RevenueRecognitionMonthDto> months, PayoutFrequency paymentFrequency, List<AgreementTimePeriod> allPeriodsForFrequency);
}
