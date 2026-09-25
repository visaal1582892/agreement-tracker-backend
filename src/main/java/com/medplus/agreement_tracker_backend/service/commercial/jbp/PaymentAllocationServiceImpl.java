package com.medplus.agreement_tracker_backend.service.commercial.jbp;

import com.medplus.agreement_tracker_backend.dto.response.RevenueRecognitionMonthDto;
import com.medplus.agreement_tracker_backend.entity.AgreementTimePeriod;
import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PaymentAllocationServiceImpl implements PaymentAllocationService {

    @Override
    public void allocatePayments(List<RevenueRecognitionMonthDto> months, PayoutFrequency paymentFrequency, List<AgreementTimePeriod> allPeriodsForFrequency) {
        if (months == null || months.isEmpty()) {
            return;
        }

        BigDecimal accumulatedEarned = BigDecimal.ZERO;
        String paymentIntervalName = paymentFrequency != null ? paymentFrequency.name() : "MONTHLY";

        for (int i = 0; i < months.size(); i++) {
            RevenueRecognitionMonthDto dto = months.get(i);
            
            if (dto.getEarnedPayout() != null) {
                accumulatedEarned = accumulatedEarned.add(dto.getEarnedPayout());
            }

            dto.setPaymentInterval(paymentIntervalName);

            boolean isLastMonthOfAgreement = (i == months.size() - 1);
            boolean isLastMonthOfPaymentInterval = false;

            if (paymentFrequency != null && allPeriodsForFrequency != null) {
                YearMonth currentMonth = YearMonth.parse(dto.getCalendarMonth(), DateTimeFormatter.ofPattern("yyyy-MM"));
                
                // Check if current month is the last included month of ANY period of this frequency
                for (AgreementTimePeriod period : allPeriodsForFrequency) {
                    YearMonth lastIncluded = period.latestIncludedMonth();
                    if (lastIncluded != null && lastIncluded.equals(currentMonth)) {
                        isLastMonthOfPaymentInterval = true;
                        break;
                    }
                }
            } else {
                isLastMonthOfPaymentInterval = true;
            }

            if (isLastMonthOfAgreement || isLastMonthOfPaymentInterval) {
                dto.setFinalPayableAmount(accumulatedEarned);
                accumulatedEarned = BigDecimal.ZERO;
            } else {
                dto.setFinalPayableAmount(BigDecimal.ZERO);
            }
        }
    }
}
