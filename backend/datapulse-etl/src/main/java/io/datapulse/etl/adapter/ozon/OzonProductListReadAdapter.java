package io.datapulse.etl.adapter.ozon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.util.StreamingPageCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.PageCaptureResult;
import io.datapulse.etl.domain.cursor.JsonPathCursorExtractor;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class OzonProductListReadAdapter {

    private static final String PRODUCT_LIST_PATH = "/v3/product/list";
    private static final int PAGE_LIMIT = 1000;

    private static final JsonPathCursorExtractor CURSOR_EXTRACTOR =
            new JsonPathCursorExtractor("result.last_id");

    private final OzonApiCaller apiCaller;
    private final StreamingPageCapture pageCapture;

    public List<CaptureResult> captureAllPages(CaptureContext context,
                                               String clientId, String apiKey,
                                               String initialLastId) {
        List<CaptureResult> results = new ArrayList<>();
        String lastId = initialLastId == null ? "" : initialLastId;
        int pageNumber = 0;
        boolean hasMore = true;

        while (hasMore) {
            String currentLastId = lastId;
            Flux<DataBuffer> body = apiCaller.post(PRODUCT_LIST_PATH,
                    Map.of(
                            "filter", Map.of("visibility", "ALL"),
                            "last_id", currentLastId,
                            "limit", PAGE_LIMIT),
                    context.connectionId(), RateLimitGroup.OZON_DEFAULT,
                    clientId, apiKey);

            PageCaptureResult page = pageCapture.capture(
                    body, context, pageNumber, CURSOR_EXTRACTOR, null,
                    currentLastId.isEmpty() ? null : currentLastId);
            results.add(page.captureResult());

            lastId = page.cursor();
            if (OzonCursorPaging.shouldStopAfterStringPage(lastId, currentLastId)) {
                if (OzonCursorPaging.isNonAdvancingStringCursor(lastId, currentLastId)) {
                    log.debug(
                            "Ozon product list pagination stopped: non-advancing last_id, connectionId={}",
                            context.connectionId());
                }
                hasMore = false;
            }

            pageNumber++;
            log.debug("Ozon product list page captured: connectionId={}, page={}",
                    context.connectionId(), pageNumber);
        }

        log.info("Ozon product list capture completed: connectionId={}, totalPages={}",
                context.connectionId(), results.size());
        return results;
    }
}
