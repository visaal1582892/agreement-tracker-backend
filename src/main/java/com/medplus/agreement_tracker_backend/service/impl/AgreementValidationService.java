package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.constants.IncomeTypeNames;
import com.medplus.agreement_tracker_backend.dto.request.AssetPayoutPeriodDto;
import com.medplus.agreement_tracker_backend.dto.request.DraftAssetPayload;
import com.medplus.agreement_tracker_backend.dto.request.DraftCommercialsPayload;
import com.medplus.agreement_tracker_backend.dto.request.DraftDetailsPayload;
import com.medplus.agreement_tracker_backend.dto.request.CommercialDataPayload;
import com.medplus.agreement_tracker_backend.dto.request.ProductRulesPayload;
import com.medplus.agreement_tracker_backend.dto.request.ProductScopeCombinationDto;
import com.medplus.agreement_tracker_backend.dto.request.UpdateDraftRequest;
import com.medplus.agreement_tracker_backend.dto.request.VendorSnapshotPayload;
import com.medplus.agreement_tracker_backend.entity.Agreement;
import com.medplus.agreement_tracker_backend.entity.AgreementAssetPayoutPeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.enums.ApprovalStatus;
import com.medplus.agreement_tracker_backend.enums.AssetCategory;
import com.medplus.agreement_tracker_backend.enums.CommercialStructure;
import com.medplus.agreement_tracker_backend.enums.LeadTimeBasis;
import com.medplus.agreement_tracker_backend.enums.PaymentRealizationType;
import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.exception.ConflictValidationException;
import com.medplus.agreement_tracker_backend.exception.IncompleteAgreementException;
import com.medplus.agreement_tracker_backend.exception.ResourceNotFoundException;
import com.medplus.agreement_tracker_backend.exception.UnauthorizedException;
import com.medplus.agreement_tracker_backend.repository.AgreementAssetPayoutPeriodRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementComputedProductRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementJbpCommercialPeriodRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementJbpConfigurationRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementManufacturerRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementDocumentRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementSlabRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementStoreMappingRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVendorRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVersionRepository;
import com.medplus.agreement_tracker_backend.repository.IncomeTypeRepository;
import com.medplus.agreement_tracker_backend.util.AssetPayoutDurationMath;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgreementValidationService {
    private final AgreementVersionRepository agreementVersionRepository;
    private final AgreementVendorRepository vendorRepository;
    private final AgreementManufacturerRepository manufacturerRuleRepository;
    private final AgreementSlabRepository slabRepository;
    private final AgreementJbpCommercialPeriodRepository jbpCommercialPeriodRepository;
    private final AgreementJbpConfigurationRepository jbpConfigurationRepository;
    private final AgreementComputedProductRepository computedProductRepository;
    private final AgreementDocumentRepository documentRepository;
    private final AgreementAssetPayoutPeriodRepository assetPayoutPeriodRepository;
    private final AgreementStoreMappingRepository storeMappingRepository;
    private final IncomeTypeRepository incomeTypeRepository;
    private final Validator validator;

    private static final String RENEW_WINDOW_MSG = "Agreement cannot be renewed outside the allowed window.";
    private static final int RENEW_WINDOW_DAYS = 90;
    private static final String RENEW_DATES_MSG = "Renewal start date must be after the current expiration date.";
    private static final String COMMERCIAL_DATE_CHANGE_MSG = "A new commercial structure must be provided because the agreement dates have been modified.";

    public void validateStep1Fields(UpdateDraftRequest request) {
        DraftDetailsPayload details = request.details();
        if (details == null || details.incomeTypeId() == null) {
            throw new BusinessException("Income type is required");
        }
        if (details.agreementTypeId() == null) {
            throw new BusinessException("Agreement type is required");
        }
        if (details.startDate() == null) {
            throw new BusinessException("Start date is required");
        }
        if (details.expiryDate() == null) {
            throw new BusinessException("Expiry date is required");
        }
        if (details.expiryDate().isBefore(details.startDate())) {
            throw new BusinessException("Expiry date must be on or after start date");
        }
    }

    public void validateStep2Fields(UpdateDraftRequest request) {
        validateStep1Fields(request);
        DraftDetailsPayload details = request.details();
        if (details == null || details.incomeTypeId() == null) {
            throw new BusinessException("Income type is required");
        }
        Long incomeTypeId = details.incomeTypeId();
        if (isAssetRentalIncomeType(incomeTypeId)) {
            validateAssetRentalStep2(request);
        } else if (isDataFeeIncomeType(incomeTypeId)) {
            validateDataFeeStep2(request);
        } else if (isCommercialContractsIncomeType(incomeTypeId)) {
            validateCommercialContractsStep2(request, details);
        } else if (isAdHocIncomeType(incomeTypeId)) {
            validateAdHocPayload(request, details);
        } else {
            validateProductsAndVendorsStep2(request);
        }
        validateSettlementRouting(details, request.vendorIds(), isAssetRentalIncomeType(incomeTypeId));
        if (!isAssetRentalIncomeType(incomeTypeId)) {
            validateDocumentsPresent(details);
        }
    }

    public void validateDocumentsPresent(DraftDetailsPayload details) {
        if (details == null || details.documents() == null || details.documents().isEmpty()) {
            throw new BusinessException("At least one document is required");
        }
        boolean hasValidDocument = details.documents().stream()
                .anyMatch(document -> document != null
                        && document.fileUrl() != null
                        && !document.fileUrl().isBlank());
        if (!hasValidDocument) {
            throw new BusinessException("At least one document is required");
        }
    }

    public void validateAssetRentalStep2(UpdateDraftRequest request) {
        validateAssetRentalConfiguration(request.asset(), request.details());
    }

    public void validateDataFeeStep2(UpdateDraftRequest request) {
        validateProductsAndVendorsStep2(request);
        DraftDetailsPayload details = request.details();
        requirePartnerLocation(details, "Data Fee");
    }

    public void validateCommercialContractsStep2(UpdateDraftRequest request, DraftDetailsPayload details) {
        validateProductsAndVendorsStep2(request);
        requirePartnerLocation(details, "Commercial Contracts");
    }

    public void validateProductsAndVendorsStep2(UpdateDraftRequest request) {
        if (resolveVendorSnapshots(request.vendors(), request.vendorIds()).isEmpty()) {
            throw new BusinessException("At least one vendor is required");
        }
        ProductRulesPayload rulesPayload = request.productRules();
        List<ProductScopeCombinationDto> combinations = rulesPayload != null && rulesPayload.combinations() != null
                ? rulesPayload.combinations()
                : List.of();
        if (combinations.isEmpty()) {
            throw new BusinessException("At least one manufacturer combination is required");
        }
    }

    public void validateSettlementRouting(DraftDetailsPayload details, List<Long> vendorIds, boolean assetRental) {
        if (details == null) {
            throw new BusinessException("Settlement details are required");
        }
        if (details.paymentRealizationType() == null || details.paymentRealizationType().isBlank()) {
            throw new BusinessException("Payment realization type is required");
        }
        String paymentType = details.paymentRealizationType();
        if (PaymentRealizationType.INVOICE_DISCOUNT.name().equals(paymentType)) {
            return;
        }
        if (PaymentRealizationType.CREDIT_NOTE.name().equals(paymentType)) {
            if (details.payoutBufferDays() == null) {
                throw new BusinessException("Payout lead time is required for Credit Note");
            }
            return;
        }
        if (PaymentRealizationType.DIRECT_PAYMENT_INVOICE.name().equals(paymentType)) {
            if (details.leadTimeBasis() == null || details.leadTimeBasis().isBlank()) {
                throw new BusinessException("Lead time basis is required for Invoice");
            }
            LeadTimeBasis basis = LeadTimeBasis.valueOf(details.leadTimeBasis());
            if (basis == LeadTimeBasis.ACTIVITY_COMPLETION_DATE && details.payoutBufferDays() == null) {
                throw new BusinessException("Payout lead time is required for Activity Completion Date basis");
            }
            if (basis == LeadTimeBasis.INVOICE_DATE) {
                if (details.invoiceGenerationLeadTime() == null) {
                    throw new BusinessException("Invoice generation lead time is required for Invoice date basis");
                }
                if (details.payoutBufferDays() == null) {
                    throw new BusinessException("Payout lead time is required for Invoice date basis");
                }
            }
        }
        if (assetRental) {
            return;
        }
        if (details.calculationBasis() == null || details.calculationBasis().isBlank()) {
            throw new BusinessException("Calculation basis is required");
        }
    }

    public void validateAssetRentalConfiguration(DraftAssetPayload asset, DraftDetailsPayload details) {
        if (asset == null || asset.assetCategory() == null) {
            throw new BusinessException("Asset category is required for Asset Rentals");
        }
        if (asset.assetCategory() != AssetCategory.ACTIVITY
                && (asset.assetType() == null || asset.assetType().isBlank())) {
            throw new BusinessException("Asset type is required for Asset Rentals");
        }
        // Store scope = uploaded storeMappings only; no separate storeCount input.
    }

    public void validateAssetRentalPayout(DraftAssetPayload asset, DraftDetailsPayload details) {
        if (asset == null) {
            throw new BusinessException("Asset payout amount is required for Asset Rentals");
        }
        boolean hasFlatPayout = asset.flatPayout() != null && asset.flatPayout().signum() > 0;
        boolean hasSchedule = asset.assetPayoutPeriods() != null && !asset.assetPayoutPeriods().isEmpty();
        if (!hasFlatPayout && !hasSchedule) {
            throw new BusinessException("Asset payout amount is required for Asset Rentals");
        }
        if (hasFlatPayout && hasSchedule) {
            throw new BusinessException("Choose either flat payout or per-store payout schedule, not both");
        }
        if (hasSchedule) {
            for (AssetPayoutPeriodDto period : asset.assetPayoutPeriods()) {
                if (period.periodMonths() == null || period.periodMonths() <= 0) {
                    throw new BusinessException("Each payout period must have a valid month count");
                }
                if (period.payoutPerStore() == null || period.payoutPerStore().signum() <= 0) {
                    throw new BusinessException("Each payout period must have a payout per store amount");
                }
            }
            LocalDate startDate = details != null ? details.startDate() : null;
            LocalDate expiryDate = details != null ? details.expiryDate() : null;
            assertScheduledMonthsWithinAgreementDuration(
                    sumDtoPeriodMonths(asset.assetPayoutPeriods()),
                    startDate,
                    expiryDate);
        }
    }

    public void validateCommercialStructureFields(Long agreementVersionId, UpdateDraftRequest request) {
        DraftDetailsPayload details = request.details();
        if (details == null || details.incomeTypeId() == null) {
            throw new BusinessException("Income type is required");
        }
        Long incomeTypeId = details.incomeTypeId();
        if (isAssetRentalIncomeType(incomeTypeId)) {
            validateAssetRentalPayout(request.asset(), details);
            return;
        }
        if (isDataFeeIncomeType(incomeTypeId)) {
            validateDataFeeCommercials(agreementVersionId, incomeTypeId, request.commercials());
            return;
        }
        if (isAdHocIncomeType(incomeTypeId)) {
            validateHybridCommercials(agreementVersionId, incomeTypeId, request.commercials());
            validateQpsOneTimeFrequency(details, request.commercials());
            return;
        }
        if (isCommercialContractsIncomeType(incomeTypeId)) {
            validateCommercialContractsCommercials(agreementVersionId, request.commercials());
            return;
        }
        validateHybridCommercials(agreementVersionId, incomeTypeId, request.commercials());
    }

    public void validateCommercialContractsCommercials(
            Long agreementVersionId,
            DraftCommercialsPayload commercials) {
        if (commercials == null) {
            throw new BusinessException("Commercial configuration is required");
        }
        if (resolveEnableFlatBaseline(commercials)) {
            if (commercials.commercialValue() == null) {
                throw new BusinessException("Flat baseline value is required when flat payout is enabled");
            }
            if (commercials.flatBaselineFrequency() == null) {
                throw new BusinessException("Flat baseline frequency is required when flat payout is enabled");
            }
            return;
        }
        if (resolveEnableSlabIncentives(commercials)
                || commercials.commercialStructure() == CommercialStructure.SLAB) {
            validateJbpMatrixPresent(agreementVersionId);
            return;
        }
        throw new BusinessException(
                "Select Flat Baseline Payout or Slab-Based Complex Incentive (JBP)");
    }

    public void validateDataFeeCommercials(
            Long agreementVersionId,
            Long incomeTypeId,
            DraftCommercialsPayload commercials) {
        validateHybridCommercials(agreementVersionId, incomeTypeId, commercials);
    }

    public void validateHybridCommercials(
            Long agreementVersionId,
            Long incomeTypeId,
            DraftCommercialsPayload commercials) {
        if (commercials == null) {
            throw new BusinessException("Commercial configuration is required");
        }
        boolean enableFlat = resolveEnableFlatBaseline(commercials);
        boolean enableSlab = resolveEnableSlabIncentives(commercials);
        if (!enableFlat && !enableSlab) {
            throw new BusinessException("Enable at least one commercial component (flat baseline or slab incentives)");
        }
        if (enableFlat && commercials.commercialValue() == null) {
            throw new BusinessException("Flat baseline value is required when flat payout is enabled");
        }
        if (enableFlat && commercials.commercialValue() != null
                && commercials.flatValueType() == com.medplus.agreement_tracker_backend.enums.SlabValueType.PERCENTAGE
                && commercials.commercialValue().compareTo(new java.math.BigDecimal("100")) > 0) {
            throw new BusinessException("Percentage cannot exceed 100%");
        }
        if (enableFlat && commercials.flatBaselineFrequency() == null) {
            throw new BusinessException("Flat baseline frequency is required when flat payout is enabled");
        }
        if (enableSlab) {
            validateLegacySlabStructureForStep(agreementVersionId, incomeTypeId);
        }
    }

    public void validateJbpMatrixPresent(Long agreementVersionId) {
        boolean hasJbpConfig = jbpConfigurationRepository.existsByAgreementVersionId(agreementVersionId);
        boolean hasJbpPeriods = jbpCommercialPeriodRepository.existsByAgreementVersionId(agreementVersionId);
        if (!hasJbpConfig || !hasJbpPeriods) {
            throw new BusinessException(
                    "JBP Matrix Configuration is missing. Please populate and upload the custom workbook.");
        }
    }

    public void validateLegacySlabStructureForStep(Long agreementVersionId, Long incomeTypeId) {
        if (isCommercialContractsIncomeType(incomeTypeId)) {
            validateJbpMatrixPresent(agreementVersionId);
            return;
        }
        if (slabRepository.findByAgreementVersionIdOrderByMinCapAsc(agreementVersionId).isEmpty()) {
            throw new BusinessException("Please add at least one slab row, or disable Slab-Based Incentives");
        }
    }

    public void validateSlabStructureForSubmit(AgreementVersion version, String agreementName) {
        Long incomeTypeId = version.getIncomeType() != null ? version.getIncomeType().getId() : null;
        if (isCommercialContractsIncomeType(incomeTypeId)) {
            try {
                validateJbpMatrixPresent(version.getId());
            } catch (BusinessException ex) {
                throw validationFailure(agreementName, ex.getMessage());
            }
            return;
        }
        if (slabRepository.findByAgreementVersionIdOrderByMinCapAsc(version.getId()).isEmpty()) {
            throw validationFailure(agreementName, "Missing slabs for slab-based incentives.");
        }
    }

    public void validateQpsOneTimeFrequency(DraftDetailsPayload details, DraftCommercialsPayload commercials) {
        if (details == null || commercials == null) {
            return;
        }
        if (!"QPS".equals(details.adhocSubType())) {
            return;
        }
        if (resolveEnableFlatBaseline(commercials)
                && commercials.flatBaselineFrequency() != PayoutFrequency.ONE_TIME) {
            throw new BusinessException("QPS agreements require One-Time payout frequency");
        }
    }

    public void validateAssetRentalPayload(DraftAssetPayload asset, DraftDetailsPayload details) {
        validateAssetRentalConfiguration(asset, details);
        validateAssetRentalPayout(asset, details);
    }

    public void validateAdHocPayload(UpdateDraftRequest request, DraftDetailsPayload details) {
        String subType = details.adhocSubType();
        if (subType == null || subType.isBlank() || "CONSUMER_PRICE_OFF".equals(subType)) {
            subType = "QPS";
        }
        if (!"QPS".equals(subType)) {
            throw new BusinessException("Ad-Hoc activity sub-type must be QPS");
        }
        ProductRulesPayload rulesPayload = request.productRules();
        List<ProductScopeCombinationDto> combinations = rulesPayload != null && rulesPayload.combinations() != null
                ? rulesPayload.combinations()
                : List.of();
        if (combinations.isEmpty()) {
            throw new BusinessException("At least one manufacturer combination is required for QPS");
        }
    }

    public void validateCompleteAgreement(AgreementVersion version) {
        Agreement parent = version.getAgreement();
        String agreementName = resolveAgreementDisplayName(parent);

        if (parent.getAgreementName() == null || parent.getAgreementName().isBlank()) {
            throw validationFailure(agreementName, "Missing Agreement Name.");
        }
        if (parent.getAgreementGroup() == null) {
            throw validationFailure(agreementName, "Missing Agreement Group.");
        }
        if (version.getStartDate() == null) {
            throw validationFailure(agreementName, "Missing Start Date.");
        }
        if (version.getExpiryDate() == null) {
            throw validationFailure(agreementName, "Missing Expiry Date.");
        }
        if (version.getIncomeType() == null) {
            throw validationFailure(agreementName, "Missing Income Type.");
        }
        if (version.getAgreementType() == null) {
            throw validationFailure(agreementName, "Missing Agreement Type.");
        }
        if (version.getIncomeType() != null
                && isAssetRentalIncomeType(version.getIncomeType().getId())) {
            validateCompleteAssetRental(version, agreementName);
            // Invoice Vendor UI is currently disabled in the wizard — do not block submit
            // on it.
            return;
        }
        if (version.getCommercialStructure() == null) {
            throw validationFailure(agreementName, "Missing Commercial Structure.");
        }
        if (version.getExpiryDate().isBefore(version.getStartDate())) {
            throw validationFailure(agreementName, "Expiry Date must be on or after Start Date.");
        }
        if (version.getCommercialStructure() == CommercialStructure.FLAT) {
            if (version.getCommercialValue() == null) {
                throw validationFailure(agreementName, "Missing Commercial Value for flat baseline.");
            }
            if (version.getFlatBaselineFrequency() == null) {
                throw validationFailure(agreementName, "Missing flat baseline frequency.");
            }
        }
        if (version.getCommercialStructure() == CommercialStructure.SLAB) {
            validateSlabStructureForSubmit(version, agreementName);
        }
        boolean isAssetRental = version.getIncomeType() != null
                && isAssetRentalIncomeType(version.getIncomeType().getId());
        if (!isAssetRental && vendorRepository.findByAgreementVersionId(version.getId()).isEmpty()) {
            throw validationFailure(agreementName, "Missing Vendor.");
        }
        if (!isAssetRental && manufacturerRuleRepository.findByAgreementVersionId(version.getId()).isEmpty()) {
            throw validationFailure(agreementName, "Missing Manufacturer selection.");
        }
        if (!isAssetRental && computedProductRepository.findByAgreementVersionId(version.getId()).isEmpty()) {
            throw validationFailure(agreementName, "Missing Product selection.");
        }
        if (documentRepository.findByAgreementVersionIdAndIsActiveTrue(version.getId()).isEmpty()) {
            throw validationFailure(agreementName, "At least one document is required.");
        }
    }

    public void validateCompleteAssetRental(AgreementVersion version, String agreementName) {
        if (version.getAssetCategory() == null) {
            throw validationFailure(agreementName, "Missing Asset Category.");
        }
        if (version.getAssetCategory() != AssetCategory.ACTIVITY
                && (version.getAssetType() == null || version.getAssetType().isBlank())) {
            throw validationFailure(agreementName, "Missing Asset Type.");
        }
        boolean hasFlatPayout = version.getCommercialStructure() == CommercialStructure.FLAT;
        List<AgreementAssetPayoutPeriod> payoutPeriods = assetPayoutPeriodRepository
                .findByAgreementVersionIdOrderByPeriodMonthsAscIdAsc(version.getId());
        boolean hasSchedule = !payoutPeriods.isEmpty();
        if (!hasFlatPayout && !hasSchedule) {
            throw validationFailure(agreementName, "Missing Asset Payout amount.");
        }

        long mappedStoreCount = storeMappingRepository.countByAgreementVersionId(version.getId());
        if (mappedStoreCount == 0) {
            throw validationFailure(agreementName, "Upload at least one participating store.");
        }
        if (hasSchedule) {
            for (AgreementAssetPayoutPeriod period : payoutPeriods) {
                if (period.getPeriodMonths() == null || period.getPeriodMonths() <= 0) {
                    throw validationFailure(agreementName, "Each payout period must have a valid month count.");
                }
                if (period.getPayoutPerStore() == null || period.getPayoutPerStore().signum() <= 0) {
                    throw validationFailure(agreementName, "Each payout period must have a payout per store amount.");
                }
            }
            try {
                assertScheduledMonthsWithinAgreementDuration(
                        sumEntityPeriodMonths(payoutPeriods),
                        version.getStartDate(),
                        version.getExpiryDate());
            } catch (BusinessException ex) {
                throw validationFailure(agreementName, ex.getMessage());
            }
        }
    }

    public void assertNotTerminated(AgreementVersion version, String message) {
        if (version.getTerminationDate() != null) {
            throw new BusinessException(message);
        }
    }

    public void assertWithinRenewWindow(AgreementVersion version) {
        LocalDate expiryDate = version.getExpiryDate();
        if (expiryDate == null) {
            throw new BusinessException(RENEW_WINDOW_MSG);
        }
        long daysToExpiry = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
        if (daysToExpiry > RENEW_WINDOW_DAYS || daysToExpiry < -RENEW_WINDOW_DAYS) {
            throw new BusinessException("Agreement must be renewed within 90 days before or after its expiry date.");
        }
    }

    public void assertRenewDates(AgreementVersion source, AgreementVersion renewed) {
        LocalDate sourceExpiry = source.getExpiryDate();
        LocalDate startDate = renewed.getStartDate();
        LocalDate expiryDate = renewed.getExpiryDate();
        if (startDate == null || expiryDate == null) {
            throw new BusinessException("Start date and expiry date are required for renewal");
        }
        if (sourceExpiry == null) {
            throw new BusinessException("Source agreement has no expiry date; cannot renew");
        }
        LocalDate minStart = sourceExpiry.plusDays(1);
        if (startDate.isBefore(minStart)) {
            throw new BusinessException(RENEW_DATES_MSG + " (" + minStart + ")");
        }
        if (!expiryDate.isAfter(startDate)) {
            throw new BusinessException("Expiry date must be after start date");
        }
    }

    public void assertRevisionBaseVersionLock(Long agreementId, Integer baseVersionNumber, Integer maxVersion) {
        if (Objects.equals(baseVersionNumber, maxVersion)) {
            return;
        }
        AgreementVersion tip = agreementVersionRepository
                .findByAgreementIdAndVersionNumber(agreementId, maxVersion)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AgreementVersion", "versionNumber", maxVersion));
        if (tip.getApprovalStatus() != ApprovalStatus.REJECTED) {
            throw new ConflictValidationException(
                    "Another user has already updated this agreement.");
        }
        AgreementVersion claimed = agreementVersionRepository
                .findByAgreementIdAndVersionNumber(agreementId, baseVersionNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AgreementVersion", "versionNumber", baseVersionNumber));
        if (claimed.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new ConflictValidationException(
                    "Another user has already updated this agreement.");
        }
        Integer maxApprovedVersion = agreementVersionRepository.findByAgreementId(agreementId).stream()
                .filter(v -> v.getApprovalStatus() == ApprovalStatus.APPROVED)
                .map(AgreementVersion::getVersionNumber)
                .max(Integer::compareTo)
                .orElse(null);
        Agreement parent = tip.getAgreement();
        boolean claimedIsCurrentApproved = parent != null
                && parent.getCurrentVersionId() != null
                && parent.getCurrentVersionId().equals(claimed.getId());
        boolean claimedIsMaxApproved = Objects.equals(baseVersionNumber, maxApprovedVersion);
        if (!claimedIsCurrentApproved && !claimedIsMaxApproved) {
            throw new ConflictValidationException(
                    "Another user has already updated this agreement.");
        }
    }

    public void assertCommercialOverrideWhenDatesChange(
            AgreementVersion source,
            AgreementVersion renewed,
            CommercialDataPayload commercialData,
            boolean renew) {
        boolean datesChanged = !Objects.equals(source.getStartDate(), renewed.getStartDate())
                || !Objects.equals(source.getExpiryDate(), renewed.getExpiryDate());
        if (!renew && !datesChanged) {
            return;
        }
        if (!requiresExcelCommercialOverride(source, renewed)) {
            return;
        }
        if (!hasRequiredCommercialOverride(source, renewed, commercialData)) {
            throw new BusinessException(COMMERCIAL_DATE_CHANGE_MSG);
        }
    }

    public boolean requiresExcelCommercialOverride(AgreementVersion source, AgreementVersion target) {
        Long incomeTypeId = target.getIncomeType() != null
                ? target.getIncomeType().getId()
                : (source.getIncomeType() != null ? source.getIncomeType().getId() : null);
        if (isCommercialContractsIncomeType(incomeTypeId)) {
            CommercialStructure structure = target.getCommercialStructure() != null
                    ? target.getCommercialStructure()
                    : source.getCommercialStructure();
            return structure == CommercialStructure.SLAB;
        }
        return false;
    }

    public boolean hasRequiredCommercialOverride(
            AgreementVersion source,
            AgreementVersion target,
            CommercialDataPayload commercialData) {
        Long incomeTypeId = target.getIncomeType() != null
                ? target.getIncomeType().getId()
                : (source.getIncomeType() != null ? source.getIncomeType().getId() : null);
        if (isAssetRentalIncomeType(incomeTypeId)) {
            // null = deep-copy; non-null (including empty list) = explicit override.
            return commercialData != null && commercialData.storeMappings() != null;
        }
        if (isCommercialContractsIncomeType(incomeTypeId)) {
            boolean hasJbp = commercialData != null
                    && commercialData.jbp() != null
                    && commercialData.jbp().sheets() != null
                    && !commercialData.jbp().sheets().isEmpty();
            boolean hasBlueprint = commercialData != null
                    && commercialData.jbpBlueprint() != null
                    && commercialData.jbpBlueprint().configurations() != null
                    && !commercialData.jbpBlueprint().configurations().isEmpty();
            boolean hasSlabs = commercialData != null
                    && commercialData.slabs() != null
                    && !commercialData.slabs().isEmpty();
            return (hasJbp && hasBlueprint) || hasSlabs;
        }
        return true;
    }

    public void validateAgreementOwnership(Agreement agreement, Long userId) {
        if (!agreement.getOwner().getId().equals(userId)) {
            throw new UnauthorizedException("You are not the owner of this agreement");
        }
    }

    private IncompleteAgreementException validationFailure(String agreementName, String reason) {
        return new IncompleteAgreementException(
                "Validation failed for '" + agreementName + "': " + reason);
    }

    private boolean isAssetRentalIncomeType(Long incomeTypeId) {
        if (incomeTypeId == null) {
            return false;
        }
        return incomeTypeRepository.findById(incomeTypeId)
                .map(incomeType -> IncomeTypeNames.ASSET_RENTALS.equalsIgnoreCase(incomeType.getName()))
                .orElse(false);
    }

    private boolean isCommercialContractsIncomeType(Long incomeTypeId) {
        if (incomeTypeId == null) {
            return false;
        }
        return incomeTypeRepository.findById(incomeTypeId)
                .map(incomeType -> IncomeTypeNames.COMMERCIAL_CONTRACTS.equalsIgnoreCase(incomeType.getName()))
                .orElse(false);
    }

    private int sumEntityPeriodMonths(List<AgreementAssetPayoutPeriod> periods) {
        int sum = 0;
        if (periods == null) {
            return 0;
        }
        for (AgreementAssetPayoutPeriod period : periods) {
            if (period.getPeriodMonths() != null && period.getPeriodMonths() > 0) {
                sum += period.getPeriodMonths();
            }
        }
        return sum;
    }

    private boolean isDataFeeIncomeType(Long incomeTypeId) {
        if (incomeTypeId == null) {
            return false;
        }
        return incomeTypeRepository.findById(incomeTypeId)
                .map(incomeType -> IncomeTypeNames.DATA_FEE.equalsIgnoreCase(incomeType.getName()))
                .orElse(false);
    }

    private boolean isAdHocIncomeType(Long incomeTypeId) {
        if (incomeTypeId == null) {
            return false;
        }
        return incomeTypeRepository.findById(incomeTypeId)
                .map(incomeType -> IncomeTypeNames.AD_HOC_ACTIVITIES.equalsIgnoreCase(incomeType.getName()))
                .orElse(false);
    }

    private void requirePartnerLocation(DraftDetailsPayload details, String incomeLabel) {
        if (details == null) {
            throw new BusinessException("Geography is required for " + incomeLabel);
        }
        List<com.medplus.agreement_tracker_backend.dto.common.AgreementLocationDto> locs = details.locations() != null
                ? details.locations()
                : List.of();
        if (locs.isEmpty()) {
            throw new BusinessException("Select at least one location for " + incomeLabel);
        }
    }

    private List<VendorSnapshotPayload> resolveVendorSnapshots(List<VendorSnapshotPayload> vendors,
            List<Long> vendorIds) {
        if (vendors != null && !vendors.isEmpty()) {
            return vendors;
        }
        return List.of();
    }

    private int sumDtoPeriodMonths(List<AssetPayoutPeriodDto> periods) {
        int sum = 0;
        if (periods == null) {
            return 0;
        }
        for (AssetPayoutPeriodDto period : periods) {
            if (period.periodMonths() != null && period.periodMonths() > 0) {
                sum += period.periodMonths();
            }
        }
        return sum;
    }

    private boolean resolveEnableFlatBaseline(DraftCommercialsPayload commercials) {
        if (commercials.enableFlatBaseline() != null) {
            return Boolean.TRUE.equals(commercials.enableFlatBaseline());
        }
        CommercialStructure structure = commercials.commercialStructure();
        return structure == CommercialStructure.FLAT;
    }

    private boolean resolveEnableSlabIncentives(DraftCommercialsPayload commercials) {
        if (commercials.enableSlabIncentives() != null) {
            return Boolean.TRUE.equals(commercials.enableSlabIncentives());
        }
        return commercials.commercialStructure() == CommercialStructure.SLAB;
    }

    private String resolveAgreementDisplayName(Agreement parent) {
        if (parent.getAgreementName() != null && !parent.getAgreementName().isBlank()) {
            return parent.getAgreementName();
        }
        return "Agreement #" + parent.getId();
    }

    private void assertScheduledMonthsWithinAgreementDuration(
            int scheduledMonths,
            LocalDate startDate,
            LocalDate expiryDate) {
        if (scheduledMonths <= 0) {
            return;
        }
        Integer maxAllowedMonths = AssetPayoutDurationMath.maxAllowedMonths(startDate, expiryDate);
        if (maxAllowedMonths == null) {
            return;
        }
        if (scheduledMonths > maxAllowedMonths) {
            throw new BusinessException(
                    "The total scheduled payout months (" + scheduledMonths
                            + ") exceeds the agreement's maximum duration of "
                            + maxAllowedMonths + " months.");
        }
    }

}
