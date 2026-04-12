package io.datapulse.etl.adapter.yandex;

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
 * Captures the full Yandex Market category tree in a single POST request.
 * No pagination — the entire tree is returned at once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YandexCategoryTreeReadAdapter {

  private static final String CATEGORY_TREE_PATH = "/v2/categories/tree";

  private final YandexApiCaller apiCaller;
  private final StreamingPageCapture pageCapture;

  public CaptureResult capture(CaptureContext context, String apiKey) {
    Flux<DataBuffer> body = apiCaller.postNoBody(
        CATEGORY_TREE_PATH, context.connectionId(),
        RateLimitGroup.YANDEX_DEFAULT, apiKey);

    PageCaptureResult page = pageCapture.capture(
        body, context, 0, NoCursorExtractor.INSTANCE);

    log.info("Yandex category tree captured: connectionId={}, bytes={}",
        context.connectionId(), page.captureResult().byteSize());
    return page.captureResult();
  }
}
