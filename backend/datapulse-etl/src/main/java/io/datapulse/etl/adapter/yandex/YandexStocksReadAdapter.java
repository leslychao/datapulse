package io.datapulse.etl.adapter.yandex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.util.CursorPagedCapture;
import io.datapulse.etl.adapter.util.CursorPagedCapture.CursorPageSpec;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.cursor.JsonPathCursorExtractor;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stocks are campaign-level: endpoint requires {@code campaignId}, not {@code businessId}.
 * Adapter fans out requests across all campaigns discovered for the connection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YandexStocksReadAdapter {

  private static final String STOCKS_PATH =
      "/v2/campaigns/%d/offers/stocks";
  private static final int PAGE_LIMIT = 200;

  private static final JsonPathCursorExtractor CURSOR_EXTRACTOR =
      new JsonPathCursorExtractor("result.paging.nextPageToken");

  private final YandexApiCaller apiCaller;
  private final CursorPagedCapture cursorPagedCapture;

  public List<CaptureResult> captureAllPages(
      CaptureContext context, String apiKey, List<Long> campaignIds) {
    List<CaptureResult> allResults = new ArrayList<>();

    for (long campaignId : campaignIds) {
      log.info("Yandex stocks: starting capture for campaignId={}, connectionId={}",
          campaignId, context.connectionId());

      String basePath = STOCKS_PATH.formatted(campaignId);

      CursorPageSpec spec = new CursorPageSpec(
          CURSOR_EXTRACTOR,
          "Yandex stocks (campaign=%d)".formatted(campaignId),
          (cursor, pageNumber) -> {
            String path = buildPagePath(basePath, cursor);
            return apiCaller.post(
                path, context.connectionId(), RateLimitGroup.YANDEX_DEFAULT,
                apiKey, Map.of());
          });

      List<CaptureResult> campaignResults = cursorPagedCapture.captureAllPages(
          context, null, spec, CursorPagedCapture.NO_OP);
      allResults.addAll(campaignResults);

      log.info("Yandex stocks: campaignId={} captured, pages={}",
          campaignId, campaignResults.size());
    }

    log.info("Yandex stocks capture completed: connectionId={}, campaigns={}, totalPages={}",
        context.connectionId(), campaignIds.size(), allResults.size());
    return allResults;
  }

  private String buildPagePath(String basePath, String cursor) {
    if (cursor == null || cursor.isEmpty()) {
      return basePath + "?limit=" + PAGE_LIMIT;
    }
    return basePath + "?limit=" + PAGE_LIMIT + "&pageToken=" + cursor;
  }
}
