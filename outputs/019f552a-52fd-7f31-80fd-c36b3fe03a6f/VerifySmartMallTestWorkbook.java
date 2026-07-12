import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class VerifySmartMallTestWorkbook {
    public static void main(String[] args) throws Exception {
        Path file = Path.of("G:/claudeproject/Agent Store/outputs/019f552a-52fd-7f31-80fd-c36b3fe03a6f/Agent-Store-智能商城-软件测试交付包-20260712.xlsx");
        String[] expected = {"测试计划", "测试用例", "环境与账号", "缺陷登记", "测试数据", "测试报告"};
        try (XSSFWorkbook workbook = new XSSFWorkbook(new FileInputStream(file.toFile()))) {
            if (workbook.getNumberOfSheets() != expected.length) throw new IllegalStateException("sheet count");
            for (int i = 0; i < expected.length; i++) {
                if (!expected[i].equals(workbook.getSheetName(i))) throw new IllegalStateException("sheet " + i);
            }
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            evaluator.evaluateAll();
            DataFormatter formatter = new DataFormatter();
            List<String> errors = new ArrayList<>();
            int formulas = 0;
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                XSSFSheet sheet = workbook.getSheetAt(s);
                for (Row row : sheet) for (Cell cell : row) {
                    if (cell.getCellType() == CellType.FORMULA) {
                        formulas++;
                        var value = evaluator.evaluate(cell);
                        if (value != null && value.getCellType() == CellType.ERROR) errors.add(sheet.getSheetName() + "!" + cell.getAddress());
                    }
                }
            }
            if (!errors.isEmpty()) throw new IllegalStateException("formula errors: " + errors);
            XSSFSheet report = workbook.getSheet("测试报告");
            String total = formatter.formatCellValue(report.getRow(3).getCell(1), evaluator);
            String pass = formatter.formatCellValue(report.getRow(3).getCell(3), evaluator);
            String fail = formatter.formatCellValue(report.getRow(3).getCell(5), evaluator);
            String notExecuted = formatter.formatCellValue(report.getRow(3).getCell(7), evaluator);
            String rate = formatter.formatCellValue(report.getRow(4).getCell(1), evaluator);
            if (!"74".equals(total) || !"54".equals(pass) || !"17".equals(fail) || !"3".equals(notExecuted)) {
                throw new IllegalStateException("unexpected report metrics: " + total + "," + pass + "," + fail + "," + notExecuted);
            }
            if (workbook.getSheet("测试用例").getLastRowNum() < 70) throw new IllegalStateException("test cases missing");
            if (workbook.getSheet("缺陷登记").getLastRowNum() < 35) throw new IllegalStateException("defects missing");
            System.out.println("VERIFY_OK sheets=" + workbook.getNumberOfSheets()
                    + " formulas=" + formulas
                    + " report=" + total + "/" + pass + "/" + fail + "/" + notExecuted
                    + " rate=" + rate
                    + " validations=" + workbook.getSheet("测试用例").getDataValidations().size() + "/" + workbook.getSheet("缺陷登记").getDataValidations().size());
        }
    }
}
