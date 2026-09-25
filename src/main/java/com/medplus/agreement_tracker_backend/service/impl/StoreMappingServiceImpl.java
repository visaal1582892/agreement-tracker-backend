package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.constants.IncomeTypeNames;
import com.medplus.agreement_tracker_backend.dto.response.AgreementStoreMappingResponse;
import com.medplus.agreement_tracker_backend.dto.response.ParsedStoreMappingsDto;
import com.medplus.agreement_tracker_backend.dto.response.StoreUploadResultDto;
import com.medplus.agreement_tracker_backend.entity.Agreement;
import com.medplus.agreement_tracker_backend.entity.AgreementStoreMapping;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.enums.ApprovalStatus;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.exception.ExcelParseValidationException;
import com.medplus.agreement_tracker_backend.exception.ResourceNotFoundException;
import com.medplus.agreement_tracker_backend.exception.UnauthorizedException;
import com.medplus.agreement_tracker_backend.entity.AgreementLocation;
import com.medplus.agreement_tracker_backend.repository.AgreementLocationRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementStoreMappingRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVersionRepository;
import com.medplus.agreement_tracker_backend.service.CommercialVersionGuard;
import com.medplus.agreement_tracker_backend.service.StoreMappingService;
import com.medplus.agreement_tracker_backend.integration.StoreLocationService;
import com.medplus.agreement_tracker_backend.integration.dto.StoreLocationDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class StoreMappingServiceImpl implements StoreMappingService {

    private final AgreementVersionRepository agreementVersionRepository;
    private final AgreementStoreMappingRepository mappingRepository;
    private final CommercialVersionGuard commercialVersionGuard;
    private final StoreLocationService storeLocationService;
    private final AgreementLocationRepository agreementLocationRepository;

    @Override
    @Transactional(readOnly = true)
    public byte[] generateTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Store Codes");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Store Code");
            sheet.autoSizeColumn(0);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("Failed to generate store mapping template");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AgreementStoreMappingResponse> searchMappings(Long agreementVersionId, Long currentUserId, String search, Pageable pageable) {
        AgreementVersion version = agreementVersionRepository.findById(agreementVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", agreementVersionId));
        
        List<AgreementStoreMapping> mappings = mappingRepository.findByAgreementVersionIdOrderByStoreIdAsc(version.getId());
        if (mappings.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        List<AgreementStoreMapping> customMappings = mappings.stream()
                .filter(AgreementStoreMapping::isCustom)
                .toList();

        Set<String> posStoreIds = mappings.stream()
                .filter(m -> !m.isCustom())
                .map(AgreementStoreMapping::getStoreId)
                .filter(code -> {
                    if (code == null || code.isBlank()) {
                        log.warn("Encountered store mapping with blank store_id for agreement version {}", agreementVersionId);
                        return false;
                    }
                    return true;
                })
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Long> codeToMappingId = mappings.stream()
                .filter(m -> m.getStoreId() != null && !m.getStoreId().isBlank())
                .collect(Collectors.toMap(
                        m -> m.getStoreId().trim().toUpperCase(),
                        AgreementStoreMapping::getId,
                        (existing, replacement) -> existing));

        List<StoreLocationDetails> pagedStores = new ArrayList<>();
        if (!posStoreIds.isEmpty()) {
            if (search != null && !search.isBlank()) {
                pagedStores = storeLocationService.searchActiveStoreLocations(posStoreIds, search, org.springframework.data.domain.PageRequest.of(0, 1000000)).getContent();
            } else {
                pagedStores = storeLocationService.getActiveStoreLocations(posStoreIds);
            }
        }

        List<AgreementStoreMappingResponse> responses = new ArrayList<>();
        for (StoreLocationDetails store : pagedStores) {
            responses.add(new AgreementStoreMappingResponse(
                codeToMappingId.get(store.getStoreId() != null ? store.getStoreId().trim().toUpperCase() : null),
                store.getStoreId(),
                store.getName(),
                store.getAddress(),
                store.getPinCode(),
                store.getRegion1(),
                store.getRegion2(),
                store.getRegion3(),
                false
            ));
        }

        for (AgreementStoreMapping custom : customMappings) {
            boolean matches = search == null || search.isBlank() || 
                (custom.getStoreId() != null && custom.getStoreId().toLowerCase().contains(search.toLowerCase())) ||
                (custom.getName() != null && custom.getName().toLowerCase().contains(search.toLowerCase()));
            
            if (matches) {
                responses.add(new AgreementStoreMappingResponse(
                    custom.getId(),
                    custom.getStoreId(),
                    custom.getName(),
                    custom.getAddress(),
                    custom.getPinCode(),
                    custom.getRegion1(),
                    custom.getRegion2(),
                    custom.getRegion3(),
                    true
                ));
            }
        }

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), responses.size());
        List<AgreementStoreMappingResponse> pagedResponses = start <= end && start < responses.size() 
            ? responses.subList(start, end) 
            : Collections.emptyList();

        return new PageImpl<>(pagedResponses, pageable, responses.size());
    }

    @Override
    public StoreUploadResultDto uploadMappings(
            Long agreementVersionId,
            MultipartFile file,
            Long currentUserId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Uploaded Excel file is required");
        }

        AgreementVersion version = loadDraftVersion(agreementVersionId, currentUserId);
        boolean enforceGeoScope = !isAssetRentalVersion(version);
        Set<String> scopedStateCodes = enforceGeoScope
                ? resolveScopedPartnerStateCodes(version.getAgreement())
                : Set.of();
        if (enforceGeoScope && scopedStateCodes.isEmpty()) {
            throw new BusinessException("Agreement state is required before uploading store mappings");
        }

        List<String> storeIds = parseStoreIds(file);
        if (storeIds.isEmpty()) {
            throw new BusinessException("No store codes found in uploaded file");
        }

        List<StoreLocationDetails> posStores = storeLocationService.getActiveStoreLocations(new LinkedHashSet<>(storeIds));
        Map<String, StoreLocationDetails> storeIdToDetails = posStores.stream()
                .collect(Collectors.toMap(
                        s -> s.getStoreId().trim().toUpperCase(),
                        s -> s,
                        (existing, replacement) -> existing));

        List<StoreUploadResultDto.StoreUploadError> skippedStores = new ArrayList<>();
        List<StoreLocationDetails> validStoresToMap = new ArrayList<>();
        Set<String> seenStoreIds = new LinkedHashSet<>();

        for (String code : storeIds) {
            String upperCode = code.trim().toUpperCase();
            StoreLocationDetails store = storeIdToDetails.get(upperCode);
            if (store == null) {
                skippedStores.add(new StoreUploadResultDto.StoreUploadError(
                        code,
                        "Store ID not found in database or inactive"));
                continue;
            }

            if (enforceGeoScope) {
                String storeState = store.getRegion2();
                if (storeState == null
                        || scopedStateCodes.stream().noneMatch(c -> c.equalsIgnoreCase(storeState.trim()))) {
                    skippedStores.add(new StoreUploadResultDto.StoreUploadError(
                            code,
                            "Store belongs to '" + (storeState != null ? storeState : "unknown")
                                    + "' which is outside the Step 2 Geography scope"));
                    continue;
                }
            }

            if (seenStoreIds.add(store.getStoreId())) {
                validStoresToMap.add(store);
            }
        }

        List<AgreementStoreMapping> mappingsToSave = new ArrayList<>();
        for (StoreLocationDetails store : validStoresToMap) {
            if (!mappingRepository.existsByAgreementVersionIdAndStoreId(version.getId(), store.getStoreId())) {
                mappingsToSave.add(AgreementStoreMapping.builder()
                        .agreementVersion(version)
                        .storeId(store.getStoreId())
                        .name(store.getName())
                        .address(store.getAddress())
                        .pinCode(store.getPinCode())
                        .region1(store.getRegion1())
                        .region2(store.getRegion2())
                        .region3(store.getRegion3())
                        .isCustom(false)
                        .build());
            }
        }
        if (!mappingsToSave.isEmpty()) {
            mappingRepository.saveAll(mappingsToSave);
        }

        List<AgreementStoreMappingResponse> successfullyMapped = validStoresToMap.stream()
                .map(store -> mappingRepository
                        .findByAgreementVersionIdAndStoreId(version.getId(), store.getStoreId())
                        .map(mapping -> new AgreementStoreMappingResponse(
                                mapping.getId(),
                                store.getStoreId(),
                                store.getName(),
                                store.getAddress(),
                                store.getPinCode(),
                                store.getRegion1(),
                                store.getRegion2(),
                                store.getRegion3(),
                                false))
                        .orElse(null))
                .filter(response -> response != null)
                .toList();

        StoreUploadResultDto result = new StoreUploadResultDto();
        result.setSuccessfullyMapped(new ArrayList<>(successfullyMapped));
        result.setSkippedStores(skippedStores);
        result.setTotalAttempted(storeIds.size());
        return result;
    }

    @Override
    public AgreementStoreMappingResponse addCustomStore(
            Long agreementVersionId,
            com.medplus.agreement_tracker_backend.dto.request.AgreementStoreDto request,
            Long currentUserId) {
        AgreementVersion version = loadDraftVersion(agreementVersionId, currentUserId);
        
        if (mappingRepository.existsByAgreementVersionIdAndStoreId(version.getId(), request.getStoreId())) {
            throw new BusinessException("Store ID " + request.getStoreId() + " is already mapped to this agreement.");
        }
        
        AgreementStoreMapping mapping = AgreementStoreMapping.builder()
                .agreementVersion(version)
                .storeId(request.getStoreId())
                .name(request.getName())
                .address(request.getAddress())
                .pinCode(request.getPinCode())
                .region1(request.getRegion1())
                .region2(request.getRegion2())
                .region3(request.getRegion3())
                .isCustom(true)
                .build();
                
        mapping = mappingRepository.save(mapping);
        
        return new AgreementStoreMappingResponse(
                mapping.getId(),
                mapping.getStoreId(),
                mapping.getName(),
                mapping.getAddress(),
                mapping.getPinCode(),
                mapping.getRegion1(),
                mapping.getRegion2(),
                mapping.getRegion3(),
                true
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ParsedStoreMappingsDto parseMappingsStateless(
            Long sourceVersionId,
            MultipartFile file,
            Long currentUserId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Uploaded Excel file is required");
        }

        AgreementVersion version = loadOwnedVersion(sourceVersionId, currentUserId);
        boolean enforceGeoScope = !isAssetRentalVersion(version);
        Set<String> scopedStateCodes = enforceGeoScope
                ? resolveScopedPartnerStateCodes(version.getAgreement())
                : Set.of();
        if (enforceGeoScope && scopedStateCodes.isEmpty()) {
            throw new BusinessException("Agreement state is required before uploading store mappings");
        }

        List<StoreIdRow> storeIds = parseStoreIdsWithRows(file);
        if (storeIds.isEmpty()) {
            throw new BusinessException("No store codes found in uploaded file");
        }

        Set<String> codesToQuery = storeIds.stream().map(StoreIdRow::code).collect(Collectors.toCollection(LinkedHashSet::new));
        List<StoreLocationDetails> posStores = storeLocationService.getActiveStoreLocations(codesToQuery);
        Map<String, StoreLocationDetails> storeIdToDetails = posStores.stream()
                .collect(Collectors.toMap(
                        s -> s.getStoreId().trim().toUpperCase(),
                        s -> s,
                        (existing, replacement) -> existing));

        List<ParsedStoreMappingsDto.RowError> errors = new ArrayList<>();
        List<AgreementStoreMappingResponse> validStores = new ArrayList<>();
        Set<String> seenStoreIds = new LinkedHashSet<>();

        for (StoreIdRow entry : storeIds) {
            String code = entry.code();
            String upperCode = code.trim().toUpperCase();
            StoreLocationDetails store = storeIdToDetails.get(upperCode);
            if (store == null) {
                errors.add(new ParsedStoreMappingsDto.RowError(
                        entry.rowIndex(), code, "Store ID not found in database or inactive"));
                continue;
            }

            if (enforceGeoScope) {
                String storeState = store.getRegion2();
                if (storeState == null
                        || scopedStateCodes.stream().noneMatch(c -> c.equalsIgnoreCase(storeState.trim()))) {
                    errors.add(new ParsedStoreMappingsDto.RowError(
                            entry.rowIndex(),
                            code,
                            "Store belongs to '" + (storeState != null ? storeState : "unknown")
                                    + "' which is outside the Step 2 Geography scope"));
                    continue;
                }
            }

            if (seenStoreIds.add(store.getStoreId())) {
                validStores.add(new AgreementStoreMappingResponse(
                        null,
                        store.getStoreId(),
                        store.getName(),
                        store.getAddress(),
                        store.getPinCode(),
                        store.getRegion1(),
                        store.getRegion2(),
                        store.getRegion3(),
                        false));
            }
        }

        if (!errors.isEmpty() && validStores.isEmpty()) {
            throw new ExcelParseValidationException(
                    "Store mapping validation failed",
                    errors.stream()
                            .map(e -> new ExcelParseValidationException.RowError(
                                    "Store Codes", e.row(), e.message()))
                            .toList());
        }

        ParsedStoreMappingsDto result = new ParsedStoreMappingsDto();
        result.setSuccessfullyMapped(validStores);
        result.setErrors(errors);
        result.setTotalAttempted(storeIds.size());
        return result;
    }

    @Override
    public void deleteMappings(Long agreementVersionId, List<Long> mappingIds, Long currentUserId) {
        AgreementVersion version = loadDraftVersion(agreementVersionId, currentUserId);
        mappingRepository.deleteByAgreementVersionIdAndIdIn(version.getId(), mappingIds);
    }

    @Override
    public void copyMappings(Long sourceVersionId, Long targetVersionId) {
        AgreementVersion target = agreementVersionRepository.findById(targetVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", targetVersionId));
        mappingRepository.deleteByAgreementVersionId(targetVersionId);

        List<AgreementStoreMapping> sourceMappings =
                mappingRepository.findByAgreementVersionIdOrderByStoreIdAsc(sourceVersionId);
        for (AgreementStoreMapping sourceMapping : sourceMappings) {
            mappingRepository.save(AgreementStoreMapping.builder()
                    .agreementVersion(target)
                    .storeId(sourceMapping.getStoreId())
                    .name(sourceMapping.getName())
                    .address(sourceMapping.getAddress())
                    .pinCode(sourceMapping.getPinCode())
                    .region1(sourceMapping.getRegion1())
                    .region2(sourceMapping.getRegion2())
                    .region3(sourceMapping.getRegion3())
                    .isCustom(sourceMapping.isCustom())
                    .build());
        }
    }

    @Override
    public void replaceMappingsFromStoreIds(Long targetVersionId, List<com.medplus.agreement_tracker_backend.dto.request.CommercialDataPayload.StoreMappingSubmitDto> storeMappings) {
        if (storeMappings == null) {
            return;
        }
        AgreementVersion target = agreementVersionRepository.findById(targetVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", targetVersionId));
        mappingRepository.deleteByAgreementVersionId(targetVersionId);
        if (storeMappings.isEmpty()) {
            return;
        }
        
        List<AgreementStoreMapping> mappingsToSave = new ArrayList<>();
        Set<String> uniqueCodes = new LinkedHashSet<>();
        
        List<String> nonCustomIds = storeMappings.stream()
                .filter(s -> !s.isCustom() && s.storeId() != null)
                .map(com.medplus.agreement_tracker_backend.dto.request.CommercialDataPayload.StoreMappingSubmitDto::storeId)
                .toList();

        java.util.Map<String, com.medplus.agreement_tracker_backend.integration.dto.StoreLocationDetails> posStoreMap = new java.util.HashMap<>();
        if (!nonCustomIds.isEmpty()) {
            List<com.medplus.agreement_tracker_backend.integration.dto.StoreLocationDetails> posStores = storeLocationService.getActiveStoreLocations(new LinkedHashSet<>(nonCustomIds));
            for (com.medplus.agreement_tracker_backend.integration.dto.StoreLocationDetails s : posStores) {
                posStoreMap.put(s.getStoreId().trim().toUpperCase(), s);
            }
        }

        for (com.medplus.agreement_tracker_backend.dto.request.CommercialDataPayload.StoreMappingSubmitDto store : storeMappings) {
            if (store.storeId() != null && uniqueCodes.add(store.storeId())) {
                if (store.isCustom()) {
                    mappingsToSave.add(AgreementStoreMapping.builder()
                            .agreementVersion(target)
                            .storeId(store.storeId())
                            .name(store.name())
                            .address(store.address())
                            .pinCode(store.pinCode())
                            .region1(store.region1())
                            .region2(store.region2())
                            .region3(store.region3())
                            .isCustom(true)
                            .build());
                } else {
                    com.medplus.agreement_tracker_backend.integration.dto.StoreLocationDetails posDetails = posStoreMap.get(store.storeId().trim().toUpperCase());
                    if (posDetails != null) {
                        mappingsToSave.add(AgreementStoreMapping.builder()
                                .agreementVersion(target)
                                .storeId(posDetails.getStoreId())
                                .name(posDetails.getName())
                                .address(posDetails.getAddress())
                                .pinCode(posDetails.getPinCode())
                                .region1(posDetails.getRegion1())
                                .region2(posDetails.getRegion2())
                                .region3(posDetails.getRegion3())
                                .isCustom(false)
                                .build());
                    }
                }
            }
        }
        mappingRepository.saveAll(mappingsToSave);
    }

    private AgreementVersion loadOwnedVersion(Long agreementVersionId, Long currentUserId) {
        AgreementVersion version = agreementVersionRepository.findById(agreementVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", agreementVersionId));
        if (!version.getAgreement().getOwner().getId().equals(currentUserId)) {
            throw new UnauthorizedException("You are not the owner of this agreement");
        }
        return version;
    }

    private AgreementVersion loadDraftVersion(Long agreementVersionId, Long currentUserId) {
        AgreementVersion version = loadOwnedVersion(agreementVersionId, currentUserId);
        if (version.getApprovalStatus() != ApprovalStatus.DRAFT) {
            throw new BusinessException("Store mappings can only be modified on draft versions");
        }
        return version;
    }

    private List<String> parseStoreIds(MultipartFile file) {
        return parseStoreIdsWithRows(file).stream()
                .map(StoreIdRow::code)
                .toList();
    }

    private List<StoreIdRow> parseStoreIdsWithRows(MultipartFile file) {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessException("Excel file does not contain any worksheets");
            }
            List<StoreIdRow> codes = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || row.getCell(0) == null) {
                    continue;
                }
                String code = readCellAsString(row.getCell(0));
                if (!code.isBlank() && seen.add(code.trim())) {
                    codes.add(new StoreIdRow(rowIndex, code.trim()));
                }
            }
            return codes;
        } catch (IOException ex) {
            throw new BusinessException("Failed to parse uploaded Excel file");
        }
    }

    private record StoreIdRow(int rowIndex, String code) {}

    private String readCellAsString(org.apache.poi.ss.usermodel.Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private Set<String> resolveScopedPartnerStateCodes(Agreement agreement) {
        if (agreement == null) {
            return Set.of();
        }
        // Load from the current version's location rows
        AgreementVersion currentVersion = agreement.getCurrentVersionId() != null
                ? agreementVersionRepository.findById(agreement.getCurrentVersionId()).orElse(null)
                : null;
        if (currentVersion == null) {
            return Set.of();
        }
        List<AgreementLocation> locs = agreementLocationRepository.findByAgreementVersionId(currentVersion.getId());
        Set<String> codes = new LinkedHashSet<>();
        for (AgreementLocation loc : locs) {
            if ("STATE".equalsIgnoreCase(loc.getLocationType()) && loc.getStateCode() != null) {
                codes.add(loc.getStateCode().trim());
            } else if ("CITY".equalsIgnoreCase(loc.getLocationType()) && loc.getStateCode() != null) {
                // cities carry their parent state code for state-level filtering
                codes.add(loc.getStateCode().trim());
            } else if ("COUNTRY".equalsIgnoreCase(loc.getLocationType()) && loc.getCountryCode() != null) {
                codes.add(loc.getCountryCode().trim());
            }
        }
        return codes;
    }

    /** Asset Rentals scope = storeIds only; partner-state geo checks do not apply. */
    private boolean isAssetRentalVersion(AgreementVersion version) {
        if (version == null || version.getIncomeType() == null || version.getIncomeType().getName() == null) {
            return false;
        }
        return IncomeTypeNames.ASSET_RENTALS.equalsIgnoreCase(version.getIncomeType().getName());
    }
}
