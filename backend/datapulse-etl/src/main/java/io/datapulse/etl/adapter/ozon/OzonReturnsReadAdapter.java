package io.datapulse.etl.adapter.ozon;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
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
public class OzonReturnsReadAdapter {

    private static final String RETURNS_PATH = "/v1/returns/list";
    private static final int PAGE_LIMIT = 500;
    private static final JsonPathCursorExtractor LAST_ID_EXTRACTOR =
            new JsonPathCursorExtractor("last_id");

    private final OzonApiCaller apiCaller;
    private final StreamingPageCapture pageCapture;

    public List<CaptureResult> captureAllPages(CaptureContext context,
                                               String clientId, String apiKey,
                                               OffsetDateTime since, OffsetDateTime to) {
        List<CaptureResult> results = new ArrayList<>();
        long lastId = 0;
        int pageNumber = 0;
        boolean hasMore = true;

        while (hasMore) {
            long currentLastId = lastId;
            Flux<DataBuffer> body = apiCaller.post(RETURNS_PATH,
                    Map.of(
                            "filter", Map.of(
                                    "logistic_return_date", Map.of(
                                            "time_from", since.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                            "time_to", to.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))),
                            "last_id", currentLastId,
                            "limit", PAGE_LIMIT),
                    context.connectionId(), RateLimitGroup.OZON_DEFAULT,
                    clientId, apiKey);

            PageCaptureResult page = pageCapture.capture(
                    body, context, pageNumber, LAST_ID_EXTRACTOR);
            results.add(page.captureResult());

            pageNumber++;

            String cursor = page.cursor();
            if (cursor != null && !cursor.isEmpty() && !"0".equals(cursor)) {
                lastId = Long.parseLong(cursor);
            } else {
                hasMore = false;
            }

            if (page.captureResult().byteSize() < 200) {
                hasMore = false;
            }
        }

        log.info("Ozon returns capture completed: connectionId={}, totalPages={}",
                context.connectionId(), results.size());
        return results;
    }
}
