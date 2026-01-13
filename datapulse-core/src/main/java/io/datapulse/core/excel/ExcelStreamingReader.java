package io.datapulse.core.excel;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

public final class ExcelStreamingReader {

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final ZoneId ZONE_ID_UTC = ZoneId.of("UTC");

  private ExcelStreamingReader() {
  }

  public static void readSheet(InputStream inputStream, int sheetIndex,
      Consumer<List<String>> rowConsumer) throws IOException {
    try (Workbook workbook = WorkbookFactory.create(inputStream)) {
      Sheet sheet = workbook.getSheetAt(sheetIndex);
      processSheet(sheet, rowConsumer);
    }
  }

  public static void readSheet(InputStream inputStream, String sheetName,
      Consumer<List<String>> rowConsumer) throws IOException {
    try (Workbook workbook = WorkbookFactory.create(inputStream)) {
      Sheet sheet = workbook.getSheet(sheetName);
      if (sheet == null) {
        return;
      }
      processSheet(sheet, rowConsumer);
    }
  }

  private static void processSheet(Sheet sheet, Consumer<List<String>> rowConsumer) {
    for (Row row : sheet) {
      int lastCellNum = row.getLastCellNum();
      if (lastCellNum < 0) {
        rowConsumer.accept(List.of());
        continue;
      }
      List<String> values = new ArrayList<>(lastCellNum);
      for (int columnIndex = 0; columnIndex < lastCellNum; columnIndex++) {
        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        values.add(cellToString(cell));
      }
      rowConsumer.accept(values);
    }
  }

  private static String cellToString(Cell cell) {
    if (cell == null) {
      return "";
    }
    CellType cellType = cell.getCellType();
    if (cellType == CellType.FORMULA) {
      cellType = cell.getCachedFormulaResultType();
    }
    if (cellType == CellType.STRING) {
      return cell.getStringCellValue();
    }
    if (cellType == CellType.NUMERIC) {
      if (DateUtil.isCellDateFormatted(cell)) {
        return cell.getLocalDateTimeCellValue()
            .atZone(ZONE_ID_UTC)
            .toLocalDate()
            .format(DATE_FORMATTER);
      }
      return Double.toString(cell.getNumericCellValue());
    }
    if (cellType == CellType.BOOLEAN) {
      return Boolean.toString(cell.getBooleanCellValue());
    }
    if (cellType == CellType.BLANK) {
      return "";
    }
    return "";
  }
}
