package io.datapulse.sellerops.domain;

import io.datapulse.sellerops.api.GridFilter;
import io.datapulse.sellerops.config.GridProperties;
import io.datapulse.sellerops.persistence.GridPostgresReadRepository;
import io.datapulse.sellerops.persistence.GridRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GridExportService {

  private final GridPostgresReadRepository pgRepository;
  private final GridProperties gridProperties;

  private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  private static final String CSV_HEADER = String.join(";",
      "Артикул", "Название", "Маркетплейс", "Подключение", "Статус",
      "Категория", "Цена", "Цена со скидкой", "Себестоимость",
      "Маржа %", "Остаток", "Политика", "Решение", "Статус действия",
      "Промо", "Блокировка", "Синхронизация"
  );

  private static final int BATCH_SIZE = 500;

  @Transactional(readOnly = true)
  public void exportCsv(long workspaceId, GridFilter filter, OutputStream out) {
    long deadlineMs = System.currentTimeMillis()
        + gridProperties.getExportTimeoutSeconds() * 1000L;

    try {
      out.write(UTF8_BOM);
      var writer = new PrintWriter(
          new OutputStreamWriter(out, StandardCharsets.UTF_8));
      writer.println(CSV_HEADER);

      int offset = 0;
      int totalExported = 0;
      int maxRows = gridProperties.getExportMaxRows();

      while (true) {
        if (System.currentTimeMillis() > deadlineMs) {
          log.warn("Export timeout reached: workspaceId={}, timeoutSec={}",
              workspaceId, gridProperties.getExportTimeoutSeconds());
          break;
        }

        List<GridRow> batch = pgRepository.findBatchForExport(
            workspaceId, filter, BATCH_SIZE, offset);
        if (batch.isEmpty()) {
          break;
        }

        totalExported += batch.size();
        if (totalExported > maxRows) {
          log.warn("Export row limit exceeded: workspaceId={}, limit={}",
              workspaceId, maxRows);
          break;
        }

        for (GridRow row : batch) {
          writer.println(toCsvLine(row));
        }
        writer.flush();

        if (batch.size() < BATCH_SIZE) {
          break;
        }
        offset += BATCH_SIZE;
      }

      writer.flush();
    } catch (Exception e) {
      log.error("CSV export failed: workspaceId={}, error={}",
          workspaceId, e.getMessage());
      throw new RuntimeException("CSV export failed", e);
    }
  }

  private String toCsvLine(GridRow row) {
    return String.join(";",
        esc(row.getSkuCode()),
        esc(row.getProductName()),
        esc(row.getMarketplaceType()),
        esc(row.getConnectionName()),
        esc(row.getStatus()),
        esc(row.getCategory()),
        fmtDecimal(row.getCurrentPrice()),
        fmtDecimal(row.getDiscountPrice()),
        fmtDecimal(row.getCostPrice()),
        fmtDecimal(row.getMarginPct()),
        row.getAvailableStock() != null
            ? String.valueOf(row.getAvailableStock()) : "",
        esc(row.getActivePolicy()),
        esc(row.getLastDecision()),
        esc(row.getLastActionStatus()),
        esc(row.getPromoStatus()),
        row.isManualLock() ? "Да" : "Нет",
        row.getLastSyncAt() != null ? row.getLastSyncAt().toString() : ""
    );
  }

  private String esc(String val) {
    if (val == null) {
      return "";
    }
    if (val.contains(";") || val.contains("\"") || val.contains("\n")) {
      return "\"" + val.replace("\"", "\"\"") + "\"";
    }
    return val;
  }

  private String fmtDecimal(BigDecimal val) {
    return val != null ? val.toPlainString() : "";
  }

}
