package io.datapulse.etl.domain.source.yandex;

import java.util.List;

import io.datapulse.etl.adapter.yandex.YandexFinanceReportReadAdapter;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Yandex FACT_FINANCE: orchestrates two async reports:
 * <ol>
 *   <li>United Marketplace Services — commission/logistics/service fees</li>
 *   <li>Goods Realization — realization data with item-level financials</li>
 * </ol>
 * Both reports follow generate → poll → download → parse flow
 * (handled internally by {@link YandexFinanceReportReadAdapter}).
 * <p>
 * Normalization is stub until real data format is verified (DD-23).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YandexFinanceFactSource implements EventSource {

  private static final String SOURCE_ID = "YandexFinanceReportReadAdapter";

  private final YandexFinanceReportReadAdapter adapter;

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
    var dateFrom = ctx.wbFactDateFrom();
    var dateTo = ctx.wbFactDateTo();

    var captureCtx = CaptureContextFactory.build(ctx, eventType(), SOURCE_ID);

    try {
      adapter.captureFinanceForPeriod(
          captureCtx, apiKey, meta.businessId(), dateFrom, dateTo);

      log.info("Yandex finance capture completed: connectionId={}, period={} to {}",
          ctx.connectionId(), dateFrom, dateTo);
      return List.of(SubSourceResult.success(SOURCE_ID, 1, 0));
    } catch (Exception e) {
      log.error("Yandex finance capture failed: connectionId={}, error={}",
          ctx.connectionId(), e.getMessage(), e);
      return List.of(SubSourceResult.failed(SOURCE_ID, e.getMessage()));
    }
  }
}
