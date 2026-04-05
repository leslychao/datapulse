package io.datapulse.etl.adapter.ozon;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
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
 * Ozon Returns uses numeric {@code last_id} in request body.
 * The cursor is extracted as a string from JSON, and {@link CursorPagedCapture}
 * handles stop-on-non-advancing-cursor uniformly (including "0" → treated as
 * cursor present but effectively first-page, which will repeat = stop).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OzonReturnsReadAdapter {

  private static final String RETURNS_PATH = "/v1/returns/list";
  private static final int PAGE_LIMIT = 500;
  private static final JsonPathCursorExtractor LAST_ID_EXTRACTOR =
      new JsonPathCursorExtractor("last_id");

  private final OzonApiCaller apiCaller;
  private final CursorPagedCapture cursorPagedCapture;

  public List<CaptureResult> captureAllPages(CaptureContext context,
      String clientId, String apiKey,
      OffsetDateTime since, OffsetDateTime to,
      long initialLastId) {
    String initialCursor = initialLastId > 0 ? String.valueOf(initialLastId) : "";

    CursorPageSpec spec = new CursorPageSpec(
        LAST_ID_EXTRACTOR,
        "Ozon returns",
        (currentCursor, pageNumber) -> {
          long numericLastId = currentCursor.isEmpty() ? 0L : Long.parseLong(currentCursor);
          return apiCaller.post(RETURNS_PATH,
              Map.of(
                  "filter", Map.of(
                      "logistic_return_date", Map.of(
                          "time_from",
                          since.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                          "time_to",
                          to.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))),
                  "last_id", numericLastId,
                  "limit", PAGE_LIMIT),
              context.connectionId(), RateLimitGroup.OZON_DEFAULT,
              clientId, apiKey);
        });

    return cursorPagedCapture.captureAllPages(
        context, initialCursor, spec,
        (pageIndex, ctx, outcome) ->
            log.debug("Ozon returns page captured: connectionId={}, page={}",
                ctx.connectionId(), pageIndex + 1));
  }
}
