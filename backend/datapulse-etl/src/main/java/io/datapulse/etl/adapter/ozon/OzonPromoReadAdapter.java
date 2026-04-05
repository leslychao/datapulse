package io.datapulse.etl.adapter.ozon;

import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.util.OffsetPagedCapture;
import io.datapulse.etl.adapter.util.StreamingPageCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.PageCaptureResult;
import io.datapulse.etl.domain.cursor.NoCursorExtractor;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OzonPromoReadAdapter {

  private static final String ACTIONS_PATH = "/v1/actions";
  private static final String ACTION_PRODUCTS_PATH = "/v1/actions/products";
  private static final String ACTION_CANDIDATES_PATH = "/v1/actions/candidates";
  private static final int PAGE_LIMIT = 1000;
  private static final int PROMO_SMALL_PAGE_THRESHOLD_BYTES = 500;

  private final OzonApiCaller apiCaller;
  private final StreamingPageCapture pageCapture;
  private final OffsetPagedCapture offsetPagedCapture;

  public List<CaptureResult> captureActions(CaptureContext context,
      String clientId, String apiKey) {
    PageCaptureResult page = pageCapture.capture(
        apiCaller.get(ACTIONS_PATH,
            context.connectionId(), RateLimitGroup.OZON_PROMO,
            clientId, apiKey),
        context, 0, NoCursorExtractor.INSTANCE);

    log.info("Ozon actions capture completed: connectionId={}, byteSize={}",
        context.connectionId(), page.captureResult().byteSize());
    return List.of(page.captureResult());
  }

  public List<CaptureResult> captureActionProducts(CaptureContext context,
      String clientId, String apiKey,
      long actionId) {
    return offsetPagedCapture.captureAllPages(
        context, PAGE_LIMIT,
        OffsetPagedCapture.DEFAULT_MAX_PAGES,
        PROMO_SMALL_PAGE_THRESHOLD_BYTES,
        "Ozon action products (actionId=%d)".formatted(actionId),
        (offset, pageNumber) -> apiCaller.post(
            ACTION_PRODUCTS_PATH,
            Map.of("action_id", actionId, "offset", offset, "limit", PAGE_LIMIT),
            context.connectionId(), RateLimitGroup.OZON_PROMO,
            clientId, apiKey));
  }

  public List<CaptureResult> captureActionCandidates(CaptureContext context,
      String clientId, String apiKey,
      long actionId) {
    return offsetPagedCapture.captureAllPages(
        context, PAGE_LIMIT,
        OffsetPagedCapture.DEFAULT_MAX_PAGES,
        PROMO_SMALL_PAGE_THRESHOLD_BYTES,
        "Ozon action candidates (actionId=%d)".formatted(actionId),
        (offset, pageNumber) -> apiCaller.post(
            ACTION_CANDIDATES_PATH,
            Map.of("action_id", actionId, "offset", offset, "limit", PAGE_LIMIT),
            context.connectionId(), RateLimitGroup.OZON_PROMO,
            clientId, apiKey));
  }
}
