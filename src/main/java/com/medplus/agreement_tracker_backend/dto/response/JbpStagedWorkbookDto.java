package com.medplus.agreement_tracker_backend.dto.response;

import com.medplus.agreement_tracker_backend.enums.JbpValueType;

import java.math.BigDecimal;
import java.util.List;

public record JbpStagedWorkbookDto(
        List<StagedSheet> sheets,
        Boolean hasSummationMismatch,
        String summationWarningMessage
) {

    public record UnpivotedRow(
            String parentPeriodName,
            Long parentPeriodId,
            String subPeriodName,
            Long timePeriodId,
            Integer slabTierNumber,
            Long jbpConfigurationId,
            String slabTierLabel,
            JbpValueType targetType,
            BigDecimal target,
            BigDecimal qualifierPercent,
            JbpValueType payoutType,
            BigDecimal payout,
            BigDecimal maxPurchase,
            BigDecimal maxPayout,
            Boolean firstInParentGroup
    ) {}

    public record StagedSheet(
            String configId,
            String configLabel,
            String sheetName,
            String frequency,
            Boolean master,
            List<UnpivotedRow> rows
    ) {}
}
