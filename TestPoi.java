import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;

public class TestPoi {
    public static void main(String[] args) {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet();
        sheet.protectSheet("password");
        sheet.lockFormatColumns(false);
        sheet.lockFormatRows(false);
        sheet.lockFormatCells(false);
        System.out.println("Success!");
    }
}
