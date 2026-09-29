package com.medplus.agreement_tracker_backend.service.impl;

// import com.fasterxml.jackson.core.JsonProcessingException;
// import com.fasterxml.jackson.core.type.TypeReference;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.medplus.agreement_tracker_backend.dto.common.PartnerLocationItemDto;
// import com.medplus.agreement_tracker_backend.enums.GeographyMode;
// import com.medplus.agreement_tracker_backend.constants.IncomeTypeNames;
// import com.medplus.agreement_tracker_backend.dto.request.AgreementDocumentDTO;
// import com.medplus.agreement_tracker_backend.dto.request.CreateAgreementRequest;
// import com.medplus.agreement_tracker_backend.dto.request.DraftAgreementItemRequest;
// import com.medplus.agreement_tracker_backend.dto.request.AssetPayoutPeriodDto;
// import com.medplus.agreement_tracker_backend.dto.request.DraftAssetPayload;
// import com.medplus.agreement_tracker_backend.dto.request.DraftCommercialsPayload;
// import com.medplus.agreement_tracker_backend.dto.request.DraftDetailsPayload;
// import com.medplus.agreement_tracker_backend.dto.request.AgreementRevisionSubmitRequest;
// import com.medplus.agreement_tracker_backend.dto.request.CommercialDataPayload;
// import com.medplus.agreement_tracker_backend.dto.request.EditAgreementRequest;
// import com.medplus.agreement_tracker_backend.dto.request.JbpConfigurationBlockDto;
// import com.medplus.agreement_tracker_backend.dto.request.JbpWorkbookRequest;
// import com.medplus.agreement_tracker_backend.dto.request.ProductRuleDTO;
// import com.medplus.agreement_tracker_backend.dto.request.ProductRulesPayload;
// import com.medplus.agreement_tracker_backend.dto.request.ProductScopeCombinationDto;
// import com.medplus.agreement_tracker_backend.dto.request.RuleDTO;
// import com.medplus.agreement_tracker_backend.dto.request.TerminateAgreementRequest;
// import com.medplus.agreement_tracker_backend.dto.request.UpdateDraftRequest;
// import com.medplus.agreement_tracker_backend.dto.request.VendorSnapshotPayload;
// import com.medplus.agreement_tracker_backend.dto.response.AgreementResponse;
// import com.medplus.agreement_tracker_backend.dto.response.AgreementVersionResponse;
// import com.medplus.agreement_tracker_backend.dto.response.ApprovalTimelineResponse;
// import com.medplus.agreement_tracker_backend.dto.response.BulkAgreementCreateResponse;
// import com.medplus.agreement_tracker_backend.dto.response.BulkGroupSubmitResponse;
// import com.medplus.agreement_tracker_backend.dto.response.JbpStagedWorkbookDto;
// import com.medplus.agreement_tracker_backend.dto.response.AgreementGroupResponse;
// import com.medplus.agreement_tracker_backend.dto.response.PendingActionRequestInfo;
// import com.medplus.agreement_tracker_backend.entity.Agreement;
// import com.medplus.agreement_tracker_backend.entity.AgreementActionRequest;
// import com.medplus.agreement_tracker_backend.entity.AgreementApproval;
import com.medplus.agreement_tracker_backend.entity.AgreementAssetPayoutPeriod;
// import com.medplus.agreement_tracker_backend.entity.AgreementAudit;
import com.medplus.agreement_tracker_backend.entity.AgreementComputedProduct;
import com.medplus.agreement_tracker_backend.entity.AgreementDivisionRule;
import com.medplus.agreement_tracker_backend.entity.AgreementDocument;
import com.medplus.agreement_tracker_backend.entity.AgreementJbpCommercialPeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementJbpConfiguration;
import com.medplus.agreement_tracker_backend.entity.AgreementManufacturer;
import com.medplus.agreement_tracker_backend.entity.AgreementProductRule;
import com.medplus.agreement_tracker_backend.entity.AgreementSlab;
// import com.medplus.agreement_tracker_backend.entity.AgreementTimePeriod;
// import com.medplus.agreement_tracker_backend.entity.AgreementType;
import com.medplus.agreement_tracker_backend.entity.AgreementVendor;
import com.medplus.agreement_tracker_backend.entity.AgreementLocation;
// import com.medplus.agreement_tracker_backend.entity.AgreementStoreMapping;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
// import com.medplus.agreement_tracker_backend.entity.AgreementGroup;
// import com.medplus.agreement_tracker_backend.entity.IncomeType;
// import com.medplus.agreement_tracker_backend.entity.User;
// import com.medplus.agreement_tracker_backend.enums.ActionRequestStatus;
// import com.medplus.agreement_tracker_backend.enums.AdHocSubType;
// import com.medplus.agreement_tracker_backend.enums.ApprovalAction;
// import com.medplus.agreement_tracker_backend.enums.ApprovalStatus;
// import com.medplus.agreement_tracker_backend.enums.RevisionType;
// import com.medplus.agreement_tracker_backend.enums.AssetCategory;
// import com.medplus.agreement_tracker_backend.enums.CalculationBasis;
import com.medplus.agreement_tracker_backend.enums.CommercialStructure;
// import com.medplus.agreement_tracker_backend.enums.DocumentType;
// import com.medplus.agreement_tracker_backend.enums.LeadTimeBasis;
// import com.medplus.agreement_tracker_backend.enums.PaymentRealizationType;
// import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
// import com.medplus.agreement_tracker_backend.enums.ProductScopeComputeStatus;
// import com.medplus.agreement_tracker_backend.enums.RuleType;
// import com.medplus.agreement_tracker_backend.enums.SlabValueType;
// import com.medplus.agreement_tracker_backend.exception.BusinessException;
// import com.medplus.agreement_tracker_backend.exception.ConflictValidationException;
// import com.medplus.agreement_tracker_backend.exception.IncompleteAgreementException;
import com.medplus.agreement_tracker_backend.exception.ResourceNotFoundException;
// import com.medplus.agreement_tracker_backend.exception.UnauthorizedException;
// import com.medplus.agreement_tracker_backend.repository.AgreementActionRequestRepository;
// import com.medplus.agreement_tracker_backend.repository.AgreementApprovalRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementAssetPayoutPeriodRepository;
// import com.medplus.agreement_tracker_backend.repository.AgreementAuditRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementComputedProductRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementDivisionRuleRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementJbpCommercialPeriodRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementJbpConfigurationRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementManufacturerRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementProductRuleRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementLocationRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementDocumentRepository;
// import com.medplus.agreement_tracker_backend.repository.AgreementReminderRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementSlabRepository;
// import com.medplus.agreement_tracker_backend.repository.AgreementStoreMappingRepository;
// import com.medplus.agreement_tracker_backend.repository.AgreementTimePeriodRepository;
// import com.medplus.agreement_tracker_backend.integration.ProductMasterIntegrationService;
// import com.medplus.agreement_tracker_backend.integration.dto.IntegrationManufacturerResponse;
// import com.medplus.agreement_tracker_backend.integration.dto.IntegrationProductResponse;
// import com.medplus.agreement_tracker_backend.repository.AgreementRepository;
// import com.medplus.agreement_tracker_backend.repository.AgreementSpec;
// import com.medplus.agreement_tracker_backend.repository.AgreementTypeRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVendorRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVersionRepository;
// import com.medplus.agreement_tracker_backend.repository.AgreementGroupRepository;
// import com.medplus.agreement_tracker_backend.repository.IncomeTypeRepository;
// import com.medplus.agreement_tracker_backend.repository.UserRepository;
// import com.medplus.agreement_tracker_backend.service.AgreementService;
// import com.medplus.agreement_tracker_backend.service.AgreementGroupService;
// import com.medplus.agreement_tracker_backend.service.StoreMappingService;
// import com.medplus.agreement_tracker_backend.util.AgreementStatusResolver;
// import com.medplus.agreement_tracker_backend.util.AssetPayoutDurationMath;
// import com.medplus.agreement_tracker_backend.util.TimePeriodDimensions;
// import com.medplus.agreement_tracker_backend.util.TimePeriodNameParser;
// import com.medplus.agreement_tracker_backend.validation.Step1Validation;
// import com.medplus.agreement_tracker_backend.validation.Step2Validation;
// import jakarta.validation.ConstraintViolationException;
// import jakarta.validation.Validator;
// import org.springframework.data.domain.Page;
// import org.springframework.data.domain.PageRequest;
// import org.springframework.data.domain.Pageable;
// import org.springframework.data.domain.Sort;
// import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
// import org.springframework.transaction.PlatformTransactionManager;
// import org.springframework.transaction.annotation.Transactional;
// import org.springframework.transaction.support.TransactionTemplate;
// import java.math.BigDecimal;
// import java.time.LocalDate;
// import java.time.LocalDateTime;
// import java.time.format.DateTimeFormatter;
// import java.time.temporal.ChronoUnit;
// import java.util.ArrayList;
// import java.util.Comparator;
import java.util.HashMap;
// import java.util.HashSet;
// import java.util.LinkedHashMap;
// import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
// import java.util.Objects;
// import java.util.Set;
// import java.util.function.Function;
// import java.util.stream.Collectors;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

// import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgreementCloneService {
    // private final AgreementRepository agreementRepository;
    private final AgreementVersionRepository agreementVersionRepository;
    // private final AgreementGroupRepository agreementGroupRepository;
    // private final AgreementGroupService agreementGroupService;
    private final AgreementVendorRepository vendorRepository;
    private final AgreementManufacturerRepository manufacturerRuleRepository;
    private final AgreementDivisionRuleRepository divisionRuleRepository;
    private final AgreementProductRuleRepository productRuleRepository;
    private final AgreementSlabRepository slabRepository;
    private final AgreementJbpCommercialPeriodRepository jbpCommercialPeriodRepository;
    private final AgreementJbpConfigurationRepository jbpConfigurationRepository;
    private final AgreementComputedProductRepository computedProductRepository;
    // private final AgreementApprovalRepository approvalRepository;
    // private final AgreementAuditRepository auditRepository;
    // private final AgreementActionRequestRepository actionRequestRepository;
    // private final AgreementReminderRepository reminderRepository;
    private final AgreementDocumentRepository documentRepository;
    private final AgreementAssetPayoutPeriodRepository assetPayoutPeriodRepository;
    // private final AgreementStoreMappingRepository storeMappingRepository;
    // private final StoreMappingService storeMappingService;
    // private final AgreementTimePeriodRepository timePeriodRepository;
    private final AgreementLocationRepository agreementLocationRepository;
    // private final UserRepository userRepository;
    // private final IncomeTypeRepository incomeTypeRepository;
    // private final AgreementTypeRepository agreementTypeRepository;
    // private final ProductMasterIntegrationService
    // productMasterIntegrationService;
    // private final AgreementProductScopeComputeService
    // agreementProductScopeComputeService;
    // private final AgreementStatusResolver statusResolver;
    // private final TransactionTemplate groupSubmitTransactionTemplate;
    // private final Validator validator;

    public void copyAssetPayoutPeriods(Long sourceVersionId, Long targetVersionId) {
        AgreementVersion target = agreementVersionRepository.findById(targetVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", targetVersionId));
        assetPayoutPeriodRepository.deleteByAgreementVersionId(targetVersionId);
        assetPayoutPeriodRepository.flush();

        List<AgreementAssetPayoutPeriod> sourcePeriods = assetPayoutPeriodRepository
                .findByAgreementVersionIdOrderByPeriodMonthsAscIdAsc(sourceVersionId);
        for (AgreementAssetPayoutPeriod sourcePeriod : sourcePeriods) {
            assetPayoutPeriodRepository.save(AgreementAssetPayoutPeriod.builder()
                    .agreementVersion(target)
                    .periodMonths(sourcePeriod.getPeriodMonths())
                    .payoutPerStore(sourcePeriod.getPayoutPerStore())
                    .build());
        }
    }

    public void maybeCopyAssetPayoutPeriods(Long sourceVersionId, Long targetVersionId) {
        List<AgreementAssetPayoutPeriod> existingTargetPeriods = assetPayoutPeriodRepository
                .findByAgreementVersionIdOrderByPeriodMonthsAscIdAsc(targetVersionId);
        if (!existingTargetPeriods.isEmpty()) {
            return;
        }
        AgreementVersion target = agreementVersionRepository.findById(targetVersionId).orElse(null);
        if (target != null && target.getCommercialStructure() == CommercialStructure.FLAT) {
            return;
        }
        copyAssetPayoutPeriods(sourceVersionId, targetVersionId);
    }

    public void copyDocuments(Long sourceVersionId, Long targetVersionId, Long currentUserId) {
        AgreementVersion target = agreementVersionRepository.findById(targetVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", targetVersionId));
        documentRepository.deleteByAgreementVersionId(targetVersionId);

        List<AgreementDocument> sourceDocuments = documentRepository
                .findByAgreementVersionIdAndIsActiveTrue(sourceVersionId);

        List<AgreementDocument> clonedDocs = sourceDocuments.stream().map(doc -> {
            AgreementDocument newDoc = AgreementDocument.builder()
                    .agreementVersion(target)
                    .fileUrl(doc.getFileUrl())
                    .originalFileName(doc.getOriginalFileName())
                    .documentType(doc.getDocumentType())
                    .isActive(true)
                    .build();
            newDoc.setCreatedByUserId(currentUserId);
            newDoc.setUpdatedByUserId(currentUserId);
            return newDoc;
        }).toList();

        if (!clonedDocs.isEmpty()) {
            documentRepository.saveAll(clonedDocs);
        }
    }

    public void copyJbpCommercialPeriods(
            Long sourceVersionId,
            AgreementVersion target,
            Map<Long, Long> configurationIdMap) {
        if (configurationIdMap.isEmpty()) {
            return;
        }
        jbpCommercialPeriodRepository.findByAgreementVersionId(sourceVersionId).forEach(sourcePeriod -> {
            Long newConfigurationId = configurationIdMap.get(sourcePeriod.getJbpConfiguration().getId());
            if (newConfigurationId == null) {
                return;
            }
            AgreementJbpConfiguration targetConfiguration = jbpConfigurationRepository.findById(newConfigurationId)
                    .orElseThrow(() -> new ResourceNotFoundException("AgreementJbpConfiguration", newConfigurationId));
            AgreementJbpCommercialPeriod copy = AgreementJbpCommercialPeriod.builder()
                    .agreementVersion(target)
                    .jbpConfiguration(targetConfiguration)
                    .targetType(sourcePeriod.getTargetType())
                    .target(sourcePeriod.getTarget())
                    .qualifierPercent(sourcePeriod.getQualifierPercent())
                    .payoutType(sourcePeriod.getPayoutType())
                    .payout(sourcePeriod.getPayout())
                    .maxPurchase(sourcePeriod.getMaxPurchase())
                    .maxPayout(sourcePeriod.getMaxPayout())
                    .slabTierNumber(sourcePeriod.getSlabTierNumber())
                    .timePeriod(sourcePeriod.getTimePeriod())
                    .parentTimePeriod(sourcePeriod.getParentTimePeriod())
                    .build();
            jbpCommercialPeriodRepository.save(copy);
        });
    }

    public void copyVendors(Long sourceVersionId, AgreementVersion target, Long userId) {
        vendorRepository.findByAgreementVersionId(sourceVersionId).forEach(v -> {
            AgreementVendor copy = AgreementVendor.builder()
                    .agreementVersion(target)
                    .vendorId(v.getVendorId())
                    .vendorNameSnapshot(v.getVendorNameSnapshot())
                    .stateSnapshot(v.getStateSnapshot())
                    .build();
            copy.setCreatedByUserId(userId);
            vendorRepository.save(copy);
        });
    }

    public void copyRulesAndComputed(Long sourceVersionId, AgreementVersion target, Long userId) {
        List<AgreementManufacturer> oldMfrs = manufacturerRuleRepository.findByAgreementVersionId(sourceVersionId);
        Long fallbackMfrId = oldMfrs.isEmpty() ? null : oldMfrs.get(0).getManufacturerId();

        oldMfrs.forEach(m -> {
            AgreementManufacturer copy = AgreementManufacturer.builder()
                    .agreementVersion(target).manufacturerId(m.getManufacturerId()).build();
            copy.setCreatedByUserId(userId);
            manufacturerRuleRepository.save(copy);
        });

        divisionRuleRepository.findByAgreementVersionId(sourceVersionId).forEach(dr -> {
            Long mfrId = dr.getManufacturerId() != null ? dr.getManufacturerId() : fallbackMfrId;
            AgreementDivisionRule copy = AgreementDivisionRule.builder()
                    .agreementVersion(target).divisionId(dr.getDivisionId())
                    .ruleType(dr.getRuleType())
                    .manufacturerId(mfrId).build();
            copy.setCreatedByUserId(userId);
            divisionRuleRepository.save(copy);
        });

        productRuleRepository.findByAgreementVersionId(sourceVersionId).forEach(pr -> {
            Long mfrId = pr.getManufacturerId() != null ? pr.getManufacturerId() : fallbackMfrId;
            AgreementProductRule copy = AgreementProductRule.builder()
                    .agreementVersion(target).productId(pr.getProductId())
                    .ruleType(pr.getRuleType())
                    .manufacturerId(mfrId).build();
            copy.setCreatedByUserId(userId);
            productRuleRepository.save(copy);
        });

        computedProductRepository.findByAgreementVersionId(sourceVersionId).forEach(cp -> {
            AgreementComputedProduct copy = AgreementComputedProduct.builder()
                    .agreementVersion(target)
                    .productId(cp.getProductId())
                    .productNameSnapshot(cp.getProductNameSnapshot())
                    .divisionNameSnapshot(cp.getDivisionNameSnapshot())
                    .manufacturerNameSnapshot(cp.getManufacturerNameSnapshot())
                    .manufacturerId(cp.getManufacturerId())
                    .divisionId(cp.getDivisionId())
                    .build();
            copy.setCreatedByUserId(userId);
            computedProductRepository.save(copy);
        });
    }

    public void copyPartnerLocation(AgreementVersion sourceVersion, AgreementVersion targetVersion, Long userId) {
        List<AgreementLocation> sourceLocs = agreementLocationRepository
                .findByAgreementVersionId(sourceVersion.getId());
        for (AgreementLocation src : sourceLocs) {
            AgreementLocation copy = AgreementLocation.builder()
                    .agreementVersion(targetVersion)
                    .locationType(src.getLocationType())
                    .countryCode(src.getCountryCode())
                    .countryName(src.getCountryName())
                    .countrySubName(src.getCountrySubName())
                    .stateCode(src.getStateCode())
                    .stateName(src.getStateName())
                    .stateSubName(src.getStateSubName())
                    .cityCode(src.getCityCode())
                    .cityName(src.getCityName())
                    .citySubName(src.getCitySubName())
                    .build();
            copy.setCreatedByUserId(userId);
            copy.setUpdatedByUserId(userId);
            agreementLocationRepository.save(copy);
        }
    }

    public Map<Long, Long> copySlabs(Long sourceVersionId, AgreementVersion target, Long userId) {
        Map<Long, Long> idMap = new HashMap<>();
        slabRepository.findByAgreementVersionIdOrderByMinCapAsc(sourceVersionId).forEach(s -> {
            AgreementSlab copy = AgreementSlab.builder()
                    .agreementVersion(target)
                    .slabType(s.getSlabType())
                    .minCap(s.getMinCap())
                    .maxCap(s.getMaxCap())
                    .capUnit(s.getCapUnit())
                    .valueType(s.getValueType())
                    .commercialValue(s.getCommercialValue())
                    .payoutFrequency(s.getPayoutFrequency())
                    .build();
            copy.setCreatedByUserId(userId);
            copy = slabRepository.save(copy);
            idMap.put(s.getId(), copy.getId());
        });
        return idMap;
    }

    public Map<Long, Long> copyJbpConfigurations(Long sourceVersionId, AgreementVersion target) {
        Map<Long, Long> idMap = new HashMap<>();
        jbpConfigurationRepository.findHydratedByAgreementVersionId(sourceVersionId).forEach(source -> {
            AgreementJbpConfiguration copy = AgreementJbpConfiguration.builder()
                    .agreementVersion(target)
                    .slabCount(source.getSlabCount())
                    .build();
            copy = jbpConfigurationRepository.save(copy);
            idMap.put(source.getId(), copy.getId());
        });
        return idMap;
    }

}
