package io.datapulse.etl.domain.source.yandex;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.datapulse.etl.adapter.yandex.YandexFinanceReportReadAdapter;
import io.datapulse.etl.adapter.yandex.YandexNormalizer;
import io.datapulse.etl.adapter.yandex.dto.YandexRealizationReportRow;
import io.datapulse.etl.adapter.yandex.dto.YandexServicesReportRow;
import io.datapulse.etl.domain.CanonicalFinanceNormalizer;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.FinanceEntryType;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.normalized.NormalizedFinanceItem;
import io.datapulse.etl.persistence.canonical.CanonicalFinanceEntryUpsertRepository;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Yandex FACT_FINANCE: orchestrates two async reports and normalizes into canonical.
 * <ol>
 *   <li>United Marketplace Services — commission/logistics/service fees</li>
 *   <li>Goods Realization — realization data with item-level financials</li>
 * </ol>
 * Both reports follow generate → poll → download → parse → normalize → upsert flow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YandexFinanceFactSource implements EventSource {

  private static final String SOURCE_ID = "YandexFinanceReportReadAdapter";
  private static final int BATCH_SIZE = 500;

  private final YandexFinanceReportReadAdapter adapter;
  private final YandexNormalizer normalizer;
  private final CanonicalFinanceNormalizer financeNormalizer;
  private final CanonicalFinanceEntryUpsertRepository repository;

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.YANDEX;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.FACT_FINANCE;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext ctx) {
    String apiKey = ctx.credentials().get(CredentialKeys.YANDEX_API_KEY);
    YandexMetadata meta = YandexMetadata.parse(ctx.connectionMetadata());
    LocalDate dateFrom = ctx.wbFactDateFrom();
    LocalDate dateTo = ctx.wbFactDateTo();
    long connectionId = ctx.connectionId();

    Set<String> unmappedTypes = ConcurrentHashMap.newKeySet();
    int totalRecords = 0;

    try {
      var captureCtx = CaptureContextFactory.build(ctx, eventType(), SOURCE_ID);

      List<YandexServicesReportRow> servicesRows = adapter.captureServicesReport(
          apiKey, connectionId, meta.businessId(), dateFrom, dateTo);
      log.info("Yandex services report captured: connectionId={}, rows={}",
          connectionId, servicesRows.size());

      totalRecords += normalizeAndUpsert(
          servicesRows, ctx, unmappedTypes);

      List<YandexFinanceReportReadAdapter.MonthPeriod> months =
          YandexFinanceReportReadAdapter.splitIntoMonths(dateFrom, dateTo);
      List<YandexRealizationReportRow> realizationRows = new ArrayList<>();
      for (var month : months) {
        realizationRows.addAll(adapter.captureRealizationReport(
            apiKey, connectionId, meta.businessId(), month.year(), month.month()));
      }
      log.info("Yandex realization report captured: connectionId={}, rows={}",
          connectionId, realizationRows.size());

      totalRecords += normalizeRealizationAndUpsert(realizationRows, ctx);

      log.info("Yandex finance normalization completed: connectionId={}, total={}",
          connectionId, totalRecords);

      if (!unmappedTypes.isEmpty()) {
        return List.of(SubSourceResult.successWithWarnings(
            SOURCE_ID, 1, totalRecords, unmappedTypes));
      }
      return List.of(SubSourceResult.success(SOURCE_ID, 1, totalRecords));

    } catch (Exception e) {
      log.error("Yandex finance capture/normalization failed: connectionId={}, error={}",
          connectionId, e.getMessage(), e);
      return List.of(SubSourceResult.failed(SOURCE_ID, e.getMessage()));
    }
  }

  private int normalizeAndUpsert(List<YandexServicesReportRow> rows,
      IngestContext ctx, Set<String> unmappedTypes) {
    int processed = 0;

    for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
      int end = Math.min(i + BATCH_SIZE, rows.size());
      List<YandexServicesReportRow> batch = rows.subList(i, end);

      List<NormalizedFinanceItem> normalized = batch.stream()
          .map(row -> {
            NormalizedFinanceItem item = normalizer.normalizeServiceCharge(row);
            if (item.entryType() == FinanceEntryType.OTHER
                && row.serviceName() != null
                && !row.serviceName().isBlank()) {
              unmappedTypes.add(row.serviceName().trim());
            }
            return item;
          })
          .toList();

      repository.batchUpsert(financeNormalizer.normalizeBatch(normalized, ctx));
      processed += normalized.size();
    }

    return processed;
  }

  private int normalizeRealizationAndUpsert(List<YandexRealizationReportRow> rows,
      IngestContext ctx) {
    int processed = 0;

    for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
      int end = Math.min(i + BATCH_SIZE, rows.size());
      List<YandexRealizationReportRow> batch = rows.subList(i, end);

      List<NormalizedFinanceItem> normalized = batch.stream()
          .map(normalizer::normalizeRealization)
          .toList();

      repository.batchUpsert(financeNormalizer.normalizeBatch(normalized, ctx));
      processed += normalized.size();
    }

    return processed;
  }
}
