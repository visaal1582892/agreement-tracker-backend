import java.io.FileOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;

public class TestPoiXML {
    public static void main(String[] args) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet();
        sheet.protectSheet("password");
        sheet.lockFormatColumns(false);
        try (FileOutputStream out = new FileOutputStream("test.xlsx")) {
            wb.write(out);
        }
    }
}
