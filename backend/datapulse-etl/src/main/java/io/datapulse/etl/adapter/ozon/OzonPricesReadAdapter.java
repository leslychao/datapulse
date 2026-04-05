package io.datapulse.etl.adapter.ozon;

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
public class OzonPricesReadAdapter {

  private static final String PRICES_PATH = "/v5/product/info/prices";
  private static final int PAGE_LIMIT = 1000;

  private static final JsonPathCursorExtractor CURSOR_EXTRACTOR =
      new JsonPathCursorExtractor("result.last_id");

  private final OzonApiCaller apiCaller;
  private final CursorPagedCapture cursorPagedCapture;

  public List<CaptureResult> captureAllPages(
      CaptureContext context, String clientId, String apiKey, String initialLastId) {
    CursorPageSpec spec = new CursorPageSpec(
        CURSOR_EXTRACTOR,
        "Ozon prices",
        (currentLastId, pageNumber) -> apiCaller.post(
            PRICES_PATH,
            Map.of(
                "filter", Map.of("visibility", "ALL"),
                "last_id", currentLastId,
                "limit", PAGE_LIMIT),
            context.connectionId(), RateLimitGroup.OZON_DEFAULT,
            clientId, apiKey));

    return cursorPagedCapture.captureAllPages(
        context, initialLastId, spec, CursorPagedCapture.NO_OP);
  }
}
