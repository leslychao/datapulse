package io.datapulse.etl.adapter.yandex;

import java.util.ArrayList;
import java.util.List;

import io.datapulse.etl.adapter.util.StreamingPageCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.PageCaptureResult;
import io.datapulse.etl.domain.cursor.NoCursorExtractor;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Captures warehouse reference data from two sources:
 * <ol>
 *   <li>{@code GET /v2/warehouses} — Yandex fulfillment warehouses (FBY/LaaS)</li>
 *   <li>{@code GET /v2/businesses/{businessId}/warehouses} — seller warehouses (FBS)</li>
 * </ol>
 * No pagination — both endpoints return full list in a single response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YandexWarehousesReadAdapter {

  private static final String FULFILLMENT_WAREHOUSES_PATH = "/v2/warehouses";
  private static final String SELLER_WAREHOUSES_PATH =
      "/v2/businesses/%d/warehouses";

  private final YandexApiCaller apiCaller;
  private final StreamingPageCapture pageCapture;

  public List<CaptureResult> capture(
      CaptureContext context, String apiKey, long businessId) {
    List<CaptureResult> results = new ArrayList<>(2);

    results.add(captureFulfillmentWarehouses(context, apiKey));
    results.add(captureSellerWarehouses(context, apiKey, businessId));

    log.info("Yandex warehouses capture completed: connectionId={}, sources=2",
        context.connectionId());
    return results;
  }

  private CaptureResult captureFulfillmentWarehouses(
      CaptureContext context, String apiKey) {
    Flux<DataBuffer> body = apiCaller.get(
        FULFILLMENT_WAREHOUSES_PATH, context.connectionId(),
        RateLimitGroup.YANDEX_WAREHOUSES, apiKey);

    PageCaptureResult page = pageCapture.capture(
        body, context, 0, NoCursorExtractor.INSTANCE);

    log.debug("Yandex fulfillment warehouses captured: connectionId={}, bytes={}",
        context.connectionId(), page.captureResult().byteSize());
    return page.captureResult();
  }

  private CaptureResult captureSellerWarehouses(
      CaptureContext context, String apiKey, long businessId) {
    String path = SELLER_WAREHOUSES_PATH.formatted(businessId);

    Flux<DataBuffer> body = apiCaller.get(
        path, context.connectionId(),
        RateLimitGroup.YANDEX_WAREHOUSES, apiKey);

    PageCaptureResult page = pageCapture.capture(
        body, context, 1, NoCursorExtractor.INSTANCE);

    log.debug("Yandex seller warehouses captured: connectionId={}, businessId={}, bytes={}",
        context.connectionId(), businessId, page.captureResult().byteSize());
    return page.captureResult();
  }
}
