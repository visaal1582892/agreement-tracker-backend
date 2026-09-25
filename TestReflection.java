import java.lang.reflect.Method;
public class TestReflection {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("org.apache.poi.xssf.usermodel.XSSFSheet");
        for (Method m : clazz.getMethods()) {
            if (m.getName().toLowerCase().contains("formatcolumn") || m.getName().toLowerCase().contains("formatcell")) {
                System.out.println(m.getName());
            }
        }
    }
}
