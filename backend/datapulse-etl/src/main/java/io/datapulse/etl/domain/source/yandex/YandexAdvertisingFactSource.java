package io.datapulse.etl.domain.source.yandex;

import java.util.List;

import io.datapulse.etl.adapter.yandex.YandexBidsReadAdapter;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
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
 * MVP stub for Yandex advertising data.
 * Currently captures current bid levels (read-only) via {@code /bids/info}.
 * Full advertising analytics implementation is Phase 2.
 * <p>
 * Captured pages are stored in S3 for future processing but not normalized
 * to canonical layer yet — no ClickHouse writer or canonical mapping defined.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YandexAdvertisingFactSource implements EventSource {

  private static final String SOURCE_ID = "YandexBidsReadAdapter";

  private final YandexBidsReadAdapter adapter;

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.YANDEX;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.ADVERTISING_FACT;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext ctx) {
    String apiKey = ctx.credentials().get(CredentialKeys.YANDEX_API_KEY);
    YandexMetadata meta = YandexMetadata.parse(ctx.connectionMetadata());

    var captureCtx = CaptureContextFactory.build(ctx, eventType(), SOURCE_ID);

    try {
      List<CaptureResult> pages = adapter.captureAllPages(
          captureCtx, apiKey, meta.businessId());

      int totalRecords = pages.stream()
          .mapToInt(p -> (int) (p.byteSize() > 0 ? 1 : 0))
          .sum();

      log.info("Yandex bids captured (raw only, no canonical mapping): "
          + "connectionId={}, pages={}", ctx.connectionId(), pages.size());
      return List.of(SubSourceResult.success(SOURCE_ID, pages.size(), totalRecords));
    } catch (Exception e) {
      log.warn("Yandex bids capture failed (non-fatal for MVP): connectionId={}, error={}",
          ctx.connectionId(), e.getMessage(), e);
      return List.of(SubSourceResult.success(SOURCE_ID, 0, 0));
    }
  }
}
