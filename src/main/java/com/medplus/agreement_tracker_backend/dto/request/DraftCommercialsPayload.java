package com.medplus.agreement_tracker_backend.dto.request;

import com.medplus.agreement_tracker_backend.enums.CommercialStructure;
import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
import com.medplus.agreement_tracker_backend.enums.SlabValueType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

public record DraftCommercialsPayload(
                CommercialStructure commercialStructure,
                @DecimalMax(value = "999999999999.99", message = "Payout value exceeds limit") @Digits(integer = 12, fraction = 2, message = "Invalid payout format") BigDecimal commercialValue,
                SlabValueType flatValueType,
                PayoutFrequency flatBaselineFrequency,
                Boolean enableFlatBaseline,
                Boolean enableSlabIncentives,
                Integer financialYearStartMonth) {
}
