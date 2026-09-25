package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.dto.response.PriceOffExcelParseResult;
import com.medplus.agreement_tracker_backend.dto.response.PriceOffParsedRowDto;
import com.medplus.agreement_tracker_backend.dto.response.PriceOffPreviewResponse;
import com.medplus.agreement_tracker_backend.dto.response.PriceOffPreviewRowDto;
import com.medplus.agreement_tracker_backend.entity.ConsumerPriceOffCampaign;
import com.medplus.agreement_tracker_backend.integration.ProductMasterIntegrationService;
import com.medplus.agreement_tracker_backend.integration.dto.IntegrationProductResponse;
import com.medplus.agreement_tracker_backend.repository.ConsumerPriceOffCampaignRepository;
import com.medplus.agreement_tracker_backend.util.PriceOffExcelLayout;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PriceOffBulkPreviewEngine {

    private static final String OVERLAP_MESSAGE =
            "An active campaign already exists for this product during this date range.";
    private static final String IN_FILE_OVERLAP_MESSAGE =
            "Duplicate overlapping date range for this product in the upload file.";

    private final ProductMasterIntegrationService productMasterIntegrationService;
    private final ConsumerPriceOffCampaignRepository campaignRepository;

    public PriceOffPreviewResponse buildPreview(PriceOffExcelParseResult parseResult) {
        Map<Integer, List<String>> errorsByRow = new HashMap<>();
        parseResult.rowErrors().forEach((rowIdx, message) ->
                errorsByRow.computeIfAbsent(rowIdx + 1, key -> new ArrayList<>()).add(message));

        List<PriceOffParsedRowDto> structuralRows = parseResult.validRows();
        if (structuralRows.size() > PriceOffExcelLayout.MAX_DATA_ROWS) {
            throw new com.medplus.agreement_tracker_backend.exception.BusinessException(
                    "Upload exceeds maximum of " + PriceOffExcelLayout.MAX_DATA_ROWS + " rows");
        }

        List<String> productIds = structuralRows.stream()
                .map(PriceOffParsedRowDto::productId)
                .filter(id -> id != null && !id.isBlank())
                .map(id -> id.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        Map<String, IntegrationProductResponse> productMap =
                productMasterIntegrationService.getProductsByCodes(productIds);

        Map<String, List<ConsumerPriceOffCampaign>> existingByProduct = productIds.isEmpty()
                ? Map.of()
                : campaignRepository.findActiveCampaignsByProductIds(productIds).stream()
                .collect(Collectors.groupingBy(
                        campaign -> campaign.getProductId().toUpperCase(Locale.ROOT),
                        LinkedHashMap::new,
                        Collectors.toList()));

        Map<String, List<PriceOffParsedRowDto>> rowsByProduct = structuralRows.stream()
                .collect(Collectors.groupingBy(
                        row -> row.productId().trim().toUpperCase(Locale.ROOT),
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (Map.Entry<String, List<PriceOffParsedRowDto>> entry : rowsByProduct.entrySet()) {
            List<PriceOffParsedRowDto> sameProduct = entry.getValue();
            for (int left = 0; left < sameProduct.size(); left++) {
                PriceOffParsedRowDto first = sameProduct.get(left);
                for (int right = left + 1; right < sameProduct.size(); right++) {
                    PriceOffParsedRowDto second = sameProduct.get(right);
                    if (datesOverlap(first.startDate(), first.endDate(), second.startDate(), second.endDate())) {
                        errorsByRow.computeIfAbsent(first.rowNumber(), key -> new ArrayList<>())
                                .add(IN_FILE_OVERLAP_MESSAGE);
                        errorsByRow.computeIfAbsent(second.rowNumber(), key -> new ArrayList<>())
                                .add(IN_FILE_OVERLAP_MESSAGE);
                    }
                }
            }
        }

        List<PriceOffPreviewRowDto> previewRows = new ArrayList<>();
        for (PriceOffParsedRowDto row : structuralRows) {
            List<String> errors = new ArrayList<>(
                    errorsByRow.getOrDefault(row.rowNumber(), List.of()));
            String productKey = row.productId().trim().toUpperCase(Locale.ROOT);
            IntegrationProductResponse product = productMap.get(productKey);
            if (product == null) {
                errors.add("Product ID [" + row.productId() + "] not found");
            }

            List<ConsumerPriceOffCampaign> existing = existingByProduct.getOrDefault(productKey, List.of());
            for (ConsumerPriceOffCampaign campaign : existing) {
                if (datesOverlap(row.startDate(), row.endDate(), campaign.getStartDate(), campaign.getEndDate())) {
                    errors.add(OVERLAP_MESSAGE);
                    break;
                }
            }

            List<String> distinctErrors = errors.stream().distinct().toList();
            boolean valid = distinctErrors.isEmpty();
            previewRows.add(new PriceOffPreviewRowDto(
                    row.rowNumber(),
                    row.productId(),
                    product != null ? product.getProductName() : row.productName(),
                    product != null ? product.getManufacturerName() : null,
                    product != null ? product.getL3Category() : null,
                    row.startDate(),
                    row.endDate(),
                    row.maxUnitCap(),
                    row.locationLabel(),
                    row.channelLabel(),
                    row.discountType(),
                    row.discountTypeLabel(),
                    row.cp(),
                    row.mrp(),
                    row.baseOffer(),
                    row.medplusContribution(),
                    row.fromQty(),
                    row.remarks(),
                    row.locationAllocations(),
                    row.totalQty(),
                    row.creditNote(),
                    row.marginPercent(),
                    row.finalOffer(),
                    row.percentOff(),
                    row.finalMarginPercent(),
                    row.negativeMargin(),
                    valid,
                    distinctErrors));
        }

        // Include structural-only error rows that never made it into validRows
        for (Map.Entry<Integer, String> entry : parseResult.rowErrors().entrySet()) {
            int displayRow = entry.getKey() + 1;
            boolean alreadyPresent = previewRows.stream().anyMatch(row -> row.rowNumber() == displayRow);
            if (!alreadyPresent) {
                previewRows.add(emptyErrorRow(displayRow, entry.getValue()));
            }
        }

        previewRows.sort(java.util.Comparator.comparingInt(PriceOffPreviewRowDto::rowNumber));
        int errorRows = (int) previewRows.stream().filter(row -> !row.valid()).count();
        int validRows = previewRows.size() - errorRows;
        return new PriceOffPreviewResponse(
                previewRows.size(),
                validRows,
                errorRows,
                errorRows == 0 && validRows > 0,
                previewRows);
    }

    /**
     * Commit-time validation only: overlap checks against DB + in-file.
     * Skips Product Microservice — preview already validated product IDs.
     */
    public void validateCommitRows(List<PriceOffPreviewRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new com.medplus.agreement_tracker_backend.exception.BusinessException(
                    "No campaign rows to commit");
        }

        List<PriceOffPreviewRowDto> invalid = rows.stream()
                .filter(row -> row == null || !row.valid()
                        || (row.errors() != null && !row.errors().isEmpty()))
                .toList();
        if (!invalid.isEmpty()) {
            throw new com.medplus.agreement_tracker_backend.exception.BusinessException(
                    "Cannot commit while " + invalid.size() + " row(s) still have validation errors");
        }

        Map<String, List<PriceOffPreviewRowDto>> rowsByProduct = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> row.productId().trim().toUpperCase(Locale.ROOT),
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (List<PriceOffPreviewRowDto> sameProduct : rowsByProduct.values()) {
            for (int left = 0; left < sameProduct.size(); left++) {
                PriceOffPreviewRowDto first = sameProduct.get(left);
                for (int right = left + 1; right < sameProduct.size(); right++) {
                    PriceOffPreviewRowDto second = sameProduct.get(right);
                    if (datesOverlap(first.startDate(), first.endDate(),
                            second.startDate(), second.endDate())) {
                        throw new com.medplus.agreement_tracker_backend.exception.BusinessException(
                                "Validation failed on commit: overlapping date ranges for product "
                                        + first.productId() + " in rows " + first.rowNumber()
                                        + " and " + second.rowNumber());
                    }
                }
            }
        }

        List<String> productIds = rows.stream()
                .map(PriceOffPreviewRowDto::productId)
                .filter(id -> id != null && !id.isBlank())
                .map(id -> id.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        Map<String, List<ConsumerPriceOffCampaign>> existingByProduct = productIds.isEmpty()
                ? Map.of()
                : campaignRepository.findActiveCampaignsByProductIds(productIds).stream()
                .collect(Collectors.groupingBy(
                        campaign -> campaign.getProductId().toUpperCase(Locale.ROOT),
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (PriceOffPreviewRowDto row : rows) {
            String productKey = row.productId().trim().toUpperCase(Locale.ROOT);
            List<ConsumerPriceOffCampaign> existing = existingByProduct.getOrDefault(productKey, List.of());
            for (ConsumerPriceOffCampaign campaign : existing) {
                if (datesOverlap(row.startDate(), row.endDate(),
                        campaign.getStartDate(), campaign.getEndDate())) {
                    throw new com.medplus.agreement_tracker_backend.exception.BusinessException(
                            "Validation failed on commit: row " + row.rowNumber() + " — " + OVERLAP_MESSAGE);
                }
            }
        }
    }

    private static PriceOffPreviewRowDto emptyErrorRow(int rowNumber, String error) {
        return new PriceOffPreviewRowDto(
                rowNumber,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                List.of(error));
    }

    private static boolean datesOverlap(
            LocalDate firstStart,
            LocalDate firstEnd,
            LocalDate secondStart,
            LocalDate secondEnd) {
        if (firstStart == null || firstEnd == null || secondStart == null || secondEnd == null) {
            return false;
        }
        return !firstStart.isAfter(secondEnd) && !secondStart.isAfter(firstEnd);
    }
}
