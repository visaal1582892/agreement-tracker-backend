package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.dto.request.JbpConfigurationBlockDto;
import com.medplus.agreement_tracker_backend.dto.request.JbpStatelessPreviewRequest;
import com.medplus.agreement_tracker_backend.dto.request.JbpWorkbookRequest;
import com.medplus.agreement_tracker_backend.entity.AgreementJbpConfiguration;
import com.medplus.agreement_tracker_backend.entity.AgreementSlab;
import com.medplus.agreement_tracker_backend.entity.AgreementTimePeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.exception.IncompleteAgreementException;
import com.medplus.agreement_tracker_backend.exception.ResourceNotFoundException;
import com.medplus.agreement_tracker_backend.repository.AgreementJbpCommercialPeriodRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementJbpConfigurationRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementSlabRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementTimePeriodRepository;
import com.medplus.agreement_tracker_backend.service.AgreementTimePeriodResolutionService;
import com.medplus.agreement_tracker_backend.service.CommercialVersionGuard;
import com.medplus.agreement_tracker_backend.service.JbpExcelGeneratorService;
import com.medplus.agreement_tracker_backend.util.DynamicFinancialYearPeriodGenerator;
import com.medplus.agreement_tracker_backend.util.JbpConfigurationCollisionValidator;
import com.medplus.agreement_tracker_backend.util.JbpExcelSheetLayout;
import com.medplus.agreement_tracker_backend.util.JbpExcelSheetLayout.Columns;
import com.medplus.agreement_tracker_backend.util.JbpTemporalReconciliationUtil;
import com.medplus.agreement_tracker_backend.util.TimePeriodDimensions;
import com.medplus.agreement_tracker_backend.util.TimePeriodDisplayFormatter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class JbpExcelGeneratorServiceImpl implements JbpExcelGeneratorService {

    private static final int HEADER_ROW = 0;
    private static final int DATA_START_ROW = 1;
    private static final int VALIDATION_END_ROW = 5000;

    private static final Set<PayoutFrequency> JBP_FREQUENCIES = Set.of(
            PayoutFrequency.YEARLY,
            PayoutFrequency.HALF_YEARLY,
            PayoutFrequency.QUARTERLY,
            PayoutFrequency.MONTHLY);

    private final AgreementJbpConfigurationRepository jbpConfigurationRepository;
    private final AgreementJbpCommercialPeriodRepository jbpCommercialPeriodRepository;
    private final AgreementSlabRepository slabRepository;
    private final AgreementTimePeriodRepository timePeriodRepository;
    private final CommercialVersionGuard commercialVersionGuard;
    private final AgreementTimePeriodResolutionService periodResolutionService;
    private final JbpCommercialServiceImpl jbpCommercialService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public byte[] generateWorkbook(
            Long agreementVersionId,
            JbpWorkbookRequest request,
            Long currentUserId,
            Integer startMonthOverride) {
        java.util.Set<String> allPaymentIntervals = new java.util.HashSet<>();
        for (JbpConfigurationBlockDto block : request.configurations()) {
            if (block.paymentIntervals() != null) {
                for (String interval : block.paymentIntervals()) {
                    if (!allPaymentIntervals.add(interval.trim().toUpperCase())) {
                        throw new BusinessException("Payment interval '" + interval + "' is selected in multiple JBP configurations. Payment intervals must be mutually exclusive.");
                    }
                }
            }
        }
        AgreementVersion version = commercialVersionGuard.loadForCommercialMutation(agreementVersionId, currentUserId);
        validateContractDates(version);
        validateRequest(request);

        int financialYearStartMonth = DynamicFinancialYearPeriodGenerator.resolveStartMonth(
                startMonthOverride != null
                        ? startMonthOverride
                        : request.financialYearStartMonth() != null
                                ? request.financialYearStartMonth()
                                : version.getFinancialYearStartMonth());
        version.setFinancialYearStartMonth(financialYearStartMonth);

        jbpCommercialService.persistWorkbookMetadata(version, request, currentUserId);
        jbpCommercialPeriodRepository.deleteByAgreementVersionId(version.getId());
        jbpConfigurationRepository.deleteByAgreementVersionId(version.getId());

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle lockedStyle = createLockedStyle(workbook);
            CellStyle editableStyle = createEditableStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook, lockedStyle);

            createInstructionsSheet(workbook, lockedStyle, headerStyle);

            Map<String, MasterSheetState> masterSheets = new LinkedHashMap<>();

            int configNumber = 1;
            for (JbpConfigurationBlockDto config : request.configurations()) {
                AgreementJbpConfiguration jbpConfiguration = provisionJbpConfiguration(
                        version, config);

                // Use target intervals (or payment intervals as fallback) to drive sheet creation.
                // Each selected target interval gets its own dedicated sheet (tab).
                List<String> sheetIntervals = config.targetIntervals();
                for (String intervalRaw : sheetIntervals) {
                    PayoutFrequency intervalFrequency = parseFrequency(intervalRaw);
                    List<AgreementTimePeriod> periods = loadPeriodsForInterval(
                            version, intervalFrequency, financialYearStartMonth, currentUserId);
                    if (periods.isEmpty()) {
                        throw new IncompleteAgreementException(
                                "No time periods found for interval " + intervalRaw
                                + " in configuration " + config.configId()
                                + ". Ensure the contract date range covers at least one period.");
                    }
                    // Each target interval sheet uses master layout (no parent-child spread).
                    Columns masterLayout = JbpExcelSheetLayout.forSheet(true);
                    String sheetName = JbpExcelSheetLayout.canonicalTargetSheetName(
                            String.valueOf(configNumber), intervalFrequency);

                    MasterSheetState masterState = masterSheets.computeIfAbsent(
                            sheetName,
                            name -> {
                                Sheet sheet = workbook.createSheet(name);
                                writeHeaderRow(sheet, masterLayout.headers(), headerStyle);
                                return new MasterSheetState(sheet, DATA_START_ROW);
                            });
                    appendMasterRows(
                            masterState,
                            masterLayout,
                            periods,
                            jbpConfiguration,
                            financialYearStartMonth,
                            lockedStyle,
                            editableStyle);
                }
                configNumber++;
            }

            masterSheets.forEach((name, state) -> {
                Columns layout = JbpExcelSheetLayout.forSheet(true);
                finalizeSheet(state.sheet(), layout, workbook);
            });

            sortWorkbookSheets(workbook);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to generate JBP workbook", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportWorkbookFromSource(Long sourceVersionId, Long currentUserId) {
        AgreementVersion version = commercialVersionGuard.loadOwnedSourceVersion(sourceVersionId, currentUserId);
        validateContractDates(version);

        List<AgreementJbpConfiguration> configurations = jbpConfigurationRepository
                .findHydratedByAgreementVersionId(sourceVersionId);
        if (configurations.isEmpty()) {
            throw new IncompleteAgreementException(
                    "Source version has no JBP configurations to export.");
        }

        int financialYearStartMonth = DynamicFinancialYearPeriodGenerator.resolveStartMonth(
                version.getFinancialYearStartMonth());

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle lockedStyle = createLockedStyle(workbook);
            CellStyle editableStyle = createEditableStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook, lockedStyle);

            createInstructionsSheet(workbook, lockedStyle, headerStyle);

            Map<String, MasterSheetState> masterSheets = new LinkedHashMap<>();

            int configNumber = 1;
            for (AgreementJbpConfiguration jbpConfiguration : configurations) {
                List<String> sheetIntervals = jbpConfiguration.getTargetIntervals() != null && !jbpConfiguration.getTargetIntervals().isEmpty()
                        ? jbpConfiguration.getTargetIntervals()
                        : jbpConfiguration.getPaymentIntervals();
                for (String intervalRaw : sheetIntervals) {
                    PayoutFrequency intervalFrequency = parseFrequency(intervalRaw);
                    List<AgreementTimePeriod> periods = loadPeriodsForInterval(
                            version, intervalFrequency, financialYearStartMonth, currentUserId);
                    if (periods.isEmpty()) {
                        continue;
                    }
                    Columns masterLayout = JbpExcelSheetLayout.forSheet(true);
                    String sheetName = JbpExcelSheetLayout.canonicalTargetSheetName(
                            String.valueOf(configNumber), intervalFrequency);

                    MasterSheetState masterState = masterSheets.computeIfAbsent(
                            sheetName,
                            name -> {
                                Sheet sheet = workbook.createSheet(name);
                                writeHeaderRow(sheet, masterLayout.headers(), headerStyle);
                                return new MasterSheetState(sheet, DATA_START_ROW);
                            });
                    appendMasterRows(
                            masterState,
                            masterLayout,
                            periods,
                            jbpConfiguration,
                            financialYearStartMonth,
                            lockedStyle,
                            editableStyle);
                }
                configNumber++;
            }

            masterSheets.forEach((name, state) -> {
                Columns layout = JbpExcelSheetLayout.forSheet(true);
                finalizeSheet(state.sheet(), layout, workbook);
            });

            record SheetSortInfo(String name, int frequencyRank, boolean master, int configNumber) {
            }
            List<SheetSortInfo> sortInfos = new java.util.ArrayList<>();
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("^Config(\\d+)-(Master|Spread)_([A-Z_]+)$");
            for (int i = 1; i < workbook.getNumberOfSheets(); i++) {
                String name = workbook.getSheetName(i);
                java.util.regex.Matcher m = p.matcher(name);
                if (m.matches()) {
                    int cNum = Integer.parseInt(m.group(1));
                    boolean isMaster = "Master".equals(m.group(2));
                    PayoutFrequency freq = PayoutFrequency.valueOf(m.group(3));
                    int rank = JbpTemporalReconciliationUtil.frequencyRank(freq);
                    sortInfos.add(new SheetSortInfo(name, rank, isMaster, cNum));
                }
            }
        sortInfos.sort(java.util.Comparator
                .comparingInt(SheetSortInfo::configNumber)
                .thenComparing(info -> !info.master())
                .thenComparing(java.util.Comparator.comparingInt(SheetSortInfo::frequencyRank).reversed()));

            int sheetIndex = 1;
            for (SheetSortInfo info : sortInfos) {
                workbook.setSheetOrder(info.name(), sheetIndex++);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to export JBP workbook from source", ex);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public byte[] generateWorkbookStateless(
            Long sourceVersionId,
            JbpStatelessPreviewRequest request,
            Long currentUserId) {
        AgreementVersion source = commercialVersionGuard.loadOwnedSourceVersion(sourceVersionId, currentUserId);
        if (request.startDate() == null || request.expiryDate() == null) {
            throw new IncompleteAgreementException("Contract dates are required to generate a JBP template.");
        }
        if (request.expiryDate().isBefore(request.startDate())) {
            throw new BusinessException("Expiry date must be on or after start date.");
        }
        JbpWorkbookRequest workbookRequest = request.workbook();
        
        java.util.Set<String> allPaymentIntervals = new java.util.HashSet<>();
        for (JbpConfigurationBlockDto block : workbookRequest.configurations()) {
            if (block.paymentIntervals() != null) {
                for (String interval : block.paymentIntervals()) {
                    if (!allPaymentIntervals.add(interval.trim().toUpperCase())) {
                        throw new BusinessException("Payment interval '" + interval + "' is selected in multiple JBP configurations. Payment intervals must be mutually exclusive.");
                    }
                }
            }
        }
        
        validateRequest(workbookRequest);

        AgreementVersion probe = buildDateProbe(source, request.startDate(), request.expiryDate(),
                workbookRequest.financialYearStartMonth());
        int financialYearStartMonth = DynamicFinancialYearPeriodGenerator.resolveStartMonth(
                workbookRequest.financialYearStartMonth() != null
                        ? workbookRequest.financialYearStartMonth()
                        : source.getFinancialYearStartMonth());

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle lockedStyle = createLockedStyle(workbook);
            CellStyle editableStyle = createEditableStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook, lockedStyle);

            createInstructionsSheet(workbook, lockedStyle, headerStyle);

            Map<String, MasterSheetState> masterSheets = new LinkedHashMap<>();

            int configNumber = 1;
            for (JbpConfigurationBlockDto config : workbookRequest.configurations()) {
                // Use target intervals (or payment intervals as fallback) to drive sheet creation.
                List<String> sheetIntervals = config.targetIntervals();
                for (String intervalRaw : sheetIntervals) {
                    PayoutFrequency intervalFrequency = parseFrequency(intervalRaw);
                    List<AgreementTimePeriod> periods = loadPeriodsForInterval(
                            probe, intervalFrequency, financialYearStartMonth, currentUserId);
                    if (periods.isEmpty()) {
                        throw new IncompleteAgreementException(
                                "No time periods found for interval " + intervalRaw
                                + " in configuration " + config.configId());
                    }
                    AgreementJbpConfiguration jbpConfiguration = buildEphemeralConfiguration(
                            probe, intervalFrequency, config);
                    Columns masterLayout = JbpExcelSheetLayout.forSheet(true);
                    String sheetName = JbpExcelSheetLayout.canonicalTargetSheetName(
                            String.valueOf(configNumber), intervalFrequency);

                    MasterSheetState masterState = masterSheets.computeIfAbsent(
                            sheetName,
                            name -> {
                                Sheet sheet = workbook.createSheet(name);
                                writeHeaderRow(sheet, masterLayout.headers(), headerStyle);
                                return new MasterSheetState(sheet, DATA_START_ROW);
                            });
                    appendMasterRows(
                            masterState,
                            masterLayout,
                            periods,
                            jbpConfiguration,
                            financialYearStartMonth,
                            lockedStyle,
                            editableStyle);
                }
                configNumber++;
            }

            masterSheets.forEach((name, state) -> {
                Columns layout = JbpExcelSheetLayout.forSheet(true);
                finalizeSheet(state.sheet(), layout, workbook);
            });

            sortWorkbookSheets(workbook);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to generate stateless JBP workbook", ex);
        }
    }

    private AgreementVersion buildDateProbe(
            AgreementVersion source,
            java.time.LocalDate startDate,
            java.time.LocalDate expiryDate,
            Integer financialYearStartMonth) {
        AgreementVersion probe = new AgreementVersion();
        probe.setId(source.getId());
        probe.setAgreement(source.getAgreement());
        probe.setStartDate(startDate);
        probe.setExpiryDate(expiryDate);
        probe.setFinancialYearStartMonth(DynamicFinancialYearPeriodGenerator.resolveStartMonth(
                financialYearStartMonth != null ? financialYearStartMonth : source.getFinancialYearStartMonth()));
        return probe;
    }

    private AgreementJbpConfiguration buildEphemeralConfiguration(
            AgreementVersion probe,
            PayoutFrequency masterFrequency,
            JbpConfigurationBlockDto config) {
        long clientConfigId;
        try {
            clientConfigId = Long.parseLong(config.configId().trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(
                    "Configuration id must be numeric for Edit/Renew template generation: " + config.configId());
        }
        if (clientConfigId <= 0) {
            throw new BusinessException("Configuration id must be a positive number: " + config.configId());
        }
        AgreementJbpConfiguration configuration = new AgreementJbpConfiguration();
        configuration.setId(clientConfigId);
        configuration.setAgreementVersion(probe);
        configuration.setSlabCount(config.maxSlabs());
        return configuration;
    }

    private void createInstructionsSheet(Workbook workbook, CellStyle lockedStyle, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Instructions");
        CellStyle titleStyle = createHeaderStyle(workbook, lockedStyle);
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);

        CellStyle sectionStyle = createHeaderStyle(workbook, lockedStyle);
        Font sectionFont = workbook.createFont();
        sectionFont.setBold(true);
        sectionStyle.setFont(sectionFont);

        int rowIndex = 0;
        Row titleRow = sheet.createRow(rowIndex++);
        setStringCell(titleRow, 0, "JBP Threshold Workbook Instructions", titleStyle);

        rowIndex = writeInstructionSection(sheet, rowIndex, "General", new String[] {
                "Do not modify hidden columns (Entity ID, Config ID).",
                "Complete all required threshold fields on each populated row.",
                "Each Target Interval sheet corresponds to a single frequency. Fill slab tiers per period."
        }, sectionStyle, lockedStyle);

        rowIndex = writeInstructionSection(sheet, rowIndex, "Targets", new String[] {
                "If Target Type is RELATIVE, Target must be less than or equal to 100."
        }, sectionStyle, lockedStyle);

        rowIndex = writeInstructionSection(sheet, rowIndex, "Qualifiers", new String[] {
                "Qualifier % must always be less than or equal to 100."
        }, sectionStyle, lockedStyle);

        rowIndex = writeInstructionSection(sheet, rowIndex, "Hierarchy", new String[] {
                "The Highest Parent interval MUST have an ABSOLUTE Target Type and a defined Payout.",
                "Sub-periods can have optional payouts, but each populated sub-period row must have either a Payout greater than 0 or a Qualifier % greater than 0."
        }, sectionStyle, lockedStyle);

        writeInstructionSection(sheet, rowIndex, "Caps", new String[] {
                "Max Purchase is strictly optional.",
                "Max Payout is strictly optional."
        }, sectionStyle, lockedStyle);

        sheet.setColumnWidth(0, 28000);
    }

    private int writeInstructionSection(
            Sheet sheet,
            int startRow,
            String heading,
            String[] bullets,
            CellStyle sectionStyle,
            CellStyle bodyStyle) {
        int rowIndex = startRow;
        Row headingRow = sheet.createRow(rowIndex++);
        setStringCell(headingRow, 0, heading, sectionStyle);
        for (String bullet : bullets) {
            Row bulletRow = sheet.createRow(rowIndex++);
            setStringCell(bulletRow, 0, "• " + bullet, bodyStyle);
        }
        sheet.createRow(rowIndex++);
        return rowIndex;
    }

    private SpreadSheetState createSpreadSheet(
            Workbook workbook,
            String sheetName,
            Columns layout,
            CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet(sheetName);
        writeHeaderRow(sheet, layout.headers(), headerStyle);
        return new SpreadSheetState(sheet, DATA_START_ROW);
    }

    private void appendMasterRows(
            MasterSheetState state,
            Columns layout,
            List<AgreementTimePeriod> parentPeriods,
            AgreementJbpConfiguration configuration,
            int financialYearStartMonth,
            CellStyle lockedStyle,
            CellStyle editableStyle) {
        for (AgreementTimePeriod period : parentPeriods) {
            if (state.hasWrittenRows()) {
                state.insertGapRow();
            }
            for (int tier = 1; tier <= configuration.getSlabCount(); tier++) {
                Row dataRow = state.sheet().createRow(state.nextRow());

                setNumericCell(dataRow, layout.colEntityId(), period.getId(), lockedStyle);

                String periodDisplay = period.getName().equals(state.lastPeriodName())
                        ? ""
                        : TimePeriodDisplayFormatter.format(period, financialYearStartMonth);
                setStringCell(dataRow, layout.colParentPeriod(), periodDisplay, lockedStyle);
                if (!periodDisplay.isEmpty()) {
                    state.setLastPeriodName(period.getName());
                }

                setStringCell(dataRow, layout.colSlabTier(), slabTierLabel(tier), lockedStyle);
                setNumericCell(dataRow, layout.colConfigId(), configuration.getId(), lockedStyle);
                // Target Type column is REMOVED from master sheet — always ABSOLUTE (hardcoded
                // by parser).

                for (int col = layout.editableStartCol(); col <= layout.editableEndCol(); col++) {
                    Cell cell = dataRow.createCell(col);
                    cell.setCellStyle(editableStyle);
                }

                state.advanceRow();
                state.markWritten();
            }
        }
    }

    private void appendSpreadRows(
            SpreadSheetState spreadState,
            Columns layout,
            List<AgreementTimePeriod> parentPeriods,
            List<AgreementTimePeriod> subPeriods,
            AgreementJbpConfiguration configuration,
            int financialYearStartMonth,
            CellStyle lockedStyle,
            CellStyle editableStyle) {
        Map<Long, List<AgreementTimePeriod>> groupedSubs = JbpTemporalReconciliationUtil
                .groupSubPeriodsByParent(subPeriods, parentPeriods);

        for (AgreementTimePeriod parent : parentPeriods) {
            List<AgreementTimePeriod> subsForParent = groupedSubs.getOrDefault(parent.getId(), List.of());
            if (subsForParent.isEmpty()) {
                continue;
            }
            if (spreadState.hasWrittenRows()) {
                spreadState.insertGapRow();
            }
            int groupStartRow = spreadState.nextRow();
            for (AgreementTimePeriod subPeriod : subsForParent) {
                for (int tier = 1; tier <= configuration.getSlabCount(); tier++) {
                    Row dataRow = spreadState.sheet().createRow(spreadState.nextRow());

                    setNumericCell(dataRow, layout.colEntityId(), subPeriod.getId(), lockedStyle);

                    String parentDisplay = parent.getName().equals(spreadState.lastParentPeriod())
                            ? ""
                            : TimePeriodDisplayFormatter.format(parent, financialYearStartMonth);
                    setStringCell(dataRow, layout.colParentPeriod(), parentDisplay, lockedStyle);
                    if (!parentDisplay.isEmpty()) {
                        spreadState.setLastParentPeriod(parent.getName());
                    }

                    String subDisplay = subPeriod.getName().equals(spreadState.lastSubPeriod())
                            ? ""
                            : TimePeriodDisplayFormatter.formatBase(subPeriod);
                    setStringCell(dataRow, layout.colSubPeriod(), subDisplay, lockedStyle);
                    if (!subDisplay.isEmpty()) {
                        spreadState.setLastSubPeriod(subPeriod.getName());
                    }

                    setStringCell(dataRow, layout.colSlabTier(), slabTierLabel(tier), lockedStyle);
                    setNumericCell(dataRow, layout.colConfigId(), configuration.getId(), lockedStyle);

                    for (int col = layout.editableStartCol(); col <= layout.editableEndCol(); col++) {
                        Cell cell = dataRow.createCell(col);
                        cell.setCellStyle(editableStyle);
                    }

                    spreadState.advanceRow();
                    spreadState.markWritten();
                }
            }
            int groupEndRow = spreadState.nextRow() - 1;
            if (groupEndRow >= groupStartRow) {
                spreadState.sheet().groupRow(groupStartRow, groupEndRow);
                spreadState.sheet().setRowSumsBelow(false);
            }
        }
    }

    private void writeHeaderRow(Sheet sheet, String[] headers, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(HEADER_ROW);
        for (int col = 0; col < headers.length; col++) {
            setStringCell(headerRow, col, headers[col], headerStyle);
        }
    }

    private void finalizeSheet(Sheet sheet, Columns layout, Workbook workbook) {
        sheet.setColumnHidden(layout.colEntityId(), true);
        sheet.setColumnHidden(layout.colConfigId(), true);
        // For spread sheets: apply Target Type dropdown (ABSOLUTE or RELATIVE).
        // For master sheets: Target Type column no longer exists; skip validation.
        if (!layout.master()) {
            String[] targetTypeOptions = JbpExcelSheetLayout.VALUE_TYPE_OPTIONS;
            applyValueTypeValidation(sheet, layout.colTargetType(), workbook, targetTypeOptions);
        }
        String[] payoutTypeOptions = layout.master()
                ? JbpExcelSheetLayout.VALUE_TYPE_OPTIONS
                : JbpExcelSheetLayout.OPTIONAL_VALUE_TYPE_OPTIONS;
        applyValueTypeValidation(sheet, layout.colPayoutType(), workbook, payoutTypeOptions);
        for (int col = 0; col < layout.headers().length; col++) {
            sheet.autoSizeColumn(col);
            sheet.setColumnWidth(col, sheet.getColumnWidth(col) + 2500);
        }
        sheet.setRowSumsBelow(false);
    }

    private void applyValueTypeValidation(
            Sheet sheet,
            int columnIndex,
            Workbook workbook,
            String[] options) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        CellRangeAddressList addressList = new CellRangeAddressList(
                DATA_START_ROW, VALIDATION_END_ROW, columnIndex, columnIndex);
        DataValidationConstraint constraint = helper.createExplicitListConstraint(options);
        DataValidation validation = helper.createValidation(constraint, addressList);
        validation.setShowErrorBox(true);
        validation.setSuppressDropDownArrow(true);
        sheet.addValidationData(validation);
    }

    private String slabTierLabel(int tierNumber) {
        return "Slab " + tierNumber;
    }

    private void validateContractDates(AgreementVersion version) {
        if (version.getStartDate() == null || version.getExpiryDate() == null) {
            throw new IncompleteAgreementException(
                    "Contract start and expiry dates must be saved before generating JBP workbook.");
        }
    }

    private void validateRequest(JbpWorkbookRequest request) {
        if (request.configurations() == null || request.configurations().isEmpty()) {
            throw new IncompleteAgreementException("Add at least one JBP configuration.");
        }
        if (request.financialYearStartMonth() == null
                || request.financialYearStartMonth() < 1
                || request.financialYearStartMonth() > 12) {
            throw new IncompleteAgreementException("Financial year start month must be between 1 and 12.");
        }
        for (JbpConfigurationBlockDto config : request.configurations()) {
            if (config.maxSlabs() == null || config.maxSlabs() < 1) {
                throw new BusinessException("Each configuration must define at least one slab.");
            }
            if (config.paymentIntervals() == null || config.paymentIntervals().isEmpty()) {
                throw new IncompleteAgreementException(
                        "Select at least one payment interval for configuration " + config.configId() + ".");
            }
            for (String frequency : config.paymentIntervals()) {
                PayoutFrequency parsed = parseFrequency(frequency);
                if (!JBP_FREQUENCIES.contains(parsed)) {
                    throw new BusinessException("Unsupported JBP frequency: " + frequency);
                }
            }
            if (config.targetIntervals() != null) {
                for (String frequency : config.targetIntervals()) {
                    PayoutFrequency parsed = parseFrequency(frequency);
                    if (!JBP_FREQUENCIES.contains(parsed)) {
                        throw new BusinessException("Unsupported JBP target frequency: " + frequency);
                    }
                }
            }
        }
        JbpConfigurationCollisionValidator.validateNoPaymentIntervalOverlap(request.configurations());
    }

    /**
     * Derives parent periods from the selected payment interval frequencies within
     * the contract window.
     * All periods of all specified frequencies that fall within the agreement
     * version's date range are returned.
     */
    private List<AgreementTimePeriod> loadParentPeriodsForIntervals(
            AgreementVersion version,
            List<String> paymentIntervals,
            Integer financialYearStartMonth,
            Long currentUserId) {
        List<AgreementTimePeriod> allPeriods = new ArrayList<>();
        for (String raw : paymentIntervals) {
            PayoutFrequency frequency = parseFrequency(raw);
            AgreementSlab probe = AgreementSlab.builder().payoutFrequency(frequency).build();
            List<AgreementTimePeriod> periodsForFrequency = periodResolutionService.resolvePeriodsForSlab(
                    version, probe, currentUserId, financialYearStartMonth);
            for (AgreementTimePeriod period : periodsForFrequency) {
                period = periodResolutionService.canonicalizePeriod(period, version, financialYearStartMonth,
                        currentUserId);
                allPeriods.add(period);
            }
        }
        allPeriods.sort(TimePeriodDimensions.chronologicalComparator());
        return allPeriods;
    }

    /**
     * Loads all periods for a single frequency within the contract window.
     */
    private List<AgreementTimePeriod> loadPeriodsForInterval(
            AgreementVersion version,
            PayoutFrequency frequency,
            Integer financialYearStartMonth,
            Long currentUserId) {
        AgreementSlab probe = AgreementSlab.builder().payoutFrequency(frequency).build();
        List<AgreementTimePeriod> periods = periodResolutionService.resolvePeriodsForSlab(
                version, probe, currentUserId, financialYearStartMonth);
        List<AgreementTimePeriod> canonicalized = new ArrayList<>();
        for (AgreementTimePeriod period : periods) {
            period = periodResolutionService.canonicalizePeriod(period, version, financialYearStartMonth,
                    currentUserId);
            canonicalized.add(period);
        }
        canonicalized.sort(TimePeriodDimensions.chronologicalComparator());
        return canonicalized;
    }

    /**
     * @deprecated Use loadParentPeriodsForIntervals instead. Kept for legacy export
     *             flow.
     */
    private List<AgreementTimePeriod> loadParentPeriods(
            AgreementVersion version,
            List<Long> parentPeriodIds,
            Integer financialYearStartMonth,
            Long currentUserId) {
        List<AgreementTimePeriod> parentPeriods = new ArrayList<>();
        PayoutFrequency expectedFrequency = null;
        for (Long periodId : parentPeriodIds) {
            AgreementTimePeriod period = timePeriodRepository.findById(periodId)
                    .orElseThrow(() -> new ResourceNotFoundException("AgreementTimePeriod", periodId));
            period = periodResolutionService.canonicalizePeriod(
                    period, version, financialYearStartMonth, currentUserId);
            if (!periodResolutionService.periodWithinContract(period, version)) {
                throw new BusinessException("Period " + period.getName() + " falls outside contract dates");
            }
            if (expectedFrequency == null) {
                expectedFrequency = period.getPeriodFrequency();
            } else if (period.getPeriodFrequency() != expectedFrequency) {
                throw new BusinessException("All parent periods in a configuration must share the same frequency.");
            }
            parentPeriods.add(period);
        }
        parentPeriods.sort(TimePeriodDimensions.chronologicalComparator());
        return parentPeriods;
    }

    /**
     * Sorts workbook sheets: Instructions first, then by config number (ascending),
     * then master sheets before spread sheets, then by frequency rank (descending).
     * Supports Master_, Spread_, and Target_ sheet names.
     */
    private void sortWorkbookSheets(Workbook workbook) {
        record SheetSortInfo(String name, int frequencyRank, boolean master, int configNumber) {
        }
        List<SheetSortInfo> sortInfos = new java.util.ArrayList<>();
        // Match Config{N}-Master_*, Config{N}-Spread_*, or Config{N}-Target_*
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("^Config(\\d+)-(?:Master|Spread|Target)_([A-Z_]+)$");
        for (int i = 1; i < workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetName(i);
            java.util.regex.Matcher m = p.matcher(name);
            if (m.matches()) {
                int cNum = Integer.parseInt(m.group(1));
                PayoutFrequency freq = PayoutFrequency.valueOf(m.group(2));
                int rank = JbpTemporalReconciliationUtil.frequencyRank(freq);
                boolean isMaster = name.contains("-Master_") || name.contains("-Target_");
                sortInfos.add(new SheetSortInfo(name, rank, isMaster, cNum));
            }
        }
        sortInfos.sort(java.util.Comparator
                .comparingInt(SheetSortInfo::configNumber)
                .thenComparing(info -> !info.master())
                .thenComparing(java.util.Comparator.comparingInt(SheetSortInfo::frequencyRank).reversed()));

        int sheetIndex = 1;
        for (SheetSortInfo info : sortInfos) {
            workbook.setSheetOrder(info.name(), sheetIndex++);
        }
    }

    private AgreementJbpConfiguration provisionJbpConfiguration(
            AgreementVersion version,
            JbpConfigurationBlockDto config) {
        AgreementJbpConfiguration configuration = AgreementJbpConfiguration.builder()
                .agreementVersion(version)
                .slabCount(config.maxSlabs())
                .build();
        configuration = jbpConfigurationRepository.save(configuration);
        jbpCommercialService.persistConfigurationBlock(configuration, config);
        return configuration;
    }

    private PayoutFrequency parseFrequency(String raw) {
        try {
            return PayoutFrequency.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid payout frequency: " + raw);
        }
    }

    private CellStyle createLockedStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        return style;
    }

    private CellStyle createEditableStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("[>=100000]##,##,##0.00;[>=1000]##,##0.00;##0.00"));
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook, CellStyle baseStyle) {
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.cloneStyleFrom(baseStyle);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        return headerStyle;
    }

    private void setStringCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setNumericCell(Row row, int col, Long value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    private static final class MasterSheetState {
        private final Sheet sheet;
        private int nextRow;
        private boolean hasWrittenRows;
        private String lastPeriodName;

        private MasterSheetState(Sheet sheet, int nextRow) {
            this.sheet = sheet;
            this.nextRow = nextRow;
        }

        private Sheet sheet() {
            return sheet;
        }

        private int nextRow() {
            return nextRow;
        }

        private boolean hasWrittenRows() {
            return hasWrittenRows;
        }

        private String lastPeriodName() {
            return lastPeriodName;
        }

        private void setLastPeriodName(String lastPeriodName) {
            this.lastPeriodName = lastPeriodName;
        }

        private void insertGapRow() {
            sheet.createRow(nextRow);
            nextRow++;
            lastPeriodName = null;
        }

        private void advanceRow() {
            nextRow++;
        }

        private void markWritten() {
            hasWrittenRows = true;
        }
    }

    private static final class SpreadSheetState {
        private final Sheet sheet;
        private int nextRow;
        private boolean hasWrittenRows;
        private String lastParentPeriod;
        private String lastSubPeriod;

        private SpreadSheetState(Sheet sheet, int nextRow) {
            this.sheet = sheet;
            this.nextRow = nextRow;
        }

        private Sheet sheet() {
            return sheet;
        }

        private int nextRow() {
            return nextRow;
        }

        private boolean hasWrittenRows() {
            return hasWrittenRows;
        }

        private String lastParentPeriod() {
            return lastParentPeriod;
        }

        private void setLastParentPeriod(String lastParentPeriod) {
            this.lastParentPeriod = lastParentPeriod;
        }

        private String lastSubPeriod() {
            return lastSubPeriod;
        }

        private void setLastSubPeriod(String lastSubPeriod) {
            this.lastSubPeriod = lastSubPeriod;
        }

        private void insertGapRow() {
            sheet.createRow(nextRow);
            nextRow++;
            lastParentPeriod = null;
            lastSubPeriod = null;
        }

        private void advanceRow() {
            nextRow++;
        }

        private void markWritten() {
            hasWrittenRows = true;
        }
    }
}
