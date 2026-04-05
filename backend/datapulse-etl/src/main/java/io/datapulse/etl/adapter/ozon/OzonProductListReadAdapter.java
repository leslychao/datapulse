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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OzonProductListReadAdapter {

  private static final String PRODUCT_LIST_PATH = "/v3/product/list";
  private static final int PAGE_LIMIT = 1000;

  private static final JsonPathCursorExtractor CURSOR_EXTRACTOR =
      new JsonPathCursorExtractor("result.last_id");

  private final OzonApiCaller apiCaller;
  private final CursorPagedCapture cursorPagedCapture;

  public List<CaptureResult> captureAllPages(
      CaptureContext context, String clientId, String apiKey, String initialLastId) {
    CursorPageSpec spec = new CursorPageSpec(
        CURSOR_EXTRACTOR,
        "Ozon product list",
        (currentLastId, pageNumber) -> apiCaller.post(
            PRODUCT_LIST_PATH,
            Map.of(
                "filter", Map.of("visibility", "ALL"),
                "last_id", currentLastId,
                "limit", PAGE_LIMIT),
            context.connectionId(), RateLimitGroup.OZON_DEFAULT,
            clientId, apiKey));

    return cursorPagedCapture.captureAllPages(
        context, initialLastId, spec,
        (pageIndex, ctx, outcome) -> {
          if (outcome.stop() && outcome.nonAdvancingCursor()) {
            log.debug(
                "Ozon product list pagination stopped: non-advancing last_id,"
                    + " connectionId={}",
                ctx.connectionId());
          }
          log.debug("Ozon product list page captured: connectionId={}, page={}",
              ctx.connectionId(), pageIndex + 1);
        });
  }
}
