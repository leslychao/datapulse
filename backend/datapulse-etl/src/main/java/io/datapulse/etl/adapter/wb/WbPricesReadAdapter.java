package io.datapulse.etl.adapter.wb;

import java.util.List;

import io.datapulse.etl.adapter.util.OffsetPagedCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WbPricesReadAdapter {

  private static final String PRICES_PATH = "/api/v2/list/goods/filter";
  private static final int PAGE_LIMIT = 1000;
  private static final int SMALL_PAGE_THRESHOLD_BYTES = 500;

  private final WbApiCaller apiCaller;
  private final IntegrationProperties properties;
  private final OffsetPagedCapture offsetPagedCapture;

  public List<CaptureResult> captureAllPages(CaptureContext context, String apiToken) {
    String baseUrl = properties.getWildberries().getPricesBaseUrl();

    return offsetPagedCapture.captureAllPages(
        context, PAGE_LIMIT,
        OffsetPagedCapture.DEFAULT_MAX_PAGES,
        SMALL_PAGE_THRESHOLD_BYTES,
        "WB prices",
        (offset, pageNumber) -> apiCaller.get(
            baseUrl + PRICES_PATH,
            uriBuilder -> uriBuilder
                .queryParam("limit", PAGE_LIMIT)
                .queryParam("offset", offset)
                .build(),
            apiToken, context.connectionId(), RateLimitGroup.WB_PRICES_READ));
  }
}
