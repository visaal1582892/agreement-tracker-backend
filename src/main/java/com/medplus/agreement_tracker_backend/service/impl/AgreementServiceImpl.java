package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.dto.request.CreateAgreementRequest;
import com.medplus.agreement_tracker_backend.dto.request.DraftAgreementItemRequest;
import com.medplus.agreement_tracker_backend.dto.request.AssetPayoutPeriodDto;
import com.medplus.agreement_tracker_backend.dto.request.DraftCommercialsPayload;
import com.medplus.agreement_tracker_backend.dto.request.DraftDetailsPayload;
import com.medplus.agreement_tracker_backend.dto.request.AgreementRevisionSubmitRequest;
import com.medplus.agreement_tracker_backend.dto.request.CommercialDataPayload;
import com.medplus.agreement_tracker_backend.dto.request.EditAgreementRequest;
import com.medplus.agreement_tracker_backend.dto.request.JbpConfigurationBlockDto;
import com.medplus.agreement_tracker_backend.dto.request.JbpWorkbookRequest;
import com.medplus.agreement_tracker_backend.dto.request.ProductRulesPayload;
import com.medplus.agreement_tracker_backend.dto.request.ProductScopeCombinationDto;
import com.medplus.agreement_tracker_backend.dto.request.TerminateAgreementRequest;
import com.medplus.agreement_tracker_backend.dto.request.UpdateDraftRequest;
import com.medplus.agreement_tracker_backend.dto.request.VendorSnapshotPayload;
import com.medplus.agreement_tracker_backend.dto.response.AgreementResponse;
import com.medplus.agreement_tracker_backend.dto.response.AgreementVersionResponse;
import com.medplus.agreement_tracker_backend.dto.response.AgreementVersionSummaryResponse;
import com.medplus.agreement_tracker_backend.dto.response.ApprovalTimelineResponse;
import com.medplus.agreement_tracker_backend.dto.response.BulkAgreementCreateResponse;
import com.medplus.agreement_tracker_backend.dto.response.BulkGroupSubmitResponse;
import com.medplus.agreement_tracker_backend.dto.response.JbpStagedWorkbookDto;
import com.medplus.agreement_tracker_backend.dto.response.AgreementGroupResponse;
import com.medplus.agreement_tracker_backend.entity.Agreement;
import com.medplus.agreement_tracker_backend.entity.AgreementApproval;
import com.medplus.agreement_tracker_backend.entity.AgreementAssetPayoutPeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementAudit;
import com.medplus.agreement_tracker_backend.entity.AgreementJbpCommercialPeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementJbpConfiguration;
import com.medplus.agreement_tracker_backend.entity.AgreementTimePeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementVendor;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.entity.AgreementGroup;
import com.medplus.agreement_tracker_backend.entity.User;
import com.medplus.agreement_tracker_backend.enums.ApprovalAction;
import com.medplus.agreement_tracker_backend.enums.ApprovalStatus;
import com.medplus.agreement_tracker_backend.enums.RevisionType;
import com.medplus.agreement_tracker_backend.enums.CommercialStructure;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.exception.IncompleteAgreementException;
import com.medplus.agreement_tracker_backend.exception.ResourceNotFoundException;
import com.medplus.agreement_tracker_backend.exception.UnauthorizedException;
import com.medplus.agreement_tracker_backend.repository.AgreementActionRequestRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementApprovalRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementAssetPayoutPeriodRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementAuditRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementComputedProductRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementDivisionRuleRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementJbpCommercialPeriodRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementJbpConfigurationRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementManufacturerRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementProductRuleRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementLocationRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementDocumentRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementReminderRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementSlabRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementStoreMappingRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementTimePeriodRepository;
import com.medplus.agreement_tracker_backend.integration.ProductMasterIntegrationService;
import com.medplus.agreement_tracker_backend.repository.AgreementRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementSpec;
import com.medplus.agreement_tracker_backend.repository.AgreementTypeRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVendorRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVersionRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementGroupRepository;
import com.medplus.agreement_tracker_backend.repository.IncomeTypeRepository;
import com.medplus.agreement_tracker_backend.repository.UserRepository;
import com.medplus.agreement_tracker_backend.service.AgreementService;
import com.medplus.agreement_tracker_backend.service.AgreementGroupService;
import com.medplus.agreement_tracker_backend.service.StoreMappingService;
import com.medplus.agreement_tracker_backend.util.AgreementStatusResolver;
import com.medplus.agreement_tracker_backend.util.AssetPayoutDurationMath;
import com.medplus.agreement_tracker_backend.validation.Step1Validation;
import com.medplus.agreement_tracker_backend.validation.Step2Validation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AgreementServiceImpl implements AgreementService {

    private static final Logger log = LoggerFactory.getLogger(AgreementServiceImpl.class);

    private final AgreementRepository agreementRepository;
    private final AgreementVersionRepository agreementVersionRepository;
    private final AgreementGroupRepository agreementGroupRepository;
    private final AgreementGroupService agreementGroupService;
    private final AgreementVendorRepository vendorRepository;
    private final AgreementManufacturerRepository manufacturerRuleRepository;
    private final AgreementDivisionRuleRepository divisionRuleRepository;
    private final AgreementProductRuleRepository productRuleRepository;
    private final AgreementSlabRepository slabRepository;
    private final AgreementJbpCommercialPeriodRepository jbpCommercialPeriodRepository;
    private final AgreementJbpConfigurationRepository jbpConfigurationRepository;
    private final AgreementComputedProductRepository computedProductRepository;
    private final AgreementApprovalRepository approvalRepository;
    private final AgreementAuditRepository auditRepository;
    private final AgreementActionRequestRepository actionRequestRepository;
    private final AgreementReminderRepository reminderRepository;
    private final AgreementDocumentRepository documentRepository;
    private final AgreementAssetPayoutPeriodRepository assetPayoutPeriodRepository;
    private final AgreementStoreMappingRepository storeMappingRepository;
    private final StoreMappingService storeMappingService;
    private final AgreementTimePeriodRepository timePeriodRepository;
    private final AgreementLocationRepository agreementLocationRepository;
    private final UserRepository userRepository;

    private final AgreementStatusResolver statusResolver;
    private final TransactionTemplate groupSubmitTransactionTemplate;
    private final Validator validator;
    private final AgreementCloneService agreementCloneService;
    private final AgreementValidationService agreementValidationService;
    private final AgreementMapperService agreementMapperService;
    private final AgreementDraftMutationService agreementDraftMutationService;
    private static final String DRAFT_AGREEMENT_NAME_PLACEHOLDER = "Draft - Pending Details";
    private static final String TERMINATED_RENEW_MSG = "Terminated agreements cannot be renewed. Please create a new agreement instead.";
    private static final String TERMINATED_EDIT_MSG = "Terminated agreements cannot be edited. Please create a new agreement instead.";
    private static final String RENEW_IDENTITY_INCOME_MSG = "Income type cannot be changed during renewal";
    private static final String RENEW_IDENTITY_TYPE_MSG = "Agreement type cannot be changed during renewal";
    private static final String RENEW_IDENTITY_VENDOR_MSG = "Vendors cannot be changed during renewal";

    public AgreementServiceImpl(
            AgreementRepository agreementRepository,
            AgreementVersionRepository agreementVersionRepository,
            AgreementGroupRepository agreementGroupRepository,
            AgreementGroupService agreementGroupService,
            AgreementVendorRepository vendorRepository,
            AgreementManufacturerRepository manufacturerRuleRepository,
            AgreementDivisionRuleRepository divisionRuleRepository,
            AgreementProductRuleRepository productRuleRepository,
            AgreementSlabRepository slabRepository,
            AgreementJbpCommercialPeriodRepository jbpCommercialPeriodRepository,
            AgreementJbpConfigurationRepository jbpConfigurationRepository,
            AgreementComputedProductRepository computedProductRepository,
            AgreementApprovalRepository approvalRepository,
            AgreementAuditRepository auditRepository,
            AgreementActionRequestRepository actionRequestRepository,
            AgreementReminderRepository reminderRepository,
            AgreementDocumentRepository documentRepository,
            AgreementAssetPayoutPeriodRepository assetPayoutPeriodRepository,
            AgreementStoreMappingRepository storeMappingRepository,
            StoreMappingService storeMappingService,
            AgreementTimePeriodRepository timePeriodRepository,
            AgreementLocationRepository agreementLocationRepository,
            UserRepository userRepository,
            IncomeTypeRepository incomeTypeRepository,
            AgreementTypeRepository agreementTypeRepository,
            ProductMasterIntegrationService productMasterIntegrationService,
            AgreementCloneService agreementCloneService,
            AgreementMapperService agreementMapperService,
            AgreementDraftMutationService agreementDraftMutationService,
            AgreementValidationService agreementValidationService,
            AgreementProductScopeComputeService agreementProductScopeComputeService,
            AgreementStatusResolver statusResolver, PlatformTransactionManager transactionManager,
            Validator validator) {
        this.agreementRepository = agreementRepository;
        this.agreementVersionRepository = agreementVersionRepository;
        this.agreementGroupRepository = agreementGroupRepository;
        this.agreementGroupService = agreementGroupService;
        this.vendorRepository = vendorRepository;
        this.manufacturerRuleRepository = manufacturerRuleRepository;
        this.divisionRuleRepository = divisionRuleRepository;
        this.productRuleRepository = productRuleRepository;
        this.slabRepository = slabRepository;
        this.jbpCommercialPeriodRepository = jbpCommercialPeriodRepository;
        this.jbpConfigurationRepository = jbpConfigurationRepository;
        this.computedProductRepository = computedProductRepository;
        this.approvalRepository = approvalRepository;
        this.auditRepository = auditRepository;
        this.actionRequestRepository = actionRequestRepository;
        this.reminderRepository = reminderRepository;
        this.documentRepository = documentRepository;
        this.assetPayoutPeriodRepository = assetPayoutPeriodRepository;
        this.storeMappingRepository = storeMappingRepository;
        this.storeMappingService = storeMappingService;
        this.timePeriodRepository = timePeriodRepository;
        this.agreementLocationRepository = agreementLocationRepository;
        this.userRepository = userRepository;
        this.agreementCloneService = agreementCloneService;
        this.agreementMapperService = agreementMapperService;
        this.agreementDraftMutationService = agreementDraftMutationService;
        this.agreementValidationService = agreementValidationService;
        this.statusResolver = statusResolver;
        this.groupSubmitTransactionTemplate = new TransactionTemplate(transactionManager);
        this.validator = validator;
    }

    @Override
    @Transactional
    public BulkAgreementCreateResponse createDraft(CreateAgreementRequest request, Long currentUserId) {
        User owner = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUserId));

        AgreementGroup group = resolveAgreementGroup(
                request.agreementGroupId(),
                request.newAgreementGroupName(),
                currentUserId);

        if (!group.isActive()) {
            throw new BusinessException("Cannot add agreements to an inactive group.");
        }

        List<Long> vendorIds = request.vendorIds() != null ? request.vendorIds() : List.of();
        List<VendorSnapshotPayload> vendors = request.vendors() != null ? request.vendors() : List.of();
        ProductRulesPayload rulesPayload = request.productRules() != null
                ? request.productRules()
                : new ProductRulesPayload(List.of());
        List<ProductScopeCombinationDto> combinations = rulesPayload.combinations() != null
                ? rulesPayload.combinations()
                : List.of();

        List<DraftAgreementItemRequest> items = request.agreements() != null && !request.agreements().isEmpty()
                ? request.agreements()
                : List.of(new DraftAgreementItemRequest(null, null));

        List<AgreementVersionResponse> created = new ArrayList<>();
        Long primaryAgreementId = null;

        for (DraftAgreementItemRequest item : items) {
            Agreement parent = Agreement.builder()
                    .agreementGroup(group)
                    .agreementName(DRAFT_AGREEMENT_NAME_PLACEHOLDER)
                    .owner(owner)
                    .isActive(true)
                    .build();
            parent.setCreatedByUserId(currentUserId);
            parent = agreementRepository.save(parent);

            AgreementVersion version = agreementDraftMutationService.buildDraftVersion(item, owner, parent, 1,
                    currentUserId);
            version = agreementVersionRepository.save(version);
            DraftDetailsPayload itemDetails = item != null ? item.details() : null;
            if (itemDetails != null) {
                agreementDraftMutationService.applyPartnerLocation(version, itemDetails, currentUserId);
            }
            agreementDraftMutationService.syncAgreementName(parent, version, currentUserId);

            agreementDraftMutationService.replaceVendors(version, vendors, vendorIds, currentUserId);
            agreementDraftMutationService.replaceRulesAndComputeProducts(version, combinations, currentUserId);

            recordAudit(parent.getId(), version.getId(), "AGREEMENT_CREATED", null, null, currentUserId);

            if (primaryAgreementId == null) {
                primaryAgreementId = parent.getId();
            }
            created.add(agreementMapperService.toVersionResponse(version));
        }

        return new BulkAgreementCreateResponse(created, primaryAgreementId);
    }

    private void applyImmediateSupersessionIfApplicable(AgreementVersion source) {
        com.medplus.agreement_tracker_backend.enums.AgreementStatus currentStatus = statusResolver.resolve(source);
        if (currentStatus == com.medplus.agreement_tracker_backend.enums.AgreementStatus.REJECTED ||
                currentStatus == com.medplus.agreement_tracker_backend.enums.AgreementStatus.EXPIRED ||
                currentStatus == com.medplus.agreement_tracker_backend.enums.AgreementStatus.DRAFT) {
            source.setSupersededFromStatus(source.getApprovalStatus());
            source.setApprovalStatus(ApprovalStatus.SUPERSEDED);
            agreementVersionRepository.save(source);
        }
    }

    @Override
    @Transactional
    public AgreementVersionResponse createNewVersion(Long agreementId, Long currentUserId) {
        Agreement parent = agreementRepository.findById(agreementId)
                .orElseThrow(() -> new ResourceNotFoundException("Agreement", agreementId));

        AgreementVersion source = resolveNewVersionSource(parent);
        agreementValidationService.assertNotTerminated(source, TERMINATED_EDIT_MSG);
        loadAndValidateOwnership(source.getId(), currentUserId);

        Integer maxVersion = agreementVersionRepository.findMaxVersionByAgreementId(agreementId);
        AgreementVersion latest = agreementVersionRepository.findByAgreementIdAndVersionNumber(agreementId, maxVersion)
                .orElseThrow(() -> new BusinessException("No agreement version exists for this agreement"));
        if (latest.getApprovalStatus() == ApprovalStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                    "A pending revision exists — it must be approved or rejected before creating a new version");
        }
        if (latest.getApprovalStatus() == ApprovalStatus.DRAFT && parent.getOwner().getId().equals(currentUserId)) {
            throw new BusinessException("A draft version already exists — update it in place");
        }

        applyImmediateSupersessionIfApplicable(source);

        User owner = parent.getOwner();

        AgreementVersion newVersion = AgreementVersion.builder()
                .agreement(parent)
                .versionNumber(maxVersion + 1)
                .owner(owner)
                .incomeType(source.getIncomeType())
                .agreementType(source.getAgreementType())
                .commercialStructure(source.getCommercialStructure())
                .commercialValue(source.getCommercialValue())
                .startDate(source.getStartDate())
                .expiryDate(source.getExpiryDate())
                .financialYearStartMonth(source.getFinancialYearStartMonth())
                .approvalStatus(ApprovalStatus.DRAFT)
                .notes(source.getNotes())
                .build();
        newVersion.setCreatedByUserId(currentUserId);
        newVersion = agreementVersionRepository.save(newVersion);

        agreementCloneService.copyVendors(source.getId(), newVersion, currentUserId);
        agreementCloneService.copyRulesAndComputed(source.getId(), newVersion, currentUserId);

        recordAudit(parent.getId(), newVersion.getId(), "NEW_VERSION_CREATED",
                String.valueOf(source.getVersionNumber()), String.valueOf(newVersion.getVersionNumber()),
                currentUserId);

        return agreementMapperService.toVersionResponse(newVersion);
    }

    @Override
    @Transactional
    public AgreementVersionResponse createVersionedEdit(Long sourceAgreementVersionId, EditAgreementRequest request,
            Long currentUserId) {
        AgreementVersion source = agreementVersionRepository.findById(sourceAgreementVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", sourceAgreementVersionId));

        agreementValidationService.assertNotTerminated(source, TERMINATED_EDIT_MSG);

        if (source.getApprovalStatus() != ApprovalStatus.APPROVED
                && source.getApprovalStatus() != ApprovalStatus.REJECTED) {
            throw new BusinessException("Can only create a versioned edit from APPROVED or REJECTED agreements");
        }

        loadAndValidateOwnership(sourceAgreementVersionId, currentUserId);

        Agreement parent = source.getAgreement();
        Integer maxVersion = agreementVersionRepository.findMaxVersionByAgreementId(parent.getId());
        AgreementVersion latest = agreementVersionRepository
                .findByAgreementIdAndVersionNumber(parent.getId(), maxVersion)
                .orElseThrow(() -> new BusinessException("No agreement version exists for this agreement"));

        if (latest.getApprovalStatus() == ApprovalStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                    "A pending revision exists — it must be approved or rejected before creating a new version");
        }
        if (latest.getApprovalStatus() == ApprovalStatus.DRAFT
                && parent.getOwner().getId().equals(currentUserId)) {
            throw new BusinessException("A draft version already exists — update it in place");
        }

        applyImmediateSupersessionIfApplicable(source);

        User owner = parent.getOwner();

        DraftAgreementItemRequest item = new DraftAgreementItemRequest(request.details(), request.commercials());
        AgreementVersion newVersion = agreementDraftMutationService.buildDraftVersion(item, owner, parent,
                maxVersion + 1, currentUserId);
        newVersion.setRevisionType(RevisionType.EDIT);
        newVersion.setBaseVersionId(source.getId());
        newVersion.setIncomeType(source.getIncomeType());
        newVersion = agreementVersionRepository.save(newVersion);
        agreementDraftMutationService.applyDraftFields(newVersion, request.details(), request.commercials());

        // Enforce date locking for edits
        newVersion.setStartDate(source.getStartDate());
        newVersion.setExpiryDate(source.getExpiryDate());

        List<Long> vendorIds = request.vendorIds() != null ? request.vendorIds() : List.of();
        List<VendorSnapshotPayload> vendors = request.vendors() != null ? request.vendors() : List.of();
        agreementDraftMutationService.replaceVendors(newVersion, vendors, vendorIds, currentUserId);

        Long incomeTypeId = resolveIncomeTypeId(newVersion, request.details());
        boolean hasProductRulesInRequest = request.productRules() != null &&
                request.productRules().combinations() != null &&
                !request.productRules().combinations().isEmpty();

        agreementDraftMutationService.syncIncomeTypeSpecificData(
                newVersion,
                incomeTypeId,
                currentUserId,
                request.productRules(),
                request.asset(),
                request.details(),
                true);

        if (!agreementDraftMutationService.isAssetRentalIncomeType(incomeTypeId) && !hasProductRulesInRequest) {
            agreementCloneService.copyRulesAndComputed(source.getId(), newVersion, currentUserId);
        }

        if (request.details() != null) {
            agreementDraftMutationService.applyPartnerLocation(newVersion, request.details(), currentUserId);
        }
        agreementDraftMutationService.syncAgreementName(parent, newVersion, currentUserId);

        newVersion.setApprovedBy(null);
        newVersion.setApprovalDate(null);
        newVersion.setUpdatedByUserId(currentUserId);
        newVersion = agreementVersionRepository.save(newVersion);

        storeMappingService.copyMappings(source.getId(), newVersion.getId());
        agreementCloneService.copyAssetPayoutPeriods(source.getId(), newVersion.getId());
        agreementCloneService.copySlabs(source.getId(), newVersion, currentUserId);
        Map<Long, Long> jbpConfigurationIdMap = agreementCloneService.copyJbpConfigurations(source.getId(), newVersion);
        agreementCloneService.copyJbpCommercialPeriods(source.getId(), newVersion, jbpConfigurationIdMap);

        if (Boolean.TRUE.equals(request.requiresReapproval())
                && source.getApprovalStatus() == ApprovalStatus.APPROVED) {
            parent.setCurrentVersionId(newVersion.getId());
            parent.setUpdatedByUserId(currentUserId);
            agreementRepository.save(parent);
        }

        recordAudit(parent.getId(), newVersion.getId(), "VERSIONED_EDIT_CREATED",
                String.valueOf(source.getVersionNumber()), String.valueOf(newVersion.getVersionNumber()),
                currentUserId);

        return agreementMapperService.toVersionResponse(newVersion);
    }

    @Override
    @Transactional
    public AgreementVersionResponse updateDraft(Long agreementVersionId, UpdateDraftRequest request, Long currentUserId,
            boolean validateStep1, boolean validateStep2,
            boolean validateCommercialStructure) {

        AgreementVersion version = loadAndValidateOwnership(agreementVersionId, currentUserId);

        if (validateStep1) {
            var violations = validator.validate(request, Step1Validation.class);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            agreementValidationService.validateStep1Fields(request, version);
        }
        if (validateStep2) {
            var violations = validator.validate(request, Step2Validation.class);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            agreementValidationService.validateStep2Fields(request, version);
        }
        if (validateCommercialStructure) {
            agreementValidationService.validateCommercialStructureFields(agreementVersionId, request);
        }

        if (version.getApprovalStatus() == ApprovalStatus.APPROVED
                && Boolean.TRUE.equals(request.requiresReapproval())) {
            return createReapprovalDraft(version, request, currentUserId);
        }
        if (version.getApprovalStatus() != ApprovalStatus.DRAFT) {
            throw new BusinessException("Only DRAFT agreements can be updated via draft save");
        }

        Agreement parent = version.getAgreement();

        Long previousIncomeTypeId = version.getIncomeType() != null ? version.getIncomeType().getId() : null;
        Long previousAgreementTypeId = version.getAgreementType() != null ? version.getAgreementType().getId() : null;

        Long incomeTypeId = resolveIncomeTypeId(version, request.details());
        UpdateDraftRequest scrubbed = agreementDraftMutationService.scrubRequestForIncomeType(request, incomeTypeId);

        agreementDraftMutationService.applyDraftFields(version, scrubbed.details(), scrubbed.commercials());

        // Enforce date locking for edits
        if (version.getRevisionType() == RevisionType.EDIT) {
            AgreementVersion activeParent = agreementVersionRepository.findById(parent.getCurrentVersionId())
                    .orElse(null);
            if (activeParent != null) {
                version.setStartDate(activeParent.getStartDate());
                version.setExpiryDate(activeParent.getExpiryDate());
            }
        }

        version.setUpdatedByUserId(currentUserId);
        version = agreementVersionRepository.save(version);

        Long newIncomeTypeId = resolveIncomeTypeId(version, scrubbed.details());
        Long newAgreementTypeId = version.getAgreementType() != null ? version.getAgreementType().getId() : null;
        boolean incomeTypeChanged = previousIncomeTypeId != null && newIncomeTypeId != null
                && !Objects.equals(previousIncomeTypeId, newIncomeTypeId);
        boolean agreementTypeChanged = previousAgreementTypeId != null && newAgreementTypeId != null
                && !Objects.equals(previousAgreementTypeId, newAgreementTypeId);
        if (incomeTypeChanged || agreementTypeChanged) {
            agreementDraftMutationService.clearDownstreamDraftData(version, parent, currentUserId);
        }

        if (scrubbed.details() != null) {
            agreementDraftMutationService.applyPartnerLocation(version, scrubbed.details(), currentUserId);
        }
        agreementDraftMutationService.syncAgreementName(parent, version, currentUserId);

        if (scrubbed.vendorIds() != null || scrubbed.vendors() != null) {
            agreementDraftMutationService.replaceVendors(version, scrubbed.vendors(), scrubbed.vendorIds(),
                    currentUserId);
        }

        agreementDraftMutationService.syncIncomeTypeSpecificData(
                version,
                incomeTypeId,
                currentUserId,
                scrubbed.productRules(),
                scrubbed.asset(),
                scrubbed.details(),
                validateStep2);
        if (scrubbed.details() != null && scrubbed.details().documents() != null) {
            agreementDraftMutationService.replaceDocuments(version, scrubbed.details().documents(), currentUserId);
        }
        if (scrubbed.commercialData() != null && scrubbed.commercialData().storeMappings() != null) {
            storeMappingService.replaceMappingsFromStoreIds(version.getId(), scrubbed.commercialData().storeMappings());
        }
        version = agreementVersionRepository.save(version);

        recordAudit(parent.getId(), version.getId(), "DRAFT_UPDATED", null, null, currentUserId);
        return agreementMapperService.toVersionResponse(version);
    }

    private AgreementVersionResponse createReapprovalDraft(AgreementVersion approvedVersion,
            UpdateDraftRequest request,
            Long currentUserId) {
        Agreement parent = approvedVersion.getAgreement();
        Integer maxVersion = agreementVersionRepository.findMaxVersionByAgreementId(parent.getId());
        AgreementVersion latest = agreementVersionRepository
                .findByAgreementIdAndVersionNumber(parent.getId(), maxVersion)
                .orElseThrow(() -> new BusinessException("No agreement version exists for this agreement"));

        if (latest.getApprovalStatus() == ApprovalStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                    "A pending revision exists — it must be approved or rejected before creating a new version");
        }
        if (latest.getApprovalStatus() == ApprovalStatus.DRAFT
                && parent.getOwner().getId().equals(currentUserId)) {
            throw new BusinessException("A draft version already exists — update it in place");
        }

        applyImmediateSupersessionIfApplicable(approvedVersion);

        AgreementVersion newVersion = AgreementVersion.builder()
                .agreement(parent)
                .versionNumber(maxVersion + 1)
                .owner(parent.getOwner())
                .approvalStatus(ApprovalStatus.DRAFT)
                .build();
        newVersion.setCreatedByUserId(currentUserId);
        newVersion = agreementVersionRepository.save(newVersion);

        agreementDraftMutationService.applyDraftFields(newVersion, request.details(), request.commercials());
        if (request.details() != null) {
            agreementDraftMutationService.applyPartnerLocation(newVersion, request.details(), currentUserId);
        }
        agreementDraftMutationService.syncAgreementName(parent, newVersion, currentUserId);

        if (request.vendorIds() != null || request.vendors() != null) {
            agreementDraftMutationService.replaceVendors(newVersion, request.vendors(), request.vendorIds(),
                    currentUserId);
        }

        Long incomeTypeId = resolveIncomeTypeId(newVersion, request.details());
        boolean hasProductRulesInRequest = request.productRules() != null &&
                request.productRules().combinations() != null &&
                !request.productRules().combinations().isEmpty();

        agreementDraftMutationService.syncIncomeTypeSpecificData(
                newVersion,
                incomeTypeId,
                currentUserId,
                request.productRules(),
                request.asset(),
                request.details(),
                true);

        if (!agreementDraftMutationService.isAssetRentalIncomeType(incomeTypeId) && !hasProductRulesInRequest) {
            agreementCloneService.copyRulesAndComputed(approvedVersion.getId(), newVersion, currentUserId);
        }
        if (request.details() != null && request.details().documents() != null) {
            agreementDraftMutationService.replaceDocuments(newVersion, request.details().documents(), currentUserId);
        }
        newVersion.setApprovedBy(null);
        newVersion.setApprovalDate(null);
        newVersion.setUpdatedByUserId(currentUserId);
        newVersion = agreementVersionRepository.save(newVersion);

        if (request.commercialData() != null && request.commercialData().storeMappings() != null) {
            storeMappingService.replaceMappingsFromStoreIds(newVersion.getId(),
                    request.commercialData().storeMappings());
        } else {
            storeMappingService.copyMappings(approvedVersion.getId(), newVersion.getId());
        }
        agreementCloneService.copyAssetPayoutPeriods(approvedVersion.getId(), newVersion.getId());

        parent.setCurrentVersionId(newVersion.getId());
        parent.setUpdatedByUserId(currentUserId);
        agreementRepository.save(parent);

        recordAudit(parent.getId(), newVersion.getId(), "REAPPROVAL_DRAFT_CREATED",
                String.valueOf(approvedVersion.getVersionNumber()),
                String.valueOf(newVersion.getVersionNumber()), currentUserId);

        return agreementMapperService.toVersionResponse(newVersion);
    }

    private Long resolveIncomeTypeId(AgreementVersion version, DraftDetailsPayload details) {
        if (version.getIncomeType() != null) {
            return version.getIncomeType().getId();
        }
        return details != null ? details.incomeTypeId() : null;
    }

    @Override
    @Transactional
    public AgreementVersionResponse cloneAgreement(Long sourceAgreementVersionId, Long currentUserId) {
        AgreementVersion source = loadAndValidateOwnership(sourceAgreementVersionId, currentUserId);
        Agreement sourceParent = source.getAgreement();
        AgreementGroup cag = sourceParent.getAgreementGroup();

        User owner = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUserId));

        Agreement parent = Agreement.builder()
                .agreementGroup(cag)
                .agreementName(DRAFT_AGREEMENT_NAME_PLACEHOLDER)
                .owner(owner)
                .isActive(true)
                .build();
        parent.setCreatedByUserId(currentUserId);
        parent = agreementRepository.save(parent);

        AgreementVersion clone = AgreementVersion.builder()
                .agreement(parent)
                .versionNumber(1)
                .owner(owner)
                .approvalStatus(ApprovalStatus.DRAFT)
                .build();
        clone.setCreatedByUserId(currentUserId);
        clone = agreementVersionRepository.save(clone);
        agreementCloneService.copyPartnerLocation(source, clone, currentUserId);

        agreementCloneService.copyVendors(source.getId(), clone, currentUserId);
        agreementCloneService.copyRulesAndComputed(source.getId(), clone, currentUserId);
        storeMappingService.copyMappings(source.getId(), clone.getId());
        agreementCloneService.copyAssetPayoutPeriods(source.getId(), clone.getId());

        recordAudit(parent.getId(), clone.getId(), "AGREEMENT_CLONED",
                String.valueOf(sourceAgreementVersionId), null, currentUserId);

        return agreementMapperService.toVersionResponse(clone);
    }

    @Override
    @Transactional(readOnly = true)
    public AgreementVersionResponse getAgreementVersionById(Long agreementVersionId, Long currentUserId) {
        long startTime = System.currentTimeMillis();
        AgreementVersion version = agreementVersionRepository.findById(agreementVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", agreementVersionId));
        enforceDraftVisibility(version, currentUserId);
        long dbTime = System.currentTimeMillis();
        AgreementVersionResponse response = agreementMapperService.toVersionResponse(version);
        long endTime = System.currentTimeMillis();
        log.info("getAgreementVersionById [{}] - DB Time: {}ms, Serialize Time: {}ms, Total Time: {}ms",
                agreementVersionId, (dbTime - startTime), (endTime - dbTime), (endTime - startTime));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public AgreementResponse getAgreementById(Long agreementId, Long currentUserId) {
        long startTime = System.currentTimeMillis();

        Agreement parent = agreementRepository.findById(agreementId)
                .orElseThrow(() -> new ResourceNotFoundException("Agreement", agreementId));
        enforceAgreementDraftVisibility(parent, currentUserId);

        List<AgreementVersion> versions = agreementVersionRepository.findByAgreementId(agreementId);
        AgreementVersion displayVersion = resolveVisibleLatest(parent, versions, currentUserId);

        long dbEndTime = System.currentTimeMillis();

        if (displayVersion == null) {
            AgreementResponse emptyResp = agreementMapperService.toParentResponseEmpty(parent);
            log.info("getAgreementById [{}] - DB Time: {}ms, Serialize Time: {}ms", agreementId,
                    (dbEndTime - startTime), (System.currentTimeMillis() - dbEndTime));
            return emptyResp;
        }

        List<AgreementVendor> vendors = vendorRepository.findByAgreementVersionId(displayVersion.getId());

        long preSerializeTime = System.currentTimeMillis();

        AgreementResponse response = agreementMapperService.toParentResponse(parent, displayVersion, vendors);

        long endTime = System.currentTimeMillis();
        log.info("getAgreementById [{}] - DB Time: {}ms, Serialize Time: {}ms, Total Time: {}ms",
                agreementId, (preSerializeTime - startTime), (endTime - preSerializeTime), (endTime - startTime));

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AgreementResponse> getAllAgreements(Pageable pageable, Long currentUserId, String scope,
            boolean canViewAllControllerParam, Long agreementGroupId,
            String agreementGroupName, String agreementName,
            String status, String ownerName, Long vendorId, Long incomeTypeId,
            LocalDate startDateFrom, LocalDate startDateTo,
            LocalDate endDateFrom, LocalDate endDateTo) {
        Pageable mappedPageable = agreementMapperService.mapAgreementPageable(pageable);
        var filterSpec = AgreementSpec.withFilters(
                agreementGroupId, agreementGroupName,
                agreementName, status, ownerName, vendorId, incomeTypeId,
                startDateFrom, startDateTo, endDateFrom, endDateTo);

        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean canViewAll = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("AGREEMENT_VIEW_ALL") || a.getAuthority().equals("DRAFT_VIEW_ALL"));

        if (!"DRAFT".equalsIgnoreCase(status)) {
            filterSpec = filterSpec.and(AgreementSpec.hasNonDraftVersion());
        }

        if ("ALL".equalsIgnoreCase(scope) && canViewAll) {
            // Admin viewing all drafts. Do NOT append ownedBy() or hasNonDraftVersion().
        } else {
            filterSpec = filterSpec.and(AgreementSpec.ownedBy(currentUserId));
        }

        Page<Agreement> parentPage = agreementRepository.findAll(filterSpec, mappedPageable);

        List<Long> agreementIds = parentPage.getContent().stream().map(Agreement::getId).toList();
        if (agreementIds.isEmpty()) {
            return parentPage.map(agreementMapperService::toParentResponseEmpty);
        }

        return parentPage.map(agreement -> {
            AgreementVersion displayVersion = resolveDisplayVersion(agreement, status);
            List<AgreementVendor> vendors = displayVersion != null
                    ? vendorRepository.findByAgreementVersionIdIn(List.of(displayVersion.getId()))
                    : List.of();
            return displayVersion != null ? agreementMapperService.toParentResponse(agreement, displayVersion, vendors)
                    : agreementMapperService.toParentResponseEmpty(agreement);
        });
    }

    private AgreementVersion resolveDisplayVersion(Agreement agreement, String filterStatus) {
        List<AgreementVersion> versions = agreementVersionRepository.findByAgreementId(agreement.getId());

        if ("DRAFT".equalsIgnoreCase(filterStatus)) {
            return versions.stream()
                .max(java.util.Comparator.comparing(AgreementVersion::getVersionNumber))
                .orElse(null);
        }

        if (agreement.getCurrentVersionId() != null) {
            return agreementVersionRepository.findById(agreement.getCurrentVersionId()).orElse(null);
        }

        return versions.stream()
            .filter(v -> v.getApprovalStatus() != ApprovalStatus.DRAFT)
            .max(java.util.Comparator.comparing(AgreementVersion::getVersionNumber))
            .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgreementVersionSummaryResponse> getVersionsByAgreementId(Long agreementId, Long currentUserId) {
        Agreement parent = agreementRepository.findById(agreementId)
                .orElseThrow(() -> new ResourceNotFoundException("Agreement", agreementId));
        enforceAgreementDraftVisibility(parent, currentUserId);

        return agreementVersionRepository.findByAgreementId(agreementId)
                .stream()
                .filter(v -> v.getApprovalStatus() != ApprovalStatus.DRAFT
                        || v.getAgreement().getOwner().getId().equals(currentUserId))
                .sorted((a, b) -> Integer.compare(a.getVersionNumber(), b.getVersionNumber()))
                .map(agreementMapperService::toVersionSummaryResponse)
                .toList();
    }

    @Override
    @Transactional
    public AgreementVersionResponse transferOwnership(Long agreementVersionId, Long newOwnerUserId,
            Long performedByUserId, boolean isAdmin, String comments) {
        return executeOwnershipTransfer(agreementVersionId, newOwnerUserId, performedByUserId, isAdmin, comments,
                false);
    }

    @Override
    @Transactional
    public AgreementVersionResponse completeApprovedTransfer(Long agreementVersionId, Long newOwnerUserId,
            Long approverId) {
        return executeOwnershipTransfer(agreementVersionId, newOwnerUserId, approverId, false, null, true);
    }

    private AgreementVersionResponse executeOwnershipTransfer(Long agreementVersionId, Long newOwnerUserId,
            Long performedByUserId, boolean isAdmin, String comments,
            boolean fromApprovedRequest) {
        AgreementVersion version = agreementVersionRepository.findById(agreementVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", agreementVersionId));

        Agreement parent = version.getAgreement();
        Long currentOwnerId = parent.getOwner().getId();
        if (!fromApprovedRequest && !isAdmin && !currentOwnerId.equals(performedByUserId)) {
            throw new UnauthorizedException("Only the owner or an admin can transfer ownership");
        }

        User newOwner = userRepository.findById(newOwnerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", newOwnerUserId));

        if (newOwnerUserId.equals(currentOwnerId)) {
            throw new BusinessException("Agreement is already owned by this user");
        }

        String auditNewValue = isAdmin
                ? buildAdminTransferAuditNote(newOwnerUserId, comments)
                : String.valueOf(newOwnerUserId);

        applyOwnershipTransfer(parent, newOwner, performedByUserId, auditNewValue, currentOwnerId);

        AgreementVersion operationalVersion = resolveOperationalVersion(parent);
        Long responseVersionId = operationalVersion != null ? operationalVersion.getId() : agreementVersionId;
        return agreementMapperService
                .toVersionResponse(agreementVersionRepository.findById(responseVersionId).orElseThrow());
    }

    @Override
    @Transactional
    public BulkGroupSubmitResponse submitGroupDraftsForApproval(Long groupId, Long currentUserId) {
        List<AgreementVersion> drafts = loadGroupDraftsForSubmit(groupId, currentUserId);
        return groupSubmitTransactionTemplate.execute(status -> {
            List<String> submittedNames = new ArrayList<>();
            for (AgreementVersion version : drafts) {
                AgreementVersion current = agreementVersionRepository.findById(version.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", version.getId()));
                commitDraftSubmit(current, null, currentUserId);
                Agreement parent = current.getAgreement();
                submittedNames.add(agreementDraftMutationService.resolveAgreementDisplayName(parent));
            }
            return new BulkGroupSubmitResponse(submittedNames.size(), submittedNames);
        });
    }

    /**
     * Read-only validation pass — no draft fields are modified.
     * Throws {@link IncompleteAgreementException} (HTTP 400) on first failure.
     */
    private List<AgreementVersion> loadGroupDraftsForSubmit(Long groupId, Long currentUserId) {
        agreementGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementGroup", groupId));

        List<AgreementVersion> drafts = agreementVersionRepository.findLatestDraftVersionsByGroupId(groupId);
        if (drafts.isEmpty()) {
            throw new IncompleteAgreementException("No draft agreements found in this group to submit");
        }

        for (AgreementVersion version : drafts) {
            agreementValidationService.validateAgreementOwnership(version.getAgreement(), currentUserId);
            agreementValidationService.validateCompleteAgreement(version);
        }
        return drafts;
    }

    @Override
    @Transactional
    public AgreementVersionResponse submitForApproval(Long agreementVersionId, String comments, Long currentUserId) {
        AgreementVersion version = loadAndValidateOwnership(agreementVersionId, currentUserId);
        if (version.getVersionNumber() > 1 && (comments == null || comments.isBlank())) {
            throw new BusinessException("Reason for edit/revision is required when submitting a revised version");
        }
        version = applySubmitForApproval(version, comments, currentUserId);
        return agreementMapperService.toVersionResponse(version);
    }

    @Override
    @Transactional
    public AgreementVersionResponse approve(Long agreementVersionId, String remarks, Long approverId) {
        AgreementVersion version = agreementVersionRepository.findById(agreementVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", agreementVersionId));

        if (version.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new BusinessException("Only PENDING_APPROVAL agreements can be approved");
        }
        if (version.getAgreement().getOwner().getId().equals(approverId)) {
            throw new AccessDeniedException("Separation of Duties violation: Cannot approve your own agreement.");
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new ResourceNotFoundException("User", approverId));

        ApprovalStatus before = version.getApprovalStatus();
        version.setApprovalStatus(ApprovalStatus.APPROVED);
        version.setApprovedBy(approver);
        version.setApprovalDate(LocalDateTime.now());
        version.setUpdatedByUserId(approverId);
        version = agreementVersionRepository.save(version);

        Agreement parent = version.getAgreement();
        parent.setCurrentVersionId(version.getId());
        parent.setUpdatedByUserId(approverId);
        agreementRepository.save(parent);

        if (version.getRevisionType() == RevisionType.EDIT && version.getBaseVersionId() != null) {
            // Deterministically find the EXACT version that spawned this edit
            Optional<AgreementVersion> exactParentOpt = agreementVersionRepository.findById(version.getBaseVersionId());

            if (exactParentOpt.isPresent()) {
                AgreementVersion exactParent = exactParentOpt.get();
                exactParent.setSupersededFromStatus(exactParent.getApprovalStatus());
                exactParent.setApprovalStatus(ApprovalStatus.EDITED);
                exactParent.setUpdatedByUserId(approverId);
                agreementVersionRepository.save(exactParent);
            }
        } else if (version.getStartDate() == null || !version.getStartDate().isAfter(LocalDate.now())) {
            List<AgreementVersion> olderVersions = agreementVersionRepository.findOlderApprovedVersions(parent.getId(),
                    version.getVersionNumber());
            for (AgreementVersion oldVersion : olderVersions) {
                oldVersion.setSupersededFromStatus(ApprovalStatus.APPROVED);
                oldVersion.setApprovalStatus(ApprovalStatus.SUPERSEDED);
                oldVersion.setUpdatedByUserId(approverId);
                agreementVersionRepository.save(oldVersion);
            }
        }

        recordApproval(version, ApprovalAction.APPROVED, remarks, before, ApprovalStatus.APPROVED, approverId);
        recordAudit(parent.getId(), agreementVersionId, "APPROVED", before.name(), ApprovalStatus.APPROVED.name(),
                approverId);

        return agreementMapperService.toVersionResponse(version);
    }

    @Override
    @Transactional
    public AgreementVersionResponse reject(Long agreementVersionId, String remarks, Long approverId) {
        AgreementVersion version = agreementVersionRepository.findById(agreementVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", agreementVersionId));

        if (version.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new BusinessException("Only PENDING_APPROVAL agreements can be rejected");
        }
        if (version.getAgreement().getOwner().getId().equals(approverId)) {
            throw new AccessDeniedException("Separation of Duties violation: Cannot approve your own agreement.");
        }

        ApprovalStatus before = version.getApprovalStatus();
        version.setApprovalStatus(ApprovalStatus.REJECTED);
        version.setUpdatedByUserId(approverId);
        version = agreementVersionRepository.save(version);

        recordApproval(version, ApprovalAction.REJECTED, remarks, before, ApprovalStatus.REJECTED, approverId);
        recordAudit(version.getAgreement().getId(), agreementVersionId, "REJECTED",
                before.name(), ApprovalStatus.REJECTED.name(), approverId);

        return agreementMapperService.toVersionResponse(version);
    }

    @Override
    @Transactional
    public AgreementVersionResponse terminate(Long agreementVersionId, TerminateAgreementRequest request,
            Long currentUserId) {
        AgreementVersion version = agreementVersionRepository.findById(agreementVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", agreementVersionId));

        if (version.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new BusinessException("Only APPROVED agreements can be terminated");
        }

        version.setTerminationDate(request.terminationDate());
        version.setTerminationReason(request.terminationReason());
        version.setUpdatedByUserId(currentUserId);
        version = agreementVersionRepository.save(version);

        recordAudit(version.getAgreement().getId(), agreementVersionId, "TERMINATED",
                null, request.terminationReason(), currentUserId);

        return agreementMapperService.toVersionResponse(version);
    }

    @Override
    @Transactional
    public AgreementVersionResponse toggleAgreementInProgress(Long agreementId, Long currentUserId) {
        Agreement parent = agreementRepository.findById(agreementId)
                .orElseThrow(() -> new ResourceNotFoundException("Agreement", agreementId));

        if (parent.getCurrentVersionId() == null) {
            throw new BusinessException("No active version exists for this agreement");
        }

        AgreementVersion version = agreementVersionRepository.findById(parent.getCurrentVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", parent.getCurrentVersionId()));

        loadAndValidateOwnership(version.getId(), currentUserId);
        agreementValidationService.assertNotTerminated(version, TERMINATED_EDIT_MSG);

        if (version.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new BusinessException("In-progress flag can only be toggled on approved agreements");
        }

        boolean nextFlag = !version.isInProgressFlag();
        version.setInProgressFlag(nextFlag);
        version.setInProgressSince(nextFlag ? LocalDateTime.now() : null);
        version.setUpdatedByUserId(currentUserId);
        version = agreementVersionRepository.save(version);

        return agreementMapperService.toVersionResponse(version);
    }

    @Override
    @Transactional
    public AgreementVersionResponse initEdit(Long sourceAgreementVersionId, Long currentUserId) {
        return initRevision(sourceAgreementVersionId, currentUserId, RevisionType.EDIT);
    }

    @Override
    @Transactional
    public AgreementVersionResponse initRenew(Long sourceAgreementVersionId, Long currentUserId) {
        return initRevision(sourceAgreementVersionId, currentUserId, RevisionType.RENEWAL);
    }

    @Override
    @Transactional
    public AgreementVersionResponse initRevise(Long sourceAgreementVersionId, Long currentUserId) {
        return initRevision(sourceAgreementVersionId, currentUserId, RevisionType.REVISION);
    }

    private AgreementVersionResponse initRevision(Long sourceAgreementVersionId, Long currentUserId,
            RevisionType type) {
        AgreementVersion source = agreementVersionRepository.findById(sourceAgreementVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", sourceAgreementVersionId));

        Agreement parent = source.getAgreement();
        loadAndValidateOwnership(sourceAgreementVersionId, currentUserId);

        // Check for existing drafts
        List<AgreementVersion> drafts = agreementVersionRepository.findByAgreementIdAndApprovalStatusIn(
                parent.getId(), List.of(ApprovalStatus.DRAFT, ApprovalStatus.PENDING_APPROVAL));
        if (!drafts.isEmpty()) {
            AgreementVersion existing = drafts.get(0);
            if (existing.getApprovalStatus() == ApprovalStatus.PENDING_APPROVAL) {
                throw new BusinessException(
                        "Cannot edit or renew while a version is pending approval. Wait for the review to complete.");
            }
            return agreementMapperService.toVersionResponse(existing);
        }

        // Validate source state
        if (type == RevisionType.RENEWAL) {
            if (source.getApprovalStatus() != ApprovalStatus.APPROVED) {
                throw new BusinessException("Only approved agreements can be renewed");
            }
            agreementValidationService.assertNotTerminated(source, TERMINATED_RENEW_MSG);
            agreementValidationService.assertWithinRenewWindow(source);
        } else {
            if (source.getApprovalStatus() != ApprovalStatus.APPROVED
                    && source.getApprovalStatus() != ApprovalStatus.REJECTED) {
                throw new BusinessException("Can only edit from an APPROVED or REJECTED agreement version");
            }
            agreementValidationService.assertNotTerminated(source, TERMINATED_EDIT_MSG);
        }

        Integer maxVersion = agreementVersionRepository.findMaxVersionByAgreementId(parent.getId());

        AgreementVersion newVersion = AgreementVersion.builder()
                .agreement(parent)
                .versionNumber(maxVersion + 1)
                .owner(parent.getOwner())
                .incomeType(source.getIncomeType())
                .agreementType(source.getAgreementType())
                .commercialStructure(source.getCommercialStructure())
                .commercialValue(source.getCommercialValue())
                .startDate(source.getStartDate())
                .expiryDate(source.getExpiryDate())
                .approvalStatus(ApprovalStatus.DRAFT)
                .revisionType(type == RevisionType.REVISION ? source.getRevisionType() : type)
                .notes(source.getNotes())
                .paymentRealizationType(source.getPaymentRealizationType())
                .assetType(source.getAssetType())
                .assetCategory(source.getAssetCategory())
                .build();

        if (type == RevisionType.REVISION) {
            newVersion.setBaseVersionId(source.getBaseVersionId());
        } else {
            newVersion.setBaseVersionId(source.getId());
        }

        newVersion.setCreatedByUserId(currentUserId);
        newVersion = agreementVersionRepository.save(newVersion);

        agreementCloneService.copyRulesAndComputed(source.getId(), newVersion, currentUserId);
        agreementCloneService.copyDocuments(source.getId(), newVersion.getId(), currentUserId);
        storeMappingService.copyMappings(source.getId(), newVersion.getId());
        agreementCloneService.copyAssetPayoutPeriods(source.getId(), newVersion.getId());
        agreementCloneService.copyPartnerLocation(source, newVersion, currentUserId);
        agreementCloneService.copyVendors(source.getId(), newVersion, currentUserId);
        agreementCloneService.copySlabs(source.getId(), newVersion, currentUserId);
        Map<Long, Long> jbpConfigurationIdMap = agreementCloneService.copyJbpConfigurations(source.getId(), newVersion);
        agreementCloneService.copyJbpCommercialPeriods(source.getId(), newVersion, jbpConfigurationIdMap);

        return agreementMapperService.toVersionResponse(newVersion);
    }

    @Override
    @Transactional
    public AgreementVersionResponse submitEdit(Long draftVersionId, String comments, Long currentUserId) {
        return submitDraftRevision(draftVersionId, comments, currentUserId, RevisionType.EDIT);
    }

    @Override
    @Transactional
    public AgreementVersionResponse submitRenew(Long draftVersionId, String comments, Long currentUserId) {
        return submitDraftRevision(draftVersionId, comments, currentUserId, RevisionType.RENEWAL);
    }

    @Override
    @Transactional
    public AgreementVersionResponse submitRevise(Long draftVersionId, String comments, Long currentUserId) {
        return submitDraftRevision(draftVersionId, comments, currentUserId, RevisionType.REVISION);
    }

    private AgreementVersionResponse submitDraftRevision(Long draftVersionId, String comments, Long currentUserId,
            RevisionType expectedType) {
        if (comments == null || comments.isBlank()) {
            throw new BusinessException("Reason for edit/revision is required when submitting a revised version");
        }

        AgreementVersion draft = agreementVersionRepository.findById(draftVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", draftVersionId));

        if (draft.getApprovalStatus() != ApprovalStatus.DRAFT) {
            throw new BusinessException("Only drafts can be submitted for approval");
        }

        loadAndValidateOwnership(draftVersionId, currentUserId);

        Agreement parent = draft.getAgreement();
        List<AgreementVersion> versions = agreementVersionRepository.findByAgreementId(parent.getId());
        boolean hasPending = versions.stream()
                .anyMatch(v -> v.getApprovalStatus() == ApprovalStatus.PENDING_APPROVAL);
        if (hasPending) {
            throw new BusinessException(
                    "Cannot edit or renew while a version is PENDING_APPROVAL. Wait for the review to complete.");
        }

        // Retrieve the true base version from the database to validate dates/rules
        AgreementVersion source = null;
        if (draft.getBaseVersionId() != null) {
            source = agreementVersionRepository.findById(draft.getBaseVersionId()).orElse(null);
        }

        // Fallback for legacy drafts created before baseVersionId was implemented
        if (source == null && draft.getVersionNumber() != null) {
            final Integer draftVersionNumber = draft.getVersionNumber();
            source = versions.stream()
                    .filter(v -> v.getVersionNumber().equals(draftVersionNumber - 1))
                    .findFirst()
                    .orElse(null);
        }

        if (source != null) {
            if (expectedType == RevisionType.RENEWAL) {
                agreementValidationService.assertWithinRenewWindow(source);
                // We cannot use assertRenewIdentityUnchanged since we don't have the request,
                // but we can check the draft's incomeType vs source
                if (!Objects.equals(source.getIncomeType().getId(), draft.getIncomeType().getId())) {
                    throw new BusinessException("Income type cannot be changed during renewal.");
                }
                agreementValidationService.assertRenewDates(source, draft);
            } else if (expectedType == RevisionType.EDIT) {
                if (draft.getExpiryDate() != null && source.getExpiryDate() != null
                        && draft.getExpiryDate().isAfter(source.getExpiryDate())) {
                    throw new BusinessException(
                            "An Edit cannot extend the agreement duration. Please use the Renew action instead.");
                }
            }
        }

        draft.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        draft.setRevisionType(expectedType);
        draft = agreementVersionRepository.save(draft);

        recordApproval(draft, ApprovalAction.SUBMITTED, comments, ApprovalStatus.DRAFT, ApprovalStatus.PENDING_APPROVAL,
                currentUserId);

        return agreementMapperService.toVersionResponse(draft);
    }

    private static final String COMMERCIAL_DATE_CHANGE_MSG = "A new commercial structure must be provided because the agreement dates have been modified.";

    /**
     * Optimistic lock for Edit/Renew.
     * Accept tip version number, OR last APPROVED when tip is REJECTED (re-edit
     * after rejection).
     * New version always increments from maxVersion+1.
     */

    /**
     * Renew, or edit with date change, on Excel-driven structures must include a
     * commercial override
     * (no deep-copy of stale targets into a new timeframe).
     */

    /**
     * Subtree partial override: null/empty commercialData subtree → deep-copy from
     * source;
     * populated subtree → persist from payload (JBP config IDs remapped via
     * blueprint → new).
     */
    void applyCommercialChildrenForRevision(AgreementVersion source,
            AgreementVersion target,
            Long currentUserId,
            CommercialDataPayload commercialData) {
        // Keep request asset periods when already applied; deep-copy only when target
        // has none.
        agreementCloneService.maybeCopyAssetPayoutPeriods(source.getId(), target.getId());
        agreementCloneService.copySlabs(source.getId(), target, currentUserId);

        boolean hasStoreOverride = commercialData != null
                && commercialData.storeMappings() != null;
        if (hasStoreOverride) {
            storeMappingService.replaceMappingsFromStoreIds(target.getId(), commercialData.storeMappings());
        } else {
            // Mapped store list is the store-count source of truth — deep-copy mappings
            // as-is.
            storeMappingService.copyMappings(source.getId(), target.getId());
        }

        boolean hasJbpOverride = commercialData != null
                && commercialData.jbp() != null
                && commercialData.jbp().sheets() != null
                && !commercialData.jbp().sheets().isEmpty();
        boolean hasJbpBlueprint = commercialData != null
                && commercialData.jbpBlueprint() != null
                && commercialData.jbpBlueprint().configurations() != null
                && !commercialData.jbpBlueprint().configurations().isEmpty();

        if (hasJbpOverride && !hasJbpBlueprint) {
            throw new BusinessException(
                    "JBP blueprint is required when submitting JBP commercial data on Edit/Renew.");
        }

        if (hasJbpOverride && hasJbpBlueprint) {
            jbpCommercialPeriodRepository.deleteByAgreementVersionId(target.getId());
            jbpConfigurationRepository.deleteByAgreementVersionId(target.getId());

            if (commercialData.jbpBlueprint().financialYearStartMonth() != null) {
                target.setFinancialYearStartMonth(commercialData.jbpBlueprint().financialYearStartMonth());
                agreementVersionRepository.save(target);
            }

            Map<Long, Long> clientConfigIdMap = provisionJbpConfigsFromBlueprint(
                    target, commercialData.jbpBlueprint());
            persistRemappedJbpPeriods(target, commercialData.jbp(), clientConfigIdMap);

            // Payment intervals and target intervals are now stored per-config on the
            // entity.
            // No separate version-level frequency table to update.
            target.setCommercialStructure(CommercialStructure.SLAB);
            target.setCommercialValue(null);
            target.setFlatValueType(null);
            target.setFlatBaselineFrequency(null);
            agreementVersionRepository.save(target);
            return;
        }

        Map<Long, Long> configurationIdMap = agreementCloneService.copyJbpConfigurations(source.getId(), target);
        agreementCloneService.copyJbpCommercialPeriods(source.getId(), target, configurationIdMap);
    }

    /**
     * Deep-copy source asset payout periods only when the target has neither a flat
     * payout
     * nor any period rows (request already applied via replaceAsset).
     */

    private Map<Long, Long> provisionJbpConfigsFromBlueprint(
            AgreementVersion target,
            JbpWorkbookRequest blueprint) {
        Map<Long, Long> clientToNewId = new HashMap<>();
        java.util.Set<String> allPaymentIntervals = new java.util.HashSet<>();

        for (JbpConfigurationBlockDto block : blueprint.configurations()) {
            long clientConfigId;
            try {
                clientConfigId = Long.parseLong(block.configId().trim());
            } catch (NumberFormatException ex) {
                throw new BusinessException("JBP configuration id must be numeric: " + block.configId());
            }
            if (block.paymentIntervals() == null || block.paymentIntervals().isEmpty()) {
                throw new BusinessException("JBP configuration " + block.configId() + " has no payment intervals.");
            }
            for (String interval : block.paymentIntervals()) {
                if (!allPaymentIntervals.add(interval.trim().toUpperCase())) {
                    throw new BusinessException("Payment interval '" + interval
                            + "' is selected in multiple JBP configurations. Payment intervals must be mutually exclusive.");
                }
            }
            AgreementJbpConfiguration configuration = AgreementJbpConfiguration.builder()
                    .agreementVersion(target)
                    .slabCount(block.maxSlabs())
                    .paymentIntervals(new java.util.ArrayList<>(block.paymentIntervals()))
                    .targetIntervals(new java.util.ArrayList<>(block.targetIntervals()))
                    .build();
            configuration = jbpConfigurationRepository.save(configuration);
            clientToNewId.put(clientConfigId, configuration.getId());
        }
        return clientToNewId;
    }

    private void persistRemappedJbpPeriods(AgreementVersion target,
            JbpStagedWorkbookDto staged,
            Map<Long, Long> sourceToNewConfigId) {
        jbpCommercialPeriodRepository.deleteByAgreementVersionId(target.getId());
        Set<Long> periodIds = new HashSet<>();
        for (var sheet : staged.sheets()) {
            if (sheet.rows() == null) {
                continue;
            }
            for (var row : sheet.rows()) {
                if (row.timePeriodId() != null) {
                    periodIds.add(row.timePeriodId());
                }
                if (row.parentPeriodId() != null) {
                    periodIds.add(row.parentPeriodId());
                }
            }
        }
        Map<Long, AgreementTimePeriod> periodsById = timePeriodRepository.findAllById(periodIds).stream()
                .collect(Collectors.toMap(AgreementTimePeriod::getId, Function.identity()));

        List<AgreementJbpCommercialPeriod> entities = new ArrayList<>();
        for (var sheet : staged.sheets()) {
            if (sheet.rows() == null) {
                continue;
            }
            for (var row : sheet.rows()) {
                Long newConfigId = sourceToNewConfigId.get(row.jbpConfigurationId());
                if (newConfigId == null) {
                    throw new BusinessException(
                            "JBP configuration " + row.jbpConfigurationId()
                                    + " from upload does not map to the new version.");
                }
                AgreementJbpConfiguration configuration = jbpConfigurationRepository.findById(newConfigId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "AgreementJbpConfiguration", newConfigId));
                AgreementTimePeriod period = periodsById.get(row.timePeriodId());
                if (period == null) {
                    throw new ResourceNotFoundException("AgreementTimePeriod", row.timePeriodId());
                }
                AgreementTimePeriod parentTimePeriod = null;
                if (row.parentPeriodId() != null && row.subPeriodName() != null) {
                    parentTimePeriod = periodsById.get(row.parentPeriodId());
                }
                entities.add(AgreementJbpCommercialPeriod.builder()
                        .agreementVersion(target)
                        .jbpConfiguration(configuration)
                        .targetType(row.targetType())
                        .target(row.target())
                        .qualifierPercent(row.qualifierPercent() != null ? row.qualifierPercent() : BigDecimal.ZERO)
                        .payoutType(row.payoutType())
                        .payout(row.payout())
                        .maxPurchase(row.maxPurchase())
                        .maxPayout(row.maxPayout())
                        .slabTierNumber(row.slabTierNumber())
                        .timePeriod(period)
                        .parentTimePeriod(parentTimePeriod)
                        .build());
            }
        }
        if (!entities.isEmpty()) {
            jbpCommercialPeriodRepository.saveAll(entities);
        }
    }

    private void deleteOrphanDraftVersions(Agreement parent, Long currentUserId) {
        List<AgreementVersion> drafts = agreementVersionRepository.findByAgreementId(parent.getId()).stream()
                .filter(v -> v.getApprovalStatus() == ApprovalStatus.DRAFT)
                .toList();
        if (drafts.isEmpty()) {
            return;
        }

        Long currentVersionId = parent.getCurrentVersionId();
        boolean currentPointsAtDraft = currentVersionId != null
                && drafts.stream().anyMatch(d -> d.getId().equals(currentVersionId));

        for (AgreementVersion draft : drafts) {
            hardDeleteAgreementVersion(draft.getId());
        }

        if (currentPointsAtDraft) {
            AgreementVersion latestApproved = agreementVersionRepository.findByAgreementId(parent.getId()).stream()
                    .filter(v -> v.getApprovalStatus() == ApprovalStatus.APPROVED)
                    .max(Comparator.comparingInt(AgreementVersion::getVersionNumber))
                    .orElse(null);
            parent.setCurrentVersionId(latestApproved != null ? latestApproved.getId() : null);
            parent.setUpdatedByUserId(currentUserId);
            agreementRepository.save(parent);
        }
    }

    @Override
    @Transactional
    public void deleteDraftAgreement(Long agreementId, Long currentUserId) {
        Agreement agreement = agreementRepository.findById(agreementId)
                .orElseThrow(() -> new ResourceNotFoundException("Agreement", agreementId));

        List<AgreementVersion> versions = agreementVersionRepository.findByAgreementId(agreementId);
        if (versions.isEmpty()) {
            throw new BusinessException("Agreement has no versions to delete");
        }

        boolean everApproved = versions.stream()
                .anyMatch(version -> version.getApprovalStatus() == ApprovalStatus.APPROVED);
        if (everApproved) {
            throw new BusinessException("Cannot delete an agreement that has been approved");
        }

        AgreementVersion activeVersion = resolveActiveVersion(agreement, versions);
        if (activeVersion.getApprovalStatus() != ApprovalStatus.DRAFT) {
            throw new BusinessException("Only draft agreements can be deleted");
        }

        loadAndValidateOwnership(activeVersion.getId(), currentUserId);

        agreement.setCurrentVersionId(null);
        agreementRepository.saveAndFlush(agreement);

        for (AgreementVersion version : versions) {
            hardDeleteAgreementVersion(version.getId());
        }
        auditRepository.deleteByAgreementId(agreementId);
        agreementRepository.delete(agreement);
    }

    @Override
    @Transactional
    public void discardDraftVersion(Long versionId, Long currentUserId) {
        AgreementVersion version = agreementVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", versionId));

        if (version.getApprovalStatus() != ApprovalStatus.DRAFT) {
            throw new BusinessException("Only draft versions can be discarded");
        }

        loadAndValidateOwnership(versionId, currentUserId);

        Agreement agreement = version.getAgreement();
        List<AgreementVersion> versions = agreementVersionRepository.findByAgreementId(agreement.getId());

        boolean isOnlyVersion = versions.size() == 1;

        if (isOnlyVersion) {
            agreement.setCurrentVersionId(null);
            agreementRepository.saveAndFlush(agreement);
            auditRepository.deleteByAgreementId(agreement.getId());
            auditRepository.flush();
            hardDeleteAgreementVersion(versionId);
            agreementVersionRepository.flush();
            agreementRepository.delete(agreement);
        } else {
            if (version.getId().equals(agreement.getCurrentVersionId())) {
                agreement.setCurrentVersionId(null);
                agreementRepository.saveAndFlush(agreement);
            }
            hardDeleteAgreementVersion(versionId);
        }
    }

    private AgreementVersion resolveVisibleLatest(Agreement agreement, List<AgreementVersion> versions, Long currentUserId) {
        if (versions.isEmpty()) {
            return null;
        }

        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean canViewAll = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("AGREEMENT_VIEW_ALL") || a.getAuthority().equals("DRAFT_VIEW_ALL"));

        AgreementVersion latest = versions.stream()
                .max(java.util.Comparator.comparing(AgreementVersion::getVersionNumber))
                .orElse(null);

        if (latest != null && (latest.getApprovalStatus() != ApprovalStatus.DRAFT || agreement.getOwner().getId().equals(currentUserId) || canViewAll)) {
            return latest;
        }

        if (agreement.getCurrentVersionId() != null) {
            return agreementVersionRepository.findById(agreement.getCurrentVersionId()).orElse(latest);
        }

        return versions.stream()
                .filter(v -> v.getApprovalStatus() != ApprovalStatus.DRAFT || agreement.getOwner().getId().equals(currentUserId) || canViewAll)
                .max(java.util.Comparator.comparing(AgreementVersion::getVersionNumber))
                .orElse(null);
    }

    private AgreementVersion resolveActiveVersion(Agreement agreement, List<AgreementVersion> versions) {
        if (agreement.getCurrentVersionId() != null) {
            return agreementVersionRepository.findById(agreement.getCurrentVersionId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("AgreementVersion", agreement.getCurrentVersionId()));
        }
        return versions.stream()
                .max((left, right) -> Integer.compare(left.getVersionNumber(), right.getVersionNumber()))
                .orElseThrow(() -> new BusinessException("Agreement has no versions to delete"));
    }

    private void hardDeleteAgreementVersion(Long versionId) {
        jbpCommercialPeriodRepository.deleteByAgreementVersionId(versionId);
        jbpConfigurationRepository.deleteByAgreementVersionId(versionId);
        slabRepository.deleteByAgreementVersionId(versionId);
        vendorRepository.deleteByAgreementVersionId(versionId);
        manufacturerRuleRepository.deleteByAgreementVersionId(versionId);
        divisionRuleRepository.deleteByAgreementVersionId(versionId);
        productRuleRepository.deleteByAgreementVersionId(versionId);
        computedProductRepository.deleteByAgreementVersionId(versionId);
        approvalRepository.deleteByAgreementVersionId(versionId);
        reminderRepository.deleteByAgreementVersionId(versionId);
        documentRepository.deleteByAgreementVersionId(versionId);
        actionRequestRepository.deleteByAgreementVersionId(versionId);
        auditRepository.deleteByAgreementVersionId(versionId);
        storeMappingRepository.deleteByAgreementVersionId(versionId);
        assetPayoutPeriodRepository.deleteByAgreementVersionId(versionId);
        agreementLocationRepository.deleteByAgreementVersionId(versionId);
        agreementVersionRepository.deleteById(versionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AgreementVersionResponse> getPendingApprovals(String search, Pageable pageable) {
        String term = (search != null && !search.isBlank()) ? search.trim() : null;
        return agreementVersionRepository.findAllPendingApproval(term, pageable)
                .map(agreementMapperService::toVersionResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalTimelineResponse> getApprovalTimeline(Long agreementVersionId) {
        List<ApprovalTimelineResponse> approvalEntries = approvalRepository
                .findByAgreementVersionIdOrderByCreatedAtAsc(agreementVersionId)
                .stream()
                .map(a -> new ApprovalTimelineResponse(
                        a.getId(), a.getAction(), null, a.getRemarks(),
                        a.getApprovalStatusBefore(), a.getApprovalStatusAfter(),
                        a.getCreatedByUserId(), agreementMapperService.resolveUserName(a.getCreatedByUserId()),
                        a.getCreatedAt()))
                .toList();

        List<ApprovalTimelineResponse> operationalEntries = actionRequestRepository
                .findByAgreementVersion_IdOrderByCreatedAtAsc(agreementVersionId)
                .stream()
                .flatMap(r -> agreementMapperService.mapActionRequestToTimeline(r).stream())
                .toList();

        List<ApprovalTimelineResponse> combined = new ArrayList<>(approvalEntries.size() + operationalEntries.size());
        combined.addAll(approvalEntries);
        combined.addAll(operationalEntries);
        combined.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));
        return combined;
    }

    @Override
    @Transactional
    public void bulkTransferOwnership(Long fromUserId, Long toUserId, List<Long> agreementIds,
            Long performedByUserId) {
        User toUser = userRepository.findById(toUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", toUserId));

        List<Agreement> agreementsToTransfer;
        if (agreementIds != null && !agreementIds.isEmpty()) {
            agreementsToTransfer = agreementRepository.findAllById(agreementIds).stream()
                    .filter(a -> a.getOwner().getId().equals(fromUserId))
                    .filter(this::isAgreementOperationallyActive)
                    .toList();
        } else {
            agreementsToTransfer = agreementRepository.findByOwnerId(fromUserId, Pageable.unpaged())
                    .stream()
                    .filter(this::isAgreementOperationallyActive)
                    .toList();
        }

        String auditNewValue = buildAdminTransferAuditNote(toUserId, "Bulk admin reassignment");
        for (Agreement agreement : agreementsToTransfer) {
            applyOwnershipTransfer(agreement, toUser, performedByUserId, auditNewValue, fromUserId);
        }
    }

    /**
     * Renew allowed when expiry has passed or is within RENEW_WINDOW_DAYS
     * (inclusive).
     */

    private void assertRenewIdentityUnchanged(AgreementVersion source, AgreementRevisionSubmitRequest request) {
        DraftDetailsPayload details = request.details();
        Long sourceIncomeId = source.getIncomeType() != null ? source.getIncomeType().getId() : null;
        Long requestIncomeId = details != null ? details.incomeTypeId() : null;
        if (requestIncomeId != null && !Objects.equals(sourceIncomeId, requestIncomeId)) {
            throw new BusinessException(RENEW_IDENTITY_INCOME_MSG);
        }

        Long sourceTypeId = source.getAgreementType() != null ? source.getAgreementType().getId() : null;
        Long requestTypeId = details != null ? details.agreementTypeId() : null;
        if (requestTypeId != null && !Objects.equals(sourceTypeId, requestTypeId)) {
            throw new BusinessException(RENEW_IDENTITY_TYPE_MSG);
        }

        Set<Long> sourceVendorIds = vendorRepository.findByAgreementVersionId(source.getId()).stream()
                .map(AgreementVendor::getVendorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> requestVendorIds = (request.vendorIds() != null ? request.vendorIds() : List.<Long>of()).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!sourceVendorIds.equals(requestVendorIds)) {
            throw new BusinessException(RENEW_IDENTITY_VENDOR_MSG);
        }
    }

    private AgreementVersion resolveNewVersionSource(Agreement parent) {
        Long agreementId = parent.getId();
        Integer maxVersion = agreementVersionRepository.findMaxVersionByAgreementId(agreementId);
        AgreementVersion latest = agreementVersionRepository
                .findByAgreementIdAndVersionNumber(agreementId, maxVersion)
                .orElseThrow(() -> new BusinessException("No agreement version exists for this agreement"));

        if (latest.getApprovalStatus() == ApprovalStatus.REJECTED) {
            return latest;
        }

        if (parent.getCurrentVersionId() == null) {
            throw new BusinessException("No active version exists to create a new version from");
        }

        AgreementVersion current = agreementVersionRepository.findById(parent.getCurrentVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", parent.getCurrentVersionId()));

        if (current.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new BusinessException("Can only create new version from an APPROVED or REJECTED agreement");
        }

        return current;
    }

    private AgreementVersion loadAndValidateOwnership(Long agreementVersionId, Long userId) {
        AgreementVersion version = agreementVersionRepository.findByIdWithAgreementOwner(agreementVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", agreementVersionId));
        agreementValidationService.validateAgreementOwnership(version.getAgreement(), userId);
        return version;
    }

    private void enforceDraftVisibility(AgreementVersion version, Long currentUserId) {
        if (version.getApprovalStatus() == ApprovalStatus.DRAFT
                && !version.getAgreement().getOwner().getId().equals(currentUserId)) {
            throw new ResourceNotFoundException("AgreementVersion", version.getId());
        }
    }

    private void enforceAgreementDraftVisibility(Agreement parent, Long currentUserId) {
        List<AgreementVersion> latestBatch = agreementVersionRepository
                .findLatestVersionsForAgreementIds(List.of(parent.getId()));
        AgreementVersion latest = latestBatch.isEmpty() ? null : latestBatch.get(0);
        if (latest != null
                && latest.getApprovalStatus() == ApprovalStatus.DRAFT
                && !parent.getOwner().getId().equals(currentUserId)
                && parent.getCurrentVersionId() == null) {
            throw new ResourceNotFoundException("Agreement", parent.getId());
        }
    }

    private static final ProductRulesPayload EMPTY_PRODUCT_RULES = new ProductRulesPayload(List.of());

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

    /**
     * Per-Store schedule hard limit: sum(periodMonths) must not exceed
     * ceil(inclusiveDays / 30.44). Skips when dates missing or sum &lt;= 0.
     */
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

    private IncompleteAgreementException validationFailure(String agreementName, String reason) {
        return new IncompleteAgreementException(
                "Validation failed for '" + agreementName + "': " + reason);
    }

    private AgreementVersion applySubmitForApproval(AgreementVersion version, String comments, Long userId) {
        if (version.getApprovalStatus() != ApprovalStatus.DRAFT) {
            throw new BusinessException("Only DRAFT agreements can be submitted for approval");
        }
        agreementValidationService.validateCompleteAgreement(version);
        return commitDraftSubmit(version, comments, userId);
    }

    /** Status transition only — caller must validate completeness first. */
    private AgreementVersion commitDraftSubmit(AgreementVersion version, String comments, Long userId) {
        if (version.getApprovalStatus() != ApprovalStatus.DRAFT) {
            throw new BusinessException("Only DRAFT agreements can be submitted for approval");
        }

        ApprovalStatus before = version.getApprovalStatus();
        version.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        version.setUpdatedByUserId(userId);
        version = agreementVersionRepository.save(version);

        String remarks = comments != null && !comments.isBlank() ? comments.trim() : null;
        recordApproval(version, ApprovalAction.SUBMITTED, remarks, before, ApprovalStatus.PENDING_APPROVAL, userId);
        recordAudit(version.getAgreement().getId(), version.getId(), "SUBMITTED_FOR_APPROVAL",
                before.name(), ApprovalStatus.PENDING_APPROVAL.name(), userId);

        return version;
    }

    private void recordApproval(AgreementVersion version, ApprovalAction action, String remarks,
            ApprovalStatus before, ApprovalStatus after, Long userId) {
        AgreementApproval approval = AgreementApproval.builder()
                .agreementVersion(version)
                .action(action)
                .remarks(remarks)
                .approvalStatusBefore(before)
                .approvalStatusAfter(after)
                .build();
        approval.setCreatedByUserId(userId);
        approvalRepository.save(approval);
    }

    private void recordAudit(Long agreementId, Long agreementVersionId, String action,
            String oldVal, String newVal, Long userId) {
        AgreementAudit audit = AgreementAudit.builder()
                .agreementId(agreementId)
                .agreementVersionId(agreementVersionId)
                .entityType("Agreement")
                .action(action)
                .oldValueJson(oldVal)
                .newValueJson(newVal)
                .createdByUserId(userId)
                .build();
        auditRepository.save(audit);
    }

    private String buildAdminTransferAuditNote(Long newOwnerUserId, String comments) {
        StringBuilder note = new StringBuilder("Admin override | newOwner=").append(newOwnerUserId);
        if (comments != null && !comments.isBlank()) {
            note.append(" | reason=").append(comments.trim());
        }
        return note.toString();
    }

    private void applyOwnershipTransfer(Agreement agreement, User newOwner, Long performedByUserId,
            String auditNewValue, Long fromOwnerId) {
        agreement.setOwner(newOwner);
        agreement.setUpdatedByUserId(performedByUserId);
        agreementRepository.save(agreement);

        AgreementVersion operationalVersion = resolveOperationalVersion(agreement);
        if (operationalVersion != null) {
            operationalVersion.setOwner(newOwner);
            operationalVersion.setUpdatedByUserId(performedByUserId);
            agreementVersionRepository.save(operationalVersion);
            recordAudit(agreement.getId(), operationalVersion.getId(), "OWNERSHIP_TRANSFERRED",
                    String.valueOf(fromOwnerId), auditNewValue, performedByUserId);
        }
    }

    private AgreementVersion resolveOperationalVersion(Agreement agreement) {
        if (agreement.getCurrentVersionId() != null) {
            return agreementVersionRepository.findById(agreement.getCurrentVersionId()).orElse(null);
        }
        return agreementVersionRepository.findByAgreementId(agreement.getId()).stream()
                .filter(v -> v.getApprovalStatus() == ApprovalStatus.DRAFT
                        || v.getApprovalStatus() == ApprovalStatus.PENDING_APPROVAL)
                .max((a, b) -> Integer.compare(a.getVersionNumber(), b.getVersionNumber()))
                .orElse(null);
    }

    private boolean isAgreementOperationallyActive(Agreement agreement) {
        AgreementVersion operationalVersion = resolveOperationalVersion(agreement);
        return operationalVersion == null || operationalVersion.getTerminationDate() == null;
    }

    // Location helpers removed — locations are now persisted in agreement_locations
    // table

    private AgreementGroup resolveAgreementGroup(Long groupId, String newName, Long userId) {
        AgreementGroupResponse response = agreementGroupService.resolveOrCreate(groupId, newName, userId);
        return agreementGroupRepository.findById(response.id())
                .orElseThrow(() -> new ResourceNotFoundException("AgreementGroup", response.id()));
    }
}
