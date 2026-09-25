package com.medplus.agreement_tracker_backend.dto.request;

import com.medplus.agreement_tracker_backend.enums.AssetCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;
import java.util.List;

public record DraftAssetPayload(
                AssetCategory assetCategory,
                String assetType,
                Integer storeCount,
                @DecimalMax(value = "999999999999.99", message = "Payout value exceeds limit") @Digits(integer = 12, fraction = 2, message = "Invalid payout format") BigDecimal payoutPerStore,
                @DecimalMax(value = "999999999999.99", message = "Payout value exceeds limit") @Digits(integer = 12, fraction = 2, message = "Invalid payout format") BigDecimal flatPayout,
                String remarks,
                List<AssetPayoutPeriodDto> assetPayoutPeriods,
                List<AgreementStoreDto> stores) {
}
