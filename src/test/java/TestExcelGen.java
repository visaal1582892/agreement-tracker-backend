import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import java.io.FileOutputStream;

public class TestExcelGen {
    @Test
    public void testOutput() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Cutoff Template");
            CellStyle lockedStyle = workbook.createCellStyle();
            lockedStyle.setLocked(true);
            CellStyle editableStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.DataFormat format = workbook.createDataFormat();
            editableStyle.setDataFormat(format.getFormat("[>=100000]##,##,##0.00;[>=1000]##,##0.00;##0.00"));
            editableStyle.setLocked(false);
            
            Row row = sheet.createRow(0);
            
            Cell c1 = row.createCell(0);
            c1.setCellValue("Locked");
            c1.setCellStyle(lockedStyle);
            
            Cell c2 = row.createCell(1);
            c2.setCellValue(100000);
            c2.setCellStyle(editableStyle);
            
            sheet.protectSheet("agreement-tracker");
            if (sheet instanceof org.apache.poi.xssf.usermodel.XSSFSheet xssfSheet) {
                org.openxmlformats.schemas.spreadsheetml.x2006.main.CTSheetProtection sheetProtection = xssfSheet.getCTWorksheet().getSheetProtection();
                sheetProtection.setFormatCells(true);
                sheetProtection.setFormatColumns(true);
            }
            try (FileOutputStream out = new FileOutputStream("test-cutoff.xlsx")) { workbook.write(out); }
        }
    }
}
