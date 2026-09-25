import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;

public class TestPoi2 {
    public static void main(String[] args) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet();
        sheet.protectSheet("password");
        sheet.lockFormatColumns(false);
        sheet.lockFormatCells(false);
        wb.write(new java.io.FileOutputStream("test.xlsx"));
        System.out.println("Success!");
    }
}
