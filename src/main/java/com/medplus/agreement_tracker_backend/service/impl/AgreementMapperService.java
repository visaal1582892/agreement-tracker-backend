package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.constants.IncomeTypeNames;
import com.medplus.agreement_tracker_backend.dto.response.AgreementResponse;
import com.medplus.agreement_tracker_backend.dto.response.AgreementVersionResponse;
import com.medplus.agreement_tracker_backend.dto.response.AgreementVersionSummaryResponse;

import com.medplus.agreement_tracker_backend.dto.response.ApprovalTimelineResponse;
import com.medplus.agreement_tracker_backend.dto.response.PendingActionRequestInfo;
import com.medplus.agreement_tracker_backend.entity.Agreement;
import com.medplus.agreement_tracker_backend.entity.AgreementActionRequest;
import com.medplus.agreement_tracker_backend.entity.AgreementComputedProduct;
import com.medplus.agreement_tracker_backend.entity.AgreementDivisionRule;
import com.medplus.agreement_tracker_backend.entity.AgreementManufacturer;
import com.medplus.agreement_tracker_backend.entity.AgreementProductRule;
import com.medplus.agreement_tracker_backend.entity.AgreementType;
import com.medplus.agreement_tracker_backend.entity.AgreementVendor;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.entity.AgreementGroup;
import com.medplus.agreement_tracker_backend.entity.User;
import com.medplus.agreement_tracker_backend.enums.ActionRequestStatus;
import com.medplus.agreement_tracker_backend.enums.ApprovalStatus;
import com.medplus.agreement_tracker_backend.enums.CommercialStructure;
import com.medplus.agreement_tracker_backend.repository.AgreementActionRequestRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementAssetPayoutPeriodRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementComputedProductRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementDivisionRuleRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementJbpCommercialPeriodRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementManufacturerRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementProductRuleRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementLocationRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementDocumentRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementStoreMappingRepository;
import com.medplus.agreement_tracker_backend.integration.ProductMasterIntegrationService;
import com.medplus.agreement_tracker_backend.integration.dto.IntegrationManufacturerResponse;
import com.medplus.agreement_tracker_backend.integration.dto.IntegrationProductResponse;
import com.medplus.agreement_tracker_backend.repository.AgreementVendorRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVersionRepository;
import com.medplus.agreement_tracker_backend.repository.IncomeTypeRepository;
import com.medplus.agreement_tracker_backend.repository.UserRepository;
import com.medplus.agreement_tracker_backend.util.AgreementStatusResolver;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgreementMapperService {
        private final AgreementVersionRepository agreementVersionRepository;
        private final AgreementVendorRepository vendorRepository;
        private final AgreementManufacturerRepository manufacturerRuleRepository;
        private final AgreementDivisionRuleRepository divisionRuleRepository;
        private final AgreementProductRuleRepository productRuleRepository;
        private final AgreementJbpCommercialPeriodRepository jbpCommercialPeriodRepository;
        private final AgreementComputedProductRepository computedProductRepository;
        private final AgreementActionRequestRepository actionRequestRepository;
        private final AgreementDocumentRepository documentRepository;
        private final AgreementAssetPayoutPeriodRepository assetPayoutPeriodRepository;
        private final AgreementStoreMappingRepository storeMappingRepository;
        private final AgreementLocationRepository agreementLocationRepository;
        private final UserRepository userRepository;
        private final IncomeTypeRepository incomeTypeRepository;
        private final ProductMasterIntegrationService productMasterIntegrationService;
        private final AgreementStatusResolver statusResolver;

        // private final TransactionTemplate groupSubmitTransactionTemplate;
        
    public AgreementVersionSummaryResponse toVersionSummaryResponse(AgreementVersion version) {
        return AgreementVersionSummaryResponse.builder()
                .id(version.getId())
                .agreementId(version.getAgreement().getId())
                .agreementName(version.getAgreement().getAgreementName())
                .versionNumber(version.getVersionNumber())
                .revisionType(version.getRevisionType())
                .ownerId(version.getOwner().getId())
                .ownerName(version.getOwner().getFullName())
                .startDate(version.getStartDate())
                .expiryDate(version.getExpiryDate())
                .approvalStatus(version.getApprovalStatus())
                .computedStatus(statusResolver.resolve(version))
                .terminalStatus(statusResolver.resolveTerminalStatus(version))
                .createdAt(version.getCreatedAt())
                .lastModifiedAt(version.getUpdatedAt())
                .build();
    }

    public AgreementVersionResponse toVersionResponse(AgreementVersion version) {
                Agreement parent = version.getAgreement();
                AgreementGroup group = parent.getAgreementGroup();

                List<AgreementVersionResponse.VendorSummary> vendors = vendorRepository
                                .findByAgreementVersionId(version.getId())
                                .stream()
                                .map(v -> new AgreementVersionResponse.VendorSummary(v.getVendorId(),
                                                v.getVendorNameSnapshot(),
                                                v.getStateSnapshot()))
                                .toList();

                Long versionIdForRules = version.getId();
                boolean isAssetRental = isAssetRentalIncomeType(
                                version.getIncomeType() != null ? version.getIncomeType().getId() : null);

                List<AgreementManufacturer> rawManufacturerRules = manufacturerRuleRepository.findByAgreementVersionId(versionIdForRules);
                List<AgreementDivisionRule> rawDivisionRules = divisionRuleRepository.findByAgreementVersionId(versionIdForRules);
                List<AgreementProductRule> rawProductRules = productRuleRepository.findByAgreementVersionId(versionIdForRules);
                boolean hasComputedProducts = computedProductRepository.countByAgreementVersionId(versionIdForRules) > 0;

                if (!isAssetRental
                                && rawManufacturerRules.isEmpty()
                                && rawDivisionRules.isEmpty()
                                && rawProductRules.isEmpty()
                                && !hasComputedProducts) {
                        
                        Optional<Long> fallbackVersionIdOpt = agreementVersionRepository.findLatestFallbackVersionIdWithRules(parent.getId());
                        if (fallbackVersionIdOpt.isPresent()) {
                            versionIdForRules = fallbackVersionIdOpt.get();
                            rawManufacturerRules = manufacturerRuleRepository.findByAgreementVersionId(versionIdForRules);
                            rawDivisionRules = divisionRuleRepository.findByAgreementVersionId(versionIdForRules);
                            rawProductRules = productRuleRepository.findByAgreementVersionId(versionIdForRules);
                        }
                }

                List<Long> manufacturerIds = rawManufacturerRules.stream().map(AgreementManufacturer::getManufacturerId).toList();

                List<Long> divisionIds = rawDivisionRules.stream()
                                .map(AgreementDivisionRule::getDivisionId)
                                .toList();
                List<String> productIds = rawProductRules.stream()
                                .map(AgreementProductRule::getProductId)
                                .toList();

                // ── Fire all three external Product Microservice calls in PARALLEL ────────
                // Previously sequential (manufacturers → divisions → products), total wait = sum of timeouts.
                // Now total wait = max of one timeout.
                AtomicReference<Map<Long, IntegrationManufacturerResponse>> mfrMapRef =
                        new AtomicReference<>(Map.of());
                AtomicReference<Map<Long, String>> divMapRef = new AtomicReference<>(Map.of());
                AtomicReference<Map<String, IntegrationProductResponse>> prodMapRef =
                        new AtomicReference<>(Map.of());

                CompletableFuture<Void> mfrFuture = manufacturerIds.isEmpty()
                        ? CompletableFuture.completedFuture(null)
                        : CompletableFuture.runAsync(() -> {
                            try {
                                mfrMapRef.set(productMasterIntegrationService.getManufacturersByIds(manufacturerIds));
                            } catch (Exception ex) {
                                log.warn("Could not hydrate manufacturer names: {}", ex.getMessage());
                            }
                        });

                CompletableFuture<Void> divFuture = divisionIds.isEmpty()
                        ? CompletableFuture.completedFuture(null)
                        : CompletableFuture.runAsync(() -> {
                            try {
                                divMapRef.set(productMasterIntegrationService.getDivisionNamesByIds(
                                        manufacturerIds, divisionIds));
                            } catch (Exception ex) {
                                log.warn("Could not hydrate division names: {}", ex.getMessage());
                            }
                        });

                CompletableFuture<Void> prodFuture = productIds.isEmpty()
                        ? CompletableFuture.completedFuture(null)
                        : CompletableFuture.runAsync(() -> {
                            try {
                                prodMapRef.set(productMasterIntegrationService.getProductsByCodes(productIds));
                            } catch (Exception ex) {
                                log.warn("Could not hydrate product details: {}", ex.getMessage());
                            }
                        });

                // Join all three — total wait = slowest single call, not sum of all three
                try {
                    CompletableFuture.allOf(mfrFuture, divFuture, prodFuture).join();
                } catch (Exception ex) {
                    log.warn("One or more Product Microservice hydration futures failed: {}", ex.getMessage());
                }

                Map<Long, IntegrationManufacturerResponse> manufacturerByIdMap = mfrMapRef.get();
                Map<Long, String> divisionNamesByIdMap = divMapRef.get();
                final Map<String, IntegrationProductResponse> finalProductsByCode = prodMapRef.get();

                // Build manufacturer summaries (with DB fallback if external call returned nothing)
                List<AgreementVersionResponse.ManufacturerSummary> manufacturers = new ArrayList<>();
                if (!manufacturerIds.isEmpty()) {
                        manufacturers = manufacturerIds.stream()
                                        .map(id -> {
                                                IntegrationManufacturerResponse hydrated = manufacturerByIdMap.get(id);
                                                return new AgreementVersionResponse.ManufacturerSummary(
                                                                id,
                                                                hydrated != null ? hydrated.getManufacturerName() : null);
                                        })
                                        .toList();
                } else {
                        // Fallback to computed products distinct manufacturers
                        try {
                                List<Object[]> distinctMfrs = computedProductRepository.findDistinctManufacturersByVersionId(versionIdForRules);
                                manufacturers = distinctMfrs.stream()
                                                .map(row -> {
                                                        String mfrIdStr = (String) row[0];
                                                        String mfrName = (String) row[1];
                                                        Long mfrId = mfrIdStr != null ? Long.parseLong(mfrIdStr) : null;
                                                        return new AgreementVersionResponse.ManufacturerSummary(mfrId, mfrName);
                                                })
                                                .filter(m -> m.id() != null)
                                                .toList();
                        } catch (Exception ex) {
                                log.warn("Could not extract distinct manufacturers from computed products: {}", ex.getMessage());
                        }
                }

                final Map<Long, String> finalDivisionNamesById = divisionNamesByIdMap;
                List<AgreementVersionResponse.DivisionRuleSummary> divisionRules = rawDivisionRules.stream()
                                .map(dr -> new AgreementVersionResponse.DivisionRuleSummary(
                                                dr.getDivisionId(),
                                                dr.getRuleType().name(),
                                                finalDivisionNamesById.get(dr.getDivisionId()),
                                                dr.getManufacturerId()))
                                .toList();
                List<AgreementVersionResponse.ProductRuleSummary> productRules = rawProductRules.stream()
                                .map(pr -> {
                                        IntegrationProductResponse hydrated = finalProductsByCode
                                                        .get(pr.getProductId());
                                        return new AgreementVersionResponse.ProductRuleSummary(
                                                        pr.getProductId(),
                                                        pr.getRuleType().name(),
                                                        hydrated != null ? hydrated.getProductName() : null,
                                                        pr.getManufacturerId());
                                })
                                .toList();

                Integer productCount = (int) computedProductRepository
                                .countByAgreementVersionId(versionIdForRules);

                AgreementType agreementType = version.getAgreementType();

                AgreementVersionResponse.AssetSummary assetSummary = toAssetSummary(version);

                List<AgreementVersionResponse.AssetPayoutPeriodSummary> assetPayoutPeriods = assetPayoutPeriodRepository
                                .findByAgreementVersionIdOrderByPeriodMonthsAscIdAsc(version.getId())
                                .stream()
                                .map(period -> new AgreementVersionResponse.AssetPayoutPeriodSummary(
                                                period.getId(),
                                                period.getPeriodMonths(),
                                                period.getPayoutPerStore()))
                                .toList();

                List<AgreementVersionResponse.DocumentSummary> documents = documentRepository
                                .findByAgreementVersionIdAndIsActiveTrue(version.getId())
                                .stream()
                                .map(document -> new AgreementVersionResponse.DocumentSummary(
                                                document.getId(),
                                                document.getFileUrl(),
                                                document.getOriginalFileName(),
                                                document.getThumbnailUrl(),
                                                document.getDocumentType().name()))
                                .toList();

                List<com.medplus.agreement_tracker_backend.dto.common.AgreementLocationDto> locations = agreementLocationRepository
                                .findByAgreementVersionId(version.getId()).stream()
                                .map(loc -> new com.medplus.agreement_tracker_backend.dto.common.AgreementLocationDto(
                                                loc.getLocationType(), loc.getCountryCode(), loc.getCountryName(),
                                                loc.getCountrySubName(),
                                                loc.getStateCode(), loc.getStateName(), loc.getStateSubName(),
                                                loc.getCityCode(), loc.getCityName(), loc.getCitySubName()))
                                .toList();

                return AgreementVersionResponse.builder()
                                .id(version.getId())
                                .agreementId(parent.getId())
                                .agreementName(parent.getAgreementName())
                                .versionNumber(version.getVersionNumber())
                                .revisionType(version.getRevisionType())
                                .agreementGroupId(group.getId())
                                .agreementGroupName(group.getName())
                                .ownerId(version.getOwner().getId())
                                .ownerName(version.getOwner().getFullName())
                                .incomeTypeId(version.getIncomeType() != null ? version.getIncomeType().getId() : null)
                                .incomeTypeName(version.getIncomeType() != null ? version.getIncomeType().getName()
                                                : null)
                                .agreementTypeId(agreementType != null ? agreementType.getId() : null)
                                .agreementTypeName(agreementType != null ? agreementType.getName() : null)
                                .commercialStructure(version.getCommercialStructure())
                                .commercialValue(version.getCommercialValue())
                                .assetCategory(version.getAssetCategory() != null ? version.getAssetCategory().name()
                                                : null)
                                .assetType(version.getAssetType())
                                .flatValueType(version.getFlatValueType())
                                .flatBaselineFrequency(version.getFlatBaselineFrequency())
                                .quantityCap(version.getQuantityCap())
                                .adhocSubType(version.getAdhocSubType() != null ? version.getAdhocSubType().name()
                                                : null)
                                .invoiceVendorId(version.getInvoiceVendorId())
                                .invoiceVendorNameSnapshot(version.getInvoiceVendorNameSnapshot())
                                .payoutBufferDays(version.getPayoutBufferDays())
                                .leadTimeBasis(version.getLeadTimeBasis())
                                .invoiceGenerationLeadTime(version.getInvoiceGenerationLeadTime())
                                .calculationBasis(version.getCalculationBasis())
                                .paymentRealizationType(version.getPaymentRealizationType())
                                .startDate(version.getStartDate())
                                .expiryDate(version.getExpiryDate())
                                .financialYearStartMonth(version.getFinancialYearStartMonth())
                                .approvalStatus(version.getApprovalStatus())
                                .computedStatus(statusResolver.resolve(version))
                                .terminalStatus(statusResolver.resolveTerminalStatus(version))
                                .inProgressFlag(version.isInProgressFlag())
                                .terminationDate(version.getTerminationDate())
                                .terminationReason(version.getTerminationReason())
                                .notes(version.getNotes())
                                .locations(locations)
                                .vendors(vendors)
                                .manufacturers(manufacturers)
                                .divisionRules(divisionRules)
                                .productRules(productRules)
                                .productCount(productCount)
                                .productScopeComputeStatus(version.getProductScopeComputeStatus())
                                .asset(assetSummary)
                                .assetPayoutPeriods(assetPayoutPeriods)
                                .documents(documents)
                                .jbpCommitted(jbpCommercialPeriodRepository.existsByAgreementVersionId(version.getId()))
                                .pendingActionRequest(resolvePendingActionRequest(parent.getId()))
                                .createdAt(version.getCreatedAt())
                                .updatedAt(version.getUpdatedAt())
                                .build();
        }

        public AgreementResponse toParentResponse(Agreement parent, AgreementVersion visible,
                        List<AgreementVendor> vendors) {
                AgreementGroup group = parent.getAgreementGroup();

                List<AgreementVersionResponse.VendorSummary> vendorSummaries = vendors.stream()
                                .map(v -> new AgreementVersionResponse.VendorSummary(v.getVendorId(),
                                                v.getVendorNameSnapshot(),
                                                v.getStateSnapshot()))
                                .toList();

                List<AgreementVersion> latestBatch = agreementVersionRepository
                                .findLatestVersionsForAgreementIds(List.of(parent.getId()));
                AgreementVersion latest = latestBatch.isEmpty() ? null : latestBatch.get(0);

                AgreementType agreementType = visible.getAgreementType();

                return new AgreementResponse(
                                parent.getId(),
                                parent.getAgreementName(),
                                group.getId(),
                                group.getName(),
                                agreementType != null ? agreementType.getId() : null,
                                agreementType != null ? agreementType.getName() : null,
                                parent.getCurrentVersionId(),
                                latest != null ? latest.getId() : null,
                                visible.getVersionNumber(),
                                statusResolver.resolve(visible),
                                visible.getApprovalStatus(),
                                parent.isActive(),
                                parent.getCreatedAt(),
                                visible.getUpdatedAt(),
                                visible.getIncomeType() != null ? visible.getIncomeType().getName() : null,
                                visible.getStartDate(),
                                visible.getExpiryDate(),
                                parent.getOwner().getFullName(),
                                parent.getOwner().getId(),
                                vendorSummaries);
        }

        public List<ApprovalTimelineResponse> mapActionRequestToTimeline(AgreementActionRequest request) {
                List<ApprovalTimelineResponse> entries = new ArrayList<>(2);
                String actionPrefix = request.getActionType().name();
                entries.add(new ApprovalTimelineResponse(
                                request.getId() * 10,
                                null,
                                actionPrefix + "_REQUESTED",
                                request.getReasonComments(),
                                null,
                                null,
                                request.getRequestedBy().getId(),
                                request.getRequestedBy().getFullName(),
                                request.getCreatedAt()));

                if (request.getResolvedAt() != null) {
                        String eventSuffix = request.getStatus() == ActionRequestStatus.APPROVED ? "APPROVED"
                                        : "REJECTED";
                        String remarks = request.getStatus() == ActionRequestStatus.REJECTED
                                        && request.getApproverComments() != null
                                                        ? request.getApproverComments()
                                                        : request.getReasonComments();
                        Long actorId = request.getResolvedBy() != null ? request.getResolvedBy().getId() : null;
                        String actorName = request.getResolvedBy() != null ? request.getResolvedBy().getFullName()
                                        : null;
                        entries.add(new ApprovalTimelineResponse(
                                        request.getId() * 10 + 1,
                                        null,
                                        actionPrefix + "_" + eventSuffix,
                                        remarks,
                                        null,
                                        null,
                                        actorId,
                                        actorName,
                                        request.getResolvedAt()));
                }
                return entries;
        }

        public PendingActionRequestInfo resolvePendingActionRequest(Long agreementId) {
                return actionRequestRepository
                                .findFirstByAgreementVersion_Agreement_IdAndStatus(agreementId,
                                                ActionRequestStatus.PENDING)
                                .map(r -> new PendingActionRequestInfo(
                                                r.getId(),
                                                r.getActionType(),
                                                r.getReasonComments(),
                                                r.getRequestedTerminationDate(),
                                                r.getTargetUser() != null ? r.getTargetUser().getId() : null,
                                                r.getTargetUser() != null ? r.getTargetUser().getFullName() : null,
                                                r.getRequestedBy().getFullName(),
                                                r.getCreatedAt()))
                                .orElse(null);
        }

        public AgreementResponse toParentResponseEmpty(Agreement parent) {
                AgreementGroup group = parent.getAgreementGroup();
                User owner = parent.getOwner();
                return new AgreementResponse(
                                parent.getId(),
                                parent.getAgreementName(),
                                group != null ? group.getId() : null,
                                group != null ? group.getName() : null,
                                null,
                                null,
                                parent.getCurrentVersionId(),
                                null,
                                null,
                                null,
                                null,
                                parent.isActive(),
                                parent.getCreatedAt(),
                                null,
                                null,
                                null,
                                null,
                                owner != null ? owner.getFullName() : null,
                                owner != null ? owner.getId() : null,
                                List.of());
        }

        public AgreementVersion resolveListDisplayVersion(Agreement parent, AgreementVersion latest,
                        Map<Long, AgreementVersion> currentVersionById,
                        Long currentUserId, String filterStatus) {
                // When filtering by DRAFT, show the DRAFT version instead of the current
                // approved version
                if ("DRAFT".equalsIgnoreCase(filterStatus) && latest != null
                                && latest.getApprovalStatus() == ApprovalStatus.DRAFT
                                && parent.getOwner().getId().equals(currentUserId)) {
                        return latest;
                }
                if (parent.getCurrentVersionId() != null) {
                        AgreementVersion current = currentVersionById.get(parent.getCurrentVersionId());
                        if (current != null) {
                                return current;
                        }
                }
                return resolveVisibleLatest(parent, latest, currentUserId);
        }

        public Pageable mapAgreementPageable(Pageable pageable) {
                if (pageable.getSort().isUnsorted()) {
                        return pageable;
                }
                List<Sort.Order> orders = pageable.getSort().stream()
                                .map(order -> {
                                        String property = switch (order.getProperty()) {
                                                case "agreementGroupName" -> "createdAt";
                                                case "agreementName" -> "agreementName";
                                                case "createdAt" -> "createdAt";
                                                default -> order.getProperty();
                                        };
                                        return new Sort.Order(order.getDirection(), property);
                                })
                                .toList();
                return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
        }

        public AgreementVersion resolveVisibleLatest(Agreement parent, AgreementVersion latest, Long currentUserId) {
                if (latest == null) {
                        return null;
                }
                if (latest.getApprovalStatus() != ApprovalStatus.DRAFT
                                || parent.getOwner().getId().equals(currentUserId)) {
                        return latest;
                }
                if (parent.getCurrentVersionId() != null) {
                        return agreementVersionRepository.findById(parent.getCurrentVersionId()).orElse(latest);
                }
                return agreementVersionRepository.findByAgreementId(parent.getId()).stream()
                                .filter(v -> v.getApprovalStatus() != ApprovalStatus.DRAFT
                                                || parent.getOwner().getId().equals(currentUserId))
                                .max((a, b) -> Integer.compare(a.getVersionNumber(), b.getVersionNumber()))
                                .orElse(null);
        }

        public String resolveUserName(Long userId) {
                if (userId == null) {
                        return null;
                }
                return userRepository.findById(userId).map(User::getFullName).orElse(null);
        }

        private boolean isAssetRentalIncomeType(Long incomeTypeId) {
                if (incomeTypeId == null) {
                        return false;
                }
                return incomeTypeRepository.findById(incomeTypeId)
                                .map(incomeType -> IncomeTypeNames.ASSET_RENTALS.equalsIgnoreCase(incomeType.getName()))
                                .orElse(false);
        }

        public AgreementVersionResponse.AssetSummary toAssetSummary(AgreementVersion version) {
                if (version.getAssetCategory() == null && version.getAssetType() == null
                                && version.getCommercialStructure() != CommercialStructure.FLAT
                                && version.getCommercialStructure() != CommercialStructure.PAYOUT_PER_STORE) {
                        return null;
                }
                BigDecimal flatPayout = version.getCommercialStructure() == CommercialStructure.FLAT
                                ? version.getCommercialValue()
                                : null;
                String payoutMode = version.getCommercialStructure() == CommercialStructure.FLAT ? "FLAT"
                                : (version.getCommercialStructure() == CommercialStructure.PAYOUT_PER_STORE
                                                ? "PER_STORE"
                                                : null);

                int storeCount = (int) storeMappingRepository.countByAgreementVersionId(version.getId());

                return new AgreementVersionResponse.AssetSummary(
                                version.getAssetCategory() != null ? version.getAssetCategory().name() : null,
                                version.getAssetType(),
                                storeCount,
                                flatPayout);
        }

}
