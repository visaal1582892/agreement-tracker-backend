package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.entity.ChannelMaster;
import com.medplus.agreement_tracker_backend.entity.PriceOffLocationMaster;
import com.medplus.agreement_tracker_backend.enums.PriceOffDiscountType;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.repository.ChannelMasterRepository;
import com.medplus.agreement_tracker_backend.repository.PriceOffLocationMasterRepository;
import com.medplus.agreement_tracker_backend.service.PriceOffExcelGeneratorService;
import com.medplus.agreement_tracker_backend.util.PriceOffExcelLayout;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PriceOffExcelGeneratorServiceImpl implements PriceOffExcelGeneratorService {

    private final PriceOffLocationMasterRepository locationMasterRepository;
    private final ChannelMasterRepository channelRepository;

    @Override
    @Transactional(readOnly = true)
    public byte[] generateTemplate() {
        List<PriceOffLocationMaster> activeLocations =
                locationMasterRepository.findByIsActiveTrueOrderByCodeAsc();
        List<ChannelMaster> channels = channelRepository.findByIsActiveTrueOrderByChannelNameAsc();

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(PriceOffExcelLayout.SHEET_NAME);
            Sheet listsSheet = workbook.createSheet(PriceOffExcelLayout.LISTS_SHEET);

            List<String> channelOptions = buildChannelOptions(channels);
            List<String> discountOptions = Arrays.stream(PriceOffDiscountType.values())
                    .map(PriceOffDiscountType::getLabel)
                    .toList();

            writeListColumn(listsSheet, 0, channelOptions);
            writeListColumn(listsSheet, 1, discountOptions);
            workbook.setSheetHidden(workbook.getSheetIndex(listsSheet), true);

            List<String> headers = buildHeaders(activeLocations);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle editableStyle = createEditableStyle(workbook);
            Row header = sheet.createRow(PriceOffExcelLayout.HEADER_ROW);
            for (int col = 0; col < headers.size(); col++) {
                Cell cell = header.createCell(col);
                cell.setCellValue(headers.get(col));
                cell.setCellStyle(headerStyle);
                sheet.autoSizeColumn(col);
            }

            createEditableDataRows(sheet, headers.size(), editableStyle);

            DataValidationHelper helper = sheet.getDataValidationHelper();
            applyDateColumn(
                    workbook,
                    sheet,
                    helper,
                    editableStyle,
                    PriceOffExcelLayout.COL_START_DATE_INDEX,
                    "Enter date as yyyy-MM-dd (example: 2026-07-01). Excel date picker available when cell is selected.");
            applyDateColumn(
                    workbook,
                    sheet,
                    helper,
                    editableStyle,
                    PriceOffExcelLayout.COL_END_DATE_INDEX,
                    "Enter end date as yyyy-MM-dd. Must be on or after Start Date.");

            int channelColIndex = headers.indexOf(PriceOffExcelLayout.COL_CHANNEL);
            int discountColIndex = headers.indexOf(PriceOffExcelLayout.COL_DISCOUNT_TYPE);
            applyDropdown(helper, sheet, channelColIndex, channelOptions.size(), listsSheet.getSheetName(), 0);
            applyDropdown(helper, sheet, discountColIndex, discountOptions.size(), listsSheet.getSheetName(), 1);

            createInstructionsSheet(workbook, activeLocations);

            workbook.setActiveSheet(workbook.getSheetIndex(sheet));
            workbook.setSelectedTab(workbook.getSheetIndex(sheet));

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("Failed to generate price off template");
        }
    }

    private List<String> buildHeaders(List<PriceOffLocationMaster> activeLocations) {
        List<String> headers = new ArrayList<>(List.of(
                PriceOffExcelLayout.COL_PRODUCT_ID,
                PriceOffExcelLayout.COL_START_DATE,
                PriceOffExcelLayout.COL_END_DATE,
                PriceOffExcelLayout.COL_MAX_UNIT_CAP,
                PriceOffExcelLayout.COL_LOCATION,
                PriceOffExcelLayout.COL_CHANNEL,
                PriceOffExcelLayout.COL_DISCOUNT_TYPE,
                PriceOffExcelLayout.COL_CP,
                PriceOffExcelLayout.COL_MRP,
                PriceOffExcelLayout.COL_BASE_OFFER,
                PriceOffExcelLayout.COL_MEDPLUS_CONTRIBUTION,
                PriceOffExcelLayout.COL_FROM_QTY));

        headers.add(PriceOffExcelLayout.COL_REMARKS);
        return headers;
    }

    private void createInstructionsSheet(Workbook workbook, List<PriceOffLocationMaster> activeLocations) {
        Sheet sheet = workbook.createSheet(PriceOffExcelLayout.INSTRUCTIONS_SHEET);
        CellStyle headerStyle = createHeaderStyle(workbook);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Price Off Campaign Upload Instructions");
        titleCell.setCellStyle(headerStyle);

        String locationCodes = activeLocations.stream()
                .map(PriceOffLocationMaster::getCode)
                .reduce((left, right) -> left + ", " + right)
                .orElse("AP, TG, KA");

        List<String> instructions = List.of(
                "1. Fill one row per campaign in the 'Price Off Campaigns' sheet.",
                "2. Location column: enter the campaign zone label (e.g., Pan India (7 states)).",
                "3. Start Date and End Date must be yyyy-MM-dd. End Date must be on or after Start Date.",
                "4. Channel and Discount Type must match the dropdown values.");

        for (int i = 0; i < instructions.size(); i++) {
            Row row = sheet.createRow(i + 2);
            row.createCell(0).setCellValue(instructions.get(i));
        }
        sheet.autoSizeColumn(0);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return headerStyle;
    }

    private CellStyle createEditableStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("[>=100000]##,##,##0.00;[>=1000]##,##0.00;##0.00"));
        return style;
    }

    private void createEditableDataRows(Sheet sheet, int columnCount, CellStyle editableStyle) {
        for (int rowIdx = PriceOffExcelLayout.DATA_START_ROW; rowIdx <= PriceOffExcelLayout.MAX_DATA_ROWS; rowIdx++) {
            Row row = sheet.createRow(rowIdx);
            for (int col = 0; col < columnCount; col++) {
                Cell cell = row.createCell(col);
                cell.setCellStyle(editableStyle);
            }
        }
    }

    private List<String> buildChannelOptions(List<ChannelMaster> channels) {
        List<String> options = new ArrayList<>();
        options.add(PriceOffExcelLayout.ALL_CHANNELS);
        channels.forEach(channel -> options.add(channel.getChannelName()));
        return options;
    }

    private void writeListColumn(Sheet sheet, int columnIndex, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                row = sheet.createRow(i);
            }
            row.createCell(columnIndex).setCellValue(values.get(i));
        }
    }

    private void applyDateColumn(
            Workbook workbook,
            Sheet sheet,
            DataValidationHelper helper,
            CellStyle editableStyle,
            int colIndex,
            String headerComment) {
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.cloneStyleFrom(editableStyle);
        CreationHelper createHelper = workbook.getCreationHelper();
        dateStyle.setDataFormat(createHelper.createDataFormat().getFormat(PriceOffExcelLayout.EXCEL_DATE_FORMAT));
        sheet.setDefaultColumnStyle(colIndex, dateStyle);
        sheet.setColumnWidth(colIndex, 15 * 256);

        CellRangeAddressList addressList = new CellRangeAddressList(
                PriceOffExcelLayout.DATA_START_ROW,
                PriceOffExcelLayout.MAX_DATA_ROWS,
                colIndex,
                colIndex);

        DataValidationConstraint constraint = helper.createDateConstraint(
                DataValidationConstraint.OperatorType.BETWEEN,
                "DATE(2020,1,1)",
                "DATE(2099,12,31)",
                PriceOffExcelLayout.EXCEL_DATE_FORMAT);

        DataValidation validation = helper.createValidation(constraint, addressList);
        validation.setShowPromptBox(false);
        validation.setEmptyCellAllowed(true);
        validation.createErrorBox(
                "Invalid Date",
                "Please enter a valid date in YYYY-MM-DD format (e.g., 2026-07-01).");
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        sheet.addValidationData(validation);

        for (int rowIdx = PriceOffExcelLayout.DATA_START_ROW; rowIdx <= PriceOffExcelLayout.MAX_DATA_ROWS; rowIdx++) {
            Row dataRow = sheet.getRow(rowIdx);
            if (dataRow != null) {
                Cell dateCell = dataRow.getCell(colIndex);
                if (dateCell != null) {
                    dateCell.setCellStyle(dateStyle);
                }
            }
        }

        Row headerRow = sheet.getRow(PriceOffExcelLayout.HEADER_ROW);
        if (headerRow == null) {
            return;
        }
        Cell headerCell = headerRow.getCell(colIndex);
        if (headerCell == null || headerComment == null || headerComment.isBlank()) {
            return;
        }
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        ClientAnchor anchor = createHelper.createClientAnchor();
        anchor.setCol1(colIndex);
        anchor.setCol2(colIndex + 3);
        anchor.setRow1(PriceOffExcelLayout.HEADER_ROW);
        anchor.setRow2(PriceOffExcelLayout.HEADER_ROW + 4);
        Comment comment = drawing.createCellComment(anchor);
        comment.setString(createHelper.createRichTextString(headerComment));
        headerCell.setCellComment(comment);
    }

    private void applyDropdown(
            DataValidationHelper helper,
            Sheet sheet,
            int columnIndex,
            int optionCount,
            String listsSheetName,
            int listsColumnIndex) {
        if (optionCount <= 0 || columnIndex < 0) {
            return;
        }
        char columnLetter = (char) ('A' + listsColumnIndex);
        String formula = listsSheetName + "!$" + columnLetter + "$1:$" + columnLetter + "$" + optionCount;
        CellRangeAddressList addressList = new CellRangeAddressList(
                PriceOffExcelLayout.DATA_START_ROW,
                PriceOffExcelLayout.MAX_DATA_ROWS,
                columnIndex,
                columnIndex);
        DataValidationConstraint constraint = helper.createFormulaListConstraint(formula);
        DataValidation validation = helper.createValidation(constraint, addressList);
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox("Invalid value", "Select a value from the dropdown list.");
        sheet.addValidationData(validation);
    }
}
