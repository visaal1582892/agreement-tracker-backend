package com.medplus.agreement_tracker_backend.service.impl;

// import com.fasterxml.jackson.core.JsonProcessingException;
// import com.fasterxml.jackson.core.type.TypeReference;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.medplus.agreement_tracker_backend.dto.common.PartnerLocationItemDto;
// import com.medplus.agreement_tracker_backend.enums.GeographyMode;
import com.medplus.agreement_tracker_backend.constants.IncomeTypeNames;
import com.medplus.agreement_tracker_backend.dto.request.AgreementDocumentDTO;
// import com.medplus.agreement_tracker_backend.dto.request.CreateAgreementRequest;
import com.medplus.agreement_tracker_backend.dto.request.DraftAgreementItemRequest;
import com.medplus.agreement_tracker_backend.dto.request.AssetPayoutPeriodDto;
import com.medplus.agreement_tracker_backend.dto.request.DraftAssetPayload;
import com.medplus.agreement_tracker_backend.dto.request.DraftCommercialsPayload;
import com.medplus.agreement_tracker_backend.dto.request.DraftDetailsPayload;
// import com.medplus.agreement_tracker_backend.dto.request.AgreementRevisionSubmitRequest;
// import com.medplus.agreement_tracker_backend.dto.request.CommercialDataPayload;
// import com.medplus.agreement_tracker_backend.dto.request.EditAgreementRequest;
// import com.medplus.agreement_tracker_backend.dto.request.JbpConfigurationBlockDto;
// import com.medplus.agreement_tracker_backend.dto.request.JbpWorkbookRequest;
import com.medplus.agreement_tracker_backend.dto.request.ProductRuleDTO;
import com.medplus.agreement_tracker_backend.dto.request.ProductRulesPayload;
import com.medplus.agreement_tracker_backend.dto.request.ProductScopeCombinationDto;
import com.medplus.agreement_tracker_backend.dto.request.RuleDTO;
// import com.medplus.agreement_tracker_backend.dto.request.TerminateAgreementRequest;
import com.medplus.agreement_tracker_backend.dto.request.UpdateDraftRequest;
import com.medplus.agreement_tracker_backend.dto.request.VendorSnapshotPayload;
// import com.medplus.agreement_tracker_backend.dto.response.AgreementResponse;
// import com.medplus.agreement_tracker_backend.dto.response.AgreementVersionResponse;
// import com.medplus.agreement_tracker_backend.dto.response.ApprovalTimelineResponse;
// import com.medplus.agreement_tracker_backend.dto.response.BulkAgreementCreateResponse;
// import com.medplus.agreement_tracker_backend.dto.response.BulkGroupSubmitResponse;
// import com.medplus.agreement_tracker_backend.dto.response.JbpStagedWorkbookDto;
// import com.medplus.agreement_tracker_backend.dto.response.AgreementGroupResponse;
// import com.medplus.agreement_tracker_backend.dto.response.PendingActionRequestInfo;
import com.medplus.agreement_tracker_backend.entity.Agreement;
// import com.medplus.agreement_tracker_backend.entity.AgreementActionRequest;
// import com.medplus.agreement_tracker_backend.entity.AgreementApproval;
import com.medplus.agreement_tracker_backend.entity.AgreementAssetPayoutPeriod;
// import com.medplus.agreement_tracker_backend.entity.AgreementAudit;
// import com.medplus.agreement_tracker_backend.entity.AgreementComputedProduct;
import com.medplus.agreement_tracker_backend.entity.AgreementDivisionRule;
import com.medplus.agreement_tracker_backend.entity.AgreementDocument;
// import com.medplus.agreement_tracker_backend.entity.AgreementJbpCommercialPeriod;
// import com.medplus.agreement_tracker_backend.entity.AgreementJbpConfiguration;
import com.medplus.agreement_tracker_backend.entity.AgreementManufacturer;
import com.medplus.agreement_tracker_backend.entity.AgreementProductRule;
// import com.medplus.agreement_tracker_backend.entity.AgreementSlab;
// import com.medplus.agreement_tracker_backend.entity.AgreementTimePeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementType;
import com.medplus.agreement_tracker_backend.entity.AgreementVendor;
import com.medplus.agreement_tracker_backend.entity.AgreementLocation;
import com.medplus.agreement_tracker_backend.entity.AgreementStoreMapping;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.entity.AgreementGroup;
import com.medplus.agreement_tracker_backend.entity.IncomeType;
import com.medplus.agreement_tracker_backend.entity.User;
// import com.medplus.agreement_tracker_backend.enums.ActionRequestStatus;
import com.medplus.agreement_tracker_backend.enums.AdHocSubType;
// import com.medplus.agreement_tracker_backend.enums.ApprovalAction;
import com.medplus.agreement_tracker_backend.enums.ApprovalStatus;
// import com.medplus.agreement_tracker_backend.enums.RevisionType;
import com.medplus.agreement_tracker_backend.enums.AssetCategory;
import com.medplus.agreement_tracker_backend.enums.CalculationBasis;
import com.medplus.agreement_tracker_backend.enums.CommercialStructure;
import com.medplus.agreement_tracker_backend.enums.DocumentType;
import com.medplus.agreement_tracker_backend.enums.LeadTimeBasis;
import com.medplus.agreement_tracker_backend.enums.PaymentRealizationType;
import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
// import com.medplus.agreement_tracker_backend.enums.ProductScopeComputeStatus;
import com.medplus.agreement_tracker_backend.enums.RuleType;
import com.medplus.agreement_tracker_backend.enums.SlabValueType;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
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
import com.medplus.agreement_tracker_backend.repository.AgreementStoreMappingRepository;
// import com.medplus.agreement_tracker_backend.repository.AgreementTimePeriodRepository;
// import com.medplus.agreement_tracker_backend.integration.ProductMasterIntegrationService;
// import com.medplus.agreement_tracker_backend.integration.dto.IntegrationManufacturerResponse;
// import com.medplus.agreement_tracker_backend.integration.dto.IntegrationProductResponse;
import com.medplus.agreement_tracker_backend.repository.AgreementRepository;
// import com.medplus.agreement_tracker_backend.repository.AgreementSpec;
import com.medplus.agreement_tracker_backend.repository.AgreementTypeRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVendorRepository;
// import com.medplus.agreement_tracker_backend.repository.AgreementVersionRepository;
// import com.medplus.agreement_tracker_backend.repository.AgreementGroupRepository;
import com.medplus.agreement_tracker_backend.repository.IncomeTypeRepository;
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
// import java.util.HashMap;
// import java.util.HashSet;
// import java.util.LinkedHashMap;
// import java.util.LinkedHashSet;
import java.util.List;
// import java.util.Map;
// import java.util.Objects;
// import java.util.Set;
// import java.util.function.Function;
// import java.util.stream.Collectors;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

// import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgreementDraftMutationService {
    public static final java.time.format.DateTimeFormatter AGREEMENT_NAME_DATE_FORMAT = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
    public static final String DRAFT_AGREEMENT_NAME_PLACEHOLDER = "Draft - Pending Details";
    public static final com.medplus.agreement_tracker_backend.dto.request.ProductRulesPayload EMPTY_PRODUCT_RULES = new com.medplus.agreement_tracker_backend.dto.request.ProductRulesPayload(
            java.util.List.of());
    private final AgreementRepository agreementRepository;
    // private final AgreementVersionRepository agreementVersionRepository;
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
    private final AgreementStoreMappingRepository storeMappingRepository;
    // private final StoreMappingService storeMappingService;
    // private final AgreementTimePeriodRepository timePeriodRepository;
    private final AgreementLocationRepository agreementLocationRepository;
    // private final UserRepository userRepository;
    private final IncomeTypeRepository incomeTypeRepository;
    private final AgreementTypeRepository agreementTypeRepository;
    // private final ProductMasterIntegrationService
    // productMasterIntegrationService;
    private final AgreementProductScopeComputeService agreementProductScopeComputeService;
    // private final AgreementStatusResolver statusResolver;
    // private final TransactionTemplate groupSubmitTransactionTemplate;
    // private final Validator validator;
    // private final AgreementCloneService agreementCloneService;
    // private final AgreementValidationService agreementValidationService;
    // private final AgreementMapperService agreementMapperService;

    public void applyDraftFields(AgreementVersion version, DraftDetailsPayload details,
            DraftCommercialsPayload commercials) {
        if (details != null) {
            if (details.startDate() != null && details.expiryDate() != null
                    && details.expiryDate().isBefore(details.startDate())) {
                throw new BusinessException("Expiry date must be on or after start date");
            }
            if (details.incomeTypeId() != null) {
                version.setIncomeType(incomeTypeRepository.findById(details.incomeTypeId())
                        .orElseThrow(() -> new ResourceNotFoundException("IncomeType", details.incomeTypeId())));
            }
            if (details.agreementTypeId() != null) {
                version.setAgreementType(agreementTypeRepository.findById(details.agreementTypeId())
                        .orElseThrow(() -> new ResourceNotFoundException("AgreementType", details.agreementTypeId())));
            }
            if (details.startDate() != null) {
                version.setStartDate(details.startDate());
            }
            if (details.expiryDate() != null) {
                version.setExpiryDate(details.expiryDate());
            }
            if (details.notes() != null) {
                version.setNotes(details.notes());
            }
            if (details.adhocSubType() != null) {
                version.setAdhocSubType(AdHocSubType.valueOf(details.adhocSubType()));
            }
            if (details.quantityCap() != null) {
                version.setQuantityCap(details.quantityCap());
            }
            if (details.invoiceVendorId() != null) {
                version.setInvoiceVendorId(details.invoiceVendorId());
                version.setInvoiceVendorNameSnapshot(details.invoiceVendorNameSnapshot());
            }
            if (details.paymentRealizationType() != null) {
                PaymentRealizationType paymentType = PaymentRealizationType.valueOf(details.paymentRealizationType());
                version.setPaymentRealizationType(paymentType);
                if (paymentType == PaymentRealizationType.INVOICE_DISCOUNT) {
                    version.setPayoutBufferDays(null);
                    version.setLeadTimeBasis(null);
                    version.setInvoiceGenerationLeadTime(null);
                } else if (paymentType == PaymentRealizationType.CREDIT_NOTE) {
                    version.setLeadTimeBasis(null);
                    version.setInvoiceGenerationLeadTime(null);
                }
            }
            if (details.payoutBufferDays() != null) {
                version.setPayoutBufferDays(details.payoutBufferDays());
            } else if (details.paymentRealizationType() != null
                    && PaymentRealizationType.INVOICE_DISCOUNT.name().equals(details.paymentRealizationType())) {
                version.setPayoutBufferDays(null);
            }
            if (details.leadTimeBasis() != null) {
                version.setLeadTimeBasis(LeadTimeBasis.valueOf(details.leadTimeBasis()));
            } else if (details.paymentRealizationType() != null
                    && !PaymentRealizationType.DIRECT_PAYMENT_INVOICE.name().equals(details.paymentRealizationType())) {
                version.setLeadTimeBasis(null);
            }
            if (details.invoiceGenerationLeadTime() != null) {
                version.setInvoiceGenerationLeadTime(details.invoiceGenerationLeadTime());
            } else if (details.paymentRealizationType() != null
                    && !PaymentRealizationType.DIRECT_PAYMENT_INVOICE.name().equals(details.paymentRealizationType())) {
                version.setInvoiceGenerationLeadTime(null);
            } else if (details.leadTimeBasis() != null
                    && !LeadTimeBasis.INVOICE_DATE.name().equals(details.leadTimeBasis())) {
                version.setInvoiceGenerationLeadTime(null);
            }
            if (details.calculationBasis() != null) {
                version.setCalculationBasis(CalculationBasis.valueOf(details.calculationBasis()));
            }
        }
        Long incomeTypeId = details != null && details.incomeTypeId() != null
                ? details.incomeTypeId()
                : version.getIncomeType() != null ? version.getIncomeType().getId() : null;
        boolean assetRental = isAssetRentalIncomeType(incomeTypeId);
        if (commercials != null && !assetRental) {
            CommercialStructure incomingStructure = commercials.commercialStructure();
            if (incomingStructure != null) {
                version.setCommercialStructure(incomingStructure);
                if (incomingStructure == CommercialStructure.FLAT) {
                    if (commercials.commercialValue() != null) {
                        version.setCommercialValue(commercials.commercialValue());
                    }
                } else if (incomingStructure == CommercialStructure.SLAB) {
                    version.setCommercialValue(null);
                }
            } else if (commercials.commercialValue() != null) {
                version.setCommercialValue(commercials.commercialValue());
            }
            if (commercials.flatValueType() != null) {
                version.setFlatValueType(commercials.flatValueType());
            }
            if (commercials.flatBaselineFrequency() != null) {
                version.setFlatBaselineFrequency(commercials.flatBaselineFrequency());
            }
            if (commercials.financialYearStartMonth() != null) {
                version.setFinancialYearStartMonth(
                        com.medplus.agreement_tracker_backend.util.DynamicFinancialYearPeriodGenerator
                                .resolveStartMonth(commercials.financialYearStartMonth()));
            }
        } else if (assetRental) {
            version.setCommercialStructure(null);
            version.setCommercialValue(null);
            version.setFlatValueType(null);
            version.setFlatBaselineFrequency(null);
        }
    }

    public void replaceAsset(AgreementVersion version, DraftAssetPayload payload, Long userId) {
        if (!hasPersistableAssetPayload(payload)) {
            return;
        }

        version.setAssetCategory(payload.assetCategory());
        version.setAssetType(payload.assetCategory() == AssetCategory.ACTIVITY ? null
                : (payload.assetType() != null ? payload.assetType().trim() : null));

        boolean hasFlatPayout = payload.flatPayout() != null && payload.flatPayout().signum() > 0;
        boolean hasSchedule = payload.assetPayoutPeriods() != null && !payload.assetPayoutPeriods().isEmpty();

        if (hasFlatPayout) {
            version.setCommercialStructure(CommercialStructure.FLAT);
            version.setCommercialValue(payload.flatPayout());
            version.setFlatValueType(SlabValueType.FIXED);
            version.setFlatBaselineFrequency(PayoutFrequency.ONE_TIME);
            assetPayoutPeriodRepository.deleteByAgreementVersionId(version.getId());
        } else if (hasSchedule) {
            version.setCommercialStructure(CommercialStructure.PAYOUT_PER_STORE);
            version.setCommercialValue(null);
            version.setFlatValueType(null);
            version.setFlatBaselineFrequency(null);
            replaceAssetPayoutPeriods(version, payload.assetPayoutPeriods());
        } else {
            version.setCommercialStructure(null);
            version.setCommercialValue(null);
            version.setFlatValueType(null);
            version.setFlatBaselineFrequency(null);
            assetPayoutPeriodRepository.deleteByAgreementVersionId(version.getId());
        }

        replaceAssetStores(version, payload.stores());
    }

    public void replaceAssetStores(AgreementVersion version,
            List<com.medplus.agreement_tracker_backend.dto.request.AgreementStoreDto> stores) {
        if (stores == null) {
            return;
        }
        storeMappingRepository.deleteByAgreementVersionId(version.getId());
        if (stores.isEmpty()) {
            return;
        }
        List<AgreementStoreMapping> mappings = new java.util.ArrayList<>();
        for (com.medplus.agreement_tracker_backend.dto.request.AgreementStoreDto store : stores) {
            mappings.add(AgreementStoreMapping.builder()
                    .agreementVersion(version)
                    .storeId(store.getStoreId())
                    .name(store.getName())
                    .address(store.getAddress())
                    .pinCode(store.getPinCode())
                    .region1(store.getRegion1())
                    .region2(store.getRegion2())
                    .region3(store.getRegion3())
                    .isCustom(store.isCustom())
                    .build());
        }
        storeMappingRepository.saveAll(mappings);
    }

    public void replaceDocuments(AgreementVersion version,
            List<AgreementDocumentDTO> documentDtos,
            Long userId) {
        documentRepository.deleteByAgreementVersionId(version.getId());
        if (documentDtos == null || documentDtos.isEmpty()) {
            return;
        }

        List<AgreementDocument> documents = documentDtos.stream()
                .filter(dto -> dto != null && dto.fileUrl() != null && !dto.fileUrl().isBlank())
                .map(dto -> {
                    String fileUrl = dto.fileUrl().trim();
                    String resolvedFileName = resolveOriginalFileName(dto);
                    AgreementDocument document = AgreementDocument.builder()
                            .agreementVersion(version)
                            .fileUrl(fileUrl)
                            .filePath(fileUrl)
                            .fileName(resolvedFileName)
                            .originalFileName(resolvedFileName)
                            .thumbnailUrl(dto.thumbnailUrl())
                            .documentType(resolveDocumentType(dto.documentType()))
                            .isActive(true)
                            .build();
                    document.setCreatedByUserId(userId);
                    document.setUpdatedByUserId(userId);
                    return document;
                })
                .toList();

        if (!documents.isEmpty()) {
            documentRepository.saveAll(documents);
        }
    }

    public void saveRulesAndComputeProducts(AgreementVersion version, List<ProductScopeCombinationDto> combinations,
            Long userId) {
        if (combinations == null || combinations.isEmpty()) {
            computedProductRepository.deleteByAgreementVersionId(version.getId());
            return;
        }

        for (ProductScopeCombinationDto combo : combinations) {
            Long mfrId = combo.manufacturerId();

            // Save manufacturer
            AgreementManufacturer am = AgreementManufacturer.builder()
                    .agreementVersion(version).manufacturerId(mfrId).build();
            am.setCreatedByUserId(userId);
            manufacturerRuleRepository.save(am);

            // Save division rules
            List<RuleDTO> safeDivisionRules = combo.divisionRules() != null ? combo.divisionRules() : List.of();
            for (RuleDTO dr : safeDivisionRules) {
                AgreementDivisionRule adr = AgreementDivisionRule.builder()
                        .agreementVersion(version).divisionId(dr.id())
                        .ruleType(RuleType.valueOf(dr.ruleType()))
                        .manufacturerId(mfrId).build();
                adr.setCreatedByUserId(userId);
                divisionRuleRepository.save(adr);
            }

            // Save product rules
            List<ProductRuleDTO> safeProductRules = combo.productRules() != null ? combo.productRules() : List.of();
            for (ProductRuleDTO pr : safeProductRules) {
                AgreementProductRule apr = AgreementProductRule.builder()
                        .agreementVersion(version).productId(pr.id())
                        .ruleType(RuleType.valueOf(pr.ruleType()))
                        .manufacturerId(mfrId).build();
                apr.setCreatedByUserId(userId);
                productRuleRepository.save(apr);
            }
        }

        agreementProductScopeComputeService.computeAndLinkProducts(version.getId(), userId);
    }

    public void replaceRulesAndComputeProducts(AgreementVersion version, List<ProductScopeCombinationDto> combinations,
            Long userId) {
        manufacturerRuleRepository.deleteByAgreementVersionId(version.getId());
        divisionRuleRepository.deleteByAgreementVersionId(version.getId());
        productRuleRepository.deleteByAgreementVersionId(version.getId());
        computedProductRepository.deleteByAgreementVersionId(version.getId());
        saveRulesAndComputeProducts(version, combinations, userId);
    }

    public void replaceVendors(AgreementVersion version,
            List<VendorSnapshotPayload> vendors,
            List<Long> vendorIds,
            Long userId) {
        vendorRepository.deleteByAgreementVersionId(version.getId());
        List<VendorSnapshotPayload> snapshots = resolveVendorSnapshots(vendors, vendorIds);
        if (!snapshots.isEmpty()) {
            saveVendors(version, snapshots, userId);
        }
    }

    public void saveVendors(AgreementVersion version, List<VendorSnapshotPayload> vendors, Long userId) {
        for (VendorSnapshotPayload vendor : vendors) {
            if (vendor.vendorId() == null) {
                throw new BusinessException("Vendor id is required");
            }
            if (vendor.vendorName() == null || vendor.vendorName().isBlank()) {
                throw new BusinessException("Vendor name is required");
            }
            AgreementVendor av = AgreementVendor.builder()
                    .agreementVersion(version)
                    .vendorId(vendor.vendorId())
                    .vendorNameSnapshot(vendor.vendorName().trim())
                    .stateSnapshot(vendor.state())
                    .build();
            av.setCreatedByUserId(userId);
            vendorRepository.save(av);
        }
    }

    public AgreementVersion buildDraftVersion(DraftAgreementItemRequest item, User owner, Agreement parent,
            int versionNumber, Long userId) {
        DraftDetailsPayload details = item != null ? item.details() : null;
        DraftCommercialsPayload commercials = item != null ? item.commercials() : null;

        if (details != null && details.startDate() != null && details.expiryDate() != null
                && details.expiryDate().isBefore(details.startDate())) {
            throw new BusinessException("Expiry date must be on or after start date");
        }

        IncomeType incomeType = null;
        if (details != null && details.incomeTypeId() != null) {
            incomeType = incomeTypeRepository.findById(details.incomeTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("IncomeType", details.incomeTypeId()));
        }
        AgreementType agreementType = null;
        if (details != null && details.agreementTypeId() != null) {
            agreementType = agreementTypeRepository.findById(details.agreementTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("AgreementType", details.agreementTypeId()));
        }
        AgreementVersion version = AgreementVersion.builder()
                .agreement(parent)
                .versionNumber(versionNumber)
                .owner(owner)
                .incomeType(incomeType)
                .agreementType(agreementType)
                .commercialStructure(commercials != null ? commercials.commercialStructure() : null)
                .commercialValue(commercials != null ? commercials.commercialValue() : null)
                .startDate(details != null ? details.startDate() : null)
                .expiryDate(details != null ? details.expiryDate() : null)
                .approvalStatus(ApprovalStatus.DRAFT)
                .notes(details != null ? details.notes() : null)
                .build();
        version.setCreatedByUserId(userId);
        return version;
    }

    public void applyPartnerLocation(AgreementVersion version, DraftDetailsPayload details, Long userId) {
        if (details == null) {
            return;
        }
        agreementLocationRepository.deleteByAgreementVersionId(version.getId());
        List<com.medplus.agreement_tracker_backend.dto.common.AgreementLocationDto> locs = details.locations() != null
                ? details.locations()
                : List.of();
        for (com.medplus.agreement_tracker_backend.dto.common.AgreementLocationDto dto : locs) {
            if (dto.locationType() == null || dto.locationType().isBlank())
                continue;
            AgreementLocation loc = AgreementLocation.builder()
                    .agreementVersion(version)
                    .locationType(dto.locationType().trim().toUpperCase())
                    .countryCode(dto.countryCode())
                    .countryName(dto.countryName())
                    .countrySubName(dto.countrySubName())
                    .stateCode(dto.stateCode())
                    .stateName(dto.stateName())
                    .stateSubName(dto.stateSubName())
                    .cityCode(dto.cityCode())
                    .cityName(dto.cityName())
                    .citySubName(dto.citySubName())
                    .build();
            loc.setCreatedByUserId(userId);
            loc.setUpdatedByUserId(userId);
            agreementLocationRepository.save(loc);
        }
    }

    public void syncIncomeTypeSpecificData(AgreementVersion version,
            Long incomeTypeId,
            Long currentUserId,
            ProductRulesPayload productRulesPayload,
            DraftAssetPayload assetPayload,
            DraftDetailsPayload detailsPayload,
            boolean validateStep2) {
        if (isAssetRentalIncomeType(incomeTypeId)) {
            clearProductRulesForVersion(version);
            version.setAdhocSubType(null);
            version.setQuantityCap(null);
            if (shouldPersistAsset(assetPayload, validateStep2)) {
                replaceAsset(version, assetPayload, currentUserId);
            }
            return;
        }

        clearAssetForVersion(version);
        if (!isAdHocIncomeType(incomeTypeId)) {
            version.setAdhocSubType(null);
            version.setQuantityCap(null);
        } else if (detailsPayload != null && "QPS".equals(detailsPayload.adhocSubType())) {
            version.setQuantityCap(null);
        }

        if (productRulesPayload != null) {
            List<ProductScopeCombinationDto> combinations = productRulesPayload.combinations() != null
                    ? productRulesPayload.combinations()
                    : List.of();
            replaceRulesAndComputeProducts(version, combinations, currentUserId);
        }
    }

    public void clearDownstreamDraftData(AgreementVersion version, Agreement parent, Long userId) {
        vendorRepository.deleteByAgreementVersionId(version.getId());
        clearProductRulesForVersion(version);
        clearAssetForVersion(version);
        clearCommercialStructureForVersion(version.getId());
        documentRepository.deleteByAgreementVersionId(version.getId());

        agreementLocationRepository.deleteByAgreementVersionId(version.getId());
        parent.setUpdatedByUserId(userId);
        agreementRepository.save(parent);

        version.setInvoiceVendorId(null);
        version.setInvoiceVendorNameSnapshot(null);
        version.setPayoutBufferDays(null);
        version.setLeadTimeBasis(null);
        version.setInvoiceGenerationLeadTime(null);
        version.setAdhocSubType(null);
        version.setQuantityCap(null);
        version.setCommercialStructure(null);
        version.setCommercialValue(null);
        version.setFlatValueType(null);
        version.setFlatBaselineFrequency(null);
        version.setFinancialYearStartMonth(
                com.medplus.agreement_tracker_backend.util.DynamicFinancialYearPeriodGenerator.resolveStartMonth(null));
        version.setUpdatedByUserId(userId);
    }

    public void clearAssetForVersion(AgreementVersion version) {
        storeMappingRepository.deleteByAgreementVersionId(version.getId());
        assetPayoutPeriodRepository.deleteByAgreementVersionId(version.getId());
        version.setAssetCategory(null);
        version.setAssetType(null);
    }

    public void clearProductRulesForVersion(AgreementVersion version) {
        manufacturerRuleRepository.deleteByAgreementVersionId(version.getId());
        divisionRuleRepository.deleteByAgreementVersionId(version.getId());
        productRuleRepository.deleteByAgreementVersionId(version.getId());
        computedProductRepository.deleteByAgreementVersionId(version.getId());
    }

    public void clearCommercialStructureForVersion(Long agreementVersionId) {
        jbpCommercialPeriodRepository.deleteByAgreementVersionId(agreementVersionId);
        jbpConfigurationRepository.deleteByAgreementVersionId(agreementVersionId);
        slabRepository.deleteByAgreementVersionId(agreementVersionId);
    }

    public UpdateDraftRequest scrubRequestForIncomeType(UpdateDraftRequest request, Long incomeTypeId) {
        if (incomeTypeId == null) {
            return request;
        }
        if (isAssetRentalIncomeType(incomeTypeId)) {
            return new UpdateDraftRequest(
                    request.agreementName(),
                    List.of(),
                    List.of(),
                    EMPTY_PRODUCT_RULES,
                    scrubDetailsForAssetRental(request.details()),
                    null,
                    request.asset(),
                    request.requiresReapproval(),
                    request.commercialData());
        }
        if (isDataFeeIncomeType(incomeTypeId)) {
            return new UpdateDraftRequest(
                    request.agreementName(),
                    request.vendorIds(),
                    request.vendors(),
                    request.productRules(),
                    scrubDetailsForDataFee(request.details()),
                    request.commercials(),
                    null,
                    request.requiresReapproval(),
                    request.commercialData());
        }
        if (isCommercialContractsIncomeType(incomeTypeId)) {
            return new UpdateDraftRequest(
                    request.agreementName(),
                    request.vendorIds(),
                    request.vendors(),
                    request.productRules(),
                    scrubDetailsForStandardContract(request.details()),
                    request.commercials(),
                    null,
                    request.requiresReapproval(),
                    request.commercialData());
        }
        if (isAdHocIncomeType(incomeTypeId)) {
            return new UpdateDraftRequest(
                    request.agreementName(),
                    request.vendorIds(),
                    request.vendors(),
                    request.productRules(),
                    scrubDetailsForAdHoc(request.details()),
                    scrubCommercialsForAdHoc(request.commercials(), request.details()),
                    null,
                    request.requiresReapproval(),
                    request.commercialData());
        }
        return new UpdateDraftRequest(
                request.agreementName(),
                request.vendorIds(),
                request.vendors(),
                request.productRules(),
                scrubDetailsForStandardContract(request.details()),
                request.commercials(),
                null,
                request.requiresReapproval(),
                request.commercialData());
    }

    public DraftDetailsPayload scrubDetailsForAssetRental(DraftDetailsPayload details) {
        if (details == null) {
            return null;
        }
        return new DraftDetailsPayload(
                details.incomeTypeId(),
                details.agreementTypeId(),
                details.startDate(),
                details.expiryDate(),
                details.notes(),
                List.of(),
                null,
                null,
                details.invoiceVendorId(),
                details.invoiceVendorNameSnapshot(),
                details.payoutBufferDays(),
                details.leadTimeBasis(),
                details.invoiceGenerationLeadTime(),
                null,
                details.paymentRealizationType(),
                details.documents());
    }

    public DraftDetailsPayload scrubDetailsForDataFee(DraftDetailsPayload details) {
        if (details == null) {
            return null;
        }
        return new DraftDetailsPayload(
                details.incomeTypeId(),
                details.agreementTypeId(),
                details.startDate(),
                details.expiryDate(),
                details.notes(),
                details.locations(),
                null,
                null,
                details.invoiceVendorId(),
                details.invoiceVendorNameSnapshot(),
                details.payoutBufferDays(),
                details.leadTimeBasis(),
                details.invoiceGenerationLeadTime(),
                details.calculationBasis(),
                details.paymentRealizationType(),
                details.documents());
    }

    public DraftDetailsPayload scrubDetailsForStandardContract(DraftDetailsPayload details) {
        if (details == null) {
            return null;
        }
        return new DraftDetailsPayload(
                details.incomeTypeId(),
                details.agreementTypeId(),
                details.startDate(),
                details.expiryDate(),
                details.notes(),
                details.locations(),
                null,
                null,
                details.invoiceVendorId(),
                details.invoiceVendorNameSnapshot(),
                details.payoutBufferDays(),
                details.leadTimeBasis(),
                details.invoiceGenerationLeadTime(),
                details.calculationBasis(),
                details.paymentRealizationType(),
                details.documents());
    }

    public DraftDetailsPayload scrubDetailsForAdHoc(DraftDetailsPayload details) {
        if (details == null) {
            return null;
        }
        String resolvedSubType = details.adhocSubType();
        if (resolvedSubType == null || resolvedSubType.isBlank()
                || "CONSUMER_PRICE_OFF".equals(resolvedSubType)) {
            resolvedSubType = "QPS";
        }
        return new DraftDetailsPayload(
                details.incomeTypeId(),
                details.agreementTypeId(),
                details.startDate(),
                details.expiryDate(),
                details.notes(),
                details.locations(),
                resolvedSubType,
                null,
                details.invoiceVendorId(),
                details.invoiceVendorNameSnapshot(),
                details.payoutBufferDays(),
                details.leadTimeBasis(),
                details.invoiceGenerationLeadTime(),
                details.calculationBasis(),
                details.paymentRealizationType(),
                details.documents());
    }

    public DraftCommercialsPayload scrubCommercialsForFlatOnly(DraftCommercialsPayload commercials) {
        if (commercials == null) {
            return null;
        }
        return new DraftCommercialsPayload(
                CommercialStructure.FLAT,
                commercials.commercialValue(),
                commercials.flatValueType(),
                commercials.flatBaselineFrequency(),
                true,
                false,
                commercials.financialYearStartMonth());
    }

    public DraftCommercialsPayload scrubCommercialsForAdHoc(DraftCommercialsPayload commercials,
            DraftDetailsPayload details) {
        if (commercials == null) {
            return null;
        }
        if (details != null && "QPS".equals(details.adhocSubType())) {
            return new DraftCommercialsPayload(
                    commercials.commercialStructure(),
                    commercials.commercialValue(),
                    commercials.flatValueType(),
                    PayoutFrequency.ONE_TIME,
                    commercials.enableFlatBaseline(),
                    commercials.enableSlabIncentives(),
                    commercials.financialYearStartMonth());
        }
        return commercials;
    }

    public boolean isAdHocIncomeType(Long incomeTypeId) {
        if (incomeTypeId == null) {
            return false;
        }
        return incomeTypeRepository.findById(incomeTypeId)
                .map(incomeType -> IncomeTypeNames.AD_HOC_ACTIVITIES.equalsIgnoreCase(incomeType.getName()))
                .orElse(false);
    }

    public boolean isDataFeeIncomeType(Long incomeTypeId) {
        if (incomeTypeId == null) {
            return false;
        }
        return incomeTypeRepository.findById(incomeTypeId)
                .map(incomeType -> IncomeTypeNames.DATA_FEE.equalsIgnoreCase(incomeType.getName()))
                .orElse(false);
    }

    public boolean isCommercialContractsIncomeType(Long incomeTypeId) {
        if (incomeTypeId == null) {
            return false;
        }
        return incomeTypeRepository.findById(incomeTypeId)
                .map(incomeType -> IncomeTypeNames.COMMERCIAL_CONTRACTS.equalsIgnoreCase(incomeType.getName()))
                .orElse(false);
    }

    public boolean isAssetRentalIncomeType(Long incomeTypeId) {
        if (incomeTypeId == null) {
            return false;
        }
        return incomeTypeRepository.findById(incomeTypeId)
                .map(incomeType -> IncomeTypeNames.ASSET_RENTALS.equalsIgnoreCase(incomeType.getName()))
                .orElse(false);
    }

    public boolean hasPersistableAssetPayload(DraftAssetPayload payload) {
        if (payload == null || payload.assetCategory() == null) {
            return false;
        }
        if (payload.assetCategory() == AssetCategory.ACTIVITY) {
            return true;
        }
        return payload.assetType() != null && !payload.assetType().isBlank();
    }

    public boolean shouldPersistAsset(DraftAssetPayload payload, boolean validateStep2) {
        if (hasPersistableAssetPayload(payload)) {
            return true;
        }
        // Partial Step 1 save (validateStep2=false): defer asset row until Step 2
        // payload is complete.
        return false;
    }

    public void replaceAssetPayoutPeriods(AgreementVersion version, List<AssetPayoutPeriodDto> periods) {
        assetPayoutPeriodRepository.deleteByAgreementVersionId(version.getId());
        if (periods == null || periods.isEmpty()) {
            return;
        }
        for (AssetPayoutPeriodDto period : periods) {
            assetPayoutPeriodRepository.save(AgreementAssetPayoutPeriod.builder()
                    .agreementVersion(version)
                    .periodMonths(period.periodMonths())
                    .payoutPerStore(period.payoutPerStore())
                    .build());
        }
    }

    public DocumentType resolveDocumentType(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            return DocumentType.SUPPORTING_DOC;
        }
        try {
            return DocumentType.valueOf(documentType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DocumentType.OTHER;
        }
    }

    public List<VendorSnapshotPayload> resolveVendorSnapshots(List<VendorSnapshotPayload> vendors,
            List<Long> vendorIds) {
        if (vendors != null && !vendors.isEmpty()) {
            return vendors;
        }
        return List.of();
    }

    public void requirePartnerLocation(DraftDetailsPayload details, String incomeLabel) {
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

    public String resolveAgreementDisplayName(Agreement parent) {
        if (parent.getAgreementName() != null && !parent.getAgreementName().isBlank()) {
            return parent.getAgreementName();
        }
        return "Agreement #" + parent.getId();
    }

    public void regenerateAgreementName(Agreement parent, AgreementVersion version) {
        if (version.getIncomeType() == null || version.getStartDate() == null) {
            if (parent.getAgreementName() == null || parent.getAgreementName().isBlank()) {
                parent.setAgreementName(DRAFT_AGREEMENT_NAME_PLACEHOLDER);
            }
            return;
        }
        AgreementGroup cag = parent.getAgreementGroup();
        String generatedName = cag.getName()
                + " - " + version.getIncomeType().getName()
                + " - " + version.getStartDate().format(AGREEMENT_NAME_DATE_FORMAT);
        parent.setAgreementName(generatedName);
    }

    public void syncAgreementName(Agreement parent, AgreementVersion version, Long userId) {
        regenerateAgreementName(parent, version);
        parent.setUpdatedByUserId(userId);
        agreementRepository.save(parent);
    }

    public String resolveOriginalFileName(AgreementDocumentDTO dto) {
        if (dto.originalFileName() != null && !dto.originalFileName().isBlank()) {
            return dto.originalFileName().trim();
        }
        return "document";
    }

}
