package io.datapulse.etl.adapter.yandex;

import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.util.CursorPagedCapture;
import io.datapulse.etl.adapter.util.CursorPagedCapture.CursorPageSpec;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.cursor.JsonPathCursorExtractor;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Captures offer prices via dedicated {@code /offer-prices} endpoint.
 * <p>
 * NOTE: prices are also available inside offer-mappings ({@code offer.basicPrice}).
 * If catalog adapter extracts prices as dual output, this adapter becomes redundant
 * and can be removed.
 */
@Service
@RequiredArgsConstructor
public class YandexPriceReadAdapter {

  private static final String OFFER_PRICES_PATH =
      "/v2/businesses/%d/offer-prices";
  private static final int PAGE_LIMIT = 100;

  private static final JsonPathCursorExtractor CURSOR_EXTRACTOR =
      new JsonPathCursorExtractor("result.paging.nextPageToken");

  private final YandexApiCaller apiCaller;
  private final CursorPagedCapture cursorPagedCapture;

  public List<CaptureResult> captureAllPages(
      CaptureContext context, String apiKey, long businessId) {
    String basePath = OFFER_PRICES_PATH.formatted(businessId);

    CursorPageSpec spec = new CursorPageSpec(
        CURSOR_EXTRACTOR,
        "Yandex prices",
        (cursor, pageNumber) -> {
          String path = buildPagePath(basePath, cursor);
          return apiCaller.post(
              path, context.connectionId(), RateLimitGroup.YANDEX_CATALOG,
              apiKey, Map.of());
        });

    return cursorPagedCapture.captureAllPages(
        context, null, spec, CursorPagedCapture.NO_OP);
  }

  private String buildPagePath(String basePath, String cursor) {
    if (cursor == null || cursor.isEmpty()) {
      return basePath + "?limit=" + PAGE_LIMIT;
    }
    return basePath + "?limit=" + PAGE_LIMIT + "&pageToken=" + cursor;
  }
}
