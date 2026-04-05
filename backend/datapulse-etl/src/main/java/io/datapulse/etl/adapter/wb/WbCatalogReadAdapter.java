package io.datapulse.etl.adapter.wb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.util.StreamingPageCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.PageCaptureResult;
import io.datapulse.etl.domain.cursor.WbCatalogCursorExtractor;
import io.datapulse.etl.domain.cursor.WbCatalogCursorExtractor.WbCatalogCursor;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * WB Catalog: compound cursor ({@code updatedAt} + {@code nmID}) with termination
 * at {@code cursor.total < limit}. Per WB API contract, both cursor fields must be
 * sent back on each page request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WbCatalogReadAdapter {

  private static final String CARDS_LIST_PATH = "/content/v2/get/cards/list";
  private static final int PAGE_LIMIT = 100;
  private static final int MAX_PAGES = 5_000;

  private final WbApiCaller apiCaller;
  private final IntegrationProperties properties;
  private final StreamingPageCapture pageCapture;

  public List<CaptureResult> captureAllPages(CaptureContext context, String apiToken) {
    List<CaptureResult> results = new ArrayList<>();
    String updatedAt = "";
    long nmId = 0;
    int pageNumber = 0;
    boolean hasMore = true;

    String baseUrl = properties.getWildberries().getContentBaseUrl();

    while (hasMore) {
      if (pageNumber >= MAX_PAGES) {
        log.warn("WB catalog: stopping pagination, max page cap reached"
            + " (connectionId={}, pages={})", context.connectionId(), pageNumber);
        break;
      }

      Flux<DataBuffer> body = apiCaller.post(
          baseUrl + CARDS_LIST_PATH, buildCursorRequest(updatedAt, nmId),
          apiToken, context.connectionId(), RateLimitGroup.WB_CONTENT);

      PageCaptureResult page = pageCapture.capture(
          body, context, pageNumber, WbCatalogCursorExtractor.INSTANCE);
      results.add(page.captureResult());

      String compositeCursor = page.cursor();
      if (compositeCursor != null && !compositeCursor.isEmpty()) {
        WbCatalogCursor parsed = WbCatalogCursorExtractor.parse(compositeCursor);
        updatedAt = parsed.updatedAt();
        nmId = parsed.nmId();

        if (parsed.total() < PAGE_LIMIT) {
          hasMore = false;
        }
      } else {
        hasMore = false;
      }

      pageNumber++;
      log.debug("WB catalog page captured: connectionId={}, page={}, nmId={}, updatedAt={}",
          context.connectionId(), pageNumber, nmId, updatedAt);
    }

    log.info("WB catalog capture completed: connectionId={}, totalPages={}",
        context.connectionId(), results.size());
    return results;
  }

  private Map<String, Object> buildCursorRequest(String updatedAt, long nmId) {
    Map<String, Object> cursor = Map.of(
        "limit", PAGE_LIMIT,
        "updatedAt", updatedAt,
        "nmID", nmId);
    Map<String, Object> filter = Map.of("withPhoto", -1);
    return Map.of("cursor", cursor, "filter", filter);
  }
}
