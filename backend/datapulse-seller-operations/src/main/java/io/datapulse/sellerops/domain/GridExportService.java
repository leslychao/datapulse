package io.datapulse.sellerops.domain;

import io.datapulse.sellerops.config.GridProperties;
import io.datapulse.sellerops.persistence.GridPostgresReadRepository;
import io.datapulse.sellerops.persistence.GridRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
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
    } catch (IOException e) {
      log.error("CSV export failed: workspaceId={}", workspaceId, e);
      throw new IllegalStateException("CSV export failed", e);
    } catch (Exception e) {
      log.error("CSV export failed: workspaceId={}, error={}",
          workspaceId, e.getMessage(), e);
      throw new IllegalStateException("CSV export failed", e);
    }
  }

  /**
   * Exports rows for the given offer ids in the workspace (order preserved, duplicates removed).
   * Ids that do not belong to the workspace are omitted.
   */
  public void exportCsvByOfferIds(long workspaceId, List<Long> offerIds, OutputStream out) {
    List<Long> uniqueIds = dedupePreserveOrder(offerIds);
    long deadlineMs = System.currentTimeMillis()
        + gridProperties.getExportTimeoutSeconds() * 1000L;

    try {
      out.write(UTF8_BOM);
      var writer = new PrintWriter(
          new OutputStreamWriter(out, StandardCharsets.UTF_8));
      writer.println(CSV_HEADER);

      int totalExported = 0;
      int maxRows = gridProperties.getExportMaxRows();

      for (int i = 0; i < uniqueIds.size(); i += BATCH_SIZE) {
        if (System.currentTimeMillis() > deadlineMs) {
          log.warn("Export timeout reached: workspaceId={}, timeoutSec={}",
              workspaceId, gridProperties.getExportTimeoutSeconds());
          break;
        }

        int end = Math.min(i + BATCH_SIZE, uniqueIds.size());
        List<Long> batchIds = uniqueIds.subList(i, end);
        List<GridRow> batch = pgRepository.findByOrderedIds(workspaceId, batchIds);

        for (GridRow row : batch) {
          writer.println(toCsvLine(row));
          totalExported++;
          if (totalExported > maxRows) {
            log.warn("Export row limit exceeded: workspaceId={}, limit={}",
                workspaceId, maxRows);
            writer.flush();
            return;
          }
        }
        writer.flush();
      }

      writer.flush();
    } catch (IOException e) {
      log.error("CSV export by offer ids failed: workspaceId={}", workspaceId, e);
      throw new IllegalStateException("CSV export failed", e);
    } catch (Exception e) {
      log.error("CSV export by offer ids failed: workspaceId={}, error={}",
          workspaceId, e.getMessage(), e);
      throw new IllegalStateException("CSV export failed", e);
    }
  }

  private static List<Long> dedupePreserveOrder(List<Long> offerIds) {
    var seen = new LinkedHashSet<Long>();
    var out = new ArrayList<Long>();
    for (Long id : offerIds) {
      if (id != null && seen.add(id)) {
        out.add(id);
      }
    }
    return out;
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
