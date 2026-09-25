package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.dto.request.JbpWorkbookRequest;
import com.medplus.agreement_tracker_backend.dto.response.JbpStagedWorkbookDto;
import com.medplus.agreement_tracker_backend.dto.response.JbpStagedWorkbookDto.StagedSheet;
import com.medplus.agreement_tracker_backend.dto.response.JbpStagedWorkbookDto.UnpivotedRow;
import com.medplus.agreement_tracker_backend.entity.AgreementJbpConfiguration;
import com.medplus.agreement_tracker_backend.entity.AgreementTimePeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.enums.JbpValueType;
import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.exception.ExcelParseValidationException;
import com.medplus.agreement_tracker_backend.exception.ExcelValidationException;
import com.medplus.agreement_tracker_backend.exception.IncompleteAgreementException;
import com.medplus.agreement_tracker_backend.repository.AgreementJbpConfigurationRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementTimePeriodRepository;
import com.medplus.agreement_tracker_backend.service.CommercialVersionGuard;
import com.medplus.agreement_tracker_backend.service.JbpExcelParserService;
import com.medplus.agreement_tracker_backend.util.ExcelCellReader;
import com.medplus.agreement_tracker_backend.util.JbpExcelUserMessages;
import com.medplus.agreement_tracker_backend.util.JbpExcelRowErrorCollector;
import com.medplus.agreement_tracker_backend.util.JbpExcelSheetLayout;
import com.medplus.agreement_tracker_backend.util.JbpExcelSheetLayout.Columns;
import com.medplus.agreement_tracker_backend.util.JbpExcelValidationErrorAnnotator;
import com.medplus.agreement_tracker_backend.util.JbpTemporalReconciliationUtil;
import com.medplus.agreement_tracker_backend.util.TimePeriodDisplayFormatter;
import com.medplus.agreement_tracker_backend.util.TimePeriodDimensions;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JbpExcelParserServiceImpl implements JbpExcelParserService {

    private static final int HEADER_ROW = 0;
    private static final int DATA_START_ROW = 1;
    private static final BigDecimal PERCENT_LIMIT = new BigDecimal("100");

    private static final Pattern SHEET_PATTERN = Pattern.compile("^(?:Config\\d+-)?(Master|Spread|Target)_([A-Z_]+)$");

    private final CommercialVersionGuard commercialVersionGuard;
    private final AgreementTimePeriodRepository timePeriodRepository;
    private final AgreementJbpConfigurationRepository jbpConfigurationRepository;

    @Override
    @Transactional(readOnly = true)
    public JbpStagedWorkbookDto parseUpload(Long agreementVersionId, MultipartFile file, Long currentUserId) {
        AgreementVersion version = commercialVersionGuard.loadForCommercialMutation(agreementVersionId, currentUserId);
        return parseWorkbook(version, file, false);
    }

    @Override
    @Transactional(readOnly = true)
    public JbpStagedWorkbookDto parseUploadStateless(Long sourceVersionId, MultipartFile file, Long currentUserId) {
        AgreementVersion version = commercialVersionGuard.loadOwnedSourceVersion(sourceVersionId, currentUserId);
        // Annotated error workbook (same UX as DRAFT jbp-upload), not JSON fieldErrors.
        return parseWorkbook(version, file, false, null);
    }

    @Override
    @Transactional(readOnly = true)
    public JbpStagedWorkbookDto parseUploadStatelessWithBlueprint(
            Long sourceVersionId,
            MultipartFile file,
            JbpWorkbookRequest blueprint,
            Long currentUserId) {
        AgreementVersion version = commercialVersionGuard.loadOwnedSourceVersion(sourceVersionId, currentUserId);
        if (blueprint == null || blueprint.configurations() == null || blueprint.configurations().isEmpty()) {
            throw new IncompleteAgreementException("JBP configuration blueprint is required for custom parse.");
        }
        return parseWorkbook(version, file, false, blueprint);
    }

    private JbpStagedWorkbookDto parseWorkbook(AgreementVersion version, MultipartFile file, boolean jsonErrors) {
        return parseWorkbook(version, file, jsonErrors, null);
    }

    private JbpStagedWorkbookDto parseWorkbook(
            AgreementVersion version,
            MultipartFile file,
            boolean jsonErrors,
            JbpWorkbookRequest blueprint) {
        if (file == null || file.isEmpty()) {
            throw new IncompleteAgreementException("Uploaded Excel file is required.");
        }

        Long agreementVersionId = version.getId();
        int financialYearStartMonth = version.getFinancialYearStartMonth();
        Map<Long, AgreementJbpConfiguration> configurationsById;
        if (blueprint != null) {
            configurationsById = buildEphemeralConfigurations(blueprint);
            if (blueprint.financialYearStartMonth() != null) {
                financialYearStartMonth = blueprint.financialYearStartMonth();
            }
        } else {
            ensureStoredConfigurations(agreementVersionId);
            configurationsById = jbpConfigurationRepository.findByAgreementVersionId(agreementVersionId).stream()
                    .collect(Collectors.toMap(AgreementJbpConfiguration::getId, Function.identity()));
        }

        Workbook workbook;
        try {
            workbook = new XSSFWorkbook(file.getInputStream());
        } catch (IOException ex) {
            throw new IncompleteAgreementException("Failed to read JBP workbook: " + ex.getMessage());
        }

        try {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IncompleteAgreementException("Excel file does not contain any worksheets.");
            }

            JbpExcelRowErrorCollector rowErrors = new JbpExcelRowErrorCollector();
            List<ParsedSheet> parsedSheets = new ArrayList<>();
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                ParsedSheet parsed = parseSheet(sheet, configurationsById, rowErrors, financialYearStartMonth);
                if (parsed != null) {
                    parsedSheets.add(parsed);
                }
            }

            if (parsedSheets.isEmpty() && !rowErrors.hasErrors()) {
                throw new IncompleteAgreementException(
                        "No filled JBP slab rows found. Complete at least one slab row before uploading.");
            }

            for (ParsedSheet sheet : parsedSheets) {
                validateIncreasingTargets(sheet, rowErrors);
            }

            if (rowErrors.hasErrors()) {
                if (jsonErrors) {
                    List<ExcelParseValidationException.RowError> errors = new ArrayList<>();
                    rowErrors.rowErrors()
                            .forEach((sheetName, byRow) -> byRow.forEach(
                                    (rowIndex, message) -> errors.add(new ExcelParseValidationException.RowError(
                                            sheetName, rowIndex, message))));
                    throw new ExcelParseValidationException(
                            "JBP workbook validation failed", errors);
                }
                byte[] errorWorkbook = JbpExcelValidationErrorAnnotator.annotateWorkbook(
                        workbook, rowErrors.rowErrors());
                throw new ExcelValidationException(errorWorkbook, "JBP_Upload_Errors.xlsx");
            }

            List<StagedSheet> stagedSheets = parsedSheets.stream()
                    .sorted(Comparator
                            .comparing(ParsedSheet::master).reversed()
                            .thenComparing(sheet -> JbpTemporalReconciliationUtil.frequencyRank(sheet.frequency()),
                                    Comparator.reverseOrder()))
                    .map(this::toStagedSheet)
                    .toList();

            return new JbpStagedWorkbookDto(stagedSheets, false, null);
        } finally {
            try {
                workbook.close();
            } catch (IOException ex) {
                throw new IncompleteAgreementException("Failed to close JBP workbook: " + ex.getMessage());
            }
        }
    }

    private Map<Long, AgreementJbpConfiguration> buildEphemeralConfigurations(JbpWorkbookRequest blueprint) {
        Map<Long, AgreementJbpConfiguration> configurationsById = new LinkedHashMap<>();
        for (var block : blueprint.configurations()) {
            long clientConfigId;
            try {
                clientConfigId = Long.parseLong(block.configId().trim());
            } catch (NumberFormatException ex) {
                throw new IncompleteAgreementException(
                        "Configuration id must be numeric: " + block.configId());
            }
            AgreementJbpConfiguration configuration = new AgreementJbpConfiguration();
            configuration.setId(clientConfigId);
            configuration.setSlabCount(block.maxSlabs());
            configurationsById.put(clientConfigId, configuration);
        }
        return configurationsById;
    }

    private void ensureStoredConfigurations(Long agreementVersionId) {
        if (jbpConfigurationRepository.findByAgreementVersionId(agreementVersionId).isEmpty()) {
            throw new IncompleteAgreementException(
                    "Generate the JBP workbook template before uploading a completed file.");
        }
    }

    private ParsedSheet parseSheet(
            Sheet sheet,
            Map<Long, AgreementJbpConfiguration> configurationsById,
            JbpExcelRowErrorCollector rowErrors,
            int financialYearStartMonth) {
        SheetIdentity identity = parseSheetIdentity(sheet.getSheetName());
        if (identity == null) {
            return null;
        }

        Columns layout = JbpExcelSheetLayout.forSheet(identity.master());
        String sheetName = sheet.getSheetName();

        if (sheet.getRow(HEADER_ROW) == null) {
            throw new IncompleteAgreementException(
                    "Invalid template format on sheet '" + sheetName + "': missing header row.");
        }

        List<IndexedRow> rows = new ArrayList<>();
        String lastParentPeriod = null;
        String lastSubPeriod = null;
        for (int rowIndex = DATA_START_ROW; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isGapRow(row, layout)) {
                continue;
            }
            try {
                IndexedRow parsed = identity.master()
                        ? parseMasterRow(row, sheetName, identity, layout, configurationsById, lastParentPeriod,
                                financialYearStartMonth)
                        : parseSpreadRow(row, sheetName, identity, layout, configurationsById,
                                lastParentPeriod, lastSubPeriod, financialYearStartMonth);
                if (parsed == null) {
                    continue;
                }
                // Unfilled template slab rows are optional — skip instead of requiring every
                // tier.
                if (isBlankCommercialRow(parsed.row())) {
                    continue;
                }
                IndexedRow normalized = normalizeQualifierDefault(parsed);
                enforceMandatoryFields(normalized, identity.master());
                validateIndexedRow(normalized, identity.master(), sheetName, rowErrors);
                if (rowErrors.rowErrors().getOrDefault(sheetName, Map.of()).containsKey(normalized.excelRowIndex())) {
                    continue;
                }
                rows.add(normalized);
                lastParentPeriod = normalized.row().parentPeriodName();
                if (normalized.row().subPeriodName() != null) {
                    lastSubPeriod = normalized.row().subPeriodName();
                }
            } catch (Exception ex) {
                rowErrors.add(sheetName, rowIndex, resolveErrorMessage(ex));
            }
        }

        if (rows.isEmpty()) {
            // Sheet may be entirely blank (user filled only one slab on another
            // sheet/tier).
            return null;
        }

        applyFirstInParentGroupFlags(rows);
        return new ParsedSheet(sheetName, identity.frequency(), identity.master(), rows);
    }

    private SheetIdentity parseSheetIdentity(String sheetName) {
        Matcher matcher = SHEET_PATTERN.matcher(sheetName);
        if (!matcher.matches()) {
            return null;
        }
        // Target_ sheets are treated as master sheets (no parent-child spread relationship).
        String type = matcher.group(1);
        boolean master = "Master".equals(type) || "Target".equals(type);
        PayoutFrequency frequency;
        try {
            frequency = PayoutFrequency.valueOf(matcher.group(2));
        } catch (IllegalArgumentException ex) {
            throw new IncompleteAgreementException("Unrecognized JBP frequency in sheet name: '" + sheetName + "'.");
        }
        return new SheetIdentity(master, frequency);
    }

    private boolean isGapRow(Row row, Columns layout) {
        Long entityId = readEntityId(row.getCell(layout.colEntityId()));
        Long configId = readEntityId(row.getCell(layout.colConfigId()));
        return entityId == null && configId == null;
    }

    private IndexedRow parseMasterRow(
            Row row,
            String sheetName,
            SheetIdentity identity,
            Columns layout,
            Map<Long, AgreementJbpConfiguration> configurationsById,
            String lastParentPeriod,
            int financialYearStartMonth) {
        int excelRowIndex = row.getRowNum();
        Long entityId = readEntityId(row.getCell(layout.colEntityId()));
        Long configurationId = readEntityId(row.getCell(layout.colConfigId()));
        if (entityId == null || configurationId == null) {
            return null;
        }

        String periodNameRaw = ExcelCellReader.readAsString(row.getCell(layout.colParentPeriod()));
        String periodName = periodNameRaw.isBlank() ? lastParentPeriod : periodNameRaw;
        if (periodName == null || periodName.isBlank()) {
            return null;
        }

        String tierLabel = ExcelCellReader.readAsString(row.getCell(layout.colSlabTier()));
        int tierNumber = parseTierNumber(tierLabel);

        AgreementTimePeriod period = resolvePeriod(entityId, periodName, identity.frequency(), sheetName,
                financialYearStartMonth);
        resolveConfiguration(configurationId, tierNumber, configurationsById);

        ThresholdValues values = readThresholdValues(row, layout, true);
        String periodDisplay = TimePeriodDisplayFormatter.format(period, financialYearStartMonth);
        boolean firstInGroup = !periodDisplay.equals(lastParentPeriod);

        return new IndexedRow(
                new UnpivotedRow(
                        periodDisplay,
                        period.getId(),
                        null,
                        period.getId(),
                        tierNumber,
                        configurationId,
                        tierLabel,
                        values.targetType(),
                        values.target(),
                        values.qualifierPercent(),
                        values.payoutType(),
                        values.payout(),
                        values.maxPurchase(),
                        values.maxPayout(),
                        firstInGroup),
                excelRowIndex);
    }

    private IndexedRow parseSpreadRow(
            Row row,
            String sheetName,
            SheetIdentity identity,
            Columns layout,
            Map<Long, AgreementJbpConfiguration> configurationsById,
            String lastParentPeriod,
            String lastSubPeriod,
            int financialYearStartMonth) {
        int excelRowIndex = row.getRowNum();
        Long entityId = readEntityId(row.getCell(layout.colEntityId()));
        Long configurationId = readEntityId(row.getCell(layout.colConfigId()));
        if (entityId == null || configurationId == null) {
            return null;
        }

        String parentRaw = ExcelCellReader.readAsString(row.getCell(layout.colParentPeriod()));
        String parentPeriodName = parentRaw.isBlank() ? lastParentPeriod : parentRaw;
        String subRaw = ExcelCellReader.readAsString(row.getCell(layout.colSubPeriod()));
        String subPeriodName = subRaw.isBlank() ? lastSubPeriod : subRaw;
        if (parentPeriodName == null || parentPeriodName.isBlank()
                || subPeriodName == null || subPeriodName.isBlank()) {
            return null;
        }

        String tierLabel = ExcelCellReader.readAsString(row.getCell(layout.colSlabTier()));
        int tierNumber = parseTierNumber(tierLabel);

        AgreementTimePeriod subPeriod = resolvePeriod(entityId, subPeriodName, identity.frequency(), sheetName,
                financialYearStartMonth);
        AgreementTimePeriod parentPeriod = resolvePeriodByNameOrDisplay(parentPeriodName, financialYearStartMonth);
        resolveConfiguration(configurationId, tierNumber, configurationsById);

        ThresholdValues values = readThresholdValues(row, layout, false);
        String parentDisplay = TimePeriodDisplayFormatter.format(parentPeriod, financialYearStartMonth);
        String subDisplay = TimePeriodDisplayFormatter.formatBase(subPeriod);
        boolean firstInGroup = !parentDisplay.equals(lastParentPeriod);

        return new IndexedRow(
                new UnpivotedRow(
                        parentDisplay,
                        parentPeriod.getId(),
                        subDisplay,
                        subPeriod.getId(),
                        tierNumber,
                        configurationId,
                        tierLabel,
                        values.targetType(),
                        values.target(),
                        values.qualifierPercent(),
                        values.payoutType(),
                        values.payout(),
                        values.maxPurchase(),
                        values.maxPayout(),
                        firstInGroup),
                excelRowIndex);
    }

    private IndexedRow normalizeQualifierDefault(IndexedRow indexedRow) {
        UnpivotedRow row = indexedRow.row();
        if (row.qualifierPercent() != null) {
            return indexedRow;
        }
        return new IndexedRow(copyRow(row, row.firstInParentGroup(), BigDecimal.ZERO), indexedRow.excelRowIndex());
    }

    /**
     * Template generates a row per slab tier. Rows with no commercial inputs are
     * optional
     * (user may fill only one slab).
     */
    private boolean isBlankCommercialRow(UnpivotedRow row) {
        boolean noTarget = row.targetType() == null && row.target() == null;
        boolean noPayout = row.payoutType() == null && row.payout() == null;
        boolean noQualifier = row.qualifierPercent() == null
                || row.qualifierPercent().compareTo(BigDecimal.ZERO) == 0;
        boolean noCaps = row.maxPurchase() == null && row.maxPayout() == null;
        return noTarget && noPayout && noQualifier && noCaps;
    }

    private void enforceMandatoryFields(IndexedRow indexedRow, boolean masterSheet) {
        // Validations removed for real-time commercial structures
    }

    private void validateIndexedRow(
            IndexedRow indexedRow,
            boolean masterSheet,
            String sheetName,
            JbpExcelRowErrorCollector rowErrors) {
        UnpivotedRow row = indexedRow.row();
        int excelRowIndex = indexedRow.excelRowIndex();

        if (!masterSheet) {
            validateSubPeriodSignificance(row, sheetName, excelRowIndex, rowErrors);
        }

        if (row.qualifierPercent().compareTo(PERCENT_LIMIT) > 0) {
            rowErrors.add(sheetName, excelRowIndex,
                    JbpExcelUserMessages.qualifierTooHigh(row.slabTierLabel()));
        }
        if (row.targetType() == JbpValueType.RELATIVE
                && row.target() != null
                && row.target().compareTo(PERCENT_LIMIT) > 0) {
            rowErrors.add(sheetName, excelRowIndex,
                    JbpExcelUserMessages.relativeTargetTooHigh(row.slabTierLabel()));
        }
        if (row.payoutType() == JbpValueType.RELATIVE
                && row.payout() != null
                && row.payout().compareTo(PERCENT_LIMIT) > 0) {
            rowErrors.add(sheetName, excelRowIndex,
                    JbpExcelUserMessages.relativePayoutTooHigh(row.slabTierLabel()));
        }
    }

    private void validateSubPeriodSignificance(
            UnpivotedRow row,
            String sheetName,
            int excelRowIndex,
            JbpExcelRowErrorCollector rowErrors) {
        boolean hasPayout = row.payoutType() != null
                && row.payout() != null
                && row.payout().compareTo(BigDecimal.ZERO) > 0;
        boolean hasQualifier = row.qualifierPercent().compareTo(BigDecimal.ZERO) > 0;
        if (hasPayout || hasQualifier) {
            return;
        }
        rowErrors.add(sheetName, excelRowIndex, JbpExcelUserMessages.subPeriodNeedsPayoutOrQualifier());
    }

    private AgreementTimePeriod resolvePeriod(
            Long entityId,
            String periodName,
            PayoutFrequency frequency,
            String sheetName,
            int financialYearStartMonth) {
        AgreementTimePeriod period = timePeriodRepository.findById(entityId)
                .orElseThrow(() -> new IncompleteAgreementException(
                        JbpExcelUserMessages.unknownEntityId(entityId)));
        if (!TimePeriodDisplayFormatter.matches(
                period.getName(),
                periodName,
                financialYearStartMonth,
                TimePeriodDimensions.sortedIncludedMonths(period))) {
            throw new IncompleteAgreementException(
                    JbpExcelUserMessages.entityPeriodMismatch(entityId, periodName));
        }
        if (period.getPeriodFrequency() != frequency) {
            throw new IncompleteAgreementException(
                    JbpExcelUserMessages.wrongPeriodFrequency(periodName, frequency));
        }
        return period;
    }

    private AgreementTimePeriod resolvePeriodByNameOrDisplay(String periodName, int financialYearStartMonth) {
        String baseName = TimePeriodDisplayFormatter.stripFySuffix(periodName);
        return timePeriodRepository.findAll().stream()
                .filter(period -> TimePeriodDisplayFormatter.matches(
                        period.getName(),
                        periodName,
                        financialYearStartMonth,
                        TimePeriodDimensions.sortedIncludedMonths(period))
                        || baseName.equalsIgnoreCase(period.getName()))
                .findFirst()
                .orElseThrow(() -> new IncompleteAgreementException(
                        JbpExcelUserMessages.unknownParentPeriod(periodName)));
    }

    private AgreementJbpConfiguration resolveConfiguration(
            Long configurationId,
            int tierNumber,
            Map<Long, AgreementJbpConfiguration> configurationsById) {
        AgreementJbpConfiguration configuration = configurationsById.get(configurationId);
        if (configuration == null) {
            throw new IncompleteAgreementException(
                    JbpExcelUserMessages.invalidConfigurationId(configurationId));
        }
        if (configuration.getSlabCount() == null || tierNumber < 1 || tierNumber > configuration.getSlabCount()) {
            throw new IncompleteAgreementException(
                    JbpExcelUserMessages.invalidSlabForConfiguration(configurationId, tierNumber));
        }
        return configuration;
    }

    private void applyFirstInParentGroupFlags(List<IndexedRow> rows) {
        String lastParent = null;
        for (int i = 0; i < rows.size(); i++) {
            IndexedRow indexedRow = rows.get(i);
            UnpivotedRow row = indexedRow.row();
            boolean first = !row.parentPeriodName().equals(lastParent);
            if (first != row.firstInParentGroup()) {
                rows.set(i, new IndexedRow(copyRow(row, first), indexedRow.excelRowIndex()));
            }
            lastParent = row.parentPeriodName();
        }
    }

    private UnpivotedRow copyRow(UnpivotedRow row, boolean firstInGroup) {
        return copyRow(row, firstInGroup, row.qualifierPercent());
    }

    private UnpivotedRow copyRow(UnpivotedRow row, boolean firstInGroup, BigDecimal qualifierPercent) {
        BigDecimal qualifier = qualifierPercent != null ? qualifierPercent : BigDecimal.ZERO;
        return new UnpivotedRow(
                row.parentPeriodName(),
                row.parentPeriodId(),
                row.subPeriodName(),
                row.timePeriodId(),
                row.slabTierNumber(),
                row.jbpConfigurationId(),
                row.slabTierLabel(),
                row.targetType(),
                row.target(),
                qualifier,
                row.payoutType(),
                row.payout(),
                row.maxPurchase(),
                row.maxPayout(),
                firstInGroup);
    }

    private ThresholdValues readThresholdValues(Row row, Columns layout, boolean masterSheet) {
        BigDecimal qualifier = readLenientDecimal(row.getCell(layout.colQualifierPercent()));
        if (qualifier == null) {
            qualifier = BigDecimal.ZERO;
        }
        // For master rows, Target Type column is removed — hardcode ABSOLUTE.
        // For spread rows, read from the Target Type column as usual.
        JbpValueType targetType = masterSheet
                ? JbpValueType.ABSOLUTE
                : parseValueType(row.getCell(layout.colTargetType()), "Target Type");
        return new ThresholdValues(
                targetType,
                readStrictDecimal(row.getCell(layout.colTarget()), "Target"),
                qualifier,
                parseOptionalValueType(row.getCell(layout.colPayoutType()), "Payout Type", masterSheet),
                readLenientDecimal(row.getCell(layout.colPayout())),
                readLenientDecimal(row.getCell(layout.colMaxPurchase())),
                readLenientDecimal(row.getCell(layout.colMaxPayout())));
    }

    private JbpValueType parseOptionalValueType(Cell cell, String label, boolean masterSheet) {
        if (masterSheet) {
            return parseValueType(cell, label);
        }
        return parseValueType(cell, label, true);
    }

    private JbpValueType parseValueType(Cell cell, String label) {
        return parseValueType(cell, label, false);
    }

    private JbpValueType parseValueType(Cell cell, String label, boolean allowBlank) {
        String raw = ExcelCellReader.readAsString(cell);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return JbpValueType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IncompleteAgreementException(JbpExcelUserMessages.invalidValueType(label));
        }
    }

    private int parseTierNumber(String tierLabel) {
        String digits = tierLabel.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            throw new IncompleteAgreementException(JbpExcelUserMessages.invalidSlabTier(tierLabel));
        }
        return Integer.parseInt(digits);
    }

    private Long readEntityId(Cell cell) {
        BigDecimal value = ExcelCellReader.readAsFormattedDecimal(cell);
        return value == null ? null : value.longValue();
    }

    private void validateIncreasingTargets(ParsedSheet sheet, JbpExcelRowErrorCollector rowErrors) {
        // Validation removed for real-time commercial structures
    }

    private BigDecimal readStrictDecimal(Cell cell, String fieldLabel) {
        if (cell == null || cell.getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK) {
            return null;
        }
        if (ExcelCellReader.hasNonBlankNonNumericContent(cell)) {
            throw new IncompleteAgreementException(JbpExcelUserMessages.invalidNumberFormat(fieldLabel));
        }
        return ExcelCellReader.readAsFormattedDecimal(cell);
    }

    private BigDecimal readLenientDecimal(Cell cell) {
        if (cell == null || cell.getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK) {
            return null;
        }
        if (ExcelCellReader.hasNonBlankNonNumericContent(cell)) {
            throw new IncompleteAgreementException(JbpExcelUserMessages.invalidRowFormat());
        }
        return ExcelCellReader.readAsFormattedDecimal(cell);
    }

    private String resolveErrorMessage(Exception ex) {
        if (ex instanceof IncompleteAgreementException || ex instanceof BusinessException) {
            return JbpExcelUserMessages.normalizeCaughtMessage(ex.getMessage());
        }
        return JbpExcelUserMessages.invalidRowFormat();
    }

    private StagedSheet toStagedSheet(ParsedSheet sheet) {
        return new StagedSheet(
                null,
                sheet.sheetName(),
                sheet.sheetName(),
                sheet.frequency().name(),
                sheet.master(),
                sheet.rows().stream().map(IndexedRow::row).toList());
    }

    private record ThresholdValues(
            JbpValueType targetType,
            BigDecimal target,
            BigDecimal qualifierPercent,
            JbpValueType payoutType,
            BigDecimal payout,
            BigDecimal maxPurchase,
            BigDecimal maxPayout) {
    }

    private record SheetIdentity(boolean master, PayoutFrequency frequency) {
    }

    private record IndexedRow(UnpivotedRow row, int excelRowIndex) {
    }

    private record ParsedSheet(
            String sheetName,
            PayoutFrequency frequency,
            boolean master,
            List<IndexedRow> rows) {
    }
}
