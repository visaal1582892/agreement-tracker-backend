package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.dto.request.PriceOffCommitRequest;
import com.medplus.agreement_tracker_backend.dto.response.PriceOffPreviewResponse;
import com.medplus.agreement_tracker_backend.dto.response.PriceOffPreviewRowDto;
import com.medplus.agreement_tracker_backend.dto.request.BulkPriceOffIdsRequest;
import com.medplus.agreement_tracker_backend.dto.request.BulkPriceOffRejectRequest;
import com.medplus.agreement_tracker_backend.dto.request.BulkUpdateCampaignIdRequest;
import com.medplus.agreement_tracker_backend.dto.request.UpdateCampaignIdRequest;
import com.medplus.agreement_tracker_backend.dto.request.PriceOffUpdateRequestDto;
import com.medplus.agreement_tracker_backend.dto.response.ConsumerPriceOffCampaignResponse;
import com.medplus.agreement_tracker_backend.dto.request.PriceOffListFilters;
import com.medplus.agreement_tracker_backend.dto.response.PriceOffLocationOptionDto;
import com.medplus.agreement_tracker_backend.dto.response.PagedResponse;
import com.medplus.agreement_tracker_backend.dto.response.PriceOffExcelParseResult;
import com.medplus.agreement_tracker_backend.dto.response.PriceOffFilterOptionDto;
import com.medplus.agreement_tracker_backend.dto.response.PriceOffFilterOptionsResponse;
import com.medplus.agreement_tracker_backend.dto.response.PriceOffUploadSummaryDto;
import com.medplus.agreement_tracker_backend.entity.ChannelMaster;
import com.medplus.agreement_tracker_backend.entity.ConsumerPriceOffCampaign;
import com.medplus.agreement_tracker_backend.entity.ConsumerPriceOffCampaignLocationAllocation;
import com.medplus.agreement_tracker_backend.entity.DiscountTypeMaster;
import com.medplus.agreement_tracker_backend.integration.ProductMasterIntegrationService;
import com.medplus.agreement_tracker_backend.integration.dto.IntegrationProductResponse;
import com.medplus.agreement_tracker_backend.enums.PriceOffApprovalStatus;
import com.medplus.agreement_tracker_backend.enums.PriceOffDiscountType;
import com.medplus.agreement_tracker_backend.enums.PriceOffDisplayStatus;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.exception.ExcelValidationException;
import com.medplus.agreement_tracker_backend.exception.ResourceNotFoundException;
import com.medplus.agreement_tracker_backend.repository.ChannelMasterRepository;
import com.medplus.agreement_tracker_backend.repository.ConsumerPriceOffCampaignRepository;
import com.medplus.agreement_tracker_backend.repository.DiscountTypeMasterRepository;
import com.medplus.agreement_tracker_backend.repository.PriceOffLocationMasterRepository;
import com.medplus.agreement_tracker_backend.repository.spec.PriceOffCampaignSpecifications;
import com.medplus.agreement_tracker_backend.service.ConsumerPriceOffService;
import com.medplus.agreement_tracker_backend.service.PriceOffCampaignValidationService;
import com.medplus.agreement_tracker_backend.service.PriceOffExcelGeneratorService;
import com.medplus.agreement_tracker_backend.service.PriceOffExcelParserService;
import com.medplus.agreement_tracker_backend.util.JbpExcelValidationErrorAnnotator;
import com.medplus.agreement_tracker_backend.util.PriceOffExcelLayout;
import com.medplus.agreement_tracker_backend.util.PriceOffCalculationUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.medplus.agreement_tracker_backend.util.PriceOffStatusResolver;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ConsumerPriceOffServiceImpl implements ConsumerPriceOffService {

    private final ConsumerPriceOffCampaignRepository campaignRepository;
    private final ProductMasterIntegrationService productMasterIntegrationService;
    private final ChannelMasterRepository channelRepository;
    private final DiscountTypeMasterRepository discountTypeRepository;
    private final PriceOffLocationMasterRepository locationMasterRepository;
    private final PriceOffExcelGeneratorService excelGeneratorService;
    private final PriceOffExcelParserService excelParserService;
    private final PriceOffCampaignValidationService validationService;
    private final PriceOffBulkPreviewEngine bulkPreviewEngine;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PriceOffFilterOptionsResponse getFilterOptions() {
        List<PriceOffFilterOptionDto> channels = buildChannelOptions(
                channelRepository.findByIsActiveTrueOrderByChannelNameAsc())
                .stream()
                .map(name -> new PriceOffFilterOptionDto(name, name))
                .toList();
        List<PriceOffFilterOptionDto> discountTypes = discountTypeRepository
                .findByIsActiveTrueOrderByDiscountNameAsc()
                .stream()
                .map(master -> new PriceOffFilterOptionDto(
                        mapMasterToCampaignDiscountType(master).name(),
                        master.getDiscountName()))
                .toList();
        return new PriceOffFilterOptionsResponse(channels, discountTypes);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceOffLocationOptionDto> getAllLocations() {
        return locationMasterRepository.findAllByOrderByCodeAsc().stream()
                .map(location -> new PriceOffLocationOptionDto(
                        location.getCode(),
                        location.getName(),
                        location.isActive()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateTemplate() {
        return excelGeneratorService.generateTemplate();
    }

    @Override
    @Transactional(readOnly = true)
    public PriceOffPreviewResponse previewUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Uploaded Excel file is required");
        }
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            PriceOffExcelParseResult parseResult = excelParserService.parseWorkbook(workbook);
            PriceOffPreviewResponse preview = bulkPreviewEngine.buildPreview(parseResult);
            
            if (preview.errorRows() > 0) {
                Map<Integer, String> sheetErrors = new java.util.HashMap<>();
                for (PriceOffPreviewRowDto row : preview.rows()) {
                    if (!row.valid() && row.errors() != null && !row.errors().isEmpty()) {
                        sheetErrors.put(row.rowNumber() - 1, String.join(", ", row.errors()));
                    }
                }
                Map<String, Map<Integer, String>> allErrors = Map.of(workbook.getSheetAt(0).getSheetName(), sheetErrors);
                byte[] annotatedWorkbook = JbpExcelValidationErrorAnnotator.annotateWorkbook(workbook, allErrors);
                throw new ExcelValidationException(annotatedWorkbook, "PriceOffs_Upload_Errors.xlsx");
            }
            
            return preview;
        } catch (IOException ex) {
            throw new BusinessException("Failed to read uploaded price off file");
        } catch (org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException ex) {
            throw new com.medplus.agreement_tracker_backend.exception.UnsupportedFileFormatException("Invalid Excel file format. Please upload a valid .xlsx file.");
        }
    }

    @Override
    public PriceOffUploadSummaryDto commitCampaigns(PriceOffCommitRequest request, Long currentUserId) {
        if (request == null || request.rows() == null || request.rows().isEmpty()) {
            throw new BusinessException("No campaign rows to commit");
        }
        if (request.rows().size() > PriceOffExcelLayout.MAX_DATA_ROWS) {
            throw new BusinessException(
                    "Commit exceeds maximum of " + PriceOffExcelLayout.MAX_DATA_ROWS + " rows");
        }
        List<PriceOffPreviewRowDto> invalid = request.rows().stream()
                .filter(row -> row == null || !row.valid() || (row.errors() != null && !row.errors().isEmpty()))
                .toList();
        if (!invalid.isEmpty()) {
            throw new BusinessException(
                    "Cannot commit while " + invalid.size() + " row(s) still have validation errors");
        }

        // Overlap-only re-check; product IDs already validated at preview (skip Product Microservice)
        bulkPreviewEngine.validateCommitRows(request.rows());

        List<ChannelMaster> allChannels = channelRepository.findByIsActiveTrueOrderByChannelNameAsc();
        Map<String, ChannelMaster> channelsByName = allChannels.stream()
                .collect(Collectors.toMap(
                        channel -> channel.getChannelName().toLowerCase(Locale.ROOT),
                        channel -> channel,
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<ConsumerPriceOffCampaign> entitiesToSave = request.rows().stream()
                .map(row -> buildCampaignFromPreviewRow(row, allChannels, channelsByName, currentUserId))
                .toList();

        List<ConsumerPriceOffCampaign> saved = campaignRepository.saveAll(entitiesToSave);

        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalCreditNote = BigDecimal.ZERO;
        for (PriceOffPreviewRowDto row : request.rows()) {
            if (row.totalQty() != null) {
                totalQty = totalQty.add(BigDecimal.valueOf(row.totalQty()));
            }
            if (row.creditNote() != null) {
                totalCreditNote = totalCreditNote.add(row.creditNote());
            }
        }
        return new PriceOffUploadSummaryDto(saved.size(), 0, totalQty, totalCreditNote);
    }

    @Override
    @Deprecated
    public PriceOffUploadSummaryDto uploadCampaigns(MultipartFile file, Long currentUserId) {
        throw new BusinessException(
                "Direct upload is disabled. Use preview (/price-offs/preview) then commit (/price-offs/commit).");
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ConsumerPriceOffCampaignResponse> listCampaigns(
            PriceOffListFilters filters,
            Pageable pageable,
            Long currentUserId) {
        PriceOffListFilters activeFilters = filters != null
                ? filters
                : new PriceOffListFilters(null, null, null, null, null, null);
        Specification<ConsumerPriceOffCampaign> spec = PriceOffCampaignSpecifications.combine(
                PriceOffCampaignSpecifications.withProductFilter(activeFilters.product()),
                PriceOffCampaignSpecifications.withCampaignIdFilter(activeFilters.campaignId()),
                PriceOffCampaignSpecifications.withLocationFilter(activeFilters.location()),
                PriceOffCampaignSpecifications.withChannelFilter(activeFilters.channel()),
                PriceOffCampaignSpecifications.withDiscountTypeFilter(activeFilters.discountType()),
                PriceOffCampaignSpecifications.withDisplayStatus(activeFilters.status()));
        Page<ConsumerPriceOffCampaign> result = campaignRepository.findAll(spec, pageable);
        return PagedResponse.from(result.map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public ConsumerPriceOffCampaignResponse getCampaign(Long id) {
        return toResponse(loadCampaign(id));
    }

    @Override
    public ConsumerPriceOffCampaignResponse updatePriceOff(
            Long id,
            PriceOffUpdateRequestDto request,
            Long currentUserId) {
        ConsumerPriceOffCampaign campaign = loadCampaignForUpdate(id);
        if (campaign.getApprovalStatus() != PriceOffApprovalStatus.DRAFT) {
            throw new BusinessException(
                    "Only draft campaigns can be edited. Current status: " + campaign.getApprovalStatus());
        }
        validationService.validateCampaignDates(request.getStartDate(), request.getEndDate());
        BigDecimal medplusContribution = request.getMedplusContribution() != null
                ? request.getMedplusContribution()
                : BigDecimal.ZERO;
        BigDecimal baseOffer = request.getBaseOffer() != null
                ? request.getBaseOffer()
                : BigDecimal.ZERO;
        validationService.validateDiscountSanity(
                request.getDiscountType(),
                request.getMrp(),
                baseOffer,
                medplusContribution);
        validationService.validateNoDbOverlap(
                campaign.getProductId(),
                request.getStartDate(),
                request.getEndDate(),
                campaign.getId());

        List<ChannelMaster> allChannels = channelRepository.findByIsActiveTrueOrderByChannelNameAsc();
        Map<String, ChannelMaster> channelsByName = allChannels.stream()
                .collect(Collectors.toMap(
                        channel -> channel.getChannelName().toLowerCase(Locale.ROOT),
                        channel -> channel,
                        (left, right) -> left,
                        LinkedHashMap::new));

        Map<String, Integer> normalizedAllocations = normalizeLocationAllocations(request.getLocationAllocations());
        mergeLocationAllocations(campaign, normalizedAllocations);

        int totalQty = sumLocationAllocations(campaign);
        if (totalQty <= 0) {
            throw new BusinessException("Total Qty must be greater than zero — enter allocation values in zone columns");
        }

        IntegrationProductResponse product = productMasterIntegrationService.findProductByCode(campaign.getProductId())
                .orElseThrow(() -> new BusinessException(
                        "Product '" + campaign.getProductId() + "' not found in Product Master"));
        BigDecimal authoritativeCp = validationService.resolveAuthoritativeCp(product, request.getCp());

        campaign.setStartDate(request.getStartDate());
        campaign.setEndDate(request.getEndDate());
        campaign.setDurationMonths(calculateDurationMonths(request.getStartDate(), request.getEndDate()));
        campaign.setMaxUnitCap(request.getMaxUnitCap());
        campaign.setFromQty(request.getFromQty());
        campaign.setRemarks(request.getRemarks());
        campaign.setLocationLabel(request.getLocationLabel().trim());
        campaign.setChannelLabel(request.getChannelLabel().trim());
        campaign.setDiscountType(request.getDiscountType());
        campaign.setCp(authoritativeCp);
        campaign.setMrp(request.getMrp());
        campaign.setBaseOffer(baseOffer);
        campaign.setMedplusContribution(medplusContribution);
        campaign.setChannels(resolveChannels(request.getChannelLabel(), allChannels, channelsByName));
        campaign.setUpdatedByUserId(currentUserId);

        applyDerivedFields(campaign, totalQty);

        campaignRepository.save(campaign);
        return toResponse(loadCampaignForUpdate(id));
    }

    @Override
    public ConsumerPriceOffCampaignResponse updateCampaignId(Long id, UpdateCampaignIdRequest request, Long currentUserId) {
        ConsumerPriceOffCampaign campaign = loadCampaign(id);
        validateCampaignIdUpdateAllowed(campaign);
        applyCampaignId(campaign, normalizeCampaignId(request.campaignId()), currentUserId);
        return toResponse(campaignRepository.save(campaign));
    }

    @Override
    public List<ConsumerPriceOffCampaignResponse> bulkUpdateCampaignId(
            BulkUpdateCampaignIdRequest request,
            Long currentUserId) {
        List<ConsumerPriceOffCampaign> campaigns = loadCampaigns(request.ids());
        String campaignId = normalizeCampaignId(request.campaignId());
        campaigns.forEach(campaign -> {
            validateCampaignIdUpdateAllowed(campaign);
            applyCampaignId(campaign, campaignId, currentUserId);
        });
        return campaignRepository.saveAll(campaigns).stream().map(this::toResponse).toList();
    }

    @Override
    public List<ConsumerPriceOffCampaignResponse> bulkSubmit(BulkPriceOffIdsRequest request, Long currentUserId) {
        List<ConsumerPriceOffCampaign> campaigns = loadCampaigns(request.ids());
        campaigns.forEach(campaign -> {
            if (campaign.getApprovalStatus() != PriceOffApprovalStatus.DRAFT) {
                throw new BusinessException("Only DRAFT campaigns can be submitted for approval");
            }
            if (Boolean.TRUE.equals(campaign.getIsNegativeMargin())) {
                throw new BusinessException(String.format(
                        "Campaign for Product ID %s has a negative margin and cannot be sent for approval. Please adjust the MedPlus Contribution.",
                        campaign.getProductId()));
            }
            campaign.setApprovalStatus(PriceOffApprovalStatus.PENDING_APPROVAL);
            campaign.setUpdatedByUserId(currentUserId);
        });
        return campaignRepository.saveAll(campaigns).stream().map(this::toResponse).toList();
    }

    @Override
    public void bulkDelete(BulkPriceOffIdsRequest request, Long currentUserId) {
        List<ConsumerPriceOffCampaign> campaigns = loadCampaigns(request.ids());
        campaigns.forEach(campaign -> {
            if (campaign.getApprovalStatus() != PriceOffApprovalStatus.DRAFT) {
                throw new BusinessException("Only DRAFT campaigns can be deleted");
            }
        });
        campaignRepository.deleteAll(campaigns);
    }

    @Override
    public List<ConsumerPriceOffCampaignResponse> bulkApprove(BulkPriceOffIdsRequest request, Long currentUserId) {
        return request.ids().stream().map(id -> approve(id, currentUserId)).toList();
    }

    @Override
    public List<ConsumerPriceOffCampaignResponse> bulkReject(BulkPriceOffRejectRequest request, Long currentUserId) {
        return request.ids().stream().map(id -> reject(id, request.remarks(), currentUserId)).toList();
    }

    @Override
    public ConsumerPriceOffCampaignResponse approve(Long id, Long currentUserId) {
        ConsumerPriceOffCampaign campaign = loadCampaign(id);
        if (campaign.getApprovalStatus() != PriceOffApprovalStatus.PENDING_APPROVAL) {
            throw new BusinessException("Only pending approval campaigns can be approved");
        }
        campaign.setApprovalStatus(PriceOffApprovalStatus.APPROVED);
        campaign.setApprovedByUserId(currentUserId);
        campaign.setRejectionRemarks(null);
        campaign.setUpdatedByUserId(currentUserId);
        return toResponse(campaignRepository.save(campaign));
    }

    @Override
    public ConsumerPriceOffCampaignResponse reject(Long id, String remarks, Long currentUserId) {
        if (remarks == null || remarks.isBlank()) {
            throw new BusinessException("Rejection remarks are required");
        }
        ConsumerPriceOffCampaign campaign = loadCampaign(id);
        if (campaign.getApprovalStatus() != PriceOffApprovalStatus.PENDING_APPROVAL) {
            throw new BusinessException("Only pending approval campaigns can be rejected");
        }
        campaign.setApprovalStatus(PriceOffApprovalStatus.REJECTED);
        campaign.setRejectionRemarks(remarks.trim());
        campaign.setUpdatedByUserId(currentUserId);
        return toResponse(campaignRepository.save(campaign));
    }

    private ConsumerPriceOffCampaign buildCampaignFromPreviewRow(
            PriceOffPreviewRowDto row,
            List<ChannelMaster> allChannels,
            Map<String, ChannelMaster> channelsByName,
            Long currentUserId) {
        ConsumerPriceOffCampaign campaign = ConsumerPriceOffCampaign.builder()
                .productId(row.productId())
                .productName(row.productName())
                .manufacturerName(row.manufacturerName())
                .l3Category(row.l3Category())
                .startDate(row.startDate())
                .endDate(row.endDate())
                .durationMonths(calculateDurationMonths(row.startDate(), row.endDate()))
                .maxUnitCap(row.maxUnitCap())
                .fromQty(row.fromQty())
                .totalQty(row.totalQty())
                .creditNote(row.creditNote())
                .remarks(row.remarks())
                .locationLabel(row.locationLabel())
                .channelLabel(row.channelLabel())
                .discountType(row.discountType())
                .cp(row.cp())
                .mrp(row.mrp())
                .baseOffer(row.baseOffer())
                .medplusContribution(row.medplusContribution())
                .marginPercent(row.marginPercent())
                .finalOffer(row.finalOffer())
                .percentOff(row.percentOff())
                .finalMarginPercent(row.finalMarginPercent())
                .isNegativeMargin(row.negativeMargin())
                .approvalStatus(PriceOffApprovalStatus.DRAFT)
                .unitsConsumed(0)
                .submittedByUserId(currentUserId)
                .states(new LinkedHashSet<>())
                .channels(resolveChannels(row.channelLabel(), allChannels, channelsByName))
                .build();
        campaign.setCreatedByUserId(currentUserId);
        campaign.setUpdatedByUserId(currentUserId);

        if (row.locationAllocations() != null) {
            row.locationAllocations().forEach((code, qty) -> {
                ConsumerPriceOffCampaignLocationAllocation allocation = ConsumerPriceOffCampaignLocationAllocation.builder()
                        .campaign(campaign)
                        .locationCode(code)
                        .allocatedQty(qty)
                        .build();
                campaign.getLocationAllocations().add(allocation);
            });
        }
        return campaign;
    }

    private void validateCampaignIdUpdateAllowed(ConsumerPriceOffCampaign campaign) {
        if (campaign.getApprovalStatus() != PriceOffApprovalStatus.APPROVED) {
            throw new BusinessException("Campaign ID can only be updated for approved campaigns");
        }
        PriceOffDisplayStatus displayStatus = PriceOffStatusResolver.resolve(campaign);
        if (displayStatus != PriceOffDisplayStatus.APPROVED
                && displayStatus != PriceOffDisplayStatus.PENDING_ACTIVATION) {
            throw new BusinessException("Campaign ID can only be updated for approved or pending activation campaigns");
        }
    }

    private void applyCampaignId(ConsumerPriceOffCampaign campaign, String campaignId, Long currentUserId) {
        campaign.setCampaignId(campaignId);
        campaign.setCampaignIdUpdatedAt(LocalDateTime.now());
        campaign.setCampaignIdUpdatedByUserId(currentUserId);
        campaign.setUpdatedByUserId(currentUserId);
    }

    private Set<ChannelMaster> resolveChannels(
            String channelLabel,
            List<ChannelMaster> allChannels,
            Map<String, ChannelMaster> channelsByName) {
        if (PriceOffExcelLayout.ALL_CHANNELS.equalsIgnoreCase(channelLabel.trim())) {
            return new LinkedHashSet<>(allChannels);
        }
        ChannelMaster channel = channelsByName.get(channelLabel.trim().toLowerCase(Locale.ROOT));
        if (channel == null) {
            throw new BusinessException("Channel '" + channelLabel + "' is not valid");
        }
        return Set.of(channel);
    }

    private ConsumerPriceOffCampaignResponse toResponse(ConsumerPriceOffCampaign campaign) {
        List<String> stateNames = campaign.getStates().stream()
                .sorted()
                .toList();
        List<String> channelNames = campaign.getChannels().stream().map(ChannelMaster::getChannelName).sorted().toList();
        Map<String, Integer> locationAllocations = campaign.getLocationAllocations().stream()
                .sorted((left, right) -> left.getLocationCode().compareToIgnoreCase(right.getLocationCode()))
                .collect(Collectors.toMap(
                        allocation -> allocation.getLocationCode(),
                        allocation -> allocation.getAllocatedQty(),
                        (left, right) -> left,
                        LinkedHashMap::new));

        return new ConsumerPriceOffCampaignResponse(
                campaign.getId(),
                campaign.getProductId(),
                campaign.getProductId(),
                campaign.getProductName(),
                campaign.getManufacturerName(),
                campaign.getL3Category(),
                campaign.getStartDate(),
                campaign.getEndDate(),
                campaign.getDurationMonths(),
                campaign.getMaxUnitCap(),
                campaign.getFromQty(),
                campaign.getTotalQty(),
                campaign.getCreditNote(),
                locationAllocations,
                campaign.getRemarks(),
                campaign.getCampaignId(),
                campaign.getCampaignIdUpdatedAt(),
                campaign.getCampaignIdUpdatedByUserId(),
                campaign.getLocationLabel(),
                stateNames,
                campaign.getChannelLabel(),
                channelNames,
                campaign.getDiscountType(),
                campaign.getDiscountType() != null ? campaign.getDiscountType().getLabel() : null,
                campaign.getCp(),
                campaign.getMrp(),
                campaign.getBaseOffer(),
                campaign.getMedplusContribution(),
                campaign.getMarginPercent(),
                campaign.getFinalOffer(),
                campaign.getPercentOff(),
                campaign.getFinalMarginPercent(),
                campaign.getIsNegativeMargin(),
                campaign.getApprovalStatus(),
                PriceOffStatusResolver.resolve(campaign),
                campaign.getUnitsConsumed(),
                campaign.getSubmittedByUserId(),
                campaign.getApprovedByUserId(),
                campaign.getRejectionRemarks(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt());
    }

    private List<ConsumerPriceOffCampaign> loadCampaigns(List<Long> ids) {
        List<ConsumerPriceOffCampaign> campaigns = campaignRepository.findByIdIn(ids);
        if (campaigns.size() != ids.size()) {
            throw new ResourceNotFoundException("One or more price off campaigns were not found");
        }
        return campaigns;
    }

    private ConsumerPriceOffCampaign loadCampaign(Long id) {
        return campaignRepository.findByIdWithProduct(id)
                .orElseThrow(() -> new ResourceNotFoundException("Price off campaign not found: " + id));
    }

    private ConsumerPriceOffCampaign loadCampaignForUpdate(Long id) {
        return campaignRepository.findByIdWithProductAndAllocations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Price off campaign not found: " + id));
    }

    private void mergeLocationAllocations(
            ConsumerPriceOffCampaign campaign,
            Map<String, Integer> incomingAllocations) {
        campaign.getLocationAllocations().removeIf(existing -> {
            String existingCode = existing.getLocationCode().toUpperCase(Locale.ROOT);
            Integer incomingQty = incomingAllocations.get(existingCode);
            return incomingQty == null || incomingQty <= 0;
        });

        incomingAllocations.forEach((locCode, newQty) -> {
            if (newQty == null || newQty <= 0) {
                return;
            }
            campaign.getLocationAllocations().stream()
                    .filter(allocation -> allocation.getLocationCode().equalsIgnoreCase(locCode))
                    .findFirst()
                    .ifPresentOrElse(
                            existing -> {
                                existing.setLocationCode(locCode);
                                existing.setAllocatedQty(newQty);
                            },
                            () -> campaign.getLocationAllocations().add(
                                    ConsumerPriceOffCampaignLocationAllocation.builder()
                                            .campaign(campaign)
                                            .locationCode(locCode)
                                            .allocatedQty(newQty)
                                            .build()));
        });
    }

    private String normalizeCampaignId(String campaignId) {
        if (campaignId == null || campaignId.isBlank()) {
            return null;
        }
        return campaignId.trim();
    }

    private List<String> buildChannelOptions(List<ChannelMaster> channels) {
        List<String> options = new ArrayList<>();
        options.add(PriceOffExcelLayout.ALL_CHANNELS);
        channels.forEach(channel -> options.add(channel.getChannelName()));
        return options;
    }

    private int calculateDurationMonths(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        java.time.Period period = java.time.Period.between(startDate, endDate);
        int months = period.getYears() * 12 + period.getMonths();
        if (period.getDays() > 0) {
            months++;
        }
        return Math.max(1, months);
    }

    private int sumLocationAllocations(ConsumerPriceOffCampaign campaign) {
        return campaign.getLocationAllocations().stream()
                .mapToInt(ConsumerPriceOffCampaignLocationAllocation::getAllocatedQty)
                .sum();
    }

    private void applyDerivedFields(ConsumerPriceOffCampaign campaign, int totalQty) {
        String discountTypeLabel = campaign.getDiscountType() != null
                ? campaign.getDiscountType().getLabel()
                : null;
        PriceOffCalculationUtil.DerivedFields derived = PriceOffCalculationUtil.calculateDerivedFields(
                campaign.getDiscountType(),
                discountTypeLabel,
                campaign.getCp(),
                campaign.getMrp(),
                campaign.getBaseOffer(),
                campaign.getMedplusContribution(),
                totalQty);
        campaign.setTotalQty(derived.totalQty());
        campaign.setCreditNote(derived.creditNote());
        campaign.setMarginPercent(derived.marginPercent());
        campaign.setFinalOffer(derived.finalOffer());
        campaign.setPercentOff(derived.percentOff());
        campaign.setFinalMarginPercent(derived.finalMarginPercent());
        campaign.setIsNegativeMargin(derived.negativeMargin());
    }

    private Map<String, Integer> normalizeLocationAllocations(Map<String, Integer> locationAllocations) {
        if (locationAllocations == null || locationAllocations.isEmpty()) {
            throw new BusinessException("At least one location allocation is required");
        }
        Map<String, Integer> normalized = new LinkedHashMap<>();
        locationAllocations.forEach((code, qty) -> {
            if (code == null || code.isBlank() || qty == null || qty <= 0) {
                return;
            }
            if (qty < 0) {
                throw new BusinessException("Allocation for zone '" + code + "' cannot be negative");
            }
            normalized.put(code.trim().toUpperCase(Locale.ROOT), qty);
        });
        if (normalized.isEmpty()) {
            throw new BusinessException("At least one location allocation must be greater than zero");
        }
        return normalized;
    }

    private PriceOffDiscountType mapMasterToCampaignDiscountType(DiscountTypeMaster master) {
        return switch (master.getCalculationKind()) {
            case PERCENTAGE -> PriceOffDiscountType.DISC_PERCENT;
            case FIXED_AMOUNT -> PriceOffDiscountType.DISC_VAL;
        };
    }
}
