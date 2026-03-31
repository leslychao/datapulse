package io.datapulse.etl.adapter.wb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.util.StreamingPageCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.PageCaptureResult;
import io.datapulse.etl.domain.cursor.JsonPathCursorExtractor;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class WbCatalogReadAdapter {

    private static final String CARDS_LIST_PATH = "/content/v2/get/cards/list";
    private static final int PAGE_LIMIT = 100;

    private static final JsonPathCursorExtractor CURSOR_EXTRACTOR =
            new JsonPathCursorExtractor("cursor.nmID");

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
            Flux<DataBuffer> body = apiCaller.post(
                    baseUrl + CARDS_LIST_PATH, buildCursorRequest(updatedAt, nmId),
                    apiToken, context.connectionId(), RateLimitGroup.WB_CONTENT);

            PageCaptureResult page = pageCapture.capture(body, context, pageNumber, CURSOR_EXTRACTOR);
            results.add(page.captureResult());

            String cursor = page.cursor();
            if (cursor != null && !cursor.isEmpty()) {
                nmId = Long.parseLong(cursor);
            } else {
                hasMore = false;
            }

            pageNumber++;
            log.debug("WB catalog page captured: connectionId={}, page={}, cursor={}",
                    context.connectionId(), pageNumber, cursor);
        }

        log.info("WB catalog capture completed: connectionId={}, totalPages={}",
                context.connectionId(), results.size());
        return results;
    }

    private Map<String, Object> buildCursorRequest(String updatedAt, long nmId) {
        Map<String, Object> cursor = Map.of(
                "limit", PAGE_LIMIT,
                "updatedAt", updatedAt,
                "nmID", nmId
        );
        Map<String, Object> filter = Map.of("withPhoto", -1);
        return Map.of("cursor", cursor, "filter", filter);
    }
}
