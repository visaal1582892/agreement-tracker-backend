package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.dto.response.PriceOffExcelParseResult;
import com.medplus.agreement_tracker_backend.dto.response.PriceOffParsedRowDto;
import com.medplus.agreement_tracker_backend.entity.PriceOffLocationMaster;
import com.medplus.agreement_tracker_backend.enums.PriceOffDiscountType;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.repository.PriceOffLocationMasterRepository;
import com.medplus.agreement_tracker_backend.service.PriceOffCampaignValidationService;
import com.medplus.agreement_tracker_backend.service.PriceOffExcelParserService;
import com.medplus.agreement_tracker_backend.util.ExcelCellReader;
import com.medplus.agreement_tracker_backend.util.PriceOffCalculationEngine;
import com.medplus.agreement_tracker_backend.util.PriceOffCalculationUtil;
import com.medplus.agreement_tracker_backend.util.PriceOffExcelLayout;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PriceOffExcelParserServiceImpl implements PriceOffExcelParserService {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
    };

    private static final Set<String> FIXED_HEADERS = Set.of(
            PriceOffExcelLayout.COL_PRODUCT_ID,
            PriceOffExcelLayout.COL_START_DATE,
            PriceOffExcelLayout.COL_END_DATE,
            PriceOffExcelLayout.COL_MAX_UNIT_CAP,
            PriceOffExcelLayout.COL_LOCATION,
            "Locations",
            PriceOffExcelLayout.COL_CHANNEL,
            PriceOffExcelLayout.COL_DISCOUNT_TYPE,
            PriceOffExcelLayout.COL_CP,
            PriceOffExcelLayout.COL_MRP,
            PriceOffExcelLayout.COL_BASE_OFFER,
            PriceOffExcelLayout.COL_MEDPLUS_CONTRIBUTION,
            PriceOffExcelLayout.COL_FROM_QTY,
            PriceOffExcelLayout.COL_REMARKS);

    private final PriceOffLocationMasterRepository locationMasterRepository;
    private final PriceOffCampaignValidationService validationService;

    @Override
    @Transactional(readOnly = true)
    public PriceOffExcelParseResult parseWorkbook(Workbook workbook) {
        Sheet sheet = workbook.getSheet(PriceOffExcelLayout.SHEET_NAME);
        if (sheet == null) {
            sheet = workbook.getSheetAt(0);
        }

        List<PriceOffLocationMaster> activeLocations =
                locationMasterRepository.findByIsActiveTrueOrderByCodeAsc();
        List<String> locationColumnCodes = activeLocations.stream()
                .map(PriceOffLocationMaster::getCode)
                .toList();

        Map<String, Integer> headerIndex = readHeaderIndex(sheet.getRow(PriceOffExcelLayout.HEADER_ROW));
        validateRequiredHeaders(headerIndex);
        List<String> dynamicLocationHeaders = resolveDynamicLocationHeaders(headerIndex, locationColumnCodes);

        List<PriceOffParsedRowDto> validRows = new ArrayList<>();
        Map<Integer, String> rowErrors = new LinkedHashMap<>();

        int dataRowCount = 0;
        for (int rowIdx = PriceOffExcelLayout.DATA_START_ROW; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null || isRowBlank(row, headerIndex)) {
                continue;
            }
            dataRowCount++;
            if (dataRowCount > PriceOffExcelLayout.MAX_DATA_ROWS) {
                throw new BusinessException(
                        "Upload exceeds maximum of " + PriceOffExcelLayout.MAX_DATA_ROWS + " rows");
            }
            int displayRow = rowIdx + 1;
            try {
                validRows.add(parseRow(displayRow, row, headerIndex, dynamicLocationHeaders));
            } catch (BusinessException ex) {
                rowErrors.put(rowIdx, ex.getMessage());
            } catch (IllegalArgumentException ex) {
                rowErrors.put(rowIdx, ex.getMessage());
            }
        }

        if (validRows.isEmpty() && rowErrors.isEmpty()) {
            throw new BusinessException("No campaign rows found in uploaded file");
        }

        // Product existence + DB overlap validated later in bulk preview engine.
        return new PriceOffExcelParseResult(validRows, rowErrors);
    }

    private PriceOffParsedRowDto parseRow(
            int rowNumber,
            Row row,
            Map<String, Integer> headerIndex,
            List<String> dynamicLocationHeaders) {
        String productCode = readCell(row, headerIndex, PriceOffExcelLayout.COL_PRODUCT_ID);
        if (productCode.isBlank()) {
            throw new BusinessException("Product ID is required");
        }
        String normalizedProductId = productCode.trim();

        LocalDate startDate = parseDate(row, headerIndex, PriceOffExcelLayout.COL_START_DATE);
        if (startDate == null) {
            throw new BusinessException("Start Date is required");
        }

        LocalDate endDate = parseDate(row, headerIndex, PriceOffExcelLayout.COL_END_DATE);
        if (endDate == null) {
            throw new BusinessException("End Date is required");
        }
        validationService.validateCampaignDates(startDate, endDate);

        Integer maxUnitCap = parseInteger(row, headerIndex, PriceOffExcelLayout.COL_MAX_UNIT_CAP);
        Integer fromQty = parseInteger(row, headerIndex, PriceOffExcelLayout.COL_FROM_QTY);
        String remarks = readCell(row, headerIndex, PriceOffExcelLayout.COL_REMARKS);

        String locationLabel = readLocationLabel(row, headerIndex);
        if (locationLabel.isBlank()) {
            throw new BusinessException("Location is required");
        }

        String channelLabel = readCell(row, headerIndex, PriceOffExcelLayout.COL_CHANNEL);
        if (channelLabel.isBlank()) {
            throw new BusinessException("Channel is required");
        }

        String discountTypeRaw = readCell(row, headerIndex, PriceOffExcelLayout.COL_DISCOUNT_TYPE);
        PriceOffDiscountType discountType = parseDiscountType(row, headerIndex);

        BigDecimal cp = requirePositiveDecimal(row, headerIndex, PriceOffExcelLayout.COL_CP, "CP");
        BigDecimal mrp = requirePositiveDecimal(row, headerIndex, PriceOffExcelLayout.COL_MRP, "MRP");

        BigDecimal baseOffer = parseNonNegativeDecimal(
                row, headerIndex, PriceOffExcelLayout.COL_BASE_OFFER, "Base Offer");
        BigDecimal medplusContribution = parseNonNegativeDecimal(
                row, headerIndex, PriceOffExcelLayout.COL_MEDPLUS_CONTRIBUTION, "Medplus Contribution");

        validationService.validateDiscountSanity(discountType, mrp, baseOffer, medplusContribution);

        Map<String, Integer> locationAllocations = readLocationAllocations(row, headerIndex, dynamicLocationHeaders);

        int totalQty = locationAllocations.values().stream().mapToInt(Integer::intValue).sum();

        BigDecimal creditNote = PriceOffCalculationUtil.calculateCreditNote(
                totalQty,
                baseOffer,
                mrp,
                discountTypeRaw,
                discountType);

        PriceOffCalculationEngine.CalculatedFields calculated = PriceOffCalculationEngine.calculate(
                discountType, cp, mrp, baseOffer, medplusContribution);
        PriceOffCalculationUtil.DerivedFields derived = PriceOffCalculationUtil.calculateDerivedFields(
                discountType, discountType.getLabel(), cp, mrp, baseOffer, medplusContribution, totalQty);

        return new PriceOffParsedRowDto(
                rowNumber,
                normalizedProductId,
                normalizedProductId,
                normalizedProductId,
                startDate,
                endDate,
                maxUnitCap,
                locationLabel.trim(),
                channelLabel.trim(),
                discountType,
                discountType.getLabel(),
                cp,
                mrp,
                baseOffer,
                medplusContribution,
                fromQty,
                blankToNull(remarks),
                locationAllocations,
                totalQty,
                creditNote,
                calculated.marginPercent(),
                calculated.finalOffer(),
                calculated.percentOff(),
                derived.finalMarginPercent(),
                derived.negativeMargin());
    }

    private String readLocationLabel(Row row, Map<String, Integer> headerIndex) {
        if (headerIndex.containsKey(PriceOffExcelLayout.COL_LOCATION)) {
            return readCell(row, headerIndex, PriceOffExcelLayout.COL_LOCATION);
        }
        return readCell(row, headerIndex, "Locations");
    }

    private Map<String, Integer> readLocationAllocations(
            Row row,
            Map<String, Integer> headerIndex,
            List<String> dynamicLocationHeaders) {
        Map<String, Integer> allocations = new LinkedHashMap<>();
        for (String code : dynamicLocationHeaders) {
            Integer allocation = parseInteger(row, headerIndex, code);
            if (allocation == null) {
                continue;
            }
            if (allocation < 0) {
                throw new BusinessException("Allocation for zone '" + code + "' cannot be negative");
            }
            if (allocation > 0) {
                allocations.put(code, allocation);
            }
        }
        return allocations;
    }

    private List<String> resolveDynamicLocationHeaders(
            Map<String, Integer> headerIndex,
            List<String> knownLocationCodes) {
        Set<String> knownCodes = knownLocationCodes.stream()
                .map(code -> code.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<String> dynamicHeaders = new ArrayList<>();
        for (String header : headerIndex.keySet()) {
            if (FIXED_HEADERS.contains(header)) {
                continue;
            }
            if (knownCodes.contains(header.toUpperCase(Locale.ROOT))) {
                dynamicHeaders.add(header);
            }
        }
        return dynamicHeaders;
    }

    private void validateRequiredHeaders(Map<String, Integer> headerIndex) {
        List<String> required = List.of(
                PriceOffExcelLayout.COL_PRODUCT_ID,
                PriceOffExcelLayout.COL_START_DATE,
                PriceOffExcelLayout.COL_END_DATE,
                PriceOffExcelLayout.COL_CHANNEL,
                PriceOffExcelLayout.COL_DISCOUNT_TYPE,
                PriceOffExcelLayout.COL_CP,
                PriceOffExcelLayout.COL_MRP,
                PriceOffExcelLayout.COL_BASE_OFFER,
                PriceOffExcelLayout.COL_MEDPLUS_CONTRIBUTION);
        for (String header : required) {
            if (!headerIndex.containsKey(header)) {
                throw new BusinessException("Missing required column: " + header);
            }
        }
        if (!headerIndex.containsKey(PriceOffExcelLayout.COL_LOCATION)
                && !headerIndex.containsKey("Locations")) {
            throw new BusinessException("Missing required column: " + PriceOffExcelLayout.COL_LOCATION);
        }
    }

    private Map<String, Integer> readHeaderIndex(Row headerRow) {
        if (headerRow == null) {
            throw new BusinessException("Template header row is missing");
        }
        Map<String, Integer> index = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String label = ExcelCellReader.readAsString(cell);
            if (!label.isBlank()) {
                index.put(label, cell.getColumnIndex());
            }
        }
        return index;
    }

    private boolean isRowBlank(Row row, Map<String, Integer> headerIndex) {
        return headerIndex.values().stream().allMatch(col -> ExcelCellReader.isBlank(row.getCell(col)));
    }

    private String readCell(Row row, Map<String, Integer> headerIndex, String header) {
        Integer col = headerIndex.get(header);
        if (col == null) {
            return "";
        }
        return ExcelCellReader.readAsString(row.getCell(col));
    }

    private BigDecimal parseDecimal(Row row, Map<String, Integer> headerIndex, String header) {
        Integer col = headerIndex.get(header);
        if (col == null) {
            return null;
        }
        return ExcelCellReader.readAsDecimal(row.getCell(col));
    }

    private PriceOffDiscountType parseDiscountType(Row row, Map<String, Integer> headerIndex) {
        String typeStr = readCell(row, headerIndex, PriceOffExcelLayout.COL_DISCOUNT_TYPE).trim();
        if (typeStr.isEmpty()) {
            throw new BusinessException("Discount type is required");
        }
        if ("Disc_%".equalsIgnoreCase(typeStr)
                || "DISC_PERCENT".equalsIgnoreCase(typeStr)
                || "Disc %".equalsIgnoreCase(typeStr)
                || "Disc_Percent".equalsIgnoreCase(typeStr)) {
            return PriceOffDiscountType.DISC_PERCENT;
        }
        if ("Disc_Val".equalsIgnoreCase(typeStr)
                || "DISC_VAL".equalsIgnoreCase(typeStr)
                || "Disc Val".equalsIgnoreCase(typeStr)) {
            return PriceOffDiscountType.DISC_VAL;
        }
        return PriceOffDiscountType.fromLabel(typeStr);
    }

    private BigDecimal parseNonNegativeDecimal(Row row, Map<String, Integer> headerIndex, String header, String label) {
        BigDecimal value = parseDecimal(row, headerIndex, header);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.signum() < 0) {
            throw new BusinessException(label + " cannot be negative");
        }
        return value;
    }

    private BigDecimal requirePositiveDecimal(Row row, Map<String, Integer> headerIndex, String header, String label) {
        BigDecimal value = parseDecimal(row, headerIndex, header);
        if (value == null || value.signum() <= 0) {
            throw new BusinessException(label + " is required and must be greater than zero");
        }
        return value;
    }

    private Integer parseInteger(Row row, Map<String, Integer> headerIndex, String header) {
        BigDecimal decimal = parseDecimal(row, headerIndex, header);
        return decimal == null ? null : decimal.intValue();
    }

    private LocalDate parseDate(Row row, Map<String, Integer> headerIndex, String header) {
        Integer col = headerIndex.get(header);
        if (col == null) {
            return null;
        }
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String raw = ExcelCellReader.readAsString(cell);
        if (raw.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        throw new BusinessException("Invalid date format for Start Date: " + raw);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
