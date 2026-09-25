import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;

public class TestPoiLock {
    public static void main(String[] args) {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet();
        sheet.protectSheet("password");
        sheet.lockFormatCells(false);
        sheet.lockFormatColumns(false);
        sheet.lockFormatRows(false);
    }
}
