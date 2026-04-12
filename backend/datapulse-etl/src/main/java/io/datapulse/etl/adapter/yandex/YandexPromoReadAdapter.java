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

@Service
@RequiredArgsConstructor
public class YandexPromoReadAdapter {

  private static final String PROMOS_PATH =
      "/v2/businesses/%d/promos";
  private static final int PAGE_LIMIT = 100;

  private static final JsonPathCursorExtractor CURSOR_EXTRACTOR =
      new JsonPathCursorExtractor("result.paging.nextPageToken");

  private final YandexApiCaller apiCaller;
  private final CursorPagedCapture cursorPagedCapture;

  public List<CaptureResult> captureAllPages(
      CaptureContext context, String apiKey, long businessId) {
    String basePath = PROMOS_PATH.formatted(businessId);

    CursorPageSpec spec = new CursorPageSpec(
        CURSOR_EXTRACTOR,
        "Yandex promos",
        (cursor, pageNumber) -> {
          String path = buildPagePath(basePath, cursor);
          return apiCaller.post(
              path, context.connectionId(), RateLimitGroup.YANDEX_DEFAULT,
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
