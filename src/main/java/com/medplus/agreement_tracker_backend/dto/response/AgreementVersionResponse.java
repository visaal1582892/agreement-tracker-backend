package com.medplus.agreement_tracker_backend.dto.response;

import com.medplus.agreement_tracker_backend.dto.common.AgreementLocationDto;
import com.medplus.agreement_tracker_backend.enums.ApprovalStatus;
import com.medplus.agreement_tracker_backend.enums.AgreementStatus;
import com.medplus.agreement_tracker_backend.enums.ProductScopeComputeStatus;
import com.medplus.agreement_tracker_backend.enums.CalculationBasis;
import com.medplus.agreement_tracker_backend.enums.CommercialStructure;
import com.medplus.agreement_tracker_backend.enums.LeadTimeBasis;
import com.medplus.agreement_tracker_backend.enums.PaymentRealizationType;
import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
import com.medplus.agreement_tracker_backend.enums.SlabValueType;

import com.medplus.agreement_tracker_backend.enums.RevisionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;

@Builder
public record AgreementVersionResponse(
        Long id,
        Long agreementId,
        String agreementName,
        Integer versionNumber,
        RevisionType revisionType,
        Long agreementGroupId,
        String agreementGroupName,
        Long ownerId,
        String ownerName,
        Long incomeTypeId,
        String incomeTypeName,
        Long agreementTypeId,
        String agreementTypeName,
        CommercialStructure commercialStructure,
        BigDecimal commercialValue,
        String assetCategory,
        String assetType,
        SlabValueType flatValueType,
        PayoutFrequency flatBaselineFrequency,
        BigDecimal quantityCap,
        String adhocSubType,
        Long invoiceVendorId,
        String invoiceVendorNameSnapshot,
        Integer payoutBufferDays,
        LeadTimeBasis leadTimeBasis,
        Integer invoiceGenerationLeadTime,
        CalculationBasis calculationBasis,
        PaymentRealizationType paymentRealizationType,
        LocalDate startDate,
        LocalDate expiryDate,
        Integer financialYearStartMonth,
        ApprovalStatus approvalStatus,
        AgreementStatus computedStatus,
        AgreementStatus terminalStatus,
        boolean inProgressFlag,
        LocalDate terminationDate,
        String terminationReason,
        String notes,
        List<AgreementLocationDto> locations,
        List<VendorSummary> vendors,
        List<ManufacturerSummary> manufacturers,
        List<DivisionRuleSummary> divisionRules,
        List<ProductRuleSummary> productRules,
        List<ProductSummary> products,
        ProductScopeComputeStatus productScopeComputeStatus,
        AssetSummary asset,
        List<AssetPayoutPeriodSummary> assetPayoutPeriods,
        List<DocumentSummary> documents,
        boolean jbpCommitted,
        PendingActionRequestInfo pendingActionRequest,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record VendorSummary(Long vendorId, String vendorName, String state) {}
    public record ManufacturerSummary(Long id, String name) {}
    public record DivisionRuleSummary(Long id, String ruleType, String name, Long manufacturerId) {}
    public record ProductRuleSummary(String id, String ruleType, String name, Long manufacturerId) {}
    public record ProductSummary(
            String productId,
            String productName,
            String manufacturerName,
            String divisionName,
            String manufacturerId,
            String divisionId
    ) {
        public ProductSummary(String productId, String productName, String manufacturerName, String divisionName) {
            this(productId, productName, manufacturerName, divisionName, null, null);
        }
    }
    public record AssetSummary(
            String assetCategory,
            String assetType,
            Integer storeCount,
            BigDecimal flatPayout
    ) {}
    public record AssetPayoutPeriodSummary(
            Long id,
            Integer periodMonths,
            BigDecimal payoutPerStore
    ) {}
    public record DocumentSummary(
            Long id,
            String fileUrl,
            String originalFileName,
            String thumbnailUrl,
            String documentType
    ) {}
}
