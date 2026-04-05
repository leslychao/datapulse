package io.datapulse.etl.adapter.wb;

import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.util.OffsetPagedCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WbStocksReadAdapter {

  private static final String STOCKS_PATH = "/api/analytics/v1/stocks-report/wb-warehouses";
  private static final int PAGE_LIMIT = 10_000;
  private static final int SMALL_PAGE_THRESHOLD_BYTES = 500;

  private final WbApiCaller apiCaller;
  private final IntegrationProperties properties;
  private final OffsetPagedCapture offsetPagedCapture;

  public List<CaptureResult> captureAllPages(CaptureContext context, String apiToken) {
    String baseUrl = properties.getWildberries().getAnalyticsBaseUrl();

    return offsetPagedCapture.captureAllPages(
        context, PAGE_LIMIT,
        OffsetPagedCapture.DEFAULT_MAX_PAGES,
        SMALL_PAGE_THRESHOLD_BYTES,
        "WB stocks",
        (offset, pageNumber) -> apiCaller.post(
            baseUrl + STOCKS_PATH,
            Map.of("limit", PAGE_LIMIT, "offset", offset),
            apiToken, context.connectionId(), RateLimitGroup.WB_ANALYTICS));
  }
}
